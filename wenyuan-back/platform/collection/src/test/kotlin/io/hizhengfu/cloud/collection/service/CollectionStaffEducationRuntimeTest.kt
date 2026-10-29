package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

class CollectionStaffEducationRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate,val failBinding:AtomicBoolean) {
        val service=c.getBean(CollectionStaffEducationService::class.java)
        val files=c.getBean(CollectionStaffEducationFileReadGuard::class.java)
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun login(id:String) {
            val user=db {c.getBean(CollectionActorDirectory::class.java).requireActive(id)}
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        }
    }
    private val at=LocalDateTime.of(2026,8,1,9,0)
    private fun plan(kind:EnumStaffEducationKind=EnumStaffEducationKind.POST_TRAINING)=StaffEducationPlan(kind,"保管岗位技能","本馆纸质藏品保管","装具取放与状态记录",at,"needs")
    private fun create(key:String="request",user:String="person")=StaffEducationCreate(key,user,employmentFileIds="employment",plan=plan())
    private fun activity()=StaffEducationParticipation("实际培训单位","装具取放",at.plusDays(1),at.plusDays(1).plusMinutes(1),1,"完成实际取放与状态记录练习",EnumStaffEducationOutcome.COMPLETED,"attendance")
    private fun revision(version:Int,p:StaffEducationPlan=plan(),a:StaffEducationParticipation?=activity())=StaffEducationRevision(version,StaffEducationContent(p,a),"据实补充实际参加原件","correction")
    private val confirmation=StaffEducationConfirmationVo(true,"本人核对课程、人员与本项岗位需求相符","signature")
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun fixture(body:(Runtime)->Unit) {
        val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("education.db")}"},DatabaseConfig {})
        val binder=mock(BusinessAttachmentBinder::class.java);val fail=AtomicBoolean(false)
        `when`(binder.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}
        doAnswer {if(fail.get())throw IllegalStateException("simulated archive binding failure");null}.`when`(binder).bindSingle(anyString(),anyString(),anyString())
        AnnotationConfigApplicationContext().use {c ->
            c.beanFactory.registerSingleton("transactionManager",manager);c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("scope",CollectionScopeResolver("multi","museum"));c.beanFactory.registerSingleton("binder",binder)
            c.register(Transactions::class.java,CollectionActorDirectory::class.java,StaffEducationAccess::class.java,CollectionPermanentArchiveService::class.java,CollectionStaffEducationService::class.java,CollectionStaffEducationFileReadGuard::class.java,io.hizhengfu.cloud.common.file.FileReadAccess::class.java)
            c.refresh();SpringContextHolder().setApplicationContext(c);val r=Runtime(c,TransactionTemplate(manager),fail)
            r.db {
                actorSchema();SchemaUtils.create(CollectionOrgs,Warehouses,CollectionStaffEducations,CollectionStaffEducationVersions,CollectionStaffEducationFileAccesses,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
                for(id in listOf("museum","other")){GroupDao.new(id){name=id;type='1';parentId="0";parentIds="0,"};CollectionOrgDao.new(id){name=id;code=id;systemGroupId=id}}
                for(id in listOf("manager","person","other-person","head","admin","foreign","former"))UserDao.new(id){name=id;loginName=id;password="unused";enabled=id!="former";groupId=if(id=="foreign")"other" else "museum"}
                actorRole("manager","EDUCATION",setOf(CollectionConst.STAFF_EDUCATION_MANAGE))
                actorRole("person",CollectionConst.ROLE_KEEPER);actorRole("other-person",CollectionConst.ROLE_KEEPER)
                actorRole("head",CollectionConst.ROLE_STORAGE_HEAD);actorRole("admin","ADMIN",setOf("ADMIN"));actorRole("foreign","FOREIGN",setOf(CollectionConst.STAFF_EDUCATION_MANAGE))
                listOf("needs","employment","attendance","signature","correction").forEach {key->FileDao.new(key){name="$key.pdf";path="/userfiles/education/$key.pdf";hash=key;ext="pdf";size=10}}
            }
            r.login("manager");body(r)
        }
    }
    private val tables=listOf(CollectionStaffEducations,CollectionStaffEducationVersions,CollectionStaffEducationFileAccesses,CollectionPermanentArchives,CollectionPermanentArchiveFiles)
    private fun state(r:Runtime)=r.db {tables.map {t->t.selectAll().map {row->t.columns.map {row[it]}}.sortedBy {it.toString()}}}
    @Test fun `education metadata batch preserves archive fallback moved names and current identity`()=fixture {r->
        val id=r.service.create("museum",create())
        val original=io.hizhengfu.cloud.common.file.FileMetadataLocation("/userfiles/education/employment.pdf")
        val moved=io.hizhengfu.cloud.common.file.FileMetadataLocation("/userfiles/education/moved.pdf")
        val free=(0 until 126).map {io.hizhengfu.cloud.common.file.FileMetadataLocation("unrelated-$it.pdf")}
        val locations=listOf(original,moved)+free
        r.db {FileDao["employment"].path=moved.path;CollectionStaffEducationFileAccesses.deleteAll()}
        var queries=0
        val first=r.db {
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().addLogger(object:SqlLogger {
                override fun log(context:org.jetbrains.exposed.v1.core.statements.StatementContext,transaction:Transaction) {
                    val sql=context.sql(transaction)
                    if(sql.trimStart().startsWith("SELECT",true) &&
                        (sql.contains(CollectionStaffEducationFileAccesses.tableName,true) || sql.contains(CollectionPermanentArchiveFiles.tableName,true)))queries++
                }
            })
            r.files.checkMetadataReadableMany(locations)
        }
        assertEquals(2,queries,"128 positions must share index and frozen archive source queries")
        listOf(original,moved).forEach {assertEquals(true,first.getValue(it).getOrThrow())}
        free.forEach {assertEquals(false,first.getValue(it).getOrThrow())}
        r.db {RolePermissions.deleteAll()}
        val revoked=r.files.checkMetadataReadableMany(locations)
        listOf(original,moved).forEach {assertTrue(revoked.getValue(it).isFailure)}
        free.forEach {assertEquals(false,revoked.getValue(it).getOrThrow())}
        r.db {actorRole("manager","RESTORED_EDUCATION",setOf(CollectionConst.STAFF_EDUCATION_VIEW))}
        assertEquals(true,r.files.checkMetadataReadableMany(locations).getValue(original).getOrThrow())
        r.db {CollectionStaffEducationDao[id].userId="other-person"}
        assertTrue(r.files.checkMetadataReadableMany(locations).getValue(original).isFailure)
        r.db {CollectionStaffEducationDao[id].userId="person";CollectionPermanentArchiveFileDao.all().first().metadata="{"}
        assertTrue(r.files.checkMetadataReadableMany(locations).values.all {it.isFailure})
    }
    @Test fun `education lists declare current all or self scope without reauthentication`()=fixture {r ->
        val own=r.service.create("museum",create())
        val other=r.service.create("museum",create("other-need","other-person"))
        r.db {actorRole("person","EDUCATION_VIEW",setOf(CollectionConst.STAFF_EDUCATION_VIEW))}
        r.login("person")
        val authentication=SecurityContextHolder.getContext().authentication
        fun check(all:Boolean) {
            val page=r.service.list("museum",pageSize=0)
            val people=r.service.people("museum")
            assertEquals(all,page["canViewAll"])
            assertEquals(all,people["canViewAll"])
            assertEquals(false,page["canCreate"])
            @Suppress("UNCHECKED_CAST") val entries=page["list"] as List<Map<String,Any?>>
            assertEquals(if(all)setOf(own,other) else setOf(own),entries.map {it["id"]}.toSet())
            @Suppress("UNCHECKED_CAST") val roster=people["people"] as List<Map<String,Any?>>
            assertEquals(if(all)setOf("person","other-person","head") else setOf("person"),roster.map {it["id"]}.toSet())
            assertSame(authentication,SecurityContextHolder.getContext().authentication)
        }
        check(true)
        r.db {RolePermissions.deleteAll()}
        check(false)
        assertThrows(BadRequestException::class.java) {r.service.list("museum","other-person")}
        assertThrows(BadRequestException::class.java) {r.service.detail(other)}
        assertEquals(own,r.service.detail(own)["id"])
        r.db {UserDao["person"].enabled=false}
        assertThrows(BadRequestException::class.java) {r.service.people("museum")}
        assertThrows(BadRequestException::class.java) {r.service.list("museum")}
        r.db {UserDao["person"].enabled=true;actorRole("person","EDUCATION_VIEW_RESTORED",setOf(CollectionConst.STAFF_EDUCATION_VIEW))}
        check(true)
    }
    @Test fun `education original requires current identity archive and file` ()=fixture {r ->
        val id=r.service.create("museum",create())
        val archive=r.db {CollectionPermanentArchiveDao.find {CollectionPermanentArchives.bizId eq id}.first {it.phase=="ORIGINAL"}.id.value}
        r.service.requireArchivedOriginal(id,archive,"employment")
        assertThrows(BadRequestException::class.java){r.service.requireArchivedOriginal(id,"missing","employment")}
        assertThrows(BadRequestException::class.java){r.service.requireArchivedOriginal(id,archive,"missing")}
        r.db {RolePermissions.deleteAll()}
        assertThrows(BadRequestException::class.java){r.service.requireArchivedOriginal(id,archive,"employment")}
    }
    @Test fun `changed original bytes invalidate education history and prevent a new version`()=fixture {r ->
        val root=dir.resolve("sealed-originals")
        java.nio.file.Files.createDirectories(root)
        for (fileId in listOf("employment","needs","attendance","correction")) {
            java.nio.file.Files.writeString(root.resolve(fileId),"ORIGINAL")
            r.db {FileDao[fileId].apply {path=fileId;size=8}}
        }
        val archives=CollectionPermanentArchiveService(
            r.c.getBean(BusinessAttachmentBinder::class.java),
            r.c.getBean(CollectionScopeResolver::class.java),
            LocalFileObjectStorage(root.toString()),unprotectedArchiveReadAccessProvider())
        val service=CollectionStaffEducationService(
            r.c.getBean(StaffEducationAccess::class.java),
            r.c.getBean(CollectionScopeResolver::class.java),
            r.c.getBean(CollectionActorDirectory::class.java),archives)
        val id=r.db {service.create("museum",create())}
        r.db {service.revise(id,revision(1))}
        assertEquals("RECORDED",r.db {service.detail(id)["state"]})
        val before=state(r)
        for (fileId in listOf("employment","attendance")) {
            val file=root.resolve(fileId)
            val timestamp=java.nio.file.Files.getLastModifiedTime(file)
            java.nio.file.Files.writeString(file,"CHANGED!")
            java.nio.file.Files.setLastModifiedTime(file,timestamp)
            assertEquals("EVIDENCE_REVIEW_REQUIRED",r.db {service.detail(id)["state"]},fileId)
            assertThrows(BadRequestException::class.java) {r.db {service.revise(id,revision(2))}}
            assertEquals(before,state(r),fileId)
            java.nio.file.Files.writeString(file,"ORIGINAL")
            java.nio.file.Files.setLastModifiedTime(file,timestamp)
            assertEquals("RECORDED",r.db {service.detail(id)["state"]},fileId)
        }
    }
    @Test fun `foreign education version cannot be returned as readable history`()=fixture {r ->
        val id=r.service.create("museum",create())
        r.db {val v=CollectionStaffEducationVersionDao.all().single();v.orgId="other"}
        val value=r.service.detail(id)
        assertTrue((value["versions"] as List<*>).isEmpty())
        assertTrue((value["archives"] as List<*>).isEmpty())
    }
    @Test fun `current cross grant reads education originals despite old session museum`()=fixture {r ->
        val id=r.service.create("museum",create());r.login("foreign")
        r.db {actorRole("foreign","CURRENT_CROSS",setOf(CollectionConst.CROSS_ORG))}
        assertEquals(id,r.service.detail(id)["id"])
        r.db {RolePermissions.deleteAll()}
        assertThrows(BadRequestException::class.java){r.service.detail(id)}
    }

    @Test fun `misassigned education person cannot obtain original files through self access`()=fixture {r ->
        val id=r.service.create("museum",create());r.db {CollectionStaffEducationDao[id].userId="other-person"}
        r.login("other-person")
        val value=r.service.detail(id)
        Assertions.assertNull(value["original"]);Assertions.assertNull(value["personName"])
        assertTrue((value["versions"] as List<*>).isEmpty())
        assertThrows(BadRequestException::class.java){r.files.requireReadable("/userfiles/education/employment.pdf","LOCAL")}
    }
    @Test fun `every education file reference must agree on the owning museum`()=fixture {r ->
        r.service.create("museum",create())
        r.db {CollectionPermanentArchiveDao.find {CollectionPermanentArchives.phase eq "ORIGINAL"}.single().orgId="other"}
        assertThrows(BadRequestException::class.java){r.files.requireReadable("/userfiles/education/employment.pdf","LOCAL")}
    }
    @Test fun `single configuration governs education lists context actions and protected files`()=fixture {r ->
        val id=r.service.create("museum",create())
        r.db {
            val scope=CollectionScopeResolver("single","other");val directory=CollectionActorDirectory(scope)
            val access=StaffEducationAccess(scope,directory);val service=CollectionStaffEducationService(access,scope,directory,r.c.getBean(CollectionPermanentArchiveService::class.java))
            assertTrue((service.context()["orgs"] as List<*>).isEmpty())
            assertFalse(access.canWrite(access.actor(),"museum"))
            assertThrows(BadRequestException::class.java){service.detail(id)}
            assertThrows(BadRequestException::class.java){service.list("museum")}
            assertThrows(BadRequestException::class.java){service.people("museum")}
            assertThrows(BadRequestException::class.java){service.create("museum",create("new"))}
            assertThrows(BadRequestException::class.java){CollectionStaffEducationFileReadGuard(access).requireReadable("/userfiles/education/employment.pdf","LOCAL")}
        }
    }

    @ParameterizedTest @ValueSource(strings=["POST_TRAINING","CONTINUING_EDUCATION"])
    fun `actual need attendance and responsible review are distinct and impose no invented course hours`(kind:String)=fixture {r ->
        val p=plan(EnumStaffEducationKind.valueOf(kind));val v=create().copy(plan=p);val id=r.service.create("museum",v)
        assertEquals(id,r.service.create("museum",v));assertEquals("PLANNED",r.service.detail(id)["state"])
        r.login("head");assertThrows(BadRequestException::class.java){r.service.confirm(id,1,confirmation)}
        r.service.revise(id,revision(1,p));assertEquals("RECORDED",r.service.detail(id)["state"])
        assertEquals(id,r.service.revise(id,revision(1,p)));r.service.confirm(id,2,confirmation)
        assertEquals(id,r.service.confirm(id,2,confirmation));assertEquals("VERIFIED",r.service.detail(id)["state"])
        assertEquals(1,r.db {CollectionStaffEducationVersionDao.find {CollectionStaffEducationVersions.educationId eq id}.last().content.content.participation!!.actualMinutes})
    }
    @Test fun `corrections retain original review and require review of the new version`()=fixture {r ->
        val id=r.service.create("museum",create());r.service.revise(id,revision(1));r.login("head");r.service.confirm(id,2,confirmation)
        val prior=r.db {CollectionPermanentArchiveDao.all().map {it.id.value to it.snapshot}.toMap()}
        r.service.revise(id,revision(2,a=activity().copy(assessment="补充真实考核说明")))
        assertEquals("RECORDED",r.service.detail(id)["state"])
        assertEquals(prior,r.db {prior.keys.associateWith {CollectionPermanentArchiveDao[it].snapshot}})
        assertTrue(r.db {CollectionStaffEducationVersionDao.find {CollectionStaffEducationVersions.version eq 2}.single().confirmation!=null})
        assertTrue(r.db {CollectionStaffEducationVersionDao.find {CollectionStaffEducationVersions.version eq 3}.single().confirmation==null})
        assertThrows(BadRequestException::class.java){r.service.confirm(id,2,confirmation)}
    }
    @ParameterizedTest @ValueSource(strings=["ATTENDED","NOT_COMPLETED"])
    fun `attendance or an unfinished course cannot be confirmed as satisfying the need`(outcome:String)=fixture {r ->
        val id=r.service.create("museum",create());r.service.revise(id,revision(1,a=activity().copy(outcome=EnumStaffEducationOutcome.valueOf(outcome))))
        r.login("head");assertThrows(BadRequestException::class.java){r.service.confirm(id,2,confirmation)}
        r.service.confirm(id,2,confirmation.copy(meetsNeed=false));assertEquals("NEEDS_SUPPLEMENT",r.service.detail(id)["state"])
    }
    @ParameterizedTest @ValueSource(strings=["future-need","deadline-without-proof","deadline-before-need","future-completion","reversed-period","zero-duration","missing-file","missing-skills","missing-correction"])
    fun `invalid facts and missing originals are rejected without partial records`(kind:String)=fixture {r ->
        val id=r.service.create("museum",create());var v=revision(1)
        v=when(kind){
            "future-need" -> v.copy(content=v.content.copy(plan=plan().copy(identifiedAt=LocalDateTime.now().plusDays(1))))
            "deadline-without-proof" -> v.copy(content=v.content.copy(plan=plan().copy(dueAt=at.plusDays(1))))
            "deadline-before-need" -> v.copy(content=v.content.copy(plan=plan().copy(dueAt=at.minusDays(1),dueBasisFileIds="needs")))
            "future-completion" -> v.copy(content=v.content.copy(participation=activity().copy(endedAt=LocalDateTime.now().plusDays(1))))
            "reversed-period" -> v.copy(content=v.content.copy(participation=activity().copy(endedAt=at)))
            "zero-duration" -> v.copy(content=v.content.copy(participation=activity().copy(actualMinutes=0)))
            "missing-file" -> v.copy(content=v.content.copy(participation=activity().copy(evidenceFileIds="missing")))
            "missing-skills" -> v.copy(content=v.content.copy(participation=activity().copy(skillsCovered="")))
            else -> v.copy(fileIds="")
        }
        val before=state(r);assertThrows(BadRequestException::class.java){r.service.revise(id,v)};assertEquals(before,state(r))
    }
    @ParameterizedTest @ValueSource(strings=["permission","disabled","foreign","administrator-signature"])
    fun `current permissions membership and responsible identity are required on each write`(kind:String)=fixture {r ->
        val id=r.service.create("museum",create());r.service.revise(id,revision(1));val before=state(r)
        when(kind){
            "permission" -> r.db {RolePermissions.deleteWhere {RolePermissions.permissionId eq CollectionConst.STAFF_EDUCATION_MANAGE}}
            "disabled" -> r.db {UserDao["manager"].enabled=false}
            "foreign" -> r.login("foreign")
            "administrator-signature" -> r.login("admin")
        }
        assertThrows(BadRequestException::class.java){if(kind=="administrator-signature")r.service.confirm(id,2,confirmation) else r.service.revise(id,revision(2))}
        assertEquals(before,state(r))
    }
    @Test fun `former staff can be recorded only with historical basis by current responsible staff without restoring access`()=fixture {r ->
        val v=create(user="former").copy(historicalPerson=true)
        assertThrows(BadRequestException::class.java){r.service.create("museum",v)}
        r.login("head");val id=r.service.create("museum",v)
        assertEquals(true,r.service.detail(id)["historicalPerson"]);assertFalse(r.db {UserDao["former"].enabled!!})
        assertThrows(BadRequestException::class.java){r.service.create("museum",create("current-former","former"))}
    }
    @Test fun `disabled museums retain readable education history but expose no write actions`()=fixture {r ->
        val id=r.service.create("museum",create());r.service.revise(id,revision(1));r.login("head")
        r.db {CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED}
        val before=state(r);val d=r.service.detail(id)
        assertEquals("RECORDED",d["state"]);assertEquals(false,d["canEdit"]);assertEquals(false,d["canConfirm"])
        assertEquals(false,r.service.list("museum")["canCreate"])
        assertThrows(BadRequestException::class.java){r.service.create("museum",create("new"))}
        assertThrows(BadRequestException::class.java){r.service.revise(id,revision(2))}
        assertThrows(BadRequestException::class.java){r.service.confirm(id,2,confirmation)}
        assertEquals(before,state(r))
    }
    @Test fun `self access is limited to own records and protected files even with an old login snapshot`()=fixture {r ->
        val id=r.service.create("museum",create());val other=r.service.create("museum",create("second","other-person"))
        r.login("person");assertEquals(id,r.service.detail(id)["id"]);assertEquals(1L,r.service.list("museum")["total"])
        assertThrows(BadRequestException::class.java){r.service.detail(other)}
        assertTrue(r.files.isProtected("/userfiles/education/employment.pdf","LOCAL"))
        // Shared employment file also belongs to another participant: self access cannot expose that original.
        assertThrows(BadRequestException::class.java){r.files.requireReadable("/userfiles/education/employment.pdf","LOCAL")}
        r.login("manager");r.files.requireReadable("/userfiles/education/employment.pdf","LOCAL")
        r.db {FileDao["employment"].path="/userfiles/education/renamed.pdf"}
        assertTrue(r.files.isProtected("/userfiles/education/employment.pdf","LOCAL"));assertTrue(r.files.isProtected("/userfiles/education/renamed.pdf","LOCAL"))
        r.db {CollectionStaffEducationFileAccesses.deleteAll()}
        val fileRead=r.c.getBean(io.hizhengfu.cloud.common.file.FileReadAccess::class.java)
        assertTrue(fileRead.requireReadable("/userfiles/education/employment.pdf","LOCAL"))
        assertTrue(fileRead.requireReadable("/userfiles/education/renamed.pdf","LOCAL"))
        r.db {RolePermissions.deleteWhere {RolePermissions.permissionId eq CollectionConst.STAFF_EDUCATION_MANAGE}}
        assertThrows(BadRequestException::class.java){fileRead.requireReadable("/userfiles/education/renamed.pdf","LOCAL")}
    }
    @ParameterizedTest @ValueSource(strings=["snapshot","file-reference","read-index","missing-version","missing-confirmation","person"])
    fun `tampered or incomplete evidence is reported and cannot silently restore an older completed status`(kind:String)=fixture {r ->
        val id=r.service.create("museum",create());r.service.revise(id,revision(1));r.login("head");r.service.confirm(id,2,confirmation)
        r.db {when(kind){
            "snapshot" -> CollectionPermanentArchiveDao.find {CollectionPermanentArchives.phase eq "V_2"}.single().snapshot="{}"
            "file-reference" -> CollectionPermanentArchiveFileDao.all().first().delete()
            "read-index" -> CollectionStaffEducationFileAccesses.deleteAll()
            "missing-version" -> CollectionStaffEducationVersionDao.find {CollectionStaffEducationVersions.version eq 2}.single().delete()
            "missing-confirmation" -> CollectionStaffEducationVersionDao.find {CollectionStaffEducationVersions.version eq 2}.single().confirmation=null
            "person" -> CollectionStaffEducationDao[id].userId="other-person"
        }}
        val before=state(r);val d=r.service.detail(id)
        assertEquals("EVIDENCE_REVIEW_REQUIRED",d["state"]);assertEquals(false,d["canEdit"])
        assertThrows(BadRequestException::class.java){r.service.revise(id,revision(2))};assertEquals(before,state(r))
    }
    @Test fun `archive failure rolls back a newly registered person need file bindings and versions`()=fixture {r ->
        val before=state(r);r.failBinding.set(true)
        assertThrows(IllegalStateException::class.java){r.service.create("museum",create())};assertEquals(before,state(r))
        r.failBinding.set(false);val id=r.service.create("museum",create());val original=state(r);r.failBinding.set(true)
        assertThrows(IllegalStateException::class.java){r.service.revise(id,revision(1))};assertEquals(original,state(r))
    }
    @Test fun `withdrawing a need preserves history and is never counted as completing post training`()=fixture {r ->
        val id=r.service.create("museum",create());val v=revision(1,a=null).copy(content=StaffEducationContent(plan(),withdrawal=StaffEducationWithdrawal("岗位调整后该专项需求终止","correction")))
        assertThrows(BadRequestException::class.java){r.service.revise(id,v)};r.login("head");r.service.revise(id,v)
        assertEquals("WITHDRAWN",r.service.detail(id)["state"])
        val people=r.service.people("museum")["people"] as List<Map<String,Any?>>
        assertEquals(false,people.single {it["id"]=="person"}["hasVerifiedPostTraining"])
        assertThrows(BadRequestException::class.java){r.service.confirm(id,2,confirmation)}
    }
    @Test fun `page size zero returns all actual records while unrecorded keepers remain visible`()=fixture {r ->
        r.service.create("museum",create());r.service.create("museum",create("second"))
        assertEquals(2L,r.service.list("museum",pageSize=0)["total"]);assertEquals(2,(r.service.list("museum",pageSize=0)["list"] as List<*>).size)
        assertEquals(1,(r.service.list("museum",pageSize=1)["list"] as List<*>).size)
        val people=r.service.people("museum")["people"] as List<Map<String,Any?>>
        assertEquals(0,people.single {it["id"]=="other-person"}["recordCount"])
        assertThrows(BadRequestException::class.java){r.service.create("museum",create().copy(plan=plan().copy(title="覆盖原请求")))}
    }
    @Test fun `concurrent create requests retry to one case with one original and one version`()=fixture {r ->
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val start=java.util.concurrent.CountDownLatch(1);val v=create()
        try {
            val jobs=(1..2).map {pool.submit<Result<String>> {r.login("manager");start.await();try{runCatching {r.service.create("museum",v)}}finally{SecurityContextHolder.clearContext()}}}
            start.countDown();val results=jobs.map {it.get(20,java.util.concurrent.TimeUnit.SECONDS)}
            assertTrue(results.any {it.isSuccess},results.toString())
            r.login("manager");val id=r.service.create("museum",v)
            assertTrue(results.filter {it.isSuccess}.all {it.getOrThrow()==id})
            assertEquals(1L,r.db {CollectionStaffEducationDao.count()});assertEquals(1L,r.db {CollectionStaffEducationVersionDao.count()})
            assertEquals(2L,r.db {CollectionPermanentArchiveDao.count()})
        }finally{pool.shutdownNow()}
    }
    @Test fun `institutional deadlines distinguish pending review from actual late completion and retain lateness after verification`()=fixture {r ->
        val p=plan().copy(dueAt=at.plusDays(1).plusSeconds(30),dueBasisFileIds="needs")
        val late=r.service.create("museum",create().copy(plan=p));r.service.revise(late,revision(1,p))
        r.login("head");r.service.confirm(late,2,confirmation)
        assertEquals("VERIFIED",r.service.detail(late)["state"]);assertEquals(true,r.service.detail(late)["completedLate"])
        val timely=p.copy(dueAt=at.plusDays(2));val id=r.service.create("museum",create("timely").copy(plan=timely));r.service.revise(id,revision(1,timely))
        assertEquals(false,r.service.detail(id)["overdue"]);assertEquals(true,r.service.detail(id)["completionInTimePendingReview"])
        val pending=r.service.create("museum",create("pending").copy(plan=p));assertEquals(true,r.service.detail(pending)["overdue"])
    }
}
