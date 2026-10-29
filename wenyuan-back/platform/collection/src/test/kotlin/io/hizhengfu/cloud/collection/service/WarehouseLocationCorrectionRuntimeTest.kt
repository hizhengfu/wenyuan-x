package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.install.CollectionInstall
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.device.dao.*
import io.hizhengfu.cloud.museum.device.common.MuseumLocationDirectory
import io.hizhengfu.cloud.museum.device.common.MuseumLocationSourceVo
import io.hizhengfu.cloud.museum.device.common.MuseumScopeResolver
import io.hizhengfu.cloud.museum.device.common.MuseumCredentialCipher
import io.hizhengfu.cloud.museum.device.service.*
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate

class WarehouseLocationCorrectionRuntimeTest {
    private val scope = CollectionScopeResolver("single", "museum")
    private val binder = mock(BusinessAttachmentBinder::class.java).also {
        `when`(it.resolveSingle(anyString())).thenAnswer { call -> call.getArgument<String>(0) }
    }
    private val archives = CollectionPermanentArchiveService(binder, scope, readAccess=unprotectedArchiveReadAccessProvider())
    private val directory = object : MuseumLocationDirectory {
        override val sourceType = "BUSINESS_MASTER"
        override val sourceCode = "COL_WAREHOUSE"
        override fun beforeSynchronize(orgId: String) = CollectionLocationDirectoryGuard.lockForSynchronization(orgId)
        override fun listEnabledLocations(orgId: String): List<MuseumLocationSourceVo> = WarehouseDao.find {
            (Warehouses.orgId eq orgId) and (Warehouses.status eq EnumContentStatus.NORMAL)
        }.map { MuseumLocationSourceVo(it.id.value, null, emptyList(), it.id.value, it.id.value,
            it.code, it.name, it.type.name, it.name) }
    }

    @AfterEach fun clear() = SecurityContextHolder.clearContext()

    private fun fixture(body: (WarehouseService, MuseumIngestConfigService) -> Unit) {
        val db = Database.connect("jdbc:sqlite:file:location-correction-${System.nanoTime()}?mode=memory&cache=shared", driver = "org.sqlite.JDBC")
        val ingest = MuseumIngestConfigService(MuseumScopeResolver(), MuseumIngestCatalogService(),
            MuseumIngestDeviceCandidateRecorder(), MuseumCredentialCipher("isolated-correction-test-key"),
            listOf(directory))
        transaction(db) {
            SchemaUtils.create(*(CollectionInstall("single").initTables() +
                setOf(MuseumLocations, MuseumLocationSources, MuseumDevices, MuseumAlarmRules, Files)).toTypedArray())
            originalReaderSchema()
            originalReader("operator", CollectionConst.WAREHOUSE_MANAGE)
            CollectionOrgDao.new("museum") { code = "M"; name = "本馆" }
            WarehouseDao.new("old") { orgId = "museum"; name = "2号展厅"; sceneType = EnumLocationScene.STORAGE; type = EnumWarehouseType.WAREHOUSE }
            FileDao.new("proof") { name = "更正签署.pdf"; path = "/files/proof.pdf"; hash = "proof"; size = 100; ext = "pdf" }
            val user = SecurityUser(id = "operator", name = "经办", username = "operator", password = "",
                groupId = "museum", groupIds = setOf("museum"), postIds = emptySet(), dataType = 0,
                roles = setOf("1"), authorities = listOf(SimpleGrantedAuthority(CollectionConst.WAREHOUSE_MANAGE)), enabled = true)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
            body(WarehouseService(scope, binder, archives = archives, ingestConfig = ingest), ingest)
        }
    }

    private fun request() = WarehouseLocationCorrectionVo(
        replacement = WarehouseVo(name = "2号展厅", sceneType = EnumLocationScene.EXHIBITION, type = EnumWarehouseType.EXHIBITION_HALL),
        reason = "旧展厅误记为保管库房，核实实际用途后保留监测历史并另建展厅位置", signedFileIds = "proof",
    )

