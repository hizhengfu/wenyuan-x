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

class CollectionManagementRulesPersistenceTest {
    private val at=LocalDateTime.of(2026,1,2,12,0,0,123000000)
    private val scope=CollectionScopeResolver("multi", "")
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val service=CollectionManagementRulesService(scope,users,archives)
    @BeforeEach fun setup() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        login()
    }
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun login(org: String="museum") {
        val user=SecurityUser(id="officer",name="经办",username="officer",password="",groupId=org,groupIds=setOf(org),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority("COLLECTION_ORG_MANAGE")),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    private fun fixture(block:(Database)->Unit) {
        val path=java.nio.file.Files.createTempFile("management-rules-",".sqlite")
        val db=Database.connect("jdbc:sqlite:$path",driver="org.sqlite.JDBC")
        try {
            transaction(db) {
                originalReaderSchema();originalReader("officer",CollectionConst.ORG_MANAGE)
                GroupDao.new("target") { name="他馆";type='1';parentId="0";parentIds="0," }
                SchemaUtils.create(CollectionOrgCodes, CollectionOrgCodeChanges, CollectionOrgs,Warehouses,Collections,CollectionRetainedLedgers,CollectionManagementRulesRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
                CollectionOrgDao.new("museum"){name="本馆";code="M"};CollectionOrgDao.new("target"){name="他馆";code="T"}
                listOf("rules","workflow","replacement","sent","filed","2100000000000000001").forEach { id -> FileDao.new(id){name="$id.pdf";this.path="/files/$id.pdf";hash=id;size=100;ext="pdf"} }
            }
            block(db)
        } finally { java.nio.file.Files.deleteIfExists(path) }
    }
    private fun rules()=CollectionManagementRulesVo("本馆藏品管理制度汇编","馆发1",at,at.toLocalDate(),"director","rules","workflow",EnumCollectionRuleTopic.entries.associateWith { "原件第${it.ordinal+1}章" })
    private fun sent()=CollectionRulesSubmissionVo("主管文物局","报1",at.plusDays(1),"sent")
    private fun filed()=CollectionRulesFilingVo("主管文物局","备1",at.plusDays(2),"filed")
    @Test fun `rules original must belong to the verified edition archive and current museum`()=fixture { db -> transaction(db) {
        val fileId="2100000000000000001"
        val id=service.register("museum",rules().copy(originalFileIds=fileId))
        val archive=archives.list(CollectionManagementRulesService.BIZ,id).single { it["phase"]=="ISSUED" }
        val archiveId=archive["id"] as String
        assertDoesNotThrow { service.requireOriginal("museum",id,archiveId,fileId) }
        for ((org,record,selected,file) in listOf(
            listOf("target",id,archiveId,fileId),
            listOf("museum","missing",archiveId,fileId),
            listOf("museum",id,"missing",fileId),
            listOf("museum",id,archiveId,"2100000000000000002"),
            listOf("museum",id,archiveId,"../file"),
        )) assertThrows(BadRequestException::class.java) { service.requireOriginal(org,record,selected,file) }
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { service.requireOriginal("museum",id,archiveId,fileId) }
    } }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "register,ISSUED,false", "register,ISSUED,true",
        "submission,ISSUED,false", "submission,ISSUED,true",
        "submission,SUBMITTED,false", "submission,SUBMITTED,true",
        "filing,ISSUED,false", "filing,ISSUED,true",
        "filing,SUBMITTED,false", "filing,SUBMITTED,true",
        "filing,FILED,false", "filing,FILED,true"
    )
    fun `repeated rules stages require original and predecessor archives`(stage:String, phase:String, altered:Boolean)=fixture { db -> transaction(db) {
        val id=service.register("museum",rules())
        if(stage!="register") service.submission("museum",id,sent())
        if(stage=="filing") service.filing("museum",id,filed())
        val original=CollectionPermanentArchiveDao.all().single { it.bizId==id && it.phase==phase }
        if(altered) original.snapshot="{}" else original.delete()
        val before=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }
        val row=CollectionManagementRulesDao[id]
        val facts=Triple(row.rules,row.submission,row.filing)
        assertThrows(BadRequestException::class.java) {
            when(stage) {
                "register" -> service.register("museum",rules())
                "submission" -> service.submission("museum",id,sent())
                else -> service.filing("museum",id,filed())
            }
        }
        assertEquals(before,CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) })
        assertEquals(facts,Triple(row.rules,row.submission,row.filing))
        assertEquals(1L,CollectionManagementRulesDao.count())
    } }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "register,revoked", "submission,revoked", "filing,revoked",
        "register,disabled", "submission,disabled", "filing,disabled",
        "register,moved", "submission,moved", "filing,moved",
        "register,museum-disabled", "submission,museum-disabled", "filing,museum-disabled",
        "register,single-other", "submission,single-other", "filing,single-other",
        "register,replay-revoked", "submission,replay-revoked", "filing,replay-revoked"
    )
    fun `rules writes recheck current authority and museum before changing original facts`(stage: String, change: String) = fixture { db -> transaction(db) {
        val id = if (stage != "register" || change == "replay-revoked") service.register("museum", rules()) else null
        if (stage == "filing" || (stage == "submission" && change == "replay-revoked")) service.submission("museum", id!!, sent())
        if (stage == "filing" && change == "replay-revoked") service.filing("museum", id!!, filed())
        when (change) {
            "revoked", "replay-revoked" -> RolePermissions.deleteAll()
            "disabled" -> UserDao["officer"].enabled = false
            "moved" -> UserDao["officer"].groupId = "target"
            "museum-disabled" -> CollectionOrgDao["museum"].orgStatus = EnumOrgStatus.DISABLED
        }
        val selected = if (change == "single-other") CollectionManagementRulesService(CollectionScopeResolver("single", "target"), users, archives) else service
        val before = CollectionManagementRulesDao.all().map { listOf(it.id.value, it.rules, it.submission, it.filing) } to CollectionPermanentArchiveDao.count()
        assertThrows(BadRequestException::class.java) {
            when (stage) {
                "register" -> selected.register("museum", rules())
                "submission" -> selected.submission("museum", id!!, sent())
                else -> selected.filing("museum", id!!, filed())
            }
        }
        assertEquals(before, CollectionManagementRulesDao.all().map { listOf(it.id.value, it.rules, it.submission, it.filing) } to CollectionPermanentArchiveDao.count())
    } }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["register", "submission", "filing"])
    fun `rules authorization loss during archive binding rolls back the whole stage`(stage: String) = fixture { db ->
        val id = transaction(db) {
            if (stage == "register") null else service.register("museum", rules()).also {
                if (stage == "filing") service.submission("museum", it, sent())
            }
        }
        val before = transaction(db) {
            CollectionManagementRulesDao.all().map { listOf(it.id.value, it.rules, it.submission, it.filing) } to CollectionPermanentArchiveDao.count()
        }
        doAnswer { RolePermissions.deleteAll(); null }.`when`(binder).bindSingle(anyString(), anyString(), anyString())
        assertThrows(BadRequestException::class.java) { transaction(db) {
            when (stage) {
                "register" -> service.register("museum", rules())
                "submission" -> service.submission("museum", id!!, sent())
                else -> service.filing("museum", id!!, filed())
            }
        } }
        transaction(db) {
            assertEquals(before, CollectionManagementRulesDao.all().map { listOf(it.id.value, it.rules, it.submission, it.filing) } to CollectionPermanentArchiveDao.count())
        }
    }

    @Test fun `rules historical cross grant is current and constrained by configured museum`()=fixture { db -> transaction(db) {
        service.register("museum",rules())
        UserDao["officer"].groupId="target"
        assertThrows(BadRequestException::class.java){service.detail("museum")}
        actorRole("officer","CURRENT_CROSS",setOf(CollectionConst.CROSS_ORG))
        CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        assertEquals("museum",service.detail("museum")["orgId"])
        val single=CollectionManagementRulesService(CollectionScopeResolver("single","target"),users,archives)
        assertThrows(BadRequestException::class.java){single.detail("museum")}
        UserDao["officer"].enabled=false
        assertThrows(BadRequestException::class.java){service.detail("museum")}
    } }

    @Test fun `current revocation blocks issued rules and filing originals despite old login`()=fixture { db -> transaction(db) {
        service.register("museum",rules());RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.detail("museum")}
    } }

    @Test fun `real issuance submission and filing remain separate immutable facts`()=fixture { db -> transaction(db) {
        val id=service.register("museum",rules());assertEquals(id,service.register("museum",rules()))
        assertNull(CollectionManagementRulesDao[id].submission);assertNull(CollectionManagementRulesDao[id].filing)
        assertThrows(BadRequestException::class.java){service.filing("museum",id,filed())}
        assertEquals(id,service.submission("museum",id,sent()));assertEquals(id,service.submission("museum",id,sent()))
        assertNull(CollectionManagementRulesDao[id].filing)
        service.filing("museum",id,filed());assertEquals(id,service.filing("museum",id,filed()))
        assertEquals(setOf("ISSUED","SUBMITTED","FILED"),archives.list(CollectionManagementRulesService.BIZ,id).map { it["phase"] }.toSet())
        assertThrows(BadRequestException::class.java){service.submission("museum",id,sent().copy(reportNo="另一个"))}
        assertThrows(BadRequestException::class.java){service.filing("museum",id,filed().copy(filingNo="另一个"))}
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("rules")}
    } }
    @Test fun `actual filed incomplete rules still expose missing coverage`()=fixture { db -> transaction(db) {
        val id=service.register("museum",rules().copy(topicReferences=mapOf(EnumCollectionRuleTopic.ACQUISITION to "第一章")))
        service.submission("museum",id,sent());service.filing("museum",id,filed())
        val row=(service.detail("museum")["records"] as List<Map<String,Any?>>).single()
        assertEquals(11,(row["missingTopics"] as List<*>).size);assertNotNull(row["filing"])
        assertFalse((row["missingTopics"] as List<*>).contains(EnumCollectionRuleTopic.ACQUISITION))
    } }
    @Test fun `future effective edition does not displace current original and inherits no filing`()=fixture { db -> transaction(db) {
        val first=service.register("museum",rules());service.submission("museum",first,sent());service.filing("museum",first,filed())
        val replacement=rules().copy(documentNo="馆发2",issuedAt=at.plusMonths(1),effectiveOn=java.time.LocalDate.now().plusMonths(1),replacementReason="完善内部流程",replacementFileIds="replacement")
        val second=service.register("museum",replacement)
        assertEquals(first,service.detail("museum")["currentId"]);assertNull(CollectionManagementRulesDao[second].submission)
        assertNotNull(CollectionManagementRulesDao[first].filing);assertEquals(2,CollectionManagementRulesDao[second].version)
        assertEquals(rules(),CollectionManagementRulesDao[first].rules)
    } }
    @Test fun `revisions need originals and preserve retroactive effective edition order`()=fixture { db -> transaction(db) {
        service.register("museum",rules())
        val revised=rules().copy(title="修订制度",issuedAt=at.plusDays(1),effectiveOn=at.toLocalDate().minusDays(1))
        assertThrows(BadRequestException::class.java){service.register("museum",revised)}
        val id=service.register("museum",revised.copy(replacementReason="依正式文书追溯施行",replacementFileIds="replacement"))
        assertEquals(id,service.detail("museum")["currentId"])
    } }
    @Test fun `source changes missing archives and invalid timing cannot file`()=fixture { db -> transaction(db) {
        assertThrows(BadRequestException::class.java){service.register("museum",rules().copy(issuedAt=LocalDateTime.now().plusDays(1)))}
        assertThrows(BadRequestException::class.java){service.register("museum",rules().copy(topicReferences=mapOf(EnumCollectionRuleTopic.GRADING to "")))}
        val id=service.register("museum",rules())
        assertThrows(BadRequestException::class.java){service.submission("museum",id,sent().copy(submittedAt=at.minusDays(1)))}
        service.submission("museum",id,sent())
        assertThrows(BadRequestException::class.java){service.filing("museum",id,filed().copy(authorityName="其他机关"))}
        assertThrows(BadRequestException::class.java){service.filing("museum",id,filed().copy(filedAt=at))}
        val row=CollectionManagementRulesDao[id];row.rules=row.rules.copy(title="偷偷改原件")
        assertThrows(BadRequestException::class.java){service.filing("museum",id,filed())}
        row.rules=rules();CollectionPermanentArchiveDao.all().first { it.phase=="SUBMITTED" }.delete()
        assertThrows(BadRequestException::class.java){service.filing("museum",id,filed())}
    } }
    @Test fun `museum scope and actual responsible role are enforced`()=fixture { db -> transaction(db) {
        val id=service.register("museum",rules());verify(users).requireMuseumMember("director","museum");verify(users).requireRole("director","museum",CollectionConst.ROLE_DIRECTOR)
        UserDao["officer"].groupId="target";login("target");assertThrows(BadRequestException::class.java){service.detail("museum")};assertThrows(BadRequestException::class.java){service.submission("museum",id,sent())}
        assertThrows(BadRequestException::class.java){service.submission("target",id,sent())}
        doThrow(BadRequestException("非实际任职人员")).`when`(users).requireRole("director","target",CollectionConst.ROLE_DIRECTOR)
        assertThrows(BadRequestException::class.java){service.register("target",rules())}
    } }
    @Test fun `archive failure rolls back and JSON preserves literal numbers and milliseconds`()=fixture { db ->
        assertThrows(BadRequestException::class.java){transaction(db){service.register("museum",rules().copy(workflowFileIds="missing"))}}
        transaction(db){assertEquals(0L,CollectionManagementRulesDao.count());assertEquals(0L,CollectionPermanentArchiveDao.count())}
        val value=rules().copy(documentNo="YES",topicReferences=emptyMap())
        assertEquals(value,JsonUtils.fromJson(JsonUtils.toJson(value)!!,CollectionManagementRulesVo::class.java))
        val id=transaction(db){service.register("museum",value)}
        transaction(db){assertEquals(value,CollectionManagementRulesDao[id].rules)}
    }
    @Test fun `organization deletion cannot orphan retained permanent rules even in bulk`()=fixture { db -> transaction(db) {
        service.register("museum",rules())
        val organizations=CollectionOrgService(scope)
        assertThrows(BadRequestException::class.java){organizations.delete("museum")}
        assertThrows(BadRequestException::class.java){organizations.deleteByIds(listOf("museum"))}
        assertNotNull(CollectionOrgDao.findById("museum"));assertEquals(1L,CollectionManagementRulesDao.count())
        login("target");assertThrows(BadRequestException::class.java){organizations.delete("museum")}
        organizations.delete("target");assertNull(CollectionOrgDao.findById("target"))
    } }
}
