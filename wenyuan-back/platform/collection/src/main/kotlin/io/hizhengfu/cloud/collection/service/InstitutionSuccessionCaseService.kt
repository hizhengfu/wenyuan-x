package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.InstitutionSuccessionCaseEvidence as E
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Component
@Transactional
class InstitutionSuccessionCaseService(private val scope:CollectionScopeResolver,private val users:CollectionResponsibleUserService,private val archives:CollectionPermanentArchiveService) {
    private val authorization=CollectionInstitutionOperationAccess(scope,"机构终止合并统筹清册",
        setOf(CollectionConst.STORAGE,CollectionConst.CROSS_ORG,CollectionConst.LEDGER_CORRECT),setOf(CollectionConst.STORAGE))
    private fun access(org:String,write:Boolean=false)=authorization.require(org,write)
    private fun finish(c:InstitutionSuccessionCaseDao,org:String,result:String):String {
        TransactionManager.current().entityCache.flush()
        c.refresh()
        if(c.orgId!=org || c.content.orgId!=org)E.fail("统筹案卷所属收藏单位已变化，请重新核对")
        access(org,true);return result
    }
    internal fun actor(org:String,id:String) {
        if(SecurityUtils.getUserId()!=id)E.fail("须由实际办理人员本人登记")
        users.requireMuseumMember(id,org)
    }
    private fun text(vararg s:String) {if(s.any(String::isBlank))E.fail("请填写实际人员、范围、依据及签署原件")}
    private fun actual(at:LocalDateTime) {if(at>LocalDateTime.now())E.fail("实际办理时间不能填写未来")}
    internal fun get(id:String,write:Boolean=false,expectedOrgId:String?=null):InstitutionSuccessionCaseDao {
        if(write)TransactionManager.current().entityCache.flush()
        val c=(if(write)InstitutionSuccessionCaseDao.find {InstitutionSuccessionCases.id eq id}.forUpdate().singleOrNull() else InstitutionSuccessionCaseDao.findById(id)) ?: E.fail("统筹案卷不存在")
        if(write)c.refresh()
        if(expectedOrgId!=null && expectedOrgId!=c.orgId)E.fail("原表单收藏单位与当前案卷不一致，请重新读取后办理")
        access(c.orgId,write);E.verify(c);return c
    }
    private inline fun <reified T> originalRequest(r:InstitutionSuccessionRecordDao):T =
        JsonUtils.fromJson((E.node(r.content).get("request") ?: E.node(r.content)).toString(),T::class.java)
            ?: E.fail("统筹办理原件内容无法核对")
    private fun originalRecordFiles(c:InstitutionSuccessionCaseDao,r:InstitutionSuccessionRecordDao):String = when(r.kind) {
        EnumSuccessionCaseRecord.SCOPE -> originalRequest<InstitutionScopeCaptureVo>(r).let {"${it.inventoryFileIds},${it.signedFileIds}"}
        EnumSuccessionCaseRecord.FOLLOW_UP -> originalRequest<InstitutionItemFollowUpVo>(r).evidenceFileIds
        EnumSuccessionCaseRecord.RESOLUTION -> originalRequest<InstitutionItemResolutionVo>(r).signedFileIds
        EnumSuccessionCaseRecord.KEEPER_REVIEW,EnumSuccessionCaseRecord.REPRESENTATIVE_REVIEW -> {
            val v=originalRequest<InstitutionScopeReviewVo>(r)
            val own="${v.archiveHandoverFileIds},${v.signedFileIds}"
            if(r.kind==EnumSuccessionCaseRecord.KEEPER_REVIEW) own else {
                val keeperId=E.node(r.content).get("keeperRecordId")?.asText()
                val keeper=E.records(c.id.value).singleOrNull {it.id.value==keeperId && it.kind==EnumSuccessionCaseRecord.KEEPER_REVIEW}
                    ?: E.fail("依法复核的保管核对来源不属于本案")
                val source=E.archive(c.orgId,E.BIZ,keeper.id.value,keeper.kind.name)
                archives.requireFileReferences(source,originalRecordFiles(c,keeper))
                // 继承授权允许复用档案移交原件，封存集合仍是本次复核声明的文件。
                own
            }
        }
        EnumSuccessionCaseRecord.ITEM_PROPOSAL -> originalRequest<InstitutionItemProposalVo>(r).let {v ->
            (listOfNotNull(v.identificationFileIds,v.revisionFileIds,v.propertyBasisFileIds,v.recipientQualificationFileIds,
                v.dispositionBasisFileIds,v.thirdPartyRightsFileIds,v.custodyAgreementFileIds,v.returnAuthorizationFileIds)+
                v.pieces.map {it.photoFileIds}).joinToString(",")
        }
        EnumSuccessionCaseRecord.ITEM_KEEPER,EnumSuccessionCaseRecord.ITEM_AUTHORIZATION -> originalRequest<InstitutionItemApprovalVo>(r).signedFileIds
        EnumSuccessionCaseRecord.ITEM_HANDOVER -> originalRequest<InstitutionItemHandoverVo>(r).let {v ->
            (v.pieces.map {it.photoFileIds}+listOf(v.signedReceiptFileIds,v.archiveHandoverFileIds)).joinToString(",")
        }
        EnumSuccessionCaseRecord.ITEM_INVESTIGATION -> originalRequest<InstitutionItemInvestigationVo>(r).let {v ->
            (listOfNotNull(v.investigationFileIds,v.signedFileIds,v.senderStatementFileIds,v.receiverStatementFileIds,
                v.thirdPartyRightsFileIds,v.reportingBasisFileIds,v.reportFileIds,v.revisionFileIds)+
                v.pieces.mapNotNull {it.photoFileIds}).filter(String::isNotBlank).joinToString(",")
        }
        EnumSuccessionCaseRecord.ITEM_INVESTIGATION_KEEPER,EnumSuccessionCaseRecord.ITEM_INVESTIGATION_AUTHORIZATION ->
            originalRequest<InstitutionItemInvestigationConfirmationVo>(r).signedFileIds
    }
    /** 先核对当前案卷及冻结快照，再按阶段精确核对原件引用；来源 ID 不能跨案复用。 */
    fun requireArchivedOriginal(id:String,sourceId:String,fileId:String):Pair<String,String> {
        if(listOf(id,sourceId,fileId).any {it.isBlank() || it.length>512} || id.length>100 || sourceId.length>100)
            E.fail("统筹原件案卷、阶段或文件无效")
        val c=get(id)
        val phase:String
        val files:String
        val bizId:String
        when {
            sourceId==id -> {
                phase="CASE";bizId=id
                files=listOf(c.content.eventFileIds,c.content.legalBasisFileIds,c.content.liquidationOrMergerPlanFileIds,
                    c.content.representativeAuthorizationFileIds).joinToString(",")
            }
            else -> {
                val r=E.records(id).singleOrNull {it.id.value==sourceId}
                if(r!=null) {phase=r.kind.name;bizId=r.id.value;files=originalRecordFiles(c,r)}
                else {
                    val item=E.items(id).singleOrNull {it.id.value==sourceId || it.itemKey==sourceId}
                        ?: E.fail("统筹原件来源不属于当前案卷")
                    phase="ITEM";bizId=item.id.value
                    files=if(item.sourceKind==EnumSuccessionSource.UNRECORDED) {
                        val actual=E.node(item.baseline).get("actual") ?: E.fail("未登记对象原件内容缺失")
                        JsonUtils.fromJson(actual.toString(),InstitutionUnrecordedVo::class.java)?.let {"${it.photoFileIds},${it.signedFileIds}"}
                            ?: E.fail("未登记对象原件内容无法核对")
                    } else {
                        val captured=E.records(id).firstOrNull {r0 ->
                            r0.kind==EnumSuccessionCaseRecord.SCOPE && E.node(r0.content).get("items")?.any {entry ->
                                entry.get("key")?.asText()==item.itemKey
                            }==true
                        } ?: E.fail("清册对象首次清点原件缺失")
                        originalRequest<InstitutionScopeCaptureVo>(captured).inventoryFileIds
                    }
                }
            }
        }
        val archive=E.archive(c.orgId,E.BIZ,bizId,phase)
        archives.requireFileReferences(archive,files)
        val matches=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq archive.id.value}.filter {ref ->
            ref.fileId==fileId || JsonUtils.mapper.readTree(ref.metadata).get("path")?.asText()==fileId
        }
        if(matches.size!=1)E.fail("原件不属于指定统筹案卷、阶段或文件")
        return archive.id.value to matches.single().fileId
    }
    internal fun record(c:InstitutionSuccessionCaseDao,kind:EnumSuccessionCaseRecord,body:Any,files:String,keeperArchive:InstitutionSuccessionRecordDao?=null):String {
        val org=c.orgId
        val json=E.json(body)
        E.records(c.id.value).firstOrNull {it.kind==kind && it.content==json}?.let {return it.id.value}
        val r=InstitutionSuccessionRecordDao.new {orgId=c.orgId;caseId=c.id.value;this.kind=kind;actorUserId=SecurityUtils.getUserId()!!;recordedAt=LocalDateTime.now();content=json}
        if(keeperArchive==null) archives.seal(c.orgId,E.BIZ,r.id.value,kind.name,E.recordValue(r),files)
        else {
            if(kind!=EnumSuccessionCaseRecord.REPRESENTATIVE_REVIEW || keeperArchive.caseId!=c.id.value ||
                keeperArchive.kind!=EnumSuccessionCaseRecord.KEEPER_REVIEW)E.fail("复核原件继承来源不属于本案保管核对")
            val source=CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq E.BIZ) and
                    (CollectionPermanentArchives.bizId eq keeperArchive.id.value) and
                    (CollectionPermanentArchives.phase eq EnumSuccessionCaseRecord.KEEPER_REVIEW.name)
            }.singleOrNull() ?: E.fail("保管核对永久档案不存在或重复")
            if(source.orgId!=c.orgId)E.fail("保管核对永久档案所属馆不一致")
            archives.sealFromArchives(c.orgId,E.BIZ,r.id.value,kind.name,E.recordValue(r),files,mapOf(source.id.value to source.digest))
        }
        return finish(c,org,r.id.value)
    }
    fun create(value:InstitutionSuccessionCaseVo,expectedOrgId:String?=null):String {
        val org=value.orgId ?: authorization.currentOrg(true);if(expectedOrgId!=null && expectedOrgId!=org)E.fail("建案收藏单位与原表单不一致，请重新核对");access(org,true);actor(org,SecurityUtils.getUserId() ?: E.fail("请先登录"))
        val museum=CollectionOrgDao.findById(org) ?: E.fail("原收藏单位不存在")
        if(museum.orgStatus!=EnumOrgStatus.ENABLED || value.sourceMuseumName!=museum.name)E.fail("请核对实际原馆及有效办理范围，法人状态不等于系统启停状态")
        text(value.sourceRegistrationNo,value.eventDocumentNo,value.eventFileIds,value.nonprofitLegalBasis,value.legalBasisFileIds,value.liquidationOrMergerPlanFileIds,value.keeperUserId,value.authorizedRepresentativeUserId,value.representativeAuthorizationFileIds)
        if(value.decidedOn>LocalDate.now() || value.effectiveOn<value.decidedOn)E.fail("请核实实际机构决定及生效安排")
        if(value.keeperUserId==value.authorizedRepresentativeUserId)E.fail("保管核对与依法负责人员复核须分开")
        users.requireRole(value.keeperUserId,org,CollectionConst.ROLE_STORAGE_HEAD);users.requireMuseumMember(value.authorizedRepresentativeUserId,org)
        val content=value.copy(orgId=org)
        val key=InstitutionSuccessionSources.digest(listOf(org,value.sourceRegistrationNo.trim(),value.eventDocumentNo.trim()))
        CollectionOrgDao.find {CollectionOrgs.id eq org}.forUpdate().single()
        InstitutionSuccessionCaseDao.find {InstitutionSuccessionCases.sourceKey eq key}.singleOrNull()?.let {E.verify(it);if(it.content==content)return it.id.value;E.fail("同一机构决定已建案，不能重复建立或覆盖原依据")}
        val c=InstitutionSuccessionCaseDao.new {orgId=org;sourceKey=key;this.content=content}
        archives.seal(org,E.BIZ,c.id.value,"CASE",mapOf("orgId" to org,"sourceKey" to key,"content" to content),listOf(value.eventFileIds,value.legalBasisFileIds,value.liquidationOrMergerPlanFileIds,value.representativeAuthorizationFileIds).joinToString(","))
        return finish(c,org,c.id.value)
    }
    private fun scopeEntries(c:InstitutionSuccessionCaseDao):List<Map<String,Any?>> {
        val live=InstitutionSuccessionSources.scan(c).associateBy {it.key}
        val old=E.items(c.id.value).associateBy {it.itemKey}
        return (live.keys+old.keys).sorted().map {key->
            val current=live[key];val previous=old[key]
            mapOf("key" to key,"kind" to (current?.kind ?: previous!!.sourceKind),"id" to (current?.id ?: previous!!.sourceId),
                "currentPresent" to (current!=null || previous?.sourceKind==EnumSuccessionSource.UNRECORDED),
                "snapshot" to (current?.snapshot ?: E.node(previous!!.baseline)))
        }
    }
    private fun scopeDigest(entries:List<Map<String,Any?>>)=InstitutionSuccessionSources.digest(entries)
    fun capture(id:String,v:InstitutionScopeCaptureVo,expectedOrgId:String?=null):String {
        val c=get(id,true,expectedOrgId);val org=c.orgId;actor(c.orgId,v.operatorUserId);actual(v.checkedAt);text(v.coverageDescription,v.inventoryFileIds,v.signedFileIds)
        val entries=scopeEntries(c);val digest=scopeDigest(entries)
        if(v.scanDigest!=digest)E.fail("系统范围已变化，请重新读取并核对清册，不能忽略新增、消失或状态改变的对象")
        val existing=E.items(id).map {it.itemKey}.toSet()
        for(e in entries)if(e["key"] !in existing) {
            val item=InstitutionSuccessionItemDao.new {orgId=c.orgId;caseId=id;itemKey=e["key"] as String;sourceKind=e["kind"] as EnumSuccessionSource;sourceId=e["id"] as String;baseline=E.json(e["snapshot"]!!)}
            archives.seal(c.orgId,E.BIZ,item.id.value,"ITEM",E.itemValue(item),v.inventoryFileIds)
        }
        finish(c,org,c.id.value)
        return record(c,EnumSuccessionCaseRecord.SCOPE,mapOf("request" to v,"scanDigest" to digest,"items" to entries),"${v.inventoryFileIds},${v.signedFileIds}")
    }
    fun addUnrecorded(id:String,v:InstitutionUnrecordedVo,expectedOrgId:String?=null):String {
        val c=get(id,true,expectedOrgId);val org=c.orgId;actor(c.orgId,v.operatorUserId);actual(v.foundAt)
        text(v.recordNo,v.name,v.unit,v.actualLocation,v.custodianDescription,v.identification,v.photoFileIds,v.signedFileIds)
        if(v.quantity<=0 || v.recordNo.length>100)E.fail("请填写正数数量及有效清点编号")
        val sourceId=InstitutionSuccessionSources.digest(v.recordNo.trim());val key="UNRECORDED:$sourceId"
        val snapshot=mapOf("name" to v.name,"recordNo" to v.recordNo,"quantity" to v.quantity,"unit" to v.unit,"state" to "UNRECORDED","actual" to v)
        E.items(id).firstOrNull {it.itemKey==key}?.let {if(it.baseline==E.json(snapshot))return it.id.value;E.fail("同一清点编号已经登记，请保留原记录并追加核实事项")}
        val item=InstitutionSuccessionItemDao.new {orgId=c.orgId;caseId=id;itemKey=key;sourceKind=EnumSuccessionSource.UNRECORDED;this.sourceId=sourceId;baseline=E.json(snapshot)}
        archives.seal(c.orgId,E.BIZ,item.id.value,"ITEM",E.itemValue(item),"${v.photoFileIds},${v.signedFileIds}");return finish(c,org,item.id.value)
    }
    internal fun item(c:InstitutionSuccessionCaseDao,key:String)=E.items(c.id.value).singleOrNull {it.itemKey==key} ?: E.fail("对象尚未纳入本案清册，请先核对并记录当前范围")
    fun followUp(id:String,v:InstitutionItemFollowUpVo,expectedOrgId:String?=null):String {
        val c=get(id,true,expectedOrgId);actor(c.orgId,v.operatorUserId);actual(v.recordedAt);item(c,v.itemKey)
        text(v.issue,v.actualMeasures,v.evidenceFileIds);users.requireMuseumMember(v.responsibleUserId,c.orgId)
        if(v.nextCheckAt<v.recordedAt)E.fail("后续核查安排不能早于本次实际处理")
        return record(c,EnumSuccessionCaseRecord.FOLLOW_UP,v,v.evidenceFileIds)
    }
    fun resolve(id:String,v:InstitutionItemResolutionVo,expectedOrgId:String?=null):String {
        val c=get(id,true,expectedOrgId);actor(c.orgId,v.operatorUserId);actual(v.recordedAt);text(v.businessId,v.conclusion,v.signedFileIds)
        val proof=E.proof(c,item(c,v.itemKey),v)
        if(v.kind==EnumSuccessionResolution.INSTITUTION_TRANSFER) {
            // userfiles 按同一文件的全部永久归档身份授权；来源馆单方核销签署件
            // 复用双方调拨原件会让接收馆失去该原件的读取资格。
            val transferArchives=CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_TRANSFER) and
                    (CollectionPermanentArchives.bizId eq v.businessId)
            }.map {it.id.value}
            val shared=if(transferArchives.isEmpty()) emptySet() else CollectionPermanentArchiveFileDao.find {
                CollectionPermanentArchiveFiles.archiveId inList transferArchives
            }.map {it.fileId}.toSet()
            val signed=BusinessAttachmentBindingPlanner.plan("","清册核销签署件",v.signedFileIds).map { ref ->
                FileDao.findById(ref.fileId)?.id?.value
                    ?: FileDao.find {Files.path eq ref.fileId}.singleOrNull()?.id?.value ?: ref.fileId
            }
            if(signed.any(shared::contains))E.fail("清册核销签署件须使用独立原件，不能复用双方调拨原件")
        }
        return record(c,EnumSuccessionCaseRecord.RESOLUTION,mapOf("request" to v,"proof" to proof),v.signedFileIds)
    }
    private fun resolution(r:InstitutionSuccessionRecordDao)=JsonUtils.fromJson(E.node(r.content).get("request").toString(),InstitutionItemResolutionVo::class.java)!!
    private fun reviewValue(r:InstitutionSuccessionRecordDao)=JsonUtils.fromJson(E.node(r.content).get("request").toString(),InstitutionScopeReviewVo::class.java)!!
    private data class Status(val entries:List<Map<String,Any?>>,val digest:String,val scope:InstitutionSuccessionRecordDao?,val fresh:Boolean,val rows:List<Map<String,Any?>>,val reviewDigest:String) {
        val pending get()=rows.count {it["resolved"]!=true}
    }
    private fun status(c:InstitutionSuccessionCaseDao):Status {
        val entries=scopeEntries(c);val digest=scopeDigest(entries);val records=E.records(c.id.value)
        val latest=records.lastOrNull {it.kind==EnumSuccessionCaseRecord.SCOPE}
        val fresh=latest?.let {E.node(it.content).get("scanDigest")?.asText()==digest} ?: false
        val items=E.items(c.id.value).associateBy {it.itemKey}
        val rows=entries.map {entry->
            val key=entry["key"] as String
            val last=records.lastOrNull {r->when(r.kind) {
                EnumSuccessionCaseRecord.FOLLOW_UP->E.value<InstitutionItemFollowUpVo>(r).itemKey==key
                EnumSuccessionCaseRecord.RESOLUTION->resolution(r).itemKey==key
                else->false
            }}
            var proof:Map<String,Any?>?=null;var error:String?=null
            if(last?.kind==EnumSuccessionCaseRecord.RESOLUTION && items[key]!=null)try {
                proof=E.proof(c,items[key]!!,resolution(last))
                if(E.node(last.content).get("proof")!=E.node(E.json(proof))) {error="完成依据已发生变化，须重新核实";proof=null}
            } catch(e:BadRequestException) {error=e.message}
            val follow=last?.takeIf {it.kind==EnumSuccessionCaseRecord.FOLLOW_UP}?.let {E.value<InstitutionItemFollowUpVo>(it)}
            val investigation=try {InstitutionItemInvestigationEvidence.status(c,key)} catch(e:BadRequestException) {mapOf("state" to "NEEDS_RECHECK","issue" to e.message)}
            entry+mapOf("itemId" to items[key]?.id?.value,"baseline" to items[key]?.let {E.node(it.baseline)},"resolved" to (proof!=null),"actionId" to last?.id?.value,"proof" to proof,"error" to error,
                "followUp" to follow,"handoverReview" to investigation,"overdue" to (follow?.nextCheckAt?.isBefore(LocalDateTime.now())==true || investigation?.get("overdue")==true))
        }
        val reviewDigest=InstitutionSuccessionSources.digest(mapOf("scopeId" to latest?.id?.value,"scanDigest" to digest,"items" to rows.map {mapOf("key" to it["key"],"actionId" to it["actionId"],"proof" to it["proof"],"resolved" to it["resolved"])}))
        return Status(entries,digest,latest,fresh,rows,reviewDigest)
    }
    fun review(id:String,v:InstitutionScopeReviewVo,representative:Boolean=false,expectedOrgId:String?=null):String {
        val c=get(id,true,expectedOrgId);actor(c.orgId,v.reviewerUserId);actual(v.reviewedAt)
        val expected=if(representative)c.content.authorizedRepresentativeUserId else c.content.keeperUserId
        if(v.reviewerUserId!=expected)E.fail("须由本案指定的保管负责人或依法授权人员本人核对")
        if(!representative)users.requireRole(v.reviewerUserId,c.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        text(v.archiveReceiverName,v.archiveHandoverFileIds,v.unresolvedStatement,v.signedFileIds)
        if(!v.allLocationsAndObligationsChecked)E.fail("尚未核实全馆地点、外部保管和所有未完成义务，不能确认清册核对完成")
        val state=status(c)
        if(!state.fresh || state.scope?.id?.value!=v.scopeRecordId || state.reviewDigest!=v.reviewDigest || state.pending!=0)E.fail("范围已变化、尚未逐项处理或核对版本过期，请先补齐清册和真实完成依据")
        if(v.reviewedAt<state.scope!!.recordedAt)E.fail("本次核对不能早于当前系统清册版本形成时间")
        val keeper=if(representative)E.records(id).lastOrNull {it.kind==EnumSuccessionCaseRecord.KEEPER_REVIEW} ?: E.fail("须先由保管负责人核对同一版本") else null
        if(keeper!=null) {
            val k=reviewValue(keeper)
            if(v.reviewedAt<k.reviewedAt || v.copy(reviewedAt=k.reviewedAt,reviewerUserId=k.reviewerUserId,signedFileIds=k.signedFileIds)!=k)E.fail("依法授权人员须复核同一清册、档案承接及未完成事项说明，不能替换保管核对依据")
        }
        return record(c,if(representative)EnumSuccessionCaseRecord.REPRESENTATIVE_REVIEW else EnumSuccessionCaseRecord.KEEPER_REVIEW,mapOf("request" to v,"keeperRecordId" to keeper?.id?.value),"${v.archiveHandoverFileIds},${v.signedFileIds}",keeper)
    }
    fun detail(id:String,page:Int=1,pageSize:Int=20):Map<String,Any?> {
        if(page<1 || pageSize<0 || pageSize>200)E.fail("页码或每页数量无效，0表示明确读取全部")
        val c=get(id);val state=status(c);val records=E.records(id)
        val org=c.orgId
        val keeper=records.lastOrNull {it.kind==EnumSuccessionCaseRecord.KEEPER_REVIEW}
        val representative=records.lastOrNull {it.kind==EnumSuccessionCaseRecord.REPRESENTATIVE_REVIEW}
        val reviewed=state.fresh && state.pending==0 && keeper!=null && representative!=null && reviewValue(keeper).reviewDigest==state.reviewDigest && reviewValue(representative).reviewDigest==state.reviewDigest && E.node(representative.content).get("keeperRecordId")?.asText()==keeper.id.value
        val selected=if(pageSize==0)state.rows else state.rows.drop(((page.toLong()-1)*pageSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(pageSize)
        val evidenceErrors=mutableListOf<String>()
        val archiveFiles=(listOf(id to "CASE")+records.map {it.id.value to it.kind.name}+E.items(id).map {it.id.value to "ITEM"}).associate { (recordId,phase) ->
            val original=archives.phaseForDisplay(E.BIZ,recordId,org,phase) ?: E.fail("统筹案卷阶段原件显示信息缺失")
            (original["evidenceIssue"] as? String)?.let {evidenceErrors += it}
            recordId to original["files"]
        }
        val result=mapOf("evidenceErrors" to evidenceErrors.distinct(),"id" to id,"orgId" to c.orgId,"canOperate" to authorization.canWrite(c.orgId),"case" to c.content,"total" to state.rows.size,"pending" to state.pending,"overdue" to state.rows.count {it["overdue"]==true},"items" to selected,
            "linkableItems" to E.items(id).filter {it.sourceKind!=EnumSuccessionSource.UNRECORDED}.map {mapOf("value" to it.itemKey,"kind" to it.sourceKind,"label" to (E.node(it.baseline).get("name")?.asText() ?: it.sourceId),"recordNo" to E.node(it.baseline).get("recordNo")?.asText())},
            "scanDigest" to state.digest,"reviewDigest" to state.reviewDigest,"scopeRecordId" to state.scope?.id?.value,"scopeFresh" to state.fresh,
            "state" to if(reviewed)"SCOPE_REVIEWED" else if(representative!=null)"NEEDS_RECHECK" else "IN_PROGRESS",
            "keeperReview" to keeper?.let {reviewValue(it)},"records" to records.map {mapOf("id" to it.id.value,"kind" to it.kind,"recordedAt" to it.recordedAt,"content" to E.node(it.content))},"archiveFiles" to archiveFiles)
        if(get(id).orgId!=org)E.fail("统筹案卷所属收藏单位已变化，请重新读取")
        return result
    }
    fun page(page:Int=1,pageSize:Int=20):Map<String,Any?> {
        if(page<1 || pageSize<0 || pageSize>200)E.fail("页码或每页数量无效")
        val ids=authorization.readableOrgs()
        val rows=InstitutionSuccessionCaseDao.all().filter {it.orgId in ids}.onEach {access(it.orgId)}.sortedByDescending {it.id.value}
        val selected=if(pageSize==0)rows else rows.drop(((page.toLong()-1)*pageSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(pageSize)
        return mapOf("total" to rows.size,"list" to selected.map {E.verify(it);mapOf("id" to it.id.value,"orgId" to it.orgId,"canOperate" to authorization.canWrite(it.orgId),"case" to it.content)})
    }
    fun context():Map<String,Any?> {
        val org=authorization.contextOrg();if(org!=null)access(org)
        val allowed=authorization.writableOrgs()
        return mapOf("currentOrgId" to org,"currentUserId" to SecurityUtils.getUserId(),"canWrite" to (org!=null && authorization.canWrite(org)),"orgs" to CollectionOrgDao.all().filter {
            it.id.value in allowed
        }.map {mapOf("value" to it.id.value,"label" to it.name)})
    }
}