    @Test fun `retirement preserves historic ingest location and excludes old warehouse from current diary`() = fixture { warehouse, ingest ->
        ingest.synchronizeLocationCatalog("museum")
        val oldUnified = MuseumLocationSourceDao.all().single().locationId
        MuseumDeviceDao.new("historic-device") {
            orgId = "museum"; code = "HIST"; name = "历史设备"; modelCode = "MODEL"; gatewayId = "GATEWAY"
            externalDeviceId = "HIST"; locationId = oldUnified; status = EnumContentStatus.DELETE
        }
        val result = warehouse.retireAsExhibition("old", request())
        assertEquals("old", result.retiredLocationId)
        assertNotEquals("old", result.replacementLocationId)
        assertEquals(EnumContentStatus.DELETE, WarehouseDao["old"].status)
        assertEquals(EnumWarehouseType.WAREHOUSE, WarehouseDao["old"].type)
        assertEquals(EnumWarehouseType.EXHIBITION_HALL, WarehouseDao[result.replacementLocationId].type)
        assertEquals(EnumContentStatus.DELETE, MuseumLocationSourceDao.all().single { it.externalId == "old" }.status)
        assertEquals(EnumContentStatus.DELETE, MuseumLocationDao[oldUnified].status)
        assertEquals("WAREHOUSE", MuseumLocationDao[oldUnified].type)
        assertEquals(oldUnified, MuseumDeviceDao["historic-device"].locationId)
        assertEquals(setOf(result.replacementLocationId), warehouse.findAll().mapNotNull { it.id }.toSet())
        val diary = WarehouseDiaryService(scope, mock(WarehouseStaffingService::class.java),
            mock(CollectionResponsibleUserService::class.java), archives)
        assertTrue(diary.gaps(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)).isEmpty())
        assertEquals(result.archiveId, warehouse.correctionHistory("old").single()["id"])
        assertDoesNotThrow { warehouse.requireCorrectionOriginal("old", result.archiveId, "proof") }
        assertThrows(BadRequestException::class.java) {
            warehouse.requireCorrectionOriginal("old", result.archiveId, "other-file")
        }
        assertThrows(BadRequestException::class.java) { CollectionLocationMutation.reference("museum", "old") }
    }

    @Test fun `active device prevents retirement and leaves current sources unchanged`() = fixture { warehouse, ingest ->
        ingest.synchronizeLocationCatalog("museum")
        val oldUnified = MuseumLocationSourceDao.all().single().locationId
        MuseumDeviceDao.new("active-device") {
            orgId = "museum"; code = "ACTIVE"; name = "启用设备"; modelCode = "MODEL"; gatewayId = "GATEWAY"
            externalDeviceId = "ACTIVE"; locationId = oldUnified
        }
        assertThrows(BadRequestException::class.java) { warehouse.retireAsExhibition("old", request()) }
        assertEquals(EnumContentStatus.NORMAL, WarehouseDao["old"].status)
        assertEquals(EnumContentStatus.NORMAL, MuseumLocationSourceDao.all().single().status)
        assertTrue(warehouse.correctionHistory("old").isEmpty())
    }

    @Test fun `business history prevents retirement even when ingest mapping may be retained`() = fixture { warehouse, ingest ->
        ingest.synchronizeLocationCatalog("museum")
        WarehouseLogDao.new("historic-log") {
            orgId = "museum"; warehouseId = "old"; logDate = LocalDate.of(2026, 9, 1); content = "已登记的库房历史"
        }
        assertThrows(BadRequestException::class.java) { warehouse.retireAsExhibition("old", request()) }
        assertEquals(EnumContentStatus.NORMAL, WarehouseDao["old"].status)
        assertEquals(EnumContentStatus.NORMAL, MuseumLocationSourceDao.all().single { it.externalId == "old" }.status)
        assertTrue(warehouse.correctionHistory("old").isEmpty())
    }
}
