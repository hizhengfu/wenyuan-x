package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.*
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.aop.support.AopUtils
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/** Original09-C05: only six missing initial CASE register independent-transaction cases.
 * Real Spring departure/archive services and PostgreSQL SQL, with real owned LOCAL input bytes.
 * The archive-file AFTER INSERT trigger only waits; another JDBC transaction changes qualification.
 * The binding adapter writes actual FileContents SQL but does not claim FileService/upload authorization.
 */
@Tag("postgres-concurrency")
class LeadershipRegisterAfterArchiveAuthorityPostgresTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate,val source:DataSource,val local:Path) {
        val departures=c.getBean(LeadershipDepartureService::class.java)
        
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun login(id:String) {
            val user=db {c.getBean(CollectionActorDirectory::class.java).requireActive(id)}
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        }
    }
    private val at=LocalDateTime.of(2026,8,1,9,0)
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun fixture(body:(Runtime)->Unit) {
        require(System.getProperty("collection.test.pg.config")!=null) {"Explicit private PostgreSQL config required"}
        departureTestDatabase(dir.resolve("unused-sqlite.db")) {source ->
            assertInstanceOf(PGSimpleDataSource::class.java,source)
            val manager=SpringTransactionManager(source,DatabaseConfig {})
            val local=java.nio.file.Files.createDirectory(dir.resolve("owned-local"))
            val binder=mock(BusinessAttachmentBinder::class.java)
            `when`(binder.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}
            doAnswer {call ->
                FileContents.insert {row -> row[fileId]=call.getArgument<String>(2);row[contentId]=call.getArgument<String>(0);row[aliasName]=call.getArgument<String>(1)}
                null
            }.`when`(binder).bindSingle(anyString(),anyString(),anyString())
            AnnotationConfigApplicationContext().use {c ->
                c.beanFactory.registerSingleton("transactionManager",manager)
                c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
                c.beanFactory.registerSingleton("scope",CollectionScopeResolver("multi","museum"))
                c.beanFactory.registerSingleton("binder",binder)
                c.beanFactory.registerSingleton("localStorage",LocalFileObjectStorage(local.toString()))
                c.register(Transactions::class.java,CollectionActorDirectory::class.java,CollectionResponsibleUserService::class.java,
                    CollectionPermanentArchiveService::class.java,io.hizhengfu.cloud.common.file.FileReadAccess::class.java,
                    LeadershipDepartureService::class.java,InventoryComplianceService::class.java)
                c.refresh();SpringContextHolder().setApplicationContext(c)
                val r=Runtime(c,TransactionTemplate(manager),source,local)
                assertTrue(AopUtils.isAopProxy(r.departures));assertTrue(AopUtils.isAopProxy(c.getBean(CollectionPermanentArchiveService::class.java)))
                val initial=SecurityUser(id="operator",name="DEV-SYNTHETIC operator",username="operator",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=emptyList(),enabled=true)
                SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(initial,null,initial.authorities)
                r.db {
                    SchemaUtils.create(Groups,Users,UserGroups,Roles,RoleUsers,Permissions,RolePermissions,CollectionOrgs,Collections,
                        Warehouses,Loans,Requisitions,CollectionReturns,InventoryBatches,Inventories,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FileContents)
                    for(id in listOf("museum","other")) {
                        GroupDao.new(id) {name="DEV-SYNTHETIC $id";type='1';parentId="0";parentIds="0,"}
                        CollectionOrgDao.new(id) {name="DEV-SYNTHETIC $id";code=id;systemGroupId=id;ownership=EnumOwnership.STATE}
                    }
                    for(id in listOf("operator","keeper","successor","former"))UserDao.new(id) {name="DEV-SYNTHETIC $id";loginName=id;password="unused";enabled=id!="former";groupId="museum"}
                    fun role(user:String,code:String,permission:String?=null) {
                        val role=RoleDao.new("role:$user:$code") {name=code;this.code=code;enabled=true}
                        RoleUsers.insert {it[userId]=user;it[roleId]=role.id.value}
                        if(permission!=null) {val p=PermissionDao.new {name=permission;this.code=permission};RolePermissions.insert {it[roleId]=role.id;it[permissionId]=p.id}}
                    }
                    role("operator","MANAGER",CollectionConst.STORAGE);role("keeper",CollectionConst.ROLE_STORAGE_HEAD);role("successor",CollectionConst.ROLE_DIRECTOR)
                    WarehouseDao.new("store") {orgId="museum";name="DEV-SYNTHETIC store";sceneType=EnumLocationScene.STORAGE}
                    CollectionDao.new("one") {orgId="museum";totalRegNo="DEV-SYNTHETIC-ONE";name="DEV-SYNTHETIC collection";nature=EnumCollectionNature.RELIC;categoryId="category";level=EnumRelicLevel.GENERAL;lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="store";registeredAt=at.minusMonths(1)}
                    InventoryBatchDao.new("batch") {orgId="museum";batchName="DEV-SYNTHETIC isolated one-item departure inventory"}
                    for(id in listOf("appointment","scope","signature")) {
                        val bytes="DEV-SYNTHETIC 09-C05 REGISTER $id; isolated software fixture only, no physical handover or human signature.\n".toByteArray()
                        java.nio.file.Files.write(local.resolve("$id.txt"),bytes)
                        FileDao.new(id) {name="DEV-SYNTHETIC-$id.txt";path="/$id.txt";hash=MessageDigest.getInstance("MD5").digest(bytes).joinToString("") {"%02x".format(it)};size=bytes.size.toLong();ext="txt"}
                    }
                }
                r.login("operator");body(r)
            }
        }
    }
    private fun request()=LeadershipDepartureRequest(EnumLeadershipPost.PRINCIPAL,"former",at.minusYears(1),at.plusDays(5),"appointment","appointment","successor","appointment","keeper","DEV-SYNTHETIC isolated full responsibility","scope")
    private data class TableState(val rows:Long,val digest:String)
    private fun projection(source:DataSource,schema:String):Map<String,TableState> = source.connection.use {c ->
        val tables=c.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname=? ORDER BY tablename").use {s ->s.setString(1,schema);s.executeQuery().use {rows ->buildList {while(rows.next())add(rows.getString(1))}}}
        tables.associateWith {table ->c.createStatement().use {s ->s.executeQuery("SELECT count(*),md5(coalesce(string_agg(to_jsonb(t)::text,'|' ORDER BY to_jsonb(t)::text),'')) FROM \"$schema\".\"$table\" t").use {rows ->assertTrue(rows.next());TableState(rows.getLong(1),rows.getString(2))}}}
    }
    private fun localState(root:Path)=java.nio.file.Files.list(root).use {s ->s.sorted().toList().associate {p ->p.fileName.toString() to MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(p)).joinToString("") {"%02x".format(it)}}}
    @ParameterizedTest(name="{0}: {1}, independent qualification rollback={2}")
    @CsvSource("REGISTER,actor-account,false","REGISTER,actor-account,true","REGISTER,keeper-account,false","REGISTER,keeper-account,true","REGISTER,successor-role,false","REGISTER,successor-role,true")
    fun `initial CASE register rechecks actor and selected officers after actual archive file INSERT`(stage:String,change:String,rollback:Boolean)=fixture {r ->
        val actorId="operator";r.login(actorId)
        val actor=r.db {r.c.getBean(CollectionActorDirectory::class.java).requireActive(actorId)}
        assertFalse("ADMIN" in actor.roles);assertFalse(CollectionConst.CROSS_ORG in actor.permissions)
        val schema=requireNotNull((r.source as PGSimpleDataSource).currentSchema);assertTrue(schema.startsWith("departure_lock_"))
        val before=projection(r.source,schema);assertEquals(19,before.size)
        val physicalBefore=localState(r.local)
        val phase="CASE_%";val lockKey=System.nanoTime() and Long.MAX_VALUE
        val marker="09C05CASE.after_actual_archive_file.$change"
        r.source.connection.use {c ->c.createStatement().use {s ->
            s.execute("CREATE FUNCTION after_case_original_insert_fn() RETURNS trigger AS \$\$ DECLARE saved_phase text; saved_biz text; BEGIN SELECT phase,biz_type INTO saved_phase,saved_biz FROM col_permanent_archive WHERE id=NEW.archive_id; IF saved_biz='${LeadershipDepartureEvidence.BIZ}' AND saved_phase LIKE '$phase' AND NEW.file_id='scope' THEN PERFORM set_config('application_name','$marker',false); PERFORM pg_advisory_xact_lock($lockKey); END IF; RETURN NEW; END; \$\$ LANGUAGE plpgsql")
            s.execute("CREATE TRIGGER after_case_original_insert AFTER INSERT ON col_permanent_archive_file FOR EACH ROW EXECUTE FUNCTION after_case_original_insert_fn()")
        }}
        val selected=when(change) {"keeper-account"->"keeper";"successor-role"->"successor";else->actorId}
        val mutation=when(change) {
            "successor-role"->"UPDATE sys_role SET enabled=false WHERE id='role:$selected:${CollectionConst.ROLE_DIRECTOR}'"
            else->"UPDATE sys_user SET enabled=false WHERE id='$selected'"
        }
        val restoration=when(change) {
            "successor-role"->"UPDATE sys_role SET enabled=true WHERE id='role:$selected:${CollectionConst.ROLE_DIRECTOR}'"
            else->"UPDATE sys_user SET enabled=true WHERE id='$selected'"
        }
        val authorityTable=if(change=="successor-role")Roles.tableName else Users.tableName
        val pool=Executors.newSingleThreadExecutor();val pid=AtomicInteger();val auth=SecurityContextHolder.getContext().authentication
        var observed:Map<String,Any?>?=null
        var outcome:Result<String>?=null
        try {r.source.connection.use {holder ->
            holder.autoCommit=false
            val holderPid=holder.createStatement().use {s ->s.executeQuery("SELECT pg_backend_pid(),pg_advisory_lock($lockKey)").use {rows ->rows.next();rows.getInt(1)}}
            try {
                val writing=pool.submit<Result<String>> {
                    SecurityContextHolder.getContext().authentication=auth
                    try {runCatching {r.db {TransactionManager.current().exec("SELECT pg_backend_pid()") {rows->rows.next();pid.set(rows.getInt(1))};r.departures.register("museum",request())}}}
                    finally {SecurityContextHolder.clearContext()}
                }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12)
                while(observed==null && System.nanoTime()<deadline) {
                    if(writing.isDone)fail<Nothing>("Actual archive-file SQL did not reach the AFTER INSERT barrier: ${writing.get().exceptionOrNull()}")
                    if(pid.get()!=0)r.source.connection.use {observer ->observer.prepareStatement("SELECT wait_event_type,wait_event,application_name,pg_blocking_pids(pid),pg_backend_pid() FROM pg_stat_activity WHERE pid=?").use {s ->
                        s.setInt(1,pid.get());s.executeQuery().use {rows ->
                            if(rows.next() && rows.getString(1)=="Lock" && rows.getString(2)=="advisory") {
                                val blocking=(rows.getArray(4).array as Array<*>).map {(it as Number).toInt()}
                                assertTrue(holderPid in blocking);assertEquals(marker,rows.getString(3))
                                assertNotEquals(pid.get(),rows.getInt(5));assertNotEquals(holderPid,rows.getInt(5))
                                observed=mapOf("writerPid" to pid.get(),"holderPid" to holderPid,"observerPid" to rows.getInt(5),"waitEventType" to "Lock","waitEvent" to "advisory","marker" to marker,"blockingPids" to blocking)
                            }
                        }
                    }}
                    if(observed==null)Thread.sleep(20)
                }
                assertNotNull(observed,"A fresh third connection must prove the real AFTER archive-file INSERT wait")
                assertEquals(before,projection(r.source,schema),"Another transaction must not see the uncommitted new snapshot/ref")
                holder.createStatement().use {assertEquals(1,it.executeUpdate(mutation))}
                if(rollback)holder.rollback() else holder.commit()
                val qualified=r.source.connection.use {c ->c.createStatement().use {s ->
                    val query=if(change=="successor-role")"SELECT enabled FROM sys_role WHERE id='role:$selected:${CollectionConst.ROLE_DIRECTOR}'" else "SELECT enabled FROM sys_user WHERE id='$selected'"
                    s.executeQuery(query).use {rows->rows.next();rows.getBoolean(1)}
                }}
                assertEquals(rollback,qualified,"The distinct authority transaction outcome must be visible before writer release")
                holder.createStatement().use {s ->s.executeQuery("SELECT pg_advisory_unlock($lockKey)").use {rows->rows.next();assertTrue(rows.getBoolean(1))}}
                outcome=writing.get(25,TimeUnit.SECONDS)
            } finally {holder.rollback();holder.createStatement().use {it.execute("SELECT pg_advisory_unlock($lockKey)")}}
        }} finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS))}
        val result=requireNotNull(outcome);val after=projection(r.source,schema)
        var verifiedOriginals:List<Map<String,Any>> = emptyList()
        if(!rollback) {
            assertInstanceOf(BadRequestException::class.java,result.exceptionOrNull(),"Committed current qualification loss must reject initial CASE registration")
            assertEquals(before-authorityTable,after-authorityTable,"All18 non-authority full-row projections must roll back the new original, references and bindings")
            assertEquals(before.getValue(CollectionPermanentArchives.tableName),after.getValue(CollectionPermanentArchives.tableName))
            assertEquals(before.getValue(CollectionPermanentArchiveFiles.tableName),after.getValue(CollectionPermanentArchiveFiles.tableName))
            assertEquals(before.getValue(FileContents.tableName),after.getValue(FileContents.tableName))
            r.source.connection.use {c ->c.createStatement().use {assertEquals(1,it.executeUpdate(restoration))}}
            assertEquals(before,projection(r.source,schema),"Restoring only actual fixture qualification must recover the complete19-table baseline")
        } else {
            assertTrue(result.isSuccess,"Rolled-back qualification change must allow the legitimate current CASE registration: ${result.exceptionOrNull()}")
            val changedTables=setOf(CollectionPermanentArchives.tableName,CollectionPermanentArchiveFiles.tableName,FileContents.tableName)
            assertEquals(before.filterKeys {it !in changedTables},after.filterKeys {it !in changedTables},"A lawful initial CASE registration may only append its archive, original metadata and binding")
            changedTables.forEach {table ->assertEquals(before.getValue(table).rows+(if(table==CollectionPermanentArchives.tableName)1L else 2L),after.getValue(table).rows,table)}
            r.db {
                val archive=CollectionPermanentArchiveDao.all().single {it.phase=="CASE_${result.getOrThrow()}" && it.bizType==LeadershipDepartureEvidence.BIZ}
                assertEquals("museum",archive.orgId)
                assertEquals(CollectionCareEvidence.digest(archive.snapshot),archive.digest)
                val originals=CollectionPermanentArchiveFileDao.all().filter {it.archiveId==archive.id.value}
                assertEquals(setOf("appointment","scope"),originals.map {it.fileId}.toSet());assertEquals(2,originals.size)
                verifiedOriginals=originals.map {file ->
                    val saved=JsonUtils.mapper.readTree(file.metadata)
                    assertEquals(physicalBefore.getValue("${file.fileId}.txt"),saved.path("_archiveContent").path("sha256").asString())
                    assertEquals(java.nio.file.Files.size(r.local.resolve("${file.fileId}.txt")),saved.path("_archiveContent").path("size").asLong())
                    mapOf("archiveId" to archive.id.value,"fileId" to file.fileId,"sha256" to saved.path("_archiveContent").path("sha256").asString(),"size" to saved.path("_archiveContent").path("size").asLong(),"phase" to archive.phase,"snapshotDigest" to archive.digest)
                }
                assertFalse(UserDao["former"].enabled==true,"Historical departing identity must stay disabled")
            }
        }
        assertEquals(physicalBefore,localState(r.local),"Already uploaded LOCAL input originals are not deleted or modified by database rollback")
        println("09C05_REGISTER_AFTER_ARCHIVE_RESULT "+JsonUtils.toJson(mapOf("stage" to stage,"selectedQualifier" to selected,"change" to change,"qualificationRollback" to rollback,"schema" to schema,"actualWait" to observed,"declaredTables" to before.size,"beforeProjection" to before.mapValues {(_,v)->mapOf("rows" to v.rows,"digest" to v.digest)},"afterProjection" to after.mapValues {(_,v)->mapOf("rows" to v.rows,"digest" to v.digest)},"localInputSha256" to physicalBefore,"verifiedArchivedOriginals" to verifiedOriginals,"businessRollback" to !rollback,"localFilesUnchanged" to true,"serviceResult" to if(result.isSuccess)"success" else result.exceptionOrNull()!!.javaClass.simpleName)))
    }
}
