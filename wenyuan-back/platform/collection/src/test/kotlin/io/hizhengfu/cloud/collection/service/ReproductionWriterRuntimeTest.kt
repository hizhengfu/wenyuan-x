package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.*
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.aop.framework.ProxyFactory
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import javax.sql.DataSource

/** Current directory and Spring transaction with an independently observed PostgreSQL row lock. */
class ReproductionWriterRuntimeTest {
    @TempDir lateinit var dir:Path
    private val at=LocalDateTime.now().minusHours(8).withNano(0)
    private val tables=arrayOf(CollectionOrgs,Collections,Reproductions,ReproductionPlans,ReproductionSafetyRecords,
        ReproductionOutputs,ReproductionOutputUses,CollectionAdministrativeRecords,CollectionPermanentArchives,
        CollectionPermanentArchiveFiles,CollectionOperationLocks,Files,Users,Groups,Roles,RoleUsers,RolePermissions,
        Permissions,UserGroups,FlowInstances,FlowInstanceLogs)
    private class Runtime(val tx:TransactionTemplate,val service:ReproductionComplianceService,
        val scope:CollectionScopeResolver,val source:DataSource,val postgres:Boolean,val binder:BusinessAttachmentBinder) {
        lateinit var planId:String
        lateinit var plan:ReproductionPlanVo
        lateinit var start:ReproductionStartVo
        lateinit var outputId:String
        lateinit var useId:String
        fun <T> db(block:()->T):T=tx.execute {block()}
        fun login(id:String="operator") {
            val actor=db {CollectionActorDirectory(scope).requireActive(id)}
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(actor,null,actor.authorities)
        }
    }
    private fun fixture(type:EnumRepType,actualParticipants:Boolean=false,block:(Runtime)->Unit) {
        val config=System.getProperty("collection.test.pg.config")
        val schema="reproduction_writer_"+UUID.randomUUID().toString().replace("-","")
        val postgres=config?.let {path->
            val p=Properties().apply {Path.of(path).toFile().inputStream().use(::load)}
            PGSimpleDataSource().apply {setURL(p.getProperty("url"));user=p.getProperty("user");password=p.getProperty("password")}.also {source->
                source.connection.use {it.createStatement().use {s->s.execute("CREATE SCHEMA $schema")}}
                source.currentSchema=schema
            }
        }
        val source:DataSource=postgres ?: SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("reproduction-writer.db")}"}
        val manager=SpringTransactionManager(source,DatabaseConfig {})
        val scope=CollectionScopeResolver("single","museum")
        val users=if(actualParticipants)CollectionResponsibleUserService(CollectionActorDirectory(scope),scope)
            else mock(CollectionResponsibleUserService::class.java)
        val binder=mock(BusinessAttachmentBinder::class.java).also {b->`when`(b.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}}
        val qualification=mock(CollectionConservationQualificationPort::class.java)
        // This fixture isolates the writer transaction; incident proof validation has its own suite.
        val incidents=mock(CollectionIncidentService::class.java).also {service->
            doReturn(mapOf("incident" to "synthetic-closed-proof")).`when`(service).requireClearance(
                anyString(),anyString(),anyString(),any() ?: IncidentSourceVo("fixture","rep","plan"),
                any() ?: at,anyString(),isNull(),anyList())
        }
        val proof=ConservationQualificationEvidence("unit","合成资质单位","certificate","合成文修号","合成机关","本材质复制拓印",
            at.minusYears(1).toLocalDate(),at.plusYears(1).toLocalDate(),"proof")
        `when`(qualification.requireQualified(anyString(),anyString(),any() ?: at.toLocalDate())).thenReturn(proof)
        try {StaticApplicationContext().use {context->
            context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            context.beanFactory.registerSingleton("qualification",qualification);context.refresh();SpringContextHolder().setApplicationContext(context)
            val original=ReproductionComplianceService(scope,users,
                CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider()),
                context.getBeanProvider(CollectionConservationQualificationPort::class.java),CollectionOperationLockService(),CollectionOriginalAccessService(),incidents)
            val factory=ProxyFactory(original).apply {isProxyTargetClass=true;addAdvice(TransactionInterceptor().apply {transactionManager=manager;transactionAttributeSource=AnnotationTransactionAttributeSource()})}
            val r=Runtime(TransactionTemplate(manager),factory.proxy as ReproductionComplianceService,scope,source,postgres!=null,binder)
            r.db {
                SchemaUtils.create(CollectionOrgs,Collections,Reproductions,ReproductionPlans,ReproductionSafetyRecords,ReproductionOutputs,
                    ReproductionOutputUses,CollectionAdministrativeRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
                    CollectionOperationLocks,Files,FlowInstances,FlowInstanceLogs)
                originalReaderSchema();originalReader("operator",CollectionConst.STORAGE)
                originalReader("other-operator",CollectionConst.STORAGE)
                if(actualParticipants) {
                    originalReader("keeper",CollectionConst.STORAGE)
                    actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD)
                    originalReader("leader",CollectionConst.STORAGE)
                    actorRole("leader",CollectionConst.ROLE_DIRECTOR)
                    originalReader("conservation",CollectionConst.STORAGE)
                    actorRole("conservation",CollectionConst.ROLE_CONSERVATION)
                    actorRole("operator",CollectionConst.ROLE_STORAGE_HEAD)
                }
                CollectionOrgDao.new("museum"){name="合成本馆";code="M";systemGroupId="museum"}
                CollectionOrgDao.new("other"){name="合成其他馆";code="O";systemGroupId="other"}
                CollectionDao.new("piece"){orgId="museum";name="合成藏品";totalRegNo="M001";registeredAt=at.minusMonths(1)
                    catalogStatus=EnumCatalogStatus.APPROVED;nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.FIRST;lifeStatus=EnumCollectionStatus.IN_STORE}
                ReproductionDao.new("rep"){orgId="museum";collectionId="piece";repType=type;purpose="科学研究";repDate=at.toLocalDate();createBy="operator"}
                FileDao.new("proof"){name="合成签署.pdf";path="proof";hash="proof";size=100;ext="pdf"}
            }
            r.login()
            try {block(r)} finally {SecurityContextHolder.clearContext()}
        }} finally {postgres?.connection?.use {it.createStatement().use {s->s.execute("DROP SCHEMA $schema CASCADE")}}}
    }
    private fun prepare(r:Runtime,kind:String) {r.db {
        r.plan=ReproductionPlanVo(sourceRevision=r.service.detail("rep")["sourceRevision"] as String,preparedAt=at,
            keeperUserId="keeper",leaderUserId="leader",undertakingUnitId="unit",undertakingUnitName="合成资质单位",scopeCheckedAt=at,
            scopeConfirmed=true,scopeReviewFileIds="proof",maxQuantity=1,usesOriginal=false,massProduction=false,rubbingYuanOrEarlier=false,
            originalSuitable=true,noDamageMethod="合成无损方法",noHardToolBeating=true,conditionFileIds="proof",eraAndMethodBasisFileIds="proof",
            limitedQuantityReason="合成研究仅需一件",siteAddress="合成研究室",safetyStandards="合成安全核对",safetyCheckIntervalDays=1,
            safetyPlanFileIds="proof",contractFileIds="proof",signedPlanFileIds="proof",confidentialityAssessment="合成保密核查",confidentialityFileIds="proof")
        if(kind=="plan")return@db
        r.planId=r.service.savePlan("rep",r.plan)
        if(kind=="administrative")return@db
        r.service.administrative("rep",permit(r))
        val rep=ReproductionDao["rep"];r.service.submit(rep);rep.flowInstanceId="flow-rep";rep.flowStatus=EnumFlowStatus.AUDIT
        listOf("keeper","leader").forEachIndexed {i,node->FlowInstanceLogDao.new {instanceId="flow-rep";taskId="task-$node";nodeKey=node;operator=node;
            action="APPROVE";comment="合成本人确认";createTime=at.plusMinutes(10+i.toLong())}}
        r.service.confirm(rep);rep.flowStatus=EnumFlowStatus.PASS
        r.start=ReproductionStartVo(r.planId,at.plusHours(1),"operator",null,null,at,true,"proof","proof")
        if(kind=="start")return@db
        assertEquals(r.planId,r.service.start("rep",r.start))
        if(kind in setOf("repeat","safety","output","stop"))return@db
        if(kind=="clearStop") {r.service.stop("rep",stop(r));return@db}
        r.outputId=r.service.output("rep",output())
        if(kind=="complete")return@db
        r.service.complete("rep",completion())
        if(kind in setOf("custody","use"))return@db
        r.useId=r.service.recordUse("rep",r.outputId,usage())
    }}
    private fun ready(r:Runtime)=prepare(r,"repeat")
    private fun permit(r:Runtime)=ReproductionAdministrativeVo(r.planId,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.NATIONAL,
        action=EnumAdministrativeAction.APPROVAL,authorityName="合成机关",documentNo="合成文号",issuedAt=at.toLocalDate(),fileIds="proof"))
    private fun output()=ReproductionOutputVo("SYNTHETIC-1","合成研究产物",at.plusHours(2),at.plusHours(3),true,
        "合成资质单位",at.plusHours(2),1,"proof","合成产物柜","keeper","科学研究","proof")
    private fun completion()=ReproductionCompletionVo(at.plusHours(4),"operator",true,"合成原件未受损，产物已登记","proof","proof")
    private fun stop(r:Runtime)=ReproductionStopVo(r.planId,at.plusHours(3),"operator","合成异常停止",false,true,false,
        "合成研究室","keeper","合成待核对现状","合成隔离保护","合成后续核查","proof","proof")
    private fun usage()=ReproductionOutputUseVo(at.plusHours(5),"科学研究","合成研究室","operator","proof")
    private fun operation(r:Runtime,kind:String):String=when(kind) {
        "plan"->r.service.savePlan("rep",r.plan)
        "administrative"->r.service.administrative("rep",permit(r))
        "start"->r.service.start("rep",r.start)
        "safety"->r.service.safety("rep",ReproductionSafetyVo(at.plusHours(2),"operator",true,"合成安全检查","合成核查措施","proof"))
        "output"->r.service.output("rep",output())
        "complete"->r.service.complete("rep",completion())
        "stop"->r.service.stop("rep",stop(r))
        "clearStop"->r.service.clearStop("rep",IncidentClearanceVo("synthetic-incident",at.plusHours(4),"conservation","operator",
            "合成已恢复安全","合成已完成处置","proof"))
        "custody"->r.service.transferCustody("rep",r.outputId,r.db {
            val out=ReproductionOutputDao[r.outputId]
            ReproductionOutputCustodyVo("museum",ReproductionOutputCustody.revision(out,emptyList(),emptyList()),at.plusHours(5),
                "keeper","合成产物柜","other-operator","合成新产物柜","合成逐件核对","合成保管交接","proof")
        })
        "use"->r.service.recordUse("rep",r.outputId,usage())
        "return"->r.service.returnOutput("rep",r.outputId,r.useId,
            ReproductionOutputReturnVo(at.plusHours(6),"合成产物柜","keeper","合成现状已核对","proof"))
        else->error(kind)
    }
    private fun mutation(r:Runtime,change:String)=when(change) {
        "permission"->"DELETE FROM ${RolePermissions.tableName} WHERE role_id='role:FILE_operator' AND permission_id='${CollectionConst.STORAGE}'"
        "role"->"DELETE FROM ${RoleUsers.tableName} WHERE user_id='operator' AND role_id='role:FILE_operator'"
        "account"->"UPDATE ${Users.tableName} SET enabled="+(if(r.postgres)"false" else "0")+" WHERE id='operator'"
        "group"->"UPDATE ${Users.tableName} SET group_id='other' WHERE id='operator'"
        "museum"->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
        "mapping"->"UPDATE ${CollectionOrgs.tableName} SET system_group_id='other' WHERE id='museum'"
        "ancestor"->"UPDATE ${CollectionOrgs.tableName} SET parent_id='other' WHERE id='middle-museum'"
        else->error(change)
    }
    private fun trigger(r:Runtime,kind:String,statement:String)=r.db {
        val phase=when(kind) {
            "plan"->"NEW.phase='PLAN'";"administrative"->"NEW.phase LIKE 'ADMIN_%'";"start"->"NEW.phase='STARTED'"
            "safety"->"NEW.phase LIKE 'SAFETY_%'";"output"->"NEW.phase LIKE 'OUTPUT_%'";"complete"->"NEW.phase='COMPLETED'"
            "stop"->"NEW.phase='STOPPED'";"clearStop"->"NEW.phase LIKE 'STOP_CLEARED_%'";"custody"->"NEW.phase LIKE 'CUSTODY_%'"
            "use"->"NEW.phase LIKE 'USE_%'";"return"->"NEW.phase LIKE 'RETURN_%'";else->error(kind)
        }
        val table=CollectionPermanentArchives.tableName
        val predicate="NEW.biz_type='${ReproductionComplianceService.BIZ}' AND $phase"
        if(r.postgres) {
            TransactionManager.current().exec("CREATE FUNCTION mutate_reproduction_writer() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF $predicate THEN $statement; END IF; RETURN NEW; END $$")
            TransactionManager.current().exec("CREATE TRIGGER mutate_reproduction_writer AFTER INSERT ON $table FOR EACH ROW EXECUTE FUNCTION mutate_reproduction_writer()")
        } else TransactionManager.current().exec("CREATE TRIGGER mutate_reproduction_writer AFTER INSERT ON $table WHEN $predicate BEGIN $statement; END")
    }
    private fun dropTrigger(r:Runtime)=r.db {
        TransactionManager.current().exec("DROP TRIGGER mutate_reproduction_writer"+(if(r.postgres)" ON ${CollectionPermanentArchives.tableName}" else ""))
        if(r.postgres)TransactionManager.current().exec("DROP FUNCTION mutate_reproduction_writer()")
    }
    private fun participantMutation(r:Runtime,person:String,change:String):String=when(change) {
        "account"->"UPDATE ${Users.tableName} SET enabled=${if(r.postgres)"false" else "0"} WHERE id='$person'"
        "group"->"UPDATE ${Users.tableName} SET group_id='other' WHERE id='$person'"
        "role"-> {
            val role=when(person) {
                "keeper"->CollectionConst.ROLE_STORAGE_HEAD
                "leader"->CollectionConst.ROLE_DIRECTOR
                "conservation"->CollectionConst.ROLE_CONSERVATION
                else->error(person)
            }
            "DELETE FROM ${RoleUsers.tableName} WHERE user_id='$person' AND role_id='role:$role'"
        }
        else->error(change)
    }
    companion object {
        @JvmStatic fun writerStages():Stream<Arguments> = listOf(EnumRepType.COPY,EnumRepType.RUBBING).flatMap {type->
            listOf("plan","administrative","start","safety","output","complete","stop","clearStop","custody","use","return").flatMap {kind->
                listOf("permission","role","account","group","museum","mapping","ancestor").map {change->Arguments.of(type,kind,change)}
            }
        }.stream()
        @JvmStatic fun independentParticipantStages():Stream<Arguments> = listOf(EnumRepType.COPY,EnumRepType.RUBBING).flatMap {type->
            listOf(Triple("plan","keeper","role"),Triple("plan","leader","role"),
                Triple("output","keeper","group"),Triple("stop","keeper","account"),
                Triple("clearStop","conservation","role"),Triple("custody","other-operator","group"),
                Triple("return","keeper","account")).flatMap {(kind,person,change)->
                listOf(false,true).map {rollback->Arguments.of(type,kind,person,change,rollback)}
            }
        }.stream()
    }
    @Tag("postgres-concurrency")
    @ParameterizedTest(name="{0} {1} participant {2} {3} rollback={4}")
    @MethodSource("independentParticipantStages")
    fun `independent participant change during archive insert respects commit and rollback`(
        type:EnumRepType,kind:String,person:String,change:String,rollback:Boolean,
    )=fixture(type,true) {r->
        org.junit.jupiter.api.Assumptions.assumeTrue(r.postgres,"须隔离PostgreSQL实际独立事务")
        prepare(r,kind)
        trigger(r,kind,"PERFORM pg_advisory_xact_lock(7100412)")
        val business=tables.filter {it !in setOf(Users,Groups,Roles,RoleUsers,RolePermissions,Permissions,UserGroups)}.toTypedArray()
        val before=r.db {settledSafetySelectionState(*business)}
        val qualificationBefore=r.db {settledSafetySelectionState(Users,RoleUsers)}
        val principal=SecurityContextHolder.getContext().authentication
        val pid=AtomicInteger();val pool=Executors.newSingleThreadExecutor()
        try {r.source.connection.use {holder->
            holder.autoCommit=false
            holder.createStatement().use {it.execute("SELECT pg_advisory_xact_lock(7100412)")}
            try {
                val writing=pool.submit<Result<String>> {
                    SecurityContextHolder.getContext().authentication=principal
                    try {runCatching {r.db {
                        TransactionManager.current().exec("SELECT pg_backend_pid()") {row->row.next();pid.set(row.getInt(1))}
                        operation(r,kind)
                    }}} finally {SecurityContextHolder.clearContext()}
                }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);var waiting=false
                while(System.nanoTime()<deadline && !waiting) {
                    if(writing.isDone)fail<Nothing>("复制拓印归档未实际等待：${writing.get().exceptionOrNull()}")
                    if(pid.get()!=0)r.source.connection.use {observer->observer.prepareStatement(
                        "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock' AND wait_event='advisory' AND query LIKE '%col_permanent_archive%' AND cardinality(pg_blocking_pids(pid))>0)",
                    ).use {q->q.setInt(1,pid.get());q.executeQuery().use {row->row.next();waiting=row.getBoolean(1)}}}
                    if(!waiting)Thread.sleep(20)
                }
                assertTrue(waiting,"须实际观察到档案INSERT触发器的PostgreSQL锁等待")
                holder.createStatement().use {assertEquals(1,it.executeUpdate(participantMutation(r,person,change)))}
                if(rollback)holder.rollback() else holder.commit()
                val result=writing.get(20,TimeUnit.SECONDS)
                if(rollback)assertTrue(result.getOrThrow().isNotBlank(),"变更回滚后原合法办理须成功")
                else assertTrue(result.exceptionOrNull() is BadRequestException,"独立提交人员资格变化后须拒绝，实际$result")
            } finally {holder.rollback()}
        }} finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS))}
        val after=r.db {settledSafetySelectionState(*business)}
        if(rollback) {
            assertNotEquals(before,after,"资格变更回滚时，原合法办理须正常写入")
            assertEquals(qualificationBefore,r.db {settledSafetySelectionState(Users,RoleUsers)})
        } else {
            assertEquals(before,after,"已提交的资格变化须使新业务及原件整体回滚")
            assertNotEquals(qualificationBefore,r.db {settledSafetySelectionState(Users,RoleUsers)},"不能回滚另一事务已提交的人员变化")
        }
        // The writer remains enabled and retains its original storage permission in both outcomes.
        r.db {
            val writer=CollectionActorDirectory(r.scope).requireActive("operator")
            assertTrue(CollectionConst.STORAGE in writer.permissions)
            assertTrue(r.scope.isMuseumMember(writer,"museum"))
        }
    }
    @Tag("postgres-concurrency")
    @ParameterizedTest
    @EnumSource(value=EnumRepType::class,names=["COPY","RUBBING"])
    fun `postgres custody column upgrade and long history preserve original registration`(type:EnumRepType)=fixture(type,true) {r->
        org.junit.jupiter.api.Assumptions.assumeTrue(r.postgres,"须现有开发PostgreSQL隔离schema")
        prepare(r,"custody")
        val original=r.db {ReproductionOutputDao[r.outputId].content}
        val archivesBefore=r.db {settledSafetySelectionState(CollectionPermanentArchives,CollectionPermanentArchiveFiles)}
        r.db {
            val tx=TransactionManager.current();tx.entityCache.flush()
            tx.exec("ALTER TABLE ${ReproductionOutputs.tableName} DROP COLUMN custody")
            SchemaUtils.createMissingTablesAndColumns(ReproductionOutputs)
            val row=ReproductionOutputs.selectAll().single()
            assertEquals(original,row[ReproductionOutputs.content])
            assertEquals(r.outputId,row[ReproductionOutputs.id].value)
            assertNull(row[ReproductionOutputs.custody],"升级不得补造历史交接")
            tx.exec("SELECT data_type,is_nullable FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='${ReproductionOutputs.tableName}' AND column_name='custody'") {column->
                assertTrue(column.next());assertEquals("text",column.getString(1));assertEquals("YES",column.getString(2))
                assertFalse(column.next())
            }
        }
        assertEquals(archivesBefore,r.db {settledSafetySelectionState(CollectionPermanentArchives,CollectionPermanentArchiveFiles)})
        val handoverIds=mutableListOf<String>()
        repeat(12) {n->
            val value=r.db {
                val out=ReproductionOutputDao[r.outputId]
                val previous=out.custody?.records.orEmpty()
                val position=ReproductionOutputCustody.position(out,emptyList(),previous)
                ReproductionOutputCustodyVo("museum",ReproductionOutputCustody.revision(out,emptyList(),previous),at.plusHours(5).plusMinutes(n.toLong()),
                    position.custodianUserId,position.location,if(n%2==0)"other-operator" else "keeper","合成交接位置$n",
                    "实际保存状况".repeat(1000),"合成逐件保管交接$n","proof")
            }
            handoverIds+=r.service.transferCustody("rep",r.outputId,value)
        }
        r.db {
            val out=ReproductionOutputDao[r.outputId]
            assertEquals(original,out.content,"新增交接不得改写原始产物登记")
            val ledger=out.custody!!
            assertEquals(handoverIds,ledger.records.map {it.id})
            assertEquals(12,ledger.records.size)
            assertTrue(JsonUtils.toJson(ledger)!!.length>65535,"应超过短文本边界并完整重读")
            assertEquals(12L,CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq ReproductionComplianceService.BIZ) and
                    (CollectionPermanentArchives.bizId eq r.planId) and (CollectionPermanentArchives.phase like "CUSTODY_%")
            }.count())
            assertNotNull(r.service.detail("rep")["plans"])
        }
    }
    @ParameterizedTest(name="{0} {1} current {2}")
    @MethodSource("writerStages")
    fun `actual archive SQL qualification change rolls back physical writer and allows restored retry`(type:EnumRepType,kind:String,change:String)=fixture(type) {r->
        if(change in setOf("mapping","ancestor"))departmentWriter(r,change)
        prepare(r,kind);trigger(r,kind,mutation(r,change));val before=r.db {settledSafetySelectionState(*tables)}
        val denied=assertThrows(BadRequestException::class.java){operation(r,kind)}
        assertTrue(denied.message.orEmpty().contains(Regex("权限|账号|收藏单位|无权")),denied.message)
        assertEquals(before,r.db {settledSafetySelectionState(*tables)},"业务、原件及目录变化须一并回滚")
        dropTrigger(r);assertDoesNotThrow {operation(r,kind)}
    }
    @ParameterizedTest
    @EnumSource(value=EnumRepType::class,names=["COPY","RUBBING"])
    fun `principal change during original binding cannot sign for earlier physical writer`(type:EnumRepType)=fixture(type) {r->
        prepare(r,"plan");val before=r.db {settledSafetySelectionState(*tables)}
        `when`(r.binder.resolveSingle("proof")).thenAnswer {r.login("other-operator");"proof"}
        val denied=assertThrows(BadRequestException::class.java){operation(r,"plan")}
        assertEquals("登记期间办理账号已变化，请重新登录核实",denied.message)
        assertEquals(before,r.db {settledSafetySelectionState(*tables)})
        `when`(r.binder.resolveSingle("proof")).thenReturn("proof");r.login();assertDoesNotThrow {operation(r,"plan")}
    }
    private fun departmentWriter(r:Runtime,change:String) {
        r.db {
            GroupDao.new("department"){name="合成复制部门";type='1';parentId=if(change=="mapping")"museum" else "0";parentIds="0,"}
            UserDao["operator"].groupId="department"
            if(change=="ancestor") {
                CollectionOrgDao.new("parent-museum"){name="合成总馆";code="P";systemGroupId="department"}
                CollectionOrgDao.new("middle-museum"){name="合成中间馆";code="I";parentId="parent-museum"}
                CollectionOrgDao["museum"].apply {systemGroupId=null;parentId="middle-museum"}
            }
        }
        r.login()
    }
    @ParameterizedTest
    @EnumSource(value=EnumRepType::class,names=["COPY","RUBBING"])
    fun `current physical writer repeats started plan without changing business state`(type:EnumRepType)=fixture(type) {r->
        ready(r);val before=r.db {settledSafetySelectionState(*tables)}
        assertEquals(r.planId,r.service.start("rep",r.start))
        assertEquals(before,r.db {settledSafetySelectionState(*tables)})
    }
    @Tag("postgres-concurrency")
    @ParameterizedTest
    @CsvSource("COPY,permission,false","COPY,permission,true","COPY,role,false","COPY,role,true","COPY,account,false","COPY,account,true",
        "COPY,group,false","COPY,group,true","COPY,museum,false","COPY,museum,true","COPY,mapping,false","COPY,mapping,true","COPY,ancestor,false","COPY,ancestor,true",
        "RUBBING,permission,false","RUBBING,permission,true","RUBBING,role,false","RUBBING,role,true","RUBBING,account,false","RUBBING,account,true",
        "RUBBING,group,false","RUBBING,group,true","RUBBING,museum,false","RUBBING,museum,true","RUBBING,mapping,false","RUBBING,mapping,true","RUBBING,ancestor,false","RUBBING,ancestor,true")
    fun `physical repeat start rechecks authority after actual collection row lock wait`(type:EnumRepType,change:String,rollback:Boolean)=fixture(type) {r->
        org.junit.jupiter.api.Assumptions.assumeTrue(r.postgres,"须隔离PostgreSQL实际行锁")
        if(change in setOf("mapping","ancestor"))departmentWriter(r,change)
        ready(r)
        val business=tables.filter {it !in setOf(Users,Groups,Roles,RoleUsers,RolePermissions,Permissions,UserGroups,CollectionOrgs)}.toTypedArray()
        val before=r.db {settledSafetySelectionState(*business)};val principal=SecurityContextHolder.getContext().authentication
        val pid=AtomicInteger();val pool=Executors.newSingleThreadExecutor()
        try {r.source.connection.use {holder->
            holder.autoCommit=false
            holder.createStatement().use {s->s.executeQuery("SELECT id FROM ${Collections.tableName} WHERE id='piece' FOR UPDATE").use {assertTrue(it.next())}}
            try {
                val writing=pool.submit<Result<String>> {
                    SecurityContextHolder.getContext().authentication=principal
                    try {runCatching {r.db {TransactionManager.current().exec("SELECT pg_backend_pid()") {row->row.next();pid.set(row.getInt(1))};r.service.start("rep",r.start)}}} finally {SecurityContextHolder.clearContext()}
                }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);var waiting=false
                while(System.nanoTime()<deadline && !waiting) {
                    if(writing.isDone)fail<Nothing>("请求未实际等待：${writing.get().exceptionOrNull()}")
                    if(pid.get()!=0)r.source.connection.use {observer->observer.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock' AND query LIKE '%col_collection%' AND cardinality(pg_blocking_pids(pid))>0)").use {s->s.setInt(1,pid.get());s.executeQuery().use {row->row.next();waiting=row.getBoolean(1)}}}
                    if(!waiting)Thread.sleep(20)
                }
                assertTrue(waiting,"须实际观察到PostgreSQL藏品行锁等待")
                val mutation=when(change) {
                    "permission"->"DELETE FROM ${RolePermissions.tableName} WHERE role_id='role:FILE_operator' AND permission_id='${CollectionConst.STORAGE}'"
                    "role"->"DELETE FROM ${RoleUsers.tableName} WHERE user_id='operator' AND role_id='role:FILE_operator'"
                    "account"->"UPDATE ${Users.tableName} SET enabled=false WHERE id='operator'"
                    "group"->"UPDATE ${Users.tableName} SET group_id='other' WHERE id='operator'"
                    "mapping"->"UPDATE ${CollectionOrgs.tableName} SET system_group_id='other' WHERE id='museum'"
                    "ancestor"->"UPDATE ${CollectionOrgs.tableName} SET parent_id='other' WHERE id='middle-museum'"
                    else->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
                }
                holder.createStatement().use {assertEquals(1,it.executeUpdate(mutation))}
                if(rollback)holder.rollback() else holder.commit()
                val result=writing.get(20,TimeUnit.SECONDS)
                if(rollback)assertEquals(r.planId,result.getOrThrow()) else assertTrue(result.exceptionOrNull() is BadRequestException,"资格变化提交后须拒绝，实际$result")
            } finally {holder.rollback()}
        }} finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS))}
        assertEquals(before,r.db {settledSafetySelectionState(*business)})
    }
}
