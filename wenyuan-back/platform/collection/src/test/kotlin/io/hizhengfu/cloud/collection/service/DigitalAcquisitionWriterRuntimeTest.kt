package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileObjectStorage
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
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
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/** Real directory and SQL-trigger revocation inside a real Spring business transaction. */
class DigitalAcquisitionWriterRuntimeTest {
    @TempDir lateinit var dir: Path
    private val at=LocalDateTime.now().minusHours(8).withNano(0)
    private val tables=arrayOf(CollectionOrgs,Collections,Reproductions,DigitalAcquisitionPlans,DigitalAcquisitionResources,
        DigitalAcquisitionFiles,DigitalAcquisitionFileAccesses,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
        CollectionOperationLocks,Files,Users,Groups,Roles,RoleUsers,RolePermissions,Permissions)
    private class Runtime(val tx:TransactionTemplate,val service:DigitalAcquisitionService,val scope:CollectionScopeResolver,
        val binder:BusinessAttachmentBinder,val postgres:Boolean,val source:DataSource) {
        lateinit var plan:DigitalAcquisitionPlanVo
        lateinit var planId:String
        lateinit var started:DigitalAcquisitionStartVo
        lateinit var captured:DigitalCaptureVo
        lateinit var processed:DigitalResourceStageVo
        lateinit var completed:DigitalAcquisitionCompletionVo
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun login(id:String) {
            val actor=db {CollectionActorDirectory(scope).requireActive(id)}
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(actor,null,actor.authorities)
        }
    }

