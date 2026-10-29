package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.UserDao
import org.jetbrains.exposed.v1.jdbc.Database
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

class InstitutionItemHandoverPersistenceTest {
    private val scope=CollectionScopeResolver("multi","")
    private val binder=mock(BusinessAttachmentBinder::class.java).also {`when`(it.resolveSingle(anyString())).thenAnswer {call->call.getArgument<String>(0)}}
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val cases=InstitutionSuccessionCaseService(scope,users,archives)
    private val items=InstitutionItemHandoverService(cases,users)
    private val investigations=InstitutionItemInvestigationService(cases,users)
    private val auxiliary=CollectionAuxiliaryService(scope,binder,archives)
    private fun login(id:String="operator",permission:String=CollectionConst.STORAGE,org:String="museum") {
        val u=SecurityUser(id=id,name=id,username=id,password="",groupId=org,groupIds=setOf(org),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(permission)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(u,null,u.authorities)
    }
    @BeforeEach fun setup() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        login()
    }
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun fixture(block:(Database)->Unit) {
        val file=java.nio.file.Files.createTempFile("institution-item-",".sqlite")
        val db=Database.connect("jdbc:sqlite:$file",driver="org.sqlite.JDBC")
        try {
            transaction(db) {
                SchemaUtils.create(CollectionOrgs,Collections,Warehouses,CollectionAuxiliaries,Acquisitions,AccessionReceipts,AcquisitionComplianceCases,DesignatedReceptions,DesignatedReceptionRecords,
                    CollectionTransfers,CollectionRetainedLedgers,Deregistrations,InstitutionSuccessionCases,InstitutionSuccessionItems,InstitutionSuccessionRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,StorageMovements)
                CollectionOrgDao.new("museum") {name="本馆";code="museum";ownership=EnumOwnership.STATE}
                CollectionOrgDao.new("other") {name="其他馆";code="other";ownership=EnumOwnership.STATE}
                originalReaderSchema()
                originalReader("ledger",CollectionConst.LEDGER_CORRECT)
                actorRole("ledger","FILE_ledger",setOf(CollectionConst.SELECT))
                originalReader("operator",CollectionConst.STORAGE)
                listOf("keeper","representative").forEach { originalReader(it,CollectionConst.STORAGE) }
                actorRole("operator","FILE_operator",setOf(CollectionConst.SELECT))
                listOf("basis","photo","sign").forEach {key->FileDao.new(key) {name="$key.pdf";path="/files/$key.pdf";hash=key;size=100;ext="pdf"}}
            }
            block(db)
        } finally {java.nio.file.Files.deleteIfExists(file)}
    }
    private fun createCase(doc:String="机构决定"):String=cases.create(InstitutionSuccessionCaseVo("museum",EnumInstitutionSuccession.MERGER,"本馆","本馆登记标识",doc,LocalDate.of(2026,8,1),LocalDate.of(2026,9,1),"basis","实际非营利法规、章程及合并方案","basis","basis","keeper","representative","sign"))
    private fun capture(id:String)=cases.capture(id,InstitutionScopeCaptureVo(cases.detail(id)["scanDigest"] as String,LocalDateTime.now(),"operator","已逐库及外部保管清点","basis","sign"))
    private fun addAux(quantity:Int=2):String {
        login("ledger",CollectionConst.LEDGER_CORRECT)
        val id=auxiliary.register(CollectionAuxiliaryVo(orgId="museum",recordNo="A-1",kind=EnumAuxiliaryKind.MODEL,name="教学模型",quantity=quantity,unit="件",location="原辅助库",registeredOn=LocalDate.now(),reason="实际非藏品登记依据",evidenceFileIds="basis"))
        login();return "AUXILIARY:$id"
    }
    private fun finding(id:String,quantity:Int=2):String {
        cases.addUnrecorded(id,InstitutionUnrecordedVo("U-1","现场对象",quantity,"件","暂存区","实际保管人员",LocalDateTime.now(),"operator","现场特征","photo","sign"))
        return InstitutionSuccessionCaseEvidence.items(id).single {it.sourceKind==EnumSuccessionSource.UNRECORDED}.itemKey
    }
    private fun pieces()=(1..2).map {InstitutionHandoverPieceVo("piece-$it","实际逐件特征 $it","状况完好","photo")}
    private fun proposal(key:String)=InstitutionItemProposalVo(key,LocalDateTime.now(),"operator",EnumInstitutionItemRoute.NON_COLLECTION_SUCCESSION,"依据专业原件确认属于教学模型等其他物品，不属于文物或藏品","basis",nonCollectionConfirmed=true,propertyBasisFileIds="basis",ownership=EnumOwnership.STATE,ownerName="本馆",ownerIdentity="本馆登记标识",recipientKind=EnumInstitutionRecipient.PUBLIC_BENEFIT_ORGANIZATION,recipientName="实际公益接收机构",recipientIdentity="recipient-code",recipientQualificationFileIds="basis",dispositionBasisFileIds="basis",thirdPartyRightsResolved=true,thirdPartyRightsFileIds="basis",publicBenefitPurpose="继续公益教育展示使用",noPrivateDistribution=true,pieces=pieces())
    private fun approve(id:String,p:String) {
        login("keeper");items.approve(id,InstitutionItemApprovalVo(p,LocalDateTime.now(),"keeper","本人核对实际分类、清册及处理依据","sign"))
        login("representative");items.approve(id,InstitutionItemApprovalVo(p,LocalDateTime.now(),"representative","本人核实原件并依法确认","sign"),true)
        login()
    }
    private fun handover(p:String,values:List<InstitutionHandoverPieceVo> = pieces())=InstitutionItemHandoverVo(p,LocalDateTime.now(),"operator","实际公益接收机构","recipient-code","真实接收地点",setOf("本馆实际点交人"),setOf("接收方实际点收人"),values,true,true,signedReceiptFileIds="sign",archiveHandoverFileIds="basis")
    private fun resolve(id:String,key:String,business:String,kind:EnumSuccessionResolution=EnumSuccessionResolution.OTHER_ITEM_HANDOVER)=cases.resolve(id,InstitutionItemResolutionVo(key,kind,business,LocalDateTime.now(),"operator","按真实原件核实处理结果","sign"))

