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
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.context.annotation.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime

class InstitutionOperationAccessRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate) {
        val cases=c.getBean(InstitutionSuccessionCaseService::class.java)
        val designated=c.getBean(DesignatedReceptionService::class.java)
        val items=c.getBean(InstitutionItemHandoverService::class.java)
        val investigations=c.getBean(InstitutionItemInvestigationService::class.java)
        val archives=c.getBean(CollectionPermanentArchiveService::class.java)
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun caseValue(org:String?="museum")=InstitutionSuccessionCaseVo(org,EnumInstitutionSuccession.MERGER,org ?: "other","registration","decision",LocalDate.now().minusDays(2),LocalDate.now(),"proof","实际非营利依据","proof","proof","keeper","representative","proof")
        fun notice(org:String?="museum")=DesignatedNoticeVo(org,EnumAuthorityLevel.PROVINCE,"authority","省文物局","notice",LocalDate.now().minusDays(2),LocalDate.now().minusDays(1),org ?: "other",EnumDesignatedSender.LAW_ENFORCEMENT,"移交人","sender","实际移交依据","proof",DesignatedItemVo("item","实际对象",EnumCollectionNature.OTHER,1,"件",EnumCompleteness.COMPLETE,"完好","proof"))
        fun assessment()=DesignatedAssessmentVo(LocalDateTime.now().minusSeconds(1),"reader","proof","符合本馆标准",true,listOf(DesignatedProfessionalOpinionVo("专家","机构","专业","proof","真实","有价值",true,"proof")),"proof")
        fun finding()=InstitutionUnrecordedVo("unrecorded","实际对象",1,"件","位置","人员",LocalDateTime.now().minusSeconds(1),"reader","特征","proof","proof")
        fun writes(expectedOrgId:String?=null):List<()->Any?> {
            val at=LocalDateTime.now().minusSeconds(1)
            return listOf(
                {items.propose("case-museum",mock(InstitutionItemProposalVo::class.java),expectedOrgId=expectedOrgId)},
                {items.approve("case-museum",mock(InstitutionItemApprovalVo::class.java),expectedOrgId=expectedOrgId)},
                {items.approve("case-museum",mock(InstitutionItemApprovalVo::class.java),true,expectedOrgId=expectedOrgId)},
                {items.handOver("case-museum",mock(InstitutionItemHandoverVo::class.java),expectedOrgId=expectedOrgId)},
                {investigations.investigate("case-museum",mock(InstitutionItemInvestigationVo::class.java),expectedOrgId=expectedOrgId)},
                {investigations.confirm("case-museum",mock(InstitutionItemInvestigationConfirmationVo::class.java),expectedOrgId=expectedOrgId)},
                {investigations.confirm("case-museum",mock(InstitutionItemInvestigationConfirmationVo::class.java),true,expectedOrgId=expectedOrgId)},
                {cases.create(caseValue(),expectedOrgId=expectedOrgId)}, {cases.capture("case-museum",InstitutionScopeCaptureVo("old",at,"reader","范围","proof","proof"),expectedOrgId=expectedOrgId)},
                {cases.addUnrecorded("case-museum",finding(),expectedOrgId=expectedOrgId)}, {cases.followUp("case-museum",InstitutionItemFollowUpVo("none",at,"reader","keeper","问题","措施",at,"proof"),expectedOrgId=expectedOrgId)},
                {cases.resolve("case-museum",InstitutionItemResolutionVo("none",EnumSuccessionResolution.LINKED_ITEM,"none",at,"reader","结论","proof"),expectedOrgId=expectedOrgId)},
                {cases.review("case-museum",InstitutionScopeReviewVo("none","none",at,"reader",true,"接收人","proof","说明","proof"),expectedOrgId=expectedOrgId)},
                {designated.create(notice(),expectedOrgId=expectedOrgId)},{designated.assess("notice-museum",assessment(),expectedOrgId=expectedOrgId)},
                {designated.custody("notice-museum",DesignatedCustodyVo(at,"reader","store","reader",setOf("本人"),emptyList(),"proof"),expectedOrgId=expectedOrgId)},
                {designated.accept("notice-museum",DesignatedAcceptanceVo(at.toLocalDate(),"proof",at,"reader","结论","proof"),expectedOrgId=expectedOrgId)},
                {designated.refer("notice-museum",DesignatedReferralVo(at,"reader","authority","ref","内容","proof","proof"),expectedOrgId=expectedOrgId)},
                {designated.reassign("notice-museum",DesignatedReassignmentVo(at.toLocalDate(),"reader","authority","省文物局","ref","新馆","new","公益","item","proof"),expectedOrgId=expectedOrgId)},
                {designated.settle("notice-museum",DesignatedSettlementVo(at,"reader",EnumDesignatedHandover.DIRECT_FROM_SENDER,"新馆","new",setOf("发"),setOf("收"),emptyList(),"结果","proof"),expectedOrgId=expectedOrgId)})
        }
    }
    private fun login(permission:String="ADMIN") {
        val u=SecurityUser(id="reader",name="旧登录",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf(SimpleGrantedAuthority(permission)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(u,null,u.authorities)
    }
    private fun fixture(mode:String="single",body:(Runtime)->Unit) {
        login()
        val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("institution-${System.nanoTime()}.db")}"})
        AnnotationConfigApplicationContext().use {c->
            c.beanFactory.registerSingleton("transactionManager",manager);c.beanFactory.registerSingleton("scope",CollectionScopeResolver(mode,"museum"))
            c.beanFactory.registerSingleton("users",mock(CollectionResponsibleUserService::class.java));c.beanFactory.registerSingleton("archives",mock(CollectionPermanentArchiveService::class.java))
            c.register(Transactions::class.java,InstitutionSuccessionCaseService::class.java,DesignatedReceptionService::class.java,InstitutionItemHandoverService::class.java,InstitutionItemInvestigationService::class.java);c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager))
            try {
                r.db {
                    originalReaderSchema();originalReader("reader",CollectionConst.STORAGE);actorRole("reader","FILE_reader",setOf(CollectionConst.ACQUIRE))
                    SchemaUtils.create(CollectionOrgs,Collections,Warehouses,CollectionAuxiliaries,Acquisitions,AccessionReceipts,AcquisitionComplianceCases,DesignatedReceptions,DesignatedReceptionRecords,CollectionTransfers,CollectionRetainedLedgers,InstitutionSuccessionCases,InstitutionSuccessionItems,InstitutionSuccessionRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles)
                    for(org in listOf("museum","other")) {
                        CollectionOrgDao.new(org){name=org;code=org;ownership=EnumOwnership.STATE;systemGroupId=org}
                        WarehouseDao.new("store-$org"){orgId=org;name=org;sceneType=EnumLocationScene.STORAGE}
                    }
                    doAnswer {call->
                        CollectionPermanentArchiveDao.new {
                            orgId=call.getArgument(0);bizType=call.getArgument(1);bizId=call.getArgument(2);phase=call.getArgument(3)
                            snapshot=InstitutionSuccessionCaseEvidence.json(call.getArgument<Any>(4));digest=CollectionCareEvidence.digest(snapshot)
                        }.id.value
                    }.`when`(r.archives).seal(anyString(),anyString(),anyString(),anyString(),anyMap<String,Any>(),nullable(String::class.java))
                    for(org in listOf("museum","other")) {
                        val case=InstitutionSuccessionCaseDao.new("case-$org"){orgId=org;sourceKey=org;content=r.caseValue(org)}
                        r.archives.seal(org,InstitutionSuccessionCaseEvidence.BIZ,case.id.value,"CASE",mapOf("orgId" to org,"sourceKey" to org,"content" to case.content),"proof")
                        val notice=DesignatedReceptionDao.new("notice-$org"){orgId=org;sourceKey=org;content=r.notice(org);orgSnapshot="{}"}
                        r.archives.seal(org,DesignatedReceptionEvidence.BIZ,notice.id.value,"NOTICE",DesignatedReceptionEvidence.noticeValue(notice),"proof")
                    }
                }
                clearInvocations(r.archives);body(r)
            } finally {SecurityContextHolder.clearContext()}
        }
    }
    @ParameterizedTest @ValueSource(strings=["permission","disabled","locked","role","group","moved","museum"])
    fun `all entry points reject stale login after current authorization changes`(kind:String)=fixture {r->
        r.db {when(kind){"permission"->RolePermissions.deleteAll();"disabled"->UserDao["reader"].enabled=false;"locked"->Users.update({Users.id eq "reader"}) {it[accountNonLocked]=false};"role"->RoleDao.find{Roles.code eq "FILE_reader"}.single().enabled=false;"group"->GroupDao["museum"].enabled=false;"moved"->UserDao["reader"].groupId="other";"museum"->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED}}
        (r.writes()+listOf({r.cases.detail("case-museum")},{r.designated.detail("notice-museum")},{r.cases.context()},{r.designated.context()})).forEach {call->assertThrows(BadRequestException::class.java){call()}}
        if(kind in setOf("permission","disabled","locked","role")) {
            assertThrows(BadRequestException::class.java){r.cases.page()};assertThrows(BadRequestException::class.java){r.designated.page(1,0)}
        } else {assertEquals(0,r.cases.page()["total"]);assertEquals(0,r.designated.page(1,0)["total"])}
        verifyNoInteractions(r.archives)
    }
    @Test fun `revocation while sealing final readiness rolls back acquisition and acceptance together`()=fixture {r->
        r.designated.assess("notice-museum",r.assessment())
        val at=LocalDateTime.now().minusSeconds(1)
        r.designated.custody("notice-museum",DesignatedCustodyVo(at,"reader","store-museum","reader",setOf("移交人"),listOf(AccessionPieceVo("one",at,"实际对象","完好",true,true,"proof")),"proof"))
        doAnswer {call->
            val row=CollectionPermanentArchiveDao.new {
                orgId=call.getArgument(0);bizType=call.getArgument(1);bizId=call.getArgument(2);phase=call.getArgument(3)
                snapshot=InstitutionSuccessionCaseEvidence.json(call.getArgument<Any>(4));digest=CollectionCareEvidence.digest(snapshot)
            }
            if(call.getArgument<String>(3)=="READY")RolePermissions.deleteAll()
            row.id.value
        }.`when`(r.archives).seal(anyString(),anyString(),anyString(),anyString(),anyMap<String,Any>(),nullable(String::class.java))
        assertTrue(assertThrows(BadRequestException::class.java){r.designated.accept("notice-museum",DesignatedAcceptanceVo(LocalDate.now(),"proof",LocalDateTime.now(),"reader","本人核对","proof"))}.message!!.contains("权限"))
        r.db {
            assertNull(DesignatedReceptionDao["notice-museum"].acquisitionId);assertEquals(0,AcquisitionDao.all().count())
            assertNull(DesignatedReceptionEvidence.last("notice-museum",EnumDesignatedRecord.ACCEPTANCE))
            assertEquals(2,DesignatedReceptionRecordDao.all().count());assertEquals(6,CollectionPermanentArchiveDao.all().count());assertTrue(RolePermissions.selectAll().count()>0)
        }
    }
    @Test fun `scope record sealing also rechecks authority and rolls back captured items`()=fixture {r->
        val digest=r.cases.detail("case-museum")["scanDigest"] as String
        doAnswer {RolePermissions.deleteAll();"archive"}.`when`(r.archives).seal(anyString(),anyString(),anyString(),anyString(),anyMap<String,Any>(),nullable(String::class.java))
        assertThrows(BadRequestException::class.java){r.cases.capture("case-museum",InstitutionScopeCaptureVo(digest,LocalDateTime.now(),"reader","实际范围","proof","proof"))}
        r.db {assertEquals(0,InstitutionSuccessionItemDao.all().count());assertEquals(0,InstitutionSuccessionRecordDao.all().count());assertTrue(RolePermissions.selectAll().count()>0)}
    }
    @Test fun `appending a case record after sealing rechecks authority`()=fixture {r->
        r.cases.addUnrecorded("case-museum",r.finding())
        val key=r.db {InstitutionSuccessionItemDao.all().single().itemKey}
        doAnswer {RolePermissions.deleteAll();"archive"}.`when`(r.archives).seal(anyString(),anyString(),anyString(),anyString(),anyMap<String,Any>(),nullable(String::class.java))
        assertThrows(BadRequestException::class.java){r.cases.followUp("case-museum",InstitutionItemFollowUpVo(key,LocalDateTime.now(),"reader","keeper","实际问题","实际措施",LocalDateTime.now().plusDays(1),"proof"))}
        r.db {assertEquals(1,InstitutionSuccessionItemDao.all().count());assertEquals(0,InstitutionSuccessionRecordDao.all().count());assertTrue(RolePermissions.selectAll().count()>0)}
    }
    @ParameterizedTest @ValueSource(strings=["", "other"])
    fun `all writes reject mismatched or blank original institution even with dual membership`(org:String)=fixture("multi") {r->
        r.db {UserGroups.insert {it[userId]="reader";it[groupId]="other"}}
        r.writes(org).forEach {call->assertTrue(assertThrows(BadRequestException::class.java){call()}.message!!.contains("表单"))}
        verifyNoInteractions(r.archives)
    }
    @Test fun `anonymous empty lists never bypass access`()=fixture {r->
        SecurityContextHolder.clearContext();assertThrows(BadRequestException::class.java){r.cases.page()};assertThrows(BadRequestException::class.java){r.designated.page(1,0,"nothing")}
    }
    @Test fun `current institution controls context list and implicit create despite stale primary group`()=fixture("multi") {r->
        r.db {UserDao["reader"].groupId="other"}
        for(context in listOf(r.cases.context(),r.designated.context())) {assertEquals("other",context["currentOrgId"]);assertEquals(true,context["canWrite"])}
        assertEquals(1,r.cases.page()["total"]);assertEquals(1,r.designated.page(1,0)["total"])
        val a=r.cases.create(r.caseValue(null));val b=r.designated.create(r.notice(null));r.db {assertEquals("other",InstitutionSuccessionCaseDao[a].orgId);assertEquals("other",DesignatedReceptionDao[b].orgId)}
        assertThrows(BadRequestException::class.java){r.cases.detail("case-museum")}
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `cross museum reading preserves configured museum and never grants writing`(mode:String)=fixture(mode) {r->
        r.db {actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))}
        assertEquals(if(mode=="single")1 else 2,r.cases.page()["total"])
        assertEquals(if(mode=="single")1 else 2,r.designated.page(1,0)["total"])
        if(mode=="multi") {
            assertEquals(false,r.cases.detail("case-other")["canOperate"]);assertEquals(false,r.designated.detail("notice-other")["canOperate"])
        } else {assertThrows(BadRequestException::class.java){r.cases.detail("case-other")};assertThrows(BadRequestException::class.java){r.designated.detail("notice-other")}}
        assertThrows(BadRequestException::class.java){r.cases.addUnrecorded("case-other",r.finding())};assertThrows(BadRequestException::class.java){r.designated.assess("notice-other",r.assessment())}
    }
    @Test fun `read only records and disabled history do not expose writable selectors`()=fixture("multi") {r->
        r.db {actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG));CollectionOrgDao["other"].orgStatus=EnumOrgStatus.DISABLED}
        assertEquals(false,r.cases.detail("case-other")["canOperate"]);assertEquals(false,r.designated.detail("notice-other")["canOperate"])
        assertFalse(r.designated.context()["locations"].toString().contains("other"));assertFalse(r.cases.context()["orgs"].toString().contains("other"))
        r.db {RolePermissions.deleteAll();actorRole("reader","FILE_reader",setOf(CollectionConst.LEDGER_CORRECT))}
        assertEquals(false,r.cases.context()["canWrite"]);assertEquals(emptyList<Any>(),r.cases.context()["orgs"])
        assertThrows(BadRequestException::class.java){r.cases.addUnrecorded("case-museum",r.finding())}
    }
    @Test fun `unassigned cross museum reader can inspect lists without a fabricated default museum`()=fixture("multi") {r->
        r.db {actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG));UserDao["reader"].groupId=null}
        for(context in listOf(r.cases.context(),r.designated.context())) {assertNull(context["currentOrgId"]);assertEquals(false,context["canWrite"]);assertEquals(emptyList<Any>(),context["orgs"])}
        assertEquals(2,r.cases.page()["total"]);assertEquals(2,r.designated.page(1,0)["total"])
        assertThrows(BadRequestException::class.java){r.cases.create(r.caseValue(null))};assertThrows(BadRequestException::class.java){r.designated.create(r.notice(null))}
    }
    @Test fun `new database permissions do not promise refreshed HTTP login`()=fixture {r->
        login(CollectionConst.SELECT);assertEquals(false,r.cases.detail("case-museum")["canOperate"]);assertEquals(false,r.designated.detail("notice-museum")["canOperate"])
        login();assertEquals(true,r.cases.detail("case-museum")["canOperate"]);assertEquals(true,r.designated.detail("notice-museum")["canOperate"])
    }
    @Test fun `institution originals remain bound to current case and frozen source phase`()=fixture {r->
        val caseArchive=r.db {
            val archive=InstitutionSuccessionCaseEvidence.archive("museum",InstitutionSuccessionCaseEvidence.BIZ,"case-museum","CASE")
            CollectionPermanentArchiveFileDao.new {orgId="museum";archiveId=archive.id.value;bizId="case-museum";fileId="case-file";metadata="""{"path":"legacy/case"}"""}
            archive.id.value
        }
        assertEquals(caseArchive to "case-file",r.cases.requireArchivedOriginal("case-museum","case-museum","legacy/case"))
        val itemId=r.cases.addUnrecorded("case-museum",r.finding())
        val key=r.db {InstitutionSuccessionItemDao[itemId].itemKey}
        val itemArchive=r.db {
            val archive=InstitutionSuccessionCaseEvidence.archive("museum",InstitutionSuccessionCaseEvidence.BIZ,itemId,"ITEM")
            CollectionPermanentArchiveFileDao.new {orgId="museum";archiveId=archive.id.value;bizId=itemId;fileId="item-file";metadata="""{"path":"proof"}"""}
            archive.id.value
        }
        assertEquals(itemArchive to "item-file",r.cases.requireArchivedOriginal("case-museum",key,"proof"))
        val now=LocalDateTime.now().minusSeconds(1)
        val recordId=r.cases.followUp("case-museum",InstitutionItemFollowUpVo(key,now,"reader","keeper","实际问题","实际措施",now.plusDays(1),"proof"))
        val recordArchive=r.db {
            val archive=InstitutionSuccessionCaseEvidence.archive("museum",InstitutionSuccessionCaseEvidence.BIZ,recordId,"FOLLOW_UP")
            CollectionPermanentArchiveFileDao.new {orgId="museum";archiveId=archive.id.value;bizId=recordId;fileId="record-file";metadata="""{"path":"legacy/record"}"""}
            archive.id.value
        }
        assertEquals(recordArchive to "record-file",r.cases.requireArchivedOriginal("case-museum",recordId,"legacy/record"))
        for((source,file) in listOf("case-other" to "legacy/case",recordId to "legacy/case",itemId to "legacy/record","missing" to "legacy/case"))
            assertThrows(BadRequestException::class.java) {r.cases.requireArchivedOriginal("case-museum",source,file)}
        r.db {RolePermissions.deleteAll()}
        assertThrows(BadRequestException::class.java) {r.cases.requireArchivedOriginal("case-museum",recordId,"legacy/record")}
    }
    @ParameterizedTest @ValueSource(strings=["case-permission","case-org","notice-permission","notice-org","create-case","create-notice"])
    fun `authorization or institution changes during sealing roll back business and archive`(kind:String)=fixture("multi") {r->
        r.db {UserGroups.insert {it[userId]="reader";it[groupId]="other"}}
        doAnswer {call->
            CollectionPermanentArchiveDao.new {orgId=call.getArgument(0);bizType=call.getArgument(1);bizId=call.getArgument(2);phase=call.getArgument(3);snapshot="{}";digest=CollectionCareEvidence.digest(snapshot)}
            if(kind.endsWith("org")) {if(kind.startsWith("case"))InstitutionSuccessionCaseDao["case-museum"].orgId="other" else DesignatedReceptionDao["notice-museum"].orgId="other"} else RolePermissions.deleteAll()
            "archive"
        }.`when`(r.archives).seal(anyString(),anyString(),anyString(),anyString(),anyMap<String,Any>(),nullable(String::class.java))
        assertThrows(BadRequestException::class.java) {when(kind){"create-case"->r.cases.create(r.caseValue());"create-notice"->r.designated.create(r.notice());else->if(kind.startsWith("case"))r.cases.addUnrecorded("case-museum",r.finding()) else r.designated.assess("notice-museum",r.assessment())}}
        r.db {assertEquals(0,InstitutionSuccessionItemDao.all().count());assertEquals(0,DesignatedReceptionRecordDao.all().count());assertEquals(2,InstitutionSuccessionCaseDao.all().count());assertEquals(2,DesignatedReceptionDao.all().count());assertEquals(4,CollectionPermanentArchiveDao.all().count());assertEquals("museum",InstitutionSuccessionCaseDao["case-museum"].orgId);assertEquals("museum",DesignatedReceptionDao["notice-museum"].orgId);assertTrue(RolePermissions.selectAll().count()>0)}
    }
}
