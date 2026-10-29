package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.*
import org.springframework.aop.framework.ProxyFactory
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import org.postgresql.ds.PGSimpleDataSource
import java.math.BigDecimal
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/** Real current directory, Spring transaction, actual SQL mutation and database lock. */
class SamplingWriterRuntimeTest {
    @TempDir lateinit var dir:Path
    private val at=LocalDateTime.now().minusHours(8).withNano(0)
    private val tables=arrayOf(CollectionOrgs,Collections,CollectionSamplings,CollectionSamplingPlans,CollectionSamples,
        CollectionAdministrativeRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionOperationLocks,
        Files,Users,Groups,Roles,RoleUsers,RolePermissions,Permissions,UserGroups,FlowInstances,FlowInstanceLogs)
    private class Runtime(val tx:TransactionTemplate,val service:CollectionSamplingService,val scope:CollectionScopeResolver,
        val binder:BusinessAttachmentBinder,val postgres:Boolean,val source:DataSource) {
        lateinit var plan:SamplingPlanVo
        lateinit var planId:String
        lateinit var start:SamplingStartVo
        lateinit var take:SamplingTakeVo
        lateinit var sampleId:String
        lateinit var end:SamplingExtractionEndVo
        lateinit var analysis:SamplingAnalysisVo
        lateinit var completion:SamplingCompletionVo
        fun <T> db(block:()->T):T=tx.execute {block()}
        fun login(id:String) {
            val user=db {CollectionActorDirectory(scope).requireActive(id)}
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        }
    }
    private fun fixture(actualParticipants:Boolean=false,block:(Runtime)->Unit) {
        val config=System.getProperty("collection.test.pg.config")
        val schema="sampling_writer_"+UUID.randomUUID().toString().replace("-","")
        val postgres=config?.let {path->
            val p=Properties().apply {Path.of(path).toFile().inputStream().use(::load)}
            PGSimpleDataSource().apply {setURL(p.getProperty("url"));user=p.getProperty("user");password=p.getProperty("password")}.also {source->
                source.connection.use {it.createStatement().use {s->s.execute("CREATE SCHEMA $schema")}}
                source.currentSchema=schema
            }
        }
        val source:DataSource=postgres ?: SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("writer.db")}"}
        val manager=SpringTransactionManager(source,DatabaseConfig {})
        val scope=CollectionScopeResolver("single","museum")
        val binder=mock(BusinessAttachmentBinder::class.java).also {b->`when`(b.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}}
        val users=if(actualParticipants)CollectionResponsibleUserService(CollectionActorDirectory(scope),scope) else mock(CollectionResponsibleUserService::class.java)
        val flow=mock(FlowInstanceService::class.java)
        `when`(flow.startBusiness(any() ?: StartFlowRequest("unused","unused"),anyString(),anyString(),any<(FlowInstanceDao)->Unit>() ?: {})).thenAnswer {c->
            val request=c.getArgument<StartFlowRequest>(0)
            FlowInstanceDao.new {definitionId="definition";definitionKey=request.definitionKey;definitionName="合成共同方案";title=request.title;initiator="operator";initiatorName="经办"}
                .also {c.getArgument<(FlowInstanceDao)->Unit>(3)(it)}
        }
        val original=CollectionSamplingService(flow,scope,users,CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider()),
            mock(CollectionOriginalAccessService::class.java),CollectionOperationLockService(),directory=CollectionActorDirectory(scope))
        val factory=ProxyFactory(original).apply {isProxyTargetClass=true;addAdvice(TransactionInterceptor().apply {transactionManager=manager;transactionAttributeSource=AnnotationTransactionAttributeSource()})}
        try {StaticApplicationContext().use {context->
            context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
            val r=Runtime(TransactionTemplate(manager),factory.proxy as CollectionSamplingService,scope,binder,postgres!=null,source)
            r.db {
                SchemaUtils.create(CollectionOrgs,Collections,CollectionSamplings,CollectionSamplingPlans,CollectionSamples,
                    CollectionAdministrativeRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionOperationLocks,Files,FlowInstances,FlowInstanceLogs)
                originalReaderSchema();originalReader("operator",CollectionConst.SAMPLING);originalReader("other-operator",CollectionConst.SAMPLING)
                if(actualParticipants) {
                    GroupDao.new("department"){name="合成保护修复部门";type='1';parentId="museum";parentIds="0,museum,"}
                    originalReader("conservation",CollectionConst.SAMPLING,"department")
                    actorRole("conservation",CollectionConst.ROLE_CONSERVATION)
                    originalReader("keeper",CollectionConst.SAMPLING)
                    actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD)
                    originalReader("custodian",CollectionConst.SAMPLING)
                    originalReader("recipient",CollectionConst.SAMPLING)
                }
                CollectionOrgDao.new("museum"){name="本馆";code="M";systemGroupId="museum"}
                CollectionOrgDao.new("other"){name="其他馆";code="O";systemGroupId="other"}
                for(id in listOf("piece","other-piece"))CollectionDao.new(id){orgId="museum";name=id;totalRegNo=id;registeredAt=at.minusMonths(1)
                    catalogStatus=EnumCatalogStatus.APPROVED;nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.FIRST;lifeStatus=EnumCollectionStatus.IN_STORE}
                CollectionSamplingDao.new("sampling"){orgId="museum";collectionId="piece";title="合成特殊研究取样";createBy="operator"}
                FileDao.new("proof"){name="合成签署原件.pdf";path="proof";hash="proof";size=100;ext="pdf"}
            }
            r.login("operator")
            try {block(r)} finally {SecurityContextHolder.clearContext()}
        }} finally {postgres?.connection?.use {it.createStatement().use {s->s.execute("DROP SCHEMA $schema CASCADE")}}}
    }
    private fun prepare(r:Runtime,kind:String)=r.db {
        if(kind=="create")return@db
        r.plan=SamplingPlanVo(r.service.detail("sampling")["sourceRevision"] as String,at,"department","conservation","keeper",EnumSamplingPurpose.RESEARCH,
            "合成特殊研究问题","合成非有损方法评估","proof","合成替代物调查","proof",
            listOf(SamplingAlternativeVo("合成候选物",true,true,true,false,"合成不能替代理由","proof")),true,"合成必要性",
            at.plusMinutes(5),at.plusHours(1),"合成实验室",listOf(SamplingPointVo("point","合成剥落处","proof","微量材料","合成方法","最小必要范围",BigDecimal("2.50"),EnumSamplingUnit.MG,2,"合成防护")),
            "合成分析室","合成分析技术",true,"合成剩余保管","合成安全养护","proof","proof")
        if(kind=="plan")return@db
        r.planId=r.service.savePlan("sampling",r.plan)
        if(kind in setOf("administrative","submit"))return@db
        r.service.submit("sampling");val parent=CollectionSamplingDao["sampling"]
        listOf("conservation","keeper").forEachIndexed {i,node->FlowInstanceLogDao.new {instanceId=parent.flowInstanceId!!;nodeKey=node;taskId="task-$node";action="APPROVE";operator=node;comment="合成本人确认";createTime=at.plusMinutes(i+1L)}}
        if(kind=="approved")return@db
        r.service.onFlowCompleted(parent,FlowCompletedEvent(parent.flowInstanceId!!,CollectionConst.FLOW_SAMPLING,"合成取样","sampling",CollectionConst.BIZ_SAMPLING,emptyMap(),"operator","经办"))
        r.service.administrative("sampling",r.planId,permit())
        r.start=SamplingStartVo(r.planId,at.plusMinutes(10),"operator","req",null,true,true,"合成安全核对","proof","proof")
        if(kind=="start")return@db
        r.service.start("sampling",r.start)
        r.take=SamplingTakeVo("S1","point",at.plusMinutes(20),"operator","合成剥落处",BigDecimal("1.25"),EnumSamplingUnit.MG,true,false,"合成无额外损伤","合成养护","合成样品柜","custodian","proof","proof","proof")
        if(kind=="take")return@db
        r.sampleId=r.service.take("sampling",r.take)
        r.end=SamplingExtractionEndVo(at.plusMinutes(40),"conservation","keeper",setOf(r.sampleId),"合成现状","合成养护",true,"proof","proof")
        if(kind=="end")return@db
        r.service.endExtraction("sampling",r.end)
        r.analysis=SamplingAnalysisVo(at.plusHours(1),"合成分析室","operator","合成分析报告",BigDecimal("0.25"),BigDecimal("1.00"),"合成剩余柜","custodian","proof","proof")
        if(kind=="analysis")return@db
        r.service.analysis("sampling",r.sampleId,r.analysis)
        r.completion=SamplingCompletionVo(at.plusHours(2),"conservation","keeper",setOf(r.sampleId),"合成共同核查","合成档案索引","proof")
    }
    private fun permit()=CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.NATIONAL,action=EnumAdministrativeAction.APPROVAL,authorityName="合成机关",documentNo="合成文号",issuedAt=at.toLocalDate(),fileIds="proof")
    private fun operation(r:Runtime,kind:String) {
        when(kind) {
            "create"->r.service.create("piece","新建合成取样")
            "plan"->r.service.savePlan("sampling",r.plan)
            "submit"->r.service.submit("sampling")
            "administrative"->r.service.administrative("sampling",r.planId,permit())
            "start"->r.service.start("sampling",r.start)
            "take"->r.service.take("sampling",r.take)
            "end"->r.service.endExtraction("sampling",r.end)
            "analysis"->r.service.analysis("sampling",r.sampleId,r.analysis)
            "complete"->r.service.complete("sampling",r.completion)
            "approved"->r.db {r.service.onFlowCompleted(CollectionSamplingDao["sampling"],FlowCompletedEvent(CollectionSamplingDao["sampling"].flowInstanceId!!,CollectionConst.FLOW_SAMPLING,"合成取样","sampling",CollectionConst.BIZ_SAMPLING,emptyMap(),"operator","经办"))}
            "handover"->r.service.handoverSample("sampling",r.sampleId,handover(r))
            else->error(kind)
        }
    }
    private fun trigger(r:Runtime,kind:String,statement:String)=r.db {
        val condition=when(kind){"create"->"NEW.title='新建合成取样'";"plan"->"NEW.phase='PLAN'";"submit"->"NEW.phase='SUBMITTED'";"administrative"->"NEW.phase LIKE 'ADMIN_%'";"start"->"NEW.phase='STARTED'";"take"->"NEW.phase LIKE 'SAMPLE_%'";"end"->"NEW.phase='EXTRACTION_ENDED'";"analysis"->"NEW.phase LIKE 'ANALYSIS_%'";"approved"->"NEW.phase LIKE 'APPROVED_%'";"handover"->"NEW.phase LIKE '${SamplingSampleJournal.prefix(r.sampleId)}%'";else->"NEW.phase='COMPLETED'"}
        val table=if(kind=="create")CollectionSamplings.tableName else CollectionPermanentArchives.tableName
        val predicate=if(kind=="create")condition else "NEW.biz_type='${CollectionConst.BIZ_SAMPLING}' AND $condition"
        if(r.postgres) {
            TransactionManager.current().exec("CREATE FUNCTION mutate_sampling_writer() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF $predicate THEN $statement; END IF; RETURN NEW; END $$")
            TransactionManager.current().exec("CREATE TRIGGER mutate_sampling_writer AFTER INSERT ON $table FOR EACH ROW EXECUTE FUNCTION mutate_sampling_writer()")
        } else TransactionManager.current().exec("CREATE TRIGGER mutate_sampling_writer AFTER INSERT ON $table WHEN $predicate BEGIN $statement; END")
    }
    private fun dropTrigger(r:Runtime,kind:String="plan")=r.db {
        val table=if(kind=="create")CollectionSamplings.tableName else CollectionPermanentArchives.tableName
        TransactionManager.current().exec("DROP TRIGGER mutate_sampling_writer"+(if(r.postgres)" ON $table" else ""))
        if(r.postgres)TransactionManager.current().exec("DROP FUNCTION mutate_sampling_writer()")
    }
    private fun snapshot(r:Runtime)=r.db {settledSafetySelectionState(*tables)}
    private fun sourceMutation(change:String)=when(change) {
        "name"->"name='实际变更后的原件名称'"
        "level"->"level=${EnumRelicLevel.SECOND.ordinal}"
        "museum"->"org_id='other'"
        else->error(change)
    }
    @Suppress("UNCHECKED_CAST")
    private fun handover(r:Runtime):SamplingSampleHandoverVo=r.db {
        val plans=r.service.detail("sampling")["plans"] as List<Map<String,Any?>>
        val samples=plans.single()["samples"] as List<Map<String,Any?>>
        val journal=samples.single()["journal"] as SamplingSampleJournalVo
        SamplingSampleHandoverVo(journal.revision,at.plusMinutes(80),"operator",journal.remainingQuantity,
            SamplingSampleCustodyVo(EnumSampleCustody.INTERNAL,"合成新样品柜","recipient"),"合成完好","合成馆内交接","proof","proof","proof")
    }
    private fun participantMutation(r:Runtime,person:String,change:String):String=when(change) {
        "role"->"DELETE FROM ${RoleUsers.tableName} WHERE user_id='$person' AND role_id='role:${if(person=="conservation")CollectionConst.ROLE_CONSERVATION else CollectionConst.ROLE_STORAGE_HEAD}'"
        "account"->"UPDATE ${Users.tableName} SET enabled=${if(r.postgres)"false" else "0"} WHERE id='$person'"
        "group"->"UPDATE ${Users.tableName} SET group_id='other' WHERE id='$person'"
        "department"->"UPDATE ${Users.tableName} SET group_id='museum' WHERE id='$person'"
        else->error(change)
    }
    @ParameterizedTest
    @CsvSource("plan,conservation,role","plan,conservation,department","plan,keeper,role",
        "submit,keeper,role","approved,conservation,role",
        "start,conservation,role","start,keeper,group",
        "take,custodian,account","take,custodian,group",
        "end,conservation,role","end,keeper,role",
        "analysis,custodian,account","analysis,custodian,group",
        "complete,conservation,role","complete,keeper,role","complete,custodian,account",
        "handover,recipient,account","handover,recipient,group")
    fun `actual participant SQL change during sealing rolls back new sampling responsibility`(kind:String,person:String,change:String)=fixture(true) {r->
        prepare(r,kind)
        // The request and archive trigger must refer to the same frozen predecessor revision.
        val handover=if(kind=="handover")handover(r) else null
        trigger(r,kind,participantMutation(r,person,change));val before=snapshot(r)
        assertThrows(BadRequestException::class.java){if(handover!=null)r.service.handoverSample("sampling",r.sampleId,handover) else operation(r,kind)}
        assertEquals(before,snapshot(r),"原件、方案、样品数量、保管及实际任职须整体回滚")
        dropTrigger(r,kind)
        assertDoesNotThrow {if(handover!=null)r.service.handoverSample("sampling",r.sampleId,handover) else operation(r,kind)}
    }
    @ParameterizedTest
    @CsvSource("take,custodian","end,conservation","analysis,custodian","complete,keeper","handover,recipient")
    fun `historical sampling replay survives a later departure without new responsibility`(kind:String,person:String)=fixture(true) {r->
        prepare(r,kind)
        val handover=if(kind=="handover")handover(r) else null
        if(handover!=null)r.service.handoverSample("sampling",r.sampleId,handover) else operation(r,kind)
        r.db {TransactionManager.current().exec(participantMutation(r,person,"account"))}
        val before=snapshot(r)
        assertDoesNotThrow {if(handover!=null)r.service.handoverSample("sampling",r.sampleId,handover) else operation(r,kind)}
        assertEquals(before,snapshot(r),"重复历史原件不得产生新办理或改写旧责任")
    }
    @Tag("postgres-concurrency")
    @ParameterizedTest
    @CsvSource("take,custodian,account,false","take,custodian,account,true",
        "end,conservation,role,false","end,conservation,role,true",
        "complete,custodian,account,false","complete,custodian,account,true",
        "handover,recipient,group,false","handover,recipient,group,true")
    fun `actual independent participant change while archive insert waits is respected`(kind:String,person:String,change:String,rollback:Boolean)=fixture(true) {r->
        org.junit.jupiter.api.Assumptions.assumeTrue(r.postgres,"须隔离PostgreSQL实际独立事务")
        prepare(r,kind)
        val handover=if(kind=="handover")handover(r) else null
        // Hold an advisory lock inside the real archive INSERT trigger, after the entry qualification check.
        trigger(r,kind,"PERFORM pg_advisory_xact_lock(7100317)")
        val business=tables.filter {it !in setOf(Users,Groups,Roles,RoleUsers,RolePermissions,Permissions,UserGroups)}.toTypedArray()
        val before=r.db {settledSafetySelectionState(*business)}
        val principal=SecurityContextHolder.getContext().authentication
        val pid=AtomicInteger();val pool=Executors.newSingleThreadExecutor()
        try {r.source.connection.use {holder->
            holder.autoCommit=false
            holder.createStatement().use {it.execute("SELECT pg_advisory_xact_lock(7100317)")}
            try {
                val writing=pool.submit<Result<Unit>> {
                    SecurityContextHolder.getContext().authentication=principal
                    try {runCatching {r.db {
                        TransactionManager.current().exec("SELECT pg_backend_pid()") {row->row.next();pid.set(row.getInt(1))}
                        if(handover!=null)r.service.handoverSample("sampling",r.sampleId,handover) else operation(r,kind)
                        Unit
                    }}} finally {SecurityContextHolder.clearContext()}
                }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);var waiting=false
                while(System.nanoTime()<deadline && !waiting) {
                    if(writing.isDone)fail<Nothing>("取样归档未实际等待：${writing.get().exceptionOrNull()}")
                    if(pid.get()!=0)r.source.connection.use {observer->observer.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock' AND wait_event='advisory' AND query LIKE '%col_permanent_archive%' AND cardinality(pg_blocking_pids(pid))>0)").use {q->q.setInt(1,pid.get());q.executeQuery().use {row->row.next();waiting=row.getBoolean(1)}}}
                    if(!waiting)Thread.sleep(20)
                }
                assertTrue(waiting,"须实际观察到档案INSERT触发器的PostgreSQL锁等待")
                holder.createStatement().use {assertEquals(1,it.executeUpdate(participantMutation(r,person,change)))}
                if(rollback)holder.rollback() else holder.commit()
                val result=writing.get(20,TimeUnit.SECONDS)
                if(rollback)result.getOrThrow() else assertTrue(result.exceptionOrNull() is BadRequestException,"独立事务提交资格变化后须回滚，实际$result")
            } finally {holder.rollback()}
        }} finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS))}
        if(rollback)assertNotEquals(before,r.db {settledSafetySelectionState(*business)},"资格变更回滚时，原合法办理应正常写入")
        else assertEquals(before,r.db {settledSafetySelectionState(*business)},"资格变更已经提交时，新的业务原件及数量保管应整体回滚")
    }
    @ParameterizedTest
    @CsvSource("plan,name","plan,level","plan,museum","submit,name","submit,level","submit,museum",
        "administrative,name","administrative,level","administrative,museum","start,name","start,level","start,museum",
        "take,name","take,level","take,museum")
    fun `actual archive SQL source change cannot authorize a stale sampling plan or extraction`(kind:String,change:String)=fixture {r->
        prepare(r,kind)
        trigger(r,kind,"UPDATE ${Collections.tableName} SET ${sourceMutation(change)} WHERE id='piece'")
        val before=snapshot(r)
        assertThrows(BadRequestException::class.java){operation(r,kind)}
        assertEquals(before,snapshot(r),"藏品、方案、签署原件、样品及占用须整体回滚")
        dropTrigger(r,kind);assertDoesNotThrow {operation(r,kind)}
    }
    @ParameterizedTest
    @CsvSource("plan","submit","administrative","start","take")
    fun `actual warehouse relocation retains the approved catalog sampling baseline`(kind:String)=fixture {r->
        prepare(r,kind)
        trigger(r,kind,"UPDATE ${Collections.tableName} SET warehouse_id='temporary-original-access' WHERE id='piece'")
        assertDoesNotThrow {operation(r,kind)}
        r.db {assertEquals("temporary-original-access",CollectionDao["piece"].warehouseId)}
    }
    @ParameterizedTest
    @CsvSource("end,name","end,level","end,museum","analysis,name","analysis,level","analysis,museum",
        "complete,name","complete,level","complete,museum")
    fun `later source changes do not rewrite or prevent factual ending and existing sample analysis`(kind:String,change:String)=fixture {r->
        prepare(r,kind)
        val frozen=r.db {CollectionSamplingPlanDao[r.planId].sourceSnapshot}
        trigger(r,kind,"UPDATE ${Collections.tableName} SET ${sourceMutation(change)} WHERE id='piece'")
        assertDoesNotThrow {operation(r,kind)}
        r.db {
            assertEquals(frozen,CollectionSamplingPlanDao[r.planId].sourceSnapshot)
            assertEquals(1,CollectionSampleDao.all().count())
        }
    }
    private fun departmentWriter(r:Runtime,change:String) {
        r.db {
            GroupDao.new("sampling-department"){name="合成取样部门";type='1';parentId=if(change=="mapping")"museum" else "0";parentIds="0,"}
            UserDao["operator"].groupId="sampling-department"
            if(change=="ancestor") {
                CollectionOrgDao.new("parent-museum"){name="合成总馆";code="P";systemGroupId="sampling-department"}
                CollectionOrgDao.new("middle-museum"){name="合成中间馆";code="I";parentId="parent-museum"}
                CollectionOrgDao["museum"].apply {systemGroupId=null;parentId="middle-museum"}
            }
        }
        r.login("operator")
    }
    @ParameterizedTest
    @CsvSource("create,permission","create,account","create,museum","submit,permission","submit,account","submit,museum",
        "plan,permission","administrative,permission","start,permission","take,permission","end,permission","analysis,permission","complete,permission",
        "plan,account","administrative,account","start,account","take,account","end,account","analysis,account","complete,account",
        "plan,museum","administrative,museum","start,museum","take,museum","end,museum","analysis,museum","complete,museum")
    fun `actual archive SQL qualification change rolls back sampling writer and allows restored retry`(kind:String,change:String)=fixture {r->
        prepare(r,kind)
        val mutation=when(change){"permission"->"DELETE FROM ${RolePermissions.tableName} WHERE role_id='role:FILE_operator' AND permission_id='${CollectionConst.SAMPLING}'";"account"->"UPDATE ${Users.tableName} SET enabled="+(if(r.postgres)"false" else "0")+" WHERE id='operator'";else->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"}
        trigger(r,kind,mutation);val before=snapshot(r)
        assertThrows(BadRequestException::class.java){operation(r,kind)}
        assertEquals(before,snapshot(r));dropTrigger(r,kind);assertDoesNotThrow {operation(r,kind)}
    }
    @Test fun `parent collection identity change in actual plan SQL cannot redirect sampling`()=fixture {r->
        prepare(r,"plan");trigger(r,"plan","UPDATE ${CollectionSamplings.tableName} SET collection_id='other-piece' WHERE id='sampling'")
        val before=snapshot(r);assertThrows(BadRequestException::class.java){operation(r,"plan")};assertEquals(before,snapshot(r));dropTrigger(r);assertDoesNotThrow {operation(r,"plan")}
    }
    @ParameterizedTest
    @CsvSource("create,mapping","plan,mapping","submit,mapping","administrative,mapping","start,mapping","take,mapping","end,mapping","analysis,mapping","complete,mapping",
        "create,ancestor","plan,ancestor","submit,ancestor","administrative,ancestor","start,ancestor","take,ancestor","end,ancestor","analysis,ancestor","complete,ancestor")
    fun `actual SQL museum mapping or ancestor change cannot preserve cached sampling authority`(kind:String,change:String)=fixture {r->
        departmentWriter(r,change);prepare(r,kind)
        val mutation=if(change=="mapping")"UPDATE ${CollectionOrgs.tableName} SET system_group_id='other' WHERE id='museum'"
            else "UPDATE ${CollectionOrgs.tableName} SET parent_id='other' WHERE id='middle-museum'"
        trigger(r,kind,mutation);val before=snapshot(r)
        val denied=assertThrows(BadRequestException::class.java){operation(r,kind)}
        assertEquals("无权查看有损取样",denied.message)
        assertEquals(before,snapshot(r));dropTrigger(r,kind);assertDoesNotThrow {operation(r,kind)}
    }
    @Test fun `principal change during original binding cannot sign for the earlier sampling writer`()=fixture {r->
        prepare(r,"plan");val before=snapshot(r)
        `when`(r.binder.resolveSingle("proof")).thenAnswer {r.login("other-operator");"proof"}
        assertThrows(BadRequestException::class.java){operation(r,"plan")};assertEquals(before,snapshot(r))
        `when`(r.binder.resolveSingle("proof")).thenReturn("proof");r.login("operator");assertDoesNotThrow {operation(r,"plan")}
    }
    @Tag("postgres-concurrency")
    @ParameterizedTest
    @CsvSource("plan,name,false","plan,name,true","plan,level,false","plan,level,true","plan,museum,false","plan,museum,true",
        "submit,name,false","submit,name,true","submit,level,false","submit,level,true","submit,museum,false","submit,museum,true",
        "administrative,name,false","administrative,name,true","administrative,level,false","administrative,level,true","administrative,museum,false","administrative,museum,true",
        "start,name,false","start,name,true","start,level,false","start,level,true","start,museum,false","start,museum,true",
        "take,name,false","take,name,true","take,level,false","take,level,true","take,museum,false","take,museum,true")
    fun `current source stage rechecks collection after actual row lock wait`(kind:String,change:String,rollback:Boolean)=fixture {r->
        org.junit.jupiter.api.Assumptions.assumeTrue(r.postgres,"须隔离PostgreSQL实际行锁")
        prepare(r,kind)
        val business=tables.filter {it!=Collections}.toTypedArray()
        val before=r.db {settledSafetySelectionState(*business)}
        val principal=SecurityContextHolder.getContext().authentication
        val pid=AtomicInteger();val pool=Executors.newSingleThreadExecutor()
        try {r.source.connection.use {holder->
            holder.autoCommit=false
            holder.createStatement().use {s->s.executeQuery("SELECT id FROM ${Collections.tableName} WHERE id='piece' FOR UPDATE").use {assertTrue(it.next())}}
            try {
                val writing=pool.submit<Result<Unit>> {
                    SecurityContextHolder.getContext().authentication=principal
                    try {runCatching {r.db {
                        // 同一事务先读取详情，实际等待后不能继续使用 DAO 缓存里的旧藏品。
                        assertNull(r.service.detail("sampling")["sourceIssue"])
                        TransactionManager.current().exec("SELECT pg_backend_pid()") {row->row.next();pid.set(row.getInt(1))}
                        operation(r,kind)
                    }}} finally {SecurityContextHolder.clearContext()}
                }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);var waiting=false
                while(System.nanoTime()<deadline && !waiting) {
                    if(writing.isDone)fail<Nothing>("取样请求未实际等待：${writing.get().exceptionOrNull()}")
                    if(pid.get()!=0)r.source.connection.use {observer->observer.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock' AND query LIKE '%col_collection%' AND cardinality(pg_blocking_pids(pid))>0)").use {s->s.setInt(1,pid.get());s.executeQuery().use {row->row.next();waiting=row.getBoolean(1)}}}
                    if(!waiting)Thread.sleep(20)
                }
                assertTrue(waiting,"须实际观察到PostgreSQL藏品锁等待")
                holder.createStatement().use {assertEquals(1,it.executeUpdate("UPDATE ${Collections.tableName} SET ${sourceMutation(change)} WHERE id='piece'"))}
                if(rollback)holder.rollback() else holder.commit()
                val result=writing.get(20,TimeUnit.SECONDS)
                if(rollback)result.getOrThrow() else {
                    assertTrue(result.exceptionOrNull() is BadRequestException,"藏品变化提交后须拒绝旧方案，实际$result")
                    assertEquals(before,r.db {settledSafetySelectionState(*business)})
                    r.db {CollectionDao["piece"].apply {name="piece";level=EnumRelicLevel.FIRST;orgId="museum"}}
                    assertDoesNotThrow {operation(r,kind)}
                }
                r.db {
                    val plan=CollectionSamplingPlanDao.all().single()
                    assertEquals(1,plan.version)
                    val phase=when(kind){"plan"->"PLAN";"submit"->"SUBMITTED";"administrative"->"ADMIN_%";"start"->"STARTED";else->"SAMPLE_%"}
                    assertEquals(1,CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizId eq plan.id.value) and (CollectionPermanentArchives.phase like phase) }.count())
                    when(kind) {
                        "submit"->assertEquals(EnumFlowStatus.AUDIT,CollectionSamplingDao["sampling"].flowStatus)
                        "start"->assertEquals(r.start,plan.started)
                        "take"->assertEquals(r.take,CollectionSampleDao.all().single().content)
                    }
                    assertEquals(if(kind in setOf("plan","administrative"))0 else 1,CollectionOperationLockDao.find {CollectionOperationLocks.bizId eq "sampling"}.count())
                }
            } finally {holder.rollback()}
        }} finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS))}
    }
    @Tag("postgres-concurrency")
    @ParameterizedTest
    @CsvSource("permission,false","permission,true","role,false","role,true","account,false","account,true","group,false","group,true","museum,false","museum,true",
        "mapping,false","mapping,true","ancestor,false","ancestor,true")
    fun `repeat start verifies current qualification after actual collection row lock wait`(change:String,rollback:Boolean)=fixture {r->
        org.junit.jupiter.api.Assumptions.assumeTrue(r.postgres,"须隔离PostgreSQL实际行锁")
        if(change in setOf("mapping","ancestor"))departmentWriter(r,change)
        prepare(r,"take")
        val business=tables.filter {it !in setOf(Users,Groups,Roles,RoleUsers,RolePermissions,Permissions,CollectionOrgs)}.toTypedArray()
        val before=r.db {settledSafetySelectionState(*business)};val principal=SecurityContextHolder.getContext().authentication
        val pid=AtomicInteger();val pool=Executors.newSingleThreadExecutor()
        try {r.source.connection.use {holder->
            holder.autoCommit=false
            holder.createStatement().use {s->s.executeQuery("SELECT id FROM ${Collections.tableName} WHERE id='piece' FOR UPDATE").use {assertTrue(it.next())}}
            try {
                val writing=pool.submit<Result<String>> {
                    SecurityContextHolder.getContext().authentication=principal
                    try {runCatching {r.db {TransactionManager.current().exec("SELECT pg_backend_pid()") {row->row.next();pid.set(row.getInt(1))};r.service.start("sampling",r.start)}}} finally {SecurityContextHolder.clearContext()}
                }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);var waiting=false
                while(System.nanoTime()<deadline && !waiting) {
                    if(writing.isDone)fail<Nothing>("取样请求未实际等待：${writing.get().exceptionOrNull()}")
                    if(pid.get()!=0)r.source.connection.use {observer->observer.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock' AND query LIKE '%col_collection%' AND cardinality(pg_blocking_pids(pid))>0)").use {s->s.setInt(1,pid.get());s.executeQuery().use {row->row.next();waiting=row.getBoolean(1)}}}
                    if(!waiting)Thread.sleep(20)
                }
                assertTrue(waiting,"须实际观察到PostgreSQL藏品锁等待")
                val mutation=when(change){"permission"->"DELETE FROM ${RolePermissions.tableName} WHERE role_id='role:FILE_operator' AND permission_id='${CollectionConst.SAMPLING}'";"role"->"DELETE FROM ${RoleUsers.tableName} WHERE user_id='operator' AND role_id='role:FILE_operator'";"account"->"UPDATE ${Users.tableName} SET enabled=false WHERE id='operator'";"group"->"UPDATE ${Users.tableName} SET group_id='other' WHERE id='operator'";"mapping"->"UPDATE ${CollectionOrgs.tableName} SET system_group_id='other' WHERE id='museum'";"ancestor"->"UPDATE ${CollectionOrgs.tableName} SET parent_id='other' WHERE id='middle-museum'";else->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"}
                holder.createStatement().use {assertEquals(1,it.executeUpdate(mutation))}
                if(rollback)holder.rollback() else holder.commit()
                val result=writing.get(20,TimeUnit.SECONDS)
                if(rollback)assertEquals(r.planId,result.getOrThrow()) else {
                    assertTrue(result.exceptionOrNull() is BadRequestException,"当前资格变化提交后须拒绝，实际$result")
                    if(change in setOf("mapping","ancestor"))assertEquals("无权查看有损取样",result.exceptionOrNull()?.message)
                }
            } finally {holder.rollback()}
        }} finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS))}
        assertEquals(before,r.db {settledSafetySelectionState(*business)})
    }
}
