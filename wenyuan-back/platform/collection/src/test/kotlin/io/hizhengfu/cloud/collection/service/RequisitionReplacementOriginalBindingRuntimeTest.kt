package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.common.web.FileViewController
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.FlowDefinitionVo
import io.hizhengfu.cloud.flow.engine.*
import io.hizhengfu.cloud.flow.service.*
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.file.service.*
import io.hizhengfu.cloud.sys.file.service.storage.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime

/** Real current directory, flow, archive, uploader binding and LOCAL bytes; no HTTP login or physical signature claim. */
class RequisitionReplacementOriginalBindingRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions

    private val time = LocalDateTime.of(2026, 9, 9, 9, 0)
    private fun dossier() = RequisitionDossierVo("dept", "department", "keeper", "director", "researcher",
        "DEV-SYNTHETIC 无损观察", "器形比较", "研究室", "限位支撑及专人值守", "old-plan", time,
        time.plusDays(10), transportRequired = false, transportDecisionReason = "本馆同室提用，不需要包装运输")

    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate,
                          val tables: List<Table>, val root: Path) {
        val requisitions = c.getBean(RequisitionService::class.java)
        val replacement = c.getBean(RequisitionResponsibilityReplacementService::class.java)
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun login(id: String) {
            val actor = db { c.getBean(CollectionActorDirectory::class.java).requireActive(id) }
            assertFalse(actor.permissions.any { it == "ADMIN" || it == CollectionConst.ADMIN || it == CollectionConst.CROSS_ORG })
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(actor, null, actor.authorities)
        }
        fun state() = db { settledSafetySelectionState(*tables.toTypedArray()) }
        fun bytes(): Map<String, String> = db { FileDao.all().associate { f ->
            f.id.value to digest(java.nio.file.Files.readAllBytes(root.resolve(f.path)), "SHA-256")
        } }
        fun original(id: String): ByteArray {
            val path = db { FileDao[id].path }
            val request = MockHttpServletRequest("GET", "/userfiles/original/$path")
            request.addHeader("If-Modified-Since", "Tue, 01 Sep 2026 10:00:00 GMT")
            val response = MockHttpServletResponse()
            val controller = FileViewController(c, false, root.toString(), false, "unused",
                c.getBean(FileObjectStorageService::class.java), c.getBean(FileReadAccess::class.java))
            controller.downloadOriginalFile(request, response)
            assertEquals(200, response.status)
            assertEquals("private, no-store", response.getHeader("Cache-Control"))
            val content = java.nio.file.Files.readAllBytes(root.resolve(path))
            assertArrayEquals(content, response.contentAsByteArray)
            return response.contentAsByteArray
        }
    }

    private fun fixture(body: (Runtime, String) -> Unit) {
        val manager = SpringTransactionManager(SQLiteDataSource().apply {
            url = "jdbc:sqlite:${dir.resolve("replacement-real-originals.db")}"
        }, DatabaseConfig {})
        try {
            AnnotationConfigApplicationContext().use { c ->
                val scope = CollectionScopeResolver("multi", "museum")
                val locks = CollectionOperationLockService(listOf(CollectionIncidentOperationGuard()))
                c.beanFactory.registerSingleton("transactionManager", manager)
                c.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
                c.beanFactory.registerSingleton("keyGenerator", org.springframework.cache.interceptor.SimpleKeyGenerator())
                c.beanFactory.registerSingleton("scope", scope)
                c.beanFactory.registerSingleton("localStorage", LocalFileObjectStorage(dir.toString()))
                c.beanFactory.registerSingleton("locks", locks)
                c.beanFactory.registerSingleton("loader", FlowGraphLoader(16, 5))
                c.beanFactory.registerSingleton("registry", mock(FlowBusinessTypeRegistry::class.java))
                c.beanFactory.registerSingleton("resolver", FlowAssigneeResolver(emptyList()))
                c.beanFactory.registerSingleton("names", mock(CollectionUserResolver::class.java))
                c.beanFactory.registerSingleton("placement", mock(CollectionPlacementService::class.java))
                c.beanFactory.registerSingleton("returns", mock(CollectionReturnService::class.java))
                c.registerBean(FileObjectStorageService::class.java, java.util.function.Supplier {
                    FileObjectStorageService(c.getBean(LocalFileObjectStorage::class.java),
                        mock(FileStorageConfigReader::class.java), mock(RustFsStorageClient::class.java), c.getBean(FileReadAccess::class.java))
                })
                c.registerBean(FileService::class.java, java.util.function.Supplier {
                    FileService(dir.toString(), FileFolderService(), c.getBean(FileObjectStorageService::class.java),
                        listOf(CollectionArchiveFileRetentionGuard(), c.getBean(CollectionBusinessOriginalFileRetentionGuard::class.java)),
                        c.getBean(FileReadAccess::class.java))
                })
                c.registerBean(CollectionService::class.java, java.util.function.Supplier {
                    CollectionService(scope, c.getBean(BusinessAttachmentBinder::class.java),
                        mock(CollectionRegistrationNumberService::class.java), locks, mock(CollectionCatalogService::class.java),
                        mock(CollectionChangeService::class.java), mock(CollectionRetainedLedgerService::class.java),
                        c.getBean(CollectionDisclosureService::class.java))
                })
                c.register(Transactions::class.java, CollectionActorDirectory::class.java, CollectionResponsibleUserService::class.java,
                    BusinessAttachmentBinder::class.java, CollectionPermanentArchiveService::class.java, FileReadAccess::class.java,
                    CollectionFileReadAuthorization::class.java, CollectionBusinessArchiveReadPolicy::class.java,
                    CollectionDisclosureService::class.java, CollectionPermanentArchiveFileReadGuard::class.java,
                    CollectionBusinessOriginalFileSource::class.java, CollectionBusinessOriginalFiles::class.java,
                    CollectionOriginalIdentityReadPolicy::class.java, CollectionOriginalHistoryService::class.java, CollectionBusinessOriginalFileReadGuard::class.java,
                    CollectionBusinessOriginalFileRetentionGuard::class.java,
                    CollectionBusinessItemService::class.java, RequisitionStatutoryService::class.java,
                    RequisitionService::class.java, RequisitionResponsibilityReplacementService::class.java,
                    CoreCollectionFlowReadSources::class.java, CollectionFlowStartPolicy::class.java, CollectionFlowReadPolicy::class.java,
                    FlowReadAccess::class.java, CollectionNamedFlowActorPolicy::class.java, CollectionRoleFlowActorPolicy::class.java,
                    CollectionFlowDefinitionConstraint::class.java, CollectionFlowFormPolicy::class.java,
                    FlowDefinitionService::class.java, FlowEngine::class.java, FlowInstanceService::class.java,
                    FlowTaskService::class.java, FlowEventDispatcher::class.java)
                c.refresh()
                SpringContextHolder().setApplicationContext(c)
                val tables = (CollectionBusinessOriginalFileSource().tables.map { it.table } +
                    CoreCollectionFlowReadSources().sources().map { it.table } + listOf(
                    Users, Roles, RoleUsers, UserGroups, Groups, Permissions, RolePermissions,
                    CollectionLedgerLogs, CollectionOrgs, Collections, CollectionIncidentItems, CollectionIncidents,
                    CollectionBusinessItems, CollectionOperationLocks, StorageMovements, Warehouses,
                    CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, FileContents, FileUsers,
                    FlowDefinitions, FlowNodeDefs, FlowEdgeDefs, FlowInstances, FlowTasks, FlowInstanceLogs, FlowCCs
                )).distinct()
                val r = Runtime(c, TransactionTemplate(manager), tables, dir)
                r.db {
                    SchemaUtils.create(*tables.toTypedArray())
                    for (id in listOf("museum", "other")) {
                        CollectionOrgDao.new(id) { name = id; code = id; systemGroupId = "g-$id" }
                        GroupDao.new("g-$id") { name = id; type = '1'; parentId = "0"; parentIds = "0," }
                    }
                    GroupDao.new("dept") { name = "研究部"; type = '2'; parentId = "g-museum"; parentIds = "0,g-museum," }
                    for (id in listOf("operator", "department", "keeper", "director", "researcher",
                        "new-department", "new-keeper", "new-director")) {
                        UserDao.new(id) { name = id; loginName = id; password = "unused"; enabled = true; groupId = "dept" }
                        actorRole(id, "BUSINESS", setOf(CollectionConst.STORAGE, CollectionConst.SELECT))
                    }
                    for (prefix in listOf("", "new-")) {
                        actorRole("${prefix}department", CollectionConst.ROLE_DEPARTMENT_HEAD)
                        actorRole("${prefix}keeper", CollectionConst.ROLE_STORAGE_HEAD)
                        actorRole("${prefix}director", CollectionConst.ROLE_DIRECTOR)
                    }
                    WarehouseDao.new("warehouse") {
                        orgId = "museum"; name = "一库"; sceneType = EnumLocationScene.STORAGE; type = EnumWarehouseType.WAREHOUSE
                    }
                    val owners = mapOf("basis" to "operator", "old-plan" to "operator", "unowned-plan" to "operator",
                        "new-plan" to "new-director", "appointment" to "new-director", "withdrawal" to "new-director")
                    owners.forEach { (id, owner) ->
                        val bytes = "DEV-SYNTHETIC $id original owned by $owner; no real signature".toByteArray()
                        java.nio.file.Files.write(dir.resolve("$id.txt"), bytes)
                        FileDao.new(id) { name = "$id.txt"; path = "$id.txt"; hash = digest(bytes, "MD5"); ext = "txt"; size = bytes.size.toLong(); secure = false }
                        FileUsers.insert { it[fileId] = id; it[userId] = owner; it[uploadDate] = time }
                    }
                    listOf("one", "two").forEach { id -> CollectionDao.new(id) {
                        orgId = "museum"; totalRegNo = id; name = id; lifeStatus = EnumCollectionStatus.IN_STORE;
                        warehouseId = "warehouse"; catalogStatus = EnumCatalogStatus.APPROVED; source = EnumSource.OLD
                    } }
                }
                r.login("operator")
                val model = javaClass.classLoader.getResourceAsStream("flow/${CollectionConst.FLOW_REQUISITION}.json")!!.use {
                    JsonUtils.mapper.readTree(it)
                }.path("model")
                val definitions = c.getBean(FlowDefinitionService::class.java)
                val definition = definitions.create(FlowDefinitionVo(key = CollectionConst.FLOW_REQUISITION, name = "提用",
                    modelData = JsonUtils.toJson(model)), "operator")
                definitions.publish(definition.id.value, "operator")
                val sourceId = r.requisitions.save(RequisitionVo(collectionIds = "one,two", purpose = EnumMoveReason.RESEARCH,
                    basis = "DEV-SYNTHETIC 研究课题", department = "研究部", operator = "operator", attachmentIds = "basis",
                    toWarehouseId = "warehouse", dossier = dossier()))
                r.requisitions.submit(sourceId)
                println("REQUISITION_REPLACEMENT_ORIGINAL_BINDING_PREPARED " + JsonUtils.toJson(mapOf(
                    "ownedRoot" to dir.toAbsolutePath().toString(), "sourceId" to sourceId,
                    "tableCount" to tables.size, "sourceStatus" to r.db { RequisitionDao[sourceId].flowStatus.name }
                )))
                body(r, sourceId)
            }
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    @Test
    fun `responsibility replacement inherits submitted writer basis without granting upload ownership and rolls back new unowned originals`() = fixture { r, sourceId ->
        r.login("new-director")
        val info = r.replacement.get(sourceId)
        assertEquals(true, info["canReplace"])
        val value = RequisitionReplacementRequest(info["expectedInstanceId"] as String, info["expectedSubmittedDigest"] as String,
            "DEV-SYNTHETIC 岗位变动后重新核定责任人", "appointment", "withdrawal",
            dossier().copy(departmentHeadUserId = "new-department", keeperUserId = "new-keeper", leaderUserId = "new-director",
                safetyPlanFileIds = "new-plan"), "new-keeper")
        val oldArchives = r.db { settledSafetySelectionState(CollectionPermanentArchives, CollectionPermanentArchiveFiles, FileContents) }
        val inputBytes = r.bytes()
        val basisOwners = r.db { FileUsers.selectAll().where { FileUsers.fileId eq "basis" }.map { it[FileUsers.userId] }.toSet() }
        assertEquals(setOf("operator"), basisOwners)
        assertTrue(r.db { CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizId eq sourceId) and (CollectionPermanentArchives.phase eq "SUBMITTED")
        }.single().digest == value.expectedSubmittedDigest })
        assertArrayEquals(java.nio.file.Files.readAllBytes(dir.resolve("basis.txt")), r.original("basis"))

        val baseline = r.state()
        val denied = assertThrows(BadRequestException::class.java) {
            r.replacement.replace(sourceId, value.copy(dossier = value.dossier.copy(safetyPlanFileIds = "unowned-plan")))
        }
        assertEquals("无权引用该文件，请勿使用他人的文件", denied.message)
        assertEquals(baseline, r.state(), "New unowned material must roll back cancellation, child, task, lock and all original indexes")
        assertEquals(inputBytes, r.bytes())

        val childId = r.replacement.replace(sourceId, value)
        r.db {
            val source = RequisitionDao[sourceId]; val child = RequisitionDao[childId]
            assertEquals(EnumFlowStatus.CANCEL, source.flowStatus)
            assertEquals("CANCELED", FlowInstanceDao[source.flowInstanceId!!].status)
            assertEquals(childId, source.supersededById)
            assertEquals(sourceId, child.replacesId)
            assertEquals(EnumFlowStatus.DRAFT, child.flowStatus)
            assertNull(child.flowInstanceId)
            assertEquals("new-keeper", child.operator)
            assertEquals("basis", child.attachmentIds)
            assertEquals(value.dossier, child.dossier)
            assertEquals(basisOwners, FileUsers.selectAll().where { FileUsers.fileId eq "basis" }.map { it[FileUsers.userId] }.toSet())
            val all = CollectionPermanentArchiveDao.all().toList()
            assertEquals(1, all.count { it.bizId == sourceId && it.phase == RequisitionReplacementEvidence.REPLACED })
            assertEquals(1, all.count { it.bizId == childId && it.phase == RequisitionReplacementEvidence.CREATED })
            val originals = all.filter { it.bizType == CollectionBusinessOriginalFiles.BIZ && it.bizId == childId }
            assertEquals(2, originals.size, "Child retains basis and its new safety plan through real identity archives")
            val basisIdentity = originals.single { archive ->
                CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.any { it.fileId == "basis" }
            }
            assertEquals(1L, FileContents.selectAll().where {
                (FileContents.contentId eq basisIdentity.id.value) and (FileContents.fileId eq "basis")
            }.count())
            all.forEach { archive -> assertEquals(CollectionCareEvidence.digest(archive.snapshot), archive.digest) }
            val preservedIds = oldArchives.getValue(CollectionPermanentArchives.tableName).map { it.first() }.toSet()
            assertEquals(oldArchives.getValue(CollectionPermanentArchives.tableName), settledSafetySelectionState(CollectionPermanentArchives)
                .getValue(CollectionPermanentArchives.tableName).filter { it.first() in preservedIds })
            val preservedRefIds = oldArchives.getValue(CollectionPermanentArchiveFiles.tableName).map { it.first() }.toSet()
            assertEquals(oldArchives.getValue(CollectionPermanentArchiveFiles.tableName), settledSafetySelectionState(CollectionPermanentArchiveFiles)
                .getValue(CollectionPermanentArchiveFiles.tableName).filter { it.first() in preservedRefIds })
            val currentContents = settledSafetySelectionState(FileContents).getValue(FileContents.tableName)
            assertTrue(currentContents.containsAll(oldArchives.getValue(FileContents.tableName)), "All earlier content bindings remain exact")
        }
        assertEquals(inputBytes, r.bytes())
        r.original("basis")
        r.original("new-plan")
        r.original("appointment")
        r.original("withdrawal")
        val accepted = r.state()
        assertEquals(childId, r.replacement.replace(sourceId, value))
        assertEquals(accepted, r.state(), "Exact repeat must preserve original child, workflow and indexes")
        println("REQUISITION_REPLACEMENT_ORIGINAL_BINDING_RESULT " + JsonUtils.toJson(mapOf(
            "sourceId" to sourceId, "childId" to childId, "ordinaryWriter" to "operator", "ordinaryDirector" to "new-director",
            "childOperator" to "new-keeper", "basisOwnersUnchanged" to basisOwners,
            "realFileBindingAndLocalBytes" to true, "unownedNewMaterialReason" to denied.message,
            "fullRollbackTableCount" to r.tables.size, "sourceArchivesPreserved" to true,
            "sameChildRepeatStable" to true, "originalReads" to 5, "inputByteHashes" to inputBytes
        )))
    }

    @Test
    fun `replacement stage history reads both signed originals without writing identities and rejects wrong refs broken peers and current permission loss`() = fixture { r, sourceId ->
        r.login("new-director")
        val info = r.replacement.get(sourceId)
        val request = RequisitionReplacementRequest(info["expectedInstanceId"] as String, info["expectedSubmittedDigest"] as String,
            "DEV-SYNTHETIC 责任变更历史原件查阅", "appointment", "withdrawal",
            dossier().copy(departmentHeadUserId = "new-department", keeperUserId = "new-keeper", leaderUserId = "new-director",
                safetyPlanFileIds = "new-plan"), "new-keeper")
        val childId = r.replacement.replace(sourceId, request)
        val service = r.c.getBean(CollectionOriginalHistoryService::class.java)
        fun rows(id: String) = service.page(CollectionConst.BIZ_REQUISITION, id, null, 1, 0).list as List<CollectionOriginalHistoryItem>
        val baseline = r.state()
        val expected = setOf("basis.txt", "new-plan.txt", "appointment.txt", "withdrawal.txt")
        val childRows = rows(childId)
        assertEquals(expected, childRows.map { it.name }.toSet(), "Two responsibility stage files must appear beside the unchanged identity entries")
        assertEquals(setOf("basis.txt", "old-plan.txt", "appointment.txt", "withdrawal.txt"), rows(sourceId).map { it.name }.toSet())
        for (id in listOf(sourceId, childId)) for (row in rows(id)) {
            val response = MockHttpServletResponse()
            service.download(row.id, response)
            assertArrayEquals(java.nio.file.Files.readAllBytes(dir.resolve(row.name)), response.contentAsByteArray)
            assertEquals("private, no-store", response.getHeader("Cache-Control"))
            assertEquals("sha256-verified", response.getHeader("X-Collection-Content-Integrity"))
        }
        assertEquals(baseline, r.state(), "History page/download must not append identity/archive/FileContents or alter business rows")
        val appointment = childRows.single { it.name == "appointment.txt" }
        val sourceWithdrawal = rows(sourceId).single { it.name == "withdrawal.txt" }
        assertTrue(appointment.id.matches(Regex("[0-9]{15,20}:[0-9]{15,20}")))
        fun denied(id: String) {
            val response = MockHttpServletResponse()
            assertThrows(BadRequestException::class.java) { service.download(id, response) }
            assertEquals(0, response.contentAsByteArray.size)
            assertNotEquals(304, response.status)
        }
        denied(appointment.id.substringBefore(':') + ":" + sourceWithdrawal.id.substringAfter(':'))
        denied(appointment.id + ":extra")
        val peer = r.db { CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizId eq sourceId) and (CollectionPermanentArchives.phase eq RequisitionReplacementEvidence.REPLACED)
        }.single().id.value }
        val peerSnapshot = r.db { CollectionPermanentArchiveDao[peer].snapshot }
        r.db { CollectionPermanentArchives.update({ CollectionPermanentArchives.id eq peer }) { it[snapshot] = peerSnapshot.replace("责任变更历史原件查阅", "变造") } }
        assertFalse(rows(childId).any { ':' in it.id })
        denied(appointment.id)
        r.db { CollectionPermanentArchives.update({ CollectionPermanentArchives.id eq peer }) { it[snapshot] = peerSnapshot } }
        val permissionRows = r.db { RolePermissions.selectAll().where { RolePermissions.roleId eq "role:BUSINESS" }.map { it[RolePermissions.permissionId] } }
        r.db { RolePermissions.deleteWhere { roleId eq "role:BUSINESS" } }
        assertTrue(rows(childId).isEmpty())
        denied(appointment.id)
        r.db { permissionRows.forEach { permission -> RolePermissions.insert { it[roleId] = org.jetbrains.exposed.v1.core.dao.id.EntityID("role:BUSINESS", Roles); it[permissionId] = permission } } }
        assertEquals(baseline, r.state())
        println("REQUISITION_REPLACEMENT_HISTORY_RESULT " + JsonUtils.toJson(mapOf(
            "sourceId" to sourceId, "childId" to childId, "ordinaryActor" to "new-director", "sourceNames" to rows(sourceId).map { it.name },
            "childNames" to childRows.map { it.name }, "localReads" to 8, "compoundIds" to childRows.filter { ':' in it.id }.map { it.id },
            "noIdentityOrBusinessWrites" to true, "wrongReferenceDenied" to true, "brokenPeerDenied" to true, "currentPermissionLossDenied" to true
        )))
    }

    companion object {
        private fun digest(bytes: ByteArray, algorithm: String) = MessageDigest.getInstance(algorithm).digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
