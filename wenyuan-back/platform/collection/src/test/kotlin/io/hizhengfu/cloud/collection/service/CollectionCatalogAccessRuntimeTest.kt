package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDate

class CollectionCatalogAccessRuntimeTest {
    @Test fun `catalog actor filters preserve museum scope and draft metadata is not a submission`() = fixture { r ->
        r.grant(CollectionConst.SELECT)
        r.db {
            val a=r.catalog();a.auditBy="reader";a.submittedBy="editor"
            val b=CollectionDao.new("second") {orgId="museum";name="第二件";totalRegNo="SECOND";lifeStatus=EnumCollectionStatus.PENDING;catalogStatus=EnumCatalogStatus.DRAFT}
            CollectionCatalogDao.new("second-catalog") {orgId="museum";collectionId=b.id.value;catalogStatus=EnumCatalogStatus.DRAFT;content=CollectionCatalogMapper.fromCollection(b.toModel());auditBy="editor";submittedBy="reader"}
            r.catalog("other").auditBy="reader"
        }
        assertEquals(2L,r.page().total)
        fun ids(query: CollectionCatalogQueryVo) = (r.page(query).list as List<CollectionCatalogVo>).map {it.collectionId}
        assertEquals(listOf("item-museum"),ids(CollectionCatalogQueryVo().apply {auditedByMe=true}))
        assertEquals(listOf("second"),ids(CollectionCatalogQueryVo().apply {auditBy="editor"}))
        assertEquals(listOf("second"),ids(CollectionCatalogQueryVo().apply {submittedByMe=true}))
        val draft=r.service.get("item-museum")
        assertNull(draft.submittedBy);assertNull(draft.submittedAt)
        assertFalse(draft.receiptLinked)
        assertEquals(1L,draft.mediaCount)
    }
    @Test
    fun `other collection draft normalizes absent grade without accepting relic grades`() = fixture { r ->
        r.grant(CollectionConst.CATALOG_EDIT)
        val content = r.content().copy(nature = EnumCollectionNature.OTHER, level = null)
        r.service.save("item-museum", content)
        assertEquals(EnumRelicLevel.UNRATED, r.service.get("item-museum").content.level)
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { r.service.save("item-museum", content.copy(level = EnumRelicLevel.FIRST)) }
        assertEquals(before, r.snapshot())
    }
    @TempDir lateinit var root: Path
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true) class Transactions
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate) {
        val service = c.getBean(CollectionCatalogService::class.java)
        val scope = c.getBean(CollectionScopeResolver::class.java)
        val binder = c.getBean(BusinessAttachmentBinder::class.java)
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun grant(vararg permissions: String) = db { RolePermissions.deleteAll(); actorRole("reader", "CURRENT_CATALOG", permissions.toSet()) }
        fun damage(kind: String) = db { when(kind) {
            "revoked" -> RolePermissions.deleteAll()
            "disabled" -> UserDao["reader"].enabled = false
            "role_disabled" -> RoleDao.find { Roles.code eq "FILE_reader" }.single().enabled = false
            "group_disabled" -> GroupDao["museum"].enabled = false
            "moved" -> UserDao["reader"].groupId = "other"
            "configured" -> ReflectionTestUtils.setField(scope, "defaultOrgId", "other")
            "museum_disabled" -> CollectionOrgDao["museum"].orgStatus = EnumOrgStatus.DISABLED
            else -> error(kind)
        } }
        fun request() = MockHttpServletRequest().apply { setParameter("pageSize", "0") }
        fun page(query: CollectionCatalogQueryVo = CollectionCatalogQueryVo()) = service.findPage(query, request())
        fun content() = db { CollectionCatalogMapper.fromCollection(CollectionDao["item-museum"].toModel()) }
        fun snapshot() = db { listOf(Collections, CollectionCatalogs, CollectionCatalogVersions, CollectionLedgerLogs, CollectionLedgerStatEvents).map { t ->
            t.selectAll().orderBy(t.id).map { row -> t.columns.map { row[it] } }
        } }
        fun entry(name: String): Any? = when(name) {
            "page" -> page()
            "get" -> service.get("item-museum")
            "versions" -> service.versions("item-museum")
            "card" -> service.card("item-museum")
            "save" -> service.save("item-museum", content())
            "submit" -> service.submit("item-museum")
            "review" -> service.review("item-museum", CollectionCatalogReviewVo(EnumCatalogStatus.REJECTED, "核对原件"))
            "revision" -> service.startRevision("item-museum")
            else -> error(name)
        }
        fun catalog(org: String = "museum") = CollectionCatalogDao.find { CollectionCatalogs.collectionId eq "item-$org" }.single()
        fun seedSubmitted() = db {
            val catalog = catalog(); catalog.catalogStatus = EnumCatalogStatus.SUBMITTED; catalog.version = 1; catalog.submittedVersion = 1
            catalog.submittedBy = "editor"; CollectionDao["item-museum"].catalogStatus = EnumCatalogStatus.SUBMITTED
            CollectionCatalogVersionDao.new("submitted") {
                orgId = "museum"; catalogId = catalog.id.value; collectionId = catalog.collectionId
                version = 1; catalogStatus = EnumCatalogStatus.SUBMITTED; content = catalog.content; submittedBy = "editor"
            }
        }
    }
    private fun fixture(mode: String = "single", body: (Runtime) -> Unit) {
        val old = SecurityUser(id="reader", name="旧管理员", username="reader", password="", groupId="museum", groupIds=setOf("museum"),
            postIds=emptySet(), dataType=0, roles=setOf(EnumRole.ADMIN.value), enabled=true,
            authorities=listOf("ADMIN", CollectionConst.CROSS_ORG, CollectionConst.CATALOG_EDIT, CollectionConst.CATALOG_AUDIT).map(::SimpleGrantedAuthority))
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(old,null,old.authorities)
        val manager = SpringTransactionManager(SQLiteDataSource().apply { url="jdbc:sqlite:${root.resolve("catalog.db")}" }, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("scope", CollectionScopeResolver(mode,"museum"))
            c.beanFactory.registerSingleton("binder", mock(BusinessAttachmentBinder::class.java).also { binder -> `when`(binder.resolveSingle(anyString())).thenAnswer { it.getArgument<String>(0) } })
            c.beanFactory.registerSingleton("grading", mock(CollectionGradingService::class.java))
            c.beanFactory.registerSingleton("accession", mock(AccessionReceiptService::class.java))
            c.register(Transactions::class.java, CollectionCatalogService::class.java, CollectionOperationLockService::class.java)
            c.refresh(); SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager))
            try {
                r.db {
                    originalReaderSchema()
                    SchemaUtils.create(CollectionOrgs, Collections, CollectionCatalogs, CollectionCatalogVersions, CollectionLedgerLogs,
                        CollectionLedgerStatEvents, CollectionCategories, Warehouses, CollectionMedias, CollectionAttachments, CollectionOperationLocks)
                    CollectionCategoryDao.new("category") { name="瓷器"; code="03"; parentId="0" }
                    originalReader("reader", CollectionConst.CATALOG_EDIT)
                    actorRole("reader", "AUDIT",setOf(CollectionConst.CATALOG_AUDIT, CollectionConst.SELECT))
                    for (org in listOf("museum","other")) {
                        CollectionOrgDao.new(org) { name=org; code=org; systemGroupId=org }
                        WarehouseDao.new("store-$org") { orgId=org;name="保管库房";sceneType=EnumLocationScene.STORAGE }
                        CollectionDao.new("item-$org") {
                            orgId=org; totalRegNo="REG-$org"; name="藏品-$org"; categoryId="category"; era="清"; texture="瓷";textureCategory1="单一质地";textureCategory2="无机质/非金属材料"
                            quantity=1; unit="件"; source=EnumSource.OLD; level=EnumRelicLevel.GENERAL; nature=EnumCollectionNature.RELIC
                            completeness=EnumCompleteness.COMPLETE; acquireDate=LocalDate.of(2020,1,1)
                            classNo="C-$org";actualQuantity=1;actualQuantityUnit="件";entryDate=LocalDate.of(2020,1,2)
                            dimensions=DimensionVo(height="12");dimensionUnit="cm";weight="100";weightUnit="g"
                            completeStatus="完好";preserveState=EnumPreserve.A;feature="实物纹饰特征";coverImage="front";warehouseId="store-$org"
                            registrationDetails=CollectionRegistrationDetailsVo(coverIsFrontView=true,weightExcludesAccessories=true,eraMeaning=EnumCollectionEraMeaning.MANUFACTURED,eraNotation=EnumCollectionEraNotation.CHINESE_HISTORY)
                            catalogStatus=EnumCatalogStatus.DRAFT; lifeStatus=EnumCollectionStatus.PENDING
                        }.also(r.service::ensureCatalogFor)
                    }
                }
                body(r)
            } finally { SecurityContextHolder.clearContext() }
        }
    }
    @ParameterizedTest @ValueSource(strings=["page","get","versions","card","save","submit","review","revision"])
    fun `all public catalog entries reject current revoked or disabled identity before mutation`(entry: String) = fixture { r ->
        r.grant(); var before=r.snapshot(); assertThrows(BadRequestException::class.java) { r.entry(entry) }; assertEquals(before,r.snapshot())
        r.grant(CollectionConst.SELECT,CollectionConst.CATALOG_EDIT,CollectionConst.CATALOG_AUDIT); r.damage("disabled")
        before=r.snapshot(); assertThrows(BadRequestException::class.java) { r.entry(entry) }; assertEquals(before,r.snapshot())
    }
    @ParameterizedTest @CsvSource("save,moved","submit,moved","review,moved","save,configured","submit,configured","review,configured",
        "save,museum_disabled","submit,museum_disabled","review,museum_disabled","save,group_disabled","submit,group_disabled","review,group_disabled")
    fun `current museum or enabled membership is required for catalog writes`(entry:String,damage:String)=fixture { r ->
        r.damage(damage); val before=r.snapshot(); assertThrows(BadRequestException::class.java) { r.entry(entry) };assertEquals(before,r.snapshot())
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `counts filters details and historical initialization share explicit current scope`(mode:String)=fixture(mode) { r ->
        val page=r.page();assertEquals(1L,page.total);assertEquals("item-museum",(page.list as List<CollectionCatalogVo>).single().collectionId)
        assertThrows(BadRequestException::class.java) { r.service.get("item-other") }
        assertThrows(BadRequestException::class.java) { r.page(CollectionCatalogQueryVo().apply {orgId="other"}) }
        assertEquals(0L,r.page(CollectionCatalogQueryVo().apply {keyword="藏品-other"}).total)
        r.db { CollectionCatalogs.deleteAll() };r.page()
        assertEquals(listOf("museum"),r.db { CollectionCatalogDao.all().map {it.orgId} })
    }
    @Test fun `multi museum current reassignment and explicit cross reads do not initialize foreign catalogs`()=fixture("multi") { r ->
        r.damage("moved");assertEquals("other",(r.page().list as List<CollectionCatalogVo>).single().orgId)
        r.grant(CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        assertEquals(2L,r.page().total)
        r.db { r.catalog().delete() };val before=r.snapshot()
        assertThrows(BadRequestException::class.java) {r.service.get("item-museum")};assertEquals(before,r.snapshot())
        assertEquals(1L,r.page().total)
        assertThrows(BadRequestException::class.java) {r.entry("save")}
    }
    @Test fun `historical cross read never initializes a disabled museum and single mode stays configured`()=fixture("multi") {r ->
        r.grant(CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.damage("museum_disabled")
        assertEquals("museum",r.service.get("item-museum").orgId)
        r.db {r.catalog().delete()};val before=r.snapshot();r.page();assertEquals(before,r.snapshot())
        assertThrows(BadRequestException::class.java){r.service.get("item-museum")}
        ReflectionTestUtils.setField(r.scope,"mode","single")
        assertThrows(BadRequestException::class.java){r.service.get("item-other")}
    }
    @Test fun `current edit and audit duties remain separate and approval consumes submitted snapshot`()=fixture {r ->
        r.grant(CollectionConst.CATALOG_EDIT);r.service.save("item-museum",r.content().copy(formalName="完整新定名"));r.service.submit("item-museum")
        assertThrows(BadRequestException::class.java){r.entry("review")}
        r.grant(CollectionConst.CATALOG_AUDIT)
        assertThrows(BadRequestException::class.java){r.service.review("item-museum",CollectionCatalogReviewVo(EnumCatalogStatus.APPROVED,"同意"))}
        r.db {r.catalog().submittedBy="editor";CollectionCatalogVersionDao.all().single().submittedBy="editor"}
        r.service.review("item-museum",CollectionCatalogReviewVo(EnumCatalogStatus.APPROVED,"核对实际原件后通过"))
        assertEquals("完整新定名",r.db {CollectionDao["item-museum"].name});assertNotNull(r.db {CollectionDao["item-museum"].registeredAt})
        r.db {
            val log = CollectionLedgerLogDao.all().single { it.field == "lifeStatus" }
            assertEquals(EnumLedgerAction.CORRECT, log.action)
            assertEquals(EnumCollectionStatus.PENDING.name, log.before)
            assertEquals(EnumCollectionStatus.IN_STORE.name, log.after)
            assertEquals(CollectionCatalogVersionDao.all().single().id.value, log.refBizId)
        }
        assertEquals(1,r.service.versions("item-museum").size);assertEquals("完整新定名",r.service.card("item-museum").catalog.content.formalName)
        assertThrows(BadRequestException::class.java){r.entry("save")}
    }
    @ParameterizedTest @ValueSource(strings=["org","collection","state","content","submitter"])
    fun `review rejects a mismatched or changed submitted snapshot without changing evidence`(damage:String)=fixture {r ->
        r.seedSubmitted();r.db {val v=CollectionCatalogVersionDao["submitted"];when(damage){
            "org"->v.orgId="other";"collection"->v.collectionId="item-other";"state"->v.catalogStatus=EnumCatalogStatus.APPROVED
            "content"->v.content=v.content.copy(formalName="另一快照");"submitter"->v.submittedBy="another"
        }}
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.entry("review")};assertEquals(before,r.snapshot())
    }
    @ParameterizedTest @ValueSource(strings=["page","get","versions","card","save","submit","review"])
    fun `wrong catalog museum cannot be read initialized or overwritten`(entry:String)=fixture {r ->
        r.db {r.catalog().orgId="other"};val before=r.snapshot()
        if(entry=="page") {assertEquals(0L,r.page().total)} else assertThrows(BadRequestException::class.java){r.entry(entry)}
        assertEquals(before,r.snapshot())
    }
    @Test fun `foreign version content and master data names cannot enrich a local catalog`()=fixture {r ->
        r.seedSubmitted();r.db {
            CollectionCatalogVersionDao["submitted"].orgId="other"
            CollectionCategoryDao.new("foreign-category"){orgId="other";name="外馆机密分类";parentId="0";parentIds="0,"}
            WarehouseDao.new("foreign-location"){orgId="other";name="外馆机密库位";parentId="0";parentIds="0,"}
            r.catalog().content=r.catalog().content.copy(categoryId="foreign-category",warehouseId="foreign-location")
        }
        assertTrue(r.service.versions("item-museum").isEmpty());val v=r.service.get("item-museum");assertNull(v.categoryName);assertNull(v.warehouseName)
    }
    @Test fun `file resolution cannot use the previous edit permission after current revocation`()=fixture {r ->
        doAnswer {r.grant();null}.`when`(r.binder).resolveSingle("proof")
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.service.save("item-museum",r.content().copy(coverImage="proof"))}
        assertEquals(before,r.snapshot())
    }
    @Test fun `internal import saves current target catalog with ledger duty and rejects a changed target`()=fixture("multi") {r ->
        r.grant(CollectionConst.LEDGER_CREATE);r.damage("moved")
        r.service.saveFromImport("item-other",r.content().copy(formalName="导入详细编目",warehouseId="store-other"),"other")
        assertEquals("导入详细编目",r.db {r.catalog("other").content.formalName})
        assertThrows(BadRequestException::class.java){r.service.save("item-other",r.content())}
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.service.saveFromImport("item-other",r.content(),"museum")};assertEquals(before,r.snapshot())
        r.grant(CollectionConst.LEDGER_CREATE,CollectionConst.STORAGE)
        assertThrows(BadRequestException::class.java){r.service.saveFromImport("item-other",r.content(),"other")}
    }
    @Test fun `disabled current role does not inherit old login catalog privileges`()=fixture {r ->
        r.grant(CollectionConst.CATALOG_EDIT)
        r.db {RoleDao.find {Roles.code eq "CURRENT_CATALOG"}.single().enabled=false}
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.entry("save")}
        assertThrows(BadRequestException::class.java){r.page()};assertEquals(before,r.snapshot())
    }
    @Test fun `approved only listing does not turn a former museum version into current approved content`()=fixture("multi") {r ->
        r.seedSubmitted();r.db {CollectionCatalogVersionDao["submitted"].apply {orgId="other";catalogStatus=EnumCatalogStatus.APPROVED}}
        r.grant(CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        assertEquals(0L,r.page(CollectionCatalogQueryVo().apply {approvedOnly=true}).total)
        assertNull(r.service.get("item-museum").approvedVersion)
    }

    @Test fun `approved card labels come from the approved content rather than the current draft`()=fixture {r ->
        r.seedSubmitted();r.db {
            CollectionCategoryDao.new("approved-category"){name="正式版本分类";parentId="0";parentIds="0,"}
            CollectionCategoryDao.new("draft-category"){name="工作副本分类";parentId="0";parentIds="0,"}
            CollectionCatalogVersionDao["submitted"].apply {catalogStatus=EnumCatalogStatus.APPROVED;content=content.copy(categoryId="approved-category")}
            r.catalog().content=r.catalog().content.copy(categoryId="draft-category")
        }
        assertEquals("正式版本分类",r.service.card("item-museum").catalog.categoryName)
        assertEquals("工作副本分类",r.service.get("item-museum").categoryName)
    }

    @Test fun `confirmed save returns a stable committed hash and atomic submit records exactly one version`() = fixture { r ->
        val first = r.service.get("item-museum")
        assertTrue(first.canEdit); assertFalse(first.canReview); assertEquals("museum", first.orgName)
        val saved = r.service.saveConfirmed("item-museum", r.content().copy(formalName="核对后定名"), first.stateHash!!)
        assertEquals(saved.stateHash, r.service.get("item-museum").stateHash)
        assertNotEquals(first.stateHash, saved.stateHash)
        val submitted = r.service.saveConfirmed("item-museum", saved.content.copy(formalName="提交定名"), saved.stateHash!!, true)
        assertEquals(EnumCatalogStatus.SUBMITTED, submitted.status); assertEquals(1, submitted.version)
        assertFalse(submitted.canEdit); assertFalse(submitted.canReview)
        assertEquals("提交定名", r.service.versions("item-museum").single().content.formalName)
        assertEquals(submitted.stateHash, r.service.get("item-museum").stateHash)
    }
    @Test fun `atomic save submit rolls back saved content when submission is incomplete`() = fixture { r ->
        val view = r.service.get("item-museum"); val before = r.snapshot()
        assertThrows(BadRequestException::class.java) {
            r.service.saveConfirmed("item-museum", view.content.copy(formalName=""), view.stateHash!!, true)
        }
        assertEquals(before, r.snapshot())
    }
    @ParameterizedTest @ValueSource(strings=["save", "submit", "save-submit"])
    fun `same version content drift rejects confirmed draft mutations`(entry: String) = fixture { r ->
        val view = r.service.get("item-museum")
        r.db { r.catalog().content = r.catalog().content.copy(formalName="另一窗口更新") }
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { when (entry) {
            "save" -> r.service.saveConfirmed("item-museum", view.content, view.stateHash!!)
            "submit" -> r.service.submitConfirmed("item-museum", view.stateHash!!)
            else -> r.service.saveConfirmed("item-museum", view.content, view.stateHash!!, true)
        } }
        assertEquals(before, r.snapshot())
    }
    @ParameterizedTest @ValueSource(strings=["content", "snapshot", "number", "submitter", "registration"])
    fun `review confirmation rejects changes since preview without overwriting changed evidence`(damage: String) = fixture { r ->
        r.seedSubmitted(); val view = r.service.get("item-museum"); assertTrue(view.canReview)
        r.db { when (damage) {
            "content" -> r.catalog().content = r.catalog().content.copy(formalName="变化正文")
            "snapshot" -> CollectionCatalogVersionDao["submitted"].auditOpinion="新增依据"
            "number" -> r.catalog().submittedVersion=2
            "submitter" -> r.catalog().submittedBy="another"
            "registration" -> CollectionDao["item-museum"].totalRegNo="CHANGED"
        } }
        val before=r.snapshot()
        assertThrows(BadRequestException::class.java) {
            r.service.reviewConfirmed("item-museum", CollectionCatalogReviewVo(EnumCatalogStatus.REJECTED,"核对"),view.stateHash!!)
        }
        assertEquals(before,r.snapshot())
    }
    @Test fun `valid review confirmation returns committed receipt and cannot be replayed`() = fixture { r ->
        r.seedSubmitted(); val view=r.service.get("item-museum")
        val result=r.service.reviewConfirmed("item-museum",CollectionCatalogReviewVo(EnumCatalogStatus.REJECTED,"需要补充依据"),view.stateHash!!)
        assertEquals(EnumCatalogStatus.REJECTED,result.status); assertEquals(result.stateHash,r.service.get("item-museum").stateHash)
        assertTrue(result.canEdit); assertFalse(result.canReview); val before=r.snapshot()
        assertThrows(BadRequestException::class.java) {r.service.reviewConfirmed("item-museum",CollectionCatalogReviewVo(EnumCatalogStatus.REJECTED,"重复"),view.stateHash!!)}
        assertEquals(before,r.snapshot())
    }
    @Test fun `rejected catalog resubmission records every main status transition once`() = fixture { r ->
        r.seedSubmitted()
        r.service.review("item-museum", CollectionCatalogReviewVo(EnumCatalogStatus.REJECTED, "补充原件依据"))
        r.service.save("item-museum", r.content())
        r.service.save("item-museum", r.content())
        r.service.submit("item-museum")
        r.db {
            val catalogId = r.catalog().id.value
            val changes = CollectionLedgerLogDao.all().filter { it.field == "catalogStatus" }.map {
                Triple(it.action, it.before to it.after, it.refBizId)
            }
            assertEquals(listOf(
                Triple(EnumLedgerAction.AUDIT, "SUBMITTED" to "REJECTED", "submitted"),
                Triple(EnumLedgerAction.CORRECT, "REJECTED" to "DRAFT", catalogId),
                Triple(EnumLedgerAction.CORRECT, "DRAFT" to "SUBMITTED", catalogId),
            ), changes)
            assertEquals(EnumCatalogStatus.SUBMITTED, CollectionDao["item-museum"].catalogStatus)
        }
    }
    @Test fun `confirmation is bound to collection and does not replace current permission`() = fixture("multi") { r ->
        val view=r.service.get("item-museum")
        r.damage("moved");val before=r.snapshot()
        assertThrows(BadRequestException::class.java) {r.service.saveConfirmed("item-other",view.content,view.stateHash!!)}
        assertEquals(before,r.snapshot())
        r.grant(CollectionConst.SELECT);val read=r.service.get("item-other")
        assertFalse(read.canEdit);assertFalse(read.canReview)
        assertThrows(BadRequestException::class.java) {r.service.saveConfirmed("item-other",read.content,read.stateHash!!)}
        assertEquals(before,r.snapshot())
    }

}
