package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.diagnostic.CollectionEmergencyOriginalDiagnosticCheck
import io.hizhengfu.cloud.collection.diagnostic.CollectionEmergencyOriginalInspection
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.context.annotation.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class CollectionEmergencyAccessRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private val signature=CollectionCareSignatureVo("本人实际核对","proof")
    private fun login(id:String="reader") {
        val u=SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(u,null,u.authorities)
    }
    private fun plan():CollectionEmergencyPlanVo {
        val at=LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS)
        return CollectionEmergencyPlanVo(title="本馆实际预案",documentNo="YA-1",issuedAt=at,effectiveAt=at,keeperUserId="keeper",securityUserId="security",directorUserId="reader",commandAndDuties="分工",warningAndActivation="启动",emergencyContacts="联络",siteProtectionAndTransfer="保护",resourcesAndSupport="物资",firstGradeDamageReporting="国家报告",otherRelicDamageReporting="省级报告",theftRobberyLossReporting="报案",recoveryAndReview="复盘",scenarios=listOf(EmergencyScenarioPlanVo(EnumEmergencyScenario.FIRE,"实际推演",7,at.plusDays(7))),originalFileIds="proof")
    }
    private inner class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate) {
        val service=c.getBean(CollectionEmergencyService::class.java)
        val duties=c.getBean(CollectionEmergencyDutyService::class.java)
        val archives=c.getBean(CollectionPermanentArchiveService::class.java)
        fun diagnostic()=c.getBean(CollectionEmergencyOriginalDiagnosticCheck::class.java).check()
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun page(keyword:String="",size:Int=0)=service.page(MockHttpServletRequest().apply {setParameter("keyword",keyword);setParameter("pageSize",size.toString())})
        @Suppress("UNCHECKED_CAST") fun rows(keyword:String="",size:Int=0)=page(keyword,size).list as List<Map<String,Any?>>
        fun prepare(operation:String,targetType:String="plan"):()->String {
            if(operation=="plan"){val value=plan();return {service.registerPlan("museum",value)}}
            val p=service.registerPlan("museum",plan())
            val at=LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MILLIS)
            var reportId:String?=null
            var from="keeper"
            val target=if(targetType=="plan")EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.KEEPER) else {
                login("keeper")
                val drill=CollectionEmergencyDrillVo(planId=p,title="实际消防推演",mode=EnumEmergencyDrillMode.TABLETOP,startedAt=at.minusMinutes(10),endedAt=at.minusMinutes(9),scenarios=setOf(EnumEmergencyScenario.FIRE),conductorUserId="keeper",evaluatorUserId="security",participantUserIds=setOf("keeper","security"),actualLocation="会议室",actualActions="实际推演",communicationRehearsal="模拟联络",actualOutcome="已记录问题",involvesOriginals=false,hasIssues=true,issues=listOf(EmergencyDrillIssueVo("问题","实际发现","keeper",at.plusDays(1),"改进联络",false)),hasRealIncident=false,attendanceFileIds="proof",signedReportFileIds="proof")
                val d=service.recordDrill("museum",drill);reportId=d
                when(targetType) {
                    "drill"->{from="security";EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d,EnumEmergencyDutyRole.EVALUATOR)}
                    "issue"->EmergencyDutyTarget(EnumEmergencyDutyTarget.ISSUE,d,EnumEmergencyDutyRole.OWNER,1)
                    else->{
                        val i=service.improve("museum",d,EmergencyImprovementVo(issueNumber=1,completedAt=at,ownerUserId="keeper",verifierUserId="security",actualActions="已补齐并测试",result="实际结果",fileIds="proof"))
                        from="security";EmergencyDutyTarget(EnumEmergencyDutyTarget.IMPROVEMENT,i,EnumEmergencyDutyRole.REVIEWER)
                    }
                }
            }
            login("reader")
            val v=EmergencyDutyAppointmentVo(target=target,expectedReportId=reportId,toUserId="next",qualification=if(targetType=="issue")EnumEmergencyDutyQualification.MEMBER else EnumEmergencyDutyQualification.KEEPER,appointedAt=at,effectiveAt=at,reason="实际岗位调整",fileIds="proof",predecessorUnavailable=false)
            if(operation=="appoint")return {duties.appoint("museum",v)}
            val id=duties.appoint("museum",v)
            login(from)
            if(operation=="handover")return {duties.sign("museum",id,"HANDOVER",signature)}
            duties.sign("museum",id,"HANDOVER",signature);login("next")
            return {duties.sign("museum",id,"ACCEPT",signature)}
        }
        fun fault(kind:String) = db {
            val id=io.hizhengfu.cloud.common.utils.SecurityUtils.getUserId()!!
            when(kind) {
                "revoked"->RolePermissions.deleteWhere { RolePermissions.roleId eq org.jetbrains.exposed.v1.core.dao.id.EntityID("role:FILE_$id",Roles) }
                "disabled"->UserDao[id].enabled=false
                "role"->RoleDao["role:FILE_$id"].enabled=false
                "group"->GroupDao["museum"].enabled=false
                "moved"->UserDao[id].groupId="other"
                "museum"->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
            }
        }
        fun state()=db {listOf(CollectionEmergencyPlans,CollectionEmergencyDrills,CollectionEmergencyDrillRevisions,CollectionEmergencyImprovements,CollectionEmergencyDuties,CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionEmergencyFileAccesses,Files,Users,Roles,RolePermissions,Groups).associate {table->table.tableName to table.selectAll().map {row->table.columns.map {column->row[column].toString()}}}}
    }
    private fun fixture(mode:String="single",body:(Runtime)->Unit) {
        login()
        val scope=CollectionScopeResolver(mode,"museum")
        val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("emergency.db")}"})
        AnnotationConfigApplicationContext().use {c->
            c.beanFactory.registerSingleton("transactionManager",manager);c.beanFactory.registerSingleton("scope",scope)
            c.beanFactory.registerSingleton("users",CollectionResponsibleUserService(CollectionActorDirectory(scope),scope))
            val binder=mock(BusinessAttachmentBinder::class.java).also {`when`(it.resolveSingle(anyString())).thenAnswer {call->call.getArgument<String>(0)}}
            c.beanFactory.registerSingleton("binder",binder)
            c.beanFactory.registerSingleton("archives",spy(CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())))
            c.beanFactory.registerSingleton("incidents",mock(CollectionIncidentService::class.java))
            c.register(Transactions::class.java,CollectionEmergencyDutyService::class.java,CollectionEmergencyService::class.java,CollectionEmergencyOriginalDiagnosticCheck::class.java);c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager))
            try {
                r.db {
                    originalReaderSchema()
                    SchemaUtils.create(CollectionOrgs,CollectionEmergencyPlans,CollectionEmergencyDrills,CollectionEmergencyDrillRevisions,CollectionEmergencyDuties,CollectionEmergencyImprovements,CollectionEmergencyFileAccesses,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,Requisitions,CollectionIncidents)
                    for(org in listOf("museum","other"))CollectionOrgDao.new(org){name=if(org=="museum")"本馆" else "外馆";code=org;systemGroupId=org}
                    mapOf("reader" to CollectionConst.ROLE_DIRECTOR,"keeper" to CollectionConst.ROLE_STORAGE_HEAD,"security" to CollectionConst.ROLE_SECURITY,"next" to CollectionConst.ROLE_STORAGE_HEAD).forEach {(id,role)->originalReader(id,CollectionConst.EMERGENCY);actorRole(id,role)}
                    FileDao.new("proof"){name="测试签署.pdf";path="proof.pdf";hash="proof";size=100;ext="pdf"}
                }
                body(r)
            } finally {SecurityContextHolder.clearContext()}
        }
    }
    companion object {
        @JvmStatic fun deniedWrites()=listOf("plan","appoint","handover","accept").flatMap {operation->listOf("revoked","disabled","role","group","moved","museum").map {arrayOf(operation,it)}}
        @JvmStatic fun changedDuringSeal()=listOf("plan","appoint","handover","accept").flatMap {operation->listOf("revoked","disabled","role","group","moved","museum").map {arrayOf(operation,it)}}
        @JvmStatic fun dutyEvidenceFaults()=listOf("appoint","handover","accept").flatMap {operation->
            listOf("duty-deleted","duty-org","duty-version","source-deleted","source-org","archive-deleted","archive-changed","reference-deleted","reference-downgraded","index-deleted","index-org","old-archive-deleted","old-archive-changed","old-reference-deleted","old-reference-org","old-index-deleted","old-index-org","file-deleted").map {arrayOf(operation,it)}+
                (if(operation=="handover")emptyList() else listOf(arrayOf(operation,"successor-role")))+
                (if(operation=="appoint")listOf(arrayOf(operation,"issuer-role")) else emptyList())
        }
        @JvmStatic fun otherDutySources()=listOf("appoint","handover","accept").flatMap {operation->listOf("drill","issue","improvement").map {arrayOf(operation,it)}}
        @JvmStatic fun signedDiagnosticPhases()=listOf("plan-KEEPER","plan-SECURITY","plan-DIRECTOR","drill-EVALUATOR","drill-DIRECTOR","revision-DRILL_REVISION","revision-EVALUATOR","revision-DIRECTOR","improvement-REVIEW").flatMap {phase->listOf("missing-reference","all-originals").map {arrayOf(phase,it)}}
        @JvmStatic fun damagedOriginals()=listOf("plan","appoint","handover","accept","drill","improvement").flatMap {stage->
            listOf("missing-reference","extra-reference","reference-org","reference-biz","missing-file","empty-metadata","invalid-json","identity-digest","missing-index","index-org").map {arrayOf(stage,it)}
        }
    }
    @ParameterizedTest @MethodSource("deniedWrites")
    fun `current access applies before all registration and duty writes`(operation:String,kind:String)=fixture {r->
        val call=r.prepare(operation);r.fault(kind);val before=r.state();clearInvocations(r.archives)
        assertThrows(BadRequestException::class.java){call()}
        assertEquals(before,r.state());verifyNoInteractions(r.archives)
    }
    @ParameterizedTest @ValueSource(strings=["revoked","disabled","role","group","moved","museum"])
    fun `detail and page cannot retain authority from old login`(kind:String)=fixture {r->
        r.service.registerPlan("museum",plan());r.fault(kind)
        assertThrows(BadRequestException::class.java){r.service.detail("museum")}
        if(kind in setOf("revoked","disabled","role"))assertThrows(BadRequestException::class.java){r.page()} else assertEquals(0,r.page().total)
    }
    @ParameterizedTest @ValueSource(strings=["plan","appoint","handover","accept"])
    fun `current authorized people can persist and repeat their own action`(operation:String)=fixture {r->
        val call=r.prepare(operation);val id=call();val saved=r.state();assertEquals(id,call());assertEquals(saved,r.state())
    }
    @ParameterizedTest @MethodSource("changedDuringSeal")
    fun `access changed during archive insertion rolls back action and access mutations`(operation:String,kind:String)=fixture {r->
        val call=r.prepare(operation);val before=r.state()
        doAnswer {invocation->val id=invocation.callRealMethod();r.fault(kind);id}.`when`(r.archives).seal(anyString(),anyString(),anyString(),anyString(),any<Any>() ?: emptyMap<String,String>(),anyString())
        assertThrows(BadRequestException::class.java){call()};assertEquals(before,r.state())
    }
    @ParameterizedTest @MethodSource("dutyEvidenceFaults")
    fun `duty writes reject physical changes after file index insertion and preserve prior state`(operation:String,kind:String)=fixture {r->
        val call=r.prepare(operation)
        // 准备完成后才安装触发器，并以本次阶段匹配，不命中此前预案及交接。
        val phase=when(operation){"appoint"->"DUTY_APPOINTED";"handover"->"DUTY_HANDOVER";else->"DUTY_ACCEPTED"}
        val change=when(kind) {
            "duty-deleted"->"DELETE FROM col_emergency_duty WHERE id=(SELECT biz_id FROM col_permanent_archive WHERE id=NEW.archive_id)"
            "duty-org"->"UPDATE col_emergency_duty SET org_id='other' WHERE id=(SELECT biz_id FROM col_permanent_archive WHERE id=NEW.archive_id)"
            "duty-version"->"UPDATE col_emergency_duty SET version=version+1 WHERE id=(SELECT biz_id FROM col_permanent_archive WHERE id=NEW.archive_id)"
            "source-deleted"->"DELETE FROM col_emergency_plan"
            "source-org"->"UPDATE col_emergency_plan SET org_id='other'"
            "archive-deleted"->"DELETE FROM col_permanent_archive WHERE id=NEW.archive_id"
            "archive-changed"->"UPDATE col_permanent_archive SET snapshot='{}' WHERE id=NEW.archive_id"
            "reference-deleted"->"DELETE FROM col_permanent_archive_file WHERE archive_id=NEW.archive_id"
            "reference-downgraded"->"UPDATE col_permanent_archive_file SET metadata=json_object('id',file_id,'path','proof.pdf','storageType','LOCAL') WHERE archive_id=NEW.archive_id"
            "index-deleted"->"DELETE FROM col_emergency_file_access WHERE id=NEW.id"
            "index-org"->"UPDATE col_emergency_file_access SET org_id='other' WHERE id=NEW.id"
            "old-reference-deleted"->"DELETE FROM col_permanent_archive_file WHERE archive_id<>NEW.archive_id"
            "old-archive-deleted"->"DELETE FROM col_permanent_archive WHERE id<>NEW.archive_id"
            "old-archive-changed"->"UPDATE col_permanent_archive SET snapshot='{}',digest='${CollectionCareEvidence.digest("{}")}' WHERE id<>NEW.archive_id"
            "old-reference-org"->"UPDATE col_permanent_archive_file SET org_id='other' WHERE archive_id<>NEW.archive_id"
            "old-index-deleted"->"DELETE FROM col_emergency_file_access WHERE archive_id<>NEW.archive_id"
            "old-index-org"->"UPDATE col_emergency_file_access SET org_id='other' WHERE archive_id<>NEW.archive_id"
            "file-deleted"->"DELETE FROM sys_file WHERE id='proof'"
            "successor-role"->"UPDATE sys_role SET enabled=0 WHERE code='${CollectionConst.ROLE_STORAGE_HEAD}'"
            "issuer-role"->"UPDATE sys_role SET enabled=0 WHERE code='${CollectionConst.ROLE_DIRECTOR}'"
            else->error(kind)
        }
        r.db {TransactionManager.current().exec("CREATE TRIGGER duty_fault AFTER INSERT ON col_emergency_file_access WHEN (SELECT phase FROM col_permanent_archive WHERE id=NEW.archive_id)='$phase' BEGIN $change; END")}
        val before=r.state()
        assertThrows(BadRequestException::class.java){call()};assertEquals(before,r.state())
        r.db {TransactionManager.current().exec("DROP TRIGGER duty_fault")}
        val id=call();val after=r.state();assertEquals(id,call());assertEquals(after,r.state())
    }
    @ParameterizedTest @MethodSource("otherDutySources")
    fun `drill issue and improvement responsibility keep their actual source during persistence`(operation:String,target:String)=fixture {r->
        val call=r.prepare(operation,target)
        val phase=when(operation){"appoint"->"DUTY_APPOINTED";"handover"->"DUTY_HANDOVER";else->"DUTY_ACCEPTED"}
        val change=when(target){"drill"->"DELETE FROM col_emergency_drill";"issue"->"UPDATE col_emergency_drill SET org_id='other'";else->"DELETE FROM col_emergency_improvement"}
        r.db {TransactionManager.current().exec("CREATE TRIGGER duty_source_fault AFTER INSERT ON col_emergency_file_access WHEN (SELECT phase FROM col_permanent_archive WHERE id=NEW.archive_id)='$phase' BEGIN $change; END")}
        val before=r.state();assertThrows(BadRequestException::class.java){call()};assertEquals(before,r.state())
        r.db {TransactionManager.current().exec("DROP TRIGGER duty_source_fault")}
        val id=call();val saved=r.state();assertEquals(id,call());assertEquals(saved,r.state())
    }
    @ParameterizedTest @MethodSource("damagedOriginals")
    fun `damaged existing originals cannot be read scheduled or reused as complete evidence`(stage:String,kind:String)=fixture {r->
        val call=if(stage in setOf("drill","improvement"))r.prepare("appoint",stage) else r.prepare(stage)
        call()
        val phase=when(stage){"plan"->"ISSUED";"appoint"->"DUTY_APPOINTED";"handover"->"DUTY_HANDOVER";"accept"->"DUTY_ACCEPTED";"drill"->"DRILL";else->"IMPROVEMENT"}
        r.db {
            val archive=CollectionPermanentArchiveDao.find {CollectionPermanentArchives.phase eq phase}.single()
            val ref=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq archive.id.value}.single()
            when(kind) {
                "missing-reference"->ref.delete()
                "extra-reference"->{
                    val file=FileDao.new("extra"){name="未声明附件.pdf";path="extra.pdf";hash="extra";size=100;ext="pdf"}
                    CollectionPermanentArchiveFileDao.new {orgId=archive.orgId;archiveId=archive.id.value;bizId=archive.bizId;fileId=file.id.value;metadata=CollectionArchiveFileIdentity.metadata(file,archive.orgId,archive.bizType,archive.bizId,io.hizhengfu.cloud.common.file.FileReadPath.keys(file.path,"LOCAL",null))}
                }
                "reference-org"->ref.orgId="other"
                "reference-biz"->ref.bizId="wrong-business"
                "missing-file"->FileDao[ref.fileId].delete()
                "empty-metadata"->ref.metadata="{}"
                "invalid-json"->ref.metadata="{"
                "identity-digest"->{val node=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(ref.metadata) as tools.jackson.databind.node.ObjectNode;node.put("_archiveReadIdentityDigest","invalid");ref.metadata=node.toString()}
                "missing-index"->CollectionEmergencyFileAccesses.deleteWhere {CollectionEmergencyFileAccesses.archiveId eq archive.id.value}
                "index-org"->CollectionEmergencyFileAccessDao.find {CollectionEmergencyFileAccesses.archiveId eq archive.id.value}.forEach {it.orgId="other"}
            }
        }
        val broken=r.state()
        assertEquals(EnumDeploymentCheckState.FAIL,r.diagnostic().state)
        assertThrows(BadRequestException::class.java){r.service.detail("museum")}
        assertThrows(BadRequestException::class.java){r.page()}
        val tasks=r.db {CollectionEmergencyTaskSource().tasks("museum",LocalDateTime.now())}
        assertEquals(listOf("ORIGINAL_ERROR"),tasks.map {it.kind})
        assertThrows(BadRequestException::class.java){call()}
        assertEquals(broken,r.state())
    }
    @ParameterizedTest @ValueSource(strings=["current","legacy"])
    fun `frozen path declarations survive file rename without changing historical identity`(format:String)=fixture {r->
        `when`(r.c.getBean(BusinessAttachmentBinder::class.java).resolveSingle("proof.pdf")).thenReturn("proof")
        val value=plan().copy(originalFileIds="proof.pdf")
        val id=r.service.registerPlan("museum",value)
        r.db {
            if(format=="legacy")CollectionPermanentArchiveFileDao.all().forEach {it.metadata=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(FileDao["proof"].toModel())!!}
            FileDao["proof"].path="renamed-proof.pdf"
            FileDao["proof"].name="更正文件显示名称.pdf"
        }
        val before=r.state()
        assertEquals(EnumDeploymentCheckState.PASS,r.diagnostic().state)
        assertEquals("museum",r.service.detail("museum")["orgId"]);assertTrue(r.page().total>0)
        assertFalse(r.db {CollectionEmergencyTaskSource().tasks("museum",LocalDateTime.now())}.any {it.kind=="ORIGINAL_ERROR"})
        assertEquals(id,r.service.registerPlan("museum",value));assertEquals(before,r.state())
    }

    @ParameterizedTest @ValueSource(strings=["plan","appoint","handover","accept","drill","improvement"])
    fun `diagnostic starts with business records when every original and index is lost`(stage:String)=fixture {r->
        val call=if(stage in setOf("drill","improvement"))r.prepare("appoint",stage) else r.prepare(stage)
        call()
        assertEquals(EnumDeploymentCheckState.PASS,r.diagnostic().state)
        r.db {
            CollectionEmergencyFileAccesses.deleteAll()
            CollectionPermanentArchiveFiles.deleteAll()
            CollectionPermanentArchives.deleteAll()
        }
        val broken=r.state()
        // 旧检查的输入已空，不能据其 PASS 判断应急原件齐备。
        assertEquals(EnumDeploymentCheckState.PASS,r.db {CollectionFileIdentityUpgrade().inspect(listOf(CollectionFileIdentityUpgrade.EMERGENCY)).outcome().state})
        val result=r.diagnostic()
        assertEquals(EnumDeploymentCheckState.FAIL,result.state)
        assertTrue(result.safeFacts.any {it.name.startsWith("issueSample")})
        assertTrue(result.remediations.isNotEmpty())
        assertEquals(broken,r.state())
    }
    @ParameterizedTest @ValueSource(strings=["plan","appoint","handover","accept","drill","improvement"])
    fun `diagnostic finds retained archives after their business rows are removed`(stage:String)=fixture {r->
        val call=if(stage in setOf("drill","improvement"))r.prepare("appoint",stage) else r.prepare(stage)
        call()
        r.db {
            CollectionEmergencyDuties.deleteAll();CollectionEmergencyImprovements.deleteAll()
            CollectionEmergencyDrillRevisions.deleteAll();CollectionEmergencyDrills.deleteAll();CollectionEmergencyPlans.deleteAll()
        }
        val broken=r.state()
        val result=r.diagnostic()
        assertEquals(EnumDeploymentCheckState.FAIL,result.state)
        assertTrue(result.safeFacts.single {it.name=="unmatchedArchives"}.value.toLong()>0)
        assertEquals(broken,r.state())
    }
    @ParameterizedTest @ValueSource(strings=["header-org","header-biz","header-phase","snapshot","digest","missing-target"])
    fun `diagnostic rejects damaged archive headers and duty source with safe locators`(kind:String)=fixture {r->
        r.prepare("appoint")()
        assertEquals(EnumDeploymentCheckState.PASS,r.diagnostic().state)
        r.db {
            val archive=CollectionPermanentArchiveDao.find {CollectionPermanentArchives.phase eq "ISSUED"}.single()
            when(kind) {
                "header-org"->archive.orgId="other"
                "header-biz"->archive.bizId="unknown"
                "header-phase"->archive.phase="UNKNOWN"
                "snapshot"->archive.snapshot="private SECRET emergency original"
                "digest"->archive.digest="invalid"
                "missing-target"->{
                    CollectionPermanentArchiveFiles.deleteWhere {CollectionPermanentArchiveFiles.archiveId eq archive.id.value}
                    CollectionEmergencyFileAccesses.deleteWhere {CollectionEmergencyFileAccesses.archiveId eq archive.id.value}
                    archive.delete();CollectionEmergencyPlans.deleteAll()
                }
            }
        }
        val broken=r.state()
        SecurityContextHolder.clearContext() // 部署工作线程不借用馆员会话；入口仍只授权管理员。
        val result=r.diagnostic()
        assertEquals(EnumDeploymentCheckState.FAIL,result.state)
        val json=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(result)!!
        assertFalse(json.contains("SECRET"));assertFalse(json.contains("测试签署"));assertFalse(json.contains("proof.pdf"))
        assertEquals(broken,r.state())
    }

    @ParameterizedTest @MethodSource("signedDiagnosticPhases")
    fun `diagnostic validates signed plans original and revised reports and improvement review`(target:String,kind:String)=fixture {r->
        r.prepare("appoint","improvement")()
        val p=r.db {CollectionEmergencyPlanDao.all().single().id.value}
        val d=r.db {CollectionEmergencyDrillDao.all().single().id.value}
        val i=r.db {CollectionEmergencyImprovementDao.all().single().id.value}
        login("keeper");r.service.confirmPlan("museum",p,"KEEPER",signature)
        login("security");r.service.confirmPlan("museum",p,"SECURITY",signature)
        login("reader");r.service.confirmPlan("museum",p,"DIRECTOR",signature)
        login("security");r.service.confirmDrill("museum",d,"EVALUATOR",signature)
        login("reader");r.service.confirmDrill("museum",d,"DIRECTOR",signature)
        login("keeper")
        val report=r.db {CollectionEmergencyDrillDao[d].content.copy(title="据原件订正名称")}
        val revised=r.service.reviseDrill("museum",d,EmergencyDrillRevisionVo(d,report,"核实文字","proof"))
        login("security");r.service.confirmDrill("museum",d,revised,"EVALUATOR",signature)
        login("reader");r.service.confirmDrill("museum",d,revised,"DIRECTOR",signature)
        login("security");r.service.reviewImprovement("museum",d,revised,i,EmergencyImprovementReviewVo(true,"核实完成","proof"))
        val saved=r.state()
        assertEquals(EnumDeploymentCheckState.PASS,r.diagnostic().state)
        assertEquals(EnumDeploymentCheckState.PASS,r.db {CollectionEmergencyOriginalInspection.inspect(batchSize=1)}.state)
        assertEquals(saved,r.state())
        val parts=target.split('-')
        val id=when(parts.first()) {"plan"->p;"drill"->d;"revision"->revised;else->i}
        r.db {
            val archive=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq parts.last())}.single()
            CollectionPermanentArchiveFiles.deleteWhere {CollectionPermanentArchiveFiles.archiveId eq archive.id.value}
            if(kind=="all-originals") {
                CollectionEmergencyFileAccesses.deleteWhere {CollectionEmergencyFileAccesses.archiveId eq archive.id.value}
                archive.delete()
            }
        }
        val broken=r.state()
        assertEquals(EnumDeploymentCheckState.FAIL,r.diagnostic().state)
        assertEquals(broken,r.state())
    }
    @ParameterizedTest @ValueSource(strings=["plan","duty","archive-snapshot"])
    fun `malformed stored json produces a safe diagnostic rather than a readiness pass`(kind:String)=fixture {r->
        r.prepare("appoint")()
        r.db {when(kind) {
            "plan"->TransactionManager.current().exec("UPDATE col_emergency_plan SET content = '{'")
            "duty"->TransactionManager.current().exec("UPDATE col_emergency_duty SET appointment = '{'")
            else->CollectionPermanentArchiveDao.find {CollectionPermanentArchives.phase eq "ISSUED"}.single().apply {
                snapshot="{";digest=CollectionCareEvidence.digest(snapshot)
            }
        }}
        val result=r.diagnostic()
        assertEquals(EnumDeploymentCheckState.FAIL,result.state)
        assertTrue(result.safeFacts.any {it.name.startsWith("issueSample")})
        // 损坏的业务 JSON 不能通过 DAO 反序列化作快照，直接核对数据库中的原始文本。
        r.db {when(kind) {
            "plan"->assertEquals("{",TransactionManager.current().exec("SELECT content FROM col_emergency_plan") {rs->rs.next();rs.getString(1)})
            "duty"->assertEquals("{",TransactionManager.current().exec("SELECT appointment FROM col_emergency_duty") {rs->rs.next();rs.getString(1)})
            else->assertEquals("{",CollectionPermanentArchiveDao.find {CollectionPermanentArchives.phase eq "ISSUED"}.single().snapshot)
        }}
    }
    @Test fun `diagnostic is bounded across pages and never modifies orphan archives`()=fixture {r->
        r.db {repeat(213) {i->CollectionPermanentArchiveDao.new("orphan-%03d".format(i)) {
            orgId="museum";bizType=CollectionEmergencyEvidence.BIZ;bizId="missing-$i";phase="ISSUED";snapshot="secret-$i";digest="invalid"
        }}}
        val broken=r.state()
        val result=r.db {CollectionEmergencyOriginalInspection.inspect(batchSize=7)}
        assertEquals(EnumDeploymentCheckState.FAIL,result.state)
        assertEquals("213",result.safeFacts.single {it.name=="scannedArchives"}.value)
        assertEquals("213",result.safeFacts.single {it.name=="unmatchedArchives"}.value)
        assertEquals(10,result.safeFacts.count {it.name.startsWith("issueSample")})
        assertEquals(broken,r.state())
    }
    @Test fun `empty diagnostic states its acceptance boundary and respects cancellation`()=fixture {r->
        val before=r.state()
        val result=r.diagnostic()
        assertEquals(EnumDeploymentCheckState.PASS,result.state)
        assertTrue(result.summary.contains("零记录"))
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {CollectionEmergencyOriginalInspection.inspect()}
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {Thread.interrupted()}
        assertEquals(before,r.state())
    }
    @Test fun `current group movement changes multi museum page and search together`()=fixture("multi") {r->
        assertTrue(r.rows().all {it["orgId"]=="museum"});r.fault("moved")
        assertEquals(0,r.page("本馆").total);assertTrue(r.rows("外馆",1).all {it["orgId"]=="other"});assertTrue(r.page("外馆").total>0)
        assertThrows(BadRequestException::class.java){r.service.detail("museum")};assertEquals("other",r.service.detail("other")["orgId"])
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `explicit cross museum read never allows foreign writing`(mode:String)=fixture(mode) {r->
        r.db {actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))}
        if(mode=="single")assertThrows(BadRequestException::class.java){r.service.detail("other")} else assertEquals("other",r.service.detail("other")["orgId"])
        assertThrows(BadRequestException::class.java){r.service.registerPlan("other",plan())}
    }
    @Test fun `anonymous user is rejected before data is returned`()=fixture {r->
        SecurityContextHolder.clearContext();assertThrows(RuntimeException::class.java){r.page()};assertThrows(RuntimeException::class.java){r.service.detail("museum")}
    }
    @Test fun `every legacy and versioned business entry checks current permission before its payload`()=fixture {r->
        val drill=mock(CollectionEmergencyDrillVo::class.java)
        val revision=mock(EmergencyDrillRevisionVo::class.java)
        val improvement=mock(EmergencyImprovementVo::class.java)
        val review=mock(EmergencyImprovementReviewVo::class.java)
        val appointment=mock(EmergencyDutyAppointmentVo::class.java)
        r.fault("revoked");clearInvocations(r.archives)
        val calls=listOf<()->Any?>(
            {r.page()},{r.service.detail("museum")},{r.service.registerPlan("museum",plan())},
            {r.service.confirmPlan("museum","missing","KEEPER",signature)},
            {r.service.recordDrill("museum",drill)},{r.service.reviseDrill("museum","missing",revision)},
            {r.service.confirmDrill("museum","missing","EVALUATOR",signature)},
            {r.service.confirmDrill("museum","missing","version","EVALUATOR",signature)},
            {r.service.improve("museum","missing",improvement)},
            {r.service.improve("museum","missing","version",improvement)},
            {r.service.reviewImprovement("museum","missing","improvement",review)},
            {r.service.reviewImprovement("museum","missing","version","improvement",review)},
            {r.duties.appoint("museum",appointment)},{r.duties.sign("museum","missing","ACCEPT",signature)},
        )
        calls.forEachIndexed {index,call->
            val error=assertThrows(BadRequestException::class.java){call()}
            assertTrue(error.message.orEmpty().contains("当前账号没有"),"入口 $index 应先拒绝当前权限: ${error.message}")
        }
        verifyNoInteractions(r.archives,drill,revision,improvement,review,appointment)
    }
}
