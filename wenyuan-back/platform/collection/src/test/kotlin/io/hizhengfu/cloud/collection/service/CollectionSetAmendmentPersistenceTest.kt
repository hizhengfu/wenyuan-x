package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.file.dao.FileContents
import io.hizhengfu.cloud.sys.file.dao.FileUsers
import io.hizhengfu.cloud.sys.file.service.FileService
import io.hizhengfu.cloud.sys.file.service.FileFolderService
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate
import java.time.LocalDateTime

open class CollectionSetAmendmentPersistenceTest {
    @org.junit.jupiter.api.io.TempDir lateinit var temp: java.nio.file.Path
    private val scope = CollectionScopeResolver("single", "museum")
    private val locks = CollectionOperationLockService()
    private val binder = mock(BusinessAttachmentBinder::class.java).also {
        `when`(it.resolveSingle(anyString())).thenAnswer { call -> call.getArgument<String>(0).trim().takeIf(String::isNotBlank) }
    }
    private val catalog = CollectionCatalogService(scope, locks,org.mockito.Mockito.mock(CollectionGradingService::class.java),org.mockito.Mockito.mock(AccessionReceiptService::class.java),binder)
    private val archives = CollectionPermanentArchiveService(binder, scope, readAccess=unprotectedArchiveReadAccessProvider())
    private val paperLedgers = mock(PaperLedgerService::class.java).also {
        `when`(it.requirePageReference("museum", "paper-book", 10)).thenReturn(PaperLedgerService.PageReference("paper-book", "总账第一册", 10, 20))
    }
    private val service = CollectionChangeService(mock(FlowInstanceService::class.java), scope, locks, binder, catalog, mock(CollectionResponsibleUserService::class.java), archives, CollectionRetainedLedgerService(scope, archives), CollectionActorDirectory(scope), paperLedgers)
    private lateinit var database: Database

