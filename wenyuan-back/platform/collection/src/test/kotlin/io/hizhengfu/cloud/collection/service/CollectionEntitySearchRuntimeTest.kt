package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.nio.file.Path
import java.time.LocalDateTime

class CollectionEntitySearchRuntimeTest {
    @TempDir lateinit var dir: Path
    private fun fixture(body: (CollectionService) -> Unit) {
        val db = Database.connect("jdbc:sqlite:${dir.resolve("entities.db")}", "org.sqlite.JDBC")
        val user = SecurityUser(id="reader", name="reader", username="reader", password="", groupId="museum",
            groupIds=setOf("museum"), postIds=emptySet(), dataType=0, roles=emptySet(), authorities=emptyList(), enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        try { transaction(db) {
            originalReaderSchema(); originalReader("reader", CollectionConst.SELECT)
            SchemaUtils.create(CollectionOrgs, Collections, CollectionCategories, Warehouses, CollectionMedias, CollectionAttachments)
            listOf("museum", "other").forEach { id -> CollectionOrgDao.new(id) { name=id; code=id; systemGroupId=id } }
            body(CollectionService(CollectionScopeResolver("multi", "museum"), mock(BusinessAttachmentBinder::class.java),
                mock(CollectionRegistrationNumberService::class.java), mock(CollectionOperationLockService::class.java),
                mock(CollectionCatalogService::class.java), mock(CollectionChangeService::class.java),
                mock(CollectionRetainedLedgerService::class.java), mock(CollectionDisclosureService::class.java)))
        } } finally { SecurityContextHolder.clearContext() }
    }
    private fun record(id: String, number: String = id, status: EnumCollectionStatus = EnumCollectionStatus.IN_STORE,
        registered: Boolean = true, org: String = "museum", name: String = "青铜鼎-$id"): CollectionDao =
        CollectionDao.new(id) { orgId=org; totalRegNo=number; this.name=name; lifeStatus=status;
            nature=EnumCollectionNature.RELIC; catalogStatus=EnumCatalogStatus.PENDING
            registeredAt=if(registered) LocalDateTime.of(2026,1,1,0,0) else null }
    private fun rows(service: CollectionService, query: CollectionQueryVo, page: Int = 1, size: Int = 30): List<CollectionVo> {
        val request=MockHttpServletRequest().apply { addParameter("pageNum",page.toString()); addParameter("pageSize",size.toString()) }
        @Suppress("UNCHECKED_CAST")
        return (service.findPage(query, request).list as? List<CollectionVo>).orEmpty()
    }
    @Test fun `registration scope keeps loans registered and distinguishes pending uncertain and exited`() = fixture { service ->
        record("stored"); record("loan", status=EnumCollectionStatus.ON_LOAN); record("restoring", status=EnumCollectionStatus.RESTORING)
        record("pending", status=EnumCollectionStatus.PENDING, registered=false)
        record("uncertain", registered=false); record("exit", status=EnumCollectionStatus.DEREGISTERED)
        val expected=mapOf(EnumCollectionRegistrationScope.REGISTERED to setOf("stored","loan","restoring"),
            EnumCollectionRegistrationScope.PENDING to setOf("pending"), EnumCollectionRegistrationScope.UNVERIFIED to setOf("uncertain"),
            EnumCollectionRegistrationScope.DEREGISTERED to setOf("exit"))
        expected.forEach { (scope, ids) -> assertEquals(ids, rows(service,CollectionQueryVo().apply { registrationScope=scope }).map { it.id }.toSet()) }
        assertEquals(6,rows(service,CollectionQueryVo()).size)
        assertEquals(setOf("stored","loan","restoring","exit"), rows(service,CollectionQueryVo().apply { registeredDateStart=java.time.LocalDate.of(2026,1,1); registeredDateEnd=java.time.LocalDate.of(2026,1,1) }).map { it.id }.toSet())
    }
    @Test fun `workbench grading filters match ungraded and precious scopes`() = fixture { service ->
        record("registered-unrated").level=EnumRelicLevel.UNRATED
        record("unrated").level=EnumRelicLevel.UNRATED
        record("pending",status=EnumCollectionStatus.PENDING,registered=false).level=EnumRelicLevel.UNRATED
        record("uncertain",registered=false).level=EnumRelicLevel.UNRATED
        record("retired",status=EnumCollectionStatus.DEREGISTERED).level=EnumRelicLevel.UNRATED
        record("other").apply { nature=EnumCollectionNature.OTHER; level=EnumRelicLevel.UNRATED }
        record("first").level=EnumRelicLevel.FIRST
        record("second").level=EnumRelicLevel.SECOND
        record("third").level=EnumRelicLevel.THIRD
        record("general").level=EnumRelicLevel.GENERAL
        record("foreign",org="other").level=EnumRelicLevel.UNRATED
        assertEquals(setOf("registered-unrated","unrated","pending","uncertain"), rows(service, CollectionQueryVo().apply { relicLevelScope=EnumCollectionRelicLevelScope.UNGRADED }).map { it.id }.toSet())
        assertEquals(setOf("first","second","third"), rows(service, CollectionQueryVo().apply { registrationScope=EnumCollectionRegistrationScope.REGISTERED; relicLevelScope=EnumCollectionRelicLevelScope.PRECIOUS }).map { it.id }.toSet())
    }
    @Test fun `exact identifier ranks before newer partial names and pagination has no duplicate or missing record`() = fixture { service ->
        record("exact","000004").createTime=LocalDateTime.of(2025,1,1,0,0)
        repeat(8) { record("partial-$it","P-$it",name="资料000004-$it").createTime=LocalDateTime.of(2026,1,1,0,0) }
        val query=CollectionQueryVo().apply { keyword="000004"; searchSort=EnumCollectionSearchSort.RELEVANCE }
        assertEquals("exact",rows(service,query,1,3).first().id)
        val ids=(1..3).flatMap { rows(service,query,it,3).map { row -> row.id } }
        assertEquals(9,ids.toSet().size); assertEquals(9,ids.size)
        assertEquals(listOf("exact"),rows(service,CollectionQueryVo().apply { totalRegNo="000004"; numberMatch=EnumCollectionNumberMatch.EXACT }).map { it.id })
    }
    @Test fun `one thousand matching objects paginate without duplicates or omissions`() = fixture { service ->
        repeat(1000) { index -> record("sample-$index", name="大样本分页-$index").createTime=LocalDateTime.of(2026,1,1,0,0) }
        val query=CollectionQueryVo().apply { keyword="大样本分页"; searchSort=EnumCollectionSearchSort.RELEVANCE; registrationScope=EnumCollectionRegistrationScope.REGISTERED }
        val ids=(1..20).flatMap { rows(service,query,it,50).map { row -> row.id } }
        assertEquals(1000, ids.size); assertEquals(1000, ids.toSet().size)
        assertTrue(rows(service,query,21,50).isEmpty())
    }
    @Test fun `combined criteria work without a keyword and multiple terms are ANDed`() = fixture { service ->
        record("bronze").apply { era="商"; texture="青铜" }
        record("other",name="玉器").apply { nature=EnumCollectionNature.OTHER; texture="玉" }
        assertEquals(listOf("bronze"),rows(service,CollectionQueryVo().apply { nature=EnumCollectionNature.RELIC; era="商"; texture="青铜" }).map { it.id })
        assertEquals(listOf("bronze"),rows(service,CollectionQueryVo().apply { keyword="青铜 商" }).map { it.id })
        assertTrue(rows(service,CollectionQueryVo().apply { keyword="青铜 玉" }).isEmpty())
    }
    @Test fun `selected ids are rechecked against current permissions and museum`() = fixture { service ->
        record("local"); record("foreign",org="other")
        val query=CollectionQueryVo().apply { collectionIds="local,foreign" }
        assertEquals(listOf("local"),rows(service,query).map { it.id })
        UserDao["reader"].groupId="other"
        assertEquals(listOf("foreign"),rows(service,query).map { it.id })
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { rows(service,query) }
    }
    @Test fun `image and attachment predicates are correlated to collection and owning museum`() = fixture { service ->
        record("covered").coverImage="cover"; record("linked"); record("empty")
        CollectionMediaDao.new("media") { collectionId="linked"; orgId="museum"; fileId="image"; mediaType=EnumCollectionMediaType.FRONT }
        CollectionAttachmentDao.new("attachment") { collectionId="linked"; orgId="museum"; fileId="file"; name="原件" }
        CollectionMediaDao.new("wrong") { collectionId="empty"; orgId="other"; fileId="wrong"; mediaType=EnumCollectionMediaType.FRONT }
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
        assertEquals(setOf("covered","linked"),rows(service,CollectionQueryVo().apply { hasImage=true }).map { it.id }.toSet())
        assertEquals(listOf("empty"),rows(service,CollectionQueryVo().apply { hasImage=false }).map { it.id })
        assertEquals(listOf("linked"),rows(service,CollectionQueryVo().apply { hasAttachment=true }).map { it.id })
        val previews = rows(service,CollectionQueryVo()).associateBy { it.id }
        assertEquals("image", previews["linked"]?.previewImage)
        assertNull(previews["empty"]?.previewImage)
        assertNull(previews["linked"]?.coverImage)
    }
}
