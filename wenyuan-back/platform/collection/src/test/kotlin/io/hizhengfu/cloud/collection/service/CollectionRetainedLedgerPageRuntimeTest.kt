package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileReadAccess
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileContents
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.RolePermissions
import io.hizhengfu.cloud.sys.system.dao.GroupDao
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

class CollectionRetainedLedgerPageRuntimeTest {
    @TempDir lateinit var root: Path
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    class Transactions

    @ParameterizedTest @ValueSource(strings=["missing", "changed", "digest"])
    fun `unavailable originals keep frozen details readable while invalid metadata and downloads still reject`(damage: String) {
        val manager=SpringTransactionManager(SQLiteDataSource().apply { url="jdbc:sqlite:${root.resolve("retained.db")}" }, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { context ->
            context.beanFactory.registerSingleton("transactionManager",manager)
            context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            context.beanFactory.registerSingleton("scope",CollectionScopeResolver("multi",""))
            context.beanFactory.registerSingleton("localStorage",LocalFileObjectStorage(root.toString()))
            context.beanFactory.registerSingleton("binder",mock(BusinessAttachmentBinder::class.java).also { binder ->
                `when`(binder.resolveSingle(anyString())).thenAnswer { it.getArgument<String>(0) }
            })
            context.register(Transactions::class.java,FileReadAccess::class.java,
                CollectionPermanentArchiveService::class.java,CollectionRetainedLedgerService::class.java)
            context.refresh()
            SpringContextHolder().setApplicationContext(context)
            val tx=TransactionTemplate(manager)
            fun <T> db(body:()->T):T=tx.execute { body() }
            val archives=context.getBean(CollectionPermanentArchiveService::class.java)
            val service=context.getBean(CollectionRetainedLedgerService::class.java)
            val user=SecurityUser(id="reader",name="查阅人员",username="reader",password="",groupId="source",
                groupIds=setOf("source"),postIds=emptySet(),dataType=0,roles=emptySet(),
                authorities=listOf(SimpleGrantedAuthority(CollectionConst.SELECT)),enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
            try {
                listOf("affected","healthy").forEach { java.nio.file.Files.writeString(root.resolve("$it.txt"),"ORIGINAL") }
                db {
                    originalReaderSchema()
                    SchemaUtils.create(CollectionOrgs,CollectionRetainedLedgers,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FileContents)
                    listOf("source","target").forEach { org ->
                        CollectionOrgDao.new(org) { name=org; code=org; systemGroupId=org }
                        GroupDao.new(org) { name=org; type='1'; parentId="0"; parentIds="0," }
                    }
                    originalReader("reader",CollectionConst.SELECT,"source")
                    listOf("affected","healthy").forEach { key -> FileDao.new(key) { name="$key.txt"; path="$key.txt"; hash=key; size=8; ext="txt" } }
                    listOf("affected","healthy","legacy","foreign").forEachIndexed { index,key ->
                        val org=if(key=="foreign") "target" else "source"
                        val target=if(org=="source") "target" else "source"
                        val file=if(key=="affected") "affected" else "healthy"
                        val snapshot=CollectionRetainedLedgerSnapshot(CollectionVo(id="piece-$key",orgId=org,totalRegNo="REG-$key",name="留存-$key"),
                            originalFileIds=listOf(file),identity=if(key=="legacy") null else CollectionRetainedLedgerIdentity(org,"piece-$key","transfer-$key",target))
                        val row=CollectionRetainedLedgerDao.new(key) {
                            orgId=org; collectionId="piece-$key"; transferId="transfer-$key"; totalRegNo="REG-$key"
                            name="留存-$key"; targetOrgId=target; archiveId=""; this.snapshot=snapshot
                            createTime=LocalDateTime.of(2026,9,1,12,0).plusMinutes(index.toLong())
                        }
                        row.archiveId=archives.seal(org,"COLLECTION_RETAINED_LEDGER",key,"TRANSFERRED",snapshot,file)
                    }
                }
                val archiveId=db { CollectionRetainedLedgerDao["affected"].archiveId }
                assertEquals(true,service.get("affected")["bindingVerified"])
                when(damage) {
                    "missing" -> java.nio.file.Files.delete(root.resolve("affected.txt"))
                    "changed" -> java.nio.file.Files.writeString(root.resolve("affected.txt"),"CHANGED!")
                    else -> db { CollectionPermanentArchiveDao[archiveId].digest="invalid-digest" }
                }
                val before=db { CollectionRetainedLedgerDao.all().map { it.id.value to it.snapshot }.toMap() }
                for(size in listOf("0","10")) {
                    val page=service.findPage(MockHttpServletRequest().apply { setParameter("pageSize",size) })
                    assertEquals(3L,page.total)
                    @Suppress("UNCHECKED_CAST")
                    val rows=(page.list as List<Map<String,Any?>>).associateBy { it["id"] }
                    assertEquals(setOf("affected","healthy","legacy"),rows.keys)
                    val affected=rows.getValue("affected")
                    assertNull(affected["bindingVerified"])
                    assertNotNull(affected["evidenceIssue"])
                    if(damage=="missing") assertTrue(affected["evidenceIssue"].toString().contains("文件缺失（文件编号：affected）"))
                    assertEquals(true,rows.getValue("healthy")["bindingVerified"])
                    assertEquals(false,rows.getValue("legacy")["bindingVerified"])
                    assertNull(rows.getValue("healthy")["evidenceIssue"])
                    assertNull(rows.getValue("legacy")["evidenceIssue"])
                    assertTrue(rows.values.none { "snapshot" in it || "archives" in it || "files" in it })
                }
                val filtered=service.findPage(MockHttpServletRequest().apply { setParameter("keyword","REG-affected"); setParameter("pageSize","1") })
                assertEquals(1L,filtered.total)
                @Suppress("UNCHECKED_CAST")
                val filteredRows=filtered.list as List<Map<String,Any?>>
                assertEquals("affected",filteredRows.single()["id"])
                if(damage=="digest") assertThrows(BadRequestException::class.java) { service.get("affected") }
                else {
                    val detail=service.get("affected")
                    assertNotNull(detail["evidenceIssue"])
                    assertNull(detail["bindingVerified"])
                    assertEquals(before.getValue("affected"),detail["snapshot"])
                    val rows=detail["archives"] as List<Map<String,Any?>>
                    assertTrue((rows.single()["files"] as List<*>).isEmpty())
                }
                assertThrows(BadRequestException::class.java) { service.requireArchivedOriginal("affected",archiveId,"affected") }
                assertThrows(BadRequestException::class.java) { service.get("foreign") }
                assertEquals(true,service.get("healthy")["bindingVerified"])
                assertEquals(before,db { CollectionRetainedLedgerDao.all().map { it.id.value to it.snapshot }.toMap() })
                db { RolePermissions.deleteAll() }
                assertThrows(BadRequestException::class.java) { service.findPage(MockHttpServletRequest()) }
            } finally { SecurityContextHolder.clearContext() }
        }
    }
}
