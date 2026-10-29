package io.hizhengfu.cloud.collection.service

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.*
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/** Reuse the established fixture, not its test methods; each invocation owns a fresh PG schema. */
internal object ArchivePostgresEvidenceSupport {
    @Suppress("UNCHECKED_CAST")
    fun <T> field(target:Any,name:String):T = target.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(target) as T
    @Suppress("UNCHECKED_CAST")
    fun <T> call(target:Any,name:String,vararg args:Any?):T {
        val method=target.javaClass.declaredMethods.single {it.name==name && it.parameterCount==args.size}.apply {isAccessible=true}
        return try {method.invoke(target,*args) as T} catch(ex:InvocationTargetException) {throw ex.targetException}
    }
    @Suppress("UNCHECKED_CAST")
    fun <T> db(runtime:Any,body:()->T):T = field<TransactionTemplate>(runtime,"tx").execute {body()} as T

    /** Both lock wait and SQL archive point are observed from an independent connection. */
    fun archiveChange(source:DataSource,tx:TransactionTemplate,key:Long,sql:String,rollback:Boolean,operation:()->String):Result<String> {
        val principal=SecurityContextHolder.getContext().authentication
        val pid=AtomicInteger();val pool=Executors.newSingleThreadExecutor()
        try {source.connection.use {holder->
            holder.autoCommit=false
            holder.createStatement().use {it.execute("SELECT pg_advisory_xact_lock($key)")}
            try {
                val writing=pool.submit<Result<String>> {
                    SecurityContextHolder.getContext().authentication=principal
                    try {runCatching {tx.execute {
                        TransactionManager.current().exec("SELECT pg_backend_pid()") {row->row.next();pid.set(row.getInt(1))}
                        operation()
                    }!!}} finally {SecurityContextHolder.clearContext()}
                }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);var waiting=false
                while(System.nanoTime()<deadline && !waiting) {
                    if(writing.isDone)fail<Nothing>("Archive INSERT did not reach the intended pause: ${writing.get().exceptionOrNull()}")
                    if(pid.get()!=0)source.connection.use {observer->observer.prepareStatement(
                        "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock' AND wait_event='advisory' AND query LIKE '%col_permanent_archive%' AND cardinality(pg_blocking_pids(pid))>0)",
                    ).use {q->q.setInt(1,pid.get());q.executeQuery().use {row->row.next();waiting=row.getBoolean(1)}}}
                    if(!waiting)Thread.sleep(20)
                }
                assertTrue(waiting,"Actual PostgreSQL archive INSERT advisory wait must be observed")
                holder.createStatement().use {it.execute("SET LOCAL statement_timeout='5s'");assertEquals(1,it.executeUpdate(sql))}
                if(rollback)holder.rollback() else holder.commit()
                return writing.get(20,TimeUnit.SECONDS)
            } finally {holder.rollback()}
        }} finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS))}
    }
}
