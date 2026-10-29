package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.EnumSource as NatureSource
import org.junit.jupiter.params.ParameterizedTest
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

class CollectionArchivePacketPersistenceTest {
    private val at=LocalDateTime.of(2026,1,2,12,0,0,123000000)
    private val scope=CollectionScopeResolver("multi", "")
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val retainedLedgers=CollectionRetainedLedgerService(scope,archives)
    private val service=CollectionArchivePacketService(scope,users,archives,retainedLedgers)
    @BeforeEach fun setup() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context);login()
    }
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun login(org: String="museum", permissions: List<String> = listOf(CollectionConst.ARCHIVE_MANAGE)) {
        val user=SecurityUser(id="officer",name="档案经办",username="officer",password="",groupId=org,groupIds=setOf(org),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=permissions.map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    private fun fixture(block:(Database)->Unit) {
        val path=java.nio.file.Files.createTempFile("archive-packet-",".sqlite")
        val pgConfig=System.getProperty("collection.test.pg.config")
        val pg=pgConfig?.let { config ->
            val properties=java.util.Properties().apply { java.nio.file.Files.newInputStream(java.nio.file.Path.of(config)).use(::load) }
            org.postgresql.ds.PGSimpleDataSource().apply { setURL(properties.getProperty("url"));user=properties.getProperty("user");password=properties.getProperty("password") }
        }
        val schema="archive_packet_"+java.util.UUID.randomUUID().toString().replace("-","")
        pg?.connection?.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
        pg?.currentSchema=schema
        val db=if(pg==null) Database.connect("jdbc:sqlite:$path",driver="org.sqlite.JDBC") else Database.connect(pg)
        try {
            transaction(db) {
                originalReaderSchema();originalReader("officer",CollectionConst.ARCHIVE_MANAGE)
                GroupDao.new("target") { name="他馆";type='1';parentId="0";parentIds="0," }
                SchemaUtils.create(CollectionOrgs,Collections,CollectionCatalogVersions,CollectionRetainedLedgers,ArchiveJurisdictions,CollectionArchivePackets,CollectionArchiveSubmissions,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
                CollectionOrgDao.new("museum"){name="本馆";code="M"};CollectionOrgDao.new("target"){name="他馆";code="T"}
                listOf("packet","photo","sign","jurisdiction","revision","submission","receipt",
                    "2100000000000000001","2100000000000000002","2100000000000000003","2100000000000000004")
                    .forEach { id -> FileDao.new(id){name="$id.pdf";this.path="/files/$id.pdf";hash=id;size=100;ext="pdf"} }
                val c=CollectionDao.new("one"){orgId="museum";totalRegNo="A1";name="铜器";quantity=2;unit="件";registeredAt=at.minusYears(1);nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.SECOND;catalogStatus=EnumCatalogStatus.APPROVED;lifeStatus=EnumCollectionStatus.IN_STORE}
                version("v1",1,CollectionCatalogMapper.fromCollection(c.toModel()))
            }
            block(db)
        } finally {
            pg?.connection?.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } }
            java.nio.file.Files.deleteIfExists(path)
        }
    }
    private fun version(id:String,n:Int,c:CollectionCatalogContentVo)=CollectionCatalogVersionDao.new(id){orgId="museum";catalogId="catalog";collectionId="one";version=n;catalogStatus=EnumCatalogStatus.APPROVED;content=c;auditTime=at.minusDays(1)}
    private fun jurisdiction()=ArchiveJurisdictionVo(EnumArchiveJurisdiction.LOCAL_COMPETENT,"主管文物局","国家文物局","主管文物局",at.toLocalDate().minusYears(1),at,"leader","jurisdiction")
    private fun packet()=CollectionArchivePacketVo("v1","档案A1",EnumCollectionArchiveFormat.BOTH,at,"archivist","档案室01柜","档案系统卷宗A1","packet","photo","sign",EnumArchiveSection.entries.associateWith { ArchiveSectionVo(EnumArchiveSectionStatus.INCLUDED,"原件目录第${it.ordinal+1}项","packet") })
    private fun sent(target:EnumArchiveFilingTarget=EnumArchiveFilingTarget.COMPETENT)=ArchivePacketSubmissionVo(target,if(target==EnumArchiveFilingTarget.NATIONAL)"国家文物局" else "主管文物局",at.plusDays(1),"报1","submission")
    private fun receipt(authority:String="主管文物局")=ArchivePacketReceiptVo(authority,at.plusDays(2),"备1","receipt")
    private fun view(): Map<String,Any?> = (service.detail("museum","one")["records"] as List<Map<String,Any?>>).last()
    @Test fun `archive original is limited to the current museum and verified source record`()=fixture { db -> transaction(db) {
        val ids=listOf("2100000000000000001","2100000000000000002","2100000000000000003","2100000000000000004")
        val jurisdictionId=service.jurisdiction("museum",jurisdiction().copy(basisFileIds=ids[0]))
        val packetId=service.record("museum","one",packet().copy(inventoryFileIds=ids[1]))
        val submissionId=service.submission("museum",packetId,sent().copy(submissionFileIds=ids[2]))
        service.receipt("museum",submissionId,receipt().copy(receiptFileIds=ids[3]))
        val original=archives.list(CollectionArchivePacketService.JURISDICTION_BIZ,jurisdictionId).single { it["phase"]=="VERIFIED" }
        val compiled=archives.list(CollectionArchivePacketService.BIZ,packetId).single { it["phase"]=="COMPILED" }
        val submitted=archives.list(CollectionArchivePacketService.BIZ,submissionId).single { it["phase"]=="SUBMITTED" }
        val filed=archives.list(CollectionArchivePacketService.BIZ,submissionId).single { it["phase"]=="FILED" }
        listOf(original,compiled,submitted,filed).zip(ids).forEach { (archive,file) ->
            assertDoesNotThrow { service.requireOriginal("museum",archive["id"] as String,file) }
        }
        val compiledId=compiled["id"] as String
        assertThrows(BadRequestException::class.java) { service.requireOriginal("target",compiledId,ids[1]) }
        assertThrows(BadRequestException::class.java) { service.requireOriginal("museum","missing",ids[1]) }
        assertThrows(BadRequestException::class.java) { service.requireOriginal("museum",compiledId,"2100000000000000009") }
        assertThrows(BadRequestException::class.java) { service.requireOriginal("museum",compiledId,"../file") }
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { service.requireOriginal("museum",compiledId,ids[1]) }
    } }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "jurisdiction,VERIFIED", "record,COMPILED",
        "submission,VERIFIED", "submission,COMPILED", "submission,JURISDICTION", "submission,SUBMITTED",
        "receipt,VERIFIED", "receipt,COMPILED", "receipt,JURISDICTION", "receipt,SUBMITTED", "receipt,FILED",
    )
    fun `repeated archive operations verify every original stage`(operation: String, phase: String) {
        for (missing in listOf(true, false)) fixture { db -> transaction(db) {
            val jurisdictionId = service.jurisdiction("museum", jurisdiction())
            val packetId = service.record("museum", "one", packet())
            val submissionId = service.submission("museum", packetId, sent())
            service.receipt("museum", submissionId, receipt())
            val id = when (phase) {
                "VERIFIED" -> jurisdictionId
                "COMPILED", "JURISDICTION" -> packetId
                else -> submissionId
            }
            val original = CollectionPermanentArchiveDao.all().single { it.bizId == id && it.phase == phase }
            if (missing) original.delete() else original.snapshot = "{}"
            fun originals() = CollectionPermanentArchiveDao.all().map { Triple(it.id.value, it.snapshot, it.digest) }.sortedBy { it.first }
            val before = originals()
            val packetBefore = CollectionArchivePacketDao[packetId].content
            val submissionBefore = CollectionArchiveSubmissionDao[submissionId].content
            val receiptBefore = CollectionArchiveSubmissionDao[submissionId].receipt
            assertThrows(BadRequestException::class.java) {
                when (operation) {
                    "jurisdiction" -> service.jurisdiction("museum", jurisdiction())
                    "record" -> service.record("museum", "one", packet())
                    "submission" -> service.submission("museum", packetId, sent())
                    else -> service.receipt("museum", submissionId, receipt())
                }
            }
            assertEquals(before, originals())
            assertEquals(packetBefore, CollectionArchivePacketDao[packetId].content)
            assertEquals(submissionBefore, CollectionArchiveSubmissionDao[submissionId].content)
            assertEquals(receiptBefore, CollectionArchiveSubmissionDao[submissionId].receipt)
        } }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("jurisdiction,revoked", "jurisdiction,disabled", "jurisdiction,moved", "record,revoked", "record,disabled", "record,moved", "submission,revoked", "submission,disabled", "submission,moved", "receipt,revoked", "receipt,disabled", "receipt,moved")
    fun `writes require current eligibility despite old login`(operation: String, change: String)=fixture { db -> transaction(db) {
        val j=jurisdiction();val v=packet();val sent=sent();val received=receipt()
        service.jurisdiction("museum",j);val packetId=service.record("museum","one",v)
        val submissionId=service.submission("museum",packetId,sent);service.receipt("museum",submissionId,received)
        when(change) {
            "revoked" -> RolePermissions.deleteAll()
            "disabled" -> UserDao["officer"].enabled=false
            "moved" -> UserDao["officer"].groupId="target"
        }
        fun originals()=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }.sortedBy { it.first }
        val before=originals();val count=CollectionArchiveSubmissionDao.count()
        assertThrows(BadRequestException::class.java) { when(operation) {
                "jurisdiction" -> service.jurisdiction("museum",j)
                "record" -> service.record("museum","one",v)
                "submission" -> service.submission("museum",packetId,sent)
                else -> service.receipt("museum",submissionId,received)
            } }
        assertEquals(before,originals());assertEquals(count,CollectionArchiveSubmissionDao.count())
    } }
    @Test fun `revoked current archive duty rejects both original directory and jurisdiction history`()=fixture { db -> transaction(db) {
        service.record("museum","one",packet());service.jurisdiction("museum",jurisdiction())
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.detail("museum","one")}
        assertThrows(BadRequestException::class.java){service.jurisdictions("museum")}
    } }
    @Test fun `archive select reads current originals without gaining archive write duty`()=fixture { db -> transaction(db) {
        service.record("museum","one",packet());service.jurisdiction("museum",jurisdiction())
        RolePermissions.deleteAll();actorRole("officer","ARCHIVE_READ_ONLY",setOf(CollectionConst.ARCHIVE_SELECT))
        login(permissions=listOf(CollectionConst.ARCHIVE_SELECT))
        assertEquals(1,(service.detail("museum","one")["records"] as List<*>).size)
        assertEquals(1,service.jurisdictions("museum").size)
        assertThrows(BadRequestException::class.java) { service.record("museum","one",packet().copy(preparedAt=at.plusHours(1),replacementReason="仅测试拒绝",replacementFileIds="revision")) }
        assertThrows(BadRequestException::class.java) { service.jurisdiction("museum",jurisdiction().copy(verifiedAt=at.plusDays(1),replacementReason="仅测试拒绝",replacementFileIds="revision")) }
    } }
    @Test fun `current department governs archive history despite old museum login`()=fixture { db -> transaction(db) {
        service.record("museum","one",packet())
        UserDao["officer"].groupId="target"
        assertThrows(BadRequestException::class.java){service.detail("museum","one")}
        assertDoesNotThrow{service.jurisdictions("target")}
    } }
    @Test fun `foreign submission cannot appear in local packet or satisfy actual filing`()=fixture { db -> transaction(db) {
        service.jurisdiction("museum",jurisdiction());val id=service.record("museum","one",packet())
        val sent=service.submission("museum",id,sent());service.receipt("museum",sent,receipt())
        CollectionArchiveSubmissionDao[sent].orgId="target"
        assertThrows(BadRequestException::class.java){service.detail("museum","one")}
    } }

    @Test fun `disabled accounts and old administrator sessions cannot read complete archive originals`()=fixture { db -> transaction(db) {
        service.record("museum","one",packet())
        val old=SecurityUser(id="officer",name="旧管理员",username="officer",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(io.hizhengfu.cloud.common.domain.EnumRole.ADMIN.value),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(old,null,old.authorities)
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.detail("museum","one")}
        actorRole("officer","READ_AGAIN",setOf(CollectionConst.ARCHIVE_MANAGE));UserDao["officer"].enabled=false
        assertThrows(BadRequestException::class.java){service.jurisdictions("museum")}
    } }
    @Test fun `historical access requires current archive duty and cross grant and respects single configuration`()=fixture { db -> transaction(db) {
        service.jurisdiction("museum",jurisdiction())
        CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        assertThrows(BadRequestException::class.java){service.jurisdictions("museum")}
        actorRole("officer","CROSS",setOf(CollectionConst.CROSS_ORG))
        assertEquals(1,service.jurisdictions("museum").size)
        val singleScope=CollectionScopeResolver("single","museum")
        val single=CollectionArchivePacketService(singleScope,users,archives,CollectionRetainedLedgerService(singleScope,archives))
        assertThrows(BadRequestException::class.java){single.jurisdictions("target")}
        RolePermissions.deleteAll();actorRole("officer","CROSS_ONLY",setOf(CollectionConst.CROSS_ORG,CollectionConst.SELECT))
        assertThrows(BadRequestException::class.java){service.jurisdictions("museum")}
    } }
    @Test fun `cross museum permission never permits foreign catalog jurisdiction or archive references inside a local packet`()=fixture { db -> transaction(db) {
        service.jurisdiction("museum",jurisdiction());val id=service.record("museum","one",packet())
        service.submission("museum",id,sent())
        actorRole("officer","CROSS",setOf(CollectionConst.CROSS_ORG))
        val row=CollectionArchivePacketDao[id];val source=row.source
        row.source=source.copy(catalogVersion=source.catalogVersion.copy(collectionId="foreign"))
        assertThrows(BadRequestException::class.java){service.detail("museum","one")};row.source=source
        val frozen=row.jurisdiction
        val other=ArchiveJurisdictionDao.new {orgId="target";version=1;content=jurisdiction()}
        row.jurisdiction=other.snapshot()
        assertThrows(BadRequestException::class.java){service.detail("museum","one")};row.jurisdiction=frozen
        val original=CollectionPermanentArchiveDao.all().first {it.bizId==id}
        original.orgId="target"
        assertThrows(BadRequestException::class.java){service.detail("museum","one")};original.orgId="museum"
        val file=CollectionPermanentArchiveFileDao.all().first {it.archiveId==original.id.value}
        file.bizId="foreign"
        assertThrows(BadRequestException::class.java){service.detail("museum","one")}
        assertEquals("foreign",file.bizId)
    } }

    @Test fun `approved catalog alone does not establish an archive and missing topics stay incomplete`()=fixture { db -> transaction(db) {
        val c=CollectionDao["one"];assertThrows(BadRequestException::class.java){service.requireEstablished(c)}
        service.record("museum","one",packet().copy(sections=emptyMap()))
        assertThrows(BadRequestException::class.java){service.requireEstablished(c)}
        assertEquals(10,(view()["coverageIssues"] as List<*>).size)
        val fixed=packet().copy(preparedAt=at.plusHours(1),replacementReason="补齐实际目录与原件",replacementFileIds="revision")
        service.record("museum","one",fixed);assertDoesNotThrow { service.requireEstablished(c) };assertEquals(2L,CollectionArchivePacketDao.count())
    } }
    @Test fun `establishment and actual administrative filing are independent phases`()=fixture { db -> transaction(db) {
        val id=service.record("museum","one",packet());assertEquals(id,service.record("museum","one",packet()))
        assertDoesNotThrow { service.requireEstablished(CollectionDao["one"]) };assertEquals(false,view()["filingCompleteForPacket"])
        assertThrows(BadRequestException::class.java){service.submission("museum",id,sent())}
        service.jurisdiction("museum",jurisdiction());val submission=service.submission("museum",id,sent())
        assertEquals(false,view()["filingCompleteForPacket"]);service.receipt("museum",submission,receipt());assertEquals(true,view()["filingCompleteForPacket"])
        assertEquals(submission,service.receipt("museum",submission,receipt()))
        assertThrows(BadRequestException::class.java){service.receipt("museum",submission,receipt().copy(filingNo="覆盖"))}
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("receipt")}
    } }
    @ParameterizedTest
    @NatureSource(value=EnumCollectionNature::class,names=["RELIC","SCIENTIFIC_FOSSIL"])
    fun `first grade local museum archives require both competent and national receipts`(nature:EnumCollectionNature)=fixture { db -> transaction(db) {
        CollectionCatalogVersionDao["v1"].content=CollectionCatalogVersionDao["v1"].content.copy(nature=nature)
        CollectionCatalogVersionDao["v1"].content=CollectionCatalogVersionDao["v1"].content.copy(level=EnumRelicLevel.FIRST)
        service.jurisdiction("museum",jurisdiction());val id=service.record("museum","one",packet())
        val national=service.submission("museum",id,sent(EnumArchiveFilingTarget.NATIONAL));service.receipt("museum",national,receipt("国家文物局"))
        assertEquals(false,view()["filingCompleteForPacket"])
        val local=service.submission("museum",id,sent());service.receipt("museum",local,receipt());assertEquals(true,view()["filingCompleteForPacket"])
        assertEquals(2,CollectionArchiveSubmissionDao.count().toInt())
    } }
    @ParameterizedTest
    @NatureSource(value=EnumCollectionNature::class,names=["RELIC","SCIENTIFIC_FOSSIL"])
    fun `nationally supervised museum files non first relic archive to national authority`(nature:EnumCollectionNature)=fixture { db -> transaction(db) {
        CollectionCatalogVersionDao["v1"].content=CollectionCatalogVersionDao["v1"].content.copy(nature=nature)
        service.jurisdiction("museum",jurisdiction().copy(kind=EnumArchiveJurisdiction.NATIONAL_COMPETENT,competentAuthorityName="国家文物局"))
        val id=service.record("museum","one",packet())
        val national=service.submission("museum",id,sent(EnumArchiveFilingTarget.NATIONAL));service.receipt("museum",national,receipt("国家文物局"))
        assertEquals(setOf(EnumArchiveFilingTarget.COMPETENT,EnumArchiveFilingTarget.NATIONAL),view()["requiredTargets"]);assertEquals(false,view()["filingCompleteForPacket"])
        val local=service.submission("museum",id,sent());service.receipt("museum",local,receipt());assertEquals(true,view()["filingCompleteForPacket"])
    } }
    @Test fun `other collections have established archives without fabricated relic grading or filing`()=fixture { db -> transaction(db) {
        CollectionCatalogVersionDao["v1"].content=CollectionCatalogVersionDao["v1"].content.copy(nature=EnumCollectionNature.OTHER,level=EnumRelicLevel.UNRATED)
        val value=packet().copy(sections=packet().sections+(EnumArchiveSection.GRADING to ArchiveSectionVo(EnumArchiveSectionStatus.NOT_APPLICABLE,"其他藏品不适用文物定级")))
        val id=service.record("museum","one",value);assertDoesNotThrow { service.requireEstablished(CollectionDao["one"]) }
        assertEquals(emptySet<EnumArchiveFilingTarget>(),view()["requiredTargets"]);assertEquals(false,view()["filingCompleteForPacket"])
        service.jurisdiction("museum",jurisdiction());assertThrows(BadRequestException::class.java){service.submission("museum",id,sent())}
    } }
    @Test fun `unoccurred optional activities are explicit and cannot hide missing required originals`()=fixture { db -> transaction(db) {
        val value=packet().copy(sections=packet().sections+(EnumArchiveSection.RESEARCH to ArchiveSectionVo(EnumArchiveSectionStatus.NOT_OCCURRED,"截至编制日未开展该项活动")))
        service.record("museum","one",value);assertDoesNotThrow { service.requireEstablished(CollectionDao["one"]) }
        val next=value.copy(preparedAt=at.plusHours(1),replacementReason="发现取得原件尚未归入",replacementFileIds="revision",sections=value.sections+(EnumArchiveSection.ACQUISITION to ArchiveSectionVo(EnumArchiveSectionStatus.NOT_OCCURRED,"不能用未发生隐藏取得记录")))
        service.record("museum","one",next);assertThrows(BadRequestException::class.java){service.requireEstablished(CollectionDao["one"])}
    } }
    @Test fun `persisted archive compares catalog timestamps at stored millisecond precision`()=fixture { db ->
        transaction(db) {
            CollectionCatalogVersionDao["v1"].auditTime=at.minusDays(1).withNano(123456000)
            CollectionCatalogVersionDao["v1"].submittedAt=at.minusDays(2).withNano(789123000)
            service.record("museum","one",packet())
        }
        transaction(db) {
            assertDoesNotThrow { service.requireEstablished(CollectionDao["one"]) }
            assertEquals(true,view()["matchesCurrentCatalog"])
        }
        transaction(db) { CollectionCatalogVersionDao["v1"].auditTime=at.minusDays(1).withNano(124456000) }
        transaction(db) {
            assertThrows(BadRequestException::class.java) { service.requireEstablished(CollectionDao["one"]) }
            assertEquals(false,view()["matchesCurrentCatalog"])
        }
    }
    @Test fun `changed approved catalog requires updated archive and new packet inherits no receipts`()=fixture { db -> transaction(db) {
        service.jurisdiction("museum",jurisdiction());val old=service.record("museum","one",packet());val submission=service.submission("museum",old,sent());service.receipt("museum",submission,receipt())
        version("v2",2,CollectionCatalogVersionDao["v1"].content.copy(formalName="依法订正名称"));assertThrows(BadRequestException::class.java){service.requireEstablished(CollectionDao["one"])}
        val fresh=packet().copy(catalogVersionId="v2",preparedAt=at.plusDays(3),replacementReason="依法变更后动态补充档案",replacementFileIds="revision")
        val next=service.record("museum","one",fresh);assertDoesNotThrow { service.requireEstablished(CollectionDao["one"]) }
        assertEquals(false,view()["filingCompleteForPacket"]);assertNull(CollectionArchivePacketDao[next].jurisdiction);assertNotNull(CollectionArchiveSubmissionDao[submission].receipt)
    } }
    @Test fun `historical supplement does not displace current archive but newer current defect does`()=fixture { db -> transaction(db) {
        service.record("museum","one",packet())
        version("v2",2,CollectionCatalogVersionDao["v1"].content.copy(formalName="依法订正名称"))
        service.record("museum","one",packet().copy(catalogVersionId="v2",preparedAt=at.plusDays(3),replacementReason="更新当前编目档案",replacementFileIds="revision"))
        service.record("museum","one",packet().copy(preparedAt=at.plusDays(4),replacementReason="补录旧版历史原件",replacementFileIds="revision"))
        assertDoesNotThrow { service.requireEstablished(CollectionDao["one"]) }
        service.record("museum","one",packet().copy(catalogVersionId="v2",preparedAt=at.plusDays(5),replacementReason="当前版发现目录缺项",replacementFileIds="revision",sections=emptyMap()))
        assertThrows(BadRequestException::class.java) { service.requireEstablished(CollectionDao["one"]) }
    } }
    @Test fun `newer current packet with altered snapshot cannot fall back to older packet`()=fixture { db -> transaction(db) {
        service.record("museum","one",packet())
        val newer=service.record("museum","one",packet().copy(preparedAt=at.plusDays(1),replacementReason="同版补充目录",replacementFileIds="revision"))
        val row=CollectionArchivePacketDao[newer]
        row.source=row.source.copy(catalogVersion=row.source.catalogVersion.copy(auditTime=at.minusDays(3)))
        assertThrows(BadRequestException::class.java) { service.requireEstablished(CollectionDao["one"]) }
    } }
    @Test fun `routing change does not replace old filings but is flagged for current archive work`()=fixture { db -> transaction(db) {
        service.jurisdiction("museum",jurisdiction());val id=service.record("museum","one",packet());val submission=service.submission("museum",id,sent())
        val updated=jurisdiction().copy(verifiedAt=at.plusDays(2),competentAuthorityName="新的主管文物局",replacementReason="实际主管变更",replacementFileIds="revision")
        service.jurisdiction("museum",updated);service.receipt("museum",submission,receipt())
        assertEquals(true,view()["filingCompleteForPacket"]);assertEquals(false,view()["matchesCurrentJurisdiction"])
        assertEquals("主管文物局",CollectionArchivePacketDao[id].jurisdiction!!.content.competentAuthorityName)
    } }
    @Test fun `future jurisdiction cannot reroute current or historical actual submissions`()=fixture { db -> transaction(db) {
        val current=service.jurisdiction("museum",jurisdiction())
        service.jurisdiction("museum",jurisdiction().copy(kind=EnumArchiveJurisdiction.NATIONAL_COMPETENT,competentAuthorityName="国家文物局",effectiveOn=java.time.LocalDate.now().plusMonths(1),verifiedAt=at.plusDays(1),replacementReason="已获未来生效的归属文书",replacementFileIds="revision"))
        val id=service.record("museum","one",packet());service.submission("museum",id,sent())
        assertEquals(current,CollectionArchivePacketDao[id].jurisdiction!!.recordId)
        assertEquals(1,service.jurisdictions("museum").count { it["current"]==true })
    } }
    @Test fun `source museum archives remain after transfer and cannot establish receiving archive`()=fixture { db -> transaction(db) {
        assertEquals("CURRENT_MUSEUM", service.collections("museum").single()["source"])
        service.record("museum","one",packet());val c=CollectionDao["one"]
        val frozen=CollectionRetainedLedgerSnapshot(c.toModel(),listOf(CollectionCatalogVersionDao["v1"].toModel()),
            identity=CollectionRetainedLedgerIdentity("museum","one","transfer","target"))
        val retained=CollectionRetainedLedgerDao.new { orgId="museum";collectionId="one";transferId="transfer";totalRegNo="A1";name="铜器";targetOrgId="target";archiveId="";snapshot=frozen }
        retained.archiveId=archives.seal("museum","COLLECTION_RETAINED_LEDGER",retained.id.value,"TRANSFERRED",frozen,null)
        c.orgId="target";assertEquals("RETAINED_SOURCE_MUSEUM",service.detail("museum","one")["source"])
        assertEquals(mapOf("id" to "one", "totalRegNo" to "A1", "name" to "铜器", "source" to "RETAINED_SOURCE_MUSEUM"), service.collections("museum").single())
        val snapshot=retained.snapshot
        retained.name="被改写的原馆名称"
        retained.snapshot=snapshot.copy(collection=snapshot.collection.copy(name="被改写的原馆名称"))
        assertThrows(BadRequestException::class.java){service.collections("museum")}
        assertThrows(BadRequestException::class.java){service.detail("museum","one")}
        retained.name="铜器";retained.snapshot=snapshot
        retained.snapshot=snapshot.copy(collection=snapshot.collection.copy(orgId="target"))
        assertThrows(BadRequestException::class.java){service.collections("museum")}
        assertThrows(BadRequestException::class.java){service.detail("museum","one")};retained.snapshot=snapshot
        UserDao["officer"].groupId="target";login("target");assertThrows(BadRequestException::class.java){service.requireEstablished(c)};assertThrows(BadRequestException::class.java){service.detail("museum","one")}
    } }
    @Test fun `archive choices require current dedicated permission and museum scope`()=fixture { db -> transaction(db) {
        assertThrows(BadRequestException::class.java) { service.collections("target") }
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { service.collections("museum") }
    } }
    @Test fun `archive originals require dedicated permission and actual institution identity`()=fixture { db -> transaction(db) {
        RolePermissions.deleteAll();actorRole("officer","ORDINARY",setOf(CollectionConst.SELECT,CollectionConst.CROSS_ORG));login(permissions=listOf(CollectionConst.SELECT,CollectionConst.CROSS_ORG));assertThrows(BadRequestException::class.java){service.detail("museum","one")};assertThrows(BadRequestException::class.java){service.record("museum","one",packet())}
        login();doThrow(BadRequestException("并非本馆人员")).`when`(users).requireMuseumMember("archivist","museum")
        assertThrows(BadRequestException::class.java){service.record("museum","one",packet())}
        UserDao["officer"].groupId="target";login("target");assertThrows(BadRequestException::class.java){service.record("target","one",packet())}
    } }
    @Test fun `internal reciprocal exchange check exposes no foreign archive read access`()=fixture { db -> transaction(db) {
        service.record("museum","one",packet());UserDao["officer"].groupId="target";login("target",listOf(CollectionConst.LEDGER_CORRECT))
        assertDoesNotThrow { service.requireEstablished(CollectionDao["one"]) }
        assertThrows(BadRequestException::class.java){service.detail("museum","one")}
    } }
    @Test fun `missing physical storage invalid chronology and altered originals cannot be completed`()=fixture { db -> transaction(db) {
        assertThrows(BadRequestException::class.java){service.record("museum","one",packet().copy(physicalLocation=null))}
        assertThrows(BadRequestException::class.java){service.record("museum","one",packet().copy(preparedAt=at.minusDays(2)))}
        val id=service.record("museum","one",packet());CollectionArchivePacketDao[id].content=packet().copy(archiveNo="篡改")
        assertThrows(BadRequestException::class.java){service.requireEstablished(CollectionDao["one"])}
        CollectionArchivePacketDao[id].content=packet();CollectionPermanentArchiveDao.all().first { it.bizId==id && it.phase=="COMPILED" }.delete()
        assertThrows(BadRequestException::class.java){service.requireEstablished(CollectionDao["one"])}
    } }
    @Test fun `receipt cannot change authority precede submission or forge future completion`()=fixture { db -> transaction(db) {
        service.jurisdiction("museum",jurisdiction());val id=service.record("museum","one",packet())
        assertThrows(BadRequestException::class.java){service.submission("museum",id,sent().copy(authorityName="另一机关"))}
        val submission=service.submission("museum",id,sent());assertThrows(BadRequestException::class.java){service.receipt("museum",submission,receipt().copy(receivedAt=at))}
        assertThrows(BadRequestException::class.java){service.receipt("museum",submission,receipt().copy(receivedAt=LocalDateTime.now().plusDays(1)))}
        assertThrows(BadRequestException::class.java){service.receipt("museum",submission,receipt("其他机关"))}
    } }
    @Test fun `archive file failure rolls back and JSON preserves actual times and original identifiers`()=fixture { db ->
        assertThrows(BadRequestException::class.java){transaction(db){service.record("museum","one",packet().copy(inventoryFileIds="missing"))}}
        transaction(db){assertEquals(0L,CollectionArchivePacketDao.count());assertEquals(0L,CollectionPermanentArchiveDao.count())}
        val value=packet().copy(archiveNo="YES");assertEquals(value,JsonUtils.fromJson(JsonUtils.toJson(value)!!,CollectionArchivePacketVo::class.java))
        val id=transaction(db){service.record("museum","one",value)}
        transaction(db){assertEquals(value,CollectionArchivePacketDao[id].content)}
    }
}
