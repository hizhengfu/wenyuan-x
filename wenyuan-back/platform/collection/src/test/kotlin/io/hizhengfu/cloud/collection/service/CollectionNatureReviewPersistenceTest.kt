package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.diagnostic.CollectionNatureDiagnosticCheck
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class CollectionNatureReviewPersistenceTest {
    private val scope = CollectionScopeResolver("single", "museum")
    private val review = CollectionNatureReviewService(scope, CollectionActorDirectory(scope))
    private val at = LocalDateTime.of(2026, 8, 1, 9, 0)
    private val context = StaticApplicationContext()

    @BeforeEach fun setup() {
        context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
        context.refresh(); SpringContextHolder().setApplicationContext(context)
        // 登录快照故意保留 ADMIN；业务必须读取关系表中的当前权限。
        val actor = SecurityUser(id="reader",name="reader",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(actor, null, actor.authorities)
    }
    @AfterEach fun cleanup() { SecurityContextHolder.clearContext(); context.close() }

    private fun fixture(block: () -> Unit) {
        val file = java.nio.file.Files.createTempFile("collection-nature-review-", ".db")
        try {
            val db = Database.connect("jdbc:sqlite:$file", driver="org.sqlite.JDBC")
            transaction(db) {
                SchemaUtils.create(Collections, CollectionOrgs, Loans, CollectionReturns, CollectionPermanentArchives)
                originalReaderSchema(); originalReader("reader", CollectionConst.SELECT)
                listOf("museum", "other").forEach { id -> CollectionOrgDao.new(id) { name=id; code=id } }
                block()
            }
        } finally { java.nio.file.Files.deleteIfExists(file) }
    }
    private fun collection(id: String, nature: EnumCollectionNature? = EnumCollectionNature.OTHER, org: String = "museum", formal: Boolean = true) =
        CollectionDao.new(id) { orgId=org; totalRegNo=id; name="具有科学价值的古人类化石待鉴定名称"; this.nature=nature
            level=if(nature==null || nature==EnumCollectionNature.OTHER) EnumRelicLevel.UNRATED else EnumRelicLevel.GENERAL
            if(formal) { catalogStatus=EnumCatalogStatus.APPROVED; registeredAt=at } }

    // 升级前永久快照的历史形状；不把这些线索模拟成正式鉴定、签署或行政事实。
    private fun archive(id: String, cid: String, loan: Boolean = true, wrapped: Boolean = false, org: String = "museum"): CollectionPermanentArchiveDao {
        val item = mapOf("collectionId" to cid, "objectKind" to "SCIENTIFIC_FOSSIL")
        val data = if(loan) mapOf("dossier" to mapOf("assessments" to listOf(item))) else mapOf("inspection" to mapOf("items" to listOf(item)))
        val json = JsonUtils.toJson(if(wrapped) mapOf("proposal" to data) else data)!!
        return CollectionPermanentArchiveDao.new(id) { orgId=org; bizType=if(loan) CollectionConst.BIZ_LOAN else CollectionConst.BIZ_RETURN; bizId=id
            phase=if(wrapped) "APPROVED_task" else if(loan) "PROPOSAL_CONFIRMED" else "INSPECTION_original"; snapshot=json; digest=CollectionCareEvidence.digest(json) }
    }
    @Test fun `all sealed loan and return proposal shapes remain discoverable after operational drafts are gone`() = fixture {
        repeat(4) { n -> collection("item$n"); archive("archive$n", "item$n", loan=n<2, wrapped=n%2==1) }
        val originals = CollectionPermanentArchiveDao.all().associate { it.id.value to (it.snapshot to it.digest) }
        val result = review.page()
        assertEquals(4, result.total); assertTrue(result.items.all { it.reasons.single().contains("历史借用或归还") })
        assertEquals(originals, CollectionPermanentArchiveDao.all().associate { it.id.value to (it.snapshot to it.digest) })
        assertTrue(CollectionDao.all().all { it.nature==EnumCollectionNature.OTHER })
        assertEquals(0L, LoanDao.count()); assertEquals(0L, CollectionReturnDao.count())
    }
    @Test fun `name alone cannot infer scientific nature and diagnostic remains explicit about evidence limits`() = fixture {
        collection("ordinary"); collection("draft", null, formal=false)
        assertEquals(0, review.page().total)
        val check = CollectionNatureDiagnosticCheck(review).check()
        assertEquals(EnumDeploymentCheckState.PASS, check.state)
        assertEquals(EnumCollectionNature.OTHER, CollectionDao["ordinary"].nature)
        org.junit.jupiter.api.Assertions.assertNull(CollectionDao["draft"].nature)
    }
    @Test fun `formal unknown nature and scientific ungraded objects are flagged while a resolved nature keeps old archives`() = fixture {
        collection("unknown", null); val fossil=collection("fossil", EnumCollectionNature.SCIENTIFIC_FOSSIL)
        fossil.level=EnumRelicLevel.UNRATED; val old=archive("legacy", "fossil"); val original=old.snapshot
        assertEquals(2, review.page().total)
        assertEquals(EnumDeploymentCheckState.FAIL, CollectionNatureDiagnosticCheck(review).check().state)
        fossil.level=EnumRelicLevel.GENERAL // 模拟另一个正式办理流程已更新主档；核查服务自身不写入。
        assertEquals(listOf("unknown"), review.page().items.map { it.collectionId })
        assertEquals(original, old.snapshot)
    }
    @Test fun `current membership and permissions override stale login and single museum default`() = fixture {
        collection("local", null); collection("foreign", null, "other")
        assertEquals(listOf("local"), review.page().items.map { it.collectionId })
        UserDao["reader"].groupId=null
        assertEquals(0, review.page().total)
        UserDao["reader"].groupId="museum"
        RolePermissions.deleteWhere { RolePermissions.permissionId eq CollectionConst.SELECT }
        assertThrows(BadRequestException::class.java) { review.page() }
        actorRole("reader", "fresh", setOf(CollectionConst.SELECT, CollectionConst.CROSS_ORG))
        assertEquals(2, review.page().total)
        UserDao["reader"].enabled=false
        assertThrows(BadRequestException::class.java) { review.page() }
    }
    @Test fun `transferred item can find source museum clues without exposing foreign objects or broken source content`() = fixture {
        collection("local"); collection("foreign", null, "other")
        archive("original-source", "local", org="other")
        archive("broken-foreign", "foreign", org="other").snapshot="SECRET FOREIGN CONTENT"
        val result=review.page()
        assertEquals(listOf("local"), result.items.map { it.collectionId }); assertEquals(0, result.unresolvedArchives)
        assertFalse(JsonUtils.toJson(result)!!.contains("SECRET"))
        assertEquals(1, review.inspect().unresolvedArchives)
    }
    @Test fun `invalid JSON or digest leaves explicit unresolved count instead of an apparent clean result`() = fixture {
        collection("one"); val broken=archive("broken", "one"); broken.snapshot="private invalid original"
        val malformed=archive("malformed", "one"); malformed.snapshot="["; malformed.digest=CollectionCareEvidence.digest(malformed.snapshot)
        val result=review.page()
        assertEquals(0, result.total); assertEquals(2, result.unresolvedArchives)
        assertFalse(JsonUtils.toJson(result)!!.contains("private"))
        assertEquals(EnumDeploymentCheckState.FAIL, CollectionNatureDiagnosticCheck(review).check().state)
    }
    @Test fun `pagination includes issues across database scan batches without converting historical rows`() = fixture {
        repeat(205) { n -> collection("item"+n.toString().padStart(3,'0'), null) }
        val second=review.page(7,30)
        assertEquals(205, second.total); assertEquals(25, second.items.size)
        assertEquals("item180", second.items.first().collectionId); assertEquals("item204", second.items.last().collectionId)
        assertTrue(review.page(8,30).items.isEmpty())
        assertThrows(BadRequestException::class.java) { review.page(0,30) }
        assertThrows(BadRequestException::class.java) { review.page(1,201) }
        assertTrue(CollectionDao.all().all { it.nature==null })
    }
}
