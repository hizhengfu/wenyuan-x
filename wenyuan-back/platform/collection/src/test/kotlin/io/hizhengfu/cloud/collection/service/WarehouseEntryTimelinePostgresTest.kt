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
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path
import java.time.LocalDateTime

import org.postgresql.ds.PGSimpleDataSource
import org.springframework.jdbc.datasource.AbstractDataSource
import java.sql.Connection
import java.util.Properties
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.lang.reflect.Proxy
import java.lang.reflect.InvocationTargetException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

@Tag("postgres-concurrency")
class WarehouseEntryTimelinePostgresTest {
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private val at=LocalDateTime.of(2026,9,1,12,0)
    private val user=SecurityUser(id="verifier",name="核验人员",username="verifier",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.WAREHOUSE_ENTRY_VERIFY)),enabled=true)
    private class Runtime(val service:WarehouseEntryTimelineService,val tx:TransactionTemplate,val binder:BusinessAttachmentBinder,val directory:CollectionActorDirectory,val scope:CollectionScopeResolver,val source:PGSimpleDataSource,val schema:String,val archiveSelects:AtomicInteger) {
        fun <T> db(block:()->T):T=tx.execute {block()}
        fun awaitLock(second:Future<*>) {
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8)
            while(System.nanoTime()<deadline) {
                if(second.isDone)fail<Unit>("Second operation completed before the first released its lock: ${second.get()}")
                val waiting=source.connection.use {c->c.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')").use {s->s.setString(1,schema+"-second");s.executeQuery().use {r->r.next();r.getBoolean(1)}}}
                if(waiting)return
                Thread.sleep(20)
            }
            fail<Unit>("No observed database lock wait")
        }
    }
    private fun fixture(body:(Runtime)->Unit) {
        val properties=Properties().apply {Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load)}
        val schema="timeline_"+UUID.randomUUID().toString().replace("-","")
        val source=PGSimpleDataSource().apply {setURL(properties.getProperty("url"));user=properties.getProperty("user");password=properties.getProperty("password");currentSchema=schema}
        source.connection.use {c->c.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
        val archiveSelects=AtomicInteger()
        val named=object:AbstractDataSource() {
            private fun name(c:Connection):Connection {
                c.prepareStatement("SELECT set_config('application_name',?,false)").use {s->s.setString(1,schema+"-"+Thread.currentThread().name);s.execute()}
                return Proxy.newProxyInstance(Connection::class.java.classLoader,arrayOf(Connection::class.java)) {_,method,args->
                    if(method.name=="prepareStatement") {
                        val sql=args?.firstOrNull() as? String
                        if(sql!=null && sql.trimStart().startsWith("SELECT",true) && sql.contains("col_permanent_archive",true))archiveSelects.incrementAndGet()
                    }
                    try {method.invoke(c,*(args ?: emptyArray()))} catch (e:InvocationTargetException){throw e.targetException}
                } as Connection
            }
            override fun getConnection()=name(source.connection)
            override fun getConnection(username:String,password:String)=name(source.getConnection(username,password))
        }
        val manager=SpringTransactionManager(named,DatabaseConfig {})
        val scope=mock(CollectionScopeResolver::class.java)
        `when`(scope.currentOrgId()).thenReturn("museum")
        `when`(scope.currentReadableOrgIds(any() ?: user,anyString())).thenReturn(setOf("museum"))
        `when`(scope.currentWritableOrgIds(any() ?: user)).thenReturn(setOf("museum"))
        `when`(scope.readableOrgIds()).thenReturn(setOf("museum"))
        val directory=mock(CollectionActorDirectory::class.java);`when`(directory.requireActive("verifier")).thenReturn(user)
        val binder=mock(BusinessAttachmentBinder::class.java);`when`(binder.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        try {AnnotationConfigApplicationContext().use {context->
            context.beanFactory.registerSingleton("transactionManager",manager);context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            context.beanFactory.registerSingleton("scope",scope);context.beanFactory.registerSingleton("directory",directory);context.beanFactory.registerSingleton("binder",binder)
            context.beanFactory.registerSingleton("readAccess",unprotectedArchiveReadAccessProvider().getIfAvailable()!!)
            context.register(Transactions::class.java,CollectionPermanentArchiveService::class.java,WarehouseEntryTimelineService::class.java)
            context.refresh();SpringContextHolder().setApplicationContext(context)
            val r=Runtime(context.getBean(WarehouseEntryTimelineService::class.java),TransactionTemplate(manager),binder,directory,scope,source,schema,archiveSelects)
            r.db {
                SchemaUtils.create(CollectionOrgs,WarehouseEntryApplications,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
                CollectionOrgDao.new("museum"){name="本馆";code="M"}
                FileDao.new("proof"){name="模拟核查材料";path="proof";hash="proof";size=1;ext="pdf"}
                WarehouseEntryApplicationDao.new("visit"){orgId="museum";applicationNo="IN-1";applicantId="operator";entrantIds="operator";warehouseIds="store";purpose="实际在库办理";plannedStart=at.minusHours(2);plannedEnd=at.plusHours(2);actualEntryAt=at.minusHours(1);actualExitAt=at.minusMinutes(1)}
                val attendance=WarehouseAttendanceVo(at.minusHours(1),setOf("operator"),identityChecked=true,continuousAccompaniment=true,signedRecordFileIds="original")
                original("diary",WarehouseDiaryService.BIZ,"log","RECORDED_1",mapOf("entries" to WarehouseDiaryEntriesVo(listOf(WarehouseDiaryVisitVo("visit","IN-1","实际工作",attendance,null,at.minusHours(1),at.plusHours(2),exitedAt=null)))))
            }
            body(r)
        }} finally {SecurityContextHolder.clearContext();source.connection.use {c->c.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}}
    }
    private fun original(id:String,biz:String,bizId:String,phase:String,value:Any,org:String="museum") {
        val text=JsonUtils.toJson(value)!!
        CollectionPermanentArchiveDao.new(id){orgId=org;bizType=biz;this.bizId=bizId;this.phase=phase;snapshot=text;digest=CollectionCareEvidence.digest(text)}
    }
    private fun operation(id:String,biz:String=CollectionConst.BIZ_CARE,operator:String="operator",time:LocalDateTime=at,org:String="museum") {
        val start=mapOf("onSiteWarehouseLogId" to "log","operatorUserId" to operator,"startedAt" to time)
        original(id,biz,id,"STARTED",if(biz==CollectionConst.BIZ_CARE)mapOf("started" to start) else start,org)
    }
    private fun review(view:WarehouseEntryTimelineView)=WarehouseEntryTimelineReviewVo("review-key-001",view.baseline,at.plusHours(1),true,"已逐项核对出入库、日记与操作签署，时间差异尚待原经办核实","联系原经办确认实际时刻并保留补充证明，不覆盖原记录","proof")
    @Test fun `maximum accepted request key stays within actual PostgreSQL phase column`()=fixture {r->
        r.db {operation("op")}
        val value=review(r.service.get("visit")).copy(requestKey="k".repeat(60))
        val id=r.service.review("visit",value);assertEquals(id,r.service.review("visit",value))
        r.db {assertTrue(CollectionPermanentArchiveDao[id].phase.length<=64)}
    }
    private fun worker(pool:ExecutorService,name:String,block:()->String):Future<Result<String>> = pool.submit<Result<String>> {
        Thread.currentThread().name=name
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        try {runCatching(block)} finally {SecurityContextHolder.clearContext()}
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans=[false,true])
    fun `review waits for departure change and honors commit or rollback`(rollback:Boolean)=fixture {r->
        r.db {operation("op")};val value=review(r.service.get("visit"));val pool=Executors.newFixedThreadPool(2)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=worker(pool,"first"){r.db {
            CollectionLocationMutation.acquire("museum")
            WarehouseEntryApplications.update({WarehouseEntryApplications.id eq "visit"}){it[actualExitAt]=at.minusMinutes(2)}
            entered.countDown();check(release.await(15,TimeUnit.SECONDS));if(rollback)throw IllegalStateException("synthetic rollback");"changed"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=worker(pool,"second"){r.db {WarehouseEntryApplicationDao["visit"].actualExitAt;r.service.review("visit",value)}}
            r.awaitLock(second);release.countDown();val one=first.get(15,TimeUnit.SECONDS);val two=second.get(15,TimeUnit.SECONDS)
            if(rollback){assertTrue(one.exceptionOrNull() is IllegalStateException);org.junit.jupiter.api.Assertions.assertNotNull(two.getOrThrow());assertEquals(1,r.service.get("visit").reviews.size)}
            else {one.getOrThrow();assertTrue(two.exceptionOrNull() is BadRequestException);assertTrue(r.service.get("visit").reviews.isEmpty())}
        } finally {release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS))}
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans=[false,true])
    fun `concurrent duplicate review is idempotent and cannot replace signed content`(different:Boolean)=fixture {r->
        r.db {operation("op")};val value=review(r.service.get("visit"));val pool=Executors.newFixedThreadPool(2)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        doAnswer {if(Thread.currentThread().name=="first"){entered.countDown();check(release.await(15,TimeUnit.SECONDS))};null}.`when`(r.binder).bindSingle(anyString(),anyString(),anyString())
        val first=worker(pool,"first"){r.service.review("visit",value)}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=worker(pool,"second"){r.service.review("visit",if(different)value.copy(statement="并发替换核查情况") else value)}
            r.awaitLock(second);release.countDown();val id=first.get(15,TimeUnit.SECONDS).getOrThrow();val result=second.get(15,TimeUnit.SECONDS)
            if(different)assertTrue(result.exceptionOrNull() is BadRequestException) else assertEquals(id,result.getOrThrow())
            assertEquals(1,r.service.get("visit").reviews.size)
        } finally {release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS))}
    }
    private fun newDiaryVersion(r:Runtime) {r.db {
        CollectionLocationMutation.acquire("museum")
        original("diary-new",WarehouseDiaryService.BIZ,"log","RECORDED_2",JsonUtils.mapper.readTree(CollectionPermanentArchiveDao["diary"].snapshot))
    }}
    @Test fun `new diary signed before review commits invalidates the old baseline`()=fixture {r->
        r.db {operation("op")};val value=review(r.service.get("visit"));val pool=Executors.newFixedThreadPool(2)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=worker(pool,"first"){r.db {newDiaryVersion(r);entered.countDown();check(release.await(15,TimeUnit.SECONDS));"diary"}}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS));val second=worker(pool,"second"){r.service.review("visit",value)}
            r.awaitLock(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            assertTrue(second.get(15,TimeUnit.SECONDS).exceptionOrNull() is BadRequestException)
            assertTrue(r.service.get("visit").reviews.isEmpty());assertEquals(2,r.service.get("visit").diaryEvidence.size)
        } finally {release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS))}
    }
    @Test fun `later diary waits for review and makes its preserved baseline visibly outdated`()=fixture {r->
        r.db {operation("op")};val initial=r.service.get("visit");val value=review(initial);val pool=Executors.newFixedThreadPool(2)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        doAnswer {if(Thread.currentThread().name=="first"){entered.countDown();check(release.await(15,TimeUnit.SECONDS))};null}.`when`(r.binder).bindSingle(anyString(),anyString(),anyString())
        val first=worker(pool,"first"){r.service.review("visit",value)}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS));val second=worker(pool,"second"){newDiaryVersion(r);"diary"}
            r.awaitLock(second);release.countDown();val id=first.get(15,TimeUnit.SECONDS).getOrThrow();second.get(15,TimeUnit.SECONDS).getOrThrow()
            val current=r.service.get("visit");assertEquals(false,current.reviews.single()["baselineCurrent"]);assertEquals(2,current.diaryEvidence.size)
            r.db {val original=JsonUtils.mapper.readTree(CollectionPermanentArchiveDao[id].snapshot);assertEquals(1,original.path("diaryEvidence").size());assertEquals(initial.baseline,original.path("content").path("baseline").asString())}
        } finally {release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS))}
    }

    @Test fun `four thousand unrelated originals are scanned in batches without per record queries`()=fixture {r->
        r.db {
            repeat(2000) {i->
                operation("op-filler-${i.toString().padStart(4,'0')}",operator="unrelated")
                original("diary-filler-${i.toString().padStart(4,'0')}",WarehouseDiaryService.BIZ,"unrelated-$i","RECORDED_1",mapOf("entries" to WarehouseDiaryEntriesVo()))
            }
            operation("zz-target")
        }
        r.archiveSelects.set(0);val start=System.nanoTime();val view=r.service.get("visit");val elapsed=(System.nanoTime()-start)/1_000_000
        assertEquals(listOf("zz-target"),view.conflicts.map {it.archiveId});assertTrue(view.evidenceErrors.isEmpty())
        assertTrue(r.archiveSelects.get()<=30,"Unexpected per-record archive query growth: ${r.archiveSelects.get()}")
        println("TIMELINE_QUERY_OBSERVATION "+JsonUtils.toJson(mapOf("unrelatedArchives" to 4000,"elapsedMillis" to elapsed,"archiveSelects" to r.archiveSelects.get(),"batchSize" to 200,"conflicts" to view.conflicts.size)))
    }

}
