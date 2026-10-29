package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.rfid.dao.RfidTagJobs
import io.hizhengfu.cloud.collection.rfid.dao.RfidTags
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidTagStatus
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.domain.FlowReactivatedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.FileContents
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate
import java.time.LocalDateTime

class CollectionTransferPersistenceTest {
    private val scope=CollectionScopeResolver("multi","")
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { call -> call.getArgument<String>(0) } }
    private val successionReadPolicies = org.springframework.beans.factory.support.DefaultListableBeanFactory().apply {
        registerSingleton("successionReadPolicy", object : CollectionArchiveReadPolicy {
            override val businessTypes = setOf(InstitutionSuccessionCaseEvidence.BIZ)
            override fun requireReadable(reference: CollectionArchiveFileReference) {
                val actor = io.hizhengfu.cloud.common.utils.SecurityUtils.getUserDetail()
                if (reference.orgId !in actor.groupIds || actor.authorities.none {
                        it.authority in setOf(CollectionConst.STORAGE, CollectionConst.LEDGER_CORRECT)
                    }) throw BadRequestException("当前账号不能读取来源馆统筹原件")
            }
        })
    }.getBeanProvider(CollectionArchiveReadPolicy::class.java)
    private val archives=CollectionPermanentArchiveService(binder,scope,
        readAccess=syntheticProtectedArchiveReadAccessProvider(),archiveReadPolicies=successionReadPolicies)
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val archivePackets=mock(CollectionArchivePacketService::class.java)
    private val statutory=CollectionTransferStatutoryService(scope,users,binder,archives,archivePackets,mock(CollectionDisclosureService::class.java))
    private val locks=CollectionOperationLockService()
    private val numbers=CollectionRegistrationNumberService()
    private val retained=CollectionRetainedLedgerService(scope,archives)
    private val catalogs=CollectionCatalogService(scope,locks,org.mockito.Mockito.mock(CollectionGradingService::class.java),org.mockito.Mockito.mock(AccessionReceiptService::class.java),binder)
    private val collections=CollectionService(scope,binder,numbers,locks,catalogs,mock(CollectionChangeService::class.java),retained,mock(CollectionDisclosureService::class.java))
    private val service=CollectionTransferService(mock(FlowInstanceService::class.java),scope,collections,binder,locks,statutory,archives,catalogs)
    private val institution=InstitutionSuccessionCaseService(scope,users,archives)

    private fun login(org: String,permission: String=CollectionConst.LEDGER_CORRECT,actor:String=if(permission==CollectionConst.STORAGE) "operator-$org" else "registrar-$org") {
        val user=SecurityUser(id=actor,name="总账经办",username=actor,password="",groupId=org,groupIds=setOf(org),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(permission)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    @BeforeEach fun prepare() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        login("source")
    }
    @AfterEach fun cleanup()=SecurityContextHolder.clearContext()
    private lateinit var fixtureDataSource: javax.sql.DataSource
    private var postgresFixture = false
    private fun fixture(number: String="000123",nature:EnumCollectionNature=EnumCollectionNature.RELIC,block: (Database)->Unit) {
        val file=java.nio.file.Files.createTempFile("collection-transfer-",".sqlite")
        val pg=System.getProperty("collection.test.pg.config")?.let { config ->
            val settings=java.util.Properties().apply { java.nio.file.Files.newInputStream(java.nio.file.Path.of(config)).use(::load) }
            require(settings.getProperty("url").startsWith("jdbc:postgresql://127.0.0.1:") && !settings.getProperty("url").endsWith("/collection_db"))
            org.postgresql.ds.PGSimpleDataSource().apply { setURL(settings.getProperty("url"));user=settings.getProperty("user");password=settings.getProperty("password") }
        }
        val schema="transfer_persistence_"+java.util.UUID.randomUUID().toString().replace("-","")
        pg?.connection?.use { it.createStatement().use { sql -> sql.execute("CREATE SCHEMA $schema") } }
        pg?.currentSchema=schema
        postgresFixture=pg!=null
        fixtureDataSource=pg ?: org.sqlite.SQLiteDataSource().apply { url="jdbc:sqlite:$file" }
        val db=Database.connect(fixtureDataSource)
        try {
            transaction(db) {
                SchemaUtils.create(io.hizhengfu.cloud.collection.dao.CollectionLedgerStatEvents,Collections,CollectionOrgs,CollectionTransfers,CollectionTransferTerminations,CollectionTransferNotices,CollectionAdministrativeRecords,CollectionCatalogs,CollectionCatalogVersions,
                    CollectionRegistrationNumbers,CollectionLedgerLogs,CollectionRetainedLedgers,CollectionMedias,CollectionMediaFolders,CollectionAttachments,
                    CollectionPlacements,Warehouses,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,io.hizhengfu.cloud.sys.file.dao.FileContents,FlowInstanceLogs,RfidTags,RfidTagJobs,
                    InstitutionSuccessionCases,InstitutionSuccessionItems,InstitutionSuccessionRecords,CollectionAuxiliaries,Acquisitions,EntryVouchers,AccessionReceipts,AcquisitionComplianceCases,DesignatedReceptions,DesignatedReceptionRecords)
                originalReaderSchema()
                listOf("source","target","outside").forEach { key ->
                    io.hizhengfu.cloud.sys.system.dao.GroupDao.new(key) { name=key;type='1';parentId="0";parentIds="0," }
                    listOf("registrar","keeper","leader","liquidator","operator").forEach { role ->
                        originalReader("$role-$key",if(role=="registrar") CollectionConst.LEDGER_CORRECT else CollectionConst.STORAGE,key)
                        actorRole("$role-$key","FILE_$role-$key",setOf(if(role=="registrar") CollectionConst.SELECT else CollectionConst.STORAGE))
                    }
                    CollectionOrgDao.new(key) { name=key;code=key;ownership=EnumOwnership.STATE }
                    WarehouseDao.new("$key-warehouse") { orgId=key;name="$key 库房";type=EnumWarehouseType.WAREHOUSE;sceneType=EnumLocationScene.STORAGE }
                }
                originalReader("case-reader",CollectionConst.LEDGER_CORRECT,"source")
                listOf("archive","approval","sign","resolution-sign","image","agreement","consent","public","evidence").forEach { key -> FileDao.new(key) { name="$key.pdf";path="/files/$key.pdf";hash=key;size=100;ext="pdf" } }
                makeCollection("object","source",number,nature)
                val folder=CollectionMediaFolderDao.new("folder") { orgId="source";collectionId="object";name="原馆影像" }
                CollectionMediaDao.new { orgId="source";collectionId="object";fileId="image";folderId=folder.id.value }
                CollectionAttachmentDao.new { orgId="source";collectionId="object";fileId="archive";name="取得档案" }
                RfidTags.insert { it[id]="tag";it[orgId]="source";it[collectionId]="object";it[epc]="1234";it[tagStatus]=EnumRfidTagStatus.ACTIVE }
                makeRequest("request","object","source","target")
            }
            block(db)
        } finally {
            pg?.connection?.use { it.createStatement().use { sql -> sql.execute("DROP SCHEMA $schema CASCADE") } }
            java.nio.file.Files.deleteIfExists(file)
        }
    }
    private fun makeCollection(id: String,org: String,no: String,nature:EnumCollectionNature=EnumCollectionNature.RELIC) {
        val node=CollectionDao.new(id) { orgId=org;totalRegNo=no;name="已登记藏品 $id";this.nature=nature;source=EnumSource.ALLOCATE;level=if(nature==EnumCollectionNature.RELIC) EnumRelicLevel.SECOND else EnumRelicLevel.UNRATED
            warehouseId="$org-warehouse";registeredAt=LocalDateTime.of(2020,1,1,0,0);catalogStatus=EnumCatalogStatus.APPROVED;lifeStatus=EnumCollectionStatus.IN_STORE }
        numbers.markUsed(org,no,id,"原始登记")
        val catalog=catalogs.ensureCatalogFor(node)
        catalog.catalogStatus=EnumCatalogStatus.APPROVED
    }
    private fun makeRequest(id: String,objectId: String,source: String,target: String): CollectionTransferDao = CollectionTransferDao.new(id) {
        orgId=source;fromOrgId=source;toOrgId=target;collectionId=objectId;reason="依法调拨共享研究";createBy="registrar-$source";baseline=CollectionCatalogMapper.fromCollection(CollectionDao[objectId].toModel())
        dossier=CollectionTransferDossierVo(EnumCollectionTransferKind.STATE_ALLOCATION,"keeper-$source","leader-$source","keeper-$target","leader-$target","$target-warehouse","archive","源账第 1 页","接收账第 2 页",provincialJurisdictionFileIds="approval",
            transportStandardReference="GB/T 23862-2024",transportStandardApplicability="逐件核对文物包装运输条件",transportCarrierName="$source 馆",transportCarrierCapabilityFileIds="archive",transportPlanFileIds="archive")
    }
    private fun administrative(request: CollectionTransferDao,national: Boolean=false) {
        login(request.orgId,CollectionConst.STORAGE)
        try { statutory.recordAdministrative(request.id.value,CollectionAdministrativeEvidenceVo(level=if(national) EnumAuthorityLevel.NATIONAL else EnumAuthorityLevel.PROVINCE,action=if(national) EnumAdministrativeAction.FILING else EnumAdministrativeAction.APPROVAL,authorityName="文物行政部门",documentNo="文物函2026-1",issuedAt=LocalDate.of(2026,8,1),fileIds="approval")) } finally { login(request.orgId) }
    }
    private fun approve(request: CollectionTransferDao,round: String="1") {
        request.flowInstanceId="flow-${request.id.value}";request.flowStatus=EnumFlowStatus.AUDIT
        locks.acquire(request.orgId,CollectionConst.BIZ_TRANSFER,request.id.value,listOf(request.collectionId))
        statutory.expectedActors(request).forEach { (node,actor) -> FlowInstanceLogDao.new { instanceId=request.flowInstanceId!!;taskId="${request.id.value}-$node-$round";nodeKey=node;action="APPROVE";operator=actor.userId;comment="本人核对原件，确认同意" } }
        service.onFlowCompleted(request,FlowCompletedEvent(request.flowInstanceId!!,CollectionConst.FLOW_TRANSFER,"交换调拨",request.id.value,CollectionConst.BIZ_TRANSFER,emptyMap(),request.createBy!!,"经办"))
    }
    private fun execution(source: String="source",target: String="target") = CollectionTransferExecutionVo(LocalDate.now(),"custodian-$source","custodian-$target","sign","逐件核对原影像，状态无变化","image",true,"sign","sign",
        appliedTransportStandardReference="GB/T 23862-2024",packingStandardConfirmed=true,packingCheckFileIds="sign")

    @Test fun `approved unexecuted transfer terminates only after four current actors and keeps original approval`(): Unit = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"]
        administrative(request);approve(request)
        val termination=CollectionTransferTerminationService(scope,statutory,locks,archives,binder,users)
        login("source",CollectionConst.STORAGE,"operator-source")
        val proposal=CollectionTransferTerminationProposalVo(LocalDateTime.now(),"operator-source",
            "原批准方案不再执行，待另建逐件方案","合成撤回决定-1","approval","仍在原馆原库，无实际点交","archive")
        val proposalId=termination.propose("request",proposal)
        assertEquals(proposalId,termination.propose("request",proposal))
        assertThrows(BadRequestException::class.java) { termination.propose("request",proposal.copy(reason="改写旧方案")) }
        login("source")
        assertThrows(BadRequestException::class.java) { service.execute("request",execution()) }
        assertEquals(1,CollectionOperationLockDao.all().count())
        val early=CollectionTransferTerminationConfirmationVo(proposalId,"target_leader",LocalDateTime.now(),"leader-target","本人确认","sign")
        login("target",CollectionConst.STORAGE,"leader-target")
        assertThrows(BadRequestException::class.java) { termination.confirm("request",early) }
        assertEquals(1,CollectionOperationLockDao.all().count())
        assertFalse(termination.detail("request")["closed"] as Boolean)
        // 接收馆只保留本单查阅权限及当前指定职务，不能借确认入口取得通用藏品写权限。
        actorRole("keeper-target", CollectionConst.ROLE_STORAGE_HEAD)
        actorRole("leader-target", CollectionConst.ROLE_DIRECTOR)
        actorRole("keeper-target", "TARGET_TRANSFER_READ", setOf(CollectionConst.SELECT))
        actorRole("leader-target", "TARGET_TRANSFER_READ", setOf(CollectionConst.SELECT))
        exec("DELETE FROM sys_role_permission WHERE role_id IN ('role:FILE_keeper-target','role:FILE_leader-target')")
        assertFalse(CollectionConst.STORAGE in CollectionActorDirectory(scope).requireActive("keeper-target").permissions)
        assertFalse(CollectionConst.STORAGE in CollectionActorDirectory(scope).requireActive("leader-target").permissions)
        for ((node,org,actor) in listOf(
            Triple("source_keeper","source","keeper-source"),Triple("source_leader","source","leader-source"),
            Triple("target_keeper","target","keeper-target"),Triple("target_leader","target","leader-target"))) {
            login(org,if(org=="target") CollectionConst.SELECT else CollectionConst.STORAGE,actor)
            val value=CollectionTransferTerminationConfirmationVo(proposalId,node,LocalDateTime.now(),actor,"本人确认原批准方案不再执行","sign")
            if(node=="target_leader") {
                io.hizhengfu.cloud.sys.system.dao.UserDao[actor].enabled=false
                assertThrows(BadRequestException::class.java) { termination.confirm("request",value) }
                assertEquals(1,CollectionOperationLockDao.all().count())
                assertEquals(4,CollectionTransferTerminationDao.all().count())
                io.hizhengfu.cloud.sys.system.dao.UserDao[actor].enabled=true
            }
            val confirmed=termination.confirm("request",value)
            assertEquals(confirmed,termination.confirm("request",value))
            if(node!="target_leader") assertEquals(1,CollectionOperationLockDao.all().count())
        }
        assertEquals(0,CollectionOperationLockDao.all().count())
        assertEquals(true,termination.detail("request")["closed"])
        assertEquals(EnumFlowStatus.PASS,request.flowStatus)
        assertNull(request.executedAt)
        assertEquals("source",CollectionDao["object"].orgId)
        assertEquals(0,StorageMovementDao.all().count())
        assertEquals(0,CollectionRetainedLedgerDao.all().count())
        assertEquals(1,CollectionPermanentArchiveDao.all().count { it.bizId=="request" && it.phase.startsWith("APPROVED_") })
        login("source")
        assertThrows(BadRequestException::class.java) { service.execute("request",execution()) }
        CollectionTransferTerminationDao.all().single { it.kind == EnumTransferTerminationRecord.PROPOSAL }.content="{}"
        assertThrows(BadRequestException::class.java) { termination.detail("request") }
    } }

    @Test fun `executed transfer cannot be turned into an approved withdrawal`(): Unit = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"]
        administrative(request);approve(request);service.execute("request",execution())
        val termination=CollectionTransferTerminationService(scope,statutory,locks,archives,binder,users)
        login("source",CollectionConst.STORAGE,"operator-source")
        val proposal=CollectionTransferTerminationProposalVo(LocalDateTime.now(),"operator-source",
            "错误地要求终止已交接申请","合成撤回决定-2","approval","已实际交接","archive")
        assertThrows(BadRequestException::class.java) { termination.propose("request",proposal) }
        assertEquals(0,CollectionTransferTerminationDao.all().count())
        assertEquals("target",CollectionDao["object"].orgId)
        assertEquals(2,StorageMovementDao.all().count())
    } }

    private fun approvedExchangePair() {
        val first = CollectionTransferDao["request"]
        makeCollection("other", "target", "000456")
        val second = makeRequest("opposite", "other", "target", "source")
        val ids = setOf("request", "opposite")
        first.dossier = first.dossier!!.copy(kind = EnumCollectionTransferKind.STATE_EXCHANGE,
            exchangeTransferIds = ids, exchangeAgreementFileIds = "agreement")
        second.dossier = second.dossier!!.copy(kind = EnumCollectionTransferKind.STATE_EXCHANGE,
            exchangeTransferIds = ids, exchangeAgreementFileIds = "agreement")
        administrative(first); administrative(first, true); approve(first)
        administrative(second); administrative(second, true); approve(second)
    }

    @Test fun `approved unexecuted exchange closes both approvals and locks atomically after both sets of confirmations`(): Unit = fixture { db ->
        val termination = CollectionTransferTerminationService(scope, statutory, locks, archives, binder, users)
        transaction(db) {
        approvedExchangePair()
        fun propose(id: String, org: String): String {
            login(org, CollectionConst.STORAGE, "operator-$org")
            return termination.propose(id, CollectionTransferTerminationProposalVo(LocalDateTime.now(), "operator-$org",
                "整组交换撤回", "合成撤回决定-交换", "approval", "仍在原馆原库", "archive"))
        }
        val first = propose("request", "source")
        login("target")
        assertThrows(BadRequestException::class.java) { service.execute("opposite", execution("target", "source")) }
        assertEquals(2, CollectionOperationLockDao.count())
        val second = propose("opposite", "target")
        for ((id, proposal) in listOf("request" to first, "opposite" to second)) {
            val source = if (id == "request") "source" else "target"
            val target = if (id == "request") "target" else "source"
            for ((node, org, actor) in listOf(
                Triple("source_keeper", source, "keeper-$source"), Triple("source_leader", source, "leader-$source"),
                Triple("target_keeper", target, "keeper-$target"), Triple("target_leader", target, "leader-$target"))) {
                login(org, CollectionConst.STORAGE, actor)
                termination.confirm(id, CollectionTransferTerminationConfirmationVo(proposal, node, LocalDateTime.now(),
                    actor, "本人确认原交换方案不再执行", "sign"))
            }
            if (id == "request") {
                assertEquals(0, CollectionTransferTerminationDao.all().count { it.kind == EnumTransferTerminationRecord.CLOSED })
                assertEquals(2, CollectionOperationLockDao.count())
                assertEquals(1, termination.detail(id)["groupReadyCount"])
            }
        }
        assertEquals(2, CollectionTransferTerminationDao.all().count { it.kind == EnumTransferTerminationRecord.CLOSED })
        }
        transaction(db) {
        assertEquals(0, CollectionOperationLockDao.all().count())
        assertEquals(2, termination.detail("opposite")["groupClosedCount"])
        assertEquals(false, termination.detail("opposite")["groupPending"])
        assertEquals(EnumFlowStatus.PASS, CollectionTransferDao["request"].flowStatus)
        assertEquals(EnumFlowStatus.PASS, CollectionTransferDao["opposite"].flowStatus)
        assertNull(CollectionTransferDao["request"].executedAt)
        assertNull(CollectionTransferDao["opposite"].executedAt)
        assertEquals("source", CollectionDao["object"].orgId)
        assertEquals("target", CollectionDao["other"].orgId)
        assertEquals(0, StorageMovementDao.count())
        assertEquals(0, CollectionRetainedLedgerDao.count())
        assertEquals(2, CollectionPermanentArchiveDao.all().count { it.phase.startsWith("APPROVED_") })
        login("source")
        assertThrows(BadRequestException::class.java) { service.execute("request", execution()) }
        login("target")
        assertThrows(BadRequestException::class.java) { service.execute("opposite", execution("target", "source")) }
        }
    }

    @Test fun `exchange withdrawal rejects different common authority`(): Unit = fixture { db -> transaction(db) {
        approvedExchangePair()
        val termination = CollectionTransferTerminationService(scope, statutory, locks, archives, binder, users)
        login("source", CollectionConst.STORAGE, "operator-source")
        termination.propose("request", CollectionTransferTerminationProposalVo(LocalDateTime.now(), "operator-source",
            "整组交换撤回", "合成撤回决定-交换", "approval", "仍在原馆原库", "archive"))
        login("target", CollectionConst.STORAGE, "operator-target")
        val different = CollectionTransferTerminationProposalVo(LocalDateTime.now(), "operator-target",
            "整组交换撤回", "另一撤回决定", "approval", "仍在原馆原库", "archive")
        assertThrows(BadRequestException::class.java) { termination.propose("opposite", different) }
        assertEquals(1, CollectionTransferTerminationDao.count())
    } }

    @Test fun `exchange withdrawal rejects an already executed group member`(): Unit = fixture { db -> transaction(db) {
        approvedExchangePair()
        login("source")
        service.execute("request", execution())
        val termination = CollectionTransferTerminationService(scope, statutory, locks, archives, binder, users)
        login("target", CollectionConst.STORAGE, "operator-target")
        val proposal = CollectionTransferTerminationProposalVo(LocalDateTime.now(), "operator-target",
            "错误撤回", "合成撤回决定-交换", "approval", "仍在原馆原库", "archive")
        assertThrows(BadRequestException::class.java) { termination.propose("opposite", proposal) }
        assertEquals(0, CollectionTransferTerminationDao.count())
    } }

    @Test fun `exchange final confirmation rolls back when either original approval archive is invalid`(): Unit = fixture { db ->
        val termination = CollectionTransferTerminationService(scope, statutory, locks, archives, binder, users)
        var finalProposal = ""
        transaction(db) {
            approvedExchangePair()
            for ((id, org) in listOf("request" to "source", "opposite" to "target")) {
                login(org, CollectionConst.STORAGE, "operator-$org")
                val proposal = termination.propose(id, CollectionTransferTerminationProposalVo(LocalDateTime.now(),
                    "operator-$org", "整组交换撤回", "合成撤回决定-交换", "approval", "仍在原馆原库", "archive"))
                val target = if (org == "source") "target" else "source"
                for ((node, actorOrg, actor) in listOf(
                    Triple("source_keeper", org, "keeper-$org"), Triple("source_leader", org, "leader-$org"),
                    Triple("target_keeper", target, "keeper-$target"), Triple("target_leader", target, "leader-$target"))) {
                    if (id == "opposite" && node == "target_leader") { finalProposal = proposal; break }
                    login(actorOrg, CollectionConst.STORAGE, actor)
                    termination.confirm(id, CollectionTransferTerminationConfirmationVo(proposal, node,
                        LocalDateTime.now(), actor, "本人确认原交换方案不再执行", "sign"))
                }
            }
            CollectionPermanentArchiveDao.all().single { it.bizId == "request" && it.phase.startsWith("APPROVED_") }.digest = "invalid"
        }
        assertThrows(BadRequestException::class.java) {
            transaction(db) {
                login("source", CollectionConst.STORAGE, "leader-source")
                termination.confirm("opposite", CollectionTransferTerminationConfirmationVo(finalProposal,
                    "target_leader", LocalDateTime.now(), "leader-source", "本人确认原交换方案不再执行", "sign"))
            }
        }
        transaction(db) {
            assertEquals(7, CollectionTransferTerminationDao.all().count { it.kind != EnumTransferTerminationRecord.PROPOSAL })
            assertEquals(0, CollectionTransferTerminationDao.all().count { it.kind == EnumTransferTerminationRecord.CLOSED })
            assertEquals(2, CollectionOperationLockDao.all().count())
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("disabled,new", "disabled,edit", "disabled,administrative", "revoked,new", "revoked,edit", "revoked,administrative", "moved,new", "moved,edit", "moved,administrative", "lost-storage,new", "lost-storage,edit", "lost-storage,administrative")
    fun `draft and administrative writes reject obsolete operator login`(kind:String,operation:String) = fixture { db ->
        val draft=transaction(db) { CollectionTransferDao["request"].toModel() }
        transaction(db) {
            login("source",CollectionConst.STORAGE)
            val account=io.hizhengfu.cloud.sys.system.dao.UserDao["operator-source"]
            when(kind) {
                "disabled" -> account.enabled=false
                "moved" -> account.groupId="outside"
                else -> {
                    io.hizhengfu.cloud.sys.system.dao.RoleUsers.deleteWhere { userId eq "operator-source" }
                    if(kind=="lost-storage")actorRole("operator-source","OPERATOR_READ_ONLY",setOf(CollectionConst.SELECT))
                }
            }
        }
        fun snapshot()=transaction(db) {
            listOf(CollectionTransfers,CollectionAdministrativeRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionOperationLocks).map { table ->
                table.selectAll().orderBy(table.id).map { row -> table.columns.map { row[it] } }
            }
        }
        val before=snapshot()
        assertThrows(BadRequestException::class.java) { transaction(db) {
            when(operation) {
                "new" -> service.save(draft.copy(id=null,reason="旧登录新建"))
                "edit" -> service.save(draft.copy(reason="旧登录修改"))
                else -> statutory.recordAdministrative("request",CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.PROVINCE,action=EnumAdministrativeAction.APPROVAL,authorityName="测试行政机关",documentNo="测试1",issuedAt=LocalDate.of(2026,8,1),fileIds="approval"))
            }
        } }
        assertEquals(before,snapshot())
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.transaction.annotation.EnableTransactionManagement(proxyTargetClass=true)
    class AdministrativeTransactions

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("disabled,false", "revoked,false", "moved,false", "orgDisabled,false",
        "disabled,true", "revoked,true", "moved,true", "orgDisabled,true")
    fun `administrative archive insert rechecks current duty and rolls back`(fault:String,proxy:Boolean) = fixture { db ->
        login("source",CollectionConst.STORAGE)
        transaction(db) {
            val mutation=when(fault) {
                "disabled" -> "UPDATE sys_user SET enabled=${if(postgresFixture) "false" else "0"} WHERE id='operator-source'"
                "revoked" -> "DELETE FROM sys_role_user WHERE user_id='operator-source'"
                "moved" -> "UPDATE sys_user SET group_id='outside' WHERE id='operator-source'"
                else -> "UPDATE col_org SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='source'"
            }
            val current=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
            if(postgresFixture) {
                current.exec("CREATE FUNCTION transfer_admin_fault_fn() RETURNS TRIGGER LANGUAGE plpgsql AS ${"$"}fault${"$"} BEGIN $mutation; RETURN NEW; END ${"$"}fault${"$"}")
                current.exec("CREATE TRIGGER transfer_admin_fault AFTER INSERT ON ${CollectionPermanentArchives.tableName} FOR EACH ROW WHEN (NEW.phase LIKE 'ADMIN_%') EXECUTE FUNCTION transfer_admin_fault_fn()")
            } else current.exec("CREATE TRIGGER transfer_admin_fault AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.phase LIKE 'ADMIN_%' BEGIN $mutation; END")
        }
        fun snapshot()=transaction(db) {
            listOf(CollectionTransfers,CollectionAdministrativeRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
                io.hizhengfu.cloud.sys.system.dao.Users,io.hizhengfu.cloud.sys.system.dao.RoleUsers,CollectionOrgs,FileContents).map { table ->
                table.selectAll().map { row -> table.columns.map { row[it] }.toString() }.sorted()
            }
        }
        val before=snapshot()
        fun perform() {
            login("source",CollectionConst.STORAGE)
            if(!proxy) { transaction(db) { administrative(CollectionTransferDao["request"]) }; return }
            val manager=org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager(fixtureDataSource)
            org.springframework.context.annotation.AnnotationConfigApplicationContext().use { context ->
                context.beanFactory.registerSingleton("transactionManager",manager)
                context.register(AdministrativeTransactions::class.java)
                context.registerBean(CollectionTransferStatutoryService::class.java,java.util.function.Supplier {statutory})
                context.refresh()
                val actual=context.getBean(CollectionTransferStatutoryService::class.java)
                assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(actual))
                actual.recordAdministrative("request",CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.PROVINCE,
                    action=EnumAdministrativeAction.APPROVAL,authorityName="文物行政部门",documentNo="文物函2026-1",
                    issuedAt=LocalDate.of(2026,8,1),fileIds="approval"))
            }
        }
        assertThrows(BadRequestException::class.java) { perform() }
        assertEquals(before,snapshot())
        transaction(db) {
            val current=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
            current.exec("DROP TRIGGER transfer_admin_fault"+if(postgresFixture)" ON ${CollectionPermanentArchives.tableName}" else "")
            if(postgresFixture)current.exec("DROP FUNCTION transfer_admin_fault_fn()")
        }
        perform()
        transaction(db) {
            assertEquals(1L,CollectionAdministrativeRecordDao.count())
            assertEquals(2L,CollectionPermanentArchiveDao.count())
            assertEquals(EnumFlowStatus.DRAFT,CollectionTransferDao["request"].flowStatus)
        }
    }

    @ParameterizedTest @ValueSource(strings=["joined-source","moved-to-target"])
    fun `transfer write hints follow current museum rather than login snapshot`(kind:String) = fixture { db -> transaction(db) {
        if(kind=="joined-source") {
            login("outside",CollectionConst.STORAGE)
            io.hizhengfu.cloud.sys.system.dao.UserDao["operator-outside"].groupId="source"
        } else {
            login("source",CollectionConst.STORAGE)
            io.hizhengfu.cloud.sys.system.dao.UserDao["operator-source"].groupId="target"
        }
        val result=service.get("request")
        assertEquals(kind=="joined-source",result.sourceWritable)
        assertEquals(kind=="joined-source",result.proposalEditable)
    } }

    @ParameterizedTest @ValueSource(strings=["storage","ledger","lost-storage"])
    fun `proposal hint requires current preparation duty without hiding ledger museum scope`(kind:String) = fixture { db -> transaction(db) {
        login("source",if(kind=="ledger") CollectionConst.LEDGER_CORRECT else CollectionConst.STORAGE)
        if(kind=="lost-storage") {
            io.hizhengfu.cloud.sys.system.dao.RoleUsers.deleteWhere { userId eq "operator-source" }
            actorRole("operator-source","CURRENT_READER",setOf(CollectionConst.SELECT))
        }
        val result=service.get("request")
        assertEquals(true,result.sourceWritable)
        assertEquals(kind=="storage",result.proposalEditable)
    } }

    @Test fun `explicit historical read does not mark disabled source museum writable`() = fixture { db -> transaction(db) {
        login("source",CollectionConst.STORAGE)
        actorRole("operator-source","HISTORICAL_READER",setOf(CollectionConst.CROSS_ORG))
        CollectionOrgDao["source"].orgStatus=EnumOrgStatus.DISABLED
        val result=service.get("request")
        assertEquals(false,result.sourceWritable)
        assertEquals(false,result.proposalEditable)
    } }

    @Test fun `new current museum assignment permits execution despite old login group`() = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"];administrative(request);approve(request)
        login("outside")
        io.hizhengfu.cloud.sys.system.dao.UserDao["registrar-outside"].groupId="source"
        assertEquals("request",service.execute("request",execution()))
        assertEquals("registrar-outside",request.executedBy)
        assertEquals("target",CollectionDao["object"].orgId)
    } }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("disabled,false", "disabled,true", "revoked,false", "revoked,true", "moved,false", "moved,true", "incompatible,false", "incompatible,true", "lost-ledger,false", "lost-ledger,true")
    fun `execution including retry requires current ledger duty and compatible museum role`(kind:String,retry:Boolean) = fixture { db ->
        transaction(db) {
            val request=CollectionTransferDao["request"];administrative(request);approve(request)
            if(retry)service.execute("request",execution())
            val account=io.hizhengfu.cloud.sys.system.dao.UserDao["registrar-source"]
            when(kind) {
                "disabled" -> account.enabled=false
                "revoked" -> io.hizhengfu.cloud.sys.system.dao.RoleUsers.deleteWhere { userId eq "registrar-source" }
                "moved" -> account.groupId="outside"
                "incompatible" -> actorRole("registrar-source","ADDED_KEEPER",setOf(CollectionConst.STORAGE))
                else -> {
                    io.hizhengfu.cloud.sys.system.dao.RoleUsers.deleteWhere { userId eq "registrar-source" }
                    actorRole("registrar-source","NOW_READ_ONLY",setOf(CollectionConst.SELECT))
                }
            }
        }
        fun snapshot()=transaction(db) {
            listOf(Collections,CollectionRegistrationNumbers,CollectionTransfers,CollectionRetainedLedgers,
                CollectionCatalogs,CollectionCatalogVersions,CollectionLedgerLogs,StorageMovements,
                CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionOperationLocks).map { table ->
                table.selectAll().orderBy(table.id).map { row -> table.columns.map { row[it] } }
            }
        }
        val before=snapshot()
        assertThrows(BadRequestException::class.java) { transaction(db) { service.execute("request",execution()) } }
        assertEquals(before,snapshot())
    }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("disabled,false", "disabled,true", "revoked,false", "revoked,true", "moved,false", "moved,true", "incompatible,false", "incompatible,true", "unchanged,false", "unchanged,true")
    fun `execution rechecks current ledger qualification after museum lock wait`(kind: String, retry: Boolean) = fixture { db ->
        check(postgresFixture)
        transaction(db) {
            val request = CollectionTransferDao["request"]
            administrative(request); approve(request)
            if (retry) service.execute("request", execution())
        }
        fun snapshot() = transaction(db) {
            listOf(Collections,CollectionRegistrationNumbers,CollectionTransfers,CollectionRetainedLedgers,
                CollectionCatalogs,CollectionCatalogVersions,CollectionLedgerLogs,StorageMovements,
                CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionOperationLocks).map { table ->
                table.selectAll().orderBy(table.id).map { row -> table.columns.map { row[it] } }
            }
        }
        val before = snapshot()
        val pid = java.util.concurrent.atomic.AtomicInteger()
        val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
        fixtureDataSource.connection.use { holder ->
            holder.autoCommit = false
            holder.createStatement().use { it.executeQuery("SELECT id FROM col_org WHERE id='source' FOR UPDATE").close() }
            try {
                val pending = pool.submit<Result<String>> {
                    login("source")
                    try { runCatching { transaction(db) {
                        exec("SELECT pg_backend_pid()") { rs -> rs.next(); pid.set(rs.getInt(1)) }
                        service.execute("request", execution())
                    } } } finally { SecurityContextHolder.clearContext() }
                }
                val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8)
                var waiting = false
                while (System.nanoTime() < deadline && !waiting) {
                    assertFalse(pending.isDone, "Execution must wait for the held museum lock")
                    waiting = fixtureDataSource.connection.use { con ->
                        con.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock')").use { statement ->
                            statement.setInt(1,pid.get());statement.executeQuery().use { rs -> rs.next();rs.getBoolean(1) }
                        }
                    }
                    if (!waiting) Thread.sleep(20)
                }
                assertTrue(waiting, "Observed actual PostgreSQL lock wait")
                transaction(db) {
                    when (kind) {
                        "disabled" -> io.hizhengfu.cloud.sys.system.dao.UserDao["registrar-source"].enabled=false
                        "revoked" -> io.hizhengfu.cloud.sys.system.dao.RoleUsers.deleteWhere { userId eq "registrar-source" }
                        "moved" -> io.hizhengfu.cloud.sys.system.dao.UserDao["registrar-source"].groupId="outside"
                        "incompatible" -> actorRole("registrar-source","ADDED_KEEPER",setOf(CollectionConst.STORAGE))
                    }
                }
                holder.commit()
                val result = pending.get(15,java.util.concurrent.TimeUnit.SECONDS)
                if (kind == "unchanged") {
                    assertEquals("request",result.getOrThrow())
                    transaction(db) {
                        assertEquals("target",CollectionDao["object"].orgId)
                        assertEquals(2,StorageMovementDao.all().count())
                        assertEquals(1,CollectionRetainedLedgerDao.all().count())
                    }
                    if (retry) assertEquals(before,snapshot(),"Authorized repeat must not duplicate business data")
                } else {
                    assertTrue(result.exceptionOrNull() is BadRequestException, "Changed qualification must reject after the wait: $result")
                    assertEquals(before,snapshot(),"Rejected execution must preserve all eleven business tables")
                }
            } finally {
                holder.rollback()
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS))
            }
        }
    }

    @ParameterizedTest @ValueSource(strings=["disabled","revoked","moved"])
    fun `old login cannot read transfer after current account access changes`(kind:String) = fixture { db -> transaction(db) {
        login("source")
        assertEquals("request",service.get("request").id)
        val account=io.hizhengfu.cloud.sys.system.dao.UserDao["registrar-source"]
        when(kind) {
            "disabled" -> account.enabled=false
            "revoked" -> io.hizhengfu.cloud.sys.system.dao.RoleUsers.deleteWhere { userId eq "registrar-source" }
            else -> account.groupId="outside"
        }
        assertThrows(BadRequestException::class.java) { service.get("request") }
        assertThrows(BadRequestException::class.java) { service.archive("request") }
        assertThrows(BadRequestException::class.java) { statutory.receivingLocations("request") }
        val request=org.springframework.mock.web.MockHttpServletRequest().also { it.setParameter("pageSize","0") }
        if(kind=="moved") assertTrue((service.findPage(request).list as List<*>).isEmpty())
        else {
            assertThrows(BadRequestException::class.java) { service.findPage(request) }
            assertThrows(BadRequestException::class.java) { statutory.parties() }
        }
    } }

    @Test fun `both transfer parties can resolve verified case originals without exposing receiving main files`() = fixture { db -> transaction(db) {
        val current = "2101817225653653504"
        val historic = "2101817226207301632"
        val receivedOnly = "2101817226207301633"
        val unrelated = "2101817226207301634"
        listOf(current, historic, receivedOnly, unrelated).forEach { id ->
            FileDao.new(id) { name="$id.pdf"; path="/files/$id.pdf"; hash=id; size=100; ext="pdf" }
        }
        CollectionTransferDao["request"].attachmentIds=current
        archives.seal("source", CollectionConst.BIZ_TRANSFER, "request", "TEST_PREVIOUS", mapOf("reason" to "历史方案"), historic)
        archives.seal("target", CollectionConst.BIZ_TRANSFER, "request", "RECEIVED", mapOf("reason" to "接收馆主档"), receivedOnly)
        for (org in listOf("source", "target")) {
            login(org)
            val display=service.archive("request")
            assertEquals(listOf("TEST_PREVIOUS"),display.map {it["phase"]})
            @Suppress("UNCHECKED_CAST")
            val files=display.single()["files"] as List<Map<String,Any?>>
            assertEquals(historic,files.single()["fileId"])
            assertTrue((files.single()["metadata"] as String).contains("$historic.pdf"))
            service.requireOriginal("request", current)
            service.requireOriginal("request", historic)
            assertThrows(BadRequestException::class.java) { service.requireOriginal("request", receivedOnly) }
            assertThrows(BadRequestException::class.java) { service.requireOriginal("request", unrelated) }
            assertThrows(BadRequestException::class.java) { service.requireOriginal("request", "../file") }
        }
        login("outside")
        assertThrows(BadRequestException::class.java) { service.archive("request") }
        assertThrows(BadRequestException::class.java) { service.requireOriginal("request", current) }
    } }

    @ParameterizedTest @ValueSource(strings=["missing","changed"])
    fun `executed transfer reads original number independently of current master`(kind:String) = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"];administrative(request);approve(request)
        service.execute("request",execution())
        if(kind=="missing") CollectionDao["object"].delete() else CollectionDao["object"].totalRegNo="ERR-CURRENT"
        login("source")
        assertEquals("000123",service.get("request").collectionTotalRegNo)
        @Suppress("UNCHECKED_CAST")
        val rows=service.findPage(org.springframework.mock.web.MockHttpServletRequest().also { it.setParameter("pageSize","0") }).list as List<CollectionTransferVo>
        assertEquals("000123",rows.single { it.id=="request" }.collectionTotalRegNo)
        CollectionRetainedLedgerDao.all().single().totalRegNo="ERR-RETAINED"
        assertThrows(BadRequestException::class.java) { service.get("request") }
    } }

    @Test fun `source transfer list keeps approved name after receiving museum correction`() = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"];administrative(request);approve(request)
        service.execute("request",execution())
        CollectionDao["object"].name="接收馆后来订正的名称"
        login("source")
        assertEquals("已登记藏品 object",service.get("request").collectionName)
        @Suppress("UNCHECKED_CAST")
        val rows=service.findPage(org.springframework.mock.web.MockHttpServletRequest().also { it.setParameter("pageSize","0") }).list as List<CollectionTransferVo>
        assertEquals("已登记藏品 object",rows.single { it.id=="request" }.collectionName)
        assertEquals("已登记藏品 object",CollectionRetainedLedgerDao.all().single().name)
        fun search(keyword: String): List<CollectionTransferVo> {
            @Suppress("UNCHECKED_CAST")
            return service.findPage(org.springframework.mock.web.MockHttpServletRequest().also {
                it.setParameter("pageSize","0");it.setParameter("keyword",keyword)
            }).list as List<CollectionTransferVo>
        }
        assertEquals(listOf("request"),search("已登记藏品 object").map { it.id })
        assertTrue(search("接收馆后来订正的名称").isEmpty())
    } }

    @Test fun `draft service preserves baseline and target location directory is limited to the parties`() = fixture { db -> transaction(db) {
        login("source",CollectionConst.STORAGE)
        val id=service.save(CollectionTransferVo(collectionId="object",toOrgId="target",reason="依法调拨"))
        assertEquals("source",service.get(id).orgId)
        assertEquals("已登记藏品 object",service.get(id).baseline?.formalName)
        assertEquals(listOf("target-warehouse"),statutory.receivingLocations(id).map { it["value"] })
        val saved=service.get(id)
        service.save(saved.copy(dossier=CollectionTransferDao["request"].dossier))
        administrative(CollectionTransferDao[id])
        login("source",CollectionConst.STORAGE)
        assertThrows(BadRequestException::class.java) { service.save(service.get(id).copy(reason="试图替换已批准行政依据的范围")) }
        login("outside")
        assertThrows(BadRequestException::class.java) { statutory.receivingLocations(id) }
    } }
    @Test fun `concurrent SQLite conflict can retry without duplicate handover or partial ledger`() = fixture { db ->
        transaction(db) { val request=CollectionTransferDao["request"];administrative(request);approve(request) }
        val ready=java.util.concurrent.CountDownLatch(2)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val calls=(1..2).map { pool.submit<String> {
                login("source")
                try { transaction(db) { ready.countDown();check(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));service.execute("request",execution()) } }
                finally { SecurityContextHolder.clearContext() }
            } }
            val results=calls.map { runCatching { it.get(20,java.util.concurrent.TimeUnit.SECONDS) } }
            assertTrue(results.any { it.getOrNull() == "request" })
            results.mapNotNull { it.exceptionOrNull() }.forEach { failure ->
                assertTrue(generateSequence(failure as Throwable?) { it.cause }.any { it is org.sqlite.SQLiteException && it.message.orEmpty().contains("SQLITE_BUSY") })
            }
            // SQLite 无行级 FOR UPDATE，竞争者事务可能返回 SQLITE_BUSY；新事务重试同一业务 ID。
            login("source")
            transaction(db) {
                assertEquals("request",service.execute("request",execution()));assertEquals(2,StorageMovementDao.all().count());assertEquals(1,CollectionRetainedLedgerDao.all().count());assertEquals("target",CollectionDao["object"].orgId)
                val events=CollectionLedgerStatEventDao.all().toList()
                assertEquals(2,events.size)
                assertEquals(setOf("source" to EnumLedgerStatEvent.TRANSFER_OUT,"target" to EnumLedgerStatEvent.TRANSFER_IN),events.map { it.orgId to it.kind }.toSet())
                assertTrue(events.all { it.sourceId=="request" })
            }
        } finally { pool.shutdownNow() }
    }

    @Test fun `transfer preparation cannot treat approved catalog as established archive`()=fixture { db -> transaction(db) {
        doThrow(BadRequestException("实际建档目录和原件缺失")).`when`(archivePackets).requireEstablished(CollectionDao["object"])
        assertTrue(assertThrows(BadRequestException::class.java) { statutory.requireProposal(CollectionTransferDao["request"]) }.message!!.contains("实际建档目录"))
    } }
    @Test fun `cross museum transport needs approved plan and signed standard check before ownership changes`() = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"]
        val dossier=request.dossier!!
        listOf(
            dossier.copy(transportStandardReference=null),
            dossier.copy(transportStandardApplicability=null),
            dossier.copy(transportCarrierName=null),
            dossier.copy(transportCarrierCapabilityFileIds=null),
            dossier.copy(transportPlanFileIds=null),
        ).forEach { incomplete ->
            request.dossier=incomplete
            assertTrue(assertThrows(BadRequestException::class.java) { statutory.requireProposal(request) }.message!!.contains("包装运输标准"))
        }
        request.dossier=dossier
        administrative(request);approve(request)
        listOf(
            execution().copy(appliedTransportStandardReference=null),
            execution().copy(appliedTransportStandardReference="其他版本"),
            execution().copy(packingStandardConfirmed=false),
            execution().copy(packingCheckFileIds=null),
            execution().copy(packingCheckFileIds="missing-file"),
        ).forEach { incomplete ->
            assertThrows(BadRequestException::class.java) { service.execute("request",incomplete) }
            assertEquals("source",CollectionDao["object"].orgId)
            assertEquals(0,StorageMovementDao.all().count())
            assertEquals(0,CollectionRetainedLedgerDao.all().count())
        }
        service.execute("request",execution().copy(packingCheckFileIds="evidence"))
        assertEquals("target",CollectionDao["object"].orgId)
        val stages=CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq "request" }
            .filter { it.phase in setOf("EXECUTED","RECEIVED") }
        assertEquals(2,stages.size)
        stages.forEach { stage ->
            assertTrue(CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq stage.id.value }
                .any { it.fileId=="evidence" })
        }
    } }
    @Test fun `old approval and missing statutory authority cannot transfer ownership`() = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"]
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(request) }
        administrative(request)
        assertThrows(BadRequestException::class.java) { statutory.requireEditable(request) }
        request.flowInstanceId="legacy";request.flowStatus=EnumFlowStatus.PASS
        assertThrows(BadRequestException::class.java) { service.execute("request",execution()) }
        assertEquals("source",CollectionDao["object"].orgId);assertEquals(0,StorageMovementDao.all().count())
    } }
    @Test fun `first grade national filing donation agreement and target warehouse are enforced`() = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"];val node=CollectionDao["object"]
        node.level=EnumRelicLevel.FIRST;node.source=EnumSource.DONATE;request.baseline=CollectionCatalogMapper.fromCollection(node.toModel())
        assertThrows(BadRequestException::class.java) { administrative(request) }
        request.dossier=request.dossier!!.copy(noDonationAgreement=true,donorNoticeFileIds="agreement",donorWrittenConsentFileIds="consent",targetWarehouseId="source-warehouse")
        assertThrows(BadRequestException::class.java) { administrative(request) }
        request.dossier=request.dossier!!.copy(targetWarehouseId="target-warehouse")
        administrative(request)
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(request) }
        administrative(request,true)
        assertDoesNotThrow { statutory.requirePrepared(request) }
    } }
    @Test fun `approval waits for signatures and execution atomically preserves both ledgers and associated files`() = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"];administrative(request);approve(request)
        assertEquals(EnumFlowStatus.PASS,request.flowStatus);assertEquals("source",CollectionDao["object"].orgId)
        assertThrows(BadRequestException::class.java) { locks.requireUnlocked("object") }
        assertThrows(BadRequestException::class.java) { service.execute("request",execution().copy(authenticityVerified=false)) }
        login("source",CollectionConst.STORAGE)
        assertThrows(BadRequestException::class.java) { service.execute("request",execution()) }
        login("source");service.execute("request",execution());service.execute("request",execution())
        val node=CollectionDao["object"]
        assertEquals("target",node.orgId);assertEquals("target-warehouse",node.warehouseId);assertEquals("000123",node.totalRegNo)
        val warehouseLogs=CollectionLedgerLogDao.all().filter { it.collectionId=="object" && it.field=="warehouseId" }
        assertEquals(1,warehouseLogs.size)
        assertEquals("target",warehouseLogs.single().orgId)
        assertEquals("source-warehouse",warehouseLogs.single().before)
        assertEquals("target-warehouse",warehouseLogs.single().after)
        assertEquals("request",warehouseLogs.single().refBizId)
        val receiptLogs=CollectionLedgerLogDao.all().filter { it.collectionId=="object" && it.field=="entryVoucherNo" }
        assertEquals(1,receiptLogs.size)
        assertEquals("target",receiptLogs.single().orgId)
        assertEquals("request",receiptLogs.single().after)
        assertEquals("request",receiptLogs.single().refBizId)
        assertEquals(2,StorageMovementDao.all().count());assertEquals(1,CollectionRetainedLedgerDao.all().count())
        assertEquals("source-warehouse",CollectionRetainedLedgerDao.all().single().snapshot.collection.warehouseId)
        assertEquals("target",CollectionMediaDao.all().single().orgId);assertEquals("target",CollectionMediaFolderDao.all().single().orgId);assertEquals("target",CollectionAttachmentDao.all().single().orgId)
        assertEquals("target",RfidTags.selectAll().single()[RfidTags.orgId])
        assertEquals("target-warehouse",CollectionCatalogVersionDao.all().maxBy { it.version }.content.warehouseId)
        assertDoesNotThrow { locks.requireUnlocked("object") }
        assertThrows(BadRequestException::class.java) { CollectionArchiveFileRetentionGuard().requireDeletable("sign") }
        assertThrows(BadRequestException::class.java) { statutory.requireNoExecutedExchange(request) }
    } }

    @Test fun `repeated transfer requires both sealed handover originals and the same execution`() = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"]
        administrative(request);approve(request);service.execute("request",execution())
        val before=StorageMovementDao.all().count()
        assertThrows(BadRequestException::class.java) {
            service.execute("request",execution().copy(conditionDescription="改写本次逐件状况"))
        }
        for (phase in listOf("EXECUTED","RECEIVED")) {
            val original=CollectionPermanentArchiveDao.all().single { it.bizId=="request" && it.phase==phase }
            val snapshot=original.snapshot
            original.snapshot="{}"
            assertThrows(BadRequestException::class.java) { service.execute("request",execution()) }
            original.snapshot=snapshot
        }
        service.execute("request",execution())
        assertEquals(before,StorageMovementDao.all().count())
        val retained=CollectionRetainedLedgerDao.all().single()
        val name=retained.name;retained.name="与原馆留账不一致"
        assertThrows(BadRequestException::class.java) { service.execute("request",execution()) }
        retained.name=name
        CollectionPermanentArchiveDao.all().single { it.bizId=="request" && it.phase=="RECEIVED" }.delete()
        assertThrows(BadRequestException::class.java) { service.execute("request",execution()) }
    } }

    @Test fun `completed transfer with historical empty packing fields remains verifiable`() = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"]
        administrative(request);approve(request);service.execute("request",execution())
        request.execution=request.execution!!.copy(appliedTransportStandardReference=null,
            packingStandardConfirmed=null,packingCheckFileIds=null)
        CollectionPermanentArchiveDao.all().filter { it.bizId=="request" && it.phase in setOf("EXECUTED","RECEIVED") }.forEach { original ->
            val snapshot=JsonUtils.mapper.readTree(original.snapshot) as tools.jackson.databind.node.ObjectNode
            val signed=snapshot.get("execution") as tools.jackson.databind.node.ObjectNode
            signed.remove("appliedTransportStandardReference")
            signed.remove("packingStandardConfirmed")
            signed.remove("packingCheckFileIds")
            original.snapshot=JsonUtils.toJson(snapshot)!!
            original.digest=CollectionCareEvidence.digest(original.snapshot)
        }
        val previous=StorageMovementDao.all().count()
        val archived=JsonUtils.mapper.readTree(CollectionPermanentArchiveDao.all().single { it.bizId=="request" && it.phase=="EXECUTED" }.snapshot)
        val current=JsonUtils.mapper.readTree(JsonUtils.toJson(request.toModel()))
        assertEquals(current.get("execution"),archived.get("execution"),"historical execution snapshot")
        assertEquals("request",service.execute("request",request.execution!!))
        assertEquals(previous,StorageMovementDao.all().count())
    } }

    @Test fun `source receipt remains verifiable after executed transfer without trusting the target current ledger`() = fixture { db -> transaction(db) {
        val collection=CollectionDao["object"]
        collection.entryVoucherNo="RJ-source-1"
        AcquisitionDao.new("receipt-acquisition") { orgId="source";name="入馆原物";source=EnumSource.ALLOCATE;collectionId="object" }
        EntryVoucherDao.new("receipt-voucher") { orgId="source";voucherNo="RJ-source-1";acquisitionIds="receipt-acquisition";verifiedAcquisitionIds="receipt-acquisition" }
        val checkedAt=LocalDateTime.of(2026,8,2,10,0)
        val content=AccessionReceiptVo("receipt-acquisition",checkedAt,"source-warehouse",setOf("keeper-source"),setOf("原移交人"),
            listOf(AccessionPieceVo("piece-1",checkedAt.minusHours(1),"实物标识","完好",true,true,"image")),true,"sign")
        val source=JsonUtils.toJson(mapOf("id" to "receipt-acquisition","orgId" to "source","actualAcquiredOn" to LocalDate.of(2026,8,1)))!!
        val receipt=AccessionReceiptDao.new("receipt") {
            orgId="source";voucherId="receipt-voucher";acquisitionId="receipt-acquisition";collectionId="object";version=1;sourceSnapshot=source;this.content=content
        }
        archives.seal("source",AccessionReceiptService.BIZ,receipt.id.value,"RECORDED",mapOf("voucherNo" to "RJ-source-1","source" to source,"content" to content),"image,sign")
        archives.seal("source",AccessionReceiptService.BIZ,receipt.id.value,"LINKED",mapOf("collectionId" to "object","totalRegNo" to "000123","content" to content),"image,sign")
        archives.seal("source",AccessionReceiptService.BIZ,receipt.id.value,"REGISTERED",mapOf("collectionId" to "object","totalRegNo" to "000123","registeredAt" to collection.registeredAt),"image,sign")
        val request=CollectionTransferDao["request"];administrative(request);approve(request);service.execute("request",execution())
        assertEquals("target",collection.orgId)
        assertEquals("request",collection.entryVoucherNo)
        assertTrue(CollectionLedgerLogDao.all().any { it.field=="entryVoucherNo" && it.before=="RJ-source-1" && it.after=="request" && it.refBizId=="request" })
        val readPolicy=CollectionMainFileReadPolicy(mock(CollectionFileReadAuthorization::class.java),scope,mock(CollectionActorDirectory::class.java))
        val evidence=AccessionReceiptEvidence(archives,readPolicy)
        val verified=evidence.requireRead(receipt,EntryVoucherDao["receipt-voucher"])
        assertEquals("source",verified.registeredCollection?.orgId)
        assertEquals("RJ-source-1",verified.registeredCollection?.entryVoucherNo)
        assertEquals(3,verified.archives.size)
        actorRole("registrar-source","RECEIPT_READER",setOf(CollectionConst.ACQUIRE))
        val reader=AccessionReceiptService(scope,mock(AcquisitionComplianceService::class.java),users,archives,readPolicy)
        val item=(reader.detail("receipt-voucher")["items"] as List<*>).single() as Map<*,*>
        assertEquals("000123",item["totalRegNo"])
        login("target")
        val returnRequest=makeRequest("return","object","target","source")
        administrative(returnRequest);approve(returnRequest);service.execute("return",execution("target","source"))
        login("source")
        assertEquals("source",collection.orgId)
        assertEquals("return",collection.entryVoucherNo)
        val voucherLogs=CollectionLedgerLogDao.all().filter { it.field=="entryVoucherNo" }
        assertEquals(2,voucherLogs.size)
        assertTrue(voucherLogs.any { it.orgId=="source" && it.before=="request" && it.after=="return" && it.refBizId=="return" })
        assertEquals("RJ-source-1",evidence.requireRead(receipt,EntryVoucherDao["receipt-voucher"]).registeredCollection?.entryVoucherNo)
        val printer=CollectionPrintService(collections,scope,mock(CollectionPrintTemplateService::class.java),
            mock(LoanStatutoryService::class.java),receiptEvidence=evidence,archives=archives)
        assertEquals("RJ-source-1",printer.entryVoucherPrint("receipt-voucher").collections.single().entryVoucherNo)
        val retained=CollectionRetainedLedgerDao.all().single { it.transferId=="request" }
        val originalArchive=retained.archiveId
        retained.archiveId="invalid"
        assertThrows(BadRequestException::class.java) { evidence.requireRead(receipt,EntryVoucherDao["receipt-voucher"]) }
        retained.archiveId=originalArchive
        CollectionDao.new("unrelated") { orgId="source";totalRegNo="unrelated";name="其他藏品" }
        AcquisitionDao["receipt-acquisition"].collectionId="unrelated"
        assertThrows(BadRequestException::class.java) { reader.detail("receipt-voucher") }
    } }
    @Test fun `execution freezes ordinary main and child bindings in both museum archives`() = fixture { db -> transaction(db) {
        val child=CollectionMediaDao.all().single().id.value
        listOf("loose-main" to "object", "loose-child" to child).forEach { (file, owner) ->
            FileDao.new(file) { name="$file.pdf";path="/files/$file.pdf";hash=file;size=100;ext="pdf" }
            FileContents.insert { it[fileId]=file;it[contentId]=owner }
        }
        val request=CollectionTransferDao["request"];administrative(request);approve(request)
        service.execute("request",execution())
        val retained=CollectionRetainedLedgerDao.all().single()
        val expected=listOf("archive","image","loose-child","loose-main")
        assertEquals(expected,retained.snapshot.originalFileIds)
        val received=CollectionPermanentArchiveDao.all().single { it.bizId=="request" && it.phase=="RECEIVED" }
        val snapshot=JsonUtils.mapper.readTree(received.snapshot)
        assertEquals(retained.archiveId,snapshot.path("sourceOriginalArchiveId").asString())
        assertEquals(JsonUtils.mapper.readTree(JsonUtils.toJson(expected)),snapshot.path("originalFileIds"))
        listOf(retained.archiveId to "source",received.id.value to "target").forEach { (archiveId, org) ->
            val files=CollectionPermanentArchiveFileDao.all().filter { it.archiveId==archiveId }
            assertTrue(files.map { it.fileId }.containsAll(expected))
            assertTrue(files.all { it.orgId==org })
        }
        assertEquals("target",CollectionDao["object"].orgId)
    } }
    @ParameterizedTest @ValueSource(strings=["source-owner","source-duplicates","target-duplicates","source-main-duplicates"])
    fun `inconsistent number reservations reject actual transfer without changing either ledger`(kind:String)=fixture { db ->
        transaction(db) {
            val request=CollectionTransferDao["request"];administrative(request);approve(request)
            if(kind=="source-main-duplicates") {
                val index=Collections.indices.single { it.columns==listOf(Collections.orgId,Collections.totalRegNo) }.indexName
                val quoted = "\"" + index.replace("\"", "\"\"") + "\""
                org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec(
                    if (postgresFixture) "ALTER TABLE ${Collections.tableName} DROP CONSTRAINT $quoted" else "DROP INDEX $quoted")
                CollectionDao.new("legacy-duplicate") { orgId="source";totalRegNo="000123";name="原馆历史重号待核实" }
            } else if(kind=="source-owner") {
                makeCollection("another","source","000999")
                CollectionRegistrationNumberDao.all().single { it.totalRegNo=="000123" }.collectionId="another"
            } else {
                // 仅在隔离旧库夹具中移除唯一索引，模拟历史升级前已有的重复占号。
                val index=CollectionRegistrationNumbers.indices.single {
                    it.columns==listOf(CollectionRegistrationNumbers.orgId,CollectionRegistrationNumbers.totalRegNo)
                }.indexName
                val quoted = "\"" + index.replace("\"", "\"\"") + "\""
                org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec(
                    if (postgresFixture) "ALTER TABLE ${CollectionRegistrationNumbers.tableName} DROP CONSTRAINT $quoted" else "DROP INDEX $quoted")
                val org=if(kind=="source-duplicates")"source" else "target"
                if(org=="target")numbers.markUsed(org,"000123","object","旧占号")
                CollectionRegistrationNumberDao.new("duplicate-reservation") {
                    orgId=org;totalRegNo="000123";collectionId="object";state=EnumRegistrationNoState.RETIRED;reason="旧占号重复待核实"
                }
            }
        }
        fun snapshot()=transaction(db) {
            listOf(Collections,CollectionRegistrationNumbers,CollectionTransfers,CollectionRetainedLedgers,
                CollectionCatalogs,CollectionCatalogVersions,CollectionLedgerLogs,StorageMovements,
                CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionMedias,CollectionMediaFolders,CollectionAttachments,
                CollectionOperationLocks).map { table -> table.selectAll().orderBy(table.id).map { row -> table.columns.map { row[it] } } }
        }
        val before=snapshot()
        val failure=assertThrows(BadRequestException::class.java) { transaction(db) { service.execute("request",execution()) } }
        assertTrue(failure.message.orEmpty().contains(when(kind) { "source-owner" -> "占号归属"; "source-main-duplicates" -> "总登记号"; else -> "多条占号" }),failure.message)
        assertEquals(before,snapshot())
        transaction(db) {
            assertEquals("source",CollectionDao["object"].orgId)
            assertEquals("000123",CollectionDao["object"].totalRegNo)
            assertNull(CollectionTransferDao["request"].executedAt)
            assertThrows(BadRequestException::class.java) { locks.requireUnlocked("object") }
        }
    }

    @Test fun `missing ordinary original rolls back transfer without releasing approval lock`() = fixture { db ->
        transaction(db) {
            val request=CollectionTransferDao["request"];administrative(request);approve(request)
            FileContents.insert { it[fileId]="missing-original";it[contentId]="object" }
        }
        assertThrows(BadRequestException::class.java) { transaction(db) { service.execute("request",execution()) } }
        transaction(db) {
            assertEquals("source",CollectionDao["object"].orgId)
            assertEquals("source",CollectionMediaDao.all().single().orgId)
            assertNull(CollectionTransferDao["request"].executedAt)
            assertEquals(0,CollectionRetainedLedgerDao.count())
            assertEquals(0,StorageMovementDao.count())
            assertTrue(CollectionPermanentArchiveDao.all().none { it.phase in setOf("TRANSFERRED","EXECUTED","RECEIVED") })
            assertThrows(BadRequestException::class.java) { locks.requireUnlocked("object") }
        }
    }
    @Test fun `target sees this incoming request while outsider cannot and only source can execute`() = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"];administrative(request);approve(request)
        login("target")
        assertEquals("request",service.get("request").id)
        assertEquals(false,service.get("request").sourceWritable)
        assertEquals(1,service.findPage(MockHttpServletRequest().also { it.setParameter("pageSize","0");it.setParameter("keyword","已登记藏品") }).data?.size)
        assertThrows(BadRequestException::class.java) { service.execute("request",execution()) }
        login("outside")
        assertThrows(BadRequestException::class.java) { service.get("request") }
        assertEquals(0,service.findPage(MockHttpServletRequest().also { it.setParameter("pageSize","0") }).data?.size)
    } }
    @Test fun `new round cannot reuse old approval archive and completed exchange cannot be restarted`() = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"];administrative(request);approve(request)
        FlowInstanceLogDao.new { instanceId=request.flowInstanceId!!;action="REACTIVATE";nodeKey="source_keeper";operator="admin" }
        assertThrows(BadRequestException::class.java) { service.execute("request",execution()) }
        approve(request,"2")
        assertDoesNotThrow { service.execute("request",execution()) }
    } }
    @Test fun `whole exchange group must share agreement and complete both approvals before any handover`() = fixture { db -> transaction(db) {
        val a=CollectionTransferDao["request"]
        makeCollection("other","target","000456");val b=makeRequest("opposite","other","target","source")
        val ids=setOf("request","opposite")
        a.dossier=a.dossier!!.copy(kind=EnumCollectionTransferKind.STATE_EXCHANGE,exchangeTransferIds=ids,exchangeAgreementFileIds="agreement")
        b.dossier=b.dossier!!.copy(kind=EnumCollectionTransferKind.STATE_EXCHANGE,exchangeTransferIds=ids,exchangeAgreementFileIds="sign")
        assertThrows(BadRequestException::class.java) { administrative(a) }
        b.dossier=b.dossier!!.copy(exchangeAgreementFileIds="agreement")
        administrative(a);administrative(a,true)
        assertThrows(BadRequestException::class.java) { statutory.requireEditable(b) }
        approve(a)
        assertThrows(BadRequestException::class.java) { service.execute("request",execution()) }
        administrative(b);administrative(b,true);approve(b)
        login("source");service.execute("request",execution())
        assertThrows(BadRequestException::class.java) { statutory.requireNoExecutedExchange(b) }
        login("target");service.execute("opposite",execution("target","source"))
        assertEquals("source",CollectionDao["other"].orgId);assertEquals(4,StorageMovementDao.all().count())
    } }
    @Test fun `failed receipt archive rolls back claim ownership ledger and movements`() = fixture { db ->
        transaction(db) { val request=CollectionTransferDao["request"];administrative(request);approve(request) }
        // 实际服务在过户之后归档签署文件，缺失原件触发整次事务回滚。
        assertThrows(BadRequestException::class.java) { transaction(db) { service.execute("request",execution().copy(signedHandoverFileIds="missing-file")) } }
        transaction(db) {
            assertNull(CollectionTransferDao["request"].executedAt);assertEquals("source",CollectionDao["object"].orgId)
            assertEquals(0,StorageMovementDao.all().count());assertEquals(0,CollectionRetainedLedgerDao.all().count())
            assertThrows(BadRequestException::class.java) { locks.requireUnlocked("object") }
        }
    }
    @ParameterizedTest @ValueSource(strings=["main", "reservation"])
    fun `receiving number collision is rejected before administrative archive or submission`(kind: String) = fixture { db -> transaction(db) {
        if (kind == "main") CollectionDao.new("target-owner") {
            orgId="target";totalRegNo="000123";name="接收馆既有藏品"
        } else CollectionRegistrationNumberDao.new {
            orgId="target";totalRegNo="000123";collectionId="target-owner"
            state=EnumRegistrationNoState.USED;reason="接收馆既有占号"
        }
        val request=CollectionTransferDao["request"]
        val archivesBefore=CollectionPermanentArchiveDao.count()
        val error=assertThrows(BadRequestException::class.java) { administrative(request) }
        assertTrue(error.message.orEmpty().contains("总登记号"))
        assertEquals(EnumFlowStatus.DRAFT,request.flowStatus)
        assertNull(request.flowInstanceId)
        assertTrue(statutory.administrative("request").isEmpty())
        assertEquals(archivesBefore,CollectionPermanentArchiveDao.count())
        assertEquals("source",CollectionDao["object"].orgId)
        assertEquals("000123",CollectionDao["object"].totalRegNo)
        assertEquals(0,CollectionRetainedLedgerDao.count())
    } }

    @ParameterizedTest @ValueSource(strings=["transfer","return","target-conflict"])
    fun `historical number remains exact through actual transfer`(operation:String)=fixture(number=" 000123 ") { db ->
        val originalNo=" 000123 "
        transaction(db) {
            // 模拟三处一致的既有原账，不通过新建输入规范化来准备历史数据。
            CollectionRegistrationNumberDao.all().single().totalRegNo=originalNo
            val request=CollectionTransferDao["request"];administrative(request);approve(request)
            if(operation=="target-conflict") CollectionRegistrationNumberDao.new {
                orgId="target";totalRegNo=originalNo;collectionId="another-object"
                state=EnumRegistrationNoState.USED;reason="接收馆既有占号"
            }
        }
        fun state()=transaction(db) {
            listOf(Collections,CollectionRegistrationNumbers,CollectionTransfers,CollectionRetainedLedgers,CollectionCatalogs,
                CollectionCatalogVersions,CollectionLedgerLogs,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
                CollectionOperationLocks).map { table -> table.selectAll().orderBy(table.id).map { row -> table.columns.map { row[it] } } }
        }
        if(operation=="target-conflict") {
            val before=state()
            val error=assertThrows(BadRequestException::class.java) { transaction(db) { service.execute("request",execution()) } }
            assertTrue(error.message.orEmpty().contains("占用"),error.message)
            assertEquals(before,state())
        } else {
            transaction(db) { service.execute("request",execution()) }
            transaction(db) {
                assertEquals("target",CollectionDao["object"].orgId)
                assertEquals(originalNo,CollectionDao["object"].totalRegNo)
                val rows=CollectionRegistrationNumberDao.all().toList()
                assertEquals(2,rows.size)
                assertTrue(rows.all { it.totalRegNo==originalNo && it.collectionId=="object" })
                assertEquals(EnumRegistrationNoState.RETIRED,rows.single { it.orgId=="source" }.state)
                assertEquals(EnumRegistrationNoState.USED,rows.single { it.orgId=="target" }.state)
                val ledger=CollectionRetainedLedgerDao.all().single()
                assertEquals(originalNo,ledger.totalRegNo);assertEquals(originalNo,ledger.snapshot.collection.totalRegNo)
            }
            if(operation=="return") {
                transaction(db) {
                    login("target");val request=makeRequest("return","object","target","source");administrative(request);approve(request)
                    service.execute("return",execution("target","source"))
                }
                transaction(db) {
                    assertEquals("source",CollectionDao["object"].orgId);assertEquals(originalNo,CollectionDao["object"].totalRegNo)
                    val rows=CollectionRegistrationNumberDao.all().toList()
                    assertEquals(2,rows.size);assertTrue(rows.all { it.totalRegNo==originalNo })
                    assertEquals(EnumRegistrationNoState.USED,rows.single { it.orgId=="source" }.state)
                    assertEquals(EnumRegistrationNoState.RETIRED,rows.single { it.orgId=="target" }.state)
                    val ledgers=CollectionRetainedLedgerDao.all().toList()
                    assertEquals(2,ledgers.size);assertTrue(ledgers.all { it.totalRegNo==originalNo && it.snapshot.collection.totalRegNo==originalNo })
                    assertTrue(CollectionLedgerLogDao.all().all { it.totalRegNo==originalNo })
                }
            }
        }
    }

    @ParameterizedTest @ValueSource(strings=["manual","automatic","incoming-other","return-original"])
    fun `retained source number stays occupied when its reservation row is missing`(operation:String)=fixture(number="000001") { db ->
        transaction(db) {
            val request=CollectionTransferDao["request"];administrative(request);approve(request);service.execute("request",execution())
            CollectionRegistrationNumbers.deleteWhere { orgId eq "source" }
            if(operation=="incoming-other") {
                login("outside");makeCollection("incoming-object","outside","000001")
                makeRequest("incoming","incoming-object","outside","source")
            }
        }
        fun retainedState()=transaction(db) {
            CollectionRetainedLedgers.selectAll().orderBy(CollectionRetainedLedgers.id).map { row -> CollectionRetainedLedgers.columns.map { row[it] } }
        }
        fun state()=transaction(db) {
            listOf(Collections,CollectionRegistrationNumbers,CollectionTransfers,CollectionRetainedLedgers,CollectionCatalogs,
                CollectionCatalogVersions,CollectionLedgerLogs,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
                CollectionOperationLocks).map { table -> table.selectAll().orderBy(table.id).map { row -> table.columns.map { row[it] } } }
        }
        val retained=retainedState()
        when(operation) {
            "manual","incoming-other" -> {
                val before=state()
                val error=assertThrows(BadRequestException::class.java) { transaction(db) {
                    if(operation=="manual")collections.createForOrg("source",CollectionVo(name="另一件藏品",totalRegNo="000001",acquireDate=LocalDate.of(2026,8,1)))
                    else administrative(CollectionTransferDao["incoming"])
                } }
                assertTrue(error.message.orEmpty().contains("留存总账"),error.message)
                assertEquals(before,state());assertEquals(retained,retainedState())
            }
            "automatic" -> {
                val id=transaction(db) { collections.createForOrg("source",CollectionVo(name="新预留藏品",acquireDate=LocalDate.of(2026,8,1))) }
                transaction(db) {
                    assertEquals("000002",CollectionDao[id].totalRegNo)
                    assertEquals("000002",CollectionRegistrationNumberDao.all().single { it.collectionId==id }.totalRegNo)
                    assertNull(CollectionDao[id].registeredAt)
                }
                assertEquals(retained,retainedState())
            }
            "return-original" -> {
                transaction(db) {
                    login("target");val request=makeRequest("return","object","target","source");administrative(request);approve(request)
                    service.execute("return",execution("target","source"))
                    assertEquals("source",CollectionDao["object"].orgId);assertEquals("000001",CollectionDao["object"].totalRegNo)
                    val reservation=CollectionRegistrationNumberDao.all().single { it.orgId=="source" }
                    assertEquals("object",reservation.collectionId);assertEquals(EnumRegistrationNoState.USED,reservation.state)
                }
                assertTrue(retainedState().containsAll(retained));assertEquals(2,retainedState().size)
            }
        }
    }

    @Test fun `same object returning to original museum restores its number without making it available to another`() = fixture { db -> transaction(db) {
        val a=CollectionTransferDao["request"];administrative(a);approve(a);service.execute("request",execution())
        login("target");val b=makeRequest("return","object","target","source");administrative(b);approve(b);service.execute("return",execution("target","source"))
        login("source");assertEquals("request",service.execute("request",execution()))
        assertEquals("source",CollectionDao["object"].orgId);assertEquals("000123",CollectionDao["object"].totalRegNo)
        assertEquals(2,CollectionRetainedLedgerDao.all().count())
        assertThrows(BadRequestException::class.java) { numbers.requireAvailable("source","000123","another") }
        assertThrows(BadRequestException::class.java) { numbers.requireAvailable("target","000123","another") }
    } }
    @Test fun `completed transfer requires real matching onsite and website result notices to close`() = fixture { db -> transaction(db) {
        val request=CollectionTransferDao["request"];administrative(request);approve(request);service.execute("request",execution())
        val onsite=DeregistrationNoticeVo(disclosureReviewId="review",kind=EnumExitNoticeKind.RESULT,channel=EnumExitNoticeChannel.MUSEUM_ONSITE,startedOn=LocalDate.now(),urlOrLocation="馆内公告栏",publicContentFileIds="public",publicationEvidenceFileIds="evidence")
        statutory.recordNotice("request",onsite);assertNull(request.announcedAt)
        assertThrows(BadRequestException::class.java) { statutory.recordNotice("request",onsite.copy(channel=EnumExitNoticeChannel.MUSEUM_WEBSITE)) }
        assertThrows(BadRequestException::class.java) { statutory.recordNotice("request",onsite.copy(channel=EnumExitNoticeChannel.MUSEUM_WEBSITE,urlOrLocation="https://museum.example/result",publicContentFileIds="agreement")) }
        statutory.recordNotice("request",onsite.copy(channel=EnumExitNoticeChannel.MUSEUM_WEBSITE,urlOrLocation="https://museum.example/result"));assertNotNull(request.announcedAt)
        assertEquals("request",service.execute("request",execution()))
    } }
    private fun succession(ownership:EnumOwnership=EnumOwnership.STATE)=CollectionInstitutionSuccessionVo(
        eventKind=EnumInstitutionSuccession.TERMINATION,sourceMuseumName="source",sourceRegistrationNo="法人登记标识",eventDocumentNo="依法终止决定1",
        decidedOn=LocalDate.of(2026,8,1),effectiveOn=LocalDate.of(2026,9,1),eventFileIds="agreement",nonprofitLegalBasis="依据适用非营利组织法规、章程及有权机关实际决议继续用于公益收藏",
        legalBasisFileIds="evidence",liquidationOrMergerPlanFileIds="archive",inventoryFileIds="archive",ownership=ownership,movable=true,propertyBasisFileIds="evidence",
        publicBenefitPurpose="在接收博物馆继续公益保护、研究和展示",noPrivateDistribution=true,thirdPartyRightsConclusion="已核对原协议及其他权利，按实际约定保留义务",thirdPartyRightsFileIds="consent",thirdPartyRightsResolved=true,
        authorizedRepresentativeUserId="liquidator-source",representativeAuthorizationFileIds="sign",
        designation=if(ownership==EnumOwnership.STATE) CollectionSuccessionDesignationVo(EnumAuthorityLevel.PROVINCE,"province-code","省文物行政部门","指定收藏1",LocalDate.of(2026,8,2),"source","target","object","000123","approval") else null,
    )
    private fun institutionRequest(ownership:EnumOwnership=EnumOwnership.STATE):CollectionTransferDao {
        val r=CollectionTransferDao["request"]
        r.dossier=r.dossier!!.copy(kind=EnumCollectionTransferKind.INSTITUTION_SUCCESSION,sourceLeaderUserId="liquidator-source",succession=succession(ownership))
        return r
    }
    private fun competentFiling(r:CollectionTransferDao) {
        login(r.orgId,CollectionConst.STORAGE)
        try { statutory.recordAdministrative(r.id.value,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.COMPETENT,action=EnumAdministrativeAction.FILING,authorityName="主管文物行政部门",documentNo="逐级报送备案1",issuedAt=LocalDate.of(2026,8,3),fileIds="evidence")) } finally { login(r.orgId) }
    }
    private fun institutionPlan():InstitutionSuccessionCaseVo {
        val s=succession()
        return InstitutionSuccessionCaseVo("source",s.eventKind,s.sourceMuseumName,s.sourceRegistrationNo,s.eventDocumentNo,s.decidedOn,s.effectiveOn,s.eventFileIds,s.nonprofitLegalBasis,s.legalBasisFileIds,s.liquidationOrMergerPlanFileIds,"keeper-source",s.authorizedRepresentativeUserId,s.representativeAuthorizationFileIds)
    }
    private fun openInstitution():String {login("source",CollectionConst.STORAGE);return institution.create(institutionPlan())}
    private fun captureInstitution(id:String):String {
        val view=institution.detail(id)
        return institution.capture(id,InstitutionScopeCaptureVo(view["scanDigest"] as String,LocalDateTime.now(),"operator-source","逐库、在借、修复、暂存及未登记记录核对","archive","sign"))
    }
    private fun resolution(key:String="COLLECTION:object",businessId:String="request",kind:EnumSuccessionResolution=EnumSuccessionResolution.INSTITUTION_TRANSFER)=InstitutionItemResolutionVo(key,kind,businessId,LocalDateTime.now(),"operator-source","已按原件核实真实处理结果","resolution-sign")
    private fun reviewInstitution(id:String,actor:String="keeper-source"):InstitutionScopeReviewVo {
        val d=institution.detail(id)
        return InstitutionScopeReviewVo(d["scopeRecordId"] as String,d["reviewDigest"] as String,LocalDateTime.now(),actor,true,"实际档案接收机构","archive","本次清册所列事项逐项核对完毕","sign")
    }
    @Suppress("UNCHECKED_CAST") private fun institutionItems(id:String)=institution.detail(id,1,0)["items"] as List<Map<String,Any?>>

    @Test fun `institution scope keeps pending loan repair retired auxiliary and unreceived records without counting them as handed objects`()=fixture {db->transaction(db) {
        val id=openInstitution()
        listOf(EnumCollectionStatus.PENDING,EnumCollectionStatus.ON_LOAN,EnumCollectionStatus.RESTORING,EnumCollectionStatus.DEREGISTERED).forEachIndexed {i,s->makeCollection("state-$i","source","state-$i");CollectionDao["state-$i"].lifeStatus=s}
        CollectionAuxiliaryDao.new("aux") {orgId="source";recordNo="辅1";content=CollectionAuxiliaryVo(orgId="source",recordNo="辅1",kind=EnumAuxiliaryKind.LAWFUL_EXIT,name="已退出关联辅助物",quantity=1,unit="件",location="辅助库",registeredOn=LocalDate.now(),reason="原件留存",evidenceFileIds="archive",originCollectionId="state-3")}
        AcquisitionDao.new("unreceived") {orgId="source";name="未取得申请";flowStatus=EnumFlowStatus.CANCEL}
        AcquisitionDao.new("derived") {orgId="source";name="有效来源";collectionId="object"}
        AcquisitionDao.new("dangling") {orgId="source";name="派生对象缺失";collectionId="not-found"}
        makeCollection("outsider","outside","900")
        val rows=institutionItems(id)
        assertEquals(8,rows.size);assertFalse(rows.any {it["id"] in setOf("derived","outsider")})
        assertTrue(rows.all {it["resolved"]==false});assertEquals(0,StorageMovementDao.all().count())
        assertEquals(0,InstitutionSuccessionItemDao.all().count())
        captureInstitution(id);assertEquals(8,InstitutionSuccessionItemDao.all().count());assertEquals(true,institution.detail(id)["scopeFresh"])
        assertEquals(8,institution.detail(id,2,3)["total"]);assertEquals(3,(institution.detail(id,2,3)["items"] as List<*>).size)
    } }
    @Test fun `institution draft non acquisition requires keeper finding and representative signing and reopens on later acquisition`()=fixture {db->transaction(db) {
        CollectionDao.new("unacquired-draft") {
            orgId="source";totalRegNo="draft-001";name="仅预留的开发草稿";lifeStatus=EnumCollectionStatus.PENDING
            catalogStatus=EnumCatalogStatus.DRAFT
        }
        val id=openInstitution()
        captureInstitution(id)
        val key="COLLECTION:unacquired-draft"
        val unsigned=InstitutionItemResolutionVo(key,EnumSuccessionResolution.DRAFT_NOT_ACQUIRED,
            "unacquired-draft",LocalDateTime.now(),"operator-source","经核对未取得、未实收","sign")
        assertThrows(BadRequestException::class.java) { institution.resolve(id,unsigned) }
        login("source",CollectionConst.STORAGE,"keeper-source")
        val finding=InstitutionItemFollowUpVo(key,LocalDateTime.now(),"keeper-source","liquidator-source",
            "待登记草稿列在机构清册中，须核实实物和取得情况",
            "已逐库核对原件和入馆凭证：此开发草稿未取得、未实收，预留库位不表示实物入库",
            LocalDateTime.now().plusDays(1),"archive")
        institution.followUp(id,finding)
        login("source",CollectionConst.STORAGE,"operator-source")
        assertThrows(BadRequestException::class.java) { institution.resolve(id,unsigned) }
        login("source",CollectionConst.STORAGE,"liquidator-source")
        val signed=unsigned.copy(recordedAt=LocalDateTime.now(),operatorUserId="liquidator-source")
        institution.resolve(id,signed)
        assertEquals(true,institutionItems(id).single { it["key"]==key }["resolved"])
        assertEquals(1,institution.detail(id)["pending"])
        AcquisitionDao.new("late-acquisition") {orgId="source";name="后续确认取得";collectionId="unacquired-draft"}
        val reopened=institutionItems(id).single { it["key"]==key }
        assertEquals(false,reopened["resolved"])
        assertEquals(2,institution.detail(id)["pending"])
        assertThrows(BadRequestException::class.java) {
            institution.resolve(id,signed.copy(recordedAt=LocalDateTime.now()))
        }
    } }
    @Test fun `institution draft non acquisition rejects formal ledger and registration number traces`()=fixture {db->transaction(db) {
        CollectionDao.new("ledger-trace-draft") {
            orgId="source";totalRegNo="draft-ledger";name="保留正式总账记录的草稿";lifeStatus=EnumCollectionStatus.PENDING
            catalogStatus=EnumCatalogStatus.DRAFT
        }
        CollectionLedgerLogDao.new {
            orgId="source";collectionId="ledger-trace-draft";totalRegNo="draft-ledger";action=EnumLedgerAction.REGISTER
        }
        CollectionDao.new("number-trace-draft") {
            orgId="source";totalRegNo="draft-number";name="登记号已变更用途的草稿";lifeStatus=EnumCollectionStatus.PENDING
            catalogStatus=EnumCatalogStatus.DRAFT
        }
        CollectionRegistrationNumberDao.new {
            orgId="source";collectionId="number-trace-draft";totalRegNo="draft-number"
            state=EnumRegistrationNoState.USED;reason="已正式登记"
        }
        val id=openInstitution();captureInstitution(id)
        login("source",CollectionConst.STORAGE,"liquidator-source")
        for(draft in listOf("ledger-trace-draft","number-trace-draft")) {
            val proof=InstitutionItemResolutionVo("COLLECTION:$draft",EnumSuccessionResolution.DRAFT_NOT_ACQUIRED,
                draft,LocalDateTime.now(),"liquidator-source","未取得、未实收","sign")
            val error=assertThrows(BadRequestException::class.java) {institution.resolve(id,proof)}
            assertTrue(error.message!!.contains("正式登记"))
        }
    } }
    @Test fun `institution capture detects changed and deleted sources and preserves the first observed baseline`()=fixture {db->transaction(db) {
        val id=openInstitution();val first=captureInstitution(id)
        val digest=institution.detail(id)["scanDigest"] as String
        val item=InstitutionSuccessionItemDao.all().single();val baseline=item.baseline
        CollectionDao["object"].lifeStatus=EnumCollectionStatus.ON_LOAN
        assertEquals(false,institution.detail(id)["scopeFresh"])
        assertThrows(BadRequestException::class.java){institution.capture(id,InstitutionScopeCaptureVo(digest,LocalDateTime.now(),"operator-source","过期范围","archive","sign"))}
        assertNotEquals(first,captureInstitution(id));assertEquals(baseline,item.baseline)
        CollectionDao["object"].delete()
        val missing=institutionItems(id).single()
        assertEquals(false,missing["currentPresent"]);assertEquals(false,missing["resolved"])
        assertEquals(1,institution.detail(id)["pending"]);assertEquals(false,institution.detail(id)["scopeFresh"])
        captureInstitution(id);assertEquals(1,institution.detail(id)["pending"])
    } }
    @Test fun `institution root remains private to source and rejects substituted actors future records and overwritten originals`()=fixture {db->transaction(db) {
        val id=openInstitution();assertEquals(id,institution.create(institutionPlan()))
        assertThrows(BadRequestException::class.java){institution.create(institutionPlan().copy(effectiveOn=LocalDate.of(2026,9,2)))}
        val digest=institution.detail(id)["scanDigest"] as String
        val capture=InstitutionScopeCaptureVo(digest,LocalDateTime.now(),"operator-source","实际范围","archive","sign")
        assertThrows(BadRequestException::class.java){institution.capture(id,capture.copy(operatorUserId="keeper-source"))}
        assertThrows(BadRequestException::class.java){institution.capture(id,capture.copy(checkedAt=LocalDateTime.now().plusDays(1)))}
        login("target",CollectionConst.STORAGE);assertThrows(BadRequestException::class.java){institution.detail(id)};assertEquals(0,institution.page()["total"])
        login("source",actor="case-reader");assertDoesNotThrow {institution.detail(id)};assertTrue(assertThrows(BadRequestException::class.java){institution.capture(id,capture)}.message!!.contains("权限"))
        login("source",CollectionConst.STORAGE);captureInstitution(id)
        InstitutionSuccessionItemDao.all().single().baseline="{}"
        assertThrows(BadRequestException::class.java){institution.detail(id)}
    } }
    @Test fun `actual institution transfer can be reviewed by two distinct actual people and new finding reopens coverage`()=fixture {db->transaction(db) {
        val id=openInstitution();captureInstitution(id)
        login("source");val r=institutionRequest();competentFiling(r);approve(r);service.execute("request",execution())
        login("source",CollectionConst.STORAGE)
        assertEquals(false,institution.detail(id)["scopeFresh"])
        val done=resolution();assertEquals(institution.resolve(id,done),institution.resolve(id,done))
        captureInstitution(id);assertEquals(0,institution.detail(id)["pending"])
        assertEquals("IN_PROGRESS",institution.detail(id)["state"])
        val keeper=reviewInstitution(id)
        assertThrows(BadRequestException::class.java){institution.review(id,keeper)}
        login("source",CollectionConst.STORAGE,"keeper-source");institution.review(id,keeper)
        login("source",CollectionConst.STORAGE,"liquidator-source")
        val rep=keeper.copy(reviewedAt=LocalDateTime.now(),reviewerUserId="liquidator-source")
        assertThrows(BadRequestException::class.java){institution.review(id,rep.copy(archiveReceiverName="替换接收者"),true)}
        institution.review(id,rep,true);assertEquals("SCOPE_REVIEWED",institution.detail(id)["state"])
        login("source",CollectionConst.STORAGE)
        val finding=InstitutionUnrecordedVo("临1","现场未登记对象",1,"件","暂存区","实际保管人员",LocalDateTime.now(),"operator-source","逐件特征待核","image","sign")
        assertEquals(institution.addUnrecorded(id,finding),institution.addUnrecorded(id,finding))
        assertEquals("NEEDS_RECHECK",institution.detail(id)["state"]);assertEquals(1,institution.detail(id)["pending"])
        assertEquals(1,CollectionDao.all().count());assertEquals(2,StorageMovementDao.all().count())
    } }
    @Test fun `other collection institution succession needs no relic designation and preserves its original number`()=fixture(nature=EnumCollectionNature.OTHER) {db->transaction(db) {
        val id=openInstitution();captureInstitution(id)
        login("source")
        val request=institutionRequest()
        request.dossier=request.dossier!!.copy(succession=succession().copy(designation=null))
        competentFiling(request);approve(request);service.execute("request",execution())
        assertEquals("target",CollectionDao["object"].orgId)
        assertEquals("000123",CollectionDao["object"].totalRegNo)
        assertEquals(EnumCollectionNature.OTHER,CollectionDao["object"].nature)
        assertEquals(1,CollectionRetainedLedgerDao.all().count())
        assertEquals(2,StorageMovementDao.all().count())
        login("source",CollectionConst.STORAGE)
        assertEquals(false,institution.detail(id)["scopeFresh"])
        institution.resolve(id,resolution())
        captureInstitution(id)
        assertEquals(0,institution.detail(id)["pending"])
        val keeper=reviewInstitution(id)
        login("source",CollectionConst.STORAGE,"keeper-source");institution.review(id,keeper)
        login("source",CollectionConst.STORAGE,"liquidator-source")
        institution.review(id,keeper.copy(reviewedAt=LocalDateTime.now(),reviewerUserId="liquidator-source"),true)
        assertEquals("SCOPE_REVIEWED",institution.detail(id)["state"])
    } }
    @Test fun `institution resolution cannot shadow a shared transfer original with a source-only signature`()=fixture {db->transaction(db) {
        val id=openInstitution();captureInstitution(id)
        login("source")
        val request=institutionRequest();competentFiling(request);approve(request);service.execute("request",execution())
        login("source",CollectionConst.STORAGE,"operator-source")
        val beforeRecords=InstitutionSuccessionRecordDao.count()
        val beforeArchives=CollectionPermanentArchiveDao.count()
        for(file in listOf("archive","sign")) {
            val error=assertThrows(BadRequestException::class.java) {
                institution.resolve(id,resolution().copy(signedFileIds=file))
            }
            assertTrue(error.message!!.contains("独立原件"))
            assertEquals(beforeRecords,InstitutionSuccessionRecordDao.count())
            assertEquals(beforeArchives,CollectionPermanentArchiveDao.count())
        }
        institution.resolve(id,resolution())
        assertEquals(true,institutionItems(id).single {it["key"]=="COLLECTION:object"}["resolved"])
    } }
    @Test fun `institution review cannot close incomplete coverage and old keeper review cannot sign a newer scope`()=fixture {db->transaction(db) {
        val id=openInstitution();captureInstitution(id)
        login("source",CollectionConst.STORAGE,"keeper-source")
        assertThrows(BadRequestException::class.java){institution.review(id,reviewInstitution(id))}
        login("source");val r=institutionRequest();competentFiling(r);approve(r);service.execute("request",execution())
        login("source",CollectionConst.STORAGE);institution.resolve(id,resolution());captureInstitution(id)
        val first=reviewInstitution(id)
        login("source",CollectionConst.STORAGE,"keeper-source");institution.review(id,first)
        login("source",CollectionConst.STORAGE);captureInstitution(id)
        login("source",CollectionConst.STORAGE,"liquidator-source")
        assertThrows(BadRequestException::class.java){institution.review(id,first.copy(reviewedAt=LocalDateTime.now(),reviewerUserId="liquidator-source"),true)}
        assertThrows(BadRequestException::class.java){institution.review(id,reviewInstitution(id,"liquidator-source"),true)}
    } }
    @Test fun `institution resolution rejects ordinary transfers earlier timestamps substituted originals and returned objects`()=fixture {db->transaction(db) {
        val id=openInstitution();captureInstitution(id)
        login("source");val r=institutionRequest();competentFiling(r);approve(r);service.execute("request",execution())
        login("source",CollectionConst.STORAGE)
        assertThrows(BadRequestException::class.java){institution.resolve(id,resolution().copy(recordedAt=LocalDateTime.of(2026,8,1,0,0)))}
        val d=r.dossier!!;r.dossier=d.copy(kind=EnumCollectionTransferKind.STATE_ALLOCATION)
        assertThrows(BadRequestException::class.java){institution.resolve(id,resolution())};r.dossier=d
        val ledger=CollectionRetainedLedgerDao.all().single();val name=ledger.name;ledger.name="被替换"
        assertThrows(BadRequestException::class.java){institution.resolve(id,resolution())};ledger.name=name
        institution.resolve(id,resolution());assertEquals(0,institution.detail(id)["pending"])
        r.executedBy="被替换的执行人员";assertEquals(1,institution.detail(id)["pending"]);r.executedBy="registrar-source"
        login("target");val back=makeRequest("back","object","target","source");administrative(back);approve(back);service.execute("back",execution("target","source"))
        login("source",CollectionConst.STORAGE);assertEquals(1,institution.detail(id)["pending"])
        assertTrue(institutionItems(id).single()["error"].toString().contains("回到本馆"))
        login("source");val onward=makeRequest("onward","object","source","outside");administrative(onward);approve(onward);service.execute("onward",execution("source","outside"))
        login("source",CollectionConst.STORAGE);assertEquals(1,institution.detail(id)["pending"])
        assertTrue(institutionItems(id).single()["error"].toString().contains("再次接收"))
    } }
    @Test fun `institution captures earlier real transfer and invalidated acquisition conclusion remains an obligation`()=fixture {db->transaction(db) {
        login("source");val r=institutionRequest();competentFiling(r);approve(r);service.execute("request",execution())
        val id=openInstitution()
        AcquisitionDao.new("unreceived") {orgId="source";name="原拟征集";flowStatus=EnumFlowStatus.CANCEL}
        captureInstitution(id);assertEquals(2,institution.detail(id)["total"])
        institution.resolve(id,resolution())
        institution.resolve(id,resolution("ACQUISITION:unreceived","unreceived",EnumSuccessionResolution.ACQUISITION_NOT_COMPLETED))
        assertEquals(0,institution.detail(id)["pending"])
        AcquisitionDao["unreceived"].acquireDate=LocalDate.now()
        assertEquals(1,institution.detail(id)["pending"])
        assertThrows(BadRequestException::class.java){institution.resolve(id,resolution("ACQUISITION:unreceived","unreceived",EnumSuccessionResolution.ACQUISITION_NOT_COMPLETED))}
        AcquisitionDao["unreceived"].acquireDate=null
        val follow=InstitutionItemFollowUpVo("COLLECTION:object",LocalDateTime.now().minusHours(2),"operator-source","keeper-source","发现新的未结事项","已联系核实并维持保管",LocalDateTime.now().minusHours(1),"evidence")
        institution.followUp(id,follow);assertEquals(1,institution.detail(id)["pending"]);assertEquals(1,institution.detail(id)["overdue"])
    } }
    @Test fun `institution rejected acquisition with erroneous legacy fields requires veto and two person correction`()=fixture {db->transaction(db) {
        AcquisitionDao.new("rejected-legacy") {
            orgId="source";name="历史误填的否决征集";flowStatus=EnumFlowStatus.REJECT
            acquireDate=LocalDate.now().minusDays(1);contractAttachmentIds="old-contract-file"
        }
        CollectionPermanentArchiveDao.new {
            orgId="source";bizType=CollectionConst.BIZ_ACQUISITION;bizId="rejected-legacy";phase="VETO"
            snapshot=JsonUtils.toJson(mapOf("expertVeto" to "rejected-legacy"))!!
            digest=CollectionCareEvidence.digest(snapshot)
        }
        val id=openInstitution();captureInstitution(id)
        val key="ACQUISITION:rejected-legacy"
        val unsigned=InstitutionItemResolutionVo(key,EnumSuccessionResolution.ACQUISITION_NOT_COMPLETED,
            "rejected-legacy",LocalDateTime.now(),"liquidator-source",
            "入藏日期、合同附件旧字段误填，未取得、未实收","sign")
        login("source",CollectionConst.STORAGE,"liquidator-source")
        assertThrows(BadRequestException::class.java) {institution.resolve(id,unsigned)}
        login("source",CollectionConst.STORAGE,"keeper-source")
        val follow=InstitutionItemFollowUpVo(key,LocalDateTime.now(),"keeper-source","liquidator-source",
            "核查否决征集的旧取得与合同字段",
            "已核对专家否决原件及接收记录：入藏日期、合同附件旧字段误填，未取得、未实收",
            LocalDateTime.now().plusDays(1),"archive")
        institution.followUp(id,follow)
        login("source",CollectionConst.STORAGE,"liquidator-source")
        institution.resolve(id,unsigned.copy(recordedAt=LocalDateTime.now()))
        assertEquals(true,institutionItems(id).single {it["key"]==key}["resolved"])
        assertNotNull(AcquisitionDao["rejected-legacy"].acquireDate)
        assertEquals("old-contract-file",AcquisitionDao["rejected-legacy"].contractAttachmentIds)
        EntryVoucherDao.new {orgId="source";voucherNo="unexpected-voucher";acquisitionIds="rejected-legacy"}
        assertEquals(false,institutionItems(id).single {it["key"]==key}["resolved"])
        assertThrows(BadRequestException::class.java) {institution.resolve(id,unsigned.copy(recordedAt=LocalDateTime.now()))}
        AcquisitionDao["rejected-legacy"].collectionId="later-confirmed-object"
        assertEquals(false,institutionItems(id).single {it["key"]==key}["resolved"])
    } }
    @ParameterizedTest
    @ValueSource(strings=["CANCEL", "VOID"])
    fun `institution terminal unreceived acquisition preserves erroneous fields with two person correction`(status:String)=fixture {db->transaction(db) {
        val terminal=EnumFlowStatus.valueOf(status)
        val originalDate=LocalDate.now().minusDays(1)
        AcquisitionDao.new("terminal-legacy") {
            orgId="source";name="未取得的废止申请";flowStatus=terminal
            acquireDate=originalDate;contractAttachmentIds="old-contract-file"
        }
        val id=openInstitution();captureInstitution(id)
        val key="ACQUISITION:terminal-legacy"
        val conclusion="入藏日期、合同附件旧字段误填，未取得、未实收"
        val resolved=InstitutionItemResolutionVo(key,EnumSuccessionResolution.ACQUISITION_NOT_COMPLETED,
            "terminal-legacy",LocalDateTime.now(),"liquidator-source",conclusion,"sign")
        login("source",CollectionConst.STORAGE,"liquidator-source")
        assertThrows(BadRequestException::class.java) {institution.resolve(id,resolved)}
        login("source",CollectionConst.STORAGE,"keeper-source")
        institution.followUp(id,InstitutionItemFollowUpVo(key,LocalDateTime.now(),"keeper-source","liquidator-source",
            "核查废止申请的取得与合同字段",conclusion,LocalDateTime.now().plusDays(1),"archive"))
        assertThrows(BadRequestException::class.java) {
            institution.resolve(id,resolved.copy(recordedAt=LocalDateTime.now(),operatorUserId="keeper-source"))
        }
        login("source",CollectionConst.STORAGE,"liquidator-source")
        institution.resolve(id,resolved.copy(recordedAt=LocalDateTime.now()))
        assertTrue(institutionItems(id).single {it["key"]==key}["resolved"] as Boolean)
        assertEquals(originalDate,AcquisitionDao["terminal-legacy"].acquireDate)
        assertEquals("old-contract-file",AcquisitionDao["terminal-legacy"].contractAttachmentIds)
        assertEquals(terminal,AcquisitionDao["terminal-legacy"].flowStatus)
        assertEquals(0,CollectionPermanentArchiveDao.find {CollectionPermanentArchives.phase eq "VETO"}.count(),
            "撤销、作废不能伪造专家否决原件")
        AcquisitionDao["terminal-legacy"].acquireDate=originalDate.minusDays(1)
        assertFalse(institutionItems(id).single {it["key"]==key}["resolved"] as Boolean,
            "纠错后来源变化须重新核查，不能沿用旧完成结论")
        AcquisitionDao["terminal-legacy"].acquireDate=originalDate
        EntryVoucherDao.new {orgId="source";voucherNo="actual-source-voucher";acquisitionIds="terminal-legacy"}
        assertFalse(institutionItems(id).single {it["key"]==key}["resolved"] as Boolean)
        assertThrows(BadRequestException::class.java) {institution.resolve(id,resolved.copy(recordedAt=LocalDateTime.now()))}
    } }
    @Test fun `missing institution original rolls back the new case without changing custody`()=fixture {db->
        login("source",CollectionConst.STORAGE)
        assertThrows(BadRequestException::class.java){transaction(db){institution.create(institutionPlan().copy(eventFileIds="missing-file"))}}
        transaction(db){assertEquals(0,InstitutionSuccessionCaseDao.all().count());assertEquals(0,InstitutionSuccessionItemDao.all().count());assertEquals("source",CollectionDao["object"].orgId);assertEquals(0,StorageMovementDao.all().count())}
    }
    @Test fun `institution scope and archive proof survive committed transactions and repeated manual finding scans`()=fixture {db->
        val id=transaction(db){val id=openInstitution();captureInstitution(id);id}
        val digest=transaction(db){assertEquals(true,institution.detail(id)["scopeFresh"]);institution.detail(id)["scanDigest"]}
        transaction(db){assertEquals(digest,institution.detail(id)["scanDigest"]);institution.addUnrecorded(id,InstitutionUnrecordedVo("现场1","现场对象",1,"件","暂存库","本馆保管",LocalDateTime.now(),"operator-source","特征核查","image","sign"));captureInstitution(id)}
        transaction(db){assertEquals(true,institution.detail(id)["scopeFresh"]);assertEquals(2,institution.detail(id)["pending"])}
    }
    @Test fun `state owned movable relic in non state museum still requires provincial designation and preserves both museum originals`()=fixture {db->transaction(db) {
        CollectionOrgDao["source"].ownership=EnumOwnership.NON_STATE
        val r=institutionRequest();val d=r.dossier!!
        r.dossier=d.copy(succession=d.succession!!.copy(designation=null))
        assertThrows(BadRequestException::class.java){statutory.requirePrepared(r)}
        r.dossier=d
        CollectionOrgDao["target"].ownership=EnumOwnership.NON_STATE
        assertThrows(BadRequestException::class.java){statutory.requirePrepared(r)}
        CollectionOrgDao["target"].ownership=EnumOwnership.STATE
        statutory.requirePrepared(r)
        assertEquals(0,statutory.administrative(r.id.value).size,"省级指定决定在专门原件中核实，不虚构普通调拨批准")
        approve(r)
        verify(users,atLeastOnce()).requireMuseumMember("liquidator-source","source")
        verify(users,never()).requireRole("liquidator-source","source",CollectionConst.ROLE_DIRECTOR)
        assertThrows(BadRequestException::class.java){statutory.requireActor(r,"source_leader","leader-source","代办")}
        statutory.requireActor(r,"source_leader","liquidator-source","本人核对授权和处置方案")
        assertEquals("source",CollectionDao["object"].orgId)
        service.execute(r.id.value,execution())
        assertEquals("target",CollectionDao["object"].orgId);assertEquals(2,StorageMovementDao.count());assertEquals(1,CollectionRetainedLedgerDao.count())
        assertEquals(EnumOrgStatus.ENABLED,CollectionOrgDao["source"].orgStatus,"逐件承接不自动宣称机构清算完成或停用原馆账号")
        val received=CollectionPermanentArchiveDao.all().single {it.bizId==r.id.value && it.phase=="RECEIVED"}
        assertEquals("target",received.orgId);assertTrue(received.snapshot.contains("指定收藏1"));assertTrue(received.snapshot.contains("依法终止决定1"))
        val receivedFiles=CollectionPermanentArchiveFileDao.all().filter {it.archiveId==received.id.value}.map {it.fileId}.toSet()
        assertTrue(receivedFiles.containsAll(setOf("agreement","approval","evidence","consent","archive","sign")))
        val print=CollectionPrintFieldBuilder.transferFields(r.toModel())
        assertTrue(print.any {it.label=="转移类别" && it.value=="博物馆终止或合并承接"})
        assertTrue(print.any {it.label=="省级指定文号" && it.value=="指定收藏1"})
    }}
    @Test fun `termination and merger keep state museum exit filings and first relic filing without imposing exchange donation procedure`()=fixture {db->transaction(db) {
        val c=CollectionDao["object"];c.level=EnumRelicLevel.FIRST;c.source=EnumSource.DONATE
        val r=institutionRequest();r.baseline=CollectionCatalogMapper.fromCollection(c.toModel())
        r.dossier=r.dossier!!.copy(succession=r.dossier!!.succession!!.copy(eventKind=EnumInstitutionSuccession.MERGER))
        assertThrows(BadRequestException::class.java){statutory.requirePrepared(r)}
        competentFiling(r)
        assertThrows(BadRequestException::class.java){statutory.requirePrepared(r)}
        administrative(r,true)
        assertDoesNotThrow {statutory.requirePrepared(r)}
        assertNull(r.dossier!!.donorWrittenConsentFileIds,"终止合并按真实第三方权利依据处理，不伪造交换调拨捐赠人同意")
        approve(r);service.execute(r.id.value,execution())
        assertEquals("target",CollectionDao["object"].orgId)
        assertEquals(EnumInstitutionSuccession.MERGER,service.get(r.id.value).dossier!!.succession!!.eventKind)
    }}
    @Test fun `non state property requires actual nonprofit and rights evidence and cannot be distributed privately`()=fixture {db->transaction(db) {
        CollectionOrgDao["source"].ownership=EnumOwnership.NON_STATE;CollectionOrgDao["target"].ownership=EnumOwnership.NON_STATE
        val r=institutionRequest(EnumOwnership.NON_STATE);val d=r.dossier!!;val v=d.succession!!
        for(invalid in listOf(v.copy(noPrivateDistribution=false),v.copy(thirdPartyRightsResolved=false),v.copy(thirdPartyRightsFileIds=""),v.copy(publicBenefitPurpose=""),v.copy(representativeAuthorizationFileIds=""),v.copy(authorizedRepresentativeUserId="unrelated"),v.copy(movable=false),v.copy(decidedOn=LocalDate.now().plusDays(1)),v.copy(effectiveOn=v.decidedOn.minusDays(1)))) {
            r.dossier=d.copy(succession=invalid);assertThrows(BadRequestException::class.java){statutory.requirePrepared(r)}
        }
        r.dossier=d;statutory.requirePrepared(r);approve(r);service.execute(r.id.value,execution())
        assertEquals("target",CollectionDao["object"].orgId)
    }}
    @Test fun `institution designation must bind exact source recipient object and registration number and cannot hide in another kind`()=fixture {db->transaction(db) {
        val r=institutionRequest();val d=r.dossier!!;val v=d.succession!!;val designation=v.designation!!
        for(invalid in listOf(designation.copy(level=EnumAuthorityLevel.NATIONAL),designation.copy(level=EnumAuthorityLevel.COMPETENT),designation.copy(sourceMuseumName="outside"),designation.copy(targetMuseumName="outside"),designation.copy(collectionId="another-object"),designation.copy(totalRegNo="wrong"),designation.copy(authorityCode=""),designation.copy(issuedOn=LocalDate.now().plusDays(1)))) {
            r.dossier=d.copy(succession=v.copy(designation=invalid));assertThrows(BadRequestException::class.java){statutory.requireProposal(r)}
        }
        r.dossier=d.copy(kind=EnumCollectionTransferKind.OTHER_LAWFUL,legalBasisFileIds="evidence")
        assertThrows(BadRequestException::class.java){statutory.requireProposal(r)}
        r.dossier=d.copy(succession=null);assertThrows(BadRequestException::class.java){statutory.requireProposal(r)}
        r.dossier=d
        login("outside");assertThrows(BadRequestException::class.java){service.get(r.id.value)}
        assertEquals(0L,StorageMovementDao.count())
    }}
    @Test fun `receiving museum reads only sealed institution basis used by current succession transfer`()=fixture { db -> transaction(db) {
        val caseId=openInstitution()
        val transfer=institutionRequest()
        competentFiling(transfer)
        val directory=CollectionActorDirectory(scope)
        val authorization=CollectionFileReadAuthorization(scope,directory)
        val disclosures=mock(CollectionDisclosureService::class.java)
        val transferPolicy=CollectionTransferArchiveReadPolicy(scope,directory,authorization,disclosures)
        val policy=CollectionBusinessArchiveReadPolicy(authorization,disclosures,null,transferPolicy)
        val caseArchive=CollectionPermanentArchiveDao.all().single { it.bizId==caseId && it.phase=="CASE" }
        val basis=CollectionArchiveFileReference(caseArchive.id.value,"source",InstitutionSuccessionCaseEvidence.BIZ,caseId,"CASE","agreement","")
        login("target",CollectionConst.STORAGE,"leader-target")
        assertThrows(BadRequestException::class.java) { policy.requireReadable(basis) }
        transfer.flowStatus=EnumFlowStatus.AUDIT
        policy.requireReadable(basis)
        assertThrows(BadRequestException::class.java) { policy.requireReadable(basis.copy(fileId="public")) }
        assertThrows(BadRequestException::class.java) { policy.requireReadable(basis.copy(phase="SCOPE")) }
        val original=transfer.dossier!!
        transfer.dossier=original.copy(succession=original.succession!!.copy(eventDocumentNo="另一机构决定"))
        assertThrows(BadRequestException::class.java) { policy.requireReadable(basis) }
        transfer.dossier=original
        policy.requireReadable(basis)
        login("outside",CollectionConst.SELECT,"operator-outside")
        assertThrows(BadRequestException::class.java) { policy.requireReadable(basis) }
        login("target",CollectionConst.STORAGE,"leader-target")
        transfer.flowStatus=EnumFlowStatus.CANCEL
        assertThrows(BadRequestException::class.java) { policy.requireReadable(basis) }
    } }
    @Test fun `approval archive rejects substituted institutional decisions even when new facts independently pass validation`()=fixture {db->transaction(db) {
        val r=institutionRequest();competentFiling(r);approve(r)
        val d=r.dossier!!
        r.dossier=d.copy(succession=d.succession!!.copy(eventDocumentNo="另一决定"))
        assertThrows(BadRequestException::class.java){service.execute(r.id.value,execution())}
        assertEquals("source",CollectionDao["object"].orgId);assertNull(r.executedAt)
        r.dossier=d
        val archive=CollectionPermanentArchiveDao.all().single {it.bizId==r.id.value && it.phase.startsWith("APPROVED_")}
        archive.digest="tampered"
        assertThrows(BadRequestException::class.java){service.execute(r.id.value,execution())}
        assertEquals(0L,StorageMovementDao.count())
        assertThrows(BadRequestException::class.java){CollectionInstitutionSuccessionPolicy.requireHandover(d.succession,LocalDate.of(2026,7,31))}
    }}
    @Test fun `missing institutional legal originals roll back archive and preserve source custody`()=fixture {db->
        transaction(db){institutionRequest()}
        assertThrows(BadRequestException::class.java){transaction(db){
            val r=CollectionTransferDao["request"];r.dossier=r.dossier!!.copy(succession=r.dossier!!.succession!!.copy(eventFileIds="missing-file"));competentFiling(r)
        }}
        transaction(db){
            val r=CollectionTransferDao["request"];assertTrue(statutory.administrative(r.id.value).isEmpty());assertEquals(0L,CollectionPermanentArchiveDao.count())
            assertEquals("source",CollectionDao["object"].orgId);assertEquals(0L,StorageMovementDao.count());assertEquals(0L,CollectionRetainedLedgerDao.count())
            competentFiling(r);approve(r);service.execute(r.id.value,execution())
            assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("agreement")}
        }
    }

}
