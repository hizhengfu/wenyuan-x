package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.engine.*
import io.hizhengfu.cloud.flow.service.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/** 实际账号关系表、原业务表和 Spring 事务引擎。核验人员入口，不代替实物、材料及签署回调验收。 */
class CollectionNamedActorRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate, val staleCalls: AtomicInteger) {
        val definitions = c.getBean(FlowDefinitionService::class.java)
        val instances = c.getBean(FlowInstanceService::class.java)
        val tasks = c.getBean(FlowTaskService::class.java)
        fun <T> db(body: () -> T): T = tx.execute { body() }
    }
    private fun fixture(body: (Runtime) -> Unit) {
        val ds = SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("named.db")}" }
        val manager = SpringTransactionManager(ds, DatabaseConfig {})
        val stale = AtomicInteger()
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("scope", CollectionScopeResolver("multi", ""))
            c.beanFactory.registerSingleton("loader", FlowGraphLoader(16, 5))
            c.beanFactory.registerSingleton("registry", FlowBusinessTypeRegistry())
            c.beanFactory.registerSingleton("resolver", FlowAssigneeResolver(listOf(object : AssigneeProvider {
                override fun getUserName(userId: String): String { stale.incrementAndGet(); return "过期姓名" }
            })))
            c.register(Transactions::class.java, CollectionActorDirectory::class.java, CollectionResponsibleUserService::class.java,
                CollectionNamedFlowActorPolicy::class.java, CollectionRoleFlowActorPolicy::class.java,
                CollectionFlowDefinitionConstraint::class.java, FlowDefinitionService::class.java, FlowEngine::class.java,
                FlowInstanceService::class.java, FlowTaskService::class.java)
            c.refresh()
            val r = Runtime(c, TransactionTemplate(manager), stale)
            r.db {
                actorSchema()
                SchemaUtils.create(CollectionOrgs, CollectionChanges, CollectionTransfers, Appraisals, AppraisalMembers, Requisitions, Deregistrations, Loans, CollectionReturns, WarehouseEntryApplications, Reproductions, ReproductionPlans, DigitalAcquisitionPlans, CollectionSamplings, CollectionSamplingPlans,
                    FlowDefinitions, FlowNodeDefs, FlowEdgeDefs, FlowInstances, FlowTasks, FlowInstanceLogs, FlowCCs)
                listOf("a", "b").forEach { id ->
                    CollectionOrgDao.new(id) { code = id; name = id; systemGroupId = "group-$id" }
                    GroupDao.new("group-$id") { name = id; type = '1'; parentId = "0"; parentIds = "0," }
                }
                listOf("dept-a", "other-dept").forEach { id -> GroupDao.new(id) { name=id; type='2'; parentId="group-a"; parentIds="0,group-a," } }
                listOf("keeper", "leader", "other", "extra", "admin", "department", "information", "conservation").forEach { id -> person(id, "dept-a") }
                person("foreign", "group-b"); person("target-keeper", "group-b"); person("target-leader", "group-b")
                listOf("keeper", "other", "foreign", "target-keeper").forEach { actorRole(it, CollectionConst.ROLE_STORAGE_HEAD) }
                listOf("leader", "target-leader").forEach { actorRole(it, CollectionConst.ROLE_DIRECTOR) }
                actorRole("department", CollectionConst.ROLE_DEPARTMENT_HEAD)
                actorRole("information", CollectionConst.ROLE_INFORMATION); actorRole("conservation", CollectionConst.ROLE_CONSERVATION)
                actorRole("admin", "ACTUAL_ADMIN", setOf("ADMIN", "FLOW_TASK_ADMIN_COMPLETE"))
                CollectionChangeDao.new("record") {
                    orgId="a"; collectionId="object"; reason="待核对订正"; before=CollectionCatalogContentVo(); content=CollectionCatalogContentVo(); baseline=CollectionCatalogContentVo()
                    keeperUserId="keeper"; leaderUserId="leader"
                }
            }
            body(r)
        }
    }
    private fun person(id: String, group: String) = UserDao.new(id) { name = "实际$id"; loginName=id; password="not-read"; enabled=true; groupId=group }
    private fun publish(r: Runtime, key: String = CollectionConst.FLOW_CHANGE, extra: String? = null, extraPerson: String = "extra") {
        val model = javaClass.classLoader.getResourceAsStream("flow/$key.json")!!.use { JsonUtils.mapper.readTree(it) }.path("model")
            .let { JsonUtils.mapper.treeToValue(it, FlowModel::class.java) }
        var next = model.copy(nodes = model.nodes.map { n ->
            if(n.properties.nodeType=="APPROVE") n.copy(properties=n.properties.copy(actions=listOf("APPROVE","REJECT","SAVE","TRANSFER","DELEGATE"))) else n
        })
        if(extra!=null) {
            val end = model.nodes.single { it.properties.nodeType=="END" }.id
            next=next.copy(nodes=next.nodes+FlowModelNode("extra", "bpmn:userTask", properties=FlowNodeProperties(nodeKey="extra",name="馆方复核",nodeType="APPROVE",assigneeType=extra,
                assignees=if(extra=="FIXED_USER") listOf(extraPerson) else emptyList(), actions=listOf("APPROVE","REJECT","SAVE","TRANSFER","DELEGATE"),fieldPerms=listOf(FieldPermItem(field="*",perm="READONLY")))),
                edges=next.edges.map { if(it.targetNodeId==end)it.copy(targetNodeId="extra") else it }+FlowModelEdge("extra-end","extra",end))
        }
        val def=r.definitions.create(FlowDefinitionVo(key=key,name="当前本人资格核对",modelData=JsonUtils.toJson(next)),"author")
        r.definitions.publish(def.id.value,"publisher")
    }
    private fun start(r:Runtime, key:String=CollectionConst.FLOW_CHANGE, business:String=CollectionConst.BIZ_CHANGE,
                      selected:Map<String,List<String>> = mapOf("keeper" to listOf("keeper"),"leader" to listOf("leader"),"extra" to listOf("extra"))) =
        r.instances.start(StartFlowRequest(definitionKey=key,title="核验",businessType=business,businessKey="record",formData=mapOf("orgId" to "b") + (if(key==CollectionConst.FLOW_WAREHOUSE_ENTRY) mapOf("requiresDirector" to true) else emptyMap()),selfSelectAssignees=selected),"admin","发起人").id.value
    private fun task(r:Runtime,id:String)=r.db { FlowTaskDao.find { (FlowTasks.instanceId eq id) and (FlowTasks.status eq "PENDING") }.single().id.value }
    private fun complete(r:Runtime,id:String,user:String="keeper",action:String="APPROVE",target:String?=null,admin:Boolean=false)=
        r.tasks.complete(CompleteTaskRequest(taskId=task(r,id),action=action,comment="本人意见",transferTo=target,transferToName=target),user,user,canAdminComplete=admin)
    private fun snapshot(r:Runtime)=r.db { listOf(FlowInstances,FlowTasks,FlowInstanceLogs).map { table -> table.selectAll().map { row -> table.columns.map { row[it] } } } }

    @ParameterizedTest @ValueSource(strings=["APPROVE","REJECT","SAVE"])
    fun `named actions recheck original officer after action and roll back task logs`(action:String)=fixture {r ->
        publish(r);val id=start(r);val before=snapshot(r);val engine=r.c.getBean(FlowEngine::class.java)
        var reached=false;val logs=r.db {FlowInstanceLogDao.count()}
        engine.actorPolicies=listOf(object:FlowActorPolicy {
            override fun taskVerifier(context:FlowActorContext,node:FlowNodeSnapshot,assignee:String?,operatorId:String,action:String,transferTo:String?):()->Unit = {
                reached=true;assertTrue(FlowInstanceLogDao.count()>logs)
            }
        })+engine.actorPolicies
        engine.formPolicies=engine.formPolicies+object:FlowFormPolicy {
            override fun protectedFields(context:FlowActorContext):Set<String> {UserDao["keeper"].enabled=false;return emptySet()}
        }
        assertThrows(BadRequestException::class.java){complete(r,id,action=action)}
        assertTrue(reached);assertEquals(before,snapshot(r))
    }

    @ParameterizedTest @ValueSource(strings=["museum","responsibility"])
    fun `named task cannot replace original museum or named responsibility during save`(change:String)=fixture {r ->
        publish(r);val id=start(r);val before=snapshot(r);val engine=r.c.getBean(FlowEngine::class.java)
        engine.formPolicies=engine.formPolicies+object:FlowFormPolicy {
            override fun protectedFields(context:FlowActorContext):Set<String> {
                if(change=="museum")CollectionChangeDao["record"].orgId="b" else CollectionChangeDao["record"].keeperUserId="other"
                return emptySet()
            }
        }
        val failure=assertThrows(BadRequestException::class.java){complete(r,id,action="SAVE")}
        assertTrue(failure.message!!.contains("责任依据"));assertEquals(before,snapshot(r))
    }

    @ParameterizedTest @ValueSource(strings=["TRANSFER","DELEGATE"])
    fun `optional named node recipient loses qualification after transfer and rolls back`(action:String)=fixture {r ->
        publish(r,extra="FIXED_USER");val id=start(r);complete(r,id);complete(r,id,user="leader")
        val before=snapshot(r);var changed=false
        r.c.addApplicationListener(org.springframework.context.ApplicationListener<org.springframework.context.ApplicationEvent> {event ->
            val payload=(event as? org.springframework.context.PayloadApplicationEvent<*>)?.payload
            if(payload is FlowTaskCompletedEvent && payload.instanceId==id) {changed=true;UserDao["other"].enabled=false}
        })
        assertThrows(BadRequestException::class.java){complete(r,id,user="extra",action=action,target="other")}
        assertTrue(changed);assertEquals(before,snapshot(r))
    }

    @Test fun `named task detail filters direct transfer and current lost qualification without changing original evidence`()=fixture {r->
        publish(r);val id=start(r);val task=task(r,id);val before=snapshot(r)
        val detail=r.tasks.getTaskDetail(task,"keeper")!!
        assertEquals(setOf("APPROVE","REJECT","SAVE"),detail.availableActions!!.toSet())
        assertEquals(setOf("TRANSFER","DELEGATE"),detail.actionRestrictions!!.keys)
        val admin=r.tasks.getTaskDetail(task,"admin",isAdmin=true,canAdminComplete=true)!!
        assertEquals(emptyList<String>(),admin.availableActions);assertEquals(before,snapshot(r))
        r.db {Users.update({Users.id eq "keeper"}){it[enabled]=false}}
        val changed=snapshot(r);val denied=r.tasks.getTaskDetail(task,"keeper")!!
        assertEquals(emptyList<String>(),denied.availableActions);assertEquals(5,denied.actionRestrictions!!.size);assertEquals(changed,snapshot(r))
        r.db {Users.update({Users.id eq "keeper"}){it[enabled]=true}}
        assertEquals(detail.availableActions,r.tasks.getTaskDetail(task,"keeper")!!.availableActions)
        complete(r,id);complete(r,id,"leader");assertEquals("COMPLETED",r.db {FlowInstanceDao[id].status})
        assertEquals(emptyList<String>(),r.tasks.getTaskDetail(task,"keeper")!!.availableActions)
    }

    @Test fun `actual selected actors are dispatched from original record and active directory`()=fixture { r ->
        publish(r);val id=start(r)
        assertEquals("实际keeper",r.db { FlowTaskDao[task(r,id)].assigneeName })
        complete(r,id);complete(r,id,"leader")
        assertEquals("COMPLETED",r.db { FlowInstanceDao[id].status });assertEquals(0,r.staleCalls.get())
    }

    @ParameterizedTest @ValueSource(strings=["disabled","deleted","locked","account-expired","credentials-expired","expired-time","role-removed","role-disabled","moved","group-disabled","museum-disabled"])
    fun `post dispatch qualification changes reject before any task or history mutation`(change:String)=fixture { r ->
        publish(r);val id=start(r)
        r.db {
            when(change) {
                "disabled"->Users.update({Users.id eq "keeper"}){it[enabled]=false}
                "deleted"->Users.update({Users.id eq "keeper"}){it[status]=EnumContentStatus.DELETE}
                "locked"->Users.update({Users.id eq "keeper"}){it[accountNonLocked]=false}
                "account-expired"->Users.update({Users.id eq "keeper"}){it[accountNonExpired]=false}
                "credentials-expired"->Users.update({Users.id eq "keeper"}){it[credentialsNonExpired]=false}
                "expired-time"->Users.update({Users.id eq "keeper"}){it[expireTime]=LocalDateTime.now().minusSeconds(1)}
                "role-removed"->RoleUsers.deleteWhere { userId eq "keeper" }
                "role-disabled"->Roles.update({Roles.code eq CollectionConst.ROLE_STORAGE_HEAD}){it[enabled]=false}
                "moved"->Users.update({Users.id eq "keeper"}){it[groupId]="group-b"}
                "group-disabled"->Groups.update({Groups.id eq "dept-a"}){it[enabled]=false}
                "museum-disabled"->CollectionOrgs.update({CollectionOrgs.id eq "a"}){it[orgStatus]=EnumOrgStatus.DISABLED}
            }
        }
        val before=snapshot(r)
        listOf("APPROVE","REJECT","SAVE","TRANSFER","DELEGATE").forEach { action ->
            assertThrows(BadRequestException::class.java) { complete(r,id,action=action,target="other") }
            assertEquals(before,snapshot(r))
        }
    }

    @ParameterizedTest @ValueSource(strings=["APPROVE","REJECT","SAVE","TRANSFER","DELEGATE"])
    fun `administrator cannot replace a named business responsibility`(action:String)=fixture { r ->
        publish(r);val id=start(r);val before=snapshot(r)
        assertThrows(BadRequestException::class.java){complete(r,id,"admin",action,"other",true)}
        assertEquals(before,snapshot(r))
    }

    @ParameterizedTest @ValueSource(strings=["TRANSFER","DELEGATE"])
    fun `even a qualified peer cannot replace the original signed responsibility through task transfer`(action:String)=fixture { r ->
        publish(r);val id=start(r);val before=snapshot(r)
        assertThrows(BadRequestException::class.java){complete(r,id,action=action,target="other")};assertEquals(before,snapshot(r))
    }

    @ParameterizedTest @ValueSource(strings=["different-person","unavailable-later-person"])
    fun `start rejects original selection mismatch or unavailable later actor without creating an instance`(kind:String)=fixture { r ->
        publish(r)
        val selected=mutableMapOf("keeper" to listOf("keeper"),"leader" to listOf("leader"))
        if(kind=="different-person")selected["keeper"]=listOf("other") else r.db { Users.update({Users.id eq "leader"}){it[enabled]=false} }
        assertThrows(BadRequestException::class.java){start(r,selected=selected)}
        assertEquals(0L,r.db { FlowInstanceDao.all().count() });assertEquals(0L,r.db { FlowTaskDao.all().count() })
    }

    @Test fun `original responsibility changes cannot be hidden by old instance selections`()=fixture { r ->
        publish(r);val id=start(r)
        r.db { CollectionChanges.update({CollectionChanges.id eq "record"}){it[keeperUserId]="other"} }
        val before=snapshot(r);assertThrows(BadRequestException::class.java){complete(r,id)};assertEquals(before,snapshot(r))
    }

    @ParameterizedTest @ValueSource(strings=["FIXED_USER","SELF_SELECT"])
    fun `additional named nodes honor active museum membership and configured transfer`(kind:String)=fixture { r ->
        publish(r,extra=kind);val id=start(r);complete(r,id);complete(r,id,"leader")
        val before=snapshot(r)
        assertThrows(BadRequestException::class.java){complete(r,id,"extra","TRANSFER","foreign")};assertEquals(before,snapshot(r))
        complete(r,id,"extra","TRANSFER","other");complete(r,id,"other")
        assertEquals("COMPLETED",r.db { FlowInstanceDao[id].status })
    }

    @ParameterizedTest @ValueSource(strings=["FIXED_USER","SELF_SELECT"])
    fun `additional foreign selections are rejected before start despite form museum`(kind:String)=fixture { r ->
        publish(r,extra=kind,extraPerson="foreign")
        assertThrows(BadRequestException::class.java){start(r,selected=mapOf("keeper" to listOf("keeper"),"leader" to listOf("leader"),"extra" to listOf("foreign")))}
        assertEquals(0L,r.db { FlowInstanceDao.all().count() })
    }

    @Test fun `transfer approval resolves source and target responsibilities from their own museums`()=fixture { r ->
        r.db { CollectionTransferDao.new("record") {
            orgId="a";fromOrgId="a";toOrgId="b";collectionId="object"
            dossier=CollectionTransferDossierVo(EnumCollectionTransferKind.OTHER_LAWFUL,"keeper","leader","target-keeper","target-leader","warehouse","archive","paper-a","paper-b")
        } }
        publish(r,CollectionConst.FLOW_TRANSFER)
        val selected=mapOf("source_keeper" to listOf("keeper"),"source_leader" to listOf("leader"),"target_keeper" to listOf("target-keeper"),"target_leader" to listOf("target-leader"))
        val id=start(r,CollectionConst.FLOW_TRANSFER,CollectionConst.BIZ_TRANSFER,selected)
        listOf("keeper","leader","target-keeper","target-leader").forEach { complete(r,id,it) }
        assertEquals("COMPLETED",r.db { FlowInstanceDao[id].status })
    }

    @Test fun `requisition department responsibility is rechecked after movement within the same museum`()=fixture { r ->
        r.db { RequisitionDao.new("record") {
            orgId="a"
            dossier=RequisitionDossierVo("dept-a","department","keeper","leader","department","研究","研究","室内","安全","file",LocalDateTime.now(),LocalDateTime.now().plusDays(1),transportRequired=false,transportDecisionReason="本馆室内原位提用")
        } }
        publish(r,CollectionConst.FLOW_REQUISITION)
        val id=start(r,CollectionConst.FLOW_REQUISITION,CollectionConst.BIZ_REQUISITION,mapOf("department" to listOf("department"),"keeper" to listOf("keeper"),"leader" to listOf("leader")))
        r.db { Users.update({Users.id eq "department"}){it[groupId]="other-dept"} }
        val before=snapshot(r);assertThrows(BadRequestException::class.java){complete(r,id,"department","REJECT")};assertEquals(before,snapshot(r))
    }

    @Test fun `registered external appraisal members may act while unregistered replacements cannot`()=fixture { r ->
        r.db {
            AppraisalDao.new("record") {orgId="a";collectionId="object"}
            listOf("keeper","foreign").forEachIndexed { i,user -> AppraisalMemberDao.new {orgId="a";appraisalId="record";userId=user;memberName=user;sort=i} }
        }
        publish(r,CollectionConst.FLOW_APPRAISAL)
        assertThrows(BadRequestException::class.java) { start(r,CollectionConst.FLOW_APPRAISAL,CollectionConst.BIZ_APPRAISAL,mapOf("committee" to listOf("keeper","extra"))) }
        val id=start(r,CollectionConst.FLOW_APPRAISAL,CollectionConst.BIZ_APPRAISAL,mapOf("committee" to listOf("keeper","foreign")))
        val ids=r.db { FlowTaskDao.find {FlowTasks.instanceId eq id}.associate {it.assignee!! to it.id.value} }
        listOf("keeper","foreign").forEach { user -> r.tasks.complete(CompleteTaskRequest(taskId=ids.getValue(user),action="APPROVE",comment="独立意见"),user,user) }
        assertEquals("COMPLETED",r.db {FlowInstanceDao[id].status})
    }
    @ParameterizedTest @ValueSource(strings=["exit","loan","return","warehouse","sampling","digital","reproduction"])
    fun `remaining statutory templates resolve named actors from their actual original records`(kind:String)=fixture {r->
        val at=LocalDateTime.of(2026,9,1,12,0)
        val (key,biz,people)=r.db {
            when(kind) {
                "exit" -> {
                    DeregistrationDao.new("record"){orgId="a";collectionId="object";dossier=DeregistrationDossierVo(keeperUserId="keeper",leaderUserId="leader")}
                    Triple(CollectionConst.FLOW_DEREGISTER,CollectionConst.BIZ_DEREGISTER,linkedMapOf("keeper" to "keeper","leader" to "leader"))
                }
                "loan" -> {
                    LoanDao.new("record"){orgId="a";dossier=LoanDossierVo(borrowerKind=EnumLoanBorrowerKind.STATE_MUSEUM,borrowerRegistrationNo="registration",borrowerIdentityFileIds="file",purpose=EnumLoanPurpose.RESEARCH,purposeDescription="研究",keeperUserId="keeper",leaderUserId="leader",supervisionUserId="other",
                        agreedOutAt=at,agreedReturnAt=at.plusDays(1),collectiveDecisionAt=at,collectiveDecisionFileIds="file",agreementSignedAt=at,signedAgreementFileIds="file",agreedCollectionListFileIds="file",safetyResponsibilities="责任",transportRequirements="运输",storageRequirements="保管",useSite="场所",carrierName="运输单位",carrierCapabilityFileIds="file",transportSupervisorName="监督人",transportSupervisorOrganization="单位",transportAssignmentFileIds="file",firstInspectionOn=at.toLocalDate(),inspectionIntervalDays=1,inspectionPlanFileIds="file")}
                    Triple(CollectionConst.FLOW_LOAN,CollectionConst.BIZ_LOAN,linkedMapOf("keeper" to "keeper","leader" to "leader"))
                }
                "return" -> {
                    CollectionReturnDao.new("record"){orgId="a";sourceBizType="COLLECTION_LOAN";sourceBizId="loan";collectionIds="object";keeperUserId="keeper"}
                    Triple(CollectionConst.FLOW_RETURN,CollectionConst.BIZ_RETURN,linkedMapOf("keeper" to "keeper"))
                }
                "warehouse" -> {
                    WarehouseEntryApplicationDao.new("record"){orgId="a";applicationNo="apply";applicantId="admin";entrantIds="other";warehouseIds="warehouse";purpose="入库";plannedStart=at;plannedEnd=at.plusHours(1)
                        dossier=WarehouseEntryDossierVo(kind=EnumWarehouseEntryKind.STAFF_SHORTAGE,keeperUserId="keeper",leaderUserId="leader",identityFileIds="file")}
                    Triple(CollectionConst.FLOW_WAREHOUSE_ENTRY,CollectionConst.BIZ_WAREHOUSE_ENTRY,linkedMapOf("keeper" to "keeper","leader" to "leader"))
                }
                "sampling" -> {
                    CollectionSamplingDao.new("record"){orgId="a";collectionId="object";title="取样"}
                    CollectionSamplingPlanDao.new("plan"){orgId="a";samplingId="record";version=1;sourceSnapshot="original";authority=EnumAuthorityLevel.NATIONAL
                        content=SamplingPlanVo("revision",at,"dept-a","conservation","keeper",EnumSamplingPurpose.RESEARCH,"理由","无损评估","file","替代调查","file",listOf(SamplingAlternativeVo("替代物",true,true,true,false,"理由","file")),true,"必要性",at,at.plusHours(1),"场所",listOf(SamplingPointVo("point","部位","file","微量","方法","最小量",java.math.BigDecimal.ONE,EnumSamplingUnit.MG,1,"防护")),"分析单位","方法",true,"剩余样品","防护","file","signed")}
                    Triple(CollectionConst.FLOW_SAMPLING,CollectionConst.BIZ_SAMPLING,linkedMapOf("conservation" to "conservation","keeper" to "keeper"))
                }
                "digital" -> {
                    ReproductionDao.new("record"){orgId="a";collectionId="object";repType=EnumRepType.SCAN_3D}
                    DigitalAcquisitionPlanDao.new("plan"){orgId="a";reproductionId="record";version=1;sourceSnapshot="original"
                        content=DigitalAcquisitionPlanVo("revision",at,"dept-a","information","keeper","扫描","场所",at,at.plusHours(1),"环境控制","搬运","加工","整理","备份","安全","权利","file","file","file","file","signed")}
                    Triple(CollectionConst.FLOW_DIGITAL_ACQUISITION_2026,CollectionConst.BIZ_REPRODUCTION,linkedMapOf("information" to "information","keeper" to "keeper"))
                }
                else -> {
                    ReproductionDao.new("record"){orgId="a";collectionId="object";repType=EnumRepType.COPY}
                    ReproductionPlanDao.new("plan"){orgId="a";reproductionId="record";version=1;sourceSnapshot="original"
                        content=ReproductionPlanVo("revision",at,"keeper","leader",null,"制作单位",null,null,null,1,true,false,null,true,"无损方法",true,"file","file","限量","场所","标准",7,"file","file","signed","保密","file")}
                    Triple(CollectionConst.FLOW_REPRODUCTION_2026,CollectionConst.BIZ_REPRODUCTION,linkedMapOf("keeper" to "keeper","leader" to "leader"))
                }
            }
        }
        publish(r,key)
        val planTable=when(kind){"sampling"->CollectionSamplingPlans;"digital"->DigitalAcquisitionPlans;"reproduction"->ReproductionPlans;else->null}
        if(planTable!=null) {
            r.db {planTable.update({planTable.id eq "plan"}){it[planTable.orgId]="b"}}
            assertThrows(BadRequestException::class.java){start(r,key,biz,people.mapValues {listOf(it.value)})}
            assertEquals(0L,r.db {FlowInstanceDao.all().count()})
            r.db {planTable.update({planTable.id eq "plan"}){it[planTable.orgId]="a"}}
        }
        val id=start(r,key,biz,people.mapValues {listOf(it.value)})
        people.values.forEach {user->complete(r,id,user)}
        assertEquals("COMPLETED",r.db {FlowInstanceDao[id].status})
        assertEquals(0,r.staleCalls.get())
    }

}
