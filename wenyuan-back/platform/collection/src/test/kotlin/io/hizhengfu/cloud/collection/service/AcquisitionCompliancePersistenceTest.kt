package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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
import java.math.BigDecimal

class AcquisitionCompliancePersistenceTest {
    private val scope=CollectionScopeResolver("single","museum")
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c->c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val service=AcquisitionComplianceService(scope,binder,archives)
    private val flow=AcquisitionService(mock(FlowInstanceService::class.java),scope,binder,service,CollectionRetainedLedgerService(scope,archives))
    private fun login(permission:String=CollectionConst.ACQUIRE) {
        val user=SecurityUser(id="acquirer",name="征集经办",username="acquirer",password="",groupIds=emptySet(),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(permission)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    @BeforeEach fun prepare() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context);login()
    }
    @AfterEach fun cleanup()=SecurityContextHolder.clearContext()
    private fun fixture(block:(AcquisitionDao)->Unit) {
        val db=Database.connect("jdbc:sqlite:file:acq-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
        transaction(db) {
            originalReaderSchema()
            SchemaUtils.create(CollectionOrgs,Acquisitions,AcquisitionComplianceCases,AcquisitionExpertOpinions,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
            CollectionOrgDao.new("museum") {name="本馆";code="museum";systemGroupId="museum"}
            originalReader("acquirer",CollectionConst.ACQUIRE)
            FileDao.new("proof") { name="原始资料.pdf";path="/files/proof.pdf";hash="proof";size=100;ext="pdf" }
            val entity=AcquisitionDao.new("purchase") { orgId="museum";name="陶器";source=EnumSource.PURCHASE;holder="合法持有人";quantity=1;unit="件";reason="符合收藏方向";completeness=EnumCompleteness.COMPLETE;completeStatus="完好" }
            block(entity)
        }
    }
    private val investigation=AcquisitionDossierVo(EnumCollectionNature.RELIC,LocalDate.of(2026,5,1),"proof","proof","proof",true,"proof",setOf("seller"),"proof")
    private fun opinion(index:Int,agree:Boolean=true)=AcquisitionExpertOpinionVo(expertKey="expert-$index",expertName="专家$index",organization="文博机构$index",professionalTitle="研究馆员",specialty="陶器",qualificationFileIds="proof",independenceFileIds="proof",authenticityOpinion="真品",valueOpinion="具有研究价值",provenanceOpinion="来源流传清楚",proposedPriceCents=10000L*index,agree=agree,signedOpinionFileIds="proof",assessedOn=LocalDate.of(2026,5,10))
    private fun completion()=AcquisitionCompletionVo(decidedOn=LocalDate.of(2026,5,12),collectiveDecisionFileIds="proof",agreementFileIds="proof",agreedOn=LocalDate.of(2026,5,15),actualAcquiredOn=LocalDate.of(2026,5,16),handoverReceiptFileIds="proof",publicDisclosurePlan="在官网公开征集管理使用情况，估价资料不公开",comparablePriceFileIds="proof",suggestedPriceCents=20000,valuationReason="专家均价并结合本馆同类记录和市场成交价",highValueAssessmentRequired=false,highValueDeterminationFileIds="proof",negotiatedPriceCents=19000,negotiatedOn=LocalDate.of(2026,5,13),negotiationFileIds="proof",paymentFileIds="proof")

    @Test fun `one dissent stops purchase and original opinion cannot be overwritten`()=fixture { entity->
        service.saveDossier(entity.id.value,investigation);service.recordExpert(entity.id.value,opinion(1,false))
        assertEquals(EnumFlowStatus.REJECT,entity.flowStatus)
        assertThrows(BadRequestException::class.java) { service.recordExpert(entity.id.value,opinion(2)) }
        assertThrows(BadRequestException::class.java) { service.complete(entity.id.value,completion()) }
        assertThrows(BadRequestException::class.java) { service.requireEditable(entity) }
        assertEquals(3,archives.list(CollectionConst.BIZ_ACQUISITION,entity.id.value).size)
    }
    @Test fun `three independent prices determine evidence basis and ceiling blocks excessive deal`()=fixture { entity->
        service.saveDossier(entity.id.value,investigation)
        assertThrows(BadRequestException::class.java) { service.recordExpert(entity.id.value,opinion(1).copy(userId="seller")) }
        service.recordExpert(entity.id.value,opinion(1));service.recordExpert(entity.id.value,opinion(2))
        assertThrows(BadRequestException::class.java) { service.complete(entity.id.value,completion()) }
        assertThrows(BadRequestException::class.java) { service.recordExpert(entity.id.value,opinion(1)) }
        service.recordExpert(entity.id.value,opinion(3))
        assertEquals(BigDecimal("20000.00"),AcquisitionCompliancePolicy.averagePrice(service.experts(entity.id.value)))
        assertThrows(BadRequestException::class.java) { service.complete(entity.id.value,completion().copy(negotiatedPriceCents=20001)) }
        assertThrows(BadRequestException::class.java) { service.complete(entity.id.value,completion().copy(highValueAssessmentRequired=true)) }
        assertNull(service.case(entity.id.value)!!.completion)
        service.complete(entity.id.value,completion())
        assertEquals(19000L,entity.proposedPrice)
        assertEquals(LocalDate.of(2026,5,16),entity.acquireDate)
        assertThrows(BadRequestException::class.java) { service.saveDossier(entity.id.value,investigation.copy(lawfulSource=false)) }
        assertThrows(BadRequestException::class.java) { service.complete(entity.id.value,completion()) }
        assertThrows(BadRequestException::class.java) { service.requireEditable(entity) }
        entity.flowStatus=EnumFlowStatus.AUDIT;entity.flowInstanceId="flow-1"
        flow.onFlowCompleted(entity,FlowCompletedEvent("flow-1",CollectionConst.FLOW_ACQUISITION,"征集",entity.id.value,CollectionConst.BIZ_ACQUISITION,emptyMap(),"acquirer","征集经办"))
        assertEquals(EnumFlowStatus.PASS,entity.flowStatus)
        assertNull(entity.collectionId)
        assertTrue(archives.list(CollectionConst.BIZ_ACQUISITION,entity.id.value).any { it["phase"]=="APPROVED_flow-1" })
    }
    @Test fun `donation requires all statutory agreement terms and uncertain source cannot be accepted`()=fixture { entity->
        entity.source=EnumSource.DONATE
        assertThrows(BadRequestException::class.java) { service.saveDossier(entity.id.value,investigation.copy(lawfulSource=false)) }
        service.saveDossier(entity.id.value,investigation)
        assertThrows(BadRequestException::class.java) { service.complete(entity.id.value,completion()) }
        val terms=DonationAgreementTermsVo("第1条详细信息","第2条来源合法且无权利瑕疵","第3条交接验收","第4条所有权转移","第5条双方权利义务","第6条特殊情形","第7条争议解决")
        assertThrows(BadRequestException::class.java) { service.complete(entity.id.value,completion().copy(donation=terms.copy(ownershipTransfer=""))) }
        service.complete(entity.id.value,completion().copy(donation=terms))
        assertDoesNotThrow { service.requirePrepared(entity) }
    }
    @Test fun `general collection readers cannot retrieve confidential price and evidence`()=fixture { entity->
        service.saveDossier(entity.id.value,investigation)
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteWhere {io.hizhengfu.cloud.sys.system.dao.RolePermissions.permissionId eq CollectionConst.ACQUIRE}
        actorRole("acquirer","GENERAL_READER",setOf(CollectionConst.SELECT))
        login(CollectionConst.SELECT)
        assertThrows(BadRequestException::class.java) { service.detail(entity.id.value) }
        val projected=AcquisitionComplianceService.visibleRecord(scope,entity.toModel().copy(proposedPrice=100,appraisedValue=200,expertOpinionAttachmentIds="proof",images="proof"))
        assertNull(projected.proposedPrice);assertNull(projected.appraisedValue);assertNull(projected.expertOpinionAttachmentIds);assertNull(projected.images)
        assertEquals("陶器",projected.name)
    }
    @Test fun `confidential archive original requires the selected case stage file and current duty`()=fixture { entity->
        val fileId="2102704108634521999"
        FileDao.new(fileId) { name="征集阶段原件.pdf";path="/files/acquisition-stage.pdf";hash="stage";size=100;ext="pdf" }
        val archiveId=archives.seal("museum",CollectionConst.BIZ_ACQUISITION,entity.id.value,"PROPOSAL",mapOf("test" to true),fileId)
        assertDoesNotThrow { service.requireArchivedOriginal(entity.id.value,archiveId,fileId) }
        assertThrows(BadRequestException::class.java) { service.requireArchivedOriginal(entity.id.value,archiveId,"2102704108634521998") }
        assertThrows(BadRequestException::class.java) { service.requireArchivedOriginal(entity.id.value,"2102704108634521997",fileId) }
        assertThrows(BadRequestException::class.java) { service.requireArchivedOriginal("other",archiveId,fileId) }
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteWhere {io.hizhengfu.cloud.sys.system.dao.RolePermissions.permissionId eq CollectionConst.ACQUIRE}
        actorRole("acquirer","GENERAL_READER",setOf(CollectionConst.SELECT))
        login(CollectionConst.SELECT)
        assertThrows(BadRequestException::class.java) { service.requireArchivedOriginal(entity.id.value,archiveId,fileId) }
    }
    private fun prepareDonation(entity:AcquisitionDao) {
        entity.source=EnumSource.DONATE
        service.saveDossier(entity.id.value,investigation)
        service.complete(entity.id.value,completion().copy(donation=DonationAgreementTermsVo("物","来源","交接","转移","义务","特殊","争议")))
        entity.collectionId="registered-collection";entity.flowStatus=EnumFlowStatus.PASS
    }
    private fun recognition()=DonationRecognitionVo("recognition-0001",DonationRecognitionKind.SPIRITUAL,"合成捐赠人",LocalDate.now(),"颁发感谢证书","proof")
    @Test fun `recognition after registration is append only and retry does not duplicate`()=fixture { entity ->
        prepareDonation(entity)
        val subject=service.case(entity.id.value)!!.completion
        val records=DonationRecognitionService(scope,archives)
        val first=records.record(entity.id.value,recognition())
        assertEquals(first,records.record(entity.id.value,recognition()))
        assertThrows(BadRequestException::class.java) { records.record(entity.id.value,recognition().copy(description="覆盖原记录")) }
        val amended=recognition().copy(requestId="recognition-0002",description="感谢证书编号补正",correctsArchiveId=first,correctionReason="补齐证书编号")
        val second=records.record(entity.id.value,amended)
        assertNotEquals(first,second)
        assertThrows(BadRequestException::class.java) { records.record(entity.id.value,amended.copy(requestId="recognition-0003")) }
        assertEquals(2,records.list(entity.id.value).size)
        assertEquals(subject,service.case(entity.id.value)!!.completion)
    }
    @Test fun `recognition rejects invalid date and material facts without writing archives`()=fixture { entity ->
        prepareDonation(entity);val records=DonationRecognitionService(scope,archives);val before=CollectionPermanentArchiveDao.count()
        val invalid=listOf(recognition().copy(occurredOn=LocalDate.now().plusDays(1)),recognition().copy(occurredOn=LocalDate.of(2026,5,1)),recognition().copy(evidenceFileIds=""),recognition().copy(kind=DonationRecognitionKind.MATERIAL),recognition().copy(amountCents=1),recognition().copy(correctsArchiveId="another-case",correctionReason="错误引用"),recognition().copy(requestId="a".repeat(41)))
        invalid.forEach { vo -> assertThrows(BadRequestException::class.java) { records.record(entity.id.value,vo) } }
        assertEquals(before,CollectionPermanentArchiveDao.count())
        val material=recognition().copy(kind=DonationRecognitionKind.MATERIAL,necessity="实际必要性依据",materialDescription="纪念品一件",approvalFileIds="proof",receiptFileIds="proof")
        assertDoesNotThrow { records.record(entity.id.value,material) }
    }
    @Test fun `recognition current permission is checked for reads writes and idempotent retry`()=fixture { entity ->
        prepareDonation(entity);val records=DonationRecognitionService(scope,archives);records.record(entity.id.value,recognition())
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteWhere {io.hizhengfu.cloud.sys.system.dao.RolePermissions.permissionId eq CollectionConst.ACQUIRE}
        assertThrows(BadRequestException::class.java) { records.list(entity.id.value) }
        assertThrows(BadRequestException::class.java) { records.record(entity.id.value,recognition()) }
    }
    @Test fun `recognition does not accept purchases or uncompleted donation`()=fixture { entity ->
        val records=DonationRecognitionService(scope,archives)
        assertThrows(BadRequestException::class.java) { records.record(entity.id.value,recognition()) }
        entity.source=EnumSource.DONATE
        assertThrows(BadRequestException::class.java) { records.record(entity.id.value,recognition()) }
    }

}
