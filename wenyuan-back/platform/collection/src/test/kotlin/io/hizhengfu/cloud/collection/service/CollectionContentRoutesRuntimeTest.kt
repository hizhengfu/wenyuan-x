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

class CollectionContentRoutesRuntimeTest {
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
        val manager = SpringTransactionManager(SQLiteDataSource().apply { url="jdbc:sqlite:${dir.resolve("content-routes.db")}" }, DatabaseConfig {})
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



    private fun create(r:Runtime,files:String="proof") = r.c.getBean(AcquisitionPlanService::class.java).save(AcquisitionPlanVo(title="计划原件",year=2026,attachmentIds=files))
    private fun storage(r:Runtime)=r.c.getBean(FileObjectStorageService::class.java)
    private fun download(r:Runtime,original:Boolean=false,range:Boolean=false,path:String="proof.txt"):MockHttpServletResponse {
        val request=MockHttpServletRequest("GET","/userfiles/${if(original) "original/" else ""}$path")
        request.addHeader("If-Modified-Since","Tue, 01 Sep 2026 10:00:00 GMT")
        if(range) request.addHeader("Range","bytes=0-3")
        val response=MockHttpServletResponse()
        if(original) r.controller.downloadOriginalFile(request,response) else r.controller.downloadFile(request,response)
        return response
    }
    @ParameterizedTest @CsvSource("false,false","true,false","false,true","true,true")
    fun `normal and original routes preserve full and range output after complete verification`(original:Boolean,range:Boolean)=fixture { r ->
        create(r)
        val response=download(r,original,range)
        assertEquals(if(range)206 else 200,response.status)
        assertEquals(if(range)"CONT" else "CONTENT:proof.txt",response.contentAsString)
        assertEquals("private, no-store",response.getHeader("Cache-Control"))
        if(range) assertEquals("bytes 0-3/17",response.getHeader("Content-Range"))
    }
    @ParameterizedTest @CsvSource("false,false","true,false","false,true","true,true")
    fun `changed bytes outside requested range prevent all output including old conditional requests`(original:Boolean,range:Boolean)=fixture { r ->
        create(r)
        java.nio.file.Files.writeString(dir.resolve("proof.txt"),"CONTENT:proof.BAD")
        val response=download(r,original,range)
        assertEquals(403,response.status);assertEquals(0,response.contentAsByteArray.size)
    }
    @Test fun `direct storage stream verifies complete source before applying range`()=fixture { r ->
        create(r)
        storage(r).openStream("proof.txt","LOCAL",2,5).use { assertEquals("NTEN",it.readBytes().toString(Charsets.UTF_8)) }
        java.nio.file.Files.writeString(dir.resolve("proof.txt"),"CONTENT:proof.BAD")
        assertThrows(BadRequestException::class.java) { storage(r).openStream("proof.txt","LOCAL",0,3).close() }
    }
    @Test fun `temporary materialization supplies a verified copy and cleans it after failure`()=fixture { r ->
        create(r);var copy:java.io.File?=null
        assertThrows(java.io.IOException::class.java) {
            storage(r).withTempFile("proof.txt","LOCAL",".txt") { file ->
                copy=file;assertNotEquals(dir.resolve("proof.txt"),file.toPath())
                java.nio.file.Files.writeString(dir.resolve("proof.txt"),"CONTENT:proof.BAD")
                assertEquals("CONTENT:proof.txt",file.readText())
                throw java.io.IOException("转换失败")
            }
        }
        assertFalse(copy!!.exists())
        assertThrows(BadRequestException::class.java) { storage(r).withTempFile("proof.txt","LOCAL",".txt") { fail<Unit>("changed original was exposed") } }
    }
    @Test fun `ZIP includes every verified member`()=fixture { r ->
        val id=create(r,"proof,replacement");val response=MockHttpServletResponse()
        r.c.getBean(FileService::class.java).zipContentFile(id,response,"原件")
        val values=mutableMapOf<String,String>()
        java.util.zip.ZipInputStream(response.contentAsByteArray.inputStream()).use { zip ->
            while(true) { val entry=zip.nextEntry ?: break;values[entry.name]=zip.readBytes().toString(Charsets.UTF_8) }
        }
        assertEquals(mapOf("proof.txt" to "CONTENT:proof.txt","replacement.txt" to "CONTENT:replacement.txt"),values)
        assertEquals("private, no-store",response.getHeader("Cache-Control"))
    }
    @Test fun `one failed ZIP member prevents even an empty or partial archive response`()=fixture { r ->
        val id=create(r,"proof,replacement")
        java.nio.file.Files.writeString(dir.resolve("replacement.txt"),"changed")
        val response=MockHttpServletResponse()
        assertThrows(BadRequestException::class.java) { r.c.getBean(FileService::class.java).zipContentFile(id,response) }
        assertEquals(0,response.contentAsByteArray.size)
    }
    private fun preview(r:Runtime, objectStorage:FileObjectStorage=storage(r), useConverter:Boolean=true, action:()->Unit={}):Pair<FilePreviewController,java.util.concurrent.atomic.AtomicInteger> {
        val count=java.util.concurrent.atomic.AtomicInteger()
        val converter=object:io.hizhengfu.cloud.sys.file.converter.FileConverter {
            override fun supportedExtensions()=setOf("txt")
            override fun convert(input:java.io.InputStream,format:String,output:java.io.OutputStream) {
                count.incrementAndGet();output.write("CONVERTED:".toByteArray());input.copyTo(output);action()
            }
        }
        return FilePreviewController(r.c.getBean(FileService::class.java),FileConvertService(r.c.getBean(FileService::class.java),if(useConverter)listOf(converter) else emptyList()),objectStorage,r.access) to count
    }
    private fun md5(value:String)=java.security.MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun poisonPreview():Path {
        val path=dir.resolve("preview-cache/${md5("FILE_PREVIEW_proof_pdf")}.pdf")
        java.nio.file.Files.createDirectories(path.parent);java.nio.file.Files.writeString(path,"POISONED-CACHE")
        return path
    }
    @Test fun `private document preview ignores old persistent cache and does not replace it`()=fixture { r ->
        create(r);val cache=poisonPreview();val (controller,count)=preview(r)
        val response=MockHttpServletResponse();controller.previewDocument("proof","pdf",response)
        assertEquals("CONVERTED:CONTENT:proof.txt",response.contentAsString);assertEquals(1,count.get())
        assertEquals("POISONED-CACHE",java.nio.file.Files.readString(cache))
        assertEquals(false,controller.previewCached("proof","pdf")["cached"])
        assertEquals("private, no-store",response.getHeader("Cache-Control"))
    }
    @Test fun `corrupt source cannot use cached document preview or reach converter`()=fixture { r ->
        create(r);poisonPreview();java.nio.file.Files.writeString(dir.resolve("proof.txt"),"changed")
        val (controller,count)=preview(r);val response=MockHttpServletResponse()
        controller.previewDocument("proof","pdf",response)
        assertEquals(400,response.status);assertEquals(0,count.get());assertEquals(0,response.contentAsByteArray.size)
    }
    @Test fun `permission revoked during conversion prevents converted response`()=fixture { r ->
        create(r);val (controller,count)=preview(r){r.permission()};val response=MockHttpServletResponse()
        controller.previewDocument("proof","pdf",response)
        assertEquals(1,count.get());assertEquals(400,response.status);assertEquals(0,response.contentAsByteArray.size)
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `file attached as a protected original during conversion is not output or publicly cached`(revoked:Boolean)=fixture {r->
        assertFalse(r.access.isProtected("proof.txt","LOCAL"))
        var attached=false
        val (controller,count)=preview(r) {
            if(!attached) {attached=true;create(r);if(revoked)r.permission()}
        }
        val response=MockHttpServletResponse();controller.previewDocument("proof","pdf",response)
        assertTrue(attached);assertTrue(r.access.isProtected("proof.txt","LOCAL"))
        assertEquals(1,count.get());assertEquals(400,response.status);assertEquals(0,response.contentAsByteArray.size)
        val cache=dir.resolve("preview-cache/${md5("FILE_PREVIEW_proof_pdf")}.pdf")
        assertFalse(java.nio.file.Files.exists(cache));assertFalse(java.nio.file.Files.exists(cache.resolveSibling(cache.fileName.toString()+".tmp")))
        if(!revoked) {
            val retry=MockHttpServletResponse();controller.previewDocument("proof","pdf",retry)
            assertEquals(200,retry.status);assertEquals("CONVERTED:CONTENT:proof.txt",retry.contentAsString)
            assertEquals("private, no-store",retry.getHeader("Cache-Control"));assertFalse(java.nio.file.Files.exists(cache))
        }
    }
    @ParameterizedTest @ValueSource(strings=["cached","passthrough"])
    fun `protection added while materializing the source prevents cached and raw preview output`(mode:String)=fixture {r->
        val cache=if(mode=="cached")poisonPreview() else null
        val actual=storage(r)
        val delayed=object:FileObjectStorage by actual {
            override fun <T> withTempFile(path:String,storageType:String?,suffix:String?,block:(java.io.File)->T):T =
                actual.withTempFile(path,storageType,suffix) {file->create(r);block(file)}
        }
        val (controller,count)=preview(r,delayed,useConverter=mode!="passthrough")
        val response=MockHttpServletResponse();controller.previewDocument("proof","pdf",response)
        assertEquals(400,response.status);assertEquals(0,response.contentAsByteArray.size);assertEquals(0,count.get())
        assertEquals("private, no-store",response.getHeader("Cache-Control"))
        if(cache!=null)assertEquals("POISONED-CACHE",java.nio.file.Files.readString(cache))
    }
    private fun image(r:Runtime):Path {
        val image=java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB)
        for(x in 0..1)for(y in 0..1)image.setRGB(x,y,java.awt.Color.RED.rgb)
        javax.imageio.ImageIO.write(image,"png",dir.resolve("proof.png").toFile())
        r.db {FileDao["proof"].path="proof.png";FileDao["proof"].ext="png";FileDao["proof"].name="proof.png"}
        create(r)
        val hash=md5("/userfiles/proof.png")
        val cache=dir.resolve("cache/${hash.take(2)}/${hash.substring(2,4)}/$hash-${md5("")}")
        java.nio.file.Files.createDirectories(cache.parent);java.nio.file.Files.writeString(cache,"POISONED-CACHE")
        return cache
    }
    @Test fun `private image ignores legacy cache and reuses authorized versioned derivative`()=fixture { r ->
        val cache=image(r);val response=download(r,path="proof.png")
        assertEquals(200,response.status)
        val rendered=javax.imageio.ImageIO.read(response.contentAsByteArray.inputStream())
        assertEquals(java.awt.Color.RED.rgb,rendered.getRGB(0,0))
        assertEquals("POISONED-CACHE",java.nio.file.Files.readString(cache))
        java.nio.file.Files.walk(dir.resolve("cache")).use { assertEquals(2,it.filter(java.nio.file.Files::isRegularFile).count().toInt()) }
        var encodedAgain=false
        withPngEncodingHook({encodedAgain=true}) {
            val second=download(r,path="proof.png")
            assertArrayEquals(response.contentAsByteArray,second.contentAsByteArray)
            assertEquals("private, no-store",second.getHeader("Cache-Control"))
            org.junit.jupiter.api.Assertions.assertNull(second.getHeader("ETag"))
        }
        assertFalse(encodedAgain,"缓存命中不应再次编码")
    }
    @ParameterizedTest @CsvSource("protect,false", "protect,true", "revoke,false", "revoke,true", "protect-failure,false")
    fun `image encoding finishes before current business authorization and cache publication`(change:String,circle:Boolean)=fixture {r->
        val png=java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB)
        for(x in 0..3)for(y in 0..3)png.setRGB(x,y,java.awt.Color.RED.rgb)
        val original=dir.resolve("proof.png")
        javax.imageio.ImageIO.write(png,"png",original.toFile())
        val originalBytes=java.nio.file.Files.readAllBytes(original)
        r.db { FileDao["proof"].path="proof.png";FileDao["proof"].ext="png";FileDao["proof"].name="proof.png" }
        if(change=="revoke")create(r)
        val response=MockHttpServletResponse()
        val request=MockHttpServletRequest("GET","/userfiles/proof.png")
        request.addParameter("w","2"); if(circle)request.addParameter("c","")
        var changed=false
        withPngEncodingHook({
            if(change.startsWith("protect"))create(r) else r.permission();changed=true
            if(change=="protect-failure")throw java.io.IOException("synthetic encoder failure")
        }) {
            r.controller.downloadFile(request,response)
        }
        assertTrue(changed,"必须在实际 PNG 编码完成期间提交业务变化")
        assertEquals(403,response.status);assertEquals(0,response.contentAsByteArray.size)
        assertEquals("private, no-store",response.getHeader("Cache-Control"))
        org.junit.jupiter.api.Assertions.assertNull(response.getHeader("ETag"))
        val cache=dir.resolve("cache")
        if(java.nio.file.Files.exists(cache))java.nio.file.Files.walk(cache).use { assertEquals(0,it.filter(java.nio.file.Files::isRegularFile).count().toInt()) }
        assertArrayEquals(originalBytes,java.nio.file.Files.readAllBytes(original))
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `public thumbnail encodes once and publishes the same complete bytes for reuse`(circle:Boolean)=fixture {r->
        val png=java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB)
        javax.imageio.ImageIO.write(png,"png",dir.resolve("public.png").toFile())
        var encodings=0
        val responses=mutableListOf<ByteArray>()
        withPngEncodingHook({
            encodings++
            val cache=dir.resolve("cache")
            if(java.nio.file.Files.exists(cache))java.nio.file.Files.walk(cache).use { assertEquals(0,it.filter(java.nio.file.Files::isRegularFile).count().toInt()) }
        },onceOnly=false) {
            repeat(2) {
                val request=MockHttpServletRequest("GET","/userfiles/public.png")
                request.addParameter("w","2");if(circle)request.addParameter("c","")
                val response=MockHttpServletResponse();r.controller.downloadFile(request,response)
                assertEquals(200,response.status);assertEquals("public, no-cache",response.getHeader("Cache-Control"))
                val image=javax.imageio.ImageIO.read(response.contentAsByteArray.inputStream())
                assertEquals(2,image.width);assertEquals(2,image.height)
                responses.add(response.contentAsByteArray)
            }
        }
        assertEquals(1,encodings);assertArrayEquals(responses[0],responses[1])
        java.nio.file.Files.walk(dir.resolve("cache")).use { files ->
            val cached=files.filter(java.nio.file.Files::isRegularFile).toList()
            assertEquals(1,cached.size);assertArrayEquals(responses[0],java.nio.file.Files.readAllBytes(cached.single()))
        }
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `pending image publication files cannot be downloaded directly or through aliases`(alias:Boolean)=fixture {r->
        val target=image(r).resolveSibling(".image-pending.tmp")
        java.nio.file.Files.writeString(target,"PRIVATE-PENDING")
        val path=if(alias) {
            java.nio.file.Files.createSymbolicLink(dir.resolve("pending-alias.png"),target);"pending-alias.png"
        } else dir.relativize(target).toString()
        assertThrows(BadRequestException::class.java) { download(r,path=path) }
        assertThrows(BadRequestException::class.java) { storage(r).openStream(path).close() }
    }
    private fun publicPng(color:java.awt.Color):Path {
        val image=java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB)
        for(x in 0..7)for(y in 0..7)image.setRGB(x,y,color.rgb)
        val path=dir.resolve("versioned.png")
        javax.imageio.ImageIO.write(image,"png",path.toFile())
        java.nio.file.Files.setLastModifiedTime(path,java.nio.file.attribute.FileTime.from(java.time.Instant.parse("2026-01-02T12:00:00Z")))
        return path
    }
    private fun thumbnail(r:Runtime,etag:String?=null,since:String?=null):MockHttpServletResponse {
        val request=MockHttpServletRequest("GET","/userfiles/versioned.png");request.addParameter("w","4")
        etag?.let {request.addHeader("If-None-Match",it)};since?.let {request.addHeader("If-Modified-Since",it)}
        return MockHttpServletResponse().also {r.controller.downloadFile(request,it)}
    }
    @ParameterizedTest @ValueSource(strings=["plain","date","etag"])
    fun `public image replaced at same path and timestamp does not reuse old pixels`(condition:String)=fixture {r->
        publicPng(java.awt.Color.RED)
        val first=thumbnail(r);assertEquals(200,first.status)
        assertEquals(java.awt.Color.RED.rgb,javax.imageio.ImageIO.read(first.contentAsByteArray.inputStream()).getRGB(1,1))
        publicPng(java.awt.Color.BLUE)
        val second=thumbnail(r,if(condition=="etag")first.getHeader("ETag")?:"\"old-client-tag\"" else null,
            if(condition=="date")"Fri, 02 Jan 2026 12:00:00 GMT" else null)
        assertEquals(200,second.status)
        assertEquals(java.awt.Color.BLUE.rgb,javax.imageio.ImageIO.read(second.contentAsByteArray.inputStream()).getRGB(1,1))
        assertEquals("public, no-cache",second.getHeader("Cache-Control"))
        assertNotEquals(first.getHeader("ETag"),second.getHeader("ETag"))
    }
    @ParameterizedTest @ValueSource(strings=["weak","strong","list","wildcard"])
    fun `unchanged public image revalidates by content tag without encoding again`(condition:String)=fixture {r->
        publicPng(java.awt.Color.RED)
        val first=thumbnail(r)
        val tag=requireNotNull(first.getHeader("ETag"))
        val supplied=when(condition) {"strong"->tag.removePrefix("W/");"list"->"\"other\", $tag";"wildcard"->"*";else->tag}
        var encodings=0
        val second=withPngEncodingHook({encodings++},onceOnly=false) {thumbnail(r,supplied)}
        assertEquals(304,second.status);assertEquals(0,second.contentAsByteArray.size)
        assertEquals(tag,second.getHeader("ETag"));assertEquals("public, no-cache",second.getHeader("Cache-Control"))
        assertEquals(0,encodings)
    }
    @Test fun `legacy image cache without source version is ignored and left intact`()=fixture {r->
        publicPng(java.awt.Color.RED)
        val hash=md5("/userfiles/versioned.png")
        val old=dir.resolve("cache/${hash.take(2)}/${hash.substring(2,4)}/$hash-${md5("w=4")}")
        java.nio.file.Files.createDirectories(old.parent);java.nio.file.Files.writeString(old,"INCOMPLETE-OLD-CACHE")
        val response=thumbnail(r);assertEquals(200,response.status)
        val decoded=javax.imageio.ImageIO.read(response.contentAsByteArray.inputStream())
        org.junit.jupiter.api.Assertions.assertNotNull(decoded)
        assertEquals(java.awt.Color.RED.rgb,decoded.getRGB(1,1))
        assertEquals("INCOMPLETE-OLD-CACHE",java.nio.file.Files.readString(old))
    }
    @Test fun `image transform parameters select a different content validator`()=fixture {r->
        publicPng(java.awt.Color.RED);val first=thumbnail(r)
        val request=MockHttpServletRequest("GET","/userfiles/versioned.png");request.addParameter("w","2")
        request.addHeader("If-None-Match",first.getHeader("ETag")!!)
        val response=MockHttpServletResponse();r.controller.downloadFile(request,response)
        assertEquals(200,response.status);assertNotEquals(first.getHeader("ETag"),response.getHeader("ETag"))
        assertEquals(2,javax.imageio.ImageIO.read(response.contentAsByteArray.inputStream()).width)
    }
    @Test fun `private images never validate public tags or expose content validators`()=fixture {r->
        image(r)
        val request=MockHttpServletRequest("GET","/userfiles/proof.png")
        request.addHeader("If-None-Match","*");request.addHeader("If-Modified-Since","Fri, 02 Jan 2026 12:00:00 GMT")
        val response=MockHttpServletResponse();r.controller.downloadFile(request,response)
        assertEquals(200,response.status);assertEquals("private, no-store",response.getHeader("Cache-Control"))
        org.junit.jupiter.api.Assertions.assertNull(response.getHeader("ETag"))
        org.junit.jupiter.api.Assertions.assertNull(response.getHeader("Last-Modified"))
    }
    @Test fun `raw fallback after image decode failure does not keep derivative validator`()=fixture {r->
        java.nio.file.Files.writeString(dir.resolve("versioned.png"),"UNDECODABLE-IMAGE")
        val response=thumbnail(r)
        assertEquals(200,response.status);assertEquals("UNDECODABLE-IMAGE",response.contentAsString)
        assertEquals("private, no-store",response.getHeader("Cache-Control"))
        org.junit.jupiter.api.Assertions.assertNull(response.getHeader("ETag"))
    }
    @Test fun `active watermark pixels distinguish cache and validator across controller reloads`()=fixture {r->
        val source=java.awt.image.BufferedImage(500,500,java.awt.image.BufferedImage.TYPE_INT_RGB)
        javax.imageio.ImageIO.write(source,"png",dir.resolve("watermark-source.png").toFile())
        fun render(color:java.awt.Color):MockHttpServletResponse {
            val mark=java.awt.image.BufferedImage(20,20,java.awt.image.BufferedImage.TYPE_INT_ARGB)
            for(x in 0..19)for(y in 0..19)mark.setRGB(x,y,color.rgb)
            javax.imageio.ImageIO.write(mark,"png",dir.resolve("mark.png").toFile())
            val controller=FileViewController(r.c,false,dir.toString(),false,"mark.png",storage(r),r.access)
            val request=MockHttpServletRequest("GET","/userfiles/watermark-source.png")
            request.addParameter("w","400");request.addParameter("h","400")
            return MockHttpServletResponse().also {controller.downloadFile(request,it)}
        }
        val first=render(java.awt.Color.RED);val second=render(java.awt.Color.BLUE)
        assertEquals(200,first.status);assertEquals(200,second.status)
        assertNotEquals(first.getHeader("ETag"),second.getHeader("ETag"))
        assertFalse(first.contentAsByteArray.contentEquals(second.contentAsByteArray))
        assertEquals(400,javax.imageio.ImageIO.read(second.contentAsByteArray.inputStream()).width)
    }
    @Test fun `corrupt private image cannot be served from old cache`()=fixture { r ->
        image(r);java.nio.file.Files.writeString(dir.resolve("proof.png"),"changed")
        val response=download(r,path="proof.png");assertEquals(403,response.status);assertEquals(0,response.contentAsByteArray.size)
    }
    @ParameterizedTest @ValueSource(strings=["image","document","alias"])
    fun `generated cache routes and canonical aliases require original business entry`(kind:String)=fixture { r ->
        val cache=if(kind=="image")image(r) else poisonPreview()
        val path=if(kind=="alias") {
            java.nio.file.Files.createSymbolicLink(dir.resolve("alias.pdf"),cache);"alias.pdf"
        }else dir.relativize(cache).toString()
        assertThrows(BadRequestException::class.java) { download(r,path=path) }
        assertThrows(BadRequestException::class.java) { storage(r).openStream(path).close() }
    }
    @Test fun `shared file with conflicting archive content baselines is refused`()=fixture { r ->
        create(r)
        val archiveId=r.seal(CollectionConst.BIZ_LOAN,id="second-phase")
        java.nio.file.Files.writeString(dir.resolve("proof.txt"),"CONTENT:proof.BAD")
        // 模拟历史导入形成的相互矛盾基线；正常新归档另有事务校验。
        r.db {
            val archive=CollectionPermanentArchiveDao[archiveId]
            val file=CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archiveId }.single()
            val keys=CollectionArchiveFileIdentity.candidates(JsonUtils.mapper.readTree(file.metadata))
            file.metadata=CollectionArchiveFileIdentity.metadata(FileDao["proof"],archive.orgId,archive.bizType,archive.bizId,keys,CollectionOriginalContent.read("CONTENT:proof.BAD".byteInputStream()))
        }
        assertThrows(BadRequestException::class.java) { storage(r).openStream("proof.txt").close() }
    }

    @Test fun `ZIP retains same named originals and removes filename directory components`()=fixture { r ->
        val id=create(r,"proof,replacement")
        r.db {FileDao["proof"].name="../原件.txt";FileDao["replacement"].name="C:\\原件.txt"}
        val response=MockHttpServletResponse();r.c.getBean(FileService::class.java).zipContentFile(id,response)
        val values=mutableMapOf<String,String>()
        java.util.zip.ZipInputStream(response.contentAsByteArray.inputStream()).use { zip ->
            while(true) { val entry=zip.nextEntry ?: break;values[entry.name]=zip.readBytes().toString(Charsets.UTF_8) }
        }
        assertEquals(setOf("原件.txt","原件 (2).txt"),values.keys)
        assertEquals(setOf("CONTENT:proof.txt","CONTENT:replacement.txt"),values.values.toSet())
    }
    @Test fun `revocation after all ZIP members are staged prevents response`()=fixture { r ->
        val id=create(r,"proof,replacement");var completed=0
        val original=storage(r)
        val changing=object:FileObjectStorage by original {
            override fun <T> withTempFile(path:String,storageType:String?,suffix:String?,block:(java.io.File)->T):T {
                val result=original.withTempFile(path,storageType,suffix,block)
                completed++
                if(completed==2) r.permission()
                return result
            }
        }
        val service=FileService(dir.toString(),FileFolderService(),changing,emptyList(),r.access)
        val response=MockHttpServletResponse()
        assertThrows(BadRequestException::class.java) {r.db { service.zipContentFile(id,response) }}
        assertEquals(2,completed);assertEquals(0,response.contentAsByteArray.size)
    }

    @Test fun `a new business phase cannot archive overwritten bytes as another original`()=fixture { r ->
        create(r)
        val metadata=r.db {CollectionPermanentArchiveFileDao.all().single().metadata}
        java.nio.file.Files.writeString(dir.resolve("proof.txt"),"CONTENT:proof.BAD")
        assertThrows(BadRequestException::class.java) {r.seal(CollectionConst.BIZ_LOAN,id="new-business-phase")}
        r.db {
            assertEquals(1L,CollectionPermanentArchiveDao.count())
            assertEquals(metadata,CollectionPermanentArchiveFileDao.all().single().metadata)
        }
    }
}