    @Test fun `real auxiliary handover needs both personal confirmations and resolves only after all actual batches`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();capture(id);val v=proposal(key);val p=items.propose(id,v)
        assertEquals(p,items.propose(id,v));assertThrows(BadRequestException::class.java){items.handOver(id,handover(p))}
        login("representative");assertThrows(BadRequestException::class.java){items.approve(id,InstitutionItemApprovalVo(p,LocalDateTime.now(),"representative","尚无保管核对","sign"),true)}
        approve(id,p)
        val first=handover(p,pieces().take(1));val h1=items.handOver(id,first);assertEquals(h1,items.handOver(id,first))
        assertThrows(BadRequestException::class.java){resolve(id,key,h1)}
        assertEquals(1,cases.detail(id)["pending"])
        val h2=items.handOver(id,handover(p,pieces().drop(1)))
        assertThrows(BadRequestException::class.java){resolve(id,key,h1)}
        resolve(id,key,h2);assertEquals(0,cases.detail(id)["pending"])
        assertThrows(BadRequestException::class.java){items.handOver(id,handover(p))}
        assertEquals(0,CollectionDao.all().count());assertEquals(0,StorageMovementDao.all().count())
        assertEquals("原辅助库",CollectionAuxiliaryDao.all().single().content.location)
        val visible=auxiliary.list("museum").single()
        assertEquals("原辅助库",visible.location);assertEquals("HANDED_OVER",visible.institutionHandover?.get("state"));assertEquals(0,visible.institutionHandover?.get("remainingQuantity"))
        assertFalse(CollectionPermanentArchiveDao.all().first {it.bizType=="COLLECTION_AUXILIARY"}.snapshot.contains("institutionHandover"))
    }}
    @Test fun `classification cannot use an active formal collection or omit property rights and real identities`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase()
        CollectionDao.new("formal") {orgId="museum";name="文物";totalRegNo="0001";nature=EnumCollectionNature.RELIC;lifeStatus=EnumCollectionStatus.IN_STORE}
        capture(id)
        for(v in listOf(proposal("COLLECTION:formal"),proposal(key).copy(nonCollectionConfirmed=false),proposal(key).copy(noPrivateDistribution=false),proposal(key).copy(thirdPartyRightsResolved=false),proposal(key).copy(recipientIdentity=""),proposal(key).copy(pieces=pieces().take(1)),proposal(key).copy(proposedAt=LocalDateTime.now().plusDays(1))))assertThrows(BadRequestException::class.java){items.propose(id,v)}
        val p=items.propose(id,proposal(key));approve(id,p)
        assertThrows(BadRequestException::class.java){items.handOver(id,handover(p).copy(recipientIdentity="another"))}
        assertThrows(BadRequestException::class.java){items.handOver(id,handover(p).copy(operatorUserId="keeper"))}
        UserDao["operator"].groupId="other";login(org="other");assertThrows(BadRequestException::class.java){items.handOver(id,handover(p))}
    }}
    @Test fun `revised proposal invalidates previous approvals and actual handover freezes the authorization`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();capture(id);val v=proposal(key);val old=items.propose(id,v);approve(id,old)
        assertThrows(BadRequestException::class.java){items.propose(id,v.copy(identificationConclusion="替换分类"))}
        val updated=items.propose(id,v.copy(proposedAt=LocalDateTime.now(),previousProposalId=old,revisionReason="据原件补充",revisionFileIds="basis",identificationConclusion="追加实际核对说明"))
        assertThrows(BadRequestException::class.java){items.handOver(id,handover(old))}
        assertThrows(BadRequestException::class.java){items.handOver(id,handover(updated))}
        approve(id,updated);items.handOver(id,handover(updated))
        login("keeper");assertThrows(BadRequestException::class.java){items.approve(id,InstitutionItemApprovalVo(updated,LocalDateTime.now(),"keeper","事后改换核对","sign"))}
        login();assertThrows(BadRequestException::class.java){items.propose(id,v.copy(previousProposalId=updated,revisionReason="更改接收单位",revisionFileIds="basis"))}
    }}
    @Test fun `signed identity link resolves a duplicate finding only through the actual referenced completion and reopens on new issue`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();val found=finding(id);capture(id)
        val link=items.propose(id,InstitutionItemProposalVo(found,LocalDateTime.now(),"operator",EnumInstitutionItemRoute.RECORD_LINK,"逐件原照片及特征证明为同一对象的未关联记录","basis",linkedItemKey=key))
        approve(id,link);assertThrows(BadRequestException::class.java){resolve(id,found,link,EnumSuccessionResolution.LINKED_ITEM)}
        val p=items.propose(id,proposal(key));approve(id,p);val h=items.handOver(id,handover(p));resolve(id,key,h)
        resolve(id,found,link,EnumSuccessionResolution.LINKED_ITEM);assertEquals(0,cases.detail(id)["pending"])
        cases.followUp(id,InstitutionItemFollowUpVo(key,LocalDateTime.now(),"operator","keeper","发现新的未结事项","实际核查中",LocalDateTime.now().plusDays(1),"basis"))
        assertEquals(2,cases.detail(id)["pending"])
        assertEquals(0,CollectionDao.all().count())
    }}
    @Test fun `a physical finding cannot disappear through a cancelled unreceived acquisition or self linking`()=fixture {db->transaction(db) {
        val id=createCase();val found=finding(id)
        AcquisitionDao.new("cancelled") {orgId="museum";name="未取得的申请";quantity=2;flowStatus=io.hizhengfu.cloud.common.domain.EnumFlowStatus.CANCEL}
        capture(id)
        val link=InstitutionItemProposalVo(found,LocalDateTime.now(),"operator",EnumInstitutionItemRoute.RECORD_LINK,"实际身份核对","basis",linkedItemKey=found)
        assertThrows(BadRequestException::class.java){items.propose(id,link)}
        val p=items.propose(id,link.copy(linkedItemKey="ACQUISITION:cancelled"));approve(id,p)
        resolve(id,"ACQUISITION:cancelled","cancelled",EnumSuccessionResolution.ACQUISITION_NOT_COMPLETED)
        assertThrows(BadRequestException::class.java){resolve(id,found,p,EnumSuccessionResolution.LINKED_ITEM)}
        assertEquals(1,cases.detail(id)["pending"])
    }}
    @Test fun `return of a non collection deposit needs the actual custody relationship and exact lawful owner`()=fixture {db->transaction(db) {
        val id=createCase();val key=finding(id);capture(id)
        val v=proposal(key).copy(route=EnumInstitutionItemRoute.RETURN_NON_COLLECTION_CUSTODY,ownership=EnumOwnership.NON_STATE,ownerName="实际寄存人",ownerIdentity="owner-code",recipientKind=EnumInstitutionRecipient.LAWFUL_OWNER,recipientName="实际寄存人",recipientIdentity="owner-code",publicBenefitPurpose=null,noPrivateDistribution=null)
        assertThrows(BadRequestException::class.java){items.propose(id,v)}
        val valid=v.copy(custodyAgreementFileIds="basis",returnAuthorizationFileIds="sign")
        assertThrows(BadRequestException::class.java){items.propose(id,valid.copy(recipientIdentity="unrelated"))}
        val p=items.propose(id,valid);approve(id,p)
        val h=items.handOver(id,handover(p).copy(recipientName="实际寄存人",recipientIdentity="owner-code"));resolve(id,key,h)
        assertEquals(0,cases.detail(id)["pending"]);assertEquals(0,CollectionDao.all().count())
    }}
    @Test fun `actual discrepancies are preserved without pretending handover completion`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();capture(id);val p=items.propose(id,proposal(key));approve(id,p)
        val changed=handover(p).copy(conditionMatches=false)
        assertThrows(BadRequestException::class.java){items.handOver(id,changed)}
        val h=items.handOver(id,changed.copy(discrepancy="点收发现破损",protectiveMeasures="已隔离并安排核查"))
        assertThrows(BadRequestException::class.java){resolve(id,key,h)};assertEquals(1,cases.detail(id)["pending"])
        assertTrue(InstitutionItemHandoverEvidence.request<InstitutionItemHandoverVo>(InstitutionSuccessionRecordDao[h]).discrepancy!!.contains("破损"))
    }}
    @Test fun `replaced auxiliary originals and another case cannot authorize duplicate disposition`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();capture(id);val p=items.propose(id,proposal(key));approve(id,p)
        val aux=CollectionAuxiliaryDao.all().single();val original=aux.content;aux.content=original.copy(location="改换位置")
        assertThrows(BadRequestException::class.java){items.handOver(id,handover(p))};aux.content=original
        items.handOver(id,handover(p,pieces().take(1)))
        val another=createCase("另一机构决定");capture(another);val otherPlan=items.propose(another,proposal(key));approve(another,otherPlan)
        assertThrows(BadRequestException::class.java){items.handOver(another,handover(otherPlan))}
    }}
    private fun investigation(id:String,key:String):InstitutionItemInvestigationVo {
        val b=InstitutionItemInvestigationEvidence.basis(InstitutionSuccessionCaseDao[id],key)
        return InstitutionItemInvestigationVo(b.batches.last().id.value,b.digest,LocalDateTime.now(),"operator","双方核实运输振动造成破损，保留原不一致事实","已采取支护及保护处置，逐件复核真实现状","basis","sign",
            b.batches.flatMap {InstitutionItemHandoverEvidence.request<InstitutionItemHandoverVo>(it).pieces}.map {InstitutionItemInvestigationPieceVo(it.pieceNo,"本次实际核实特征","破损已支护，原痕迹保留",true,true,true,true,b.plan.recipientName,b.plan.recipientIdentity,"实际接收保管区","接收机构实际保管人员","photo")},
            true,"basis","按实际物品性质和事件核实报告义务及实际办理依据","basis",false,false,true,"basis","basis")
    }
    private fun confirmInvestigation(id:String,r:String) {
        login("keeper");investigations.confirm(id,InstitutionItemInvestigationConfirmationVo(r,LocalDateTime.now(),"keeper","本人核对双方说明、调查、逐件状况及后续保管","sign"))
        login("representative");investigations.confirm(id,InstitutionItemInvestigationConfirmationVo(r,LocalDateTime.now(),"representative","本人复核同一调查与处理结果","sign"),true);login()
    }
    private fun discrepancy(id:String,key:String):String {
        val p=items.propose(id,proposal(key));approve(id,p)
        return items.handOver(id,handover(p).copy(conditionMatches=false,discrepancy="实际点收破损",protectiveMeasures="已保护并通知核查"))
    }
    @Test fun `actual investigation and two personal confirmations close differences while preserving original batch and ledger`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();capture(id);val h=discrepancy(id,key)
        val original=InstitutionSuccessionRecordDao[h].content;val v=investigation(id,key);val r=investigations.investigate(id,v)
        assertEquals(r,investigations.investigate(id,v));assertThrows(BadRequestException::class.java){resolve(id,key,h)}
        login("representative");assertThrows(BadRequestException::class.java){investigations.confirm(id,InstitutionItemInvestigationConfirmationVo(r,LocalDateTime.now(),"representative","尚无保管确认","sign"),true)}
        confirmInvestigation(id,r);resolve(id,key,h);assertEquals(0,cases.detail(id)["pending"])
        assertEquals(original,InstitutionSuccessionRecordDao[h].content)
        val a=auxiliary.list("museum").single();assertEquals("原辅助库",a.location)
        assertEquals(true,a.institutionHandover?.get("hadDiscrepancy"));assertEquals("CONFIRMED",a.institutionHandover?.get("investigationState"))
        assertEquals(0,CollectionDao.all().count());assertEquals(0,StorageMovementDao.all().count())
    }}
    @Test fun `incomplete identity custody statements and reports can be recorded but require followup and cannot be confirmed`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();capture(id);val h=discrepancy(id,key);val complete=investigation(id,key)
        val partial=complete.copy(allIssuesResolved=false,senderStatementFileIds=null,reportingRequired=true,reportingCompleted=false,
            pieces=complete.pieces.map {it.copy(identityConfirmed=false,present=false,safeCustody=false,recipientIdentity=null,actualLocation=null,custodianDescription=null,photoFileIds=null)})
        assertThrows(BadRequestException::class.java){investigations.investigate(id,partial)}
        val r=investigations.investigate(id,partial.copy(remainingIssues="身份及实物去向仍待核，正在索取交出方说明并履行报告",responsibleUserId="keeper",nextCheckAt=LocalDateTime.now().plusDays(1)))
        login("keeper");assertThrows(BadRequestException::class.java){investigations.confirm(id,InstitutionItemInvestigationConfirmationVo(r,LocalDateTime.now(),"keeper","不能将未知事实改为一致","sign"))};login()
        assertThrows(BadRequestException::class.java){investigations.investigate(id,complete)}
        val revised=complete.copy(investigatedAt=LocalDateTime.now(),previousInvestigationId=r,revisionReason="补齐实际查验和报告",revisionFileIds="basis",reportingRequired=true,reportingCompleted=true,reportFileIds="basis")
        val next=investigations.investigate(id,revised);confirmInvestigation(id,next);resolve(id,key,h)
        assertEquals(0,cases.detail(id)["pending"]);assertFalse(InstitutionItemHandoverEvidence.request<InstitutionItemInvestigationVo>(InstitutionSuccessionRecordDao[r]).pieces.first().present)
    }}
    @Test fun `new followup invalidates investigation and linked completion until latest facts are investigated and confirmed again`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();val found=finding(id);capture(id);val h=discrepancy(id,key)
        val link=items.propose(id,InstitutionItemProposalVo(found,LocalDateTime.now(),"operator",EnumInstitutionItemRoute.RECORD_LINK,"同一对象身份核实","basis",linkedItemKey=key));approve(id,link)
        val v=investigation(id,key);val r=investigations.investigate(id,v);confirmInvestigation(id,r);resolve(id,key,h);resolve(id,found,link,EnumSuccessionResolution.LINKED_ITEM)
        assertEquals(0,cases.detail(id)["pending"])
        cases.followUp(id,InstitutionItemFollowUpVo(key,LocalDateTime.now(),"operator","keeper","接收保管出现新问题","已保护并核查",LocalDateTime.now().plusDays(1),"basis"))
        assertEquals(2,cases.detail(id)["pending"]);assertThrows(BadRequestException::class.java){resolve(id,key,h)}
        assertThrows(BadRequestException::class.java){investigations.investigate(id,v)}
        val next=investigations.investigate(id,investigation(id,key).copy(previousInvestigationId=r,revisionReason="核查新增问题",revisionFileIds="basis"))
        confirmInvestigation(id,next);resolve(id,key,h);resolve(id,found,link,EnumSuccessionResolution.LINKED_ITEM);assertEquals(0,cases.detail(id)["pending"])
    }}
    @Test fun `additional actual batch invalidates old investigation and partial delivery never becomes complete`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();capture(id);val p=items.propose(id,proposal(key));approve(id,p)
        val h=items.handOver(id,handover(p,pieces().take(1)).copy(identityMatches=false,discrepancy="首件身份待核",protectiveMeasures="隔离核查"))
        val first=investigation(id,key);val r=investigations.investigate(id,first);confirmInvestigation(id,r)
        assertThrows(BadRequestException::class.java){resolve(id,key,h)}
        val last=items.handOver(id,handover(p,pieces().drop(1)));assertThrows(BadRequestException::class.java){resolve(id,key,last)}
        assertThrows(BadRequestException::class.java){investigations.investigate(id,first.copy(previousInvestigationId=r,revisionReason="仍用旧批次",revisionFileIds="basis"))}
        val complete=investigation(id,key).copy(previousInvestigationId=r,revisionReason="追加实际第二批核查",revisionFileIds="basis")
        assertThrows(BadRequestException::class.java){investigations.investigate(id,complete.copy(pieces=complete.pieces.take(1)))}
        assertThrows(BadRequestException::class.java){investigations.investigate(id,complete.copy(pieces=listOf(complete.pieces.first(),complete.pieces.first())))}
        val next=investigations.investigate(id,complete);confirmInvestigation(id,next);resolve(id,key,last);assertEquals(0,cases.detail(id)["pending"])
    }}
    @Test fun `investigation cannot substitute a different recipient relic missing report or impersonated confirmation`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();capture(id);discrepancy(id,key);val v=investigation(id,key)
        assertThrows(BadRequestException::class.java){investigations.investigate(id,v.copy(operatorUserId="someone"))}
        assertThrows(BadRequestException::class.java){investigations.investigate(id,v.copy(investigatedAt=LocalDateTime.now().plusDays(1)))}
        assertThrows(BadRequestException::class.java){investigations.investigate(id,v.copy(investigatedAt=LocalDateTime.of(2026,8,1,0,0)))}
        UserDao["operator"].groupId="other";login(org="other");assertThrows(BadRequestException::class.java){investigations.investigate(id,v)};UserDao["operator"].groupId="museum";login()
        val another=createCase("不同案卷");assertThrows(BadRequestException::class.java){investigations.investigate(another,v)}
        for(pending in listOf(v.copy(reportingRequired=true,reportingCompleted=true),v.copy(pieces=v.pieces.map {it.copy(nonCollectionConfirmed=false)}),v.copy(pieces=v.pieces.map {it.copy(recipientIdentity="changed")}))) {
            assertThrows(BadRequestException::class.java){investigations.investigate(id,pending)}
        }
        val r=investigations.investigate(id,v)
        assertThrows(BadRequestException::class.java){investigations.confirm(id,InstitutionItemInvestigationConfirmationVo(r,LocalDateTime.now(),"keeper","冒名确认","sign"))}
        assertThrows(BadRequestException::class.java){investigations.confirm(id,InstitutionItemInvestigationConfirmationVo(r,LocalDateTime.now(),"operator","非指定人","sign"))}
        login("keeper");doThrow(BadRequestException("无保管负责人职责")).`when`(users).requireRole("keeper","museum",CollectionConst.ROLE_STORAGE_HEAD)
        assertThrows(BadRequestException::class.java){investigations.confirm(id,InstitutionItemInvestigationConfirmationVo(r,LocalDateTime.now(),"keeper","无实际职责","sign"))}
    }}
    @Test fun `new investigation and replaced keeper original invalidate prior authorization and completed proof`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();capture(id);val h=discrepancy(id,key);val r=investigations.investigate(id,investigation(id,key));confirmInvestigation(id,r);resolve(id,key,h)
        login("keeper");val v=InstitutionItemInvestigationConfirmationVo(r,LocalDateTime.now(),"keeper","补充本人真实核对意见","sign")
        val k=investigations.confirm(id,v);assertEquals(k,investigations.confirm(id,v));login()
        assertEquals(1,cases.detail(id)["pending"]);assertThrows(BadRequestException::class.java){resolve(id,key,h)}
        login("representative");investigations.confirm(id,InstitutionItemInvestigationConfirmationVo(r,LocalDateTime.now(),"representative","复核新的保管确认","sign"),true);login();resolve(id,key,h)
        val next=investigations.investigate(id,investigation(id,key).copy(previousInvestigationId=r,revisionReason="追加专业复查原件",revisionFileIds="basis"))
        assertEquals(1,cases.detail(id)["pending"])
        login("keeper");assertThrows(BadRequestException::class.java){investigations.confirm(id,v)};login()
        confirmInvestigation(id,next);resolve(id,key,h);assertEquals(0,cases.detail(id)["pending"])
    }}
    @Test fun `normal initial handover cannot reuse old completion after a subsequently discovered issue`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();capture(id);val p=items.propose(id,proposal(key));approve(id,p);val h=items.handOver(id,handover(p));resolve(id,key,h)
        cases.followUp(id,InstitutionItemFollowUpVo(key,LocalDateTime.now(),"operator","keeper","发现后续保存状况问题","已保护并安排核查",LocalDateTime.now().plusDays(1),"basis"))
        assertThrows(BadRequestException::class.java){resolve(id,key,h)}
        val r=investigations.investigate(id,investigation(id,key));confirmInvestigation(id,r);resolve(id,key,h);assertEquals(0,cases.detail(id)["pending"])
    }}
    @Test fun `missing investigation file rolls back and committed originals remain verifiable across transactions`()=fixture {db->
        val ids=transaction(db){val key=addAux();val id=createCase();capture(id);val h=discrepancy(id,key);listOf(id,key,h)}
        val (id,key,h)=ids
        assertThrows(BadRequestException::class.java){transaction(db){investigations.investigate(id,investigation(id,key).copy(investigationFileIds="missing"))}}
        val r=transaction(db){assertNull(InstitutionItemInvestigationEvidence.latest(InstitutionSuccessionCaseDao[id],key));investigations.investigate(id,investigation(id,key))}
        transaction(db){confirmInvestigation(id,r);resolve(id,key,h)}
        transaction(db){assertEquals(0,cases.detail(id)["pending"]);val row=InstitutionSuccessionRecordDao[r];val original=row.content;row.content=original.replace("破损已支护","未核实替换")
            assertThrows(BadRequestException::class.java){cases.detail(id)};row.content=original}
    }
    @Test fun `committed originals remain readable and missing actual receipt files roll back only the attempted batch`()=fixture {db->
        val ids=transaction(db){val key=addAux();val id=createCase();capture(id);val p=items.propose(id,proposal(key));approve(id,p);listOf(id,key,p)}
        val (id,key,p)=ids
        assertThrows(BadRequestException::class.java){transaction(db){items.handOver(id,handover(p).copy(signedReceiptFileIds="missing"))}}
        transaction(db){assertTrue(InstitutionItemHandoverEvidence.handovers(InstitutionSuccessionCaseDao[id],key).isEmpty());val h=items.handOver(id,handover(p));resolve(id,key,h)}
        transaction(db){assertEquals(0,cases.detail(id)["pending"]);assertNotNull(cases.detail(id)["records"])}
    }
    @Test fun `auxiliary projection rejects a handover case outside its source museum`()=fixture {db->transaction(db) {
        val key=addAux();val id=createCase();capture(id);val p=items.propose(id,proposal(key));approve(id,p)
        items.handOver(id,handover(p))
        assertEquals(2,auxiliary.list("museum").single().institutionHandover!!["handedQuantity"])
        InstitutionSuccessionCaseDao[id].orgId="other"
        val error=assertThrows(BadRequestException::class.java){auxiliary.list("museum")}
        assertEquals("辅助账交接案卷不属于当前收藏单位",error.message)
    }}

}
