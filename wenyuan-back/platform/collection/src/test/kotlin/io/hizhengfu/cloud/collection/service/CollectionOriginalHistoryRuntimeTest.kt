package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.common.web.FileViewController
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.file.api.FileFrontController
import io.hizhengfu.cloud.sys.file.api.FilePreviewController
import io.hizhengfu.cloud.sys.file.domain.ParamsFile
import io.hizhengfu.cloud.sys.file.service.*
import io.hizhengfu.cloud.sys.file.service.storage.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.mock.web.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

class CollectionOriginalHistoryRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) @EnableCaching class Transactions
    private val at = LocalDateTime.of(2026, 1, 2, 12, 0)
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate, root: Path) {
        val archives = c.getBean(CollectionPermanentArchiveService::class.java)
        val disclosures = c.getBean(CollectionDisclosureService::class.java)
        val access = c.getBean(FileReadAccess::class.java)
        val controller = FileViewController(c, false, root.toString(), false, "unused", c.getBean(FileObjectStorageService::class.java), access)
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun seal(biz: String, org: String = "museum", id: String = "case", file: String = "proof", phase: String = "RECORDED"): String {
            val previous=SecurityContextHolder.getContext().authentication
            val user=SecurityUser(id="archivist",name="归档人员",username="archivist",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
            try { return archives.seal(org,biz,id,phase,mapOf("fact" to id),file) }
            finally { SecurityContextHolder.getContext().authentication=previous }
        }
        fun permission(vararg values: String) = db {
            RolePermissions.deleteAll()
            actorRole("reader", "FILE_reader", values.toSet())
        }
        fun allow(path: String = "proof.txt") {
            val response = MockHttpServletResponse()
            val request = MockHttpServletRequest("GET", "/userfiles/$path")
            request.addHeader("If-Modified-Since", "Tue, 01 Sep 2026 10:00:00 GMT")
            controller.downloadFile(request, response)
            assertEquals("CONTENT:$path", response.contentAsString)
            assertEquals("private, no-store", response.getHeader("Cache-Control"))
        }
        fun deny(path: String = "proof.txt") {
            for (original in listOf(false, true)) {
                val response = MockHttpServletResponse()
                val request = MockHttpServletRequest("GET", "/userfiles/${if (original) "original/" else ""}$path")
                request.addHeader("Range", "bytes=0-3"); request.addHeader("If-Modified-Since", "Tue, 01 Sep 2026 10:00:00 GMT")
                assertThrows(BadRequestException::class.java) { if (original) controller.downloadOriginalFile(request, response) else controller.downloadFile(request, response) }
                assertEquals(0, response.contentAsByteArray.size); assertNotEquals(304, response.status)
            }
        }
    }
    private fun login(id: String = "reader") {
        val user = SecurityUser(id=id, name=id, username=id, password="", groupId="museum", groupIds=setOf("museum"), postIds=emptySet(), dataType=0,
            roles=setOf(EnumRole.ADMIN.value), roleCodes=setOf(CollectionConst.ROLE_DIRECTOR), authorities=listOf("ADMIN",CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority), enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }
    private fun fixture(body: (Runtime) -> Unit) {
        val manager = SpringTransactionManager(SQLiteDataSource().apply { url="jdbc:sqlite:${dir.resolve("original-history.db")}" }, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("localStorage", LocalFileObjectStorage(dir.toString()))
            c.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("keyGenerator", org.springframework.cache.interceptor.SimpleKeyGenerator())
            c.beanFactory.registerSingleton("scope", CollectionScopeResolver("single", "museum"))
            c.beanFactory.registerSingleton("users", mock(CollectionResponsibleUserService::class.java))
            c.registerBean(FileObjectStorageService::class.java, java.util.function.Supplier { FileObjectStorageService(c.getBean(LocalFileObjectStorage::class.java), mock(FileStorageConfigReader::class.java), mock(RustFsStorageClient::class.java), c.getBean(FileReadAccess::class.java)) })
            c.registerBean(FileService::class.java, java.util.function.Supplier { FileService(dir.toString(), FileFolderService(), c.getBean(FileObjectStorageService::class.java), listOf(CollectionArchiveFileRetentionGuard(), c.getBean(CollectionBusinessOriginalFileRetentionGuard::class.java)), c.getBean(FileReadAccess::class.java)) })
            c.beanFactory.registerSingleton("flowInstances", mock(io.hizhengfu.cloud.flow.service.FlowInstanceService::class.java))
            c.register(CollectionOriginalHistoryService::class.java, io.hizhengfu.cloud.collection.api.CollectionOriginalHistoryController::class.java, WarehouseService::class.java, AcquisitionPlanService::class.java, CollectionBusinessOriginalFileSource::class.java, CollectionBusinessOriginalFiles::class.java, CollectionOriginalIdentityReadPolicy::class.java, CollectionBusinessOriginalFileReadGuard::class.java, CollectionBusinessOriginalFileRetentionGuard::class.java)
            c.register(Transactions::class.java, CollectionActorDirectory::class.java, CollectionFileReadAuthorization::class.java, FileReadAccess::class.java,
                BusinessAttachmentBinder::class.java, CollectionPermanentArchiveService::class.java, CollectionPermanentArchiveFileReadGuard::class.java,
                CollectionBusinessArchiveReadPolicy::class.java, CollectionPersonnelArchiveReadPolicy::class.java, CollectionTransferArchiveReadPolicy::class.java,
                CollectionDisclosureArchiveReadPolicy::class.java, CollectionDisclosureService::class.java, StaffEducationAccess::class.java,
                AcquisitionOriginalFiles::class.java, AcquisitionFileReadGuard::class.java, DigitalAcquisitionFileReadGuard::class.java,
                CollectionEmergencyFileReadGuard::class.java, CollectionStaffEducationFileReadGuard::class.java)
            c.refresh(); SpringContextHolder().setApplicationContext(c)
            val r = Runtime(c, TransactionTemplate(manager), dir)
            r.db {
                originalReaderSchema()
                SchemaUtils.create(*CollectionBusinessOriginalFileSource().tables.map { it.table }.distinct().toTypedArray())
                SchemaUtils.create(IncidentRelocations)
                SchemaUtils.create(CollectionOrgs, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, FileContents, FileUsers,
                    Acquisitions, AcquisitionComplianceCases, AcquisitionExpertOpinions, DigitalAcquisitionFileAccesses, CollectionEmergencyFileAccesses,
                    CollectionStaffEducations, CollectionStaffEducationFileAccesses, CollectionTransfers, CollectionDisclosureReviews, CollectionDisclosureEvents,
                    Collections, CollectionCatalogVersions, Deregistrations, DeregistrationExpertOpinions, DeregistrationNotices, CollectionTransferNotices)
                listOf("museum", "other").forEach { id -> CollectionOrgDao.new(id) { name=id; code=id; systemGroupId=id } }
                originalReader("reader", CollectionConst.SELECT)
                originalReader("archivist", "ADMIN")
                Roles.insert { it[Roles.id]=EnumRole.ADMIN.value; it[code]="ADMIN"; it[name]="管理员" }
                RoleUsers.insert { it[userId]="archivist"; it[roleId]=EnumRole.ADMIN.value }
                for (id in listOf("proof", "public", "review", "replacement")) {
                    FileDao.new(id) { name="$id.txt"; path="$id.txt"; hash=id; ext="txt"; size=32; secure=false }
                    FileUsers.insert { it[fileId]=id; it[userId]="reader"; it[uploadDate]=at }
                    java.nio.file.Files.writeString(dir.resolve("$id.txt"), "CONTENT:$id.txt")
                }
                val collection = CollectionDao.new("one") { orgId="museum"; name="铜器"; totalRegNo="A1"; registeredAt=at.minusDays(10); catalogStatus=EnumCatalogStatus.APPROVED; lifeStatus=EnumCollectionStatus.IN_STORE }
                CollectionCatalogVersionDao.new("v1") { orgId="museum"; catalogId="catalog"; collectionId="one"; version=1; catalogStatus=EnumCatalogStatus.APPROVED; content=CollectionCatalogMapper.fromCollection(collection.toModel()); auditTime=at.minusDays(1) }
            }
            login()
            try { body(r) } finally { SecurityContextHolder.clearContext() }
        }
    }


    private fun history(r:Runtime) = r.c.getBean(CollectionOriginalHistoryService::class.java)
    private fun create(r:Runtime, file:String="proof"): String = r.c.getBean(AcquisitionPlanService::class.java).save(AcquisitionPlanVo(title="征集计划",year=2026,attachmentIds=file))
    private fun rows(r:Runtime, biz:String?=null, id:String?=null, keyword:String?=null): List<CollectionOriginalHistoryItem> =
        history(r).page(biz,id,keyword).list as List<CollectionOriginalHistoryItem>
    private fun denyDownload(r:Runtime,id:String) {
        val response=MockHttpServletResponse()
        assertThrows(BadRequestException::class.java) { history(r).download(id,response) }
        assertEquals(0,response.contentAsByteArray.size);assertNotEquals(304,response.status)
    }
    private fun identity(r:Runtime,biz:String,org:String="museum",id:String="other-case",file:String="proof"):String {
        login("archivist")
        try { return r.archives.seal(org,CollectionBusinessOriginalFiles.BIZ,id,CollectionBusinessOriginalFiles.PHASE+file,
            mapOf("businessType" to biz,"fileId" to file),file) }
        finally { login() }
    }
    @Test fun `permanent metadata batch finds legacy text prefix copies without granting bytes or skipping shared duties`() = fixture {r->
        val hash="0123456789abcdef0123456789abcdef"
        val copy="files/20261004/$hash.json"
        val registered="files-backup/old/$copy"
        java.nio.file.Files.createDirectories(dir.resolve(registered).parent)
        java.nio.file.Files.createDirectories(dir.resolve(copy).parent)
        java.nio.file.Files.writeString(dir.resolve(registered),"ORIGINAL")
        java.nio.file.Files.copy(dir.resolve(registered),dir.resolve(copy))
        r.db {FileDao["proof"].apply {path=registered;this.hash=hash;size=8}}
        identity(r,CollectionConst.BIZ_ACQUISITION_PLAN)
        val guard=r.c.getBean(CollectionPermanentArchiveFileReadGuard::class.java)
        val location=FileMetadataLocation(copy,"LOCAL")
        val other=FileMetadataLocation("images/20261004/$hash.json","LOCAL")
        val remote=FileMetadataLocation(copy,"RUSTFS")
        val locations=listOf(location,other,remote)
        val first=guard.checkMetadataReadableMany(locations)
        assertEquals(true,first.getValue(location).getOrThrow())
        assertEquals(false,first.getValue(other).getOrThrow());assertEquals(false,first.getValue(remote).getOrThrow())
        assertThrows(BadRequestException::class.java) {guard.checkReadable(copy,"LOCAL")}
        identity(r,LedgerReconciliationService.BIZ,id="legacy-copy-finance")
        assertTrue(guard.checkMetadataReadableMany(locations).getValue(location).isFailure)
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_FINANCE)
        assertEquals(true,guard.checkMetadataReadableMany(locations).getValue(location).getOrThrow())
        r.db {CollectionPermanentArchiveFileDao.all().last().metadata="{"}
        assertTrue(guard.checkMetadataReadableMany(locations).values.all {it.isFailure})
    }
    @Test fun `authorized history displays and searches business titles without treating missing titles as identifiers`() = fixture { r ->
        val id=create(r)
        assertEquals("征集计划",rows(r,keyword="征集计划").single().businessLabel)
        assertEquals(id,rows(r,keyword=id).single().businessId)
        assertTrue(rows(r,keyword="不存在的业务名称").isEmpty())
        r.permission()
        assertTrue(rows(r,keyword="征集计划").isEmpty())
        r.permission(CollectionConst.SELECT)
        r.db { AcquisitionPlanDao[id].delete() }
        org.junit.jupiter.api.Assertions.assertNull(rows(r).single().businessLabel)
        assertTrue(rows(r,keyword="征集计划").isEmpty())
        assertEquals("proof.txt",rows(r,keyword="proof.txt").single().name)
    }
    @Test fun `business labels require the same business type and museum including linked collection names`() = fixture { r ->
        r.db {
            val main=CollectionMainOriginalFileSource().businessLabels.single()
            val valid=CollectionOriginalBusinessKey("museum",CollectionMainOriginalFileSource.BIZ,"one")
            val wrongOrg=valid.copy(orgId="other")
            val wrongType=valid.copy(businessType=CollectionConst.BIZ_LOAN)
            assertEquals(mapOf(valid to "A1 · 铜器"),main.read(setOf(valid,wrongOrg,wrongType)))
            AppraisalDao.new("cross-label") {orgId="other";collectionId="one"}
            val appraisal=CollectionBusinessOriginalFileSource().businessLabels.single {it.businessType==CollectionConst.BIZ_APPRAISAL}
            assertTrue(appraisal.read(setOf(CollectionOriginalBusinessKey("other",CollectionConst.BIZ_APPRAISAL,"cross-label"))).isEmpty())
        }
    }
    @Test fun `history includes replaced originals and survives business removal`() = fixture { r ->
        val id=create(r)
        r.c.getBean(AcquisitionPlanService::class.java).save(AcquisitionPlanVo(id=id,title="计划",year=2026,attachmentIds="replacement"))
        r.db { AcquisitionPlanDao[id].delete();FileContents.deleteAll() }
        val list=rows(r,CollectionConst.BIZ_ACQUISITION_PLAN,id)
        assertEquals(setOf("proof.txt","replacement.txt"),list.map { it.name }.toSet())
        assertTrue(list.all { it.availability=="AVAILABLE" })
        list.forEach { item ->
            val response=MockHttpServletResponse();history(r).download(item.id,response)
            assertEquals("CONTENT:${item.name}",response.contentAsString)
            assertEquals("private, no-store",response.getHeader("Cache-Control"));assertEquals("nosniff",response.getHeader("X-Content-Type-Options"))
        }
    }
    @Test fun `page and download use actual current authority instead of stale login`() = fixture { r ->
        create(r);val item=rows(r).single()
        r.permission()
        assertTrue(rows(r).isEmpty());denyDownload(r,item.id)
        r.permission(CollectionConst.SELECT);assertEquals(1,rows(r).size)
    }
    @Test fun `large history reads source metadata in batches without retaining permission results`() = fixture { r ->
        repeat(70) { identity(r,CollectionConst.BIZ_ACQUISITION_PLAN,id="history-$it") }
        var batches=0
        var individualReads=0
        r.c.beanFactory.registerSingleton("batchObserver",object:FileReadMetadataBatchAuthorizationGuard {
            override fun isProtected(path:String,storageType:String?)=false
            override fun requireReadable(path:String,storageType:String?)=Unit
            override fun checkReadable(path:String,storageType:String?)=false
            override fun checkMetadataReadable(path:String,storageType:String?):Boolean { individualReads++;return false }
            override fun checkMetadataReadableMany(locations:List<FileMetadataLocation>):Map<FileMetadataLocation,Result<Boolean>> {
                batches++
                return locations.associateWith {Result.success(false)}
            }
        })
        val page=history(r).page(CollectionConst.BIZ_ACQUISITION_PLAN,null,null,1,20)
        assertEquals(70L,page.total);assertEquals(20,(page.list as List<*>).size)
        assertTrue(batches in 1..8,"来源扫描不应随每条历史记录重复")
        assertEquals(0,individualReads)
        r.permission()
        assertEquals(0L,history(r).page(null,null,null,1,0).total)
        r.permission(CollectionConst.SELECT)
        assertEquals(70,(history(r).page(null,null,null,1,0).list as List<*>).size)
        assertEquals(0,individualReads)
    }
    @Test fun `permission loss after source batch authorization prevents history output`() = fixture { r ->
        create(r)
        var changed=false
        r.c.beanFactory.registerSingleton("latePermissionChange",object:FileReadMetadataBatchAuthorizationGuard {
            override fun isProtected(path:String,storageType:String?)=false
            override fun requireReadable(path:String,storageType:String?)=Unit
            override fun checkReadable(path:String,storageType:String?)=false
            override fun checkMetadataReadable(path:String,storageType:String?)=false
            override fun checkMetadataReadableMany(locations:List<FileMetadataLocation>):Map<FileMetadataLocation,Result<Boolean>> {
                r.db {RolePermissions.deleteAll()};changed=true
                return locations.associateWith {Result.success(false)}
            }
        })
        val error=assertThrows(BadRequestException::class.java) {rows(r)}
        assertTrue(changed)
        assertTrue(error.message.orEmpty().contains("查询期间"))
    }
    @Test fun `multiple location batches cannot output earlier records after the current account is disabled`() = fixture {r ->
        repeat(150) {n ->
            val id="batch-file-$n"
            r.db {FileDao.new(id) {name="$id.txt";path="$id.txt";hash=id;ext="txt";size=8}}
            java.nio.file.Files.writeString(dir.resolve("$id.txt"),"SYNTHETIC-$n")
            identity(r,CollectionConst.BIZ_ACQUISITION_PLAN,id="batch-case-$n",file=id)
        }
        var batches=0
        r.c.beanFactory.registerSingleton("multipleBatchChange",object:FileReadMetadataBatchAuthorizationGuard {
            override fun isProtected(path:String,storageType:String?)=false
            override fun requireReadable(path:String,storageType:String?)=Unit
            override fun checkReadable(path:String,storageType:String?)=false
            override fun checkMetadataReadable(path:String,storageType:String?)=false
            override fun checkMetadataReadableMany(locations:List<FileMetadataLocation>):Map<FileMetadataLocation,Result<Boolean>> {
                batches++
                if(batches==2)r.db {UserDao["reader"].enabled=false}
                return locations.associateWith {Result.success(false)}
            }
        })
        assertThrows(BadRequestException::class.java) {history(r).page(null,null,null,1,20)}
        assertTrue(batches>=2)
        assertEquals(150L,history(r).page(null,null,null,1,0).total)
    }
    @Test fun `batch source denial hides only restricted originals and still protects their download`() = fixture { r ->
        val restricted=create(r,"proof")
        val allowed=create(r,"replacement")
        val restrictedEntry=rows(r,CollectionConst.BIZ_ACQUISITION_PLAN,restricted).single()
        identity(r,LedgerReconciliationService.BIZ,file="proof")
        val page=history(r).page(CollectionConst.BIZ_ACQUISITION_PLAN,null,null,1,0)
        assertEquals(1L,page.total)
        assertEquals(allowed,(page.list as List<CollectionOriginalHistoryItem>).single().businessId)
        denyDownload(r,restrictedEntry.id)
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_FINANCE)
        assertEquals(2L,history(r).page(CollectionConst.BIZ_ACQUISITION_PLAN,null,null,1,0).total)
    }
    @ParameterizedTest @ValueSource(strings=["disabled","locked","anonymous"])
    fun `inactive user cannot query even an empty history`(kind:String) = fixture { r ->
        r.db { when(kind) {
            "disabled" -> UserDao["reader"].enabled=false
            "locked" -> Users.update({Users.id eq "reader"}) {it[accountNonLocked]=false}
            "anonymous" -> SecurityContextHolder.clearContext()
        } }
        assertThrows(BadRequestException::class.java) { rows(r) }
    }
    @Test fun `pagination totals and filename search exclude unauthorized origins`() = fixture { r ->
        create(r,"public")
        identity(r,LedgerReconciliationService.BIZ,file="proof")
        val only=history(r).page(null,null,null,1,1)
        assertEquals(1L,only.total)
        assertEquals("public.txt",(only.list as List<CollectionOriginalHistoryItem>).single().name)
        assertEquals(0L,history(r).page(null,null,"proof").total)
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_FINANCE)
        assertEquals(2L,history(r).page(null,null,null,1,1).total)
        assertEquals(2,(history(r).page(null,null,null,1,0).list as List<*>).size)
        assertEquals(0,(history(r).page(null,null,null,Long.MAX_VALUE,1).list as List<*>).size)
    }
    @Test fun `shared materials retain stricter source rules in both listing and download`() = fixture { r ->
        val id=create(r);val entry=rows(r).single()
        identity(r,LedgerReconciliationService.BIZ)
        assertTrue(rows(r,CollectionConst.BIZ_ACQUISITION_PLAN,id).isEmpty());denyDownload(r,entry.id)
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_FINANCE)
        assertEquals(1,rows(r,CollectionConst.BIZ_ACQUISITION_PLAN,id).size)
    }
    @Test fun `unverified shared original remains visible only with every source duty`() = fixture { r ->
        val id = create(r)
        val entry = rows(r).single()
        identity(r, LedgerReconciliationService.BIZ)
        java.nio.file.Files.move(dir.resolve("proof.txt"), dir.resolve("preserved.txt"))
        java.nio.file.Files.copy(dir.resolve("replacement.txt"), dir.resolve("proof.txt"))
        assertTrue(rows(r, CollectionConst.BIZ_ACQUISITION_PLAN, id).isEmpty())
        r.permission(CollectionConst.SELECT, CollectionConst.LEDGER_FINANCE)
        assertEquals("IDENTITY_UNVERIFIED", rows(r, CollectionConst.BIZ_ACQUISITION_PLAN, id).single().availability)
        denyDownload(r, entry.id)
    }
    @Test fun `history does not require membership in both transfer museums`() = fixture { r ->
        r.db {CollectionTransferDao.new("transfer") {orgId="museum";collectionId="one";toOrgId="other"}}
        identity(r,CollectionConst.BIZ_TRANSFER,id="transfer")
        // 留存阶段带馆别，复用同一业务下两馆的共同原件。
        login("archivist")
        r.archives.seal("other",CollectionBusinessOriginalFiles.BIZ,"transfer",CollectionBusinessOriginalFiles.PHASE+"target",
            mapOf("businessType" to CollectionConst.BIZ_TRANSFER,"fileId" to "proof"),"proof")
        login();r.permission(CollectionConst.LEDGER_CORRECT)
        assertEquals(2,rows(r).size)
        r.db {UserDao["reader"].groupId="other"};assertEquals(2,rows(r).size)
    }
    @ParameterizedTest @ValueSource(strings=["permission","role","account","group","museum"])
    fun `transfer history rejects qualification changes within its query and does not retain download authority`(kind:String) = fixture {r ->
        r.db {CollectionTransferDao.new("transfer") {orgId="museum";collectionId="one";toOrgId="other"}}
        val original=identity(r,CollectionConst.BIZ_TRANSFER,id="transfer")
        assertEquals(1,rows(r,CollectionConst.BIZ_TRANSFER).size)
        var changed=false
        r.c.beanFactory.registerSingleton("transferQualificationChange",object:FileReadMetadataBatchAuthorizationGuard {
            override fun isProtected(path:String,storageType:String?)=false
            override fun requireReadable(path:String,storageType:String?)=Unit
            override fun checkReadable(path:String,storageType:String?)=false
            override fun checkMetadataReadable(path:String,storageType:String?)=false
            override fun checkMetadataReadableMany(locations:List<FileMetadataLocation>):Map<FileMetadataLocation,Result<Boolean>> {
                if(!changed) {
                    changed=true
                    r.db {when(kind) {
                        "permission" -> RolePermissions.deleteAll()
                        "role" -> RoleDao["role:FILE_reader"].enabled=false
                        "account" -> UserDao["reader"].enabled=false
                        // 另一馆也是合法调拨一方；身份变化仍不能混入前一次查询结果。
                        "group" -> UserDao["reader"].groupId="other"
                        "museum" -> CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
                    }}
                }
                return locations.associateWith {Result.success(false)}
            }
        })
        assertThrows(BadRequestException::class.java) {rows(r,CollectionConst.BIZ_TRANSFER)}
        assertTrue(changed)
        assertEquals(1,rows(r,CollectionConst.BIZ_TRANSFER).size,"失败查询应回滚同事务资格变化")
        r.permission()
        assertTrue(rows(r,CollectionConst.BIZ_TRANSFER).isEmpty())
        denyDownload(r,original)
        r.permission(CollectionConst.SELECT)
        assertEquals(1,rows(r,CollectionConst.BIZ_TRANSFER).size)
    }
    @Test fun `renamed original uses saved identity and original download name`() = fixture { r ->
        create(r);val entry=rows(r).single()
        java.nio.file.Files.move(dir.resolve("proof.txt"),dir.resolve("renamed.txt"))
        r.db {FileDao["proof"].path="renamed.txt";FileDao["proof"].name="renamed.txt"}
        val response=MockHttpServletResponse();history(r).download(entry.id,response)
        assertEquals("CONTENT:proof.txt",response.contentAsString)
        assertTrue(response.getHeader("Content-Disposition")!!.contains("proof.txt"))
    }
    @Test fun `changed file row cannot replace the frozen historical original`() = fixture { r ->
        create(r);val entry=rows(r).single()
        r.db {FileDao["proof"].path="replacement.txt"}
        val response=MockHttpServletResponse();history(r).download(entry.id,response)
        assertEquals("CONTENT:proof.txt",response.contentAsString)
    }
    @ParameterizedTest @ValueSource(strings=["missing","replaced"])
    fun `missing or substituted local file is listed for verification and cannot download`(kind:String) = fixture { r ->
        create(r);val entry=rows(r).single()
        java.nio.file.Files.move(dir.resolve("proof.txt"),dir.resolve("preserved.txt"))
        if(kind=="replaced") java.nio.file.Files.copy(dir.resolve("replacement.txt"),dir.resolve("proof.txt"))
        assertEquals("IDENTITY_UNVERIFIED",rows(r).single().availability)
        denyDownload(r,entry.id)
    }
    @ParameterizedTest @ValueSource(strings=["digest","metadata","reference","source"])
    fun `corrupt identity records cannot expose metadata or bytes`(kind:String) = fixture { r ->
        create(r);val entry=rows(r).single()
        r.db {when(kind) {
            "digest" -> CollectionPermanentArchiveDao[entry.id].digest="tampered"
            "metadata" -> CollectionPermanentArchiveFileDao.all().single().metadata="{broken"
            "reference" -> CollectionPermanentArchiveFileDao.all().single().orgId="other"
            "source" -> {
                val a=CollectionPermanentArchiveDao[entry.id];a.snapshot="""{"businessType":"MISSING","fileId":"proof"}""";a.digest=CollectionCareEvidence.digest(a.snapshot)
            }
        }}
        assertTrue(rows(r).isEmpty());denyDownload(r,entry.id)
    }
    @Test fun `read denial still rolls back draft replacement and new identity`() = fixture { r ->
        r.db { AcquisitionPlanDao.new("legacy") {orgId="museum";title="原计划";year=2026;attachmentIds="proof"} }
        r.permission()
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(AcquisitionPlanService::class.java).save(AcquisitionPlanVo(id="legacy",title="修改计划",year=2026,attachmentIds="replacement"))
        }
        r.db { assertEquals("原计划",AcquisitionPlanDao["legacy"].title);assertEquals("proof",AcquisitionPlanDao["legacy"].attachmentIds);assertEquals(0L,CollectionPermanentArchiveDao.count()) }
    }
    @ParameterizedTest @ValueSource(strings=["missing","duplicate","foreign"])
    fun `batched history excludes broken references without replacing them with current files`(kind:String) = fixture {r ->
        val invalidPlan=create(r,"proof")
        val validPlan=create(r,"replacement")
        val invalid=rows(r,CollectionConst.BIZ_ACQUISITION_PLAN,invalidPlan).single()
        r.db {
            val file=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq invalid.id}.single()
            when(kind) {
                "missing" -> file.delete()
                "foreign" -> file.orgId="other"
                "duplicate" -> CollectionPermanentArchiveFileDao.new("duplicate-reference") {
                    archiveId=file.archiveId;orgId=file.orgId;bizId=file.bizId;fileId="review";metadata=file.metadata
                }
            }
        }
        assertTrue(rows(r,CollectionConst.BIZ_ACQUISITION_PLAN,invalidPlan).isEmpty())
        denyDownload(r,invalid.id)
        val page=history(r).page(CollectionConst.BIZ_ACQUISITION_PLAN,null,null,1,0)
        assertEquals(1L,page.total)
        assertEquals(validPlan,(page.list as List<CollectionOriginalHistoryItem>).single().businessId)
        val response=MockHttpServletResponse()
        history(r).download((page.list as List<CollectionOriginalHistoryItem>).single().id,response)
        assertEquals("CONTENT:replacement.txt",response.contentAsString)
    }
    @Test fun `history still requires valid device transition when native identity cannot directly match`() = fixture {r ->
        create(r)
        val original=rows(r).single()
        val oldKey="n"+"0".repeat(63)
        val observed=FileReadPath.keys("proof.txt","LOCAL",r.c.getBean(LocalFileObjectStorage::class.java))
        val stable=observed.single {it.startsWith("u")}
        val baseline=r.db {
            SchemaUtils.create(CollectionPermanentOriginalTransitions)
            val archive=CollectionPermanentArchiveDao[original.id]
            val reference=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq original.id}.single()
            val metadata=JsonUtils.mapper.readTree(reference.metadata)
            val content=requireNotNull(CollectionArchiveFileIdentity.content(metadata))
            val keys=CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(archive.id.value,archive.orgId,
                archive.bizType,archive.bizId,archive.phase,reference.fileId,reference.metadata),metadata)
            reference.metadata=CollectionArchiveFileIdentity.metadata(FileDao["proof"],archive.orgId,archive.bizType,
                archive.bizId,keys.filterNot {it.startsWith("n") || it.startsWith("u")}.toSet()+oldKey,content)
            content
        }
        assertEquals("IDENTITY_UNVERIFIED",rows(r).single().availability)
        denyDownload(r,original.id)
        val evidence=CollectionPermanentOriginalTransitionRegistry.Evidence("proof","museum",oldKey,stable,
            baseline.sha256,baseline.size,"former","current","synthetic",1,"开发合成逐文件承接","reader","")
        r.db {CollectionPermanentOriginalTransitionDao.new("proof") {
            fileId=evidence.fileId;orgId=evidence.orgId;frozenNativeKey=evidence.frozenNativeKey;stableKey=evidence.stableKey
            contentSha256=evidence.contentSha256;contentSize=evidence.contentSize;formerDevice=evidence.formerDevice
            currentDevice=evidence.currentDevice;inode=evidence.inode;birthMillis=evidence.birthMillis;reason=evidence.reason
            evidenceDigest=CollectionPermanentOriginalTransitionRegistry.seal(evidence)
        }}
        assertEquals("AVAILABLE",rows(r).single().availability)
        val response=MockHttpServletResponse();history(r).download(original.id,response)
        assertEquals("CONTENT:proof.txt",response.contentAsString)
        r.db {CollectionPermanentOriginalTransitionDao["proof"].stableKey="u"+"f".repeat(63)}
        assertEquals("IDENTITY_UNVERIFIED",rows(r).single().availability)
        denyDownload(r,original.id)
    }
    @Test fun `history API metadata is private and does not expose stored paths`() = fixture { r ->
        create(r)
        val response=MockHttpServletResponse()
        val result=r.c.getBean(io.hizhengfu.cloud.collection.api.CollectionOriginalHistoryController::class.java).page(null,null,null,1,20,response)
        assertEquals("private, no-store",response.getHeader("Cache-Control"))
        val json=JsonUtils.toJson(result)!!
        assertTrue(json.contains("orgName"));assertFalse(json.contains("readKeys"));assertFalse(json.contains("storageType"));assertFalse(json.contains("metadata"))
    }

    @Test fun `new original records actual bytes not upload hash or declared size`() = fixture { r ->
        create(r)
        val entry=rows(r).single()
        assertEquals("SHA256_RECORDED",entry.contentIntegrity)
        r.db {
            val metadata=JsonUtils.mapper.readTree(CollectionPermanentArchiveFileDao.all().single().metadata)
            assertEquals(CollectionOriginalContent.read("CONTENT:proof.txt".byteInputStream()),CollectionArchiveFileIdentity.content(metadata))
            assertNotEquals(FileDao["proof"].hash,metadata.path("_archiveContent").path("sha256").asString())
        }
        val response=MockHttpServletResponse();history(r).download(entry.id,response)
        assertEquals("CONTENT:proof.txt",response.contentAsString)
        assertEquals("sha256-verified",response.getHeader("X-Collection-Content-Integrity"))
    }
    @Test fun `in place same length rewrite with restored timestamp cannot download`() = fixture { r ->
        create(r);val entry=rows(r).single();val path=dir.resolve("proof.txt")
        val local=r.c.getBean(LocalFileObjectStorage::class.java)
        val identity=local.nativeReadIdentity("proof.txt")
        val timestamp=java.nio.file.Files.getLastModifiedTime(path)
        java.nio.file.Files.writeString(path,"CHANGED:proof.txt")
        java.nio.file.Files.setLastModifiedTime(path,timestamp)
        assertEquals(identity,local.nativeReadIdentity("proof.txt"))
        assertEquals("AVAILABLE",rows(r).single().availability)
        denyDownload(r,entry.id)
    }
    @Test fun `truncated original cannot download`() = fixture { r ->
        create(r);val entry=rows(r).single()
        java.nio.file.Files.writeString(dir.resolve("proof.txt"),"")
        denyDownload(r,entry.id)
    }
    @Test fun `idempotent archive does not accept rewritten original or replace baseline`() = fixture { r ->
        val id=create(r)
        val before=r.db { CollectionPermanentArchiveFileDao.all().single().metadata }
        java.nio.file.Files.writeString(dir.resolve("proof.txt"),"CHANGED:proof.txt")
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(AcquisitionPlanService::class.java).save(AcquisitionPlanVo(id=id,title="不能写入",year=2026,attachmentIds="proof"))
        }
        r.db {
            assertEquals(before,CollectionPermanentArchiveFileDao.all().single().metadata)
            assertEquals("征集计划",AcquisitionPlanDao[id].title)
            assertEquals(1L,CollectionPermanentArchiveDao.count())
        }
    }
    @ParameterizedTest @ValueSource(strings=["hash","size","algorithm","remove","null","fraction","overflow"])
    fun `modified or downgraded content baseline is rejected`(kind:String) = fixture { r ->
        create(r);val entry=rows(r).single()
        r.db {
            val file=CollectionPermanentArchiveFileDao.all().single()
            val metadata=JsonUtils.mapper.readTree(file.metadata) as tools.jackson.databind.node.ObjectNode
            val content=metadata.path("_archiveContent") as tools.jackson.databind.node.ObjectNode
            when(kind) {
                "hash" -> content.put("sha256","0".repeat(64))
                "size" -> content.put("size",0)
                "algorithm" -> content.put("algorithm","MD5")
                "remove" -> metadata.remove("_archiveContent")
                "null" -> metadata.putNull("_archiveContent")
                "fraction" -> content.put("size",1.5)
                "overflow" -> content.set("size",JsonUtils.mapper.readTree("9223372036854775808"))
            }
            file.metadata=metadata.toString()
        }
        assertTrue(rows(r).isEmpty());denyDownload(r,entry.id)
    }
    @Test fun `legacy identity remains unverified and is never backfilled during reading`() = fixture { r ->
        create(r);val entry=rows(r).single()
        val legacy=r.db {
            val file=CollectionPermanentArchiveFileDao.all().single()
            val archive=CollectionPermanentArchiveDao[file.archiveId]
            val metadata=JsonUtils.mapper.readTree(file.metadata)
            file.metadata=CollectionArchiveFileIdentity.metadata(FileDao[file.fileId],archive.orgId,archive.bizType,archive.bizId,CollectionArchiveFileIdentity.candidates(metadata))
            file.metadata
        }
        assertEquals("NOT_RECORDED",rows(r).single().contentIntegrity)
        val response=MockHttpServletResponse();history(r).download(entry.id,response)
        assertEquals("not-recorded",response.getHeader("X-Collection-Content-Integrity"))
        r.db { assertEquals(legacy,CollectionPermanentArchiveFileDao.all().single().metadata) }
    }
    @Test fun `permission revoked while verified copy is prepared prevents all response bytes`() = fixture { r ->
        create(r);val entry=rows(r).single()
        val delegate=r.c.getBean(FileObjectStorageService::class.java)
        val changingStorage=object: FileObjectStorage by delegate {
            override fun openStream(path:String,storageType:String?,rangeStart:Long?,rangeEndInclusive:Long?):java.io.InputStream {
                val input=delegate.openStream(path,storageType,rangeStart,rangeEndInclusive)
                return object:java.io.FilterInputStream(input) {
                    override fun read(b:ByteArray,off:Int,len:Int):Int {
                        val count=super.read(b,off,len)
                        if(count<0) r.permission()
                        return count
                    }
                }
            }
        }
        val service=CollectionOriginalHistoryService(r.c.getBean(CollectionActorDirectory::class.java),r.c.getBean(CollectionOriginalIdentityReadPolicy::class.java),r.access,changingStorage,requireNotNull(r.tx.transactionManager),r.c.getBean(LocalFileObjectStorage::class.java))
        val response=MockHttpServletResponse()
        assertThrows(BadRequestException::class.java) { r.db { service.download(entry.id,response) } }
        assertEquals(0,response.contentAsByteArray.size)
        org.junit.jupiter.api.Assertions.assertNull(response.getHeader("X-Collection-Content-Integrity"))
    }

    @Test fun `source changed after staging cannot replace verified response bytes`() = fixture { r ->
        create(r);val entry=rows(r).single()
        val delegate=r.c.getBean(FileObjectStorageService::class.java)
        val changingStorage=object: FileObjectStorage by delegate {
            override fun openStream(path:String,storageType:String?,rangeStart:Long?,rangeEndInclusive:Long?):java.io.InputStream {
                return object:java.io.FilterInputStream(delegate.openStream(path,storageType,rangeStart,rangeEndInclusive)) {
                    override fun read(b:ByteArray,off:Int,len:Int):Int {
                        val count=super.read(b,off,len)
                        if(count<0) java.nio.file.Files.writeString(dir.resolve("proof.txt"),"CHANGED:proof.txt")
                        return count
                    }
                }
            }
        }
        val service=CollectionOriginalHistoryService(r.c.getBean(CollectionActorDirectory::class.java),r.c.getBean(CollectionOriginalIdentityReadPolicy::class.java),r.access,changingStorage,requireNotNull(r.tx.transactionManager),r.c.getBean(LocalFileObjectStorage::class.java))
        val response=MockHttpServletResponse()
        r.db { service.download(entry.id,response) }
        assertEquals("CONTENT:proof.txt",response.contentAsString)
        assertEquals("sha256-verified",response.getHeader("X-Collection-Content-Integrity"))
        denyDownload(r,entry.id)
    }
    @Test fun `download suspends caller transaction during IO and preserves its later rollback`() = fixture { r ->
        create(r); val entry=rows(r).single()
        val delegate=r.c.getBean(FileObjectStorageService::class.java)
        var opened=false
        var output=false
        fun noTransaction() {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            org.junit.jupiter.api.Assertions.assertNull(org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.currentOrNull())
        }
        val storage=object:FileObjectStorage by delegate {
            override fun openStream(path:String,storageType:String?,rangeStart:Long?,rangeEndInclusive:Long?):java.io.InputStream {
                noTransaction();opened=true
                return delegate.openStream(path,storageType,rangeStart,rangeEndInclusive)
            }
        }
        val service=CollectionOriginalHistoryService(r.c.getBean(CollectionActorDirectory::class.java),r.c.getBean(CollectionOriginalIdentityReadPolicy::class.java),
            r.access,storage,requireNotNull(r.tx.transactionManager),r.c.getBean(LocalFileObjectStorage::class.java))
        val response=object:MockHttpServletResponse() {
            override fun getOutputStream():jakarta.servlet.ServletOutputStream {
                noTransaction();output=true
                return super.getOutputStream()
            }
        }
        val failure=assertThrows(IllegalStateException::class.java) {
            r.db {
                Files.update({Files.id eq "public"}) {it[Files.name]="调用者未提交修改"}
                service.download(entry.id,response)
                assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                assertEquals("调用者未提交修改",FileDao["public"].name)
                throw IllegalStateException("rollback caller")
            }
        }
        assertEquals("rollback caller",failure.message)
        assertTrue(opened);assertTrue(output)
        assertEquals("CONTENT:proof.txt",response.contentAsString)
        r.db { assertEquals("public.txt",FileDao["public"].name) }
    }

    @Test fun `response failure still closes original stream and restores caller transaction`() = fixture { r ->
        create(r); val entry=rows(r).single()
        val delegate=r.c.getBean(FileObjectStorageService::class.java)
        var closed=false
        val storage=object:FileObjectStorage by delegate {
            override fun openStream(path:String,storageType:String?,rangeStart:Long?,rangeEndInclusive:Long?):java.io.InputStream =
                object:java.io.FilterInputStream(delegate.openStream(path,storageType,rangeStart,rangeEndInclusive)) {
                    override fun close() { closed=true;super.close() }
                }
        }
        val service=CollectionOriginalHistoryService(r.c.getBean(CollectionActorDirectory::class.java),r.c.getBean(CollectionOriginalIdentityReadPolicy::class.java),
            r.access,storage,requireNotNull(r.tx.transactionManager),r.c.getBean(LocalFileObjectStorage::class.java))
        val response=object:MockHttpServletResponse() {
            override fun getOutputStream():jakarta.servlet.ServletOutputStream = throw java.io.IOException("synthetic client disconnect")
        }
        r.db {
            val failure=assertThrows(java.io.IOException::class.java) { service.download(entry.id,response) }
            assertEquals("synthetic client disconnect",failure.message)
            assertTrue(closed)
            assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            assertEquals("proof.txt",FileDao["proof"].name)
        }
    }

    @Test fun `legacy remote location is not presented as recorded byte verification`() = fixture { r ->
        r.db {
            val file=FileDao["proof"];file.storageType=EnumFileStorageType.RUSTFS;file.path="/rustfs/bucket/proof.txt"
            val record=CollectionPermanentArchiveDao.new {
                orgId="museum";bizType=CollectionBusinessOriginalFiles.BIZ;bizId="legacy-remote";phase=CollectionBusinessOriginalFiles.PHASE+"legacy"
                snapshot=JsonUtils.toJson(mapOf("businessType" to CollectionConst.BIZ_ACQUISITION_PLAN,"fileId" to "proof"))!!
                digest=CollectionCareEvidence.digest(snapshot)
            }
            CollectionPermanentArchiveFileDao.new {
                orgId="museum";archiveId=record.id.value;bizId=record.bizId;fileId="proof"
                metadata=CollectionArchiveFileIdentity.metadata(file,record.orgId,record.bizType,record.bizId,setOf(FileReadPath.key(file.path,"RUSTFS")))
            }
        }
        val entry=rows(r).single()
        assertEquals("RECORDED_LOCATION",entry.availability)
        assertEquals("NOT_RECORDED",entry.contentIntegrity)
        r.db { org.junit.jupiter.api.Assertions.assertNull(CollectionArchiveFileIdentity.content(JsonUtils.mapper.readTree(CollectionPermanentArchiveFileDao.all().single().metadata))) }
    }
}
