package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumLedgerStatEvent
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.aop.support.AopUtils
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Properties
import javax.sql.DataSource

/** Only original15-C11's five explicitly missing target-PG current-permission readers.
 * Reuses the accepted private fixture construction, never invokes any accepted test method.
 * Isolated synthetic seeds establish nonempty payloads, not real users, signatures or GUI acceptance.
 */
@Tag("postgres-concurrency")
class AnnualCurrentReadersPostgresTest {
    @TempDir lateinit var dir: Path
    @AfterEach fun clear() = SecurityContextHolder.clearContext()
    private class Runtime(val delegate: Any) {
        val c = property("C") as AnnotationConfigApplicationContext
        val tx = property("Tx") as TransactionTemplate
        private fun property(name: String): Any = delegate.javaClass.getDeclaredMethod("get$name").apply { isAccessible=true }.invoke(delegate)
        fun <T> db(block: () -> T): T = tx.execute { block() }
        fun permission(value: String) = invoke(delegate,"permission",arrayOf(value))
    }
    companion object {
        private fun invoke(owner: Any,name: String,vararg args: Any): Any? = try {
            owner.javaClass.declaredMethods.single {it.name==name}.apply {isAccessible=true}.invoke(owner,*args)
        } catch(e:InvocationTargetException) {throw e.targetException}
    }
    private fun fixture(body:(Any,Runtime,PGSimpleDataSource)->Unit) {
        require(System.getProperty("collection.test.pg.config")!=null)
        val owner=CollectionArchiveReadRuntimeTest().apply {dir=Files.createDirectory(this@AnnualCurrentReadersPostgresTest.dir.resolve("owned-local"))}
        val callback:(Any)->Unit={ delegate ->
            val r=Runtime(delegate)
            val schema=r.db {requireNotNull(TransactionManager.current().exec("SELECT current_schema()") {rs->assertTrue(rs.next());rs.getString(1)})}
            assertTrue(schema.startsWith("archive_read_"))
            val properties=Properties().apply {Files.newInputStream(Path.of(System.getProperty("collection.test.pg.config"))).use(::load)}
            val source=PGSimpleDataSource().apply {setURL(properties.getProperty("url"));user=properties.getProperty("user");password=properties.getProperty("password");currentSchema=schema;applicationName=schema}
            body(owner,r,source)
        }
        invoke(owner,"postgresFixture",callback)
    }
    private data class TableState(val rows:Long,val digest:String)
    private fun projection(source:DataSource,schema:String):Map<String,TableState> = source.connection.use {c->
        val tables=c.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname=? ORDER BY tablename").use {s->
            s.setString(1,schema);s.executeQuery().use {rs->buildList {while(rs.next())add(rs.getString(1))}}
        }
        tables.associateWith {name->c.createStatement().use {s->
            s.executeQuery("SELECT count(*),md5(coalesce(string_agg(to_jsonb(t)::text,'|' ORDER BY to_jsonb(t)::text),'')) FROM \"$schema\".\"$name\" t").use {rs->
                assertTrue(rs.next());TableState(rs.getLong(1),rs.getString(2))
            }
        }}
    }
    private fun localState(root:Path)=Files.list(root).use {stream->stream.sorted().toList().filter {Files.isRegularFile(it)}.associate {p->
        p.fileName.toString() to MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)).joinToString(""){"%02x".format(it)}
    }}
    private fun grants(source:DataSource):List<Pair<String,String>> = source.connection.use {c->c.createStatement().use {s->
        s.executeQuery("SELECT role_id,permission_id FROM sys_role_permission WHERE role_id IN (SELECT role_id FROM sys_role_user WHERE user_id='reader') ORDER BY role_id,permission_id").use {rs->
            buildList {while(rs.next())add(rs.getString(1) to rs.getString(2))}
        }
    }}
    private fun revoke(source:DataSource,expected:Int)=source.connection.use {c->
        assertTrue(c.autoCommit)
        c.createStatement().use {s->assertEquals(expected,s.executeUpdate("DELETE FROM sys_role_permission WHERE role_id IN (SELECT role_id FROM sys_role_user WHERE user_id='reader')"))}
    }
    private fun restore(source:DataSource,values:List<Pair<String,String>>)=source.connection.use {c->
        assertTrue(c.autoCommit)
        c.prepareStatement("INSERT INTO sys_role_permission(role_id,permission_id) VALUES (?,?)").use {s->
            values.forEach {(role,permission)->s.setString(1,role);s.setString(2,permission);assertEquals(1,s.executeUpdate())}
        }
    }
    @ParameterizedTest(name="15-C11 ordinary current PostgreSQL reader {0}")
    @ValueSource(strings=["annual_reports","annual_legacy","annual_stat","annual_dashboard","grading"])
    fun `target PostgreSQL readers use current ordinary duties and never reseal`(kind:String)=fixture {owner,r,source->
        val permission=if(kind=="grading")CollectionConst.APPRAISE else CollectionConst.STAT
        when(kind) {
            "annual_reports"->invoke(owner,"filingOriginal",r.delegate,"annual")
            "annual_legacy"->r.db {AnnualReportArchiveDao.new("DEV-SYNTHETIC-legacy") {
                orgId="museum";year=2025;statistician="DEV-SYNTHETIC reader";keeperDirector="DEV-SYNTHETIC keeper";museumLeader="DEV-SYNTHETIC director"
                reportAttachmentIds="proof";reportedBy="reader";reportedAt=LocalDateTime.of(2026,1,2,12,0)
            }}
            "grading"->invoke(owner,"gradingOriginal",r.delegate,"application")
            else->r.db {CollectionLedgerStatRecorder.record(CollectionDao["one"],EnumLedgerStatEvent.REGISTER,"DEV-SYNTHETIC-15-C11-register",LocalDateTime.of(2025,1,2,12,0))}
        }
        r.permission(permission)
        val actor=r.db {r.c.getBean(CollectionActorDirectory::class.java).requireActive("reader")}
        assertFalse(EnumRole.ADMIN.value in actor.roles);assertFalse(CollectionConst.CROSS_ORG in actor.permissions)
        assertEquals(setOf(permission),actor.permissions.toSet())
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(actor,null,actor.authorities)
        val originalAuthentication=SecurityContextHolder.getContext().authentication
        val bean:Any=when(kind) {
            "annual_reports"->r.c.getBean(AnnualReportEvidenceService::class.java)
            "annual_legacy"->r.c.getBean(AnnualReportArchiveService::class.java)
            "annual_stat"->r.c.getBean(CollectionLedgerStatisticsService::class.java)
            "annual_dashboard"->r.c.getBean(CollectionDashboardService::class.java)
            else->r.c.getBean(CollectionGradingService::class.java)
        }
        assertTrue(AopUtils.isAopProxy(bean))
        fun read():Any=when(kind) {
            "annual_reports"->(bean as AnnualReportEvidenceService).detail(2025).also {
                assertEquals("museum",it["orgId"]);assertEquals(1,(it["records"] as List<*>).size)
            }
            "annual_legacy"->requireNotNull((bean as AnnualReportArchiveService).get(2025)).also {
                assertEquals("DEV-SYNTHETIC-legacy",it.id);assertEquals("museum",it.orgId);assertEquals("proof",it.reportAttachmentIds)
            }
            "annual_stat"->(bean as CollectionLedgerStatisticsService).preview(2025).also {
                assertEquals("museum",it.orgId);assertEquals(1,it.newTotal);assertEquals(1,it.closingTotal)
            }
            "annual_dashboard"->(bean as CollectionDashboardService).annual(2025).also {
                assertEquals("museum",it.orgId);assertEquals(1,it.newTotal);assertEquals(1,it.closingTotal)
            }
            else->(bean as CollectionGradingService).detail("one").also {
                assertEquals("one",it["collectionId"]);assertEquals(1,(it["records"] as List<*>).size)
            }
        }
        val schema=requireNotNull(source.currentSchema)
        val qualifiedBefore=projection(source,schema)
        val fileBefore=localState((owner as CollectionArchiveReadRuntimeTest).dir)
        val first=requireNotNull(JsonUtils.toJson(read()))
        assertEquals(qualifiedBefore,projection(source,schema),"Positive reader must not write or reseal")
        val originalGrants=grants(source);assertEquals(1,originalGrants.size)
        revoke(source,originalGrants.size)
        val revokedBefore=projection(source,schema)
        assertSame(originalAuthentication,SecurityContextHolder.getContext().authentication)
        val rejection=assertThrows(BadRequestException::class.java) {read()}
        assertTrue(requireNotNull(rejection.message).contains("权限"),"Must be a current read-duty refusal, not a broken seed or file failure: ${rejection.message}")
        val revokedAfter=projection(source,schema)
        assertEquals(revokedBefore,revokedAfter,"Rejected reader must not return old payload or mutate any table")
        assertEquals(qualifiedBefore.filterKeys {it!="sys_role_permission"},revokedBefore.filterKeys {it!="sys_role_permission"})
        restore(source,originalGrants)
        assertEquals(first,JsonUtils.toJson(read()))
        val restored=projection(source,schema)
        assertEquals(qualifiedBefore,restored);assertEquals(fileBefore,localState(owner.dir));assertSame(originalAuthentication,SecurityContextHolder.getContext().authentication)
        fun rows(values:Map<String,TableState>)=values.mapValues {(_,v)->mapOf("rows" to v.rows,"digest" to v.digest)}
        println("15_C11_TARGET_PG_CURRENT_READ_RESULT "+JsonUtils.toJson(mapOf("kind" to kind,"schema" to schema,"ordinaryPermission" to permission,
            "sameAuthenticatedPrincipal" to true,"genuineSpringProxy" to true,"independentQualificationCommit" to true,"refusal" to rejection.message,
            "qualifiedBefore" to rows(qualifiedBefore),"revokedBefore" to rows(revokedBefore),"revokedAfter" to rows(revokedAfter),"restored" to rows(restored),
            "payloadSha256" to MessageDigest.getInstance("SHA-256").digest(first.toByteArray()).joinToString(""){"%02x".format(it)},"localFiles" to fileBefore,
            "businessWriteOrReseal" to false,"actualHttpOrGuiOrHumanSigning" to false,"seedQualificationCollaborator" to "Existing responsible-users mock only for seed creation; actual current actor directory for every reader")))
    }
}
