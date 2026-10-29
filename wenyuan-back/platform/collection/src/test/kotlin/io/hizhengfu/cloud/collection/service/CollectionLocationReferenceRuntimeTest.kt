package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.install.CollectionInstall
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime
import org.mockito.Mockito.mock
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.cache.concurrent.ConcurrentMapCacheManager

class CollectionLocationReferenceRuntimeTest {
    private fun fixture(body: (WarehouseService) -> Unit) {
        val db=Database.connect("jdbc:sqlite:file:location-reference-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
        AnnotationConfigApplicationContext().use { context ->
        context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
        context.refresh();SpringContextHolder().setApplicationContext(context)
        transaction(db) {
            SchemaUtils.create(*CollectionInstall("single").initTables().toTypedArray())
            CollectionOrgDao.new("museum") { code="museum";name="测试馆" }
            WarehouseDao.new("location") { orgId="museum";name="待核对位置";type=EnumWarehouseType.WAREHOUSE;sceneType=EnumLocationScene.STORAGE;parentId="0";parentIds="0,";isLeaf=true }
            body(WarehouseService(CollectionScopeResolver("single","museum"),mock(BusinessAttachmentBinder::class.java)))
        }
        }
    }
    private fun reference(source: String) {
        when(source) {
            "collection" -> CollectionDao.new("item") { orgId="museum";name="原藏品";totalRegNo="A1";warehouseId="location" }
            "movement" -> StorageMovementDao.new("movement") { orgId="other";collectionId="old-item";type=EnumMoveType.OUT;fromWarehouseId="location" }
            "placement" -> CollectionPlacementDao.new("placement") { orgId="museum";sourceBizType="EXHIBITION";sourceBizId="exhibition";collectionId="item";locationId="location" }
            "ledger" -> CollectionLedgerLogDao.new("ledger") { orgId="other";collectionId="old-item";action=EnumLedgerAction.entries.first();before=JsonUtils.toJson(mapOf("warehouseId" to "location")) }
            "catalog" -> {
                val item=CollectionDao.new("item") { orgId="museum";name="藏品";totalRegNo="A1";warehouseId="location" }
                CollectionCatalogVersionDao.new("catalog-version") { orgId="museum";catalogId="catalog";collectionId="item";version=1;catalogStatus=EnumCatalogStatus.APPROVED;content=CollectionCatalogMapper.fromCollection(item.toModel()) }
                item.warehouseId=null
            }
            "entry" -> WarehouseEntryApplicationDao.new("entry") { orgId="other";applicationNo="entry1";applicantId="keeper";entrantIds="keeper";warehouseIds="another, location ; third";purpose="历史入库";plannedStart=LocalDateTime.of(2026,1,1,8,0);plannedEnd=plannedStart.plusHours(1) }
            "anchor" -> CollectionPermanentArchiveDao.new("archive") { orgId="other";bizType=WarehouseStaffingService.BIZ;bizId="location";phase="ASSIGNED";snapshot="{}";digest=CollectionCareEvidence.digest(snapshot) }
            "embedded" -> CollectionPermanentArchiveDao.new("archive") { orgId="other";bizType="HISTORICAL_TEST";bizId="old-case";phase="RECORDED";snapshot=JsonUtils.toJson(mapOf("sourceSnapshot" to JsonUtils.toJson(mapOf("warehouseId" to "location"))))!!;digest=CollectionCareEvidence.digest(snapshot) }
            "loan-record" -> LoanComplianceRecordDao.new("loan-record") { orgId="other";loanId="loan";kind="HISTORICAL";content=JsonUtils.toJson(mapOf("warehouseId" to "location"))!! }
            "snapshot" -> CollectionPermanentArchiveDao.new("archive") { orgId="other";bizType="HISTORICAL_TEST";bizId="old-case";phase="RECORDED";snapshot=JsonUtils.toJson(mapOf("items" to listOf(mapOf("fromWarehouseId" to "location"))))!!;digest=CollectionCareEvidence.digest(snapshot) }
        }
    }
    @ParameterizedTest @CsvSource("collection,delete","collection,type","collection,scene","movement,delete","movement,type","movement,scene",
        "placement,delete","placement,type","placement,scene","snapshot,delete","snapshot,type","snapshot,scene",
        "ledger,delete","ledger,type","ledger,scene","catalog,delete","catalog,type","catalog,scene",
        "entry,delete","entry,type","entry,scene","anchor,delete","anchor,type","anchor,scene",
        "embedded,delete","embedded,type","embedded,scene","loan-record,delete","loan-record,type","loan-record,scene")
    fun `referenced location cannot be removed or reclassified`(source: String, operation: String) = fixture { service ->
        reference(source)
        val before=WarehouseDao["location"].toModel()
        assertThrows(BadRequestException::class.java) {
            when(operation) {
                "delete" -> service.delete("location")
                "type" -> service.update("location",WarehouseVo(name="不应保存",type=EnumWarehouseType.AREA))
                else -> service.update("location",WarehouseVo(name="不应保存",type=EnumWarehouseType.EXHIBITION_HALL,sceneType=EnumLocationScene.EXHIBITION))
            }
        }
        assertEquals(before,WarehouseDao["location"].toModel())
    }
    @ParameterizedTest @ValueSource(strings=["collection","movement","snapshot","catalog"])
    fun `referenced location still permits ordinary name correction`(source: String) = fixture { service ->
        reference(source);service.update("location",WarehouseVo(name="核实名称"))
        assertEquals("核实名称",WarehouseDao["location"].name)
        assertEquals(EnumWarehouseType.WAREHOUSE,WarehouseDao["location"].type)
    }
    @Test fun `unreferenced leaf can be reclassified and removed with legacy plain text logs`() = fixture { service ->
        CollectionLedgerLogDao.new("ledger") { orgId="museum";collectionId="item";action=EnumLedgerAction.entries.first();before="旧名称";after="新名称" }
        service.update("location",WarehouseVo(name="空置区域",type=EnumWarehouseType.AREA))
        assertEquals(EnumWarehouseType.AREA,WarehouseDao["location"].type)
        service.delete("location");assertNull(WarehouseDao.findById("location"))
    }
    @Test fun `similar identifiers and narrative text do not block another location`() = fixture { service ->
        CollectionPermanentArchiveDao.new("archive") { orgId="museum";bizType="HISTORICAL_TEST";bizId="case";phase="RECORDED";snapshot=JsonUtils.toJson(mapOf("warehouseId" to "location-long","remark" to "核对location区域"))!!;digest=CollectionCareEvidence.digest(snapshot) }
        service.delete("location");assertNull(WarehouseDao.findById("location"))
    }
    @Test fun `original image identity alone does not prevent deleting an unused location`() = fixture { service ->
        CollectionPermanentArchiveDao.new("archive") { orgId="museum";bizType=CollectionBusinessOriginalFiles.BIZ;bizId="location";phase="ORIGINAL_ACCESS_test";snapshot=JsonUtils.toJson(mapOf("businessType" to CollectionBusinessOriginalFileSource.WAREHOUSE,"fileId" to "image"))!!;digest=CollectionCareEvidence.digest(snapshot) }
        service.delete("location");assertNull(WarehouseDao.findById("location"))
        assertNotNull(CollectionPermanentArchiveDao.findById("archive"))
    }
    @Test fun `reference beyond the first snapshot page remains protected`() = fixture { service ->
        repeat(201) { i -> CollectionPermanentArchiveDao.new("archive-${i.toString().padStart(4,'0')}") { orgId="other";bizType="HISTORICAL_TEST";bizId="case-$i";phase="RECORDED";snapshot=JsonUtils.toJson(mapOf("warehouseId" to if(i==200)"location" else "elsewhere"))!!;digest=CollectionCareEvidence.digest(snapshot) } }
        assertThrows(BadRequestException::class.java) { service.delete("location") }
        assertNotNull(WarehouseDao.findById("location"))
    }
    @ParameterizedTest @ValueSource(strings=["malformed-snapshot","missing-table"])
    fun `incomplete reference evidence cannot be treated as no references`(failure: String) = fixture { service ->
        if(failure=="missing-table")SchemaUtils.drop(StorageMovements)
        else CollectionPermanentArchiveDao.new("archive") { orgId="other";bizType="HISTORICAL_TEST";bizId="case";phase="RECORDED";snapshot="{broken";digest=CollectionCareEvidence.digest(snapshot) }
        assertThrows(BadRequestException::class.java) { service.delete("location") }
        assertNotNull(WarehouseDao.findById("location"))
    }
}
