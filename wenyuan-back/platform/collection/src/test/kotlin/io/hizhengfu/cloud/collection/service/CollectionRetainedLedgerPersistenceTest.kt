package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.mock.web.MockHttpServletRequest
import java.time.LocalDateTime

class CollectionRetainedLedgerPersistenceTest {
    @TempDir lateinit var originalRoot: java.nio.file.Path
    private fun login(orgId:String) {
        val user=SecurityUser(id="user-$orgId",name="馆方人员",username="user-$orgId",password="",groupId=orgId,groupIds=setOf(orgId),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.SELECT)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    @AfterEach fun cleanup()=SecurityContextHolder.clearContext()
    @Test fun `original museum retains immutable ledger and attachments after recipient edits current record`() {
        val original=originalRoot.resolve("files/proof.jpg")
        java.nio.file.Files.createDirectories(original.parent)
        java.nio.file.Files.writeString(original,"ORIGINAL")
        val db=Database.connect("jdbc:sqlite:file:retained-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(io.hizhengfu.cloud.collection.dao.CollectionLedgerStatEvents,Collections,CollectionOrgs,CollectionRegistrationNumbers,CollectionLedgerLogs,CollectionCatalogs,CollectionCatalogVersions,
                CollectionRetainedLedgers,CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionMedias,CollectionAttachments,Files,io.hizhengfu.cloud.sys.file.dao.FileContents)
            listOf("source","target").forEach { id->CollectionOrgDao.new(id) { name=id;code=id;ownership=EnumOwnership.STATE } }
            actorSchema()
            listOf("source","target").forEach {org->
                io.hizhengfu.cloud.sys.system.dao.GroupDao.new(org){name=org;type='1';parentId="0";parentIds="0,"}
                originalReader("user-$org",CollectionConst.SELECT,org)
            }
            FileDao.new("proof") { name="原始影像.jpg";path="/files/proof.jpg";hash="original";size=100;ext="jpg" }
            val node=CollectionDao.new("object") { orgId="source";totalRegNo="000123";name="原馆登记名称";lifeStatus=EnumCollectionStatus.IN_STORE;catalogStatus=EnumCatalogStatus.APPROVED;registeredAt=LocalDateTime.of(2020,1,1,0,0);coverImage="proof" }
            CollectionMediaDao.new { orgId="source";collectionId="object";fileId="proof";title="原馆影像" }
            val scope=CollectionScopeResolver("multi","")
            val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { call->call.getArgument<String>(0) } }
            val archives=CollectionPermanentArchiveService(binder,scope,LocalFileObjectStorage(originalRoot.toString()),unprotectedArchiveReadAccessProvider())
            val retained=CollectionRetainedLedgerService(scope,archives)
            val numbers=CollectionRegistrationNumberService()
            val catalog=CollectionCatalogService(scope,CollectionOperationLockService(),org.mockito.Mockito.mock(CollectionGradingService::class.java),org.mockito.Mockito.mock(AccessionReceiptService::class.java),binder)
            val service=CollectionService(scope,binder,numbers,CollectionOperationLockService(),catalog,mock(CollectionChangeService::class.java),retained,mock(CollectionDisclosureService::class.java))
            login("source")
            catalog.ensureCatalogFor(node)
            numbers.markUsed("source",node.totalRegNo,node.id.value,"初次登记")
            val retainedId=retained.retain(node,"target","transfer-1")
            val existing=CollectionRetainedLedgerDao[retainedId]
            val originalArchiveId=existing.archiveId
            existing.archiveId="missing-original"
            assertThrows(BadRequestException::class.java) { retained.retain(node,"target","transfer-1") }
            existing.archiveId=originalArchiveId
            val originalSnapshot=existing.snapshot
            existing.name="被改写的原馆名称"
            existing.snapshot=originalSnapshot.copy(collection=originalSnapshot.collection.copy(name="被改写的原馆名称"))
            assertThrows(BadRequestException::class.java) { retained.retain(node,"target","transfer-1") }
            existing.name="原馆登记名称";existing.snapshot=originalSnapshot
            assertEquals(retainedId,retained.retain(node,"target","transfer-1"))
            val timestamp=java.nio.file.Files.getLastModifiedTime(original)
            java.nio.file.Files.writeString(original,"CHANGED!")
            java.nio.file.Files.setLastModifiedTime(original,timestamp)
            val unavailable=retained.get(retainedId)
            assertEquals(originalSnapshot,unavailable["snapshot"])
            assertNotNull(unavailable["evidenceIssue"])
            assertThrows(BadRequestException::class.java) {retained.requireArchivedOriginal(retainedId,originalArchiveId,"proof")}
            assertThrows(BadRequestException::class.java) {retained.retain(node,"target","transfer-1")}
            assertEquals(1L,CollectionRetainedLedgerDao.count())
            java.nio.file.Files.writeString(original,"ORIGINAL")
            java.nio.file.Files.setLastModifiedTime(original,timestamp)
            assertEquals(retainedId,retained.retain(node,"target","transfer-1"))
            assertEquals(1L,CollectionRetainedLedgerDao.count())
            service.changeOrg("object","target","依法调拨","transfer-1")
            val row=CollectionRetainedLedgerDao.all().single()
            assertEquals("source",row.orgId)
            assertEquals("000123",row.totalRegNo)
            assertEquals("原馆登记名称",row.snapshot.collection.name)
            assertEquals("source",row.snapshot.collection.orgId)
            assertEquals("target",node.orgId)
            assertEquals("target",CollectionCatalogDao.all().single().orgId)
            assertEquals(2,CollectionLedgerLogDao.all().count())
            node.name="接收馆依法订正后的名称"
            assertEquals("原馆登记名称",(retained.get(row.id.value)["snapshot"] as CollectionRetainedLedgerSnapshot).collection.name)
            assertDoesNotThrow { retained.requireArchivedOriginal(row.id.value,row.archiveId,"proof") }
            assertThrows(BadRequestException::class.java) { retained.requireArchivedOriginal(row.id.value,"another-archive","proof") }
            assertThrows(BadRequestException::class.java) { retained.requireArchivedOriginal(row.id.value,row.archiveId,"unrelated-file") }
            val archive=CollectionPermanentArchiveDao[row.archiveId]
            val currentArchive=archive.snapshot
            val currentDigest=archive.digest
            val currentSnapshot=row.snapshot
            val pathSnapshot=currentSnapshot.copy(
                collection=currentSnapshot.collection.copy(coverImage="/files/proof.jpg"),
                media=currentSnapshot.media.map {it.copy(fileId="/files/proof.jpg")},
                originalFileIds=emptyList(),
            )
            row.snapshot=pathSnapshot
            archive.snapshot=JsonUtils.toJson(pathSnapshot)!!
            archive.digest=CollectionCareEvidence.digest(archive.snapshot)
            assertDoesNotThrow { retained.requireArchivedOriginal(row.id.value,row.archiveId,"proof") }
            row.snapshot=currentSnapshot
            archive.snapshot=currentArchive
            archive.digest=currentDigest
            assertThrows(BadRequestException::class.java) { numbers.requireAvailable("source","000123","another-object") }
            assertThrows(BadRequestException::class.java) { CollectionArchiveFileRetentionGuard().requireDeletable("proof") }
            val page=retained.findPage(MockHttpServletRequest().also { it.setParameter("pageSize","0") })
            assertEquals(1,page.data?.size)
            login("target")
            assertThrows(BadRequestException::class.java) { retained.get(row.id.value) }
            assertThrows(BadRequestException::class.java) { retained.requireArchivedOriginal(row.id.value,row.archiveId,"proof") }
            assertEquals(0,retained.findPage(MockHttpServletRequest().also { it.setParameter("pageSize","0") }).data?.size)
        }
    }
}
