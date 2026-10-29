package io.hizhengfu.cloud.collection.service

import org.jetbrains.exposed.v1.dao.entityCache

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.time.LocalDateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import java.time.LocalDate

class DeregistrationEvidencePersistenceTest {
    @org.junit.jupiter.api.io.TempDir lateinit var originalRoot: java.nio.file.Path
    private val scope=CollectionScopeResolver("single","museum")
    private val binder=mock(BusinessAttachmentBinder::class.java).also {
        `when`(it.resolveSingle(anyString())).thenAnswer { call -> call.getArgument<String>(0) }
    }
    private val archives by lazy { CollectionPermanentArchiveService(binder,scope,LocalFileObjectStorage(originalRoot.toString()),unprotectedArchiveReadAccessProvider()) }
    private val calendar by lazy { CollectionCalendarService(scope,binder,archives) }
    private val disclosures=mock(CollectionDisclosureService::class.java).also { `when`(it.latestStatutoryReviewId("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"request")).thenReturn("review") }
    private val service by lazy { DeregistrationStatutoryService(scope,binder,archives,calendar,mock(CollectionResponsibleUserService::class.java),disclosures) }

    private val locks=CollectionOperationLockService()
    private val auxiliary by lazy { CollectionAuxiliaryService(scope,binder,archives) }
    private val collections=CollectionService(scope,binder,mock(CollectionRegistrationNumberService::class.java),locks,
        mock(CollectionCatalogService::class.java),mock(CollectionChangeService::class.java),mock(CollectionRetainedLedgerService::class.java),mock(CollectionDisclosureService::class.java))
    private val paperLedgers=mock(PaperLedgerService::class.java).also {
        `when`(it.requirePageReference(anyString(),anyString(),anyInt())).thenReturn(PaperLedgerService.PageReference("paper-book","总账第一册",1,20))
    }
    private val workflow by lazy { DeregistrationService(mock(FlowInstanceService::class.java),scope,collections,mock(CollectionPrintService::class.java),binder,locks,service,archives,auxiliary,paperLedgers) }
    private fun execution()=DeregistrationExecutionVo("opinion",paperUpdatedAt=LocalDate.now(),dispositionReceiptFileIds="evidence",paperLedgerBookId="paper-book",paperLedgerPageNo=1)

    @Test fun `actual lawful exit keeps its restriction and original ledger when the auxiliary object is institutionally handed to state research`() = fixture {request->
        originalReader("operator",CollectionConst.SELECT)
        actorRole("operator","FILE_operator",setOf(CollectionConst.STORAGE))
        listOf("keeper","leader").forEach { originalReader(it,CollectionConst.STORAGE) }
        prepared(request);approve(request)
        workflow.execute(request.id.value,execution())
        val people=mock(CollectionResponsibleUserService::class.java)
        val cases=InstitutionSuccessionCaseService(scope,people,archives);val items=InstitutionItemHandoverService(cases,people)
        fun actor(id:String) {
            val u=SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.STORAGE)),enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(u,null,u.authorities)
        }
        actor("operator")
        val now=java.time.LocalDateTime.now()
        val id=cases.create(InstitutionSuccessionCaseVo("museum",EnumInstitutionSuccession.MERGER,"测试馆","法人标识","合并决定",LocalDate.of(2026,8,1),LocalDate.of(2026,9,1),"plan","真实非营利法规及方案","plan","plan","keeper","leader","evidence"))
        cases.capture(id,InstitutionScopeCaptureVo(cases.detail(id)["scanDigest"] as String,now,"operator","原总账与退出辅助账逐件核对","plan","evidence"))
        val auxiliaryId=CollectionAuxiliaryDao.all().single().id.value;val key="AUXILIARY:$auxiliaryId"
        val piece=InstitutionHandoverPieceVo("specimen-1","原标本特征","按原退出及清点原件核实","evidence")
        val v=InstitutionItemProposalVo(key,java.time.LocalDateTime.now(),"operator",EnumInstitutionItemRoute.NON_COLLECTION_SUCCESSION,"经原件确认系已依法退出的不达标其他标本","opinion",nonCollectionConfirmed=true,propertyBasisFileIds="evidence",ownership=EnumOwnership.STATE,ownerName="测试馆",ownerIdentity="法人标识",recipientKind=EnumInstitutionRecipient.PUBLIC_BENEFIT_ORGANIZATION,recipientName="国有科研单位",recipientIdentity="research-code",recipientQualificationFileIds="evidence",dispositionBasisFileIds="plan",thirdPartyRightsResolved=true,thirdPartyRightsFileIds="evidence",publicBenefitPurpose="继续国有科研保管使用",noPrivateDistribution=true,pieces=listOf(piece))
        assertThrows(BadRequestException::class.java){items.propose(id,v)}
        CollectionDao["object"].nature=EnumCollectionNature.RELIC
        assertThrows(BadRequestException::class.java){items.propose(id,v.copy(recipientKind=EnumInstitutionRecipient.STATE_TEACHING_RESEARCH))}
        CollectionDao["object"].nature=EnumCollectionNature.OTHER
        val p=items.propose(id,v.copy(recipientKind=EnumInstitutionRecipient.STATE_TEACHING_RESEARCH))
        fun confirm(planId:String) {
            actor("keeper");items.approve(id,InstitutionItemApprovalVo(planId,java.time.LocalDateTime.now(),"keeper","核对原退出限制及本次实际去向","evidence"))
            actor("leader");items.approve(id,InstitutionItemApprovalVo(planId,java.time.LocalDateTime.now(),"leader","依法确认真实保管使用方案","evidence"),true)
            actor("operator")
        }
        confirm(p)
        val hand=items.handOver(id,InstitutionItemHandoverVo(p,java.time.LocalDateTime.now(),"operator","国有科研单位","research-code","实际科研标本室",setOf("实际交出人"),setOf("实际接收人"),listOf(piece),true,true,signedReceiptFileIds="evidence",archiveHandoverFileIds="plan"))
        cases.resolve(id,InstitutionItemResolutionVo(key,EnumSuccessionResolution.OTHER_ITEM_HANDOVER,hand,java.time.LocalDateTime.now(),"operator","逐件核实实际交齐","evidence"))
        assertEquals(1,cases.detail(id)["pending"])
        val link=items.propose(id,InstitutionItemProposalVo("COLLECTION:object",java.time.LocalDateTime.now(),"operator",EnumInstitutionItemRoute.RECORD_LINK,"原总账保留，实物对应同一退出辅助账","evidence",linkedItemKey=key))
        confirm(link)
        cases.resolve(id,InstitutionItemResolutionVo("COLLECTION:object",EnumSuccessionResolution.LINKED_ITEM,link,java.time.LocalDateTime.now(),"operator","核实原账与实际承接的关系","evidence"))
        assertEquals(0,cases.detail(id)["pending"]);assertEquals("000123",CollectionDao["object"].totalRegNo)
        assertEquals("HANDED_OVER",auxiliary.list("museum").single().institutionHandover?.get("state"))
    }

    @BeforeEach fun prepare() {
        val context=StaticApplicationContext()
        context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
        context.refresh(); SpringContextHolder().setApplicationContext(context)
        val user=SecurityUser(id="registrar",name="总账经办人",username="registrar",password="",groupIds=emptySet(),postIds=emptySet(),dataType=0,
            roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.LEDGER_CORRECT)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    @AfterEach fun cleanup() = SecurityContextHolder.clearContext()

    private fun fixture(block: (DeregistrationDao) -> Unit) {
        val db=Database.connect("jdbc:sqlite:file:exit-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(io.hizhengfu.cloud.collection.dao.CollectionLedgerStatEvents,Collections,CollectionOrgs,Deregistrations,DeregistrationExpertOpinions,DeregistrationNotices,CollectionAdministrativeRecords,
                CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionCalendarVersions,Files,FlowInstanceLogs,CollectionOperationLocks,CollectionLedgerLogs,CollectionAuxiliaries,
                InstitutionSuccessionCases,InstitutionSuccessionItems,InstitutionSuccessionRecords,Acquisitions,AccessionReceipts,AcquisitionComplianceCases,DesignatedReceptions,DesignatedReceptionRecords,CollectionTransfers,CollectionRetainedLedgers,Warehouses,CollectionCategories,CollectionMedias,CollectionAttachments)
            java.nio.file.Files.createDirectories(originalRoot.resolve("files"))
            listOf("qualification","independence","opinion","public","evidence","plan","approval").forEach { key ->
                FileDao.new(key) { name="$key.pdf"; path="/files/$key.pdf"; hash=key; size=100; ext="pdf" }
                java.nio.file.Files.writeString(originalRoot.resolve("files/$key.pdf"),"ORIGINAL-$key")
            }
            CollectionOrgDao.new("museum") { name="测试馆"; code="M"; ownership=EnumOwnership.STATE }
            CollectionOrgDao.new("other"){name="其他馆";code="O";ownership=EnumOwnership.STATE}
            originalReaderSchema()
            originalReader("registrar",CollectionConst.SELECT)
            actorRole("registrar","FILE_registrar",setOf(CollectionConst.LEDGER_CORRECT,CollectionConst.DEREGISTER))
            val collection=CollectionDao.new("object") {
                orgId="museum"; totalRegNo="000123"; name="残损标本"; nature=EnumCollectionNature.OTHER
                lifeStatus=EnumCollectionStatus.IN_STORE; catalogStatus=EnumCatalogStatus.APPROVED
            }
            val request=DeregistrationDao.new("request") {
                orgId="museum"; collectionId="object"; reason=EnumDeregReason.OTHER_BELOW_STANDARD; detail="已核实低于入藏标准"
                createBy="registrar"; baseline=CollectionCatalogMapper.fromCollection(collection.toModel())
                dossier=DeregistrationDossierVo(proposedOn=LocalDate.of(2026,5,1),keeperUserId="keeper",leaderUserId="leader",
                    destinationType=EnumExitDestination.AUXILIARY_ACCOUNT,dispositionPlanFileIds="plan",auxiliaryRecordNo="F-001",auxiliaryLocation="标本辅助库")
            }
            block(request)
        }
    }
    private fun expert(key: String, agree: Boolean = true) = DeregistrationExpertOpinionVo(expertKey=key,expertName="专家$key",organization="外部文博机构$key",
        professionalTitle="文博研究馆员",specialty="自然标本",seniorQualificationFileIds="qualification",independenceStatementFileIds="independence",
        opinion="已逐件核对",agree=agree,signedOpinionFileIds="opinion",assessedAt=LocalDate.of(2026,5,20))

    @Test fun `exit history rejects changed signed expert bytes under the same file identity`()=fixture {request->
        service.recordExpert(request.id.value,expert("1"))
        val original=CollectionPermanentArchiveDao.all().single {it.phase.startsWith("EXPERT_")}
        val ref=CollectionPermanentArchiveFileDao.all().single {it.archiveId==original.id.value && it.fileId=="opinion"}
        assertNotNull(CollectionArchiveFileIdentity.content(io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(ref.metadata)))
        assertNotNull(workflow.get(request.id.value))
        val path=originalRoot.resolve("files/opinion.pdf")
        val timestamp=java.nio.file.Files.getLastModifiedTime(path)
        java.nio.file.Files.writeString(path,"MODIFIED-opinion")
        java.nio.file.Files.setLastModifiedTime(path,timestamp)
        assertTrue(workflow.get(request.id.value)!!.evidenceErrors.isNotEmpty())
        assertFalse(workflow.get(request.id.value)!!.canOperate)
        assertUnavailableOriginals(workflow.archive(request.id.value))
    }

    @Test fun `exit original is limited to current proposal and verified case archive files`() = fixture { request ->
        val current="2103030124866662400";val expertFile="2103030124866662401";val unrelated="2103030124866662402"
        listOf(current,expertFile,unrelated).forEach { id ->
            FileDao.new(id) { name="$id.pdf";path="/files/$id.pdf";hash=id;size=100;ext="pdf" }
            java.nio.file.Files.writeString(originalRoot.resolve("files/$id.pdf"),"ORIGINAL-$id")
        }
        request.dossier=request.dossier!!.copy(dispositionPlanFileIds=current)
        workflow.requireOriginal(request.id.value,current)
        service.recordExpert(request.id.value,expert("1").copy(seniorQualificationFileIds=expertFile))
        workflow.requireOriginal(request.id.value,expertFile)
        assertThrows(BadRequestException::class.java){workflow.requireOriginal(request.id.value,unrelated)}
        assertThrows(BadRequestException::class.java){workflow.requireOriginal(request.id.value,"../file")}
    }

    @Test fun `first dissent terminates procedure and blocks all new applications for the same object`() = fixture { request ->
        service.recordExpert(request.id.value,expert("1",agree=false))
        assertEquals(EnumFlowStatus.REJECT,request.flowStatus)
        assertEquals(expert("1").assessedAt.plusYears(5),request.vetoUntil)
        assertThrows(BadRequestException::class.java) { service.requireNoVeto("object") }
        assertThrows(BadRequestException::class.java) { service.requireEditable(request) }
        assertThrows(BadRequestException::class.java) { service.recordExpert(request.id.value,expert("2")) }
        assertEquals(3,archives.list(CollectionConst.BIZ_DEREGISTER,request.id.value).size)
    }

    @Test fun `retained veto rejects changed signed dissent bytes before reuse`()=fixture {request->
        service.recordExpert(request.id.value,expert("1",false))
        assertDoesNotThrow{DeregistrationVetoEvidence.verify(request,archives)}
        val path=originalRoot.resolve("files/opinion.pdf")
        val timestamp=java.nio.file.Files.getLastModifiedTime(path)
        java.nio.file.Files.writeString(path,"MODIFIED-opinion")
        java.nio.file.Files.setLastModifiedTime(path,timestamp)
        assertThrows(BadRequestException::class.java){DeregistrationVetoEvidence.verify(request,archives)}
    }
    @Test fun `retained veto rejects changed proposal plan bytes before reuse`()=fixture {request->
        service.recordExpert(request.id.value,expert("1",false))
        assertDoesNotThrow{DeregistrationVetoEvidence.verify(request,archives)}
        val path=originalRoot.resolve("files/plan.pdf")
        val timestamp=java.nio.file.Files.getLastModifiedTime(path)
        java.nio.file.Files.writeString(path,"REPLACED-plan")
        java.nio.file.Files.setLastModifiedTime(path,timestamp)
        assertThrows(BadRequestException::class.java){DeregistrationVetoEvidence.verify(request,archives)}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["clearedDeadline","pastDeadline","changedObject","missingRequest","malformedOrphanVeto","changedOrphanIdentity"])
    fun `retained veto still prevents restart after mutable request changes`(change:String)=fixture {request->
        service.recordExpert(request.id.value,expert("1",false))
        val next=request.toModel().copy(id=null)
        when(change) {
            "clearedDeadline"->request.vetoUntil=null
            "pastDeadline"->request.vetoUntil=LocalDate.now().minusDays(1)
            "changedObject"->request.collectionId="another-object"
            "missingRequest"->request.delete()
            "malformedOrphanVeto"->{request.delete();CollectionPermanentArchiveDao.all().single {it.phase=="VETO"}.apply {snapshot="{";digest=CollectionCareEvidence.digest(snapshot)}}
            "changedOrphanIdentity"->{request.delete();val a=CollectionPermanentArchiveDao.all().single {it.phase=="VETO"};val root=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(a.snapshot) as tools.jackson.databind.node.ObjectNode;root.put("collectionId","other-object");a.snapshot=root.toString();a.digest=CollectionCareEvidence.digest(a.snapshot)}
        }
        assertThrows(BadRequestException::class.java){service.requireNoVeto("object")}
        assertThrows(BadRequestException::class.java){workflow.save(next)}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["clearedDeadline","changedStatus","missingVeto","missingExpert","vetoFiles","changedDeadline"])
    fun `veto history read rejects missing or changed termination evidence`(change:String)=fixture {request->
        service.recordExpert(request.id.value,expert("1",false))
        assertNotNull(workflow.get(request.id.value))
        when(change) {
            "clearedDeadline"->request.vetoUntil=null
            "changedStatus"->request.flowStatus=EnumFlowStatus.DRAFT
            "missingVeto"->CollectionPermanentArchiveDao.all().single {it.phase=="VETO"}.delete()
            "missingExpert"->DeregistrationExpertOpinionDao.all().single().delete()
            "changedDeadline"->request.vetoUntil=request.vetoUntil!!.plusYears(1)
            else->{val a=CollectionPermanentArchiveDao.all().single {it.phase=="VETO"};CollectionPermanentArchiveFileDao.all().filter {it.archiveId==a.id.value}.forEach {it.delete()}}
        }
        assertThrows(BadRequestException::class.java){workflow.get(request.id.value)}
    }

    @Test fun `veto period follows the signed assessment and expires at its fifth anniversary`()=fixture {request->
        service.recordExpert(request.id.value,expert("1",false))
        val anniversary=expert("1").assessedAt.plusYears(5)
        assertEquals(anniversary,request.vetoUntil)
        assertThrows(BadRequestException::class.java){service.requireNoVeto("object",anniversary.minusDays(1))}
        assertDoesNotThrow{service.requireNoVeto("object",anniversary)}
        assertDoesNotThrow{service.requireNoVeto("unrelated",LocalDate.now())}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["versionRemoved","expertArchiveMissing","proposalReference","vetoOrg"])
    fun `veto originals retain their identity and evidence links`(change:String)=fixture {request->
        service.recordExpert(request.id.value,expert("1",false))
        val a=CollectionPermanentArchiveDao.all().single {it.phase=="VETO"}
        when(change) {
            "expertArchiveMissing"->CollectionPermanentArchiveDao.all().single {it.phase.startsWith("EXPERT_")}.delete()
            "vetoOrg"->a.orgId="other"
            else->{val root=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(a.snapshot) as tools.jackson.databind.node.ObjectNode
                if(change=="versionRemoved")root.remove("vetoVersion") else (root.get("proposalArchive") as tools.jackson.databind.node.ObjectNode).put("id","other-archive")
                a.snapshot=root.toString();a.digest=CollectionCareEvidence.digest(a.snapshot)}
        }
        assertThrows(BadRequestException::class.java){service.requireNoVeto("object")}
        assertThrows(BadRequestException::class.java){workflow.get(request.id.value)}
    }

    @Test fun `legacy veto keeps its retained deadline and is not silently resealed`()=fixture {request->
        service.recordExpert(request.id.value,expert("1",false))
        val a=CollectionPermanentArchiveDao.all().single {it.phase=="VETO"}
        val oldUntil=LocalDate.now().plusYears(5)
        request.vetoUntil=oldUntil
        a.snapshot=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(mapOf("collectionId" to "object","totalRegNo" to "000123","expertOpinion" to expert("1",false),"vetoUntil" to oldUntil))!!
        a.digest=CollectionCareEvidence.digest(a.snapshot)
        val original=a.snapshot
        assertNotNull(workflow.get(request.id.value))
        assertThrows(BadRequestException::class.java){service.requireNoVeto("object",oldUntil.minusDays(1))}
        assertDoesNotThrow{service.requireNoVeto("object",oldUntil)}
        assertEquals(original,a.snapshot)
    }

    @Test fun `duplicate or museum experts cannot count and first assessment freezes the proposed disposition`() = fixture { request ->
        assertThrows(BadRequestException::class.java) { service.recordExpert(request.id.value,expert("inside").copy(employerOrgId="museum")) }
        service.recordExpert(request.id.value,expert("1"))
        assertThrows(BadRequestException::class.java) { service.recordExpert(request.id.value,expert("1")) }
        assertThrows(BadRequestException::class.java) { service.requireEditable(request) }
        assertThrows(BadRequestException::class.java) { service.requirePrepared(request) }
        assertEquals(1,service.experts(request.id.value).size)
    }

    @Test fun `five written consents plus three overlapping notice channels and provincial approval are all required`() = fixture { request ->
        (1..5).forEach { service.recordExpert(request.id.value,expert("$it")) }
        assertThrows(BadRequestException::class.java) { service.requirePrepared(request) }
        EnumExitNoticeChannel.entries.forEach { channel ->
            service.recordNotice(request.id.value,DeregistrationNoticeVo(disclosureReviewId="review",kind=EnumExitNoticeKind.CONSULTATION,channel=channel,
                startedOn=LocalDate.of(2026,6,1),endedOn=LocalDate.of(2026,7,31),urlOrLocation="https://museum.example.org/exit/1",publicContentFileIds="public",publicationEvidenceFileIds="evidence"))
        }
        assertThrows(BadRequestException::class.java) { service.requirePrepared(request) }
        assertThrows(BadRequestException::class.java) {
            service.recordAdministrative(request.id.value,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.PROVINCE,action=EnumAdministrativeAction.APPROVAL,
                authorityName="省文物部门",documentNo="批复001",issuedAt=LocalDate.of(2026,7,1),fileIds="approval"))
        }
        service.recordAdministrative(request.id.value,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.PROVINCE,action=EnumAdministrativeAction.APPROVAL,
            authorityName="省文物部门",documentNo="批复001",issuedAt=LocalDate.of(2026,8,3),fileIds="approval"))
        assertDoesNotThrow { service.requirePrepared(request) }
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["object"].lifeStatus)
        assertEquals(10,archives.list(CollectionConst.BIZ_DEREGISTER,request.id.value).size)
    }

    @Test fun `administrative evidence freezes a force majeure proposal and missing object can lawfully exit`() = fixture { request ->
        request.reason=EnumDeregReason.FORCE_MAJEURE
        request.dossier=request.dossier!!.copy(destinationType=EnumExitDestination.LOST)
        val collection=CollectionDao["object"]
        collection.lifeStatus=EnumCollectionStatus.MISSING
        service.recordAdministrative(request.id.value,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.COMPETENT,action=EnumAdministrativeAction.FILING,
            authorityName="主管部门",documentNo="备案01",issuedAt=LocalDate.of(2026,8,3),fileIds="approval"))
        assertThrows(BadRequestException::class.java) { service.requireEditable(request) }
        assertDoesNotThrow { service.requirePrepared(request) }
        collection.lifeStatus=EnumCollectionStatus.ON_LOAN
        assertThrows(BadRequestException::class.java) { service.requirePrepared(request) }
    }

    @Test fun `incomplete proposal remains editable and cannot freeze an expert assessment`() = fixture { request ->
        request.dossier=request.dossier!!.copy(keeperUserId=null)
        assertThrows(BadRequestException::class.java) { service.recordExpert(request.id.value,expert("1")) }
        assertDoesNotThrow { service.requireEditable(request) }
        assertTrue(service.experts(request.id.value).isEmpty())
        assertTrue(archives.list(CollectionConst.BIZ_DEREGISTER,request.id.value).isEmpty())
    }

    @Test fun `publicity fixes the complete expert set and the shared content across channels`() = fixture { request ->
        (1..5).forEach { service.recordExpert(request.id.value,expert("$it")) }
        val notice=DeregistrationNoticeVo(disclosureReviewId="review",kind=EnumExitNoticeKind.CONSULTATION,channel=EnumExitNoticeChannel.MUSEUM_ONSITE,
            startedOn=LocalDate.of(2026,6,1),endedOn=LocalDate.of(2026,7,31),urlOrLocation="馆内一楼",publicContentFileIds="public",publicationEvidenceFileIds="evidence")
        service.recordNotice(request.id.value,notice)
        assertThrows(BadRequestException::class.java) { service.recordExpert(request.id.value,expert("6")) }
        assertThrows(BadRequestException::class.java) { service.recordNotice(request.id.value,notice.copy(channel=EnumExitNoticeChannel.MUSEUM_WEBSITE,urlOrLocation="https://museum.example.org",publicContentFileIds="plan")) }
        assertThrows(BadRequestException::class.java) { service.recordNotice(request.id.value,notice.copy(channel=EnumExitNoticeChannel.MUSEUM_WEBSITE)) }
        assertEquals(1,service.notices(request.id.value).size)
    }

    private fun prepared(request: DeregistrationDao) {
        (1..5).forEach { service.recordExpert(request.id.value,expert("$it")) }
        EnumExitNoticeChannel.entries.forEach { channel -> service.recordNotice(request.id.value,DeregistrationNoticeVo(disclosureReviewId="review",kind=EnumExitNoticeKind.CONSULTATION,channel=channel,
            startedOn=LocalDate.of(2026,6,1),endedOn=LocalDate.of(2026,7,31),urlOrLocation="https://museum.example.org/exit/1",publicContentFileIds="public",publicationEvidenceFileIds="evidence")) }
        service.recordAdministrative(request.id.value,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.PROVINCE,action=EnumAdministrativeAction.APPROVAL,
            authorityName="省文物部门",documentNo="批复001",issuedAt=LocalDate.of(2026,8,3),fileIds="approval"))
    }

    @Test fun `statutory approval drives exit list filters detail and print document number`() = fixture { request ->
        prepared(request)
        val row = (workflow.findPage(org.springframework.mock.web.MockHttpServletRequest()).list as List<DeregistrationVo>).single()
        assertEquals(EnumApproveLevel.PROVINCE, row.approveLevel)
        assertEquals("批复001", row.approvalDocNo)
        assertEquals("批复001", workflow.get(request.id.value)!!.approvalDocNo)
        for ((key, value) in listOf("approveLevel" to "PROVINCE", "approvalDocNo" to "批复001", "keyword" to "批复001")) {
            val query = org.springframework.mock.web.MockHttpServletRequest().apply { addParameter(key, value) }
            assertEquals(1L, workflow.findPage(query).total, key)
        }
        for ((key, value) in listOf("approveLevel" to "NATIONAL", "approvalDocNo" to "旧批文", "keyword" to "旧批文")) {
            val query = org.springframework.mock.web.MockHttpServletRequest().apply { addParameter(key, value) }
            assertEquals(0L, workflow.findPage(query).total, key)
        }
        val printer = CollectionPrintService(collections, scope, mock(CollectionPrintTemplateService::class.java), mock(LoanStatutoryService::class.java), archives=archives)
        assertEquals("批复001", printer.print(EnumCollectionPrintType.DEREGISTRATION,request.id.value).documentNo)
    }

    @Test fun `filing document is searchable without mislabeling it as an approval level`() = fixture { request ->
        request.reason = EnumDeregReason.FORCE_MAJEURE
        request.dossier = request.dossier!!.copy(destinationType=EnumExitDestination.LOST,auxiliaryRecordNo=null,auxiliaryLocation=null)
        service.recordAdministrative(request.id.value,CollectionAdministrativeEvidenceVo(
            level=EnumAuthorityLevel.COMPETENT,action=EnumAdministrativeAction.FILING,
            authorityName="主管文物部门",documentNo="备案001",issuedAt=LocalDate.of(2026,8,3),fileIds="approval"))
        val row = (workflow.findPage(org.springframework.mock.web.MockHttpServletRequest()).list as List<DeregistrationVo>).single()
        assertNull(row.approveLevel)
        assertEquals("备案001",row.approvalDocNo)
        assertEquals(1L,workflow.findPage(org.springframework.mock.web.MockHttpServletRequest().apply {addParameter("approvalDocNo","备案001")}).total)
        assertEquals(0L,workflow.findPage(org.springframework.mock.web.MockHttpServletRequest().apply {addParameter("approveLevel","PROVINCE")}).total)
    }
    private fun approve(request: DeregistrationDao,round: String="1",approvedAt:LocalDateTime?=null) {
        request.flowStatus=EnumFlowStatus.AUDIT;request.flowInstanceId="exit-flow"
        locks.acquire("museum",CollectionConst.BIZ_DEREGISTER,request.id.value,listOf(request.collectionId))
        listOf("keeper","leader").forEachIndexed { index,node -> FlowInstanceLogDao.new {
            instanceId="exit-flow";taskId="$node-$round";nodeKey=node;action="APPROVE";operator=node;comment="本人核对原件，确认同意"
            approvedAt?.let {createTime=it.plusNanos(index.toLong()*1_000_000)}
        } }
        workflow.onFlowCompleted(request,FlowCompletedEvent("exit-flow",CollectionConst.FLOW_DEREGISTER,"退出",request.id.value,CollectionConst.BIZ_DEREGISTER,emptyMap(),"registrar","经办人"))
    }

    @Test fun `exit history rejects changed execution-only signed original bytes`()=fixture {request->
        prepared(request);approve(request)
        FileDao.new("execution-only"){name="签署订正.pdf";path="/files/execution-only.pdf";hash="execution-only";size=8;ext="pdf"}
        val path=originalRoot.resolve("files/execution-only.pdf")
        java.nio.file.Files.writeString(path,"ORIGINAL")
        workflow.execute(request.id.value,execution().copy(signedChangeFileIds="execution-only"))
        assertNotNull(workflow.get(request.id.value))
        val timestamp=java.nio.file.Files.getLastModifiedTime(path)
        java.nio.file.Files.writeString(path,"CHANGED!")
        java.nio.file.Files.setLastModifiedTime(path,timestamp)
        assertTrue(workflow.get(request.id.value)!!.evidenceErrors.isNotEmpty())
        assertFalse(workflow.get(request.id.value)!!.canOperate)
        assertUnavailableOriginals(workflow.archive(request.id.value))
    }

    @Test fun `approval awaits execution which retains original ledger and atomically creates auxiliary entry once`() = fixture { request ->
        prepared(request);approve(request)
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["object"].lifeStatus)
        assertEquals(0,CollectionAuxiliaryDao.all().count())
        val execution=execution()
        workflow.execute(request.id.value,execution)
        val executed=CollectionPermanentArchiveDao.all().single {it.bizId==request.id.value && it.phase=="EXECUTED"}
        assertTrue(executed.snapshot.contains("\"paperLedgerBookId\":\"paper-book\""))
        assertTrue(executed.snapshot.contains("\"paperLedgerPageNo\":1"))
        assertTrue(executed.snapshot.contains("总账第一册 第1页"))
        assertEquals(EnumCollectionStatus.DEREGISTERED,CollectionDao["object"].lifeStatus)
        assertEquals("000123",CollectionDao["object"].totalRegNo)
        val entry=auxiliary.list("museum").single()
        assertEquals("000123",entry.originTotalRegNo)
        assertEquals("F-001",entry.recordNo)
        assertEquals(request.id.value,entry.exitId)
        assertNotNull(request.executedAt)
        assertEquals(2,CollectionLedgerLogDao.all().count())
        val exitLog = CollectionLedgerLogDao.all().single { it.field == "lifeStatus" }
        assertEquals(EnumLedgerAction.DEREGISTER, exitLog.action)
        assertEquals("lifeStatus", exitLog.field)
        assertEquals(EnumCollectionStatus.IN_STORE.name, exitLog.before)
        assertEquals(EnumCollectionStatus.DEREGISTERED.name, exitLog.after)
        assertEquals(request.id.value, exitLog.refBizId)
        val voucherLog = CollectionLedgerLogDao.all().single { it.field == "deregVoucherNo" }
        assertNull(voucherLog.before)
        assertEquals(CollectionDao["object"].deregVoucherNo,voucherLog.after)
        assertEquals(request.id.value,voucherLog.refBizId)
        workflow.execute(request.id.value,execution)
        assertEquals(1,CollectionAuxiliaryDao.all().count())
        assertEquals(2,CollectionLedgerLogDao.all().count())
        assertTrue(CollectionOperationLockDao.all().empty())
        listOf(EnumExitNoticeChannel.MUSEUM_ONSITE,EnumExitNoticeChannel.MUSEUM_WEBSITE).forEach { channel ->
            service.recordNotice(request.id.value,DeregistrationNoticeVo(disclosureReviewId="review",kind=EnumExitNoticeKind.RESULT,channel=channel,startedOn=LocalDate.now(),
                urlOrLocation="https://museum.example.org/exit-result/1",publicContentFileIds="public",publicationEvidenceFileIds="evidence"))
        }
        assertNotNull(request.announcedAt)
        assertTrue(archives.list(CollectionConst.BIZ_DEREGISTER,request.id.value).any { it["phase"]=="CLOSED" })
    }

    @Test fun `completed exit retry requires the original signed execution`() = fixture { request ->
        prepared(request);approve(request)
        val original=execution()
        assertEquals(request.id.value,workflow.execute(request.id.value,original))
        val before=listOf(CollectionLedgerLogDao.count(),CollectionAuxiliaryDao.count(),CollectionPermanentArchiveDao.count())
        listOf(original.copy(signedChangeFileIds="evidence"),
            original.copy(dispositionReceiptFileIds="opinion"),
            original.copy(paperUpdatedAt=original.paperUpdatedAt.plusDays(1)),
            original.copy(paperLedgerPageNo=2)).forEach { changed ->
            assertThrows(BadRequestException::class.java) {workflow.execute(request.id.value,changed)}
            assertEquals(before,listOf(CollectionLedgerLogDao.count(),CollectionAuxiliaryDao.count(),CollectionPermanentArchiveDao.count()))
        }
        assertEquals(request.id.value,workflow.execute(request.id.value,original))
    }

    @Test fun `exit rejects free text and forged paper page without changing formal ledger`() = fixture { request ->
        prepared(request);approve(request)
        listOf(
            execution().copy(paperLedgerBookId=null,paperLedgerRef="任意纸账引用"),
            execution().copy(paperLedgerRef="伪造的账册页码"),
        ).forEach { value ->
            assertThrows(BadRequestException::class.java){workflow.execute(request.id.value,value)}
            assertNull(request.executedAt)
            assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["object"].lifeStatus)
            assertEquals(0L,CollectionAuxiliaryDao.count())
        }
    }

    @Test fun `restart cannot execute using previous round approval archive`() = fixture { request ->
        prepared(request);approve(request)
        FlowInstanceLogDao.new { instanceId="exit-flow";action="REACTIVATE";operator="manager";nodeKey="keeper";comment="重新核实";createTime=LocalDateTime.now().plusSeconds(1) }
        assertThrows(BadRequestException::class.java) { service.requireCurrentApprovalEvidence(request) }
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["object"].lifeStatus)
    }

    @Test fun `auxiliary registration never reclassifies a formal collection`() = fixture { request ->
        val vo=CollectionAuxiliaryVo(orgId="museum",recordNo="M-001",kind=EnumAuxiliaryKind.MODEL,name="教学模型",quantity=1,unit="件",location="教研室",registeredOn=LocalDate.now(),reason="核收为模型",evidenceFileIds="evidence")
        assertThrows(BadRequestException::class.java) { auxiliary.register(vo.copy(originCollectionId="object")) }
        assertThrows(BadRequestException::class.java) { auxiliary.register(vo.copy(kind=EnumAuxiliaryKind.LAWFUL_EXIT)) }
        assertThrows(BadRequestException::class.java) { auxiliary.registerExit(request,"evidence") }
        val id=auxiliary.register(vo)
        assertEquals("教学模型",CollectionAuxiliaryDao[id].content.name)
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["object"].lifeStatus)
        assertEquals("000123",CollectionDao["object"].totalRegNo)
        assertThrows(BadRequestException::class.java) { auxiliary.register(vo) }
        val detail=auxiliary.detail("museum",id)
        val archiveId=detail["archiveId"] as String
        assertEquals(id,(detail["record"] as CollectionAuxiliaryVo).id)
        assertEquals(listOf("evidence"),(detail["files"] as List<*>).map {(it as Map<*,*>)["fileId"]})
        assertEquals(archiveId,auxiliary.requireArchivedOriginal("museum",id,archiveId,"evidence"))
        assertThrows(BadRequestException::class.java) { auxiliary.requireArchivedOriginal("museum",id,"wrong-archive","evidence") }
        assertThrows(BadRequestException::class.java) { auxiliary.requireArchivedOriginal("museum",id,archiveId,"other-file") }
        assertThrows(BadRequestException::class.java) { auxiliary.detail("other",id) }
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { auxiliary.requireArchivedOriginal("museum",id,archiveId,"evidence") }
    }

    private fun auxiliaryValue(no:String="M-001")=CollectionAuxiliaryVo(orgId="museum",recordNo=no,kind=EnumAuxiliaryKind.MODEL,name="实际教学模型",quantity=1,unit="件",location="教研室",registeredOn=LocalDate.now(),reason="真实非藏品分类依据",evidenceFileIds="evidence")

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("list,revoked","list,disabled","list,moved","register,revoked","register,disabled","register,moved","register,warehouse","register,keeper")
    fun `auxiliary current duties cannot be replaced by old ledger login`(action:String,change:String)=fixture {
        auxiliary.register(auxiliaryValue());assertEquals(1,auxiliary.list("museum").size)
        when(change) {
            "revoked"->io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
            "disabled"->io.hizhengfu.cloud.sys.system.dao.UserDao["registrar"].enabled=false
            "moved"->io.hizhengfu.cloud.sys.system.dao.UserDao["registrar"].groupId="other"
            "warehouse"->actorRole("registrar","FILE_registrar",setOf(CollectionConst.WAREHOUSE_MANAGE))
            "keeper"->actorRole("registrar",CollectionConst.ROLE_KEEPER)
        }
        assertThrows(BadRequestException::class.java){if(action=="list")auxiliary.list("museum") else auxiliary.register(auxiliaryValue("M-002"))}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["revoked","disabled","moved","warehouse","missingExitDuty","missingLedgerDuty","foreignCollection"])
    fun `exit execution checks current authority before changing formal ledger`(change:String)=fixture {request ->
        prepared(request);approve(request)
        when(change) {
            "missingExitDuty"->{io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll();actorRole("registrar","FILE_registrar",setOf(CollectionConst.LEDGER_CORRECT))}
            "missingLedgerDuty"->{io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll();actorRole("registrar","FILE_registrar",setOf(CollectionConst.DEREGISTER))}
            "foreignCollection"->CollectionDao["object"].orgId="other"

            "revoked"->io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
            "disabled"->io.hizhengfu.cloud.sys.system.dao.UserDao["registrar"].enabled=false
            "moved"->io.hizhengfu.cloud.sys.system.dao.UserDao["registrar"].groupId="other"
            "warehouse"->actorRole("registrar","FILE_registrar",setOf(CollectionConst.WAREHOUSE_MANAGE))
        }
        val value=execution()
        assertAll({assertThrows(BadRequestException::class.java){workflow.execute(request.id.value,value)}},
            {assertNull(request.executedAt)}, {assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["object"].lifeStatus)},
            {assertEquals(0L,CollectionAuxiliaryDao.count())})
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["revoked","disabled","moved","warehouse","missingExitDuty","missingLedgerDuty","foreignCollection"])
    fun `completed exit retry rejects stale authority without changing original ledger or archive`(change:String)=fixture { request ->
        prepared(request);approve(request)
        val value=execution()
        assertEquals(request.id.value,workflow.execute(request.id.value,value))
        when(change) {
            "missingExitDuty"->{io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll();actorRole("registrar","FILE_registrar",setOf(CollectionConst.LEDGER_CORRECT))}
            "missingLedgerDuty"->{io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll();actorRole("registrar","FILE_registrar",setOf(CollectionConst.DEREGISTER))}
            "foreignCollection"->CollectionDao["object"].orgId="other"
            "revoked"->io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
            "disabled"->io.hizhengfu.cloud.sys.system.dao.UserDao["registrar"].enabled=false
            "moved"->io.hizhengfu.cloud.sys.system.dao.UserDao["registrar"].groupId="other"
            "warehouse"->actorRole("registrar","FILE_registrar",setOf(CollectionConst.WAREHOUSE_MANAGE))
        }
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().commit()
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.clear()
        fun state()=listOf(Collections,Deregistrations,CollectionPermanentArchives,
            CollectionPermanentArchiveFiles,CollectionAuxiliaries,CollectionLedgerLogs,CollectionOperationLocks).map { table ->
            table.selectAll().orderBy(table.id).map { row -> table.columns.map { row[it] } }
        }
        val before=state()
        assertThrows(BadRequestException::class.java) {workflow.execute(request.id.value,value)}
        assertEquals(before,state())
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["name","number","contentOrg","contentId","contentExit","projection","archiveOrg","missingArchive","missingFiles","fileOrg"])
    fun `auxiliary read verifies registered original and complete evidence`(change:String)=fixture {
        val id=auxiliary.register(auxiliaryValue())
        assertEquals("实际教学模型",auxiliary.list("museum").single().name)
        val row=CollectionAuxiliaryDao[id]
        val archive=CollectionPermanentArchiveDao.all().single {it.bizType=="COLLECTION_AUXILIARY" && it.bizId==id}
        val refs=CollectionPermanentArchiveFileDao.all().filter {it.archiveId==archive.id.value}
        assertEquals(1,refs.size)
        when(change) {
            "name"->row.content=row.content.copy(name="被替换的名称")
            "number"->row.content=row.content.copy(recordNo="被掩盖的编号")
            "contentOrg"->row.content=row.content.copy(orgId="other")
            "contentId"->row.content=row.content.copy(id="other-row")
            "contentExit"->row.content=row.content.copy(exitId="other-exit")
            "projection"->row.content=row.content.copy(institutionHandover=mapOf("state" to "HANDED_OVER"))
            "archiveOrg"->archive.orgId="other"
            "missingArchive"->archive.delete()
            "missingFiles"->refs.forEach {it.delete()}
            "fileOrg"->refs.single().orgId="other"
        }
        assertThrows(BadRequestException::class.java){auxiliary.list("museum")}
    }

    @Test fun `repeating an executed exit still requires current duties`()=fixture {request->
        prepared(request);approve(request)
        val value=execution()
        workflow.execute(request.id.value,value)
        val existing=auxiliary.list("museum").single().id
        assertEquals(existing,auxiliary.registerExit(request,"evidence"))
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){workflow.execute(request.id.value,value)}
        assertThrows(BadRequestException::class.java){auxiliary.registerExit(request,"evidence")}
        assertEquals(1L,CollectionAuxiliaryDao.count())
    }

    @Test fun `current cross museum auxiliary read does not grant external writes or bypass single museum`()=fixture {
        val multi=CollectionAuxiliaryService(CollectionScopeResolver("multi",""),binder,archives)
        multi.register(auxiliaryValue())
        io.hizhengfu.cloud.sys.system.dao.UserDao["registrar"].groupId="other"
        val id=multi.register(auxiliaryValue().copy(orgId="other"))
        io.hizhengfu.cloud.sys.system.dao.UserDao["registrar"].groupId="museum"
        assertThrows(BadRequestException::class.java){multi.list("other")}
        actorRole("registrar","FILE_registrar",setOf(CollectionConst.CROSS_ORG))
        assertEquals(id,multi.list("other").single().id)
        assertThrows(BadRequestException::class.java){multi.register(auxiliaryValue("M-002").copy(orgId="other"))}
        assertThrows(BadRequestException::class.java){auxiliary.list("other")}
        assertEquals(2L,CollectionAuxiliaryDao.count())
    }

    @Test fun `disabled museum auxiliary history requires explicit current read and cannot be written`()=fixture {
        val id=auxiliary.register(auxiliaryValue())
        CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        assertThrows(BadRequestException::class.java){auxiliary.list("museum")}
        actorRole("registrar","FILE_registrar",setOf(CollectionConst.CROSS_ORG))
        assertEquals(id,auxiliary.list("museum").single().id)
        assertThrows(BadRequestException::class.java){auxiliary.register(auxiliaryValue("M-002"))}
        assertEquals(1L,CollectionAuxiliaryDao.count())
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("get,revoked","get,disabled","get,moved","page,revoked","page,disabled","page,moved","archive,revoked","archive,disabled","archive,moved","expert,revoked","expert,disabled","expert,moved","flow,revoked","flow,disabled","flow,moved","save,revoked","save,disabled","save,moved")
    fun `exit current access cannot reuse cached registrar login`(action:String,change:String)=fixture {request->
        assertNotNull(workflow.get(request.id.value))
        assertEquals(1L,workflow.findPage(org.springframework.mock.web.MockHttpServletRequest()).total)
        when(change) {
            "revoked"->io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
            "disabled"->io.hizhengfu.cloud.sys.system.dao.UserDao["registrar"].enabled=false
            "moved"->io.hizhengfu.cloud.sys.system.dao.UserDao["registrar"].groupId="other"
        }
        if(action=="page" && change=="moved") {
            assertEquals(0L,workflow.findPage(org.springframework.mock.web.MockHttpServletRequest()).total)
        } else assertThrows(BadRequestException::class.java) {
            when(action) {
                "get"->workflow.get(request.id.value)
                "page"->workflow.findPage(org.springframework.mock.web.MockHttpServletRequest())
                "archive"->workflow.archive(request.id.value)
                "expert"->service.recordExpert(request.id.value,expert("new"))
                "flow"->workflow.getFlowInfo(request.id.value)
                "save"->workflow.save(request.toModel().copy(detail="不应写入"))
            }
        }
        assertEquals("已核实低于入藏标准",request.detail)
        assertEquals(0L,DeregistrationExpertOpinionDao.count())
    }

    @Test fun `exit list never borrows receiving museum private name`()=fixture {request->
        CollectionDao["object"].apply {orgId="other";name="接收馆内部名称"}
        val page=workflow.findPage(org.springframework.mock.web.MockHttpServletRequest())
        val row=(page.list as List<*>).single() as DeregistrationVo
        assertEquals("残损标本",row.collectionName)
        val query=org.springframework.mock.web.MockHttpServletRequest().apply {addParameter("keyword","接收馆内部名称")}
        assertEquals(0L,workflow.findPage(query).total)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["expertOrg","noticeOrg","adminOrg","expertBody","noticeBody","adminBody","proposal","missingExpert","missingNotice","missingAdmin","proposalFiles","expertFiles","noticeFiles","adminFiles"])
    fun `exit detail checks fixed proposal every original and complete file references`(change:String)=fixture {request->
        prepared(request)
        assertEquals(5,workflow.get(request.id.value)!!.experts.size)
        val printer=CollectionPrintService(collections,scope,mock(CollectionPrintTemplateService::class.java),mock(LoanStatutoryService::class.java),archives=archives)
        assertEquals(request.id.value,printer.print(EnumCollectionPrintType.DEREGISTRATION,request.id.value).businessId)
        val expert=DeregistrationExpertOpinionDao.all().first()
        val notice=DeregistrationNoticeDao.all().first()
        val admin=CollectionAdministrativeRecordDao.all().first()
        when(change) {
            "expertOrg"->expert.orgId="other"
            "noticeOrg"->notice.orgId="other"
            "adminOrg"->admin.orgId="other"
            "expertBody"->expert.content=expert.content.copy(opinion="被替换的意见")
            "noticeBody"->notice.content=notice.content.copy(urlOrLocation="被替换的实发地点")
            "adminBody"->admin.content=admin.content.copy(documentNo="被替换的文号")
            "proposal"->request.detail="被替换的理由"
            "missingExpert"->expert.delete()
            "missingNotice"->notice.delete()
            "missingAdmin"->admin.delete()
            else->{
                val phase=when(change){"proposalFiles"->"PROPOSAL";"expertFiles"->"EXPERT_${expert.id.value}";"noticeFiles"->"NOTICE_${notice.id.value}";else->"ADMIN_${admin.id.value}"}
                val original=CollectionPermanentArchiveDao.all().single {it.bizId==request.id.value && it.phase==phase}
                val refs=CollectionPermanentArchiveFileDao.all().filter {it.archiveId==original.id.value}
                assertTrue(refs.isNotEmpty());refs.forEach {it.delete()}
            }
        }
        assertThrows(BadRequestException::class.java){printer.print(EnumCollectionPrintType.DEREGISTRATION,request.id.value)}
        assertThrows(BadRequestException::class.java){workflow.get(request.id.value)}
        assertThrows(BadRequestException::class.java){workflow.getFlowInfo(request.id.value)}
        assertThrows(BadRequestException::class.java){workflow.findPage(org.springframework.mock.web.MockHttpServletRequest())}
    }

    @Test fun `current exit read survives transfer without exposing new private primary data`()=fixture {request->
        prepared(request)
        CollectionDao["object"].apply {orgId="other";name="接收馆内部资料"}
        val record=workflow.get(request.id.value)!!
        assertEquals("残损标本",record.collectionName);assertNotNull(record.sourceIssue);assertFalse(record.canOperate)
        assertEquals(5,record.experts.size)
        assertThrows(BadRequestException::class.java){service.recordExpert(request.id.value,expert("6"))}
    }

    @Test fun `exit history explicit cross grant still cannot borrow external name or write outside museum`()=fixture {request->
        prepared(request)
        actorRole("registrar","FILE_registrar",setOf(CollectionConst.CROSS_ORG))
        CollectionDao["object"].apply {orgId="other";name="他馆私有名称"}
        val query=org.springframework.mock.web.MockHttpServletRequest().apply {addParameter("keyword","他馆私有名称")}
        assertEquals(0L,workflow.findPage(query).total)
        query.removeParameter("keyword");query.addParameter("orgId","other")
        assertThrows(BadRequestException::class.java){workflow.findPage(query)}
        CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        assertEquals(5,workflow.get(request.id.value)!!.experts.size)
        assertFalse(workflow.get(request.id.value)!!.canOperate)
        assertThrows(BadRequestException::class.java){service.publicReviewOptions(request.id.value)}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["comment","operatorName","time","missingFiles"])
    fun `approved exit must match frozen approval logs before execution`(change:String)=fixture {request->
        prepared(request);approve(request)
        assertNotNull(service.requireCurrentApprovalEvidence(request))
        val log=FlowInstanceLogDao.all().first()
        when(change) {
            "comment"->log.comment="后改的审批意见"
            "operatorName"->log.operatorName="后改的签署人名称"
            "time"->FlowInstanceLogDao.all().last().apply {createTime=createTime.plusMinutes(1)}
            "missingFiles"->{val archived=CollectionPermanentArchiveDao.all().single {it.phase.startsWith("APPROVED_")};CollectionPermanentArchiveFileDao.all().filter {it.archiveId==archived.id.value}.forEach {it.delete()}}
        }
        assertThrows(BadRequestException::class.java){workflow.execute(request.id.value,execution())}
        assertNull(request.executedAt);assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["object"].lifeStatus)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["approvalLog","executionMissing","executionTime","closedMissing","expertMissing","noticeBody"])
    fun `result notice cannot extend inconsistent exit history`(change:String)=fixture {request->
        closeExit(request)
        when(change) {
            "approvalLog"->FlowInstanceLogDao.all().first().comment="后改审批原文"
            "executionMissing"->CollectionPermanentArchiveDao.all().single {it.phase=="EXECUTED"}.delete()
            "executionTime"->request.executedAt=request.executedAt!!.plusMinutes(1)
            "closedMissing"->CollectionPermanentArchiveDao.all().single {it.phase=="CLOSED"}.delete()
            "expertMissing"->DeregistrationExpertOpinionDao.all().first().delete()
            "noticeBody"->DeregistrationNoticeDao.all().first().apply {content=content.copy(urlOrLocation="被替换的位置")}
        }
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.clear()
        val notices=DeregistrationNoticeDao.all().count()
        val archivesBefore=CollectionPermanentArchiveDao.all().associate {it.id.value to it.snapshot}
        val closedAt=request.announcedAt
        assertThrows(BadRequestException::class.java){service.publicReviewOptions(request.id.value)}
        assertThrows(BadRequestException::class.java){service.recordNotice(request.id.value,DeregistrationNoticeVo(disclosureReviewId="review",kind=EnumExitNoticeKind.RESULT,channel=EnumExitNoticeChannel.MUSEUM_WEBSITE,startedOn=LocalDate.now(),urlOrLocation="https://museum.example.org/added",publicContentFileIds="public",publicationEvidenceFileIds="evidence"))}
        assertEquals(notices,DeregistrationNoticeDao.all().count())
        assertEquals(archivesBefore,CollectionPermanentArchiveDao.all().associate {it.id.value to it.snapshot})
        assertEquals(closedAt,request.announcedAt)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["expert","consultation","administrative"])
    fun `materials cannot extend changed prior expert evidence`(next:String)=fixture {request->
        val n=if(next=="expert")1 else 5
        (1..n).forEach {service.recordExpert(request.id.value,expert("$it"))}
        if(next=="administrative")EnumExitNoticeChannel.entries.forEach {channel->service.recordNotice(request.id.value,DeregistrationNoticeVo(disclosureReviewId="review",kind=EnumExitNoticeKind.CONSULTATION,channel=channel,startedOn=LocalDate.of(2026,6,1),endedOn=LocalDate.of(2026,7,31),urlOrLocation="https://museum.example.org/exit",publicContentFileIds="public",publicationEvidenceFileIds="evidence"))}
        DeregistrationExpertOpinionDao.all().first().apply {content=content.copy(opinion="后来替换的原意见")}
        val before=listOf(DeregistrationExpertOpinionDao.all().count(),DeregistrationNoticeDao.all().count(),CollectionAdministrativeRecordDao.all().count(),CollectionPermanentArchiveDao.all().count())
        assertThrows(BadRequestException::class.java){when(next) {
            "expert"->service.recordExpert(request.id.value,expert("6"))
            "consultation"->service.recordNotice(request.id.value,DeregistrationNoticeVo(disclosureReviewId="review",kind=EnumExitNoticeKind.CONSULTATION,channel=EnumExitNoticeChannel.MUSEUM_ONSITE,startedOn=LocalDate.of(2026,6,1),endedOn=LocalDate.of(2026,7,31),urlOrLocation="馆内一楼",publicContentFileIds="public",publicationEvidenceFileIds="evidence"))
            else->service.recordAdministrative(request.id.value,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.PROVINCE,action=EnumAdministrativeAction.APPROVAL,authorityName="省文物部门",documentNo="批复002",issuedAt=LocalDate.of(2026,8,3),fileIds="approval"))
        }}
        assertEquals(before,listOf(DeregistrationExpertOpinionDao.all().count(),DeregistrationNoticeDao.all().count(),CollectionAdministrativeRecordDao.all().count(),CollectionPermanentArchiveDao.all().count()))
    }

    private fun executeExit(request:DeregistrationDao) {
        prepared(request);approve(request)
        workflow.execute(request.id.value,execution())
    }
    private fun closeExit(request:DeregistrationDao) {
        executeExit(request)
        listOf(EnumExitNoticeChannel.MUSEUM_ONSITE,EnumExitNoticeChannel.MUSEUM_WEBSITE).forEach {channel->
            service.recordNotice(request.id.value,DeregistrationNoticeVo(disclosureReviewId="review",kind=EnumExitNoticeKind.RESULT,channel=channel,startedOn=LocalDate.now(),
                urlOrLocation="https://museum.example.org/result",publicContentFileIds="public",publicationEvidenceFileIds="evidence"))
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["executionTime","clearedExecution","missingExecution","executionFiles","flowInstance","approvedBy","formalState","closureTime","clearedClosure","missingClosure","closureFiles"])
    fun `exit lifecycle read rejects changed or missing execution and closure facts`(change:String)=fixture {request->
        closeExit(request);assertNotNull(workflow.get(request.id.value))
        fun original(phase:String)=CollectionPermanentArchiveDao.all().single {it.bizId==request.id.value && it.phase==phase}
        when(change) {
            "executionTime"->request.executedAt=request.executedAt!!.plusMinutes(1)
            "clearedExecution"->request.executedAt=null
            "missingExecution"->original("EXECUTED").delete()
            "executionFiles"->CollectionPermanentArchiveFileDao.all().filter {it.archiveId==original("EXECUTED").id.value}.forEach {it.delete()}
            "flowInstance"->request.flowInstanceId="其他流程"
            "approvedBy"->request.approvedBy="其他批准人"
            "formalState"->CollectionDao["object"].lifeStatus=EnumCollectionStatus.IN_STORE
            "closureTime"->request.announcedAt=request.announcedAt!!.plusMinutes(1)
            "clearedClosure"->request.announcedAt=null
            "missingClosure"->original("CLOSED").delete()
            "closureFiles"->CollectionPermanentArchiveFileDao.all().filter {it.archiveId==original("CLOSED").id.value}.forEach {it.delete()}
        }
        assertThrows(BadRequestException::class.java){workflow.get(request.id.value)}
    }

    @Test fun `submitted original remains required after approval`()=fixture {request->
        prepared(request)
        DeregistrationService::class.java.getDeclaredMethod("beforeSubmit",DeregistrationDao::class.java).apply {isAccessible=true}.invoke(workflow,request)
        approve(request)
        assertNotNull(workflow.get(request.id.value))
        CollectionPermanentArchiveDao.all().single {it.phase=="SUBMITTED"}.delete()
        assertThrows(BadRequestException::class.java){workflow.get(request.id.value)}
        assertThrows(BadRequestException::class.java){workflow.execute(request.id.value,execution())}
        assertNull(request.executedAt)
    }

    @Test fun `legacy lifecycle originals remain readable without being resealed`()=fixture {request->
        closeExit(request)
        val originals=CollectionPermanentArchiveDao.all().filter {it.phase.startsWith("APPROVED_") || it.phase in setOf("EXECUTED","CLOSED")}
        originals.forEach {archive->
            val root=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(archive.snapshot) as tools.jackson.databind.node.ObjectNode
            listOf("lifecycleVersion","proposalArchive","priorApprovals","priorApprovalCount","submissionState","submissionArchive","approvalArchive","executionArchive").forEach {root.remove(it)}
            archive.snapshot=root.toString();archive.digest=CollectionCareEvidence.digest(archive.snapshot)
        }
        val before=originals.associate {it.id.value to it.snapshot}
        assertNotNull(workflow.get(request.id.value))
        assertEquals(request.id.value,workflow.execute(request.id.value,execution()))
        assertEquals(before,originals.associate {it.id.value to it.snapshot})
        FlowInstanceLogDao.all().first().comment="旧原件签署后来被替换"
        assertThrows(BadRequestException::class.java){workflow.get(request.id.value)}
        assertThrows(BadRequestException::class.java){workflow.execute(request.id.value,execution())}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["changed","transferred","missing"])
    fun `exit historical print keeps proposal identity without reading mutable primary`(change:String)=fixture {request->
        closeExit(request)
        val printer=CollectionPrintService(collections,scope,mock(CollectionPrintTemplateService::class.java),mock(LoanStatutoryService::class.java),archives=archives)
        val before=printer.deregistrationPrint(request.id.value)
        assertEquals("残损标本",before.collections.single().name)
        when(change) {
            "changed"->{CollectionDao["object"].apply {name="当前更正后名称";quantity=9};CollectionOrgDao["museum"].name="当前单位名称"}
            "transferred"->CollectionDao["object"].apply {orgId="other";name="接收馆内部名称"}
            "missing"->CollectionDao["object"].delete()
        }
        val historical=printer.deregistrationPrint(request.id.value)
        assertEquals("残损标本",historical.collections.single().name)
        assertEquals("000123",historical.collections.single().totalRegNo)
        assertEquals(before.collections.single().quantity,historical.collections.single().quantity)
        assertEquals("测试馆",historical.orgName)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["oldComment","oldApprovalMissing","oldApprovalFiles"])
    fun `new approval keeps earlier completed approval originals linked`(change:String)=fixture {request->
        val firstApproval=LocalDateTime.of(2026,8,4,12,0,0,123000000)
        prepared(request);approve(request,approvedAt=firstApproval)
        val earlier=CollectionPermanentArchiveDao.all().single {it.phase.startsWith("APPROVED_")}
        val oldLog=FlowInstanceLogDao.all().first()
        FlowInstanceLogDao.new {instanceId="exit-flow";action="REACTIVATE";operator="manager";nodeKey="keeper";comment="重新核对";createTime=firstApproval.plusDays(1)}
        request.flowStatus=EnumFlowStatus.AUDIT
        approve(request,"2",firstApproval.plusDays(2))
        assertNotNull(workflow.get(request.id.value))
        when(change) {
            "oldComment"->oldLog.comment="后改旧轮次意见"
            "oldApprovalMissing"->earlier.delete()
            "oldApprovalFiles"->CollectionPermanentArchiveFileDao.all().filter {it.archiveId==earlier.id.value}.forEach {it.delete()}
        }
        assertThrows(BadRequestException::class.java){workflow.get(request.id.value)}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["approval","execution","closure","newPrintMissing"])
    fun `new stage reference identity cannot be replaced even if local digest is recomputed`(change:String)=fixture {request->
        closeExit(request);assertNotNull(workflow.get(request.id.value))
        val phase=when(change){"approval"->"APPROVED_leader-1";"execution"->"EXECUTED";"closure"->"CLOSED";else->"PROPOSAL"}
        val archive=CollectionPermanentArchiveDao.all().single {it.bizId==request.id.value && it.phase==phase}
        val root=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(archive.snapshot) as tools.jackson.databind.node.ObjectNode
        if(change=="newPrintMissing")root.remove("printOriginal")
        else (root.get(when(change){"approval"->"proposalArchive";"execution"->"approvalArchive";else->"executionArchive"}) as tools.jackson.databind.node.ObjectNode).put("id","other-original")
        archive.snapshot=root.toString();archive.digest=CollectionCareEvidence.digest(archive.snapshot)
        assertThrows(BadRequestException::class.java){workflow.get(request.id.value)}
    }

    @Test fun `later result notice preserves first closure original and remains readable`()=fixture {request->
        closeExit(request)
        val closed=CollectionPermanentArchiveDao.all().single {it.phase=="CLOSED"};val original=closed.snapshot
        service.recordNotice(request.id.value,DeregistrationNoticeVo(disclosureReviewId="review",kind=EnumExitNoticeKind.RESULT,channel=EnumExitNoticeChannel.MUSEUM_WEBSITE,startedOn=LocalDate.now(),urlOrLocation="https://museum.example.org/later",publicContentFileIds="public",publicationEvidenceFileIds="evidence"))
        assertEquals(6,workflow.get(request.id.value)!!.notices.size)
        assertEquals(original,closed.snapshot)
    }

    @Test fun `legacy proposal print uses only retained baseline and never backfills current identifiers`()=fixture {request->
        prepared(request)
        val proposal=CollectionPermanentArchiveDao.all().single {it.phase=="PROPOSAL"}
        val root=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(proposal.snapshot) as tools.jackson.databind.node.ObjectNode
        root.remove("printOriginal");root.remove("printOriginalVersion")
        proposal.snapshot=root.toString();proposal.digest=CollectionCareEvidence.digest(proposal.snapshot)
        val original=proposal.snapshot
        CollectionDao["object"].apply {orgId="other";name="当前外馆名称"}
        val printer=CollectionPrintService(collections,scope,mock(CollectionPrintTemplateService::class.java),mock(LoanStatutoryService::class.java),archives=archives)
        val result=printer.deregistrationPrint(request.id.value)
        assertEquals("残损标本",result.collections.single().name);assertNull(result.collections.single().totalRegNo);assertNull(result.orgName)
        assertTrue(result.fields.any {it.value?.contains("未留存")==true})
        assertEquals(original,proposal.snapshot)
    }

}
