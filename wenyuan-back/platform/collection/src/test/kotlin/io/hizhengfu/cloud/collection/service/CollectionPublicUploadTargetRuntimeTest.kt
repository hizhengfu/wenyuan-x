package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.api.FileController
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.file.service.*
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.annotation.*
import org.springframework.mock.web.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.sqlite.SQLiteDataSource
import java.nio.file.Path

class CollectionPublicUploadTargetRuntimeTest {
    @TempDir lateinit var root: Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private object Articles : io.hizhengfu.cloud.autoconfigure.exposed.UIDTable("ordinary_article")
    private class OrdinaryInstall : io.hizhengfu.cloud.common.install.AbstractInstall() {
        override fun initTables() = setOf(Articles)
    }
    private fun fixture(body: (AnnotationConfigApplicationContext, TransactionTemplate) -> Unit) {
        val manager = SpringTransactionManager(SQLiteDataSource().apply { url="jdbc:sqlite:${root.resolve("target.db")}" }, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c ->
            c.environment.propertySources.addFirst(org.springframework.core.env.MapPropertySource("test", mapOf("file.basedir" to root.resolve("files").toString())))
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("collectionUploadTargets",io.hizhengfu.cloud.collection.install.CollectionInstall("single"))
            c.beanFactory.registerSingleton("ordinaryInstall",OrdinaryInstall())
            c.register(Transactions::class.java, LocalFileObjectStorage::class.java, FileReadAccess::class.java,
                FileService::class.java, FileFolderService::class.java, FileSystemStorageService::class.java,
                FileChunkSessionService::class.java, CollectionMainOriginalFileSource::class.java, CollectionPublicUploadTargetGuard::class.java)
            c.refresh(); SpringContextHolder().setApplicationContext(c)
            val tx=TransactionTemplate(manager)
            val user=SecurityUser(id="owner",name="owner",username="owner",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf("ADMIN"),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
            tx.executeWithoutResult {
                SchemaUtils.create(Users,Files,FileUsers,FileContents,FileFolders,FileChunkSessions,
                    CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionStaffEducations,
                    CollectionStaffEducationVersions,DigitalAcquisitionFileAccesses,CollectionEmergencyFileAccesses)
                SchemaUtils.create(*io.hizhengfu.cloud.collection.install.CollectionInstall("single").initTables().toTypedArray())
                SchemaUtils.create(Articles)
                Articles.insert { it[id]="cms-article" }
                Users.insert { it[id]="owner"; it[loginName]="owner"; it[name]="owner"; it[password]="test-only"; it[groupId]="museum"; it[enabled]=true }
                Collections.insert { it[id]="collection"; it[orgId]="other-museum"; it[name]="原藏品"; it[totalRegNo]="A1" }
                CollectionMedias.insert { it[id]="media"; it[orgId]="other-museum"; it[collectionId]="collection"; it[fileId]="old-file" }
            }
            try { body(c,tx) } finally { SecurityContextHolder.clearContext() }
        }
    }
    private fun file(name:String="evidence.txt")=MockMultipartFile("file",name,"text/plain","evidence".toByteArray())
    private fun request()=MockHttpServletRequest().apply { addParameter("publicUpload","false") }
    private fun controller(c:AnnotationConfigApplicationContext)=FileController(c.getBean(FileSystemStorageService::class.java),c.getBean(FileService::class.java),org.mockito.Mockito.mock(FileOrphanCleanupService::class.java),c.getBean(FileChunkSessionService::class.java))

    @ParameterizedTest @ValueSource(strings=["multipart","quick","folder","chunk","chunk-folder"])
    fun `unknown future business id cannot be populated through public upload`(variant:String)=fixture { c,tx ->
        val target="999999999999999999"
        val uploaded=c.getBean(FileSystemStorageService::class.java).upload(file=file(),contentId=null)!!
        tx.executeWithoutResult { FileFolders.insert { it[contentId]=target; it[folder]="future" } }
        var events=0; c.addApplicationListener { if(it is io.hizhengfu.cloud.event.FileUploadEvent) events++ }
        when(variant) {
            "multipart" -> assertThrows(BadRequestException::class.java) { controller(c).upload(file(),target,null,request()) }
            "quick" -> assertThrows(BadRequestException::class.java) { controller(c).quickUpload(io.hizhengfu.cloud.sys.file.domain.QuickUploadRequest(hash=uploaded.hash!!,name="evidence.txt",contentId=target),request()) }
            "folder" -> assertThrows(BadRequestException::class.java) { controller(c).upload(file("future/evidence.txt"),null,null,request()) }
            else -> {
                val sessions=c.getBean(FileChunkSessionService::class.java);val id=sessions.start();sessions.upload(id,0,file())
                assertThrows(BadRequestException::class.java) { sessions.finish(id,if(variant=="chunk-folder") "future/evidence.txt" else "evidence.txt",8,if(variant=="chunk-folder") null else target,null,emptyMap()) }
                tx.executeWithoutResult { assertEquals("OPEN",FileChunkSessions.selectAll().single()[FileChunkSessions.state]) }
                assertTrue(java.nio.file.Files.exists(root.resolve("files/temp/${id}_0")))
            }
        }
        tx.executeWithoutResult {
            assertEquals(1L,Files.selectAll().count());assertEquals(0L,FileContents.selectAll().count())
            Collections.insert { it[id]=target;it[orgId]="museum";it[name]="后来创建的记录";it[totalRegNo]="FUTURE" }
            assertTrue(CollectionMainOriginalFileSource().references(CollectionMainOriginalFileSource.BIZ,target).isEmpty())
        }
        assertEquals(0,events)
    }

    @ParameterizedTest @ValueSource(strings=["collection","media"])
    fun `public multipart cannot bind a museum target even with an admin login`(target:String)=fixture { c,tx ->
        var events=0; c.addApplicationListener { if(it is io.hizhengfu.cloud.event.FileUploadEvent) events++ }
        val mvc=MockMvcBuilders.standaloneSetup(controller(c)).addPlaceholderValue("apiPath","/api").build()
        val failure=assertThrows(Exception::class.java) { mvc.perform(multipart("/api/file/upload").file(file()).param("contentId",target).param("publicUpload","false").param("bindingOrigin","BUSINESS")) }
        assertTrue(generateSequence(failure as Throwable?) { it.cause }.any { it is BadRequestException })
        tx.executeWithoutResult { assertEquals(0L,Files.selectAll().count()); assertEquals(0L,FileContents.selectAll().count()) }
        assertEquals(0,events)
    }

    @ParameterizedTest @ValueSource(strings=["quick","existing","folder","chunk","chunk-folder"])
    fun `all public upload variants reject direct business bindings`(variant:String)=fixture { c,tx ->
        val storage=c.getBean(FileSystemStorageService::class.java)
        val uploaded=storage.upload(file=file(),contentId=null)!!
        if(variant=="existing") tx.executeWithoutResult { c.getBean(FileService::class.java).insertFileContent(uploaded.id!!,"collection","original") }
        tx.executeWithoutResult { FileFolders.insert { it[contentId]="collection"; it[folder]="mapped" } }
        var events=0; c.addApplicationListener { if(it is io.hizhengfu.cloud.event.FileUploadEvent) events++ }
        when(variant) {
            "quick" -> assertThrows(BadRequestException::class.java) { controller(c).quickUpload(io.hizhengfu.cloud.sys.file.domain.QuickUploadRequest(hash=uploaded.hash!!,name="evidence.txt",contentId="collection"),request()) }
            "existing" -> assertThrows(BadRequestException::class.java) { controller(c).upload(file("renamed.txt"),"collection",null,request()) }
            "folder" -> assertThrows(BadRequestException::class.java) { controller(c).upload(file("mapped/evidence.txt"),null,null,request()) }
            else -> {
                val sessions=c.getBean(FileChunkSessionService::class.java); val id=sessions.start(); sessions.upload(id,0,file())
                assertThrows(BadRequestException::class.java) { sessions.finish(id,if(variant=="chunk-folder") "mapped/evidence.txt" else "evidence.txt",8,if(variant=="chunk-folder") null else "collection",null,emptyMap()) }
                tx.executeWithoutResult { assertEquals("OPEN",FileChunkSessions.selectAll().single()[FileChunkSessions.state]) }
                assertTrue(java.nio.file.Files.exists(root.resolve("files/temp/${id}_0")))
            }
        }
        tx.executeWithoutResult {
            assertEquals(1L,Files.selectAll().count())
            assertEquals(if(variant=="existing") 1L else 0L,FileContents.selectAll().count())
            if(variant=="existing") assertEquals("original",FileContents.selectAll().single()[FileContents.aliasName])
        }
        assertEquals(0,events)
    }

    @Test fun `unbound public upload and existing business binding remain available`()=fixture { c,tx ->
        val result=controller(c).upload(file(),null,null,request()).data as io.hizhengfu.cloud.common.domain.FileVo
        assertNotNull(result.id)
        tx.executeWithoutResult { assertEquals(0L,FileContents.selectAll().count()) }
        c.getBean(FileService::class.java).insertFileContent(result.id!!,"collection","业务已核对")
        tx.executeWithoutResult { assertEquals("业务已核对",FileContents.selectAll().single()[FileContents.aliasName]) }
    }

    @ParameterizedTest @ValueSource(strings=["multipart","quick","folder","chunk","chunk-folder"])
    fun `existing ordinary module target remains available to all public upload variants`(variant:String)=fixture { c,tx ->
        tx.executeWithoutResult { FileFolders.insert { it[contentId]="cms-article";it[folder]="ordinary" } }
        when(variant) {
            "multipart" -> controller(c).upload(file(),"cms-article",null,request())
            "quick" -> {
                val uploaded=c.getBean(FileSystemStorageService::class.java).upload(file=file(),contentId=null)!!
                controller(c).quickUpload(io.hizhengfu.cloud.sys.file.domain.QuickUploadRequest(hash=uploaded.hash!!,name="evidence.txt",contentId="cms-article"),request())
            }
            "folder" -> controller(c).upload(file("ordinary/evidence.txt"),null,null,request())
            else -> {
                val sessions=c.getBean(FileChunkSessionService::class.java);val id=sessions.start();sessions.upload(id,0,file())
                val result=sessions.finish(id,if(variant=="chunk-folder") "ordinary/evidence.txt" else "evidence.txt",8,if(variant=="chunk-folder") null else "cms-article",null,emptyMap())
                assertNotNull(result.id)
                tx.executeWithoutResult { assertEquals("COMPLETE",FileChunkSessions.selectAll().single()[FileChunkSessions.state]) }
            }
        }
        tx.executeWithoutResult { assertEquals("cms-article",FileContents.selectAll().single()[FileContents.contentId]) }
    }

    @Test fun `unknown historic association is preserved but cannot authorize a new public write`()=fixture { c,tx ->
        val uploaded=c.getBean(FileSystemStorageService::class.java).upload(file=file(),contentId=null)!!
        tx.executeWithoutResult { FileContents.insert {it[fileId]=uploaded.id!!;it[contentId]="historic-unknown";it[aliasName]="旧关联待核对"} }
        assertThrows(BadRequestException::class.java) { c.getBean(FileService::class.java).insertFileContent(uploaded.id!!,"historic-unknown","新名称",FileBindingOrigin.PUBLIC_UPLOAD) }
        tx.executeWithoutResult { assertEquals("旧关联待核对",FileContents.selectAll().single()[FileContents.aliasName]) }
    }

    @Test fun `physical table without a current module declaration cannot authorize a public binding`()=fixture { c,tx ->
        tx.executeWithoutResult {
            val t=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
            t.exec("CREATE TABLE undeclared_content(id VARCHAR(64) PRIMARY KEY)")
            t.exec("INSERT INTO undeclared_content VALUES ('legacy-record')")
        }
        assertThrows(BadRequestException::class.java) { controller(c).upload(file(),"legacy-record",null,request()) }
        tx.executeWithoutResult { assertEquals(0L,Files.selectAll().count());assertEquals(0L,FileContents.selectAll().count()) }
    }

    @Test fun `identity is rechecked after actual binding insert and rolls back on change`()=fixture { c,tx ->
        val storage=c.getBean(FileSystemStorageService::class.java); val uploaded=storage.upload(file=file(),contentId=null)!!
        tx.executeWithoutResult {
            Articles.insert { it[id]="appearing" }
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("CREATE TRIGGER bind_target AFTER INSERT ON sys_file_content WHEN NEW.content_id = 'appearing' BEGIN UPDATE col_collection SET id = 'appearing' WHERE id = 'collection'; END")
        }
        assertThrows(BadRequestException::class.java) { c.getBean(FileService::class.java).insertFileContent(uploaded.id!!,"appearing",bindingOrigin=FileBindingOrigin.PUBLIC_UPLOAD) }
        tx.executeWithoutResult { assertEquals(0L,FileContents.selectAll().count()); assertEquals(0L,Collections.selectAll().where { Collections.id eq "appearing" }.count()) }
    }

    @Test fun `ordinary target removed by actual insert cannot retain the association`()=fixture { c,tx ->
        val uploaded=c.getBean(FileSystemStorageService::class.java).upload(file=file(),contentId=null)!!
        tx.executeWithoutResult {
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("CREATE TRIGGER remove_target AFTER INSERT ON sys_file_content BEGIN DELETE FROM ordinary_article WHERE id=NEW.content_id; END")
        }
        assertThrows(BadRequestException::class.java) { c.getBean(FileService::class.java).insertFileContent(uploaded.id!!,"cms-article",bindingOrigin=FileBindingOrigin.PUBLIC_UPLOAD) }
        tx.executeWithoutResult { assertEquals(0L,FileContents.selectAll().count());assertEquals(1L,Articles.selectAll().count()) }
    }

    @Test fun `ordinary record cannot override an existing museum identity with the same id`()=fixture { c,tx ->
        tx.executeWithoutResult { Articles.insert { it[id]="collection" } }
        assertThrows(BadRequestException::class.java) { controller(c).upload(file(),"collection",null,request()) }
        tx.executeWithoutResult { assertEquals(0L,Files.selectAll().count());assertEquals(0L,FileContents.selectAll().count()) }
    }
}
