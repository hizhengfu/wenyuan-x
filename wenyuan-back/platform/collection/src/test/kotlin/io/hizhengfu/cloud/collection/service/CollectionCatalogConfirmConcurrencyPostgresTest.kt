package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileReadAccess
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.service.FileFolderService
import io.hizhengfu.cloud.sys.file.service.FileService
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.aop.support.AopUtils
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.sql.*
import java.time.LocalDate
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/** One actual target-PG race, not registration, original-file, human signature or GUI acceptance. */
@Tag("postgres-concurrency")
class CollectionCatalogConfirmConcurrencyPostgresTest {
    @TempDir lateinit var root: Path
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true) class Transactions
    private data class TableState(val rows: Long, val digest: String)
    private data class Trace(val marker: String, val pids: MutableList<Int> = CopyOnWriteArrayList(), val successfulSql: MutableList<String> = CopyOnWriteArrayList())
    private data class Outcome(val marker: String, val receipt: CollectionCatalogVo?, val failure: Throwable?, val trace: Trace)
    private val active = ThreadLocal<Trace>()
    private fun invoke(real: Any, method: java.lang.reflect.Method, args: Array<out Any?>): Any? =
        try { method.invoke(real, *args) } catch (e: InvocationTargetException) { throw e.targetException }
    private fun <T> proxy(type: Class<T>, action: (java.lang.reflect.Method, Array<out Any?>) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> action(method,args ?: emptyArray()) })
    private inner class ObservedSource(private val source: DataSource): DataSource by source {
        override fun getConnection() = observe(source.connection)
        override fun getConnection(username: String?, password: String?) = observe(source.getConnection(username,password))
        private fun observe(real: Connection): Connection {
            active.get()?.let { trace -> real.createStatement().use { s -> s.executeQuery("SELECT pg_backend_pid()").use { r -> r.next();trace.pids.add(r.getInt(1)) } } }
            return proxy(Connection::class.java) { method,args ->
                when(val value=invoke(real,method,args)) {
                    is CallableStatement -> statement(value,args.firstOrNull() as? String,CallableStatement::class.java)
                    is PreparedStatement -> statement(value,args.firstOrNull() as? String,PreparedStatement::class.java)
                    is Statement -> statement(value,null,Statement::class.java)
                    else -> value
                }
            }
        }
        private fun <T:Statement> statement(real:T,prepared:String?,type:Class<T>):T = proxy(type) { method,args ->
            val value=invoke(real,method,args)
            if(method.name in setOf("execute","executeQuery","executeUpdate","executeBatch","executeLargeUpdate","executeLargeBatch"))
                active.get()?.successfulSql?.add(prepared ?: args.firstOrNull() as? String ?: "")
            value
        }
    }
    private fun projection(source:DataSource,schema:String):Map<String,TableState> = source.connection.use { c ->
        val names=c.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname=? ORDER BY tablename").use { s ->s.setString(1,schema);s.executeQuery().use { r ->buildList {while(r.next())add(r.getString(1))} } }
        names.associateWith { table ->c.createStatement().use { s ->s.executeQuery("SELECT count(*),md5(coalesce(string_agg(to_jsonb(t)::text,'|' ORDER BY to_jsonb(t)::text),'')) FROM \"$schema\".\"$table\" t").use {r->r.next();TableState(r.getLong(1),r.getString(2))} } }
    }
    private fun actor() {
        val permissions=setOf(CollectionConst.CATALOG_EDIT,CollectionConst.SELECT)
        val u=SecurityUser(id="editor",name="DEV-SYNTHETIC editor",username="editor",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=permissions.map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(u,null,u.authorities)
    }
    @Test fun `two old confirmations waiting on actual PG lock create one submitted version`() {
        val props=Properties().apply {Path.of(requireNotNull(System.getProperty("collection.test.pg.config"))).toFile().inputStream().use(::load)}
        val schema="catalog_confirm_race_"+UUID.randomUUID().toString().replace("-","")
        val source=PGSimpleDataSource().apply {setURL(props.getProperty("url"));user=props.getProperty("user");password=props.getProperty("password");applicationName="C35-$schema"}
        source.connection.use {c->c.createStatement().use {it.execute("CREATE SCHEMA \"$schema\"")}}
        source.currentSchema=schema
        var result:Map<String,Any?>?=null
        try {
            val manager=SpringTransactionManager(ObservedSource(source),DatabaseConfig {})
            AnnotationConfigApplicationContext().use {c->
                c.beanFactory.registerSingleton("transactionManager",manager)
                c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
                c.beanFactory.registerSingleton("scope",CollectionScopeResolver("single","museum"))
                // These registration/grade collaborators are not invoked by draft save or submit; assert this explicitly.
                val grading=mock(CollectionGradingService::class.java);val accession=mock(AccessionReceiptService::class.java)
                c.beanFactory.registerSingleton("grading",grading);c.beanFactory.registerSingleton("accession",accession)
                val local=LocalFileObjectStorage(root.toString())
                c.beanFactory.registerSingleton("local",local)
                c.registerBean(FileService::class.java,java.util.function.Supplier {FileService(root.toString(),c.getBean(FileFolderService::class.java),local,fileReadAccess=c.getBean(FileReadAccess::class.java))})
                c.register(Transactions::class.java,FileReadAccess::class.java,FileFolderService::class.java,BusinessAttachmentBinder::class.java,CollectionCatalogService::class.java,CollectionOperationLockService::class.java)
                c.refresh();SpringContextHolder().setApplicationContext(c)
                val service=c.getBean(CollectionCatalogService::class.java);assertTrue(AopUtils.isAopProxy(service))
                val tx=TransactionTemplate(manager)
                fun <T> db(body:()->T):T=requireNotNull(tx.execute {body()})
                actor()
                db {
                    originalReaderSchema()
                    SchemaUtils.create(CollectionOrgs,Collections,CollectionCatalogs,CollectionCatalogVersions,CollectionLedgerLogs,CollectionLedgerStatEvents,CollectionCategories,Warehouses,CollectionMedias,CollectionAttachments,CollectionOperationLocks)
                    originalReader("editor",CollectionConst.CATALOG_EDIT);actorRole("editor","DEV_C35_READ",setOf(CollectionConst.SELECT))
                    CollectionOrgDao.new("museum"){name="DEV-SYNTHETIC isolated catalog museum";code="DEV-C35";systemGroupId="museum"}
                    CollectionCategoryDao.new("category"){name="DEV-SYNTHETIC category";parentId="0";parentIds="0,"}
                    CollectionDao.new("object"){orgId="museum";totalRegNo="DEV-C35";name="DEV-SYNTHETIC pending catalog";categoryId="category";era="DEV-SYNTHETIC";texture="DEV-SYNTHETIC";quantity=1;unit="件";this.source=EnumSource.OLD;level=EnumRelicLevel.GENERAL;completeness=EnumCompleteness.COMPLETE;acquireDate=LocalDate.of(2020,1,1);catalogStatus=EnumCatalogStatus.DRAFT;lifeStatus=EnumCollectionStatus.PENDING;nature=EnumCollectionNature.OTHER}.also(service::ensureCatalogFor)
                }
                val view=service.get("object");val oldHash=requireNotNull(view.stateHash)
                assertEquals(EnumCatalogStatus.DRAFT,view.status);assertTrue(view.canEdit)
                val before=projection(source,schema)
                val executor=Executors.newFixedThreadPool(2)
                val entered=CountDownLatch(2)
                val traces=listOf(Trace("DEV-SYNTHETIC winner candidate A"),Trace("DEV-SYNTHETIC winner candidate B"))
                val waits=mutableListOf<Map<String,Any>>()
                var blockerPid=0
                val outcomes:List<Outcome>
                try {
                    source.connection.use { blocker ->
                        blocker.autoCommit=false
                        blocker.createStatement().use { s ->s.executeQuery("SELECT pg_backend_pid()").use {r->r.next();blockerPid=r.getInt(1)};s.executeQuery("SELECT id FROM \"$schema\".\"${Collections.tableName}\" WHERE id='object' FOR UPDATE").use {r->assertTrue(r.next())} }
                        val jobs=traces.map { trace ->executor.submit(Callable {
                            actor();active.set(trace);entered.countDown()
                            try {val written=runCatching {service.saveConfirmed("object",view.content.copy(formalName=trace.marker),oldHash,true)};Outcome(trace.marker,written.getOrNull(),written.exceptionOrNull(),trace)}
                            finally {active.remove();SecurityContextHolder.clearContext()}
                        }) }
                        try {
                            assertTrue(entered.await(10,TimeUnit.SECONDS))
                            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
                            do {
                                // pg_stat_activity.query is truncated before FOR UPDATE for the wide main-table SELECT.
                                // Observe the actual table/lock now; require complete successful FOR UPDATE SQL after release below.
                                val current=source.connection.use {connection->connection.prepareStatement("SELECT pid,wait_event_type,wait_event,pg_blocking_pids(pid)::text FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock' AND lower(query) LIKE lower(?) ORDER BY pid").use {s->s.setString(1,"C35-$schema");s.setString(2,"%${Collections.tableName}%");s.executeQuery().use {r->buildList {while(r.next())add(mapOf("pid" to r.getInt(1),"waitEventType" to r.getString(2),"waitEvent" to r.getString(3),"blockingPids" to r.getString(4)))}}}}
                                if(current.size==2){waits.addAll(current);break}
                                Thread.sleep(10)
                            } while(System.nanoTime()<deadline)
                            if(waits.size!=2) {
                                val diagnostic=source.connection.use {connection->connection.prepareStatement("SELECT pid,state,wait_event_type,wait_event,query FROM pg_stat_activity WHERE application_name=? ORDER BY pid").use {s->s.setString(1,"C35-$schema");s.executeQuery().use {r->buildList {while(r.next())add(mapOf("pid" to r.getInt(1),"state" to r.getString(2),"waitEventType" to r.getString(3),"waitEvent" to r.getString(4),"query" to r.getString(5)))}}}}
                                println("CATALOG_CONFIRM_CONCURRENCY_SETUP_DIAGNOSTIC="+JsonUtils.toJson(mapOf("activity" to diagnostic,"requests" to jobs.mapIndexed {i,j->mapOf("done" to j.isDone,"successfulSql" to traces[i].successfulSql,"failure" to if(j.isDone)j.get().failure?.stackTraceToString() else null)})))
                            }
                            assertEquals(2,waits.size,"Both actual independent transactions must wait on the actual PG row lock")
                            blocker.commit()
                            outcomes=jobs.map {it.get(30,TimeUnit.SECONDS)}
                        } finally {if(!blocker.isClosed)blocker.rollback()}
                    }
                } finally {executor.shutdownNow();assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS))}
                val success=outcomes.single {it.receipt!=null};val refused=outcomes.single {it.failure!=null}
                assertTrue(refused.failure is BadRequestException,refused.failure?.stackTraceToString())
                assertEquals(EnumCatalogStatus.SUBMITTED,success.receipt!!.status);assertEquals(1,success.receipt.version)
                val final=service.get("object");assertEquals(success.marker,final.content.formalName);assertEquals(success.receipt.stateHash,final.stateHash)
                val versions=service.versions("object");assertEquals(1,versions.size);assertEquals(1,versions.single().version);assertEquals(success.marker,versions.single().content.formalName)
                val finalProjection=projection(source,schema)
                assertEquals(1L,finalProjection.getValue(CollectionCatalogVersions.tableName).rows-before.getValue(CollectionCatalogVersions.tableName).rows)
                fun dml(sql:String)=Regex("^\\s*(INSERT|UPDATE|DELETE)\\b",RegexOption.IGNORE_CASE).containsMatchIn(sql)
                assertEquals(emptyList<String>(),refused.trace.successfulSql.filter(::dml),"The refused transaction must not execute successful data mutations")
                assertTrue(success.trace.successfulSql.any {dml(it)&&it.contains(CollectionCatalogVersions.tableName)})
                assertEquals(1,success.trace.pids.distinct().size);assertEquals(1,refused.trace.pids.distinct().size);assertNotEquals(success.trace.pids.single(),refused.trace.pids.single())
                assertTrue(waits.all {it["pid"] in (success.trace.pids+refused.trace.pids)})
                assertTrue(outcomes.all {outcome->outcome.trace.successfulSql.any {it.contains(Collections.tableName,ignoreCase=true)&&it.contains("FOR UPDATE",ignoreCase=true)}})
                verifyNoInteractions(grading,accession)
                result=mapOf("schema" to schema,"before" to before,"final" to finalProjection,"actualConcurrentRequests" to 2,"actualLockWaits" to waits,"blockerPid" to blockerPid,"successfulMarker" to success.marker,"refusedMarker" to refused.marker,"failureClass" to refused.failure!!.javaClass.name,"refusal" to refused.failure.message,"oldStateHash" to oldHash,"finalStateHash" to final.stateHash,"exactSubmittedVersion" to 1,"versionCount" to 1,"winnerContentExact" to true,"loserSuccessfulDmlCount" to 0,"transactions" to outcomes.map {mapOf("marker" to it.marker,"pids" to it.trace.pids,"successfulSql" to it.trace.successfulSql,"success" to (it.receipt!=null))},"actualSpringProxy" to true,"actualPostgresDriverAndIndependentConnections" to true,"noAdmin" to true,"ordinaryPermissions" to listOf(CollectionConst.CATALOG_EDIT,CollectionConst.SELECT),"gradeAndRegistrationCollaboratorsNotCalled" to true,"noFileOrActualSignatureOrGuiClaim" to true)
            }
        } finally {
            active.remove();SecurityContextHolder.clearContext()
            source.connection.use {c->c.createStatement().use {it.execute("DROP SCHEMA \"$schema\" CASCADE")}}
        }
        val removed=source.connection.use {c->c.prepareStatement("SELECT NOT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname=?)").use {s->s.setString(1,schema);s.executeQuery().use {r->r.next();r.getBoolean(1)}}}
        assertTrue(removed)
        println("CATALOG_CONFIRM_CONCURRENCY_RESULT="+JsonUtils.toJson(requireNotNull(result)+mapOf("isolatedSchemaRemoved" to removed)))
    }
}
