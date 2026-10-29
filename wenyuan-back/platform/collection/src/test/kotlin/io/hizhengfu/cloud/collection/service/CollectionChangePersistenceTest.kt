package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileReadAccess
import io.hizhengfu.cloud.common.file.FileReadGuard
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
import io.hizhengfu.cloud.sys.system.dao.RolePermissions
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.mock.web.MockHttpServletRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.nio.file.Path

class CollectionChangePersistenceTest {
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
        database = Database.connect("jdbc:sqlite:file:change-${System.nanoTime()}?mode=memory&cache=shared", driver="org.sqlite.JDBC")
    }
    @AfterEach fun cleanup() = SecurityContextHolder.clearContext()

    private fun fixture(block: () -> Unit) = transaction(database) {
        originalReaderSchema()
        SchemaUtils.create(CollectionOrgs)
        CollectionOrgDao.new("museum") { name="本馆"; code="museum"; systemGroupId="museum" }
        originalReader("registrar",CollectionConst.LEDGER_CORRECT)
        actorRole("registrar","CATALOG_READER",setOf(CollectionConst.SELECT))
        SchemaUtils.create(io.hizhengfu.cloud.collection.dao.CollectionLedgerStatEvents,Collections, CollectionCatalogs, CollectionCatalogVersions, CollectionChanges, CollectionChangeApprovals, CollectionDisclosureReviews, CollectionDisclosureEvents,
            CollectionOperationLocks, CollectionLedgerLogs, FlowInstanceLogs, CollectionMedias, CollectionAttachments, CollectionCategories, Warehouses, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, FileContents, FileUsers)
        listOf("evidence", "signed", "public-review", "public-old", "public-new", "public-replacement", "public-event").forEach { fileId ->
            FileDao.new(fileId) { name="$fileId.pdf"; path="/files/$fileId.pdf"; ext="pdf"; hash=fileId; size=10 }
        }
        CollectionCategoryDao.new("ceramic") { name="瓷器"; code="03"; parentId="0" }
        val collection = CollectionDao.new("object-1") {
            orgId="museum"; totalRegNo="000001"; name="旧定名"; era="清"; texture="瓷"; categoryId="ceramic"
            level=EnumRelicLevel.GENERAL; source=EnumSource.OLD; acquireDate=LocalDate.of(2020,1,1)
            completeness=EnumCompleteness.COMPLETE; lifeStatus=EnumCollectionStatus.IN_STORE
            catalogStatus=EnumCatalogStatus.APPROVED; registeredAt=LocalDateTime.of(2020,1,2,10,0)
            published=true
        }
        catalog.ensureCatalogFor(collection)
        block()
    }

    private fun draft(): String {
        val initial = service.proposal("object-1")
        return service.save(initial.copy(reason="根据原始档案订正", content=initial.content!!.copy(formalName="新定名"),
            keeperUserId="keeper", leaderUserId="leader", evidenceFileIds="evidence"))
    }
    private fun complete(id: String, includeLeader: Boolean = true, round: String = "") {
        val change = CollectionChangeDao[id]
        change.flowStatus=EnumFlowStatus.AUDIT; change.flowInstanceId="instance-$id"
        locks.acquire("museum", CollectionConst.BIZ_CHANGE, id, listOf("object-1"))
        listOfNotNull("keeper", "leader".takeIf { includeLeader }).forEach { node ->
            FlowInstanceLogDao.new {
                instanceId="instance-$id"; taskId="$node-$id-$round"; nodeKey=node; action="APPROVE"; operator=node; comment="本人核对无误，同意"
            }
        }
        service.onFlowCompleted(change, FlowCompletedEvent("instance-$id", CollectionConst.FLOW_CHANGE, "变更", id, CollectionConst.BIZ_CHANGE, emptyMap(), "registrar", "经办人"))
    }
    private fun execution() = CollectionChangeExecutionVo(signedChangeFileIds="signed", paperUpdatedAt=LocalDate.now(), paperLedgerBookId="paper-book", paperLedgerPageNo=10)

    @Test fun `draft and approval do not alter master or approved card then signed execution versions atomically`() = fixture {
        val originalVersions = catalog.versions("object-1")
        val id = draft()
        assertEquals("旧定名", CollectionDao["object-1"].name)
        assertEquals("旧定名", CollectionChangeDao[id].before.formalName)
        assertEquals(originalVersions, catalog.versions("object-1"))
        complete(id)
        assertEquals(EnumFlowStatus.PASS, CollectionChangeDao[id].flowStatus)
        assertEquals("旧定名", CollectionDao["object-1"].name)
        assertThrows(BadRequestException::class.java) { locks.requireUnlocked("object-1") }
        assertThrows(BadRequestException::class.java) { service.execute(id, execution().copy(signedChangeFileIds=null)) }
        assertThrows(BadRequestException::class.java) { service.execute(id, execution().copy(paperUpdatedAt=LocalDate.now().minusDays(1))) }
        assertNull(CollectionChangeDao[id].appliedAt)
        service.execute(id, execution())
        assertEquals("新定名", CollectionDao["object-1"].name)
        assertEquals("paper-book", CollectionChangeDao[id].paperLedgerBookId)
        assertEquals(10, CollectionChangeDao[id].paperLedgerPageNo)
        assertEquals("总账第一册 第10页", CollectionChangeDao[id].paperLedgerRef)
        assertEquals("000001", CollectionDao["object-1"].totalRegNo)
        assertNull(CollectionDao["object-1"].publishedAt, "信息变更不制造真实公开或 CMS 供稿时间")
        assertEquals("新定名", catalog.card("object-1").catalog.content.formalName)
        assertEquals("leader", CollectionCatalogVersionDao.all().maxBy { it.version }.auditBy)
        assertEquals("旧定名", CollectionCatalogVersionDao.all().minBy { it.version }.content.formalName)
        assertEquals(1L, CollectionLedgerLogDao.all().count())
        assertEquals(2, archives.list(CollectionConst.BIZ_CHANGE, id).size)
        val files = FileService("/tmp/collection-test-files", mock(FileFolderService::class.java),
            retentionGuards=listOf(CollectionArchiveFileRetentionGuard()),
            fileReadAccess=FileReadAccess(DefaultListableBeanFactory().getBeanProvider(FileReadGuard::class.java)))
        assertThrows(BadRequestException::class.java) { files.delete("evidence") }
        assertThrows(BadRequestException::class.java) { files.deleteByIds(listOf("signed", "evidence")) }
        assertThrows(BadRequestException::class.java) { files.deleteByFileIdAndContentId("signed", id) }
        assertNotNull(FileDao.findById("signed"))
        assertDoesNotThrow { locks.requireUnlocked("object-1") }
        service.execute(id, execution())
        assertEquals(1L, CollectionLedgerLogDao.all().count())
        assertEquals(originalVersions.size + 1, catalog.versions("object-1").size)
    }

    @Test fun `repeated execution refuses changed signed archive or missing original reference`() = fixture {
        val id = draft()
        complete(id)
        service.execute(id, execution())
        val archive = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_CHANGE) and
                (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq "APPLIED")
        }.single()
        val original = archive.snapshot
        val logCount = CollectionLedgerLogDao.all().count()
        archive.snapshot = "{}"
        assertThrows(BadRequestException::class.java) { service.execute(id, execution()) }
        assertEquals(logCount, CollectionLedgerLogDao.all().count())
        archive.snapshot = original
        assertEquals(id, service.execute(id, execution()))
        CollectionPermanentArchiveFileDao.find {
            (CollectionPermanentArchiveFiles.archiveId eq archive.id.value) and
                (CollectionPermanentArchiveFiles.fileId eq "signed")
        }.single().delete()
        assertThrows(BadRequestException::class.java) { service.execute(id, execution()) }
        assertEquals(logCount, CollectionLedgerLogDao.all().count())
    }

    @Test fun `repeated execution requires the original signed form and paper correction`() = fixture {
        val id=draft()
        complete(id)
        val original=execution()
        assertEquals(id,service.execute(id,original))
        val before=listOf(CollectionLedgerLogDao.count(),CollectionPermanentArchiveDao.count(),
            CollectionCatalogVersionDao.count())
        listOf(original.copy(signedChangeFileIds="evidence"),
            original.copy(paperUpdatedAt=original.paperUpdatedAt!!.plusDays(1)),
            original.copy(paperLedgerBookId="another-book"),
            original.copy(paperLedgerPageNo=11),
            original.copy(paperLedgerRef="伪造的纸账页码")).forEach { changed ->
            assertThrows(BadRequestException::class.java) { service.execute(id,changed) }
            assertEquals(before,listOf(CollectionLedgerLogDao.count(),CollectionPermanentArchiveDao.count(),
                CollectionCatalogVersionDao.count()))
        }
        assertEquals(id,service.execute(id,original.copy(paperLedgerRef="总账第一册 第10页")))
    }

    @Test fun `execution rechecks current ledger duty after collection lock wait`() = fixture {
        val id = draft()
        complete(id)
        val waitingLocks = spy(CollectionOperationLockService())
        val guarded = CollectionChangeService(mock(FlowInstanceService::class.java), scope, waitingLocks, binder,
            catalog, mock(CollectionResponsibleUserService::class.java), archives,
            CollectionRetainedLedgerService(scope, archives), CollectionActorDirectory(scope), paperLedgers)
        doAnswer { call ->
            val result = call.callRealMethod()
            RolePermissions.deleteAll()
            result
        }.`when`(waitingLocks).requireUnlocked("object-1", CollectionConst.BIZ_CHANGE, id)

        assertThrows(BadRequestException::class.java) { guarded.execute(id, execution()) }
        assertNull(CollectionChangeDao[id].appliedAt)
        assertEquals("旧定名", CollectionDao["object-1"].name)
        assertEquals(0L, CollectionLedgerLogDao.all().count())
        assertTrue(archives.list(CollectionConst.BIZ_CHANGE, id).none { it["phase"] == "APPLIED" })
    }

    @Test fun `signed information change withdraws stale public feed until a new reviewed copy is enabled`() = fixture {
        actorRole("registrar", "PUBLIC_FEED", setOf(CollectionConst.PUBLISH))
        val actor=SecurityUser(id="registrar",name="总账经办人",username="registrar",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,
            roles=emptySet(),authorities=listOf(CollectionConst.LEDGER_CORRECT,CollectionConst.PUBLISH).map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(actor,null,actor.authorities)
        val disclosure=CollectionDisclosureService(scope,mock(CollectionResponsibleUserService::class.java),archives)
        fun reviewed(name:String,file:String,reason:String?=null)=CollectionDisclosureReviewVo(
            sourceRevision=disclosure.detail("museum",EnumDisclosureSubject.RESOURCE,"object-1")["sourceRevision"] as String,
            decision=EnumDisclosureDecision.PUBLIC,reviewedAt=LocalDateTime.now(),reviewerUserId="registrar",signedReviewFileIds="public-review",
            restrictions=EnumDisclosureRestriction.entries.associateWith {DisclosureRestrictionVo(EnumDisclosureHandling.REDACTED,"已逐项核查公开内容")},
            content=CollectionPublicContentVo(name,"本馆已核查藏品公开资料","通过本馆服务台反馈",mapOf(EnumPublicFact.NAME to name)),
            publicContentFileIds=file,replacementReason=reason,replacementFileIds=reason?.let {"public-replacement"})
        val old=disclosure.review("museum",EnumDisclosureSubject.RESOURCE,"object-1",reviewed("旧定名","public-old"))
        disclosure.enable("museum",old)
        val published=disclosure.event("museum",old,CollectionDisclosureEventVo(
            EnumDisclosureEvent.PUBLISHED,EnumDisclosureChannel.OFFICIAL_WEBSITE,"https://museum.example.org/object-1",
            LocalDateTime.now(),"registrar","public-event","public-old"))
        fun activePublication()=(disclosure.detail("museum",EnumDisclosureSubject.RESOURCE,"object-1")["activePublications"] as List<*>).single() as Map<*,*>
        assertEquals(false,activePublication()["needsUpdate"])
        assertEquals("旧定名",disclosure.feed(EnumDisclosureSubject.RESOURCE).single().content!!.facts[EnumPublicFact.NAME])
        val id=draft();complete(id)
        assertTrue(disclosure.feed(EnumDisclosureSubject.RESOURCE).single().available,"审批尚未生效，不得提前撤下已核查公开稿")
        service.execute(id,execution())
        val stale=disclosure.feed(EnumDisclosureSubject.RESOURCE).single()
        assertFalse(stale.available);assertNull(stale.content)
        val records=disclosure.detail("museum",EnumDisclosureSubject.RESOURCE,"object-1")["records"] as List<*>
        assertEquals(false,(records.single() as Map<*,*>)["matchesCurrentSource"])
        assertEquals(published,activePublication()["id"])
        assertEquals(true,activePublication()["needsUpdate"],"原公开记录须保留并提示核对实际渠道")
        val next=disclosure.review("museum",EnumDisclosureSubject.RESOURCE,"object-1",reviewed("新定名","public-new","法定信息变更后更新公开稿"))
        assertFalse(disclosure.feed(EnumDisclosureSubject.RESOURCE).single().available,"新版核查不自动形成发布事实")
        disclosure.enable("museum",next)
        val current=disclosure.feed(EnumDisclosureSubject.RESOURCE).single()
        assertTrue(current.available)
        assertEquals("新定名",current.content!!.facts[EnumPublicFact.NAME])
        assertEquals(true,activePublication()["needsUpdate"],"新稿供稿不能代替官网实际更新")
    }

    @Test fun `historical exact number permits signed information change and remains in ledger`() = fixture {
        val number=" 000001 "
        CollectionDao["object-1"].totalRegNo=number
        val proposal=service.proposal("object-1")
        assertEquals(number,proposal.totalRegNo)
        assertThrows(BadRequestException::class.java) { service.save(proposal.copy(totalRegNo=number.trim(),reason="改号")) }
        assertEquals(0L,CollectionChangeDao.all().count())
        val id=draft()
        assertEquals("旧定名",CollectionDao["object-1"].name)
        complete(id)
        assertEquals("旧定名",CollectionDao["object-1"].name)
        service.execute(id,execution())
        assertEquals("新定名",CollectionDao["object-1"].name)
        assertEquals(number,CollectionDao["object-1"].totalRegNo)
        assertEquals(number,CollectionLedgerLogDao.all().single().totalRegNo)
        assertNotNull(CollectionChangeDao[id].appliedAt)
    }

    @Test fun `source museum change list keeps verified identity after collection transfer`() = fixture {
        val id = draft()
        val source = CollectionDao["object-1"]
        val snapshot = CollectionRetainedLedgerSnapshot(source.toModel(), identity =
            CollectionRetainedLedgerIdentity("museum", "object-1", "transfer-1", "other"))
        SchemaUtils.create(CollectionRetainedLedgers)
        val retained = CollectionRetainedLedgerDao.new {
            orgId = "museum"; collectionId = "object-1"; transferId = "transfer-1"
            totalRegNo = "000001"; name = "旧定名"; targetOrgId = "other"
            archiveId = ""; this.snapshot = snapshot
        }
        retained.archiveId = archives.seal("museum", "COLLECTION_RETAINED_LEDGER", retained.id.value,
            "TRANSFERRED", snapshot, "")
        CollectionOrgDao.new("other") { name = "接收馆"; code = "other"; systemGroupId = "other" }
        source.orgId = "other"; source.name = "接收馆现时名称"

        val detail = service.get(id)
        assertEquals("000001", detail.totalRegNo)
        assertEquals("旧定名", detail.collectionName)
        val page = service.findPage(MockHttpServletRequest().apply { setParameter("pageSize", "0") })
        @Suppress("UNCHECKED_CAST")
        val listed = (page.list as List<CollectionChangeVo>).single { it.id == id }
        assertEquals("000001", listed.totalRegNo)
        assertEquals("旧定名", listed.collectionName)
        assertEquals("接收馆现时名称", CollectionDao["object-1"].name)

        retained.name = "被改写的留存名称"
        assertThrows(BadRequestException::class.java) { service.get(id) }
    }

    @ParameterizedTest @ValueSource(strings = ["missing", "changed"])
    fun `unavailable retained original warns in list and detail without weakening execution`(
        damage: String, @TempDir directory: Path,
    ) = fixture {
        val id = draft()
        val originalPath = directory.resolve("retained-proof.txt")
        java.nio.file.Files.writeString(originalPath, "frozen original")
        FileDao.new("retained-proof") { name="原馆留存原件"; path="retained-proof.txt"; ext="txt"; hash="original"; size=15 }
        val localArchives = CollectionPermanentArchiveService(binder, scope,
            io.hizhengfu.cloud.common.file.LocalFileObjectStorage(directory.toString()), unprotectedArchiveReadAccessProvider())
        val retainedService = CollectionRetainedLedgerService(scope, localArchives)
        val localService = CollectionChangeService(mock(FlowInstanceService::class.java), scope, locks, binder, catalog,
            mock(CollectionResponsibleUserService::class.java), localArchives, retainedService, CollectionActorDirectory(scope), paperLedgers)
        val source = CollectionDao["object-1"]
        val snapshot = CollectionRetainedLedgerSnapshot(source.toModel(), originalFileIds=listOf("retained-proof"),
            identity=CollectionRetainedLedgerIdentity("museum", "object-1", "transfer-1", "other"))
        SchemaUtils.create(CollectionRetainedLedgers)
        val retained = CollectionRetainedLedgerDao.new {
            orgId="museum"; collectionId="object-1"; transferId="transfer-1"; totalRegNo="000001"
            name="旧定名"; targetOrgId="other"; archiveId=""; this.snapshot=snapshot
        }
        retained.archiveId = localArchives.seal("museum", "COLLECTION_RETAINED_LEDGER", retained.id.value,
            "TRANSFERRED", snapshot, "retained-proof")
        CollectionOrgDao.new("other") { name="接收馆"; code="other"; systemGroupId="other" }
        source.orgId="other"; source.name="接收馆现时名称"; source.totalRegNo="OTHER-001"
        val current = CollectionDao.new("current-object") { orgId="museum"; name="正常本馆藏品"; totalRegNo="000002" }
        val content = CollectionCatalogMapper.fromCollection(current.toModel())
        CollectionChangeDao.new("current-change") {
            orgId="museum"; collectionId=current.id.value; reason="正常申请"
            before=content; baseline=content; this.content=content.copy(formalName="拟变更名")
        }
        assertEquals("旧定名", localService.get(id).collectionName)
        if (damage == "missing") java.nio.file.Files.delete(originalPath)
        else java.nio.file.Files.writeString(originalPath, "changed original")
        val before = CollectionChangeDao.all().map { it.toModel() }.sortedBy { it.id }
        for (pageSize in listOf("0", "10")) {
            val page = localService.findPage(MockHttpServletRequest().apply { setParameter("pageSize", pageSize) })
            assertEquals(2L, page.total)
            @Suppress("UNCHECKED_CAST")
            val rows = page.list as List<CollectionChangeVo>
            val affected = rows.single { it.id == id }
            assertNotNull(affected.collectionEvidenceIssue)
            if (damage == "missing") assertTrue(affected.collectionEvidenceIssue!!.contains("文件缺失（文件编号：retained-proof）"))
            assertNull(affected.collectionName)
            assertNull(affected.totalRegNo)
            val unaffected = rows.single { it.id == "current-change" }
            assertEquals("正常本馆藏品", unaffected.collectionName)
            assertEquals("000002", unaffected.totalRegNo)
            assertNull(unaffected.collectionEvidenceIssue)
        }
        val detail=localService.get(id)
        assertNotNull(detail.collectionEvidenceIssue)
        assertEquals("旧定名",detail.collectionName)
        assertEquals("000001",detail.totalRegNo)
        assertEquals(CollectionChangeDao[id].reason,detail.reason)
        assertThrows(BadRequestException::class.java) { retainedService.verifiedBoundSnapshotForReader(retained.id.value,
            "museum", setOf(CollectionConst.LEDGER_CORRECT), CollectionConst.CROSS_ORG) }
        assertThrows(BadRequestException::class.java) { localArchives.listForAuthorizedOrg("COLLECTION_RETAINED_LEDGER", retained.id.value, "museum") }
        assertThrows(BadRequestException::class.java) { localService.execute(id, execution()) }
        assertThrows(BadRequestException::class.java) { retainedService.inspectBoundSnapshotForReader(retained.id.value,
            "other", setOf(CollectionConst.LEDGER_CORRECT), CollectionConst.CROSS_ORG) }
        assertEquals(before, CollectionChangeDao.all().map { it.toModel() }.sortedBy { it.id })
        assertEquals(snapshot, retained.snapshot)
        assertEquals("接收馆现时名称", source.name)
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { localService.findPage(MockHttpServletRequest()) }
        assertThrows(BadRequestException::class.java) { retainedService.inspectBoundSnapshotForReader(retained.id.value,
            "museum", setOf(CollectionConst.LEDGER_CORRECT), CollectionConst.CROSS_ORG) }
    }

    @Test fun `skipped statutory approver prevents completion even with custom completed flow`() = fixture {
        val id=draft()
        assertThrows(BadRequestException::class.java) { complete(id, includeLeader=false) }
        assertEquals(EnumFlowStatus.AUDIT, CollectionChangeDao[id].flowStatus)
        assertEquals("旧定名", CollectionDao["object-1"].name)
        assertThrows(BadRequestException::class.java) { service.execute(id, execution()) }
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = ["JUMP", "REJECT", "RETURN"])
    fun `legacy pass cannot apply a ledger change using evidence before a new round`(action: String) = fixture {
        val id=draft(); complete(id)
        val change=CollectionChangeDao[id]
        val original=archives.list(CollectionConst.BIZ_CHANGE,id)
        FlowInstanceLogDao.new { instanceId=change.flowInstanceId!!; this.action=action; nodeKey="keeper"; operator="manager"; createTime=LocalDateTime.now().plusMinutes(1) }
        assertThrows(BadRequestException::class.java) { service.execute(id,execution()) }
        assertNull(change.appliedAt); assertEquals("旧定名",CollectionDao["object-1"].name)
        assertEquals(0L,CollectionLedgerLogDao.all().count()); assertEquals(original,archives.list(CollectionConst.BIZ_CHANGE,id))
    }

    @Test fun `submitted application cannot be reset or overwritten and stale baseline cannot apply`() = fixture {
        val id=draft(); complete(id)
        assertThrows(BadRequestException::class.java) { service.saveDraft(id) }
        assertThrows(BadRequestException::class.java) { service.save(service.get(id).copy(reason="覆盖")) }
        CollectionDao["object-1"].era="明"
        assertThrows(BadRequestException::class.java) { service.execute(id, execution()) }
        assertNull(CollectionChangeDao[id].appliedAt)
        assertEquals(0L, CollectionLedgerLogDao.all().count())
    }

    @Test fun `reopened approval cannot reuse old opinions and archives both approval rounds`() = fixture {
        val id=draft(); complete(id)
        val change=CollectionChangeDao[id]
        FlowInstanceLogDao.new {
            instanceId="instance-$id"; action="REACTIVATE"; operator="manager"; nodeKey="keeper"; comment="重新核实"
        }
        change.flowStatus=EnumFlowStatus.AUDIT
        assertThrows(BadRequestException::class.java) {
            service.onFlowCompleted(change, FlowCompletedEvent("instance-$id", CollectionConst.FLOW_CHANGE, "变更", id, CollectionConst.BIZ_CHANGE, emptyMap(), "registrar", "经办人"))
        }
        complete(id, round="renewed")
        service.execute(id, execution())
        assertEquals(3, archives.list(CollectionConst.BIZ_CHANGE,id).size)
        assertEquals(4L, CollectionChangeApprovalDao.all().count())
        assertEquals("新定名", CollectionDao["object-1"].name)
    }

    @Test fun `legacy catalogue reset and registration number correction cannot bypass immutable official record`() = fixture {
        assertThrows(BadRequestException::class.java) { catalog.startRevision("object-1") }
        CollectionDao["object-1"].catalogStatus=EnumCatalogStatus.DRAFT
        CollectionCatalogDao.find { CollectionCatalogs.collectionId eq "object-1" }.single().catalogStatus=EnumCatalogStatus.DRAFT
        assertThrows(BadRequestException::class.java) { catalog.save("object-1", CollectionCatalogContentVo(formalName="绕过")) }
        assertThrows(BadRequestException::class.java) { catalog.submit("object-1") }
        assertThrows(BadRequestException::class.java) { service.requestCorrection("object-1", CollectionCorrectVo(field="totalRegNo", value="999999", reason="错号")) }
        assertEquals("000001", CollectionDao["object-1"].totalRegNo)
        assertEquals("旧定名", CollectionDao["object-1"].name)
    }

    @Test fun `unregistered or unapproved collection cannot start paper ledger correction`() = fixture {
        val collection = CollectionDao["object-1"]
        val proposal = service.proposal("object-1")
        val content = proposal.content!!.copy(formalName = "拟订正名称")
        for ((registered, status) in listOf(
            false to EnumCatalogStatus.DRAFT,
            false to EnumCatalogStatus.APPROVED,
            true to EnumCatalogStatus.DRAFT,
        )) {
            collection.registeredAt = if (registered) LocalDateTime.of(2020, 1, 2, 10, 0) else null
            collection.catalogStatus = status
            assertThrows(BadRequestException::class.java) {
                service.save(proposal.copy(content = content, reason = "开发反例：未完成正式登账或编目审核"))
            }
            assertEquals(0L, CollectionChangeDao.count())
            assertEquals("旧定名", collection.name)
        }
    }

    @Test fun `approved paper ledger correction cannot apply if formal registration is lost`() = fixture {
        val id = draft()
        complete(id)
        val collection = CollectionDao["object-1"]
        collection.registeredAt = null
        assertThrows(BadRequestException::class.java) { service.execute(id, execution()) }
        assertNull(CollectionChangeDao[id].appliedAt)
        assertEquals("旧定名", collection.name)
        assertEquals(0L, CollectionLedgerLogDao.count())
    }
}
