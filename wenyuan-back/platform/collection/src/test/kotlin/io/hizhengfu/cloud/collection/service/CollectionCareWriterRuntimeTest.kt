package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
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
import java.time.temporal.ChronoUnit
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/** Real directory, actual archive INSERT and Spring rollback in an owned schema. */
class CollectionCareWriterRuntimeTest {
    @TempDir lateinit var dir: Path
    private val at=LocalDateTime.now().minusHours(8).truncatedTo(ChronoUnit.MILLIS)
    private val tables=arrayOf(CollectionOrgs,Collections,Warehouses,WarehouseLogs,WarehouseEntryApplications,Requisitions,StorageMovements,
        CollectionCarePlans,CollectionCareChecks,CollectionCareWorks,CollectionOperationLocks,
        CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,
        Users,Groups,Roles,RoleUsers,RolePermissions,Permissions,UserGroups)
    companion object {
        private val stages=listOf("plan","conservation-sign","keeper-sign","check","start","end","clear")
        @JvmStatic fun qualificationCases():List<Arguments> =
            stages.flatMap { stage -> listOf("permission","account","museum","membership","role").map { Arguments.of(stage,it) } } +
            listOf("plan","conservation-sign","keeper-sign","start","clear").flatMap { stage ->
                listOf("conservation-role","keeper-role").map { Arguments.of(stage,it) }
            } + listOf("check","end").flatMap { stage -> listOf("custodian-account","custodian-membership").map { Arguments.of(stage,it) } }
        @JvmStatic fun currentSourceCases():List<Arguments> =
            listOf("plan","conservation-sign","keeper-sign","check","start").flatMap { stage ->
                listOf("name","nature","quantity").map { Arguments.of(stage,it) }
            } + listOf("start","end","clear").map { Arguments.of(it,"inventory-hold") }
        @JvmStatic fun historicalRepeatCases()=listOf("plan","check","start","end","clear")
        @JvmStatic fun historicalCompletionCases()=listOf("end","clear")
        @JvmStatic fun openFormCases()=listOf("missing","blank","wrong-revision","name","nature","quantity")
        @JvmStatic fun commitCases()=listOf(true,false)
    }
    private class Runtime(val tx:TransactionTemplate,val service:CollectionCareService,val incidents:CollectionIncidentService,
        val scope:CollectionScopeResolver,val archives:CollectionPermanentArchiveService,val postgres:Boolean,val source:DataSource) {
        lateinit var operation:()->String
        lateinit var actor:String
        fun <T> db(block:()->T):T=tx.execute {block()}
        fun login(id:String) {
            val user=db {CollectionActorDirectory(scope).requireActive(id)}
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        }
    }
    private fun fixture(block:(Runtime)->Unit) {
        val schema="care_writer_"+UUID.randomUUID().toString().replace("-","")
        val pg=System.getProperty("collection.test.pg.config")?.let { path ->
            val properties=Properties().apply {Path.of(path).toFile().inputStream().use(::load)}
            PGSimpleDataSource().apply {
                setURL(properties.getProperty("url"));user=properties.getProperty("user");password=properties.getProperty("password")
                connection.use {it.createStatement().use {sql->sql.execute("CREATE SCHEMA $schema")}}
                currentSchema=schema;applicationName=schema
            }
        }
        val source:DataSource=pg ?: SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("care.db")}"}
        val manager=SpringTransactionManager(source,DatabaseConfig {})
        val scope=CollectionScopeResolver("single","museum")
        val users=CollectionResponsibleUserService(CollectionActorDirectory(scope),scope)
        val root=createSafetyOriginalRoot()
        val binder=mock(BusinessAttachmentBinder::class.java).also {`when`(it.resolveSingle(anyString())).thenAnswer {c->c.getArgument<String>(0)}}
        val archives=CollectionPermanentArchiveService(binder,scope,LocalFileObjectStorage(root.toString()),unprotectedArchiveReadAccessProvider())
        val sources=CollectionIncidentSourceService(listOf(CollectionCareIncidentSourceProvider(archives)))
        val incidents=CollectionIncidentService(scope,users,archives,sources,listOf(CollectionCareSafetyGuard(archives)))
        val locks=CollectionOperationLockService(listOf(CollectionIncidentOperationGuard(),CollectionCareSafetyGuard(archives,incidents)))
        val factory=ProxyFactory(CollectionCareService(scope,users,archives,CollectionOriginalAccessService(),locks,incidents)).apply {
            isProxyTargetClass=true
            addAdvice(TransactionInterceptor().apply {transactionManager=manager;transactionAttributeSource=AnnotationTransactionAttributeSource()})
        }
        try {StaticApplicationContext().use {context ->
            context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
            val r=Runtime(TransactionTemplate(manager),factory.proxy as CollectionCareService,incidents,scope,archives,pg!=null,source)
            r.db {
                SchemaUtils.create(*tables)
                CollectionOrgDao.new("museum"){name="合成本馆";code="M";systemGroupId="museum"}
                CollectionOrgDao.new("other"){name="合成外馆";code="O";systemGroupId="other"}
                originalReaderSchema();incidentReaderFixture("operator","keeper","leader","conservation","custodian")
                for(id in listOf("operator","keeper","conservation"))actorRole(id,"CARE_$id",setOf(CollectionConst.CARE,CollectionConst.SELECT,"FILE_SELECT"))
                actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD);actorRole("conservation",CollectionConst.ROLE_CONSERVATION)
                actorRole("leader",CollectionConst.ROLE_DIRECTOR);actorRole("custodian",CollectionConst.ROLE_KEEPER)
                WarehouseDao.new("store"){orgId="museum";name="合成库房"}
                CollectionDao.new("piece"){orgId="museum";name="合成藏品";totalRegNo="M1";nature=EnumCollectionNature.OTHER;
                    registeredAt=at.minusMonths(1);catalogStatus=EnumCatalogStatus.APPROVED;lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="store"}
                FileDao.new("proof"){name="合成原件.pdf";path="proof";hash="proof";size=100;ext="pdf"}
                TransactionManager.current().exec("CREATE TABLE care_writer_fault (hits INTEGER NOT NULL)")
                TransactionManager.current().exec("INSERT INTO care_writer_fault VALUES (0)")
            }
            r.login("operator")
            try {block(r)} finally {SecurityContextHolder.clearContext()}
        }} finally {
            pg?.connection?.use {it.createStatement().use {sql->sql.execute("DROP SCHEMA $schema CASCADE")}}
            removeSafetyOriginalRoot(root)
        }
    }
    private fun now()=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
    private val signature=CollectionCareSignatureVo("合成本人确认方法与风险","proof")
    private fun plan(r:Runtime)=CollectionCarePlanVo(sourceRevision=r.service.detail("piece")["sourceRevision"] as String,preparedAt=at,
        conservationUserId="conservation",keeperUserId="keeper",inspectionIntervalDays=7,careIntervalDays=30,
        methods=setOf(EnumCareMethod.DUST_REMOVAL),instructions="合成除尘范围",environmentRequirements="合成环境核对",storageProtection="合成支撑",
        safetyInstructions="合成发现异常停止",planFileIds="proof")
    private fun inspection(p:String,abnormal:Boolean=false)=CollectionCareCheckVo(p,if(abnormal)at.plusMinutes(2) else now(),"operator","合成库房",true,"custodian",
        "合成保存现状","合成病害记录","合成环境记录",abnormal,!abnormal,"合成保护措施","合成复核安排","proof","proof")
    private fun attendance(r:Runtime)=r.db {
        val attendedAt=now()
        val entry=WarehouseAttendanceVo(attendedAt,setOf("operator","custodian"),identityChecked=true,continuousAccompaniment=true,signedRecordFileIds="proof")
        WarehouseEntryApplicationDao.new("entry"){orgId="museum";applicationNo="合成入库";applicantId="operator";entrantIds="operator,custodian";
            warehouseIds="store";purpose="合成养护";plannedStart=attendedAt;plannedEnd=attendedAt.plusDays(1);actualEntryAt=attendedAt;entryRecord=entry}
        val entries=WarehouseDiaryEntriesVo(listOf(WarehouseDiaryVisitVo("entry","合成入库","合成养护",entry,null,attendedAt,attendedAt.plusDays(1),exitedAt=null)))
        val diary=WarehouseDiaryVo(attendedAt,setOf("custodian"),true,"合成养护","合成无异常",listOf(WarehouseEnvironmentReadingVo(attendedAt,22.0,50.0,"合成读数","proof")),false,signedDiaryFileIds="proof")
        WarehouseLogDao.new("log"){orgId="museum";warehouseId="store";logDate=attendedAt.toLocalDate();content="合成养护";this.diary=diary;this.entries=entries}
        r.archives.seal("museum",WarehouseDiaryService.BIZ,"log","RECORDED_1",mapOf("warehouseId" to "store","logDate" to attendedAt.toLocalDate(),"workContent" to "合成养护","diary" to diary,"entries" to entries),"proof")
        r.archives.seal("museum",CollectionConst.BIZ_WAREHOUSE_ENTRY,"entry","CHECKED_IN",entry,"proof")
    }
    private fun prepare(r:Runtime,stage:String,unsafeEnd:Boolean=false) {
        val value=plan(r)
        if(stage=="plan") {r.actor="operator";r.operation={r.service.savePlan("piece",value)};return}
        val p=r.service.savePlan("piece",value)
        r.login("conservation")
        if(stage=="conservation-sign"){r.actor="conservation";r.operation={r.service.sign("piece",p,signature)};return}
        r.service.sign("piece",p,signature);r.login("keeper")
        if(stage=="keeper-sign"){r.actor="keeper";r.operation={r.service.sign("piece",p,signature)};return}
        r.service.sign("piece",p,signature);r.login("operator")
        val inspect=inspection(p,stage=="clear")
        if(stage=="check"){r.actor="operator";r.operation={r.service.inspect("piece",inspect,value.sourceRevision)};return}
        val check=r.service.inspect("piece",inspect,value.sourceRevision)
        if(stage=="clear") {
            val support=IncidentSourceTestSupport(r.incidents,at.plusMinutes(3),"proof")
            val incident=r.db {support.create(IncidentSourceVo(CollectionCareEvidence.BIZ,"piece",check),listOf(IncidentItemVo("piece",true,EnumIncidentSecurity.NONE,false,"合成裂隙")))}
            r.db {support.close(incident)}
            val clearance=IncidentClearanceVo(incident,now(),"conservation","keeper","合成处理后稳定","合成保护核实","proof")
            r.actor="keeper";r.login(r.actor);r.operation={r.service.clear("piece",check,clearance)};return
        }
        attendance(r)
        val start=CollectionCareStartVo(p,check,now(),"operator",setOf(EnumCareMethod.DUST_REMOVAL),onSiteWarehouseLogId="log",actualLocation="合成库房",safetyChecks="合成环境与共同到场",signedFileIds="proof")
        r.actor="operator"
        if(stage=="start"){r.operation={r.service.start("piece",start)};return}
        val work=r.service.start("piece",start)
        val end=CollectionCareEndVo(now(),true,setOf(EnumCareMethod.DUST_REMOVAL),"合成完成步骤","合成现状","合成病害",false,true,true,"合成库房","custodian","合成保护措施","合成复核","proof","proof")
        r.operation={r.service.end("piece",work,if(unsafeEnd)end.copy(completed=false,safeToRelease=false) else end)}
    }
    private fun trigger(r:Runtime,stage:String,change:String)=r.db {
        val phase=when(stage){"plan"->"PLAN";"conservation-sign"->"CONSERVATION";"keeper-sign"->"KEEPER";"check"->"CHECKED";"start"->"STARTED";"end"->"ENDED";else->"CLEARED_1"}
        val mutation=when(change) {
            "permission"->"DELETE FROM sys_role_permission WHERE role_id='role:CARE_${r.actor}' AND permission_id='${CollectionConst.CARE}'"
            "account"->"UPDATE sys_user SET enabled=${if(r.postgres)"false" else "0"} WHERE id='${r.actor}'"
            "museum"->"UPDATE col_org SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
            "membership"->"UPDATE sys_user SET group_id='other' WHERE id='${r.actor}'"
            "role"->"UPDATE sys_role SET enabled=${if(r.postgres)"false" else "0"} WHERE id='role:CARE_${r.actor}'"
            "conservation-role"->"DELETE FROM sys_role_user WHERE user_id='conservation' AND role_id='role:${CollectionConst.ROLE_CONSERVATION}'"
            "keeper-role"->"DELETE FROM sys_role_user WHERE user_id='keeper' AND role_id='role:${CollectionConst.ROLE_STORAGE_HEAD}'"
            "custodian-account"->"UPDATE sys_user SET enabled=${if(r.postgres)"false" else "0"} WHERE id='custodian'"
            "custodian-membership"->"UPDATE sys_user SET group_id='other' WHERE id='custodian'"
            "name"->"UPDATE col_collection SET name='合成已订正名称' WHERE id='piece'"
            "nature"->"UPDATE col_collection SET collection_nature=${EnumCollectionNature.RELIC.ordinal} WHERE id='piece'"
            "quantity"->"UPDATE col_collection SET quantity=2 WHERE id='piece'"
            "inventory-hold"->"UPDATE col_collection SET inventory_hold=${if(r.postgres)"true" else "1"} WHERE id='piece'"
            else->error(change)
        }
        val predicate="NEW.biz_type='${CollectionCareEvidence.BIZ}' AND NEW.phase='$phase'"
        if(r.postgres) {
            TransactionManager.current().exec("CREATE FUNCTION mutate_care_writer() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF $predicate THEN UPDATE care_writer_fault SET hits=hits+1; $mutation; END IF; RETURN NEW; END $$")
            TransactionManager.current().exec("CREATE TRIGGER mutate_care_writer AFTER INSERT ON col_permanent_archive FOR EACH ROW EXECUTE FUNCTION mutate_care_writer()")
        } else TransactionManager.current().exec("CREATE TRIGGER mutate_care_writer AFTER INSERT ON col_permanent_archive WHEN $predicate BEGIN UPDATE care_writer_fault SET hits=hits+1; $mutation; END")
    }
    private fun snapshot(r:Runtime)=r.db {settledSafetySelectionState(*tables)}
    private fun assertQualification(stage:String,change:String)=fixture {r ->
        prepare(r,stage);r.login(r.actor);trigger(r,stage,change)
        val before=snapshot(r)
        val failure=runCatching {r.operation()}.exceptionOrNull()
        if(failure==null) {
            val hits=r.db {TransactionManager.current().exec("SELECT hits FROM care_writer_fault"){v->v.next();v.getInt(1)}!!}
            assertEquals(1,hits,"Counterexample must execute the actual archive INSERT and mutation SQL")
            fail<Unit>("Actual archive SQL $change change still committed $stage; rejection and rollback required")
        }
        assertInstanceOf(BadRequestException::class.java,failure)
        assertEquals(before,snapshot(r),"Care facts, archives, occupancy and current qualification must roll back together")
        assertEquals(0,r.db {TransactionManager.current().exec("SELECT hits FROM care_writer_fault"){v->v.next();v.getInt(1)}!!})
        r.db {
            TransactionManager.current().exec("DROP TRIGGER mutate_care_writer"+if(r.postgres)" ON col_permanent_archive" else "")
            if(r.postgres)TransactionManager.current().exec("DROP FUNCTION mutate_care_writer()")
        }
        val id=r.operation();val settled=snapshot(r)
        assertEquals(id,r.operation());assertEquals(settled,snapshot(r),"Valid repeated request must retain its original facts")
    }
    @ParameterizedTest @MethodSource("qualificationCases")
    fun `actual archive SQL rechecks care writer and current participants`(stage:String,change:String)=assertQualification(stage,change)
    @Tag("postgres-concurrency") @ParameterizedTest @MethodSource("qualificationCases")
    fun `postgres actual archive SQL rechecks care writer and current participants`(stage:String,change:String) {
        assertNotNull(System.getProperty("collection.test.pg.config"));assertQualification(stage,change)
    }
    @ParameterizedTest @MethodSource("currentSourceCases")
    fun `actual archive SQL rejects stale care source and new release restriction`(stage:String,change:String)=assertQualification(stage,change)
    @Tag("postgres-concurrency") @ParameterizedTest @MethodSource("currentSourceCases")
    fun `postgres actual archive SQL rejects stale care source and new release restriction`(stage:String,change:String) {
        assertNotNull(System.getProperty("collection.test.pg.config"));assertQualification(stage,change)
    }
    private fun assertHistoricalRepeat(stage:String)=fixture {r ->
        prepare(r,stage);r.login(r.actor)
        val id=r.operation();val history=r.service.history("piece","museum")
        r.db {CollectionDao["piece"].name="合成后来订正名称"}
        val before=snapshot(r)
        assertEquals(id,r.operation());assertEquals(before,snapshot(r))
        val after=r.service.history("piece","museum")
        listOf("plans","checks","works").forEach {assertEquals(history[it],after[it])}
    }
    @ParameterizedTest @MethodSource("historicalRepeatCases")
    fun `frozen care repeat survives a later catalog correction`(stage:String)=assertHistoricalRepeat(stage)
    @Tag("postgres-concurrency") @ParameterizedTest @MethodSource("historicalRepeatCases")
    fun `postgres frozen care repeat survives a later catalog correction`(stage:String) {
        assertNotNull(System.getProperty("collection.test.pg.config"));assertHistoricalRepeat(stage)
    }
    private fun assertHistoricalCompletion(stage:String)=fixture {r ->
        prepare(r,stage);r.login(r.actor);trigger(r,stage,"name")
        val id=r.operation()
        assertEquals(1,r.db {TransactionManager.current().exec("SELECT hits FROM care_writer_fault"){v->v.next();v.getInt(1)}!!})
        assertEquals("合成已订正名称",r.db {CollectionDao["piece"].name})
        val before=snapshot(r);assertEquals(id,r.operation());assertEquals(before,snapshot(r))
        assertDoesNotThrow {r.service.history("piece","museum")}
    }
    @ParameterizedTest @MethodSource("historicalCompletionCases")
    fun `actual historical care completion permits independent catalog correction`(stage:String)=assertHistoricalCompletion(stage)
    @Tag("postgres-concurrency") @ParameterizedTest @MethodSource("historicalCompletionCases")
    fun `postgres actual historical care completion permits independent catalog correction`(stage:String) {
        assertNotNull(System.getProperty("collection.test.pg.config"));assertHistoricalCompletion(stage)
    }
    private fun assertUnsafeEnd()=fixture {r ->
        prepare(r,"end",unsafeEnd=true);r.login(r.actor);trigger(r,"end","inventory-hold")
        r.operation()
        r.db {
            assertTrue(CollectionDao["piece"].inventoryHold)
            assertFalse(CollectionCareWorkDao.all().single().ended!!.safeToRelease)
            assertEquals(1L,CollectionOperationLockDao.count())
            assertEquals(1,TransactionManager.current().exec("SELECT hits FROM care_writer_fault"){v->v.next();v.getInt(1)}!!)
        }
        assertDoesNotThrow {r.service.history("piece","museum")}
    }
    @org.junit.jupiter.api.Test fun `new inventory restriction still permits an actual unsafe end`()=assertUnsafeEnd()
    @Tag("postgres-concurrency") @org.junit.jupiter.api.Test
    fun `postgres new inventory restriction still permits an actual unsafe end`() {
        assertNotNull(System.getProperty("collection.test.pg.config"));assertUnsafeEnd()
    }
    private fun assertOpenForm(change:String)=fixture {r ->
        val original=r.service.detail("piece")["sourceRevision"] as String
        val value=inspection("",false).copy(planId=null)
        val sent=when(change) {
            "missing"->null;"blank"->"";"wrong-revision"->"0".repeat(64)
            else->{r.db {val c=CollectionDao["piece"];when(change){"name"->c.name="合成打开表单后订正";"nature"->c.nature=EnumCollectionNature.RELIC;"quantity"->c.quantity=2}};original}
        }
        val before=snapshot(r)
        val error=assertThrows(BadRequestException::class.java){r.service.inspect("piece",value,sent)}
        assertTrue(error.message!!.contains("检查表单版本"));assertEquals(before,snapshot(r))
        val refreshed=r.service.detail("piece")["sourceRevision"] as String
        val id=r.service.inspect("piece",value,refreshed)
        val settled=snapshot(r)
        assertEquals(id,r.service.inspect("piece",value));assertEquals(settled,snapshot(r))
        r.db {
            val archive=CollectionPermanentArchiveDao.all().single()
            assertFalse(archive.snapshot.contains("sourceRevision"),"Transport revision must not rewrite frozen check content")
            CollectionDao["piece"].name="合成后续名称订正"
        }
        val frozen=snapshot(r);assertEquals(id,r.service.inspect("piece",value,original));assertEquals(frozen,snapshot(r))
    }
    @ParameterizedTest @MethodSource("openFormCases")
    fun `new inspection rejects missing and stale open form source but replays frozen facts`(change:String)=assertOpenForm(change)
    @Tag("postgres-concurrency") @ParameterizedTest @MethodSource("openFormCases")
    fun `postgres new inspection rejects missing and stale open form source but replays frozen facts`(change:String) {
        assertNotNull(System.getProperty("collection.test.pg.config"));assertOpenForm(change)
    }
    @Tag("postgres-concurrency") @ParameterizedTest @MethodSource("commitCases")
    fun `postgres inspection rechecks open form after actual catalog lock commit or rollback`(commit:Boolean) {
        assertNotNull(System.getProperty("collection.test.pg.config"))
        fixture {r ->
            val source=r.source as PGSimpleDataSource
            val original=r.service.detail("piece")["sourceRevision"] as String
            val value=inspection("",false).copy(planId=null)
            val executor=Executors.newSingleThreadExecutor()
            try {source.connection.use {holder ->
                holder.autoCommit=false
                holder.createStatement().use {it.executeUpdate("UPDATE col_collection SET name='合成独立事务订正' WHERE id='piece'")}
                val future=executor.submit<String> {
                    r.login("operator")
                    try {r.service.inspect("piece",value,original)} finally {SecurityContextHolder.clearContext()}
                }
                try {
                    val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
                    var observed=false
                    while(System.nanoTime()<deadline && !observed) {
                        source.connection.use {observer ->observer.prepareStatement("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock' AND query LIKE '%col_collection%')").use {sql ->
                            sql.setString(1,source.applicationName);sql.executeQuery().use {v->v.next();observed=v.getBoolean(1)}
                        }}
                        if(!observed)Thread.sleep(20)
                    }
                    assertTrue(observed,"Must observe the actual collection row lock wait before resolving the owner transaction")
                    if(commit)holder.commit() else holder.rollback()
                    if(commit)assertInstanceOf(BadRequestException::class.java,assertThrows(ExecutionException::class.java){future.get(15,TimeUnit.SECONDS)}.cause)
                    else assertNotNull(future.get(15,TimeUnit.SECONDS))
                    r.db {
                        assertEquals(if(commit)0L else 1L,CollectionCareCheckDao.count())
                        assertEquals(if(commit)0L else 1L,CollectionPermanentArchiveDao.count())
                    }
                    if(commit)assertNotNull(r.service.inspect("piece",value,r.service.detail("piece")["sourceRevision"] as String))
                } finally {holder.rollback()}
            }} finally {executor.shutdownNow();assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS))}
        }
    }
}
