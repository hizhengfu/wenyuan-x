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
import java.security.MessageDigest
import java.time.LocalDateTime

class CollectionRemoteOriginalRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) @EnableCaching class Transactions
    private val at = LocalDateTime.of(2026, 1, 2, 12, 0)
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate, root: Path, val remote: RemoteStore) {
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
    private fun fixture(wal: Boolean = false, body: (Runtime) -> Unit) {
        RemoteStore().use { remote ->
        val source = SQLiteDataSource().apply { url="jdbc:sqlite:${dir.resolve("remote-original.db")}" }
        if (wal) source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA journal_mode=WAL").use { result ->
                    assertTrue(result.next()); assertEquals("wal", result.getString(1))
                }
            }
        }
        val manager = SpringTransactionManager(source, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("localStorage", LocalFileObjectStorage(dir.toString()))
            c.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("keyGenerator", org.springframework.cache.interceptor.SimpleKeyGenerator())
            c.beanFactory.registerSingleton("scope", CollectionScopeResolver("single", "museum"))
            c.beanFactory.registerSingleton("users", mock(CollectionResponsibleUserService::class.java))
            c.registerBean(RustFsStorageClient::class.java, java.util.function.Supplier { RustFsStorageClient(remote.config) })
            c.registerBean(FileObjectStorageService::class.java, java.util.function.Supplier { FileObjectStorageService(c.getBean(LocalFileObjectStorage::class.java), remote.config, c.getBean(RustFsStorageClient::class.java), c.getBean(FileReadAccess::class.java)) })
            c.registerBean(FileService::class.java, java.util.function.Supplier { FileService(dir.toString(), FileFolderService(), c.getBean(FileObjectStorageService::class.java), listOf(CollectionArchiveFileRetentionGuard(), c.getBean(CollectionBusinessOriginalFileRetentionGuard::class.java)), c.getBean(FileReadAccess::class.java)) })
            c.beanFactory.registerSingleton("flowInstances", mock(io.hizhengfu.cloud.flow.service.FlowInstanceService::class.java))
            c.register(CollectionOriginalHistoryService::class.java, io.hizhengfu.cloud.collection.api.CollectionOriginalHistoryController::class.java, WarehouseService::class.java, AcquisitionPlanService::class.java, CollectionBusinessOriginalFileSource::class.java, CollectionBusinessOriginalFiles::class.java, CollectionOriginalIdentityReadPolicy::class.java, CollectionBusinessOriginalFileReadGuard::class.java, CollectionBusinessOriginalFileRetentionGuard::class.java)
            c.register(Transactions::class.java, CollectionActorDirectory::class.java, CollectionFileReadAuthorization::class.java, FileReadAccess::class.java,
                BusinessAttachmentBinder::class.java, CollectionPermanentArchiveService::class.java, CollectionPermanentArchiveFileReadGuard::class.java,
                CollectionBusinessArchiveReadPolicy::class.java, CollectionPersonnelArchiveReadPolicy::class.java, CollectionTransferArchiveReadPolicy::class.java,
                CollectionDisclosureArchiveReadPolicy::class.java, CollectionDisclosureService::class.java, StaffEducationAccess::class.java,
                AcquisitionOriginalFiles::class.java, AcquisitionFileReadGuard::class.java, DigitalAcquisitionFileReadGuard::class.java,
                CollectionEmergencyFileReadGuard::class.java, CollectionStaffEducationFileReadGuard::class.java,
                CollectionPermanentRemoteRecoveryService::class.java)
            c.refresh(); SpringContextHolder().setApplicationContext(c)
            val r = Runtime(c, TransactionTemplate(manager), dir, remote)
            r.db {
                originalReaderSchema()
                SchemaUtils.create(*CollectionBusinessOriginalFileSource().tables.map { it.table }.distinct().toTypedArray())
                SchemaUtils.create(IncidentRelocations)
                SchemaUtils.create(CollectionOrgs, CollectionPermanentArchives, CollectionPermanentArchiveFiles,
                    CollectionPermanentRemoteRecoveries, Files, FileContents, FileUsers,
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
                FileDao["proof"].storageType=EnumFileStorageType.RUSTFS; FileDao["proof"].path="/docs/proof.txt"
                val collection = CollectionDao.new("one") { orgId="museum"; name="铜器"; totalRegNo="A1"; registeredAt=at.minusDays(10); catalogStatus=EnumCatalogStatus.APPROVED; lifeStatus=EnumCollectionStatus.IN_STORE }
                CollectionCatalogVersionDao.new("v1") { orgId="museum"; catalogId="catalog"; collectionId="one"; version=1; catalogStatus=EnumCatalogStatus.APPROVED; content=CollectionCatalogMapper.fromCollection(collection.toModel()); auditTime=at.minusDays(1) }
            }
            login()
            try { body(r) } finally { SecurityContextHolder.clearContext() }
        }
        }
    }



    private data class Request(val method:String,val version:String?,val ifMatch:String?,val range:String?)
    private class RemoteStore:AutoCloseable {
        val server=com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1",0),0)
        val config=mock(FileStorageConfigReader::class.java)
        @Volatile var settings=FileStorageSettings(EnumFileStorageType.RUSTFS,"http://127.0.0.1:${server.address.port}","test-access","test-secret","originals","us-east-1",null,false)
        val data=java.util.concurrent.ConcurrentHashMap<String,ByteArray>().apply { put("v1","remote-original-v1".toByteArray());put("v2","remote-original-v2".toByteArray()) }
        val requests=java.util.concurrent.CopyOnWriteArrayList<Request>()
        @Volatile var latest="v1"
        @Volatile var versionMode="normal"
        @Volatile var omitETag=false
        @Volatile var deletedLatest=false
        @Volatile var denyVersion=false
        @Volatile var wrongResponseVersion:String?=null
        @Volatile var afterInitialHead:(()->Unit)?=null
        @Volatile var beforeHead:(()->Unit)?=null
        @Volatile var beforeGetHeaders:(()->Unit)?=null
        @Volatile var beforeGetBody:(()->Unit)?=null
        init {
            org.mockito.Mockito.`when`(config.settings()).thenAnswer { settings }
            server.createContext("/") { exchange ->
                try { respond(exchange) } finally { exchange.close() }
            }
            server.start()
        }
        private fun respond(exchange:com.sun.net.httpserver.HttpExchange) {
            val requested=exchange.requestURI.rawQuery?.split('&')?.map { it.split('=',limit=2) }?.firstOrNull { it.first()=="versionId" }?.getOrNull(1)?.let { java.net.URLDecoder.decode(it,Charsets.UTF_8) }
            val match=exchange.requestHeaders.getFirst("If-Match")
            requests+=Request(exchange.requestMethod,requested,match,exchange.requestHeaders.getFirst("Range"))
            fun error(status:Int,code:String) {
                val bytes="<Error><Code>$code</Code><Message>fixture object unavailable</Message></Error>".toByteArray()
                exchange.responseHeaders.set("Content-Type","application/xml")
                exchange.sendResponseHeaders(status,if(exchange.requestMethod=="HEAD")-1 else bytes.size.toLong())
                if(exchange.requestMethod!="HEAD")exchange.responseBody.write(bytes)
            }
            if(exchange.requestURI.path!="/${settings.rustFsBucket}/docs/proof.txt"){error(404,"NoSuchKey");return}
            if(requested!=null&&denyVersion){error(403,"AccessDenied");return}
            if(requested==null&&deletedLatest){error(404,"NoSuchKey");return}
            val selected=requested?:latest
            val body=data[selected]?:run {error(404,"NoSuchVersion");return}
            val tag="\"etag-$selected\""
            if(match!=null&&match!=tag){error(412,"PreconditionFailed");return}
            if(!omitETag)exchange.responseHeaders.set("ETag",tag)
            val responseVersion=wrongResponseVersion?:when(versionMode){"none"->null;"null"->"null";else->selected}
            responseVersion?.let {exchange.responseHeaders.set("x-amz-version-id",it)}
            exchange.responseHeaders.set("Last-Modified","Tue, 01 Sep 2026 10:00:00 GMT")
            exchange.responseHeaders.set("Content-Type","application/octet-stream")
            exchange.responseHeaders.set("Content-Length",body.size.toString())
            if(exchange.requestMethod=="HEAD") {
                beforeHead?.also { beforeHead=null;it() }
                exchange.sendResponseHeaders(200,-1)
                if(requested==null)afterInitialHead?.also {afterInitialHead=null;it()}
            }else{
                beforeGetHeaders?.also { beforeGetHeaders=null; it() }
                exchange.sendResponseHeaders(200,body.size.toLong())
                beforeGetBody?.also { beforeGetBody=null; it() }
                exchange.responseBody.write(body)
            }
        }
        override fun close(){server.stop(0)}
    }
    private fun create(r:Runtime)=r.c.getBean(AcquisitionPlanService::class.java).save(AcquisitionPlanVo(title="远端原件",year=2026,attachmentIds="proof"))
    private fun recoveryRequest(r:Runtime, version:String):CollectionPermanentRemoteRecoveryRequest {
        val (ids, archives)=r.db {
            CollectionPermanentArchiveFileDao.all().map { it.id.value }.sorted() to CollectionPermanentArchiveDao.count()
        }
        val inventory="a".repeat(64)
        val count=ids.size
        val report=JsonUtils.toJson(mapOf(
            "format" to "collection-restored-content-check-v3",
            "result" to "ALL_CONTENT_REFERENCES_AND_ARCHIVES_MATCH",
            "inventorySha256" to inventory,
            "expectedReferences" to count,
            "results" to ids.map { mapOf("referenceId" to it,"status" to "CONTENT_MATCH") },
            "counts" to mapOf("CONTENT_MATCH" to count),
            "restoredReferences" to mapOf("result" to "MATCH","independentReferenceCount" to count,
                "counts" to mapOf("REFERENCE_MATCH" to count)),
            "restoredArchives" to mapOf("result" to "MATCH","expectedArchives" to archives,
                "independentRestoredArchiveCount" to archives,
                "counts" to mapOf("ARCHIVE_MATCH" to archives),
                "sourceReferenceLinks" to mapOf("result" to "MATCH"),
                "restoredReferenceLinks" to mapOf("result" to "MATCH")),
        ))!!
        val digest=MessageDigest.getInstance("SHA-256").digest(report.toByteArray()).joinToString("") { "%02x".format(it) }
        return CollectionPermanentRemoteRecoveryRequest("proof",version,"\"etag-$version\"",
            inventory,"b".repeat(64),"c".repeat(64),digest,report,
            "开发环境中根据独立备份清单与逐条内容比对结果登记新的远端对象版本")
    }
    private fun history(r:Runtime)=r.c.getBean(CollectionOriginalHistoryService::class.java)
    private fun item(r:Runtime)=(history(r).page(null,null,null).list as List<CollectionOriginalHistoryItem>).single()
    private fun downloaded(r:Runtime,id:String):MockHttpServletResponse=MockHttpServletResponse().also {history(r).download(id,it)}
    private fun deny(r:Runtime,id:String) {
        val response=MockHttpServletResponse();assertThrows(BadRequestException::class.java){history(r).download(id,response)}
        assertEquals(0,response.contentAsByteArray.size)
    }
    @ParameterizedTest @ValueSource(strings=["headers", "body"])
    fun `legacy SDK read rechecks committed permission changes while acquiring or copying remote object`(stage:String)=fixture {r->
        create(r)
        r.db {
            // 完整旧版文件登记，不补造摘要或远端版本；保留已有业务索引。
            CollectionPermanentArchiveFileDao.all().single().metadata=JsonUtils.mapper.writeValueAsString(FileDao["proof"].toModel())
        }
        val old=item(r)
        assertEquals("NOT_RECORDED",old.contentIntegrity)
        assertEquals("not-recorded",downloaded(r,old.id).getHeader("X-Collection-Content-Integrity"))
        org.junit.jupiter.api.Assertions.assertNull(r.access.contentBaseline("/docs/proof.txt","RUSTFS"))
        val mutation=java.util.concurrent.atomic.AtomicBoolean(false)
        val change:()->Unit={r.permission();mutation.set(true)}
        if(stage=="headers") r.remote.beforeGetHeaders=change else r.remote.beforeGetBody=change
        val response=MockHttpServletResponse()
        var converted=false
        if(stage=="headers") {
            r.controller.downloadFile(MockHttpServletRequest("GET","/userfiles/rustfs/docs/proof.txt"),response)
            assertEquals(403,response.status)
        } else assertThrows(BadRequestException::class.java) {
            r.c.getBean(FileObjectStorageService::class.java).withTempFile("/docs/proof.txt","RUSTFS") { converted=true }
        }
        assertTrue(mutation.get(),"独立 HTTP 服务线程已提交当前角色权限变更")
        assertEquals(0,response.contentAsByteArray.size);assertFalse(converted)
    }
    @ParameterizedTest
    @CsvSource("legacy,headers,permission", "legacy,headers,disabled", "legacy,headers,metadata", "legacy,headers,reference",
        "verified,headers,permission", "verified,headers,disabled", "verified,body,permission", "verified,body,disabled",
        "outer,headers,permission")
    fun `history download sees independent commits instead of retaining a database snapshot across remote IO`(format:String, stage:String, change:String)=fixture(wal=true) {r->
        create(r)
        if(format=="legacy") r.db {
            CollectionPermanentArchiveFileDao.all().single().metadata=JsonUtils.mapper.writeValueAsString(FileDao["proof"].toModel())
        }
        val archive=item(r)
        val mutation=java.util.concurrent.atomic.AtomicBoolean(false)
        val mutate:()->Unit={
            when(change) {
                "permission" -> r.permission()
                "disabled" -> r.db { UserDao["reader"].enabled=false }
                "metadata" -> r.db {
                    val row=CollectionPermanentArchiveFileDao.all().single()
                    val value=JsonUtils.mapper.readTree(row.metadata) as tools.jackson.databind.node.ObjectNode
                    value.put("name","changed-retained-name.txt"); row.metadata=value.toString()
                }
                "reference" -> r.db { CollectionPermanentArchiveFileDao.all().single().delete() }
            }
            mutation.set(true)
        }
        if(stage=="headers") r.remote.beforeGetHeaders=mutate else r.remote.beforeGetBody=mutate
        val response=MockHttpServletResponse()
        assertThrows(BadRequestException::class.java) {
            if(format=="outer") r.db {
                // 建立调用者自己的旧快照；下载不能继承它执行最终查阅复验。
                assertEquals(true,UserDao["reader"].enabled)
                history(r).download(archive.id,response)
            } else history(r).download(archive.id,response)
        }
        assertTrue(mutation.get(),"权限或登记变更必须由独立线程事务提交成功")
        assertEquals(0,response.contentAsByteArray.size)
        org.junit.jupiter.api.Assertions.assertNull(response.getHeader("X-Collection-Content-Integrity"))
    }
    @ParameterizedTest @ValueSource(strings=["metadata", "exists", "version"])
    fun `SDK head rejects permission revoked before response`(operation:String)=fixture {r->
        create(r)
        val storage=r.c.getBean(FileObjectStorageService::class.java)
        val revision=r.access.objectRevision("/docs/proof.txt","RUSTFS")!!
        val committed=java.util.concurrent.atomic.AtomicBoolean(false)
        r.remote.beforeHead={r.permission();committed.set(true)}
        assertThrows(BadRequestException::class.java) {
            when(operation) {
                "metadata" -> storage.metadata("/docs/proof.txt","RUSTFS")
                "exists" -> storage.exists("/docs/proof.txt","RUSTFS")
                else -> storage.metadataAtRevision("/docs/proof.txt","RUSTFS",revision)
            }
        }
        assertTrue(committed.get())
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `SDK conditional download cannot reuse public cache after new business protection`(original:Boolean)=fixture {r->
        val committed=java.util.concurrent.atomic.AtomicBoolean(false)
        r.remote.beforeHead={
            r.db { AcquisitionPlanDao.new("late-protected") { orgId="museum";title="旧业务补录";year=2026;attachmentIds="proof" } }
            committed.set(true)
        }
        val request=MockHttpServletRequest("GET","/userfiles/${if(original) "original/" else ""}rustfs/docs/proof.txt")
        request.addHeader("If-Modified-Since","Tue, 01 Sep 2026 10:00:00 GMT")
        val response=MockHttpServletResponse()
        if(original) r.controller.downloadOriginalFile(request,response) else r.controller.downloadFile(request,response)
        assertTrue(committed.get());assertEquals(403,response.status)
        assertEquals("private, no-store",response.getHeader("Cache-Control"))
        assertEquals(0,response.contentAsByteArray.size)
    }
    @Test fun `SDK archive captures pinned revision and actual remote content`()=fixture {r->
        create(r);val item=item(r)
        assertEquals("REMOTE_VERSION_RECORDED",item.availability);assertEquals("SHA256_RECORDED",item.contentIntegrity)
        r.db {
            val meta=JsonUtils.mapper.readTree(CollectionPermanentArchiveFileDao.all().single().metadata)
            assertEquals("v1",CollectionArchiveFileIdentity.revision(meta)!!.versionId)
            assertEquals(CollectionOriginalContent.read("remote-original-v1".byteInputStream()),CollectionArchiveFileIdentity.content(meta))
            assertNotEquals(FileDao["proof"].hash,CollectionArchiveFileIdentity.content(meta)!!.sha256)
        }
        assertTrue(r.remote.requests.any{it.method=="GET"&&it.version=="v1"&&it.ifMatch=="\"etag-v1\""})
        assertTrue(r.remote.requests.all{it.method in setOf("GET","HEAD")})
        val response=downloaded(r,item.id);assertEquals("remote-original-v1",response.contentAsString)
        assertEquals("sha256-verified",response.getHeader("X-Collection-Content-Integrity"))
    }
    @Test fun `latest overwrite does not replace historical bytes and normal route pins version`()=fixture {r->
        create(r);val item=item(r);r.remote.latest="v2";r.remote.requests.clear()
        assertEquals("remote-original-v1",downloaded(r,item.id).contentAsString)
        val request=MockHttpServletRequest("GET","/userfiles/rustfs/docs/proof.txt");request.addHeader("Range","bytes=0-3");request.addHeader("If-Modified-Since","Tue, 01 Sep 2026 10:00:00 GMT")
        val response=MockHttpServletResponse();r.controller.downloadFile(request,response)
        assertEquals(206,response.status);assertEquals("remo",response.contentAsString);assertEquals("private, no-store",response.getHeader("Cache-Control"))
        assertTrue(r.remote.requests.all{it.version=="v1"});assertTrue(r.remote.requests.filter{it.method=="GET"}.all{it.range==null})
    }
    @Test fun `latest delete marker does not hide a retained readable version`()=fixture {r->
        create(r);val item=item(r);r.remote.deletedLatest=true
        assertTrue(r.c.getBean(FileObjectStorageService::class.java).exists("/docs/proof.txt","RUSTFS"))
        assertEquals("remote-original-v1",downloaded(r,item.id).contentAsString)
    }
    @ParameterizedTest @ValueSource(strings=["missing","permission"])
    fun `unavailable retained version never falls back to latest`(kind:String)=fixture {r->
        create(r);val item=item(r);r.remote.latest="v2";r.remote.requests.clear()
        if(kind=="missing")r.remote.data.remove("v1") else r.remote.denyVersion=true
        deny(r,item.id);assertTrue(r.remote.requests.isNotEmpty());assertTrue(r.remote.requests.all{it.version=="v1"})
    }
    @ParameterizedTest @ValueSource(strings=["bucket","endpoint"])
    fun `changed storage location is refused before any remote request`(kind:String)=fixture {r->
        create(r);val item=item(r);r.remote.requests.clear()
        r.remote.settings=if(kind=="bucket")r.remote.settings.copy(rustFsBucket="different") else r.remote.settings.copy(rustFsEndpoint="http://127.0.0.1:9")
        deny(r,item.id);assertTrue(r.remote.requests.isEmpty())
    }
    @Test fun `credential rotation does not change retained storage identity`()=fixture {r->
        create(r);val item=item(r)
        r.remote.settings=r.remote.settings.copy(rustFsAccessKey="rotated-test-access",rustFsSecretKey="rotated-test-secret")
        assertEquals("remote-original-v1",downloaded(r,item.id).contentAsString)
    }
    @Test fun `server returning a different version is rejected before bytes`()=fixture {r->
        create(r);val item=item(r);r.remote.wrongResponseVersion="v2";deny(r,item.id)
    }
    @Test fun `unchanged remote version and ETag cannot conceal changed bytes`()=fixture {r->
        create(r);val item=item(r);r.remote.data["v1"]=ByteArray("remote-original-v1".length){'X'.code.toByte()};deny(r,item.id)
    }
    @ParameterizedTest @ValueSource(strings=["none","null"])
    fun `unversioned objects retain byte baseline and conditional read without fixed version claim`(mode:String)=fixture {r->
        r.remote.versionMode=mode;create(r);val item=item(r)
        assertEquals("RECORDED_LOCATION",item.availability);assertEquals("SHA256_RECORDED",item.contentIntegrity)
        r.db {org.junit.jupiter.api.Assertions.assertNull(CollectionArchiveFileIdentity.revision(JsonUtils.mapper.readTree(CollectionPermanentArchiveFileDao.all().single().metadata))!!.versionId)}
        assertEquals("remote-original-v1",downloaded(r,item.id).contentAsString)
        r.remote.latest="v2";r.remote.requests.clear();deny(r,item.id)
        assertTrue(r.remote.requests.all{it.version==null&&it.ifMatch=="\"etag-v1\""})
    }
    @Test fun `missing both version and condition identifier rolls archive and business back`()=fixture {r->
        r.remote.versionMode="none";r.remote.omitETag=true
        assertThrows(BadRequestException::class.java){create(r)}
        r.db {assertEquals(0L,CollectionPermanentArchiveDao.count());assertEquals(0L,AcquisitionPlanDao.count())}
        assertTrue(r.remote.requests.none{it.method=="GET"})
    }
    @Test fun `version selected by HEAD remains fixed when latest changes before GET`()=fixture {r->
        r.remote.afterInitialHead={r.remote.latest="v2"};create(r)
        assertEquals("remote-original-v1",downloaded(r,item(r).id).contentAsString)
    }
    @Test fun `conditional object changed between HEAD and GET rolls back instead of mixing observations`()=fixture {r->
        r.remote.versionMode="none";r.remote.afterInitialHead={r.remote.latest="v2"}
        assertThrows(BadRequestException::class.java){create(r)}
        r.db {assertEquals(0L,CollectionPermanentArchiveDao.count());assertEquals(0L,AcquisitionPlanDao.count())}
    }
    @Test fun `revoked business reader cannot contact remote storage`()=fixture {r->
        create(r);val item=item(r);r.remote.requests.clear();r.permission();deny(r,item.id)
        assertTrue(r.remote.requests.isEmpty())
    }
    @ParameterizedTest @ValueSource(strings=["storage","version","etag","remove","null"])
    fun `modified remote revision metadata cannot expose history or call storage`(kind:String)=fixture {r->
        create(r);val item=item(r);r.remote.requests.clear()
        r.db {
            val row=CollectionPermanentArchiveFileDao.all().single();val meta=JsonUtils.mapper.readTree(row.metadata) as tools.jackson.databind.node.ObjectNode
            val revision=meta.path("_archiveObjectRevision") as tools.jackson.databind.node.ObjectNode
            when(kind){"storage"->revision.put("storageIdentity","0".repeat(64));"version"->revision.put("versionId","v2");"etag"->revision.put("eTag","other");"remove"->meta.remove("_archiveObjectRevision");"null"->meta.putNull("_archiveObjectRevision")}
            row.metadata=meta.toString()
        }
        assertEquals(0L,history(r).page(null,null,null).total);deny(r,item.id);assertTrue(r.remote.requests.isEmpty())
    }
    @Test fun `another business phase retains original pinned version after latest overwrite`()=fixture {r->
        create(r);r.remote.latest="v2";val id=r.seal(CollectionConst.BIZ_LOAN,id="second-phase")
        r.db {val metadata=CollectionPermanentArchiveFileDao.find{CollectionPermanentArchiveFiles.archiveId eq id}.single().metadata;assertEquals("v1",CollectionArchiveFileIdentity.revision(JsonUtils.mapper.readTree(metadata))!!.versionId)}
        assertEquals("remote-original-v1",downloaded(r,item(r).id).contentAsString)
    }

    @Test fun `fixed version without ETag still captures and verifies actual bytes`()=fixture {r->
        r.remote.omitETag=true;create(r);val item=item(r);r.remote.latest="v2"
        assertEquals("REMOTE_VERSION_RECORDED",item.availability)
        assertEquals("remote-original-v1",downloaded(r,item.id).contentAsString)
        assertTrue(r.remote.requests.filter{it.method=="GET"}.all{it.version=="v1"&&it.ifMatch==null})
    }
    @Test fun `idempotent draft save verifies retained remote version after latest overwrite`()=fixture {r->
        val id=create(r);val before=r.db{CollectionPermanentArchiveFileDao.all().single().metadata}
        r.remote.latest="v2"
        r.c.getBean(AcquisitionPlanService::class.java).save(AcquisitionPlanVo(id=id,title="同一材料补充说明",year=2026,attachmentIds="proof"))
        r.db{assertEquals(1L,CollectionPermanentArchiveDao.count());assertEquals(before,CollectionPermanentArchiveFileDao.all().single().metadata)}
        assertEquals("remote-original-v1",downloaded(r,item(r).id).contentAsString)
    }
    @Test fun `missing retained version rolls back draft save without resetting its baseline`()=fixture {r->
        val id=create(r);val before=r.db{CollectionPermanentArchiveFileDao.all().single().metadata}
        r.remote.latest="v2";r.remote.data.remove("v1")
        assertThrows(BadRequestException::class.java){r.c.getBean(AcquisitionPlanService::class.java).save(AcquisitionPlanVo(id=id,title="不能写入",year=2026,attachmentIds="proof"))}
        r.db{assertEquals("远端原件",AcquisitionPlanDao[id].title);assertEquals(1L,CollectionPermanentArchiveDao.count());assertEquals(before,CollectionPermanentArchiveFileDao.all().single().metadata)}
    }

    @Test fun `audited remote recovery pins new version and preserves every frozen archive across another bucket move`()=fixture {r->
        create(r);val item=item(r)
        val originalSettings=r.remote.settings
        val original=r.db{CollectionPermanentArchiveFileDao.all().single().metadata}
        r.remote.data["restored-1"]="remote-original-v1".toByteArray()
        r.remote.settings=r.remote.settings.copy(rustFsBucket="restored-one")
        r.remote.latest="restored-1"
        assertEquals("IDENTITY_UNVERIFIED",item(r).availability)
        r.remote.requests.clear();deny(r,item.id);assertTrue(r.remote.requests.isEmpty())
        val recovery=r.c.getBean(CollectionPermanentRemoteRecoveryService::class.java)
        val first=recoveryRequest(r,"restored-1")
        r.remote.requests.clear()
        assertThrows(BadRequestException::class.java){recovery.record(first)}
        assertTrue(r.remote.requests.isEmpty(),"案卷成员不能提交远端版本承接")
        login("archivist")
        assertThrows(BadRequestException::class.java){recovery.record(first.copy(restoreReportSha256="0".repeat(64)))}
        assertThrows(BadRequestException::class.java){recovery.record(first.copy(targetVersionId=null,targetETag=null))}
        assertTrue(r.remote.requests.isEmpty(),"无效报告或版本不得触及远端对象")
        assertThrows(BadRequestException::class.java){recovery.record(recoveryRequest(r,"v2"))}
        r.db{assertEquals(0L,CollectionPermanentRemoteRecoveryDao.count())}
        assertEquals("proof",recovery.record(first));assertEquals("proof",recovery.record(first))
        r.db{assertEquals(1L,CollectionPermanentRemoteRecoveryDao.count());assertEquals(original,CollectionPermanentArchiveFileDao.all().single().metadata)}
        login();assertEquals("REMOTE_VERSION_RECORDED",item(r).availability)
        assertEquals("remote-original-v1",downloaded(r,item.id).contentAsString)
        r.remote.latest="v2";assertEquals("remote-original-v1",downloaded(r,item.id).contentAsString)
        r.remote.latest="restored-1"
        r.seal(CollectionConst.BIZ_LOAN,id="after-recovery")
        r.db{
            assertEquals(2L,CollectionPermanentArchiveDao.count())
            assertEquals(setOf("v1","restored-1"),CollectionPermanentArchiveFileDao.all().map {
                CollectionArchiveFileIdentity.revision(JsonUtils.mapper.readTree(it.metadata))!!.versionId
            }.toSet())
        }
        r.remote.data["restored-2"]="remote-original-v1".toByteArray()
        r.remote.settings=r.remote.settings.copy(rustFsBucket="restored-two")
        r.remote.latest="restored-2"
        assertEquals("IDENTITY_UNVERIFIED",item(r).availability)
        deny(r,item.id)
        login("archivist")
        assertEquals("proof",recovery.record(recoveryRequest(r,"restored-2")))
        r.db{assertEquals(2L,CollectionPermanentRemoteRecoveryDao.count());assertEquals(original,CollectionPermanentArchiveFileDao.all().first().metadata)}
        login();assertEquals("REMOTE_VERSION_RECORDED",item(r).availability)
        assertEquals("remote-original-v1",downloaded(r,item.id).contentAsString)
        r.db{CollectionPermanentRemoteRecoveryDao.all().maxBy {it.createTime}.evidenceDigest="0".repeat(64)}
        deny(r,item.id)
        r.remote.settings=originalSettings;r.remote.latest="v1"
        deny(r,item.id)
    }
}
