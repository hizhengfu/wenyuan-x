package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import io.hizhengfu.cloud.collection.service.ArchivePostgresEvidenceSupport as P

/** 06-C07/12-C31: ordinary dual-museum actors, true PG lock ordering, real archive rows. */
class CollectionFragilityMuseumPostgresTest {
    private val at=LocalDateTime.now().minusDays(2).withNano(0)
    private class Runtime(val source:PGSimpleDataSource,val tx:TransactionTemplate,val scope:CollectionScopeResolver,
        val service:CollectionFragilityService,val archives:CollectionPermanentArchiveService) {
        fun <T> db(body:()->T):T=tx.execute {body()}
    }
    private val business=arrayOf<Table>(Collections,CollectionFragilityAssessments,CollectionFragileUseRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles)
    private fun assessment(revision:String)=FragilityAssessmentVo(sourceRevision=revision,assessedAt=at,
        fragileMaterial=false,materialAndCondition="DEV-SYNTHETIC材质",environmentRequirements="DEV-SYNTHETIC环境",
        storageProtection="DEV-SYNTHETIC保护",useRiskAssessment="DEV-SYNTHETIC风险",conservationUserId="conservation",keeperUserId="keeper",
        historyVerifiedFrom=at.minusDays(1),historyReviewFileIds="proof",assessmentFileIds="proof")
    private fun history()=FragileUseHistoryVo(EnumFragileUseKind.DISPLAY,at.minusDays(2),at.minusDays(1),"DEV-SYNTHETIC实际历史依据","DEV-SYNTHETIC历史展出记录","proof")
    private fun fixture(body:(Runtime)->Unit) {
        val path=System.getProperty("collection.test.pg.config") ?: error("Explicit isolated PG config required")
        val p=Properties().apply {Path.of(path).toFile().inputStream().use(::load)}
        val schema="fragility_museum_"+UUID.randomUUID().toString().replace("-","")
        val source=PGSimpleDataSource().apply {setURL(p.getProperty("url"));user=p.getProperty("user");password=p.getProperty("password")}
        source.connection.use {it.createStatement().use {s->s.execute("CREATE SCHEMA $schema")}}
        source.currentSchema=schema;source.applicationName=schema
        try {StaticApplicationContext().use {context->
            context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
            val manager=SpringTransactionManager(source,DatabaseConfig {})
            val scope=CollectionScopeResolver("multi","museum")
            val binder=mock(BusinessAttachmentBinder::class.java).also {b->`when`(b.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}}
            val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
            val original=CollectionFragilityService(scope,CollectionResponsibleUserService(CollectionActorDirectory(scope),scope),archives,CollectionOperationLockService())
            val factory=ProxyFactory(original).apply {isProxyTargetClass=true;addAdvice(TransactionInterceptor().apply {transactionManager=manager;transactionAttributeSource=AnnotationTransactionAttributeSource()})}
            val r=Runtime(source,TransactionTemplate(manager),scope,factory.proxy as CollectionFragilityService,archives)
            r.db {
                originalReaderSchema()
                SchemaUtils.create(CollectionOrgs,Collections,CollectionFragilityAssessments,CollectionFragileUsePlans,CollectionFragileUseRecords,
                    CollectionFragileUseAdjustments,CollectionFragileMonitorings,CollectionFragileAutomaticChecks,CollectionFragileMonitorRuntimes,
                    CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
                for(org in listOf("museum","other"))CollectionOrgDao.new(org) {name="DEV-SYNTHETIC $org";code=org;systemGroupId=org}
                for(id in listOf("reader","conservation","keeper")) {
                    originalReader(id,CollectionConst.CARE)
                    UserGroups.insert {it[userId]=id;it[groupId]="other"}
                }
                actorRole("conservation",CollectionConst.ROLE_CONSERVATION);actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD)
                CollectionDao.new("piece") {orgId="museum";name="DEV-SYNTHETIC piece";totalRegNo="M1"}
                FileDao.new("proof") {name="DEV-SYNTHETIC.pdf";this.path="proof";hash="proof";size=100;ext="pdf"}
            }
            val actor=r.db {CollectionActorDirectory(scope).requireActive("reader")}
            assertFalse(EnumRole.ADMIN.value in actor.roles)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(actor,null,actor.authorities)
            r.db {
                val row=CollectionFragilityAssessmentDao.new("former") {orgId="museum";collectionId="piece";version=7;catalog=CollectionCareEvidence.catalog(CollectionDao["piece"]);content=assessment("former")}
                archives.seal("museum",CollectionFragilityEvidence.BIZ,"former","ASSESSMENT",CollectionFragilityEvidence.assessmentValue(row),"proof")
            }
            try {body(r)} finally {SecurityContextHolder.clearContext()}
        }} finally {source.currentSchema="public";source.connection.use {it.createStatement().use {s->s.execute("DROP SCHEMA $schema CASCADE")}}}
    }
    private fun pause(r:Runtime)=r.db {
        TransactionManager.current().exec("CREATE FUNCTION pause_fragility_archive() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.phase='HISTORY' THEN PERFORM pg_advisory_xact_lock(7100484); END IF; RETURN NEW; END $$")
        TransactionManager.current().exec("CREATE TRIGGER pause_fragility_archive AFTER INSERT ON ${CollectionPermanentArchives.tableName} FOR EACH ROW EXECUTE FUNCTION pause_fragility_archive()")
    }
    private fun waiting(r:Runtime,pid:Int,table:String,advisory:Boolean=false):Boolean=r.source.connection.use {c->c.prepareStatement(
        "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock' AND query LIKE ? AND cardinality(pg_blocking_pids(pid))>0${if(advisory)" AND wait_event='advisory'" else ""})",
    ).use {q->q.setInt(1,pid);q.setString(2,"%$table%");q.executeQuery().use {row->row.next();row.getBoolean(1)}}}
    private fun awaitWait(r:Runtime,pid:AtomicInteger,table:String,advisory:Boolean=false) {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(System.nanoTime()<deadline) {if(pid.get()!=0 && waiting(r,pid.get(),table,advisory))return;Thread.sleep(20)}
        fail<Nothing>("Did not observe actual PG $table lock wait")
    }
    private fun receivingVersion(r:Runtime) {
        // Foreign malformed body cannot be parsed or used as the receiving museum's previous confirmation.
        r.db {TransactionManager.current().exec("UPDATE col_fragility_assessment SET content='\"invalid-former-private-content\"' WHERE id='former'")}
        val detail=r.service.detail("piece");assertEquals("other",detail["orgId"]);assertEquals(true,detail["canOperate"])
        assertTrue((detail["assessments"] as List<*>).isEmpty());assertTrue((detail["records"] as List<*>).isEmpty())
        val value=assessment(detail["sourceRevision"] as String)
        val principal=SecurityContextHolder.getContext().authentication
        val pids=listOf(AtomicInteger(),AtomicInteger());val pool=Executors.newFixedThreadPool(2)
        val id=try {r.source.connection.use {holder->
            holder.autoCommit=false
            holder.createStatement().use {it.execute("SELECT id FROM col_collection WHERE id='piece' FOR UPDATE")}
            try {
                val writers=pids.map {pid->pool.submit<String> {
                    SecurityContextHolder.getContext().authentication=principal
                    try {r.db {TransactionManager.current().exec("SELECT pg_backend_pid()") {row->row.next();pid.set(row.getInt(1))};r.service.assess("piece",value,"other")}}
                    finally {SecurityContextHolder.clearContext()}
                }}
                pids.forEach {awaitWait(r,it,"col_collection")}
                holder.commit()
                val ids=writers.map {it.get(20,TimeUnit.SECONDS)}
                assertEquals(ids[0],ids[1],"Two waiting submissions of one receiving confirmation create only one global version")
                ids[0]
            } finally {holder.rollback()}
        }} finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS))}
        r.db {
            assertEquals(8,CollectionFragilityAssessmentDao[id].version);assertEquals("other",CollectionFragilityAssessmentDao[id].orgId)
            assertEquals(2,CollectionFragilityAssessmentDao.all().count())
            assertEquals("museum",CollectionFragilityAssessments.selectAll().single {it[CollectionFragilityAssessments.id].value=="former"}[CollectionFragilityAssessments.orgId])
        }
    }
    @Tag("postgres-concurrency") @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `transfer first blocks old confirmation and preserves receiving global assessment version`(rollback:Boolean)=fixture {r->
        assertEquals("museum",r.service.detail("piece")["orgId"])
        val before=r.db {settledSafetySelectionState(*business)}
        val principal=SecurityContextHolder.getContext().authentication
        val pid=AtomicInteger();val pool=Executors.newSingleThreadExecutor()
        try {r.source.connection.use {holder->
            holder.autoCommit=false
            holder.createStatement().use {s->s.execute("SELECT id FROM col_collection WHERE id='piece' FOR UPDATE");assertEquals(1,s.executeUpdate("UPDATE col_collection SET org_id='other',total_reg_no='O1' WHERE id='piece'"))}
            try {
                val writer=pool.submit<Result<String>> {
                    SecurityContextHolder.getContext().authentication=principal
                    try {runCatching {r.db {TransactionManager.current().exec("SELECT pg_backend_pid()") {row->row.next();pid.set(row.getInt(1))};r.service.history("piece",history(),"museum")}}}
                    finally {SecurityContextHolder.clearContext()}
                }
                awaitWait(r,pid,"col_collection")
                if(rollback)holder.rollback() else holder.commit()
                val result=writer.get(20,TimeUnit.SECONDS)
                if(rollback)assertTrue(result.getOrThrow().isNotBlank()) else {
                    assertTrue(result.exceptionOrNull() is BadRequestException);assertTrue(result.exceptionOrNull()!!.message!!.contains("收藏单位已变化"))
                    r.db {assertEquals(0,CollectionFragileUseRecordDao.all().count());assertEquals(before[CollectionPermanentArchives.tableName],settledSafetySelectionState(CollectionPermanentArchives)[CollectionPermanentArchives.tableName])}
                    receivingVersion(r)
                }
            } finally {holder.rollback()}
        }} finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS))}
    }
    @Tag("postgres-concurrency") @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `archive first serializes museum transfer behind collection lock and retains old original`(rollback:Boolean)=fixture {r->
        pause(r)
        val principal=SecurityContextHolder.getContext().authentication
        val writerPid=AtomicInteger();val moverPid=AtomicInteger();val pool=Executors.newFixedThreadPool(2)
        try {r.source.connection.use {holder->
            holder.autoCommit=false;holder.createStatement().use {it.execute("SELECT pg_advisory_xact_lock(7100484)")}
            try {
                val writer=pool.submit<String> {
                    SecurityContextHolder.getContext().authentication=principal
                    try {r.db {TransactionManager.current().exec("SELECT pg_backend_pid()") {row->row.next();writerPid.set(row.getInt(1))};r.service.history("piece",history(),"museum")}}
                    finally {SecurityContextHolder.clearContext()}
                }
                awaitWait(r,writerPid,"col_permanent_archive",true)
                val mover=pool.submit<Unit> {r.source.connection.use {c->
                    c.autoCommit=false
                    try {c.createStatement().use {s->s.executeQuery("SELECT pg_backend_pid()").use {row->row.next();moverPid.set(row.getInt(1))};assertEquals(1,s.executeUpdate("UPDATE col_collection SET org_id='other',total_reg_no='O1' WHERE id='piece'"))};if(rollback)c.rollback() else c.commit()}
                    finally {c.rollback()}
                }}
                awaitWait(r,moverPid,"col_collection");assertFalse(mover.isDone)
                holder.commit()
                val saved=writer.get(20,TimeUnit.SECONDS);mover.get(20,TimeUnit.SECONDS)
                r.db {assertEquals("museum",CollectionFragileUseRecordDao[saved].orgId);CollectionFragilityEvidence.record(CollectionFragileUseRecordDao[saved])}
                if(!rollback)receivingVersion(r) else assertEquals("museum",r.service.detail("piece")["orgId"])
            } finally {holder.rollback()}
        }} finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS))}
    }
    @Tag("postgres-concurrency") @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `independent qualification loss at actual fragile archive insert rolls back all new records`(rollback:Boolean)=fixture {r->
        pause(r)
        val before=r.db {settledSafetySelectionState(*business)}
        val qualificationBefore=r.db {settledSafetySelectionState(RolePermissions)}
        val result=P.archiveChange(r.source,r.tx,7100484,"DELETE FROM sys_role_permission WHERE role_id='role:FILE_reader' AND permission_id='${CollectionConst.CARE}'",rollback) {r.service.history("piece",history(),"museum")}
        if(rollback) {assertTrue(result.getOrThrow().isNotBlank());assertEquals(qualificationBefore,r.db {settledSafetySelectionState(RolePermissions)})}
        else {assertTrue(result.exceptionOrNull() is BadRequestException,"Actual committed qualification-loss result: $result");assertEquals(before,r.db {settledSafetySelectionState(*business)});assertNotEquals(qualificationBefore,r.db {settledSafetySelectionState(RolePermissions)})}
    }
}