    @BeforeEach fun prepare() {
        val context = StaticApplicationContext()
        context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
        context.refresh()
        SpringContextHolder().setApplicationContext(context)
        val user = SecurityUser(id="registrar", name="总账经办人", username="registrar", password="", groupIds=emptySet(), postIds=emptySet(), dataType=0,
            roles=emptySet(), authorities=listOf(SimpleGrantedAuthority(CollectionConst.LEDGER_CORRECT)), enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        database = connectDatabase()
    }
    protected open fun connectDatabase(): Database = Database.connect("jdbc:sqlite:${temp.resolve("set-amendment.db")}", driver="org.sqlite.JDBC")
    protected open fun closeDatabase() {}
    @AfterEach fun cleanup() { try { closeDatabase() } finally { SecurityContextHolder.clearContext() } }

    private fun fixture(block: () -> Unit) {
      transaction(database) {
        originalReaderSchema()
        SchemaUtils.create(CollectionOrgs)
        CollectionOrgDao.new("museum") { name="本馆"; code="museum"; systemGroupId="museum" }
        originalReader("registrar",CollectionConst.LEDGER_CORRECT)
        actorRole("registrar", "SET_ENTRY", setOf(CollectionConst.LEDGER_CREATE, CollectionConst.SELECT))
        SchemaUtils.create(io.hizhengfu.cloud.collection.dao.CollectionLedgerStatEvents,Collections, CollectionSets, CollectionCatalogs, CollectionCatalogVersions, CollectionChanges, CollectionChangeApprovals,
            CollectionOperationLocks, CollectionLedgerLogs, FlowInstanceLogs, CollectionMedias, CollectionAttachments, CollectionCategories, Warehouses, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, FileContents, FileUsers)
        listOf("evidence", "signed").forEach { fileId ->
            FileDao.new(fileId) { name="$fileId.pdf"; path="/files/$fileId.pdf"; ext="pdf"; hash=fileId; size=10 }
        }
        val collection = CollectionDao.new("object-1") {
            orgId="museum"; totalRegNo="000001"; name="旧定名"; era="清"; texture="瓷"; categoryId="ceramic"
            level=EnumRelicLevel.GENERAL; source=EnumSource.OLD; acquireDate=LocalDate.of(2020,1,1)
            completeness=EnumCompleteness.COMPLETE; lifeStatus=EnumCollectionStatus.IN_STORE
            catalogStatus=EnumCatalogStatus.APPROVED; registeredAt=LocalDateTime.of(2020,1,2,10,0)
            published=true
        }
        catalog.ensureCatalogFor(collection)
        for (id in listOf("child-1", "child-2", "foreign")) CollectionDao.new(id) {
            orgId = if (id == "foreign") "other" else "museum"; totalRegNo = "REG-$id"; name = "原名称-$id"
            registeredAt = LocalDateTime.of(2020,1,2,10,0); catalogStatus = EnumCatalogStatus.APPROVED
        }
        CollectionSetDao.new("relation-1") {orgId="museum";parentCollectionId="object-1";childCollectionId="child-1";partNo="01";partName="原分件";relation=EnumSetRelation.PART}
      }
      transaction(database) { block() }
    }

    private fun draft(): String {
        val initial = service.proposal("object-1", true)
        return service.save(initial.copy(reason="根据原始档案订正", content=initial.content, setAmendment=initial.setAmendment!!.copy(after=initial.setAmendment.after.map {it.copy(partName="订正分件")}),
            keeperUserId="keeper", leaderUserId="leader", evidenceFileIds="evidence"))
    }
    private fun complete(id: String, includeLeader: Boolean = true, round: String = "") {
        val change = CollectionChangeDao[id]
        if (change.flowInstanceId == null) org.springframework.test.util.ReflectionTestUtils.invokeMethod<Unit>(service,"beforeSubmit",change)
        change.flowStatus=EnumFlowStatus.AUDIT; change.flowInstanceId="instance-$id"
        org.springframework.test.util.ReflectionTestUtils.invokeMethod<Unit>(service,"acquireApprovalResources",change)
        listOfNotNull("keeper", "leader".takeIf { includeLeader }).forEach { node ->
            FlowInstanceLogDao.new {
                instanceId="instance-$id"; taskId="$node-$id-$round"; nodeKey=node; action="APPROVE"; operator=node; comment="本人核对无误，同意"
            }
        }
        service.onFlowCompleted(change, FlowCompletedEvent("instance-$id", CollectionConst.FLOW_CHANGE, "变更", id, CollectionConst.BIZ_CHANGE, emptyMap(), "registrar", "经办人"))
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
    }
    private fun execution() = CollectionChangeExecutionVo(signedChangeFileIds="signed", paperUpdatedAt=LocalDate.now(), paperLedgerBookId="paper-book", paperLedgerPageNo=10)

    /** PostgreSQL 子类用已提交夹具检验跨事务锁等待；普通测试仍沿用同一准备链。 */
    protected fun preparedApprovedChange(): String {
        lateinit var id: String
        fixture { id = draft(); complete(id) }
        return id
    }
    protected fun executeApprovedChange(id: String): String = service.execute(id, execution())
    protected fun testDatabase(): Database = database


    private val setService = CollectionSetService(scope, mock(CollectionService::class.java), locks)
    private fun rows() = CollectionSetAmendments.relations(CollectionDao["object-1"])
    private fun edit() = CollectionSetVo(id="relation-1", childCollectionId="child-1", partNo="01",partName="绕过",relation=EnumSetRelation.PART)

    @Test fun `persisted amendment accepts unchanged submillisecond registration timestamps`() = fixture {
        val registered = LocalDateTime.of(2020, 1, 2, 10, 0, 0, 123456000)
        CollectionDao["object-1"].registeredAt = registered
        CollectionDao["child-1"].registeredAt = registered
        val id = draft()
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.clear()
        val stored = CollectionDao["object-1"].registeredAt
        if (database.vendor == "postgresql") assertEquals(registered, stored)
        complete(id)
        service.execute(id, execution())
        assertEquals("订正分件", rows().single().partName)
        assertEquals(stored, CollectionDao["object-1"].registeredAt)
    }

    @Test fun `structure-only amendment waits for personal approvals and signed paper correction then retains exact history`() = fixture {
        val before=rows();val id=draft();val proposed=CollectionChangeDao[id].setAmendment!!
        assertEquals(before, rows());assertEquals("旧定名",CollectionDao["object-1"].name)
        complete(id);assertEquals(before,rows())
        assertThrows(BadRequestException::class.java) {service.execute(id,execution().copy(signedChangeFileIds=null))}
        assertThrows(BadRequestException::class.java) {locks.requireUnlocked("child-1")}
        service.execute(id,execution())
        assertEquals("订正分件",rows().single().partName);assertEquals("relation-1",rows().single().id)
        assertEquals(before, CollectionChangeDao[id].setAmendment!!.before)
        assertEquals(proposed,CollectionChangeDao[id].setAmendment)
        val retained=archives.list(CollectionConst.BIZ_CHANGE,id)
        assertEquals(4,retained.size)
        assertEquals(2L,CollectionLedgerLogDao.all().count())
        assertDoesNotThrow {locks.requireUnlocked("child-1")}
        val applied=CollectionPermanentArchiveDao.all().single {it.phase=="SET_APPLIED"}.snapshot
        assertTrue(applied.contains("原名称-child-1"));assertTrue(applied.contains("REG-child-1"));assertTrue(applied.contains("原分件"));assertTrue(applied.contains("订正分件"))
        CollectionDao["child-1"].name="后续名称";CollectionDao["child-1"].orgId="other"
        assertEquals(applied,CollectionPermanentArchiveDao.all().single {it.phase=="SET_APPLIED"}.snapshot)
        service.execute(id,execution());assertEquals(2L,CollectionLedgerLogDao.all().count())
    }

    @Test fun `repeated set execution refuses missing or changed set applied original`() = fixture {
        val id=draft();complete(id);service.execute(id,execution())
        val applied=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_CHANGE) and
                (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq "SET_APPLIED")
        }.single()
        val original=applied.snapshot
        val originalDigest=applied.digest
        val before=rows()
        val logs=CollectionLedgerLogDao.all().count()
        applied.snapshot="{}"
        assertThrows(BadRequestException::class.java) {service.execute(id,execution())}
        assertEquals(before,rows());assertEquals(logs,CollectionLedgerLogDao.all().count())
        val mismatched=original.replace("\"paperLedgerBookId\":\"paper-book\"", "\"paperLedgerBookId\":\"wrong-book\"")
        assertNotEquals(original,mismatched)
        applied.snapshot=mismatched;applied.digest=CollectionCareEvidence.digest(mismatched)
        assertThrows(BadRequestException::class.java) {service.execute(id,execution())}
        applied.snapshot=original
        applied.digest=originalDigest
        assertEquals(id,service.execute(id,execution()))
        CollectionPermanentArchiveFileDao.find {
            (CollectionPermanentArchiveFiles.archiveId eq applied.id.value) and
                (CollectionPermanentArchiveFiles.fileId eq "signed")
        }.single().delete()
        assertThrows(BadRequestException::class.java) {service.execute(id,execution())}
        assertEquals(before,rows());assertEquals(logs,CollectionLedgerLogDao.all().count())
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["save","delete","replace","add"])
    fun `formal relationship cannot bypass amendment using direct relation endpoints`(action:String)=fixture {
        val before=rows()
        assertThrows(BadRequestException::class.java) {when(action) {
            "delete"->setService.delete("object-1","relation-1")
            "replace"->setService.save("object-1",edit().copy(childCollectionId="child-2"))
            "add"->setService.save("object-1",edit().copy(id=null,partNo="02",childCollectionId="child-2"))
            else->setService.save("object-1",edit())
        }}
        assertEquals(before,rows());assertEquals(0L,CollectionChanges.selectAll().count())
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["registered","approved","child","review","locked"])
    fun `downgraded parent flags or formal children cannot reopen unapproved editing`(kind:String)=fixture {
        CollectionDao["object-1"].registeredAt=null;CollectionDao["object-1"].catalogStatus=EnumCatalogStatus.DRAFT
        CollectionDao["child-1"].registeredAt=null;CollectionDao["child-1"].catalogStatus=EnumCatalogStatus.DRAFT
        when(kind) {
            "registered"->CollectionDao["object-1"].registeredAt=LocalDateTime.now()
            "approved"->CollectionDao["object-1"].catalogStatus=EnumCatalogStatus.APPROVED
            "child"->CollectionDao["child-1"].registeredAt=LocalDateTime.now()
            "review"->CollectionDao["child-1"].catalogStatus=EnumCatalogStatus.SUBMITTED
            "locked"->locks.acquire("museum","OTHER","busy",listOf("child-1"))
        }
        val before=rows();assertThrows(BadRequestException::class.java){setService.delete("object-1","relation-1")};assertEquals(before,rows())
    }

    @Test fun `new pending relationships remain editable without statutory approval`()=fixture {
        for(id in listOf("object-1","child-1")){CollectionDao[id].registeredAt=null;CollectionDao[id].catalogStatus=EnumCatalogStatus.DRAFT}
        setService.save("object-1",edit());assertEquals("绕过",rows().single().partName)
        setService.delete("object-1","relation-1");assertTrue(rows().isEmpty())
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["before","foreign","missing","duplicate_number","duplicate_child","cycle","other_parent","row_id","quantity","main"])
    fun `invalid or stale proposed relation cannot create an application`(kind:String)=fixture {
        val initial=service.proposal("object-1",true);val a=initial.setAmendment!!;val original=a.after.single()
        val proposed=when(kind) {
            "before"->a.copy(before=emptyList(),after=emptyList())
            "foreign"->a.copy(after=listOf(original.copy(childCollectionId="foreign")))
            "missing"->a.copy(after=listOf(original.copy(childCollectionId="missing")))
            "duplicate_number"->a.copy(after=listOf(original,original.copy(id=null,childCollectionId="child-2")))
            "duplicate_child"->a.copy(after=listOf(original,original.copy(id=null,partNo="02")))
            "cycle"->a.copy(after=listOf(original.copy(childCollectionId="object-1")))
            "row_id"->a.copy(after=listOf(original.copy(id="unknown")))
            "quantity"->a.copy(after=listOf(original.copy(quantity=0)))
            "main"->a.copy(after=listOf(original.copy(relation=EnumSetRelation.MAIN)))
            else->{CollectionSetDao.new("other-relation"){orgId="museum";parentCollectionId="child-2";childCollectionId="child-1";partNo="01";partName="占用";relation=EnumSetRelation.PART};a.copy(after=listOf(original.copy(partName="新")))}
        }
        assertThrows(BadRequestException::class.java) {service.save(initial.copy(reason="订正",setAmendment=proposed))}
        assertEquals(0L,CollectionChangeDao.all().count());assertEquals(a.before,rows())
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["name","org","number","registered","relation","new_relation","submitted","approved"])
    fun `changed baseline or retained approval prevents execution without modifying relations`(kind:String)=fixture {
        val id=draft();complete(id)
        when(kind) {
            "name"->CollectionDao["child-1"].name="换名"
            "org"->CollectionDao["child-1"].orgId="other"
            "number"->CollectionDao["child-1"].totalRegNo="OTHER"
            "registered"->CollectionDao["child-1"].registeredAt=LocalDateTime.of(2021,1,2,10,0)
            "relation"->CollectionSetDao["relation-1"].partName="另外订正"
            "new_relation"->CollectionSetDao.new("new-row"){orgId="museum";parentCollectionId="object-1";partNo="02";partName="增加";relation=EnumSetRelation.PART}
            "submitted"->CollectionPermanentArchiveDao.all().single {it.phase=="SUBMITTED"}.snapshot="{}"
            "approved"->CollectionPermanentArchiveDao.all().single {it.phase.startsWith("APPROVED_")}.snapshot="{}"
        }
        val before=CollectionSetDao.all().map {it.toModel()}
        assertThrows(BadRequestException::class.java) {service.execute(id,execution())}
        assertNull(CollectionChangeDao[id].appliedAt);assertEquals(before,CollectionSetDao.all().map {it.toModel()});assertEquals(0L,CollectionLedgerLogDao.all().count())
    }

    @Test fun `replacement preserves deleted row in archives and binds newly assigned relationship identity`()=fixture {
        val initial=service.proposal("object-1",true)
        val a=initial.setAmendment!!.copy(after=listOf(CollectionSetRelationSnapshot(childCollectionId="child-2",partNo="02",partName="更正分件",relation=EnumSetRelation.PART)))
        val id=service.save(initial.copy(reason="原分件关联错误",setAmendment=a,keeperUserId="keeper",leaderUserId="leader",evidenceFileIds="evidence"))
        complete(id)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("child-1")}
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("child-2")}
        service.execute(id,execution())
        assertNull(CollectionSetDao.findById("relation-1"));val actual=rows().single();assertEquals("child-2",actual.childCollectionId);assertNotNull(actual.id)
        val snapshot=CollectionPermanentArchiveDao.all().single {it.phase=="SET_APPLIED"}.snapshot
        assertTrue(snapshot.contains("relation-1"));assertTrue(snapshot.contains(actual.id!!));assertTrue(snapshot.contains("REG-child-1"));assertTrue(snapshot.contains("REG-child-2"))
    }

    @Test fun `removing all parts is an explicit approved amendment not an omitted request`()=fixture {
        val initial=service.proposal("object-1",true)
        val id=service.save(initial.copy(reason="解除错误套件",setAmendment=initial.setAmendment!!.copy(after=emptyList()),keeperUserId="keeper",leaderUserId="leader",evidenceFileIds="evidence"))
        complete(id);service.execute(id,execution());assertTrue(rows().isEmpty());assertEquals(1,CollectionChangeDao[id].setAmendment!!.before.size)
    }

    @Test fun `client identity snapshots cannot forge original registration or museum`()=fixture {
        val initial=service.proposal("object-1",true);val a=initial.setAmendment!!
        val id=service.save(initial.copy(reason="订正",setAmendment=a.copy(after=a.after.map{it.copy(partName="新")},identities=a.identities.map{it.copy(totalRegNo="伪造",orgId="other")})))
        assertEquals(a.identities,CollectionChangeDao[id].setAmendment!!.identities)
    }

    @Test fun `missing statutory leader still prevents structure amendment approval`()=fixture {
        val id=draft();val before=rows();assertThrows(BadRequestException::class.java){complete(id,false)}
        assertThrows(BadRequestException::class.java){service.execute(id,execution())};assertEquals(before,rows())
    }
}
