package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import io.hizhengfu.cloud.sys.file.service.storage.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.mock.web.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** Uses the byte service delegated to by the real joint-safety selected-stage original controller. */
class CustodySharedOriginalReadRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true) class Transactions
    private val content = "DEV-SYNTHETIC-CUSTODY-SHARED-ORIGINAL-20261004".toByteArray()

    @Test
    fun `custody original without shared outgoing reference needs no outgoing permission`() = fixture { r ->
        val selected = r.seal("CUSTODY_P_123")
        r.permission(CollectionConst.CARE)
        r.read(selected, true)
    }

    @ParameterizedTest @ValueSource(strings = [CollectionConst.BIZ_REQUISITION, CollectionConst.BIZ_LOAN])
    fun `selected custody download requires outgoing read duty for its shared original`(outgoingBusiness: String) = fixture { r ->
        val selected = r.seal("CUSTODY_P_123")
        r.seal("OUT_EXECUTED", outgoingBusiness)
        r.read(selected, true) // Two actual current ordinary duties initially authorize these bytes.
        r.permission(CollectionConst.CARE)
        r.read(selected, false) // A selected custody archive must not mask the same file's outgoing duty.
        r.permission(CollectionConst.CARE, CollectionConst.STORAGE)
        r.read(selected, true)
    }

    @Test
    fun `unrelated selected care archive keeps its own duty despite the same outgoing reference`() = fixture { r ->
        val selected = r.seal("CHECKED", CollectionConst.BIZ_CARE)
        r.seal("OUT_EXECUTED")
        r.permission(CollectionConst.CARE)
        r.read(selected, true)
    }

    private inner class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate) {
        fun db(body: () -> Unit) { tx.execute { body() } }
        fun seal(phase: String, businessType: String = CollectionConst.BIZ_REQUISITION): String = c.getBean(CollectionPermanentArchiveService::class.java).seal(
            "museum", businessType, "requisition", phase,
            mapOf("requisitionId" to "requisition", "synthetic" to true, "phase" to phase), "proof",
        )
        fun permission(vararg values: String) = db {
            RolePermissions.deleteAll()
            values.forEach { value ->
                RolePermissions.insert { it[roleId] = EntityID("reader-role", Roles); it[permissionId] = EntityID(value, Permissions) }
            }
        }
        fun read(archiveId: String, allowed: Boolean) {
            val originals = c.getBean(FileOriginalDownloadService::class.java)
            for (protocol in listOf("ordinary", "range", "conditional")) {
                val request = MockHttpServletRequest("GET", "/api/v1/collection/joint-safety/requisition/original/requisition/CUSTODY_P_123/proof")
                if (protocol == "range") request.addHeader("Range", "bytes=0-3")
                if (protocol == "conditional") request.addHeader("If-Modified-Since", "Tue, 01 Sep 2026 10:00:00 GMT")
                RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
                val response = MockHttpServletResponse()
                if (allowed) {
                    originals.downloadFromArchive("proof", archiveId, response)
                    assertEquals(200, response.status)
                    assertArrayEquals(content, response.contentAsByteArray)
                } else {
                    assertThrows(BadRequestException::class.java) {
                        originals.downloadFromArchive("proof", archiveId, response)
                    }
                    assertEquals(0, response.contentAsByteArray.size)
                    assertNotEquals(304, response.status)
                }
                assertEquals("private, no-store", response.getHeader("Cache-Control"))
            }
        }
    }

    private fun fixture(body: (Runtime) -> Unit) {
        val manager = SpringTransactionManager(SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("custody-shared.db")}" }, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("scope", CollectionScopeResolver("single", "museum"))
            c.beanFactory.registerSingleton("local", LocalFileObjectStorage(dir.toString()))
            val binder = mock(BusinessAttachmentBinder::class.java)
            `when`(binder.resolveSingle(anyString())).thenAnswer { it.getArgument<String>(0) }
            c.beanFactory.registerSingleton("binder", binder)
            c.registerBean(CollectionBusinessArchiveReadPolicy::class.java, java.util.function.Supplier {
                CollectionBusinessArchiveReadPolicy(c.getBean(CollectionFileReadAuthorization::class.java), mock(CollectionDisclosureService::class.java))
            })
            c.registerBean(FileObjectStorageService::class.java, java.util.function.Supplier {
                FileObjectStorageService(c.getBean(LocalFileObjectStorage::class.java), mock(FileStorageConfigReader::class.java),
                    mock(RustFsStorageClient::class.java), c.getBean(FileReadAccess::class.java))
            })
            c.register(Transactions::class.java, CollectionActorDirectory::class.java, CollectionFileReadAuthorization::class.java,
                FileReadAccess::class.java, CollectionPermanentArchiveService::class.java, CollectionPermanentArchiveFileReadGuard::class.java,
                FileOriginalDownloadService::class.java)
            c.refresh(); SpringContextHolder().setApplicationContext(c)
            val r = Runtime(c, TransactionTemplate(manager))
            r.db {
                SchemaUtils.create(Users, Roles, RoleUsers, UserGroups, Groups, Permissions, RolePermissions, CollectionOrgs,
                    CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, FileContents, Requisitions)
                GroupDao.new("museum") { name = "synthetic museum"; type = '1'; parentId = "0"; parentIds = "0," }
                CollectionOrgDao.new("museum") { name = "synthetic museum"; code = "synthetic"; systemGroupId = "museum" }
                UserDao.new("reader") { name = "ordinary reader"; loginName = "reader"; password = "unused"; enabled = true; groupId = "museum" }
                Roles.insert { it[id] = "reader-role"; it[code] = "DEV_CUSTODY_READER"; it[name] = "ordinary reader" }
                RoleUsers.insert { it[userId] = "reader"; it[roleId] = "reader-role" }
                listOf(CollectionConst.CARE, CollectionConst.STORAGE).forEach { permission ->
                    Permissions.insert { it[id] = permission; it[code] = permission; it[name] = permission; it[parentId] = "0"; it[parentIds] = "0," }
                }
                FileDao.new("proof") {
                    name = "DEV-SYNTHETIC-custody-shared.txt"; path = "proof.txt"; ext = "txt"; size = content.size.toLong()
                    hash = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(content)); secure = false
                }
            }
            java.nio.file.Files.write(dir.resolve("proof.txt"), content)
            val user = SecurityUser(id = "reader", name = "reader", username = "reader", password = "", groupId = "museum",
                groupIds = setOf("museum"), postIds = emptySet(), dataType = 0, roles = emptySet(),
                authorities = listOf(SimpleGrantedAuthority(CollectionConst.CARE), SimpleGrantedAuthority(CollectionConst.STORAGE)), enabled = true)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
            try {
                r.permission(CollectionConst.CARE, CollectionConst.STORAGE)
                body(r)
                assertArrayEquals(content, java.nio.file.Files.readAllBytes(dir.resolve("proof.txt")))
            } finally { RequestContextHolder.resetRequestAttributes(); SecurityContextHolder.clearContext() }
        }
    }
}