    private fun fixture(body:(Runtime)->Unit) {
        val config=System.getProperty("collection.test.pg.config")
        val schema="digital_writer_"+UUID.randomUUID().toString().replace("-","")
        val postgres=config?.let {path->
            val p=Properties().apply {Path.of(path).toFile().inputStream().use(::load)}
            PGSimpleDataSource().apply {setURL(p.getProperty("url"));user=p.getProperty("user");password=p.getProperty("password")}.also {source->
                source.connection.use {it.createStatement().use {sql->sql.execute("CREATE SCHEMA $schema")}}
                source.currentSchema=schema
            }
        }
        val source:DataSource=postgres ?: SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("writer.db")}"}
        val manager=SpringTransactionManager(source,DatabaseConfig {})
        val scope=CollectionScopeResolver("single","museum")
        val binder=mock(BusinessAttachmentBinder::class.java).also {b->`when`(b.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}}
        val users=mock(CollectionResponsibleUserService::class.java)
        val storage=mock(FileObjectStorage::class.java)
        val blobs=mapOf("raw" to "RAW".toByteArray(),"processed" to "PROCESSED".toByteArray(),"organized" to "ORGANIZED".toByteArray(),"stored" to "STORED".toByteArray())
        `when`(storage.openStream(anyString(),anyString(),isNull(),isNull())).thenAnswer {blobs.getValue(it.getArgument(0)).inputStream()}
        val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
        val original=DigitalAcquisitionService(scope,users,archives,mock(CollectionOriginalAccessService::class.java),CollectionOperationLockService(),storage)
        val factory=ProxyFactory(original).apply {
            isProxyTargetClass=true
            addAdvice(TransactionInterceptor().apply {transactionManager=manager;transactionAttributeSource=AnnotationTransactionAttributeSource()})
        }
        try {StaticApplicationContext().use {context->
            context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
            val r=Runtime(TransactionTemplate(manager),factory.proxy as DigitalAcquisitionService,scope,binder,postgres!=null,source)
            r.db {
                SchemaUtils.create(CollectionOrgs,Collections,Reproductions,DigitalAcquisitionPlans,DigitalAcquisitionResources,
                    DigitalAcquisitionFiles,DigitalAcquisitionFileAccesses,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
                    CollectionOperationLocks,Files,FlowInstances,FlowInstanceLogs)
                originalReaderSchema();originalReader("operator",CollectionConst.STORAGE);originalReader("other-operator",CollectionConst.STORAGE)
                CollectionOrgDao.new("museum"){name="本馆";code="M";systemGroupId="museum"}
                CollectionOrgDao.new("other"){name="其他馆";code="O";systemGroupId="other"}
                for(id in listOf("piece","other-piece"))CollectionDao.new(id){orgId="museum";name=id;totalRegNo=id;registeredAt=at.minusMonths(1)
                    catalogStatus=EnumCatalogStatus.APPROVED;nature=EnumCollectionNature.OTHER;lifeStatus=EnumCollectionStatus.IN_STORE}
                ReproductionDao.new("rep"){orgId="museum";collectionId="piece";repType=EnumRepType.SCAN_3D;purpose="合成数字采集";repDate=at.toLocalDate()}
                FileDao.new("proof"){name="合成签署.txt";path="proof";hash="proof";size=10;ext="txt"}
                blobs.forEach {(id,bytes)->FileDao.new(id){name="$id.bin";path=id;hash=id;size=bytes.size.toLong();ext="bin"}}
            }
            r.login("operator")
            try {body(r)} finally {SecurityContextHolder.clearContext()}
        }} finally {
            postgres?.connection?.use {it.createStatement().use {sql->sql.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }

    private fun prepare(r:Runtime,kind:String)=r.db {
        r.plan=DigitalAcquisitionPlanVo(r.service.detail("rep")["sourceRevision"] as String,at,"department","information","keeper",
            "合成扫描","合成工作室",at.plusMinutes(5),at.plusHours(1),"环境","防护","加工","整理","备份","权限","合成内部",
            "proof","proof","proof","proof","proof")
        if(kind=="plan")return@db
        r.planId=r.service.savePlan("rep",r.plan)
        val parent=ReproductionDao["rep"]
        if(kind=="submit")return@db
        r.service.submit(parent);parent.flowInstanceId="instance";parent.flowStatus=EnumFlowStatus.AUDIT
        listOf("information","keeper").forEachIndexed {i,node->FlowInstanceLogDao.new {instanceId="instance";nodeKey=node;taskId="task-$node"
            action="APPROVE";operator=node;comment="合成本人确认";createTime=at.plusMinutes(i+1L)}}
        if(kind=="confirm")return@db
        r.service.confirm(parent);parent.flowStatus=EnumFlowStatus.PASS
        r.started=DigitalAcquisitionStartVo(r.planId,at.plusMinutes(10),"operator","req",null,true,true,"合成控制","proof","proof")
        if(kind=="start")return@db
        r.service.start("rep",r.started)
        r.captured=DigitalCaptureVo(at.plusMinutes(45),"operator","合成参数",true,"合成完好","proof",setOf("raw"),"proof")
        if(kind=="capture")return@db
        r.service.capture("rep",r.captured)
        val raw=DigitalAcquisitionResourceDao.all().single {it.content.stage==EnumDigitalResourceStage.RAW}.id.value
        r.processed=DigitalResourceStageVo(EnumDigitalResourceStage.PROCESSED,at.plusHours(1),"operator",setOf(raw),setOf("processed"),"合成加工","合成参数","proof")
        if(kind=="resource")return@db
        val processed=r.service.resource("rep",r.processed)
        val organized=r.service.resource("rep",r.processed.copy(stage=EnumDigitalResourceStage.ORGANIZED,occurredAt=at.plusHours(2),parentResourceIds=setOf(processed),fileIds=setOf("organized")))
        val stored=r.service.resource("rep",r.processed.copy(stage=EnumDigitalResourceStage.STORED,occurredAt=at.plusHours(3),parentResourceIds=setOf(organized),fileIds=setOf("stored"),
            storageLocation="主存储",backupLocation="独立备份",restorationVerifiedAt=at.plusHours(3),accessControlsVerified=true,securityCheckFileIds="proof"))
        r.completed=DigitalAcquisitionCompletionVo(at.plusHours(4),"information","keeper",setOf(stored),"合成共同核查","proof")
    }
    private fun operation(r:Runtime,kind:String) {
        when(kind) {
            "plan"->r.service.savePlan("rep",r.plan)
            "submit"->r.db {r.service.submit(ReproductionDao["rep"])}
            "confirm"->r.db {r.service.confirm(ReproductionDao["rep"])}
            "start"->r.service.start("rep",r.started)
            "capture"->r.service.capture("rep",r.captured)
            "resource"->r.service.resource("rep",r.processed)
            "complete"->r.service.complete("rep",r.completed)
            else->error(kind)
        }
    }
    private fun trigger(r:Runtime,kind:String,statement:String)=r.db {
        val condition=when(kind) {"plan"->"NEW.phase = 'PLAN'";"submit"->"NEW.phase = 'SUBMITTED'";"confirm"->"NEW.phase LIKE 'APPROVED_%'"
            "start"->"NEW.phase = 'STARTED'";"capture"->"NEW.phase = 'CAPTURED'";"resource"->"NEW.phase LIKE 'RESOURCE_%'";else->"NEW.phase = 'COMPLETED'"}
        val target="${CollectionPermanentArchives.tableName}"
        if(r.postgres) {
            TransactionManager.current().exec("CREATE FUNCTION mutate_digital_writer() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.biz_type='${DigitalAcquisitionService.BIZ}' AND $condition THEN $statement; END IF; RETURN NEW; END $$")
            TransactionManager.current().exec("CREATE TRIGGER mutate_digital_writer AFTER INSERT ON $target FOR EACH ROW EXECUTE FUNCTION mutate_digital_writer()")
        } else TransactionManager.current().exec("CREATE TRIGGER mutate_digital_writer AFTER INSERT ON $target WHEN NEW.biz_type='${DigitalAcquisitionService.BIZ}' AND $condition BEGIN $statement; END")
    }
    private fun dropTrigger(r:Runtime)=r.db {
        TransactionManager.current().exec("DROP TRIGGER mutate_digital_writer"+(if(r.postgres)" ON ${CollectionPermanentArchives.tableName}" else ""))
        if(r.postgres)TransactionManager.current().exec("DROP FUNCTION mutate_digital_writer()")
    }
    private fun snapshot(r:Runtime)=r.db {settledSafetySelectionState(*tables)}

    @ParameterizedTest
    @CsvSource("plan,permission","submit,permission","confirm,permission","start,permission","capture,permission","resource,permission","complete,permission",
        "plan,account","submit,account","confirm,account","start,account","capture,account","resource,account","complete,account",
        "plan,museum","submit,museum","confirm,museum","start,museum","capture,museum","resource,museum","complete,museum")
    fun `actual archive SQL revocation rolls back every digital write entry then allows restored retry`(kind:String,change:String)=fixture {r->
        prepare(r,kind)
        val statement=when(change) {
            "permission"->"DELETE FROM ${RolePermissions.tableName} WHERE role_id='role:FILE_operator' AND permission_id='${CollectionConst.STORAGE}'"
            "account"->"UPDATE ${Users.tableName} SET enabled="+(if(r.postgres)"false" else "0")+" WHERE id='operator'"
            else->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
        }
        trigger(r,kind,statement);val before=snapshot(r)
        assertThrows(BadRequestException::class.java){operation(r,kind)}
        assertEquals(before,snapshot(r));dropTrigger(r)
        assertDoesNotThrow {operation(r,kind)}
    }

    @Test fun `actual parent identity change during plan SQL rolls back source and archive`()=fixture {r->
        prepare(r,"plan");trigger(r,"plan","UPDATE ${Reproductions.tableName} SET collection_id='other-piece' WHERE id='rep'")
        val before=snapshot(r);assertThrows(BadRequestException::class.java){operation(r,"plan")};assertEquals(before,snapshot(r))
        dropTrigger(r);assertDoesNotThrow {operation(r,"plan")}
    }

    @Test fun `principal change during signing cannot attribute the first writer record to another active writer`()=fixture {r->
        prepare(r,"plan");val before=snapshot(r)
        `when`(r.binder.resolveSingle("proof")).thenAnswer {r.login("other-operator");"proof"}
        assertThrows(BadRequestException::class.java){operation(r,"plan")};assertEquals(before,snapshot(r))
        `when`(r.binder.resolveSingle("proof")).thenReturn("proof");r.login("operator")
        assertDoesNotThrow {operation(r,"plan")}
    }

    @Tag("postgres-concurrency")
    @ParameterizedTest
    @CsvSource("permission,false","permission,true","role,false","role,true","account,false","account,true",
        "group,false","group,true","museum,false","museum,true")
    fun `idempotent start rechecks current authority after actual collection lock wait`(change:String,rollback:Boolean)=fixture {r->
        org.junit.jupiter.api.Assumptions.assumeTrue(r.postgres,"真实行锁等待仅在隔离PostgreSQL运行")
        prepare(r,"capture")
        val businessTables=tables.filter {it !in setOf(Users,Groups,Roles,RoleUsers,RolePermissions,Permissions,CollectionOrgs)}.toTypedArray()
        val before=r.db {settledSafetySelectionState(*businessTables)}
        val principal=SecurityContextHolder.getContext().authentication
        val pid=AtomicInteger();val pool=Executors.newSingleThreadExecutor()
        try {r.source.connection.use {holder->
            holder.autoCommit=false
            holder.createStatement().use {sql->sql.executeQuery("SELECT id FROM ${Collections.tableName} WHERE id='piece' FOR UPDATE").use {assertTrue(it.next())}}
            try {
                val writing=pool.submit<Result<String>> {
                    SecurityContextHolder.getContext().authentication=principal
                    try {runCatching {r.db {
                        TransactionManager.current().exec("SELECT pg_backend_pid()") {rows->rows.next();pid.set(rows.getInt(1))}
                        r.service.start("rep",r.started)
                    }}} finally {SecurityContextHolder.clearContext()}
                }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
                var waiting=false
                while(System.nanoTime()<deadline && !waiting) {
                    if(writing.isDone)fail<Nothing>("数字采集请求未等待藏品行锁：${writing.get().exceptionOrNull()}")
                    if(pid.get()!=0)r.source.connection.use {observer->observer.prepareStatement(
                        "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock' AND pg_backend_pid()<>pid AND query LIKE '%col_collection%' AND cardinality(pg_blocking_pids(pid))>0)").use {sql->
                        sql.setInt(1,pid.get());sql.executeQuery().use {rows->rows.next();waiting=rows.getBoolean(1)}
                    }}
                    if(!waiting)Thread.sleep(20)
                }
                assertTrue(waiting,"须观察到请求实际等待PostgreSQL藏品行锁")
                val mutation=when(change) {
                    "permission"->"DELETE FROM ${RolePermissions.tableName} WHERE role_id='role:FILE_operator' AND permission_id='${CollectionConst.STORAGE}'"
                    "role"->"DELETE FROM ${RoleUsers.tableName} WHERE user_id='operator' AND role_id='role:FILE_operator'"
                    "account"->"UPDATE ${Users.tableName} SET enabled=false WHERE id='operator'"
                    "group"->"UPDATE ${Users.tableName} SET group_id='other' WHERE id='operator'"
                    else->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
                }
                holder.createStatement().use {assertEquals(1,it.executeUpdate(mutation))}
                if(rollback)holder.rollback() else holder.commit()
                val result=writing.get(20,TimeUnit.SECONDS)
                if(rollback)assertEquals(r.planId,result.getOrThrow())
                else assertTrue(result.exceptionOrNull() is BadRequestException,"当前资格变化提交后须拒绝，实际$result")
            } finally {holder.rollback()}
        }} finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS))}
        assertEquals(before,r.db {settledSafetySelectionState(*businessTables)},"重复请求不应改写业务、原件或占用")
        r.db {
            UserDao["operator"].enabled=true;UserDao["operator"].groupId="museum";CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.ENABLED
            actorRole("operator","FILE_operator",setOf(CollectionConst.STORAGE))
        }
        assertEquals(r.planId,r.service.start("rep",r.started))
        assertEquals(before,r.db {settledSafetySelectionState(*businessTables)})
    }
}
