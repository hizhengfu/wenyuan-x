package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class CollectionDisclosurePersistenceTest {
    @org.junit.jupiter.api.io.TempDir lateinit var originalRoot: java.nio.file.Path
    private val at=LocalDateTime.of(2026,1,2,12,0,0,123000000)
    private val scope=CollectionScopeResolver("multi", "")
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val service=CollectionDisclosureService(scope,users,archives)
    private val exitStatutory=DeregistrationStatutoryService(scope,binder,archives,CollectionCalendarService(scope,binder,archives),users,service)
    private val transferStatutory=CollectionTransferStatutoryService(scope,users,binder,archives,mock(CollectionArchivePacketService::class.java),service)
    private val collections=CollectionService(scope,binder,mock(CollectionRegistrationNumberService::class.java),mock(CollectionOperationLockService::class.java),mock(CollectionCatalogService::class.java),mock(CollectionChangeService::class.java),mock(CollectionRetainedLedgerService::class.java),service)
    @BeforeEach fun setup() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context);login()
    }
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun login(org: String="museum", permissions: List<String> = listOf(CollectionConst.PUBLISH)) {
        val user=SecurityUser(id="officer",name="公开经办",username="officer",password="",groupId=org,groupIds=setOf(org),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=permissions.map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    private fun fixture(block:(Database)->Unit) {
        val path=java.nio.file.Files.createTempFile("disclosure-",".sqlite")
        val db=Database.connect("jdbc:sqlite:$path",driver="org.sqlite.JDBC")
        try {
            transaction(db) {
                originalReaderSchema();originalReader("officer",CollectionConst.PUBLISH)
                GroupDao.new("target") { name="他馆";type='1';parentId="0";parentIds="0," }
                SchemaUtils.create(Deregistrations,DeregistrationExpertOpinions,DeregistrationNotices,CollectionAdministrativeRecords,CollectionCalendarVersions,CollectionTransfers,CollectionTransferTerminations,CollectionTransferNotices,CollectionOrgs,Collections,CollectionCatalogVersions,Acquisitions,AcquisitionComplianceCases,CollectionDisclosureReviews,CollectionDisclosureEvents,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
                CollectionOrgDao.new("museum"){name="本馆";code="M"};CollectionOrgDao.new("target"){name="他馆";code="T"}
                listOf("review","public","proof","replacement","YES").forEach { id -> FileDao.new(id){name="$id.pdf";this.path="/files/$id.pdf";hash=id;size=100;ext="pdf"} }
                val c=CollectionDao.new("one"){orgId="museum";totalRegNo="A1";name="铜器";registeredAt=at.minusYears(1);catalogStatus=EnumCatalogStatus.APPROVED;lifeStatus=EnumCollectionStatus.IN_STORE;valuation=789012345;warehouseId="SECRET-WAREHOUSE";rfidTag="SECRET-RFID";remark="PRIVATE-REMARK";published=true}
                version("v1",1,CollectionCatalogMapper.fromCollection(c.toModel()))
            };block(db)
        } finally { java.nio.file.Files.deleteIfExists(path) }
    }
    private fun version(id:String,n:Int,c:CollectionCatalogContentVo)=CollectionCatalogVersionDao.new(id){orgId="museum";catalogId="catalog";collectionId="one";version=n;catalogStatus=EnumCatalogStatus.APPROVED;content=c;auditTime=at.minusDays(1)}
    private fun detail()=service.detail("museum",EnumDisclosureSubject.RESOURCE,"one")
    private fun review(subject:EnumDisclosureSubject=EnumDisclosureSubject.RESOURCE,id:String="one")=CollectionDisclosureReviewVo(
        sourceRevision=service.detail("museum",subject,id)["sourceRevision"] as String,decision=EnumDisclosureDecision.PUBLIC,reviewedAt=at,reviewerUserId="reviewer",signedReviewFileIds="review",
        restrictions=EnumDisclosureRestriction.entries.associateWith { DisclosureRestrictionVo(EnumDisclosureHandling.REDACTED,"已核查，内部信息均未写入公开稿") },
        content=CollectionPublicContentVo("馆藏铜器","面向公众的馆藏介绍","通过本馆服务台提供意见",mapOf(EnumPublicFact.NAME to "铜器"),listOf("https://museum.example.org/images/bronze.jpg")),publicContentFileIds="public")
    private fun save(v:CollectionDisclosureReviewVo=review())=service.review("museum",EnumDisclosureSubject.RESOURCE,"one",v)
    private fun publication(channel:EnumDisclosureChannel=EnumDisclosureChannel.OFFICIAL_WEBSITE)=CollectionDisclosureEventVo(EnumDisclosureEvent.PUBLISHED,channel,if(channel==EnumDisclosureChannel.OFFICIAL_WEBSITE)"https://museum.example.org/one" else "馆内一层公告栏",at.plusDays(1),"officer","proof","public")
    @Test fun `disclosure original requires selected object review event and file` ()=fixture {db->transaction(db) {
        val reviewId=save()
        val reviewArchive=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionDisclosureService.BIZ && it.bizId==reviewId && it.phase=="REVIEWED"}.id.value
        service.requireArchivedOriginal("museum",EnumDisclosureSubject.RESOURCE,"one",reviewId,reviewArchive,"public")
        val eventId=service.event("museum",reviewId,publication())
        val eventArchive=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionDisclosureService.BIZ && it.bizId==eventId && it.phase=="PUBLISHED"}.id.value
        service.requireArchivedOriginal("museum",EnumDisclosureSubject.RESOURCE,"one",reviewId,eventArchive,"proof")
        assertThrows(BadRequestException::class.java){service.requireArchivedOriginal("museum",EnumDisclosureSubject.RESOURCE,"one","missing",reviewArchive,"public")}
        assertThrows(BadRequestException::class.java){service.requireArchivedOriginal("museum",EnumDisclosureSubject.RESOURCE,"one",reviewId,"missing","public")}
        assertThrows(BadRequestException::class.java){service.requireArchivedOriginal("museum",EnumDisclosureSubject.RESOURCE,"one",reviewId,reviewArchive,"missing")}
        UserDao["officer"].groupId="target";login("target")
        assertThrows(BadRequestException::class.java){service.requireArchivedOriginal("museum",EnumDisclosureSubject.RESOURCE,"one",reviewId,reviewArchive,"public")}
    } }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("false,true", "false,false", "true,true", "true,false")
    fun `repeated publication and withdrawal require their original`(withdrawn: Boolean, missing: Boolean)=fixture { db -> transaction(db) {
        val reviewId=save()
        val posted=service.event("museum",reviewId,publication())
        val value=if(withdrawn) publication().copy(action=EnumDisclosureEvent.WITHDRAWN,occurredAt=at.plusDays(2),publicContentFileIds=null,revokesPublicationId=posted,reason="实际撤下") else publication()
        val eventId=service.event("museum",reviewId,value)
        val original=CollectionPermanentArchiveDao.all().single { it.bizId==eventId && it.phase==value.action.name }
        if(missing) original.delete() else original.snapshot="{}"
        fun originals()=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }.sortedBy { it.first }
        val before=originals();val count=CollectionDisclosureEventDao.count()
        assertThrows(BadRequestException::class.java){service.event("museum",reviewId,value)}
        assertEquals(before,originals());assertEquals(count,CollectionDisclosureEventDao.count())
        assertEquals(value,CollectionDisclosureEventDao[eventId].content)
    } }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("review,revoked", "review,disabled", "review,moved", "event,revoked", "event,disabled", "event,moved")
    fun `writes require current eligibility despite old login`(operation: String, change: String)=fixture { db -> transaction(db) {
        val v=review();val reviewId=save(v);val publication=publication()
        service.event("museum",reviewId,publication)
        when(change) {
            "revoked" -> RolePermissions.deleteAll()
            "disabled" -> UserDao["officer"].enabled=false
            "moved" -> UserDao["officer"].groupId="target"
        }
        fun originals()=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }.sortedBy { it.first }
        val before=originals();val count=CollectionDisclosureEventDao.count()
        assertThrows(BadRequestException::class.java) { if(operation=="review") save(v) else service.event("museum",reviewId,publication) }
        assertEquals(before,originals());assertEquals(count,CollectionDisclosureEventDao.count())
    } }
    @Test fun `optional research source uses reviewed feed and invalidates changed or removed source`()=fixture { db -> transaction(db) {
        val subject = EnumDisclosureSubject.RESEARCH_ACHIEVEMENT
        var current: CollectionDisclosureSource? = CollectionDisclosureSource("research-v1", "内部成果名称", true, at.minusDays(1))
        val provider = object : CollectionDisclosureSourceProvider {
            override val subject = EnumDisclosureSubject.RESEARCH_ACHIEVEMENT
            override fun source(orgId: String, id: String, lock: Boolean) = current.takeIf { orgId == "museum" && id == "research-one" }
        }
        val research = CollectionDisclosureService(scope, users, archives, listOf(provider))
        assertTrue(research.feed(subject=subject).isEmpty())
        val value = review().copy(sourceRevision="research-v1", content=CollectionPublicContentVo("公开研究标题", "已核查的研究介绍", "本馆服务台"))
        val id = research.review("museum", subject, "research-one", value)
        research.enable("museum", id)
        val published = research.feed(subject=subject).single()
        assertTrue(published.available)
        assertEquals("公开研究标题", published.content!!.title)
        assertFalse(JsonUtils.toJson(published)!!.contains("内部成果名称"))
        current = current!!.copy(revision="research-v2")
        val changed = research.feed(subject=subject).single()
        assertFalse(changed.available); assertNull(changed.content)
        current = null
        assertFalse(research.feed(subject=subject).single().available)
        val disabledModule = CollectionDisclosureService(scope, users, archives)
        assertFalse(disabledModule.feed(subject=subject).single().available)
        val duplicated = CollectionDisclosureService(scope, users, archives, listOf(provider, provider))
        assertThrows(BadRequestException::class.java) { duplicated.detail("museum", subject, "research-one") }
    } }

    @Test fun `current revocation rejects private disclosure originals`()=fixture { db -> transaction(db) {
        save();RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){detail()}
    } }
    @Test fun `current revocation rejects public feed despite old login`()=fixture { db -> transaction(db) {
        val id=save();service.enable("museum",id);RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.feed()}
    } }
    @Test fun `foreign publication cannot appear in local review history`()=fixture { db -> transaction(db) {
        val id=save();val event=service.event("museum",id,publication())
        CollectionDisclosureEventDao[event].orgId="target"
        assertThrows(BadRequestException::class.java){detail()}
    } }
    @Test fun `disabled museum returns removal to currently authorized cross museum feed reader`()=fixture { db -> transaction(db) {
        val id=save();service.enable("museum",id)
        actorRole("officer","CROSS",setOf(CollectionConst.CROSS_ORG));CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        val output=service.feed().single();assertFalse(output.available);assertNull(output.content)
    } }

    @Test fun `current departure cross grant and single configuration govern review and feed independently`()=fixture { db -> transaction(db) {
        val id=save();service.enable("museum",id)
        UserDao["officer"].groupId="target"
        assertThrows(BadRequestException::class.java){detail()};assertTrue(service.feed().isEmpty())
        actorRole("officer","CURRENT_CROSS",setOf(CollectionConst.CROSS_ORG))
        assertEquals("museum",detail()["orgId"]);assertTrue(service.feed().single().available)
        val single=CollectionDisclosureService(CollectionScopeResolver("single","target"),users,archives)
        assertThrows(BadRequestException::class.java){single.detail("museum",EnumDisclosureSubject.RESOURCE,"one")}
        assertTrue(single.feed().isEmpty())
        RolePermissions.deleteAll();actorRole("officer","GENERAL_CROSS",setOf(CollectionConst.SELECT,CollectionConst.CROSS_ORG))
        assertThrows(BadRequestException::class.java){detail()};assertTrue(service.feed().single().available)
    } }
    @Test fun `disabled account and stale administrator cannot read either disclosure surface`()=fixture { db -> transaction(db) {
        val id=save();service.enable("museum",id)
        UserDao["officer"].enabled=false
        assertThrows(BadRequestException::class.java){detail()};assertThrows(BadRequestException::class.java){service.feed()}
        UserDao["officer"].enabled=true;RolePermissions.deleteAll()
        val old=SecurityUser(id="officer",name="旧管理员",username="officer",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(io.hizhengfu.cloud.common.domain.EnumRole.ADMIN.value),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(old,null,old.authorities)
        assertThrows(BadRequestException::class.java){detail()};assertThrows(BadRequestException::class.java){service.feed()}
    } }
    @Test fun `foreign expert association stops statutory public copy without exposing expert ids`()=fixture { db -> transaction(db) {
        exitCase()
        val id=service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",statutoryReview(EnumDisclosureSubject.EXIT_CONSULTATION))
        service.enable("museum",id);assertTrue(service.feed(EnumDisclosureSubject.EXIT_CONSULTATION).single().available)
        DeregistrationExpertOpinionDao.all().first().orgId="target"
        val output=service.feed(EnumDisclosureSubject.EXIT_CONSULTATION).single()
        assertFalse(output.available);assertNull(output.content)
        val detail=service.detail("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit")
        assertFalse(detail["sourceReady"] as Boolean);assertNull(detail["expertOpinionIds"])
    } }
    @Test fun `cross permission cannot legitimize foreign permanent file reference in private review`()=fixture { db -> transaction(db) {
        save();actorRole("officer","CROSS",setOf(CollectionConst.CROSS_ORG))
        CollectionPermanentArchiveFileDao.all().first().orgId="target"
        assertThrows(BadRequestException::class.java){detail()}
    } }

    @Test fun `old publish flag and direct endpoint cannot release full private collection`()=fixture { db -> transaction(db) {
        assertFalse(collections.publishedFeed().single().available);assertNull(collections.publishedFeed().single().content)
        assertThrows(BadRequestException::class.java){collections.publishCms("one")}
        assertEquals(0L,CollectionDisclosureReviewDao.count())
    } }
    @Test fun `explicit reviewed copy is only enabled deliberately and contains no private fields`()=fixture { db -> transaction(db) {
        val id=save();assertEquals(id,save());assertFalse(collections.publishedFeed().single().available)
        assertEquals("one",collections.publishCms("one"));assertTrue(collections.publishedFeed().single().available)
        assertEquals(review().content,collections.publishedFeed().single().content)
        val json=JsonUtils.toJson(collections.publishedFeed())!!
        listOf("789012345","SECRET-WAREHOUSE","SECRET-RFID","PRIVATE-REMARK","signedReviewFileIds","restrictions","reviewerUserId","valuation","warehouseId","sourceRevision").forEach { assertFalse(json.contains(it),it) }
        assertTrue((detail()["activePublications"] as List<*>).isEmpty());assertEquals(0L,CollectionDisclosureEventDao.count())
        assertEquals(2,archives.list(CollectionDisclosureService.BIZ,id).size)
    } }
    @Test fun `all secrecy categories and real actors times originals are required`()=fixture { db -> transaction(db) {
        assertThrows(BadRequestException::class.java){save(review().copy(restrictions=emptyMap()))}
        assertThrows(BadRequestException::class.java){save(review().copy(reviewedAt=LocalDateTime.now().plusDays(1)))}
        assertThrows(BadRequestException::class.java){save(review().copy(reviewedAt=at.minusYears(1)))}
        assertThrows(BadRequestException::class.java){save(review().copy(publicContentFileIds=""))}
        doThrow(BadRequestException("须为本馆实际人员")).`when`(users).requireMuseumMember("outsider","museum")
        assertThrows(BadRequestException::class.java){save(review().copy(reviewerUserId="outsider"))}
        assertEquals(0L,CollectionDisclosureReviewDao.count())
    } }
    @Test fun `private whole item decision stops old feed and preserves actual publication requiring removal`()=fixture { db -> transaction(db) {
        val id=save();service.enable("museum",id);val posted=service.event("museum",id,publication())
        val withheld=review().copy(decision=EnumDisclosureDecision.WITHHELD,content=null,publicContentFileIds=null,withholdingReason="核实涉及工作秘密",restrictions=review().restrictions+(EnumDisclosureRestriction.WORK_SECRET to DisclosureRestrictionVo(EnumDisclosureHandling.WITHHELD,"不公开依据见原件")),reviewedAt=at.plusDays(2),replacementReason="发现限制公开内容",replacementFileIds="replacement")
        save(withheld);assertFalse(service.feed().single().available);assertNull(service.feed().single().content)
        val active=(detail()["activePublications"] as List<Map<String,Any?>>).single();assertEquals(posted,active["id"]);assertEquals(true,active["needsUpdate"])
        assertThrows(BadRequestException::class.java){service.enableLatestResource("one")}
    } }
    @Test fun `public manuscript rejects withheld fragments html and credential bearing image addresses`()=fixture { db -> transaction(db) {
        val value=review()
        assertThrows(BadRequestException::class.java){save(value.copy(restrictions=value.restrictions+(EnumDisclosureRestriction.WORK_SECRET to DisclosureRestrictionVo(EnumDisclosureHandling.WITHHELD,"仍然保密"))))}
        assertThrows(BadRequestException::class.java){save(value.copy(content=value.content!!.copy(body="<script>alert(1)</script>")))}
        assertThrows(BadRequestException::class.java){save(value.copy(content=value.content!!.copy(imageUrls=listOf("https://user:secret@example.org/a.jpg"))))}
        assertThrows(BadRequestException::class.java){save(value.copy(content=value.content!!.copy(imageUrls=listOf("file:///private/a.jpg"))))}
    } }
    @Test fun `formal revision withdraws old copy and newly reviewed version inherits no enablement or publications`()=fixture { db -> transaction(db) {
        val old=review();val id=save(old);service.enable("museum",id);service.event("museum",id,publication())
        version("v2",2,CollectionCatalogVersionDao["v1"].content.copy(formalName="更正名称"));assertFalse(service.feed().single().available)
        assertThrows(BadRequestException::class.java){save(old.copy(reviewedAt=at.plusDays(2),replacementReason="旧页面",replacementFileIds="replacement"))}
        val next=save(review().copy(reviewedAt=at.plusDays(2),replacementReason="更新公开资料",replacementFileIds="replacement"));assertNotEquals(id,next);assertFalse(service.feed().single().available)
        service.enable("museum",next);assertTrue(service.feed().single().available);assertEquals(1L,CollectionDisclosureEventDao.count())
        assertThrows(BadRequestException::class.java){service.enable("museum",id)}
    } }
    @Test fun `actual published and removed evidence requires same copy place and chronology`()=fixture { db -> transaction(db) {
        val id=save();assertThrows(BadRequestException::class.java){service.event("museum",id,publication().copy(publicContentFileIds="review"))}
        assertThrows(BadRequestException::class.java){service.event("museum",id,publication().copy(occurredAt=LocalDateTime.now().plusDays(1)))}
        val posted=service.event("museum",id,publication());assertEquals(posted,service.event("museum",id,publication()))
        val withdrawal=publication().copy(action=EnumDisclosureEvent.WITHDRAWN,occurredAt=at.plusDays(2),publicContentFileIds=null,revokesPublicationId=posted,reason="更新页面前实际撤下")
        assertThrows(BadRequestException::class.java){service.event("museum",id,withdrawal.copy(location="https://museum.example.org/another"))}
        service.event("museum",id,withdrawal);assertTrue((detail()["activePublications"] as List<*>).isEmpty());assertEquals(2L,CollectionDisclosureEventDao.count())
        assertThrows(BadRequestException::class.java){service.event("museum",id,withdrawal.copy(reason="不能覆盖旧原因"))}
    } }
    @Test fun `stopping cms supply does not fabricate real site removal and old version cannot restart`()=fixture { db -> transaction(db) {
        val id=save();service.enable("museum",id);service.event("museum",id,publication(EnumDisclosureChannel.MUSEUM_SITE))
        service.stop("museum",id,DisclosureFeedStopVo("准备更新"));assertFalse(service.feed().single().available)
        assertEquals(1,(detail()["activePublications"] as List<*>).size);assertThrows(BadRequestException::class.java){service.enable("museum",id)}
    } }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["different-reason","missing-original","changed-snapshot","changed-digest"])
    fun `repeated feed stop requires the sealed reason and original`(change:String)=fixture { db -> transaction(db) {
        val id=save();service.enable("museum",id)
        val stop=DisclosureFeedStopVo("准备更新")
        assertEquals(id,service.stop("museum",id,stop))
        assertEquals(id,service.stop("museum",id,stop))
        val original=CollectionPermanentArchiveDao.all().single { it.bizId==id && it.phase=="FEED_STOPPED" }
        when(change) {
            "missing-original" -> original.delete()
            "changed-snapshot" -> original.snapshot="{}"
            "changed-digest" -> original.digest="0".repeat(64)
        }
        val before=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }.sortedBy { it.first }
        val value=if(change=="different-reason") DisclosureFeedStopVo("改写旧原因") else stop
        assertThrows(BadRequestException::class.java) { service.stop("museum",id,value) }
        assertEquals(before,CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }.sortedBy { it.first })
        assertNotNull(CollectionDisclosureReviewDao[id].feedStoppedAt)
    } }
    @Test fun `source transfer and deregistration preserve source tombstones and do not expose to receiving museum`()=fixture { db -> transaction(db) {
        val id=save();service.enable("museum",id);CollectionDao["one"].lifeStatus=EnumCollectionStatus.DEREGISTERED;assertFalse(service.feed().single().available)
        CollectionDao["one"].orgId="target";assertEquals("museum",service.feed().single().orgId);assertFalse(service.feed().single().available)
        UserDao["officer"].groupId="target";login("target");assertFalse(service.feed().single().available);assertNull(service.feed().single().content)
        assertThrows(BadRequestException::class.java){service.detail("museum",EnumDisclosureSubject.RESOURCE,"one")}
    } }
    @Test fun `read public feed does not grant private review access and foreign write is rejected`()=fixture { db -> transaction(db) {
        val id=save();service.enable("museum",id);RolePermissions.deleteAll();actorRole("officer","GENERAL",setOf(CollectionConst.SELECT));login(permissions=listOf(CollectionConst.SELECT));assertTrue(collections.publishedFeed().single().available)
        assertThrows(BadRequestException::class.java){detail()};assertThrows(BadRequestException::class.java){service.enable("museum",id)}
        UserDao["officer"].groupId="target";login("target");assertTrue(service.feed().isEmpty());assertThrows(BadRequestException::class.java){service.stop("museum",id,DisclosureFeedStopVo("越权"))}
    } }
    @Test fun `changed or missing sealed review fails closed in feed and cannot log actual release`()=fixture { db -> transaction(db) {
        val id=save();service.enable("museum",id);val original=CollectionDisclosureReviewDao[id].content
        CollectionDisclosureReviewDao[id].content=original.copy(content=original.content!!.copy(body="擅自改写"));assertFalse(service.feed().single().available)
        assertThrows(BadRequestException::class.java){service.event("museum",id,publication())}
        CollectionDisclosureReviewDao[id].content=original
        val seal=CollectionPermanentArchiveDao.find { CollectionPermanentArchives.phase eq "REVIEWED" }.single()
        val originalSnapshot=seal.snapshot;seal.snapshot="{\"tampered\":true}";assertFalse(service.feed().single().available)
        seal.snapshot=originalSnapshot;assertTrue(service.feed().single().available)
        CollectionPermanentArchiveDao.find { CollectionPermanentArchives.phase eq "FEED_ENABLED" }.single().delete()
        assertFalse(service.feed().single().available);assertThrows(BadRequestException::class.java){service.enable("museum",id)}
        seal.delete();assertFalse(service.feed().single().available)
    } }
    @Test fun `reviewed public feed rejects changed original bytes under unchanged file identity`()=fixture { db -> transaction(db) {
        for (fileId in listOf("review", "public")) {
            java.nio.file.Files.writeString(originalRoot.resolve(fileId), "ORIGINAL")
            FileDao[fileId].path=fileId
        }
        val contentArchives=CollectionPermanentArchiveService(binder,scope,LocalFileObjectStorage(originalRoot.toString()),unprotectedArchiveReadAccessProvider())
        val contentService=CollectionDisclosureService(scope,users,contentArchives)
        val id=contentService.review("museum",EnumDisclosureSubject.RESOURCE,"one",review())
        contentService.enable("museum",id)
        assertTrue(contentService.feed().single().available)
        for (fileId in listOf("review", "public")) {
            val path=originalRoot.resolve(fileId)
            val timestamp=java.nio.file.Files.getLastModifiedTime(path)
            java.nio.file.Files.writeString(path,"CHANGED!")
            java.nio.file.Files.setLastModifiedTime(path,timestamp)
            assertFalse(contentService.feed().single().available,fileId)
            assertThrows(BadRequestException::class.java){contentService.event("museum",id,publication())}
            java.nio.file.Files.writeString(path,"ORIGINAL")
            java.nio.file.Files.setLastModifiedTime(path,timestamp)
            assertTrue(contentService.feed().single().available)
        }
        assertEquals(0L,CollectionDisclosureEventDao.count())
    } }
    @Test fun `published public copy rejects changed publication proof bytes`()=fixture { db -> transaction(db) {
        for (fileId in listOf("review", "public", "proof")) {
            java.nio.file.Files.writeString(originalRoot.resolve(fileId), "ORIGINAL")
            FileDao[fileId].path=fileId
        }
        val contentArchives=CollectionPermanentArchiveService(binder,scope,LocalFileObjectStorage(originalRoot.toString()),unprotectedArchiveReadAccessProvider())
        val contentService=CollectionDisclosureService(scope,users,contentArchives)
        val id=contentService.review("museum",EnumDisclosureSubject.RESOURCE,"one",review())
        contentService.enable("museum",id)
        val eventId=contentService.event("museum",id,publication())
        val archive=CollectionPermanentArchiveDao.all().single {it.bizId==eventId && it.phase=="PUBLISHED"}
        val ref=CollectionPermanentArchiveFileDao.all().single {it.archiveId==archive.id.value && it.fileId=="public"}
        val reference=CollectionArchiveFileReference(archive.id.value,"museum",CollectionDisclosureService.BIZ,eventId,"PUBLISHED","public",ref.metadata)
        assertTrue(contentService.isPublicArchiveFile(reference))
        val path=originalRoot.resolve("proof")
        val timestamp=java.nio.file.Files.getLastModifiedTime(path)
        java.nio.file.Files.writeString(path,"CHANGED!")
        java.nio.file.Files.setLastModifiedTime(path,timestamp)
        assertFalse(contentService.isPublicArchiveFile(reference))
        assertEquals(1L,CollectionDisclosureEventDao.count())
    } }
    @Test fun `public originals retained and literal file IDs millisecond json are preserved`()=fixture { db -> transaction(db) {
        val value=review().copy(signedReviewFileIds="YES");val id=save(value)
        assertEquals(value,JsonUtils.fromJson(JsonUtils.toJson(value)!!,CollectionDisclosureReviewVo::class.java))
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("YES")}
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireUnlinkable("public",id)}
    } }
    @Test fun `archive attachment failure rolls back review and no public state appears`()=fixture { db ->
        assertThrows(BadRequestException::class.java){transaction(db){save(review().copy(signedReviewFileIds="missing"))}}
        transaction(db){assertEquals(0L,CollectionDisclosureReviewDao.count());assertEquals(0L,CollectionPermanentArchiveDao.count());assertFalse(service.feed().single().available)}
    }
    @Test fun `acquisition disclosure requires actual completion and management use copy without revealing price`()=fixture { db -> transaction(db) {
        AcquisitionDao.new("acq"){orgId="museum";name="征集陶器";source=EnumSource.PURCHASE;quantity=1;unit="件";holder="PRIVATE-HOLDER";appraisedValue=654321}
        val subject=EnumDisclosureSubject.ACQUISITION
        assertFalse(service.detail("museum",subject,"acq")["sourceReady"] as Boolean)
        val dossier=AcquisitionDossierVo(EnumCollectionNature.RELIC,at.toLocalDate().minusDays(4),"proof","proof","proof",true,"proof",setOf("seller"),"proof")
        AcquisitionComplianceCaseDao.new{orgId="museum";acquisitionId="acq";this.subject="PRIVATE-SUBJECT";this.dossier=dossier;completion=AcquisitionCompletionVo(decidedOn=at.toLocalDate().minusDays(3),collectiveDecisionFileIds="proof",agreementFileIds="proof",agreedOn=at.toLocalDate().minusDays(2),actualAcquiredOn=at.toLocalDate().minusDays(1),handoverReceiptFileIds="proof",publicDisclosurePlan="官网公布",suggestedPriceCents=654321)}
        val value=review(subject,"acq")
        assertThrows(BadRequestException::class.java){service.review("museum",subject,"acq",value)}
        val id=service.review("museum",subject,"acq",value.copy(content=value.content!!.copy(facts=value.content.facts+mapOf(EnumPublicFact.MANAGEMENT to "取得后依法保管",EnumPublicFact.USE to "尚未利用"))))
        service.enable("museum",id);val output=service.feed(subject).single();assertTrue(output.available)
        val json=JsonUtils.toJson(output)!!;listOf("654321","PRIVATE-HOLDER","PRIVATE-SUBJECT","signedReviewFileIds").forEach { assertFalse(json.contains(it)) }
        service.event("museum",id,publication(EnumDisclosureChannel.ANNUAL_REPORT));assertEquals(1L,CollectionDisclosureEventDao.count())
        AcquisitionComplianceCaseDao.all().single().orgId="target"
        assertFalse(service.feed(subject).single().available);assertNull(service.feed(subject).single().content)
        assertFalse(service.detail("museum",subject,"acq")["sourceReady"] as Boolean)
    } }
    private fun exitCase(): DeregistrationDao {
        actorRole("officer","EXIT_EDITOR",setOf(CollectionConst.DEREGISTER))
        CollectionOrgDao["museum"].ownership=EnumOwnership.STATE
        CollectionDao["one"].nature=EnumCollectionNature.RELIC
        val row=DeregistrationDao.new("exit"){orgId="museum";collectionId="one";reason=EnumDeregReason.DAMAGE_NO_VALUE;detail="无保存价值";baseline=CollectionCatalogMapper.fromCollection(CollectionDao["one"].toModel());dossier=DeregistrationDossierVo(proposedOn=at.toLocalDate().minusDays(2),keeperUserId="keeper",leaderUserId="director",destinationType=EnumExitDestination.AUXILIARY_ACCOUNT,dispositionPlanFileIds="proof",auxiliaryRecordNo="AUX1",auxiliaryLocation="辅助保管区")}
        (1..5).forEach { n -> exitStatutory.recordExpert("exit",DeregistrationExpertOpinionVo(expertKey="expert$n",expertName="PRIVATE-NAME$n",organization="PRIVATE-ORG$n",professionalTitle="研究馆员",specialty="铜器",seniorQualificationFileIds="proof",independenceStatementFileIds="proof",opinion="原独立意见$n",agree=true,signedOpinionFileIds="proof",assessedAt=at.toLocalDate().minusDays(1))) }
        return row
    }
    private fun statutoryReview(subject:EnumDisclosureSubject,id:String="exit",replacement:Boolean=false):CollectionDisclosureReviewVo {
        val value=review(subject,id)
        val opinions=if(subject==EnumDisclosureSubject.EXIT_CONSULTATION)DeregistrationExpertOpinionDao.find { DeregistrationExpertOpinions.deregistrationId eq id }.mapIndexed { index, row -> PublicExpertOpinionVo(row.id.value,"评估专家${index+1}","该名专家对本件同意退出的独立理由${index+1}",true) } else emptyList()
        val facts=if(subject==EnumDisclosureSubject.EXIT_CONSULTATION)mapOf(EnumPublicFact.NAME to "铜器",EnumPublicFact.DISPOSITION_PLAN to "纳入辅助账册保管") else mapOf(EnumPublicFact.NAME to "铜器",EnumPublicFact.ACCOUNT_DISPOSITION to "原总登记号永久保留并留存退出凭据",EnumPublicFact.OBJECT_DISPOSITION to "按批准方案完成实物交接")
        return value.copy(content=value.content!!.copy(facts=facts,expertOpinions=opinions),reviewedAt=if(replacement)LocalDateTime.of(2026,7,11,12,0) else LocalDateTime.of(2026,5,21,12,0),replacementReason=if(replacement)"按核实原件订正公开稿" else null,replacementFileIds=if(replacement)"replacement" else null,publicContentFileIds=if(replacement)"replacement" else "public")
    }
    private fun statutoryNotice(reviewId:String,channel:EnumExitNoticeChannel=EnumExitNoticeChannel.MUSEUM_ONSITE,replacement:Boolean=false,kind:EnumExitNoticeKind=EnumExitNoticeKind.CONSULTATION)=DeregistrationNoticeVo(kind=kind,channel=channel,startedOn=if(replacement)java.time.LocalDate.of(2026,7,13) else java.time.LocalDate.of(2026,5,25),endedOn=if(kind==EnumExitNoticeKind.CONSULTATION) { if(replacement)java.time.LocalDate.of(2026,8,31) else java.time.LocalDate.of(2026,7,10) } else null,urlOrLocation=if(channel==EnumExitNoticeChannel.MUSEUM_ONSITE)"馆内公示栏" else "https://museum.example.org/exit",publicContentFileIds=if(replacement)"replacement" else "public",publicationEvidenceFileIds="proof",disclosureReviewId=reviewId)
    @Test fun `statutory consultation must contain all individual public expert opinions and disposition`()=fixture { db -> transaction(db) {
        exitCase();val value=statutoryReview(EnumDisclosureSubject.EXIT_CONSULTATION)
        assertThrows(BadRequestException::class.java){service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",value.copy(content=value.content!!.copy(expertOpinions=value.content.expertOpinions.dropLast(1))))}
        assertThrows(BadRequestException::class.java){service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",value.copy(content=value.content!!.copy(expertOpinions=List(5){value.content.expertOpinions.first()})))}
        assertThrows(BadRequestException::class.java){service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",value.copy(content=value.content!!.copy(facts=mapOf(EnumPublicFact.NAME to "铜器"))))}
        val id=service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",value);service.enable("museum",id)
        val output=service.feed(EnumDisclosureSubject.EXIT_CONSULTATION).single();assertEquals(5,output.content!!.expertOpinions.size)
        val json=JsonUtils.toJson(output)!!;assertFalse(json.contains("PRIVATE-NAME"));assertFalse(json.contains("PRIVATE-ORG"));assertFalse(json.contains("seniorQualificationFileIds"));assertFalse(json.contains("signedOpinionFileIds"))
        assertThrows(BadRequestException::class.java){service.event("museum",id,publication())}
    } }
    @Test fun `actual statutory notice cannot bypass reviewed version or substitute another manuscript`()=fixture { db -> transaction(db) {
        exitCase();val id=service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",statutoryReview(EnumDisclosureSubject.EXIT_CONSULTATION))
        val value=statutoryNotice(id)
        assertThrows(BadRequestException::class.java){exitStatutory.recordNotice("exit",value.copy(disclosureReviewId=null))}
        assertThrows(BadRequestException::class.java){exitStatutory.recordNotice("exit",value.copy(publicContentFileIds="review"))}
        assertThrows(BadRequestException::class.java){exitStatutory.recordNotice("exit",value.copy(startedOn=at.toLocalDate()))}
        exitStatutory.recordNotice("exit",value);assertEquals(1L,DeregistrationNoticeDao.count());assertEquals(0L,CollectionDisclosureEventDao.count())
        assertEquals(1,(service.detail("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit")["statutoryNotices"] as List<*>).size)
    } }
    @Test fun `statutory public manuscript rejects changed notice proof bytes`()=fixture { db -> transaction(db) {
        for (fileId in listOf("review", "public", "proof")) {
            java.nio.file.Files.writeString(originalRoot.resolve(fileId), "ORIGINAL")
            FileDao[fileId].path=fileId
        }
        exitCase()
        val contentArchives=CollectionPermanentArchiveService(binder,scope,LocalFileObjectStorage(originalRoot.toString()),unprotectedArchiveReadAccessProvider())
        val contentService=CollectionDisclosureService(scope,users,contentArchives)
        val contentStatutory=DeregistrationStatutoryService(scope,binder,contentArchives,CollectionCalendarService(scope,binder,contentArchives),users,contentService)
        val id=contentService.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",statutoryReview(EnumDisclosureSubject.EXIT_CONSULTATION))
        contentService.enable("museum",id)
        val noticeId=contentStatutory.recordNotice("exit",statutoryNotice(id))
        val archive=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionConst.BIZ_DEREGISTER && it.phase=="NOTICE_$noticeId"}
        val ref=CollectionPermanentArchiveFileDao.all().single {it.archiveId==archive.id.value && it.fileId=="public"}
        val reference=CollectionArchiveFileReference(archive.id.value,"museum",CollectionConst.BIZ_DEREGISTER,"exit",archive.phase,"public",ref.metadata)
        assertTrue(contentService.isPublicNoticeArchiveFile(reference))
        val path=originalRoot.resolve("proof")
        val timestamp=java.nio.file.Files.getLastModifiedTime(path)
        java.nio.file.Files.writeString(path,"CHANGED!")
        java.nio.file.Files.setLastModifiedTime(path,timestamp)
        assertFalse(contentService.isPublicNoticeArchiveFile(reference))
        assertEquals(1L,DeregistrationNoticeDao.count())
    } }
    @Test fun `changed public draft restarts all three channels and never mixes completed old periods`()=fixture { db -> transaction(db) {
        val request=exitCase();val first=service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",statutoryReview(EnumDisclosureSubject.EXIT_CONSULTATION))
        EnumExitNoticeChannel.entries.forEach { exitStatutory.recordNotice("exit",statutoryNotice(first,it)) };assertEquals(java.time.LocalDate.of(2026,7,10),exitStatutory.requirePublicity(request))
        val second=service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",statutoryReview(EnumDisclosureSubject.EXIT_CONSULTATION,replacement=true))
        exitStatutory.recordNotice("exit",statutoryNotice(second,replacement=true));assertThrows(BadRequestException::class.java){exitStatutory.requirePublicity(request)}
        EnumExitNoticeChannel.entries.filter { it!=EnumExitNoticeChannel.MUSEUM_ONSITE }.forEach { exitStatutory.recordNotice("exit",statutoryNotice(second,it,true)) }
        assertEquals(java.time.LocalDate.of(2026,8,31),exitStatutory.requirePublicity(request));assertEquals(6L,DeregistrationNoticeDao.count())
        assertThrows(BadRequestException::class.java){exitStatutory.recordNotice("exit",statutoryNotice(first))}
        exitStatutory.recordAdministrative("exit",CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.PROVINCE,action=EnumAdministrativeAction.APPROVAL,authorityName="省文物行政部门",documentNo="批复1",issuedAt=java.time.LocalDate.of(2026,9,1),fileIds="proof"))
        assertThrows(BadRequestException::class.java){service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",statutoryReview(EnumDisclosureSubject.EXIT_CONSULTATION,replacement=true).copy(reviewedAt=LocalDateTime.of(2026,9,2,12,0)))}
    } }
    @Test fun `short or absent completed interval and stale proposal cannot satisfy public consultation`()=fixture { db -> transaction(db) {
        val request=exitCase();val id=service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",statutoryReview(EnumDisclosureSubject.EXIT_CONSULTATION))
        EnumExitNoticeChannel.entries.forEach { exitStatutory.recordNotice("exit",statutoryNotice(id,it).copy(endedOn=java.time.LocalDate.of(2026,6,1))) }
        assertThrows(BadRequestException::class.java){exitStatutory.requirePublicity(request)}
        request.dossier=request.dossier!!.copy(auxiliaryLocation="擅自改换处置地点")
        assertThrows(BadRequestException::class.java){exitStatutory.recordNotice("exit",statutoryNotice(id))}
    } }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["approvalLog","proposalFiles","executionFiles","executionMissing","expertMissing","formalState","malformedExecution"])
    fun `exit result public source stops when its complete evidence is broken`(change:String)=fixture {db->transaction(db) {
        executedExit()
        val value=statutoryReview(EnumDisclosureSubject.EXIT_RESULT).copy(reviewedAt=LocalDateTime.now())
        val review=service.review("museum",EnumDisclosureSubject.EXIT_RESULT,"exit",value)
        service.enable("museum",review)
        assertTrue(service.feed(EnumDisclosureSubject.EXIT_RESULT).single().available)
        when(change) {
            "approvalLog"->io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao.all().first().comment="被替换的批准原文"
            "executionMissing"->CollectionPermanentArchiveDao.all().single {it.bizId=="exit" && it.phase=="EXECUTED"}.delete()
            "expertMissing"->DeregistrationExpertOpinionDao.all().first().delete()
            "formalState"->CollectionDao["one"].lifeStatus=EnumCollectionStatus.IN_STORE
            "malformedExecution"->CollectionPermanentArchiveDao.all().single {it.bizId=="exit" && it.phase=="EXECUTED"}.apply {snapshot="{";digest=CollectionCareEvidence.digest(snapshot)}
            else->{val phase=if(change=="proposalFiles")"PROPOSAL" else "EXECUTED";val a=CollectionPermanentArchiveDao.all().single {it.bizId=="exit" && it.phase==phase};CollectionPermanentArchiveFileDao.all().filter {it.archiveId==a.id.value}.forEach {it.delete()}}
        }
        assertFalse(service.detail("museum",EnumDisclosureSubject.EXIT_RESULT,"exit")["sourceReady"] as Boolean)
        val feed=service.feed(EnumDisclosureSubject.EXIT_RESULT).single()
        assertFalse(feed.available);assertNull(feed.content)
        assertThrows(BadRequestException::class.java){service.enable("museum",review)}
        assertThrows(BadRequestException::class.java){service.review("museum",EnumDisclosureSubject.EXIT_RESULT,"exit",value)}
        assertThrows(BadRequestException::class.java){service.review("museum",EnumDisclosureSubject.EXIT_RESULT,"exit",value.copy(reviewedAt=LocalDateTime.now(),replacementReason="重新核实",replacementFileIds="replacement"))}
        assertEquals(1,CollectionDisclosureReviewDao.all().count {it.subject==EnumDisclosureSubject.EXIT_RESULT})
        service.stop("museum",review,DisclosureFeedStopVo("原件核对期间停止供稿"))
        assertNotNull(CollectionDisclosureReviewDao[review].feedStoppedAt)
    }}

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["opinionBody","opinionFiles","proposalMissing"])
    fun `consultation cannot publish changed evidence as a fresh source`(change:String)=fixture {db->transaction(db) {
        exitCase();val value=statutoryReview(EnumDisclosureSubject.EXIT_CONSULTATION)
        val review=service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",value)
        service.enable("museum",review)
        assertTrue(service.feed(EnumDisclosureSubject.EXIT_CONSULTATION).single().available)
        when(change) {
            "opinionBody"->DeregistrationExpertOpinionDao.all().first().apply {content=content.copy(opinion="后改意见")}
            "proposalMissing"->CollectionPermanentArchiveDao.all().single {it.bizId=="exit" && it.phase=="PROPOSAL"}.delete()
            else->{val a=CollectionPermanentArchiveDao.all().first {it.phase.startsWith("EXPERT_")};CollectionPermanentArchiveFileDao.all().filter {it.archiveId==a.id.value}.forEach {it.delete()}}
        }
        val detail=service.detail("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit")
        assertFalse(detail["sourceReady"] as Boolean)
        assertNull(detail["sourceRevision"]);assertNull(detail["expertOpinionIds"])
        val feed=service.feed(EnumDisclosureSubject.EXIT_CONSULTATION).single();assertFalse(feed.available);assertNull(feed.content)
        assertThrows(BadRequestException::class.java){service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",value.copy(reviewedAt=LocalDateTime.now(),replacementReason="重新核查",replacementFileIds="replacement"))}
    }}

    @Test fun `duplicate review verifies its retained signed original`()=fixture {db->transaction(db) {
        val value=review();val id=save(value)
        val archive=CollectionPermanentArchiveDao.all().single {it.bizId==id && it.phase=="REVIEWED"}
        CollectionPermanentArchiveFileDao.all().filter {it.archiveId==archive.id.value}.forEach {it.delete()}
        assertThrows(BadRequestException::class.java){save(value)}
        assertEquals(1L,CollectionDisclosureReviewDao.count())
    }}

    private fun executedExit(): DeregistrationDao {
        val request=exitCase()
        assertFalse(service.detail("museum",EnumDisclosureSubject.EXIT_RESULT,"exit")["sourceReady"] as Boolean)
        val consultation=service.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"exit",statutoryReview(EnumDisclosureSubject.EXIT_CONSULTATION))
        EnumExitNoticeChannel.entries.forEach {exitStatutory.recordNotice("exit",statutoryNotice(consultation,it))}
        exitStatutory.recordAdministrative("exit",CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.PROVINCE,action=EnumAdministrativeAction.APPROVAL,authorityName="省文物部门",documentNo="正式批复001",issuedAt=java.time.LocalDate.of(2026,7,12),fileIds="proof"))
        SchemaUtils.create(io.hizhengfu.cloud.flow.dao.FlowInstanceLogs,CollectionOperationLocks,CollectionAuxiliaries,CollectionLedgerLogs,CollectionLedgerStatEvents,CollectionCategories,Warehouses,CollectionMedias,CollectionAttachments)
        actorRole("officer","EXIT_EDITOR",setOf(CollectionConst.DEREGISTER,CollectionConst.LEDGER_CORRECT))
        val locks=CollectionOperationLockService()
        val paperLedgers=mock(PaperLedgerService::class.java).also { `when`(it.requirePageReference("museum","paper-book",1)).thenReturn(PaperLedgerService.PageReference("paper-book","总账第一册",1,20)) }
        val workflow=DeregistrationService(mock(io.hizhengfu.cloud.flow.service.FlowInstanceService::class.java),scope,collections,mock(CollectionPrintService::class.java),binder,locks,exitStatutory,archives,CollectionAuxiliaryService(scope,binder,archives),paperLedgers)
        request.flowStatus=io.hizhengfu.cloud.common.domain.EnumFlowStatus.AUDIT;request.flowInstanceId="exit-flow"
        locks.acquire("museum",CollectionConst.BIZ_DEREGISTER,"exit",listOf("one"))
        listOf("keeper" to "keeper","leader" to "director").forEach {(node,user)->io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao.new {
            instanceId="exit-flow";taskId="task-$node";nodeKey=node;action="APPROVE";operator=user;comment="本人核对原件，确认同意"
        }}
        workflow.onFlowCompleted(request,io.hizhengfu.cloud.flow.domain.FlowCompletedEvent("exit-flow",CollectionConst.FLOW_DEREGISTER,"退出","exit",CollectionConst.BIZ_DEREGISTER,emptyMap(),"officer","经办人"))
        workflow.execute("exit",DeregistrationExecutionVo("proof",paperUpdatedAt=java.time.LocalDate.now(),dispositionReceiptFileIds="proof",paperLedgerBookId="paper-book",paperLedgerPageNo=1))
        return request
    }
    @Test fun `result copy requires actual execution archive and both account and object disposition`()=fixture { db -> transaction(db) {
        val request=executedExit()
        val v=statutoryReview(EnumDisclosureSubject.EXIT_RESULT).copy(reviewedAt=LocalDateTime.now())
        assertThrows(BadRequestException::class.java){service.review("museum",EnumDisclosureSubject.EXIT_RESULT,"exit",v.copy(content=v.content!!.copy(facts=mapOf(EnumPublicFact.NAME to "铜器"))))}
        val first=service.review("museum",EnumDisclosureSubject.EXIT_RESULT,"exit",v)
        fun publish(review:String,replacement:Boolean=false) {
            listOf(EnumExitNoticeChannel.MUSEUM_ONSITE,EnumExitNoticeChannel.MUSEUM_WEBSITE).forEach {exitStatutory.recordNotice("exit",statutoryNotice(review,it,replacement,EnumExitNoticeKind.RESULT).copy(startedOn=java.time.LocalDate.now()))}
        }
        publish(first)
        assertTrue(exitStatutory.enrich(request).currentNoticeComplete);val closedAt=request.announcedAt;assertNotNull(closedAt)
        val closed=CollectionPermanentArchiveDao.all().single {it.bizId=="exit" && it.phase=="CLOSED"}.snapshot
        val next=service.review("museum",EnumDisclosureSubject.EXIT_RESULT,"exit",statutoryReview(EnumDisclosureSubject.EXIT_RESULT,replacement=true).copy(reviewedAt=LocalDateTime.now()));assertFalse(exitStatutory.enrich(request).currentNoticeComplete)
        publish(next,true)
        assertTrue(exitStatutory.enrich(request).currentNoticeComplete);assertEquals(closedAt,request.announcedAt)
        assertEquals(4,DeregistrationNoticeDao.all().count {it.content.kind==EnumExitNoticeKind.RESULT})
        assertEquals(closed,CollectionPermanentArchiveDao.all().single {it.bizId=="exit" && it.phase=="CLOSED"}.snapshot)
    } }
    @Test fun `source museum transfer result can update publicity without replacing its first closed archive`()=fixture { db -> transaction(db) {
        actorRole("officer","TRANSFER_NOTICE_EDITOR",setOf(CollectionConst.LEDGER_CORRECT))
        SchemaUtils.create(CollectionRetainedLedgers)
        val signed=CollectionTransferExecutionVo(handedOn=at.toLocalDate(),sourceCustodianUserId="keeper",targetCustodianUserId="receiver",signedHandoverFileIds="proof",conditionDescription="逐件核实",conditionImageFileIds="proof",authenticityVerified=true,signedLedgerChangeFileIds="proof",receivingLedgerReceiptFileIds="proof")
        val request=CollectionTransferDao.new("transfer"){orgId="museum";fromOrgId="museum";toOrgId="target";collectionId="one";baseline=CollectionCatalogMapper.fromCollection(CollectionDao["one"].toModel());execution=signed;executedAt=at}
        archives.seal("museum",CollectionConst.BIZ_TRANSFER,"transfer","EXECUTED",mapOf("request" to request.toModel(),"execution" to signed),"proof")
        val first=service.review("museum",EnumDisclosureSubject.TRANSFER_RESULT,"transfer",statutoryReview(EnumDisclosureSubject.TRANSFER_RESULT,"transfer"))
        listOf(EnumExitNoticeChannel.MUSEUM_ONSITE,EnumExitNoticeChannel.MUSEUM_WEBSITE).forEach { transferStatutory.recordNotice("transfer",statutoryNotice(first,it,kind=EnumExitNoticeKind.RESULT)) }
        assertTrue(transferStatutory.enrich(request).currentNoticeComplete);val closed=archives.list(CollectionConst.BIZ_TRANSFER,"transfer").last { it["phase"]=="CLOSED" }
        val second=service.review("museum",EnumDisclosureSubject.TRANSFER_RESULT,"transfer",statutoryReview(EnumDisclosureSubject.TRANSFER_RESULT,"transfer",true));assertFalse(transferStatutory.enrich(request).currentNoticeComplete)
        listOf(EnumExitNoticeChannel.MUSEUM_ONSITE,EnumExitNoticeChannel.MUSEUM_WEBSITE).forEach { transferStatutory.recordNotice("transfer",statutoryNotice(second,it,true,EnumExitNoticeKind.RESULT)) }
        assertTrue(transferStatutory.enrich(request).currentNoticeComplete);assertEquals(closed,archives.list(CollectionConst.BIZ_TRANSFER,"transfer").last { it["phase"]=="CLOSED" })
        login("target");assertThrows(BadRequestException::class.java){transferStatutory.publicReviewOptions("transfer")}
        assertThrows(BadRequestException::class.java){service.detail("target",EnumDisclosureSubject.TRANSFER_RESULT,"transfer")}
    } }
    @Test fun `new statutory expert payload preserves existing resource review snapshots when empty`()=fixture { db -> transaction(db) {
        val id=save();service.enable("museum",id)
        assertFalse(JsonUtils.toJson(review().content)!!.contains("expertOpinions"));assertTrue(service.feed(EnumDisclosureSubject.RESOURCE).single().available)
        val notice=statutoryNotice("review").copy(disclosureReviewId=null)
        assertFalse(JsonUtils.toJson(notice)!!.contains("disclosureReviewId"))
    } }
}
