package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class ReturnStatutoryService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val binder: BusinessAttachmentBinder,
    private val archives: CollectionPermanentArchiveService,
    private val incidents: CollectionIncidentService? = null,
) {
    fun source(e: CollectionReturnDao): ReturnSourceSnapshotVo = returnSourceSnapshot(e)
    fun requireEditable(e: CollectionReturnDao) {
        if(e.executionState==EnumReturnExecutionState.EXECUTED || e.inspection!=null || e.flowInstanceId!=null)fail("已登记点交或提交的归还方案须保留；不能改换来源、清单和保管位置")
    }
    fun requirePrepared(e: CollectionReturnDao): ReturnInspectionVo {
        val snapshot=source(e)
        if(e.sourceSnapshot!=snapshot)fail("来源点交基线已变化或尚未核实，请重新核对归还原件")
        if(e.operator.isNullOrBlank() || e.keeperUserId.isNullOrBlank())fail("请明确归还经办人和保管部门负责人")
        users.requireRole(e.keeperUserId!!,e.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        requireWarehouse(e.orgId,e.toWarehouseId)
        val value=e.inspection ?: fail("请先逐件查验真实性和保存状况，由双方签署归还点交单")
        if(value.inspectedAt.isBefore(snapshot.outAt) || value.inspectedAt.isAfter(LocalDateTime.now()))fail("实际归还点交时间须在出库之后且不得虚填未来时间")
        if(!value.originalDocumentsChecked || !value.distinctPartiesConfirmed)fail("须凭来源协议或提用凭证及出库点交单核验，并确认双方人员不同")
        val loan=e.sourceBizType==CollectionConst.BIZ_LOAN
        if(value.custodianUserIds.size<(if(loan)2 else 1) || value.custodianUserIds.any(String::isBlank))fail("保管方实际点交人员不足；借用归还保管方须至少两人")
        value.custodianUserIds.forEach { users.requireRole(it,e.orgId,CollectionConst.ROLE_KEEPER) }
        if(loan) {
            if(value.returningPersonNames.map(String::trim).filter(String::isNotBlank).distinct().size<2)fail("借用方归还点交须至少两名实际人员")
            if(e.executionState!=EnumReturnExecutionState.EXECUTED) {
                if(listOf(value.transportStandardReference,value.transportStandardApplicability,value.transportCarrierName,value.transportCarrierCapabilityFileIds,value.transportSupervisorName,value.transportSupervisionFileIds,value.packingCheckFileIds).any { it.isNullOrBlank() } || value.packingStandardConfirmed!=true)
                    fail("借用归还须核对适用包装运输标准、承运能力、专人监督及逐件包装签署原件")
                requiredFiles(value.transportCarrierCapabilityFileIds)
                requiredFiles(value.transportSupervisionFileIds)
                requiredFiles(value.packingCheckFileIds)
            }
        } else {
            if(value.returningUserIds.isEmpty() || value.returningUserIds.intersect(value.custodianUserIds).isNotEmpty())fail("提用归还须由不同的保管和提用人员共同签署")
            value.returningUserIds.forEach { users.requireDepartmentMember(it,e.orgId,snapshot.departmentId!!) }
        }
        val ids=splitCollectionIds(e.collectionIds)
        if(value.items.size!=ids.size || value.items.map { it.collectionId }.toSet()!=ids.toSet())fail("归还点交应逐件覆盖本次实际接收清单，不得漏件、重复或混入其他实物")
        val archivedScientific=if(loan) emptySet() else archivedScientificInspections(e)
        value.items.forEach { item ->
            val c=CollectionDao.findById(item.collectionId) ?: fail("藏品不存在")
            if(c.orgId!=e.orgId)fail("归还藏品所属馆不一致")
            val original = if (loan) LoanDao[e.sourceBizId].dossier!!.assessments.single { it.collectionId==item.collectionId }.objectKind
                else if (item.collectionId in archivedScientific) EnumLoanObjectKind.SCIENTIFIC_FOSSIL else null
            // 历史借用/已签署归还曾以 OTHER + SCIENTIFIC_FOSSIL 记录；保留其较严格的原类别，不能改为普通其他藏品。
            val legacyScientific = c.nature == EnumCollectionNature.OTHER && item.objectKind == EnumLoanObjectKind.SCIENTIFIC_FOSSIL &&
                original == EnumLoanObjectKind.SCIENTIFIC_FOSSIL
            if (!legacyScientific) CollectionNaturePolicy.requireLoanKind(c.nature,item.objectKind)
            if (original != null && original != item.objectKind) fail("归还不能改变原借用或已签署归还原件核实的法定管理类别")
            if(item.authenticityMethod.isBlank() || item.condition.isBlank())fail("每件藏品须记录真实性查验方法和具体保存状况")
            if(item.textNeedsImages && files(item.imageFileIds)==null)fail("文字不能尽述的保存状况须补充影像")
            if(item.abnormal() && (item.anomalyDescription.isNullOrBlank() || item.protectiveMeasures.isNullOrBlank()))fail("异常件须如实描述差异，并明确暂存保管和防护措施；不能直接恢复可借用状态")
        }
        requiredFiles(value.returningAuthorizationFileIds);requiredFiles(value.signedHandoverFileIds)
        return value
    }
    private fun archivedScientificInspections(e: CollectionReturnDao): Set<String> =
        CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.orgId eq e.orgId) and (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_RETURN) and
                (CollectionPermanentArchives.bizId eq e.id.value)
        }.filter { it.phase.startsWith("INSPECTION_") }.flatMap { row ->
            if (row.digest != CollectionCareEvidence.digest(row.snapshot)) fail("原归还点交档案摘要不一致，请核对原件")
            val data = try { JsonUtils.mapper.readTree(row.snapshot) }
                catch (_: tools.jackson.core.JacksonException) { fail("原归还点交档案无法解析，请核对原件") }
            if (data.path("id").asString() != e.id.value || data.path("orgId").asString() != e.orgId || data.path("sourceBizType").asString() != e.sourceBizType ||
                data.path("sourceBizId").asString() != e.sourceBizId || data.path("sourceSnapshot") != JsonUtils.mapper.readTree(JsonUtils.toJson(e.sourceSnapshot)!!))
                fail("原归还点交来源不一致，请核对原件")
            val historical=try { JsonUtils.fromJson(data.path("inspection").toString(),ReturnInspectionVo::class.java) }
                catch (_:tools.jackson.core.JacksonException) { fail("原归还点交档案无法解析，请核对原件") }
                ?: fail("原归还点交档案缺少逐件查验内容")
            val fileIds=(listOfNotNull(e.sourceSnapshot?.outSignedFileIds,e.sourceSnapshot?.agreementFileIds,
                historical.returningAuthorizationFileIds,historical.signedHandoverFileIds,
                historical.transportCarrierCapabilityFileIds,historical.transportSupervisionFileIds,
                historical.packingCheckFileIds,e.attachmentIds)+historical.items.mapNotNull {it.imageFileIds}).joinToString(",")
            archives.verifyExisting(row,e.orgId,CollectionConst.BIZ_RETURN,e.id.value,row.phase,data,fileIds)
            historical.items.filter {it.objectKind==EnumLoanObjectKind.SCIENTIFIC_FOSSIL}.map {it.collectionId}
        }.toSet()
    fun requireWarehouse(orgId: String,id: String?) {
        val place=id?.takeIf(String::isNotBlank)?.let { WarehouseDao.findById(it) } ?: fail("请选择实际接收保管位置")
        if(place.orgId!=orgId || place.status!=io.hizhengfu.cloud.common.domain.EnumContentStatus.NORMAL ||
            place.sceneType!=EnumLocationScene.STORAGE)fail("归还位置须为本馆有效保管库位")
    }
    fun recordInspection(id: String,vo: ReturnInspectionVo): String {
        val e=locked(id)
        val orgId=requireCurrentWrite(e,"归还逐件点交")
        if(e.executionState==EnumReturnExecutionState.EXECUTED || e.flowStatus !in setOf(EnumFlowStatus.DRAFT,EnumFlowStatus.AUDIT))fail("仅可在本轮确认前登记实际点交；已执行记录应通过异常处理追加材料")
        if(e.inspection!=null) {
            val currentArchive=currentInspectionArchive(e)
            if(e.inspection==vo)return id
            if(vo.expectedInspectionArchiveId!=currentArchive.id.value)fail("归还点交原件版本已变化，请重新打开案卷核对后补充或更正")
        } else if(!vo.expectedInspectionArchiveId.isNullOrBlank())fail("当前尚无归还点交原件，请重新打开案卷登记")
        if(rows(id).isNotEmpty())fail("已有异常报告必须保留对应点交事实，请通过调查处理追加说明")
        if(e.inspection!=null && vo.replacementReason.isNullOrBlank())fail("补充或更正点交原件须说明原因，原版永久保留")
        val old=e.inspection;val oldSnapshot=e.sourceSnapshot
        e.sourceSnapshot=source(e);e.inspection=vo
        try { requirePrepared(e) } catch(ex: Exception) { e.inspection=old;e.sourceSnapshot=oldSnapshot;throw ex }
        archives.seal(e.orgId,CollectionConst.BIZ_RETURN,id,"INSPECTION_${IdGenerate.nextId()}",proposal(e),allFiles(e))
        e.returnDate=vo.inspectedAt.toLocalDate()
        recheckCurrentWrite(e,orgId,"归还逐件点交")
        return id
    }
    fun requireSealedInspection(e:CollectionReturnDao) { currentInspectionArchive(e) }
    private fun currentInspectionArchive(e:CollectionReturnDao):CollectionPermanentArchiveDao {
        val archive=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_RETURN) and (CollectionPermanentArchives.bizId eq e.id.value)
        }.filter {it.phase.startsWith("INSPECTION_")}
            .maxWithOrNull(compareBy<CollectionPermanentArchiveDao>({it.createTime},{it.id.value}))
            ?: fail("现有归还点交缺少永久档案，请核对历史原件")
        if(archive.orgId!=e.orgId || archive.digest!=CollectionCareEvidence.digest(archive.snapshot))
            fail("现有归还点交永久档案所属馆或摘要不一致，请核对原件")
        val archivedInspection=try {
            JsonUtils.fromJson(JsonUtils.mapper.readTree(archive.snapshot).path("inspection").toString(),ReturnInspectionVo::class.java)
        } catch (_:tools.jackson.core.JacksonException) {fail("现有归还点交永久档案无法解析，请核对原件")}
        if(archivedInspection!=e.inspection)fail("现有归还点交与最新永久档案不一致，请核对原件")
        val currentProposal=proposal(e)
        val archivedProposal=try { JsonUtils.mapper.readTree(archive.snapshot) }
            catch (_:tools.jackson.core.JacksonException) {fail("现有归还点交永久档案无法解析，请核对原件")}
        val expectedProposal=JsonUtils.mapper.readTree(JsonUtils.toJson(currentProposal)!!)
        if(currentProposal.keys.any { it!="inspection" && archivedProposal.path(it)!=expectedProposal.path(it) })
            fail("现有归还点交的来源、清单或保管依据与最新永久档案不一致，请核对原件")
        archives.verifyExisting(archive,e.orgId,CollectionConst.BIZ_RETURN,e.id.value,archive.phase,
            archivedProposal,allFiles(e))
        return archive
    }
    fun requireActor(e: CollectionReturnDao,node: String?,operator: String?,comment: String?) {
        if(node!="keeper" || operator!=e.keeperUserId || comment.isNullOrBlank())fail("本单保管负责人须本人核对逐件验收、异常及安全保管措施并填写意见")
        users.requireRole(operator!!,e.orgId,CollectionConst.ROLE_STORAGE_HEAD)
    }
    private fun approval(e: CollectionReturnDao): FlowInstanceLogDao {
        val all=io.hizhengfu.cloud.flow.service.FlowApprovalHistory.current(e.flowInstanceId ?: fail("缺少归还确认实例"))
        return all.lastOrNull { it.nodeKey=="keeper" && it.operator==e.keeperUserId && !it.comment.isNullOrBlank() && it.taskId!=null } ?: fail("缺少本轮保管负责人实际确认；旧审批状态不能代替逐件验收")
    }
    fun confirmApproval(e: CollectionReturnDao) {
        val inspection=requirePrepared(e);currentInspectionArchive(e);val log=approval(e)
        requireActor(e,log.nodeKey,log.operator,log.comment)
        if(log.createTime.isBefore(inspection.inspectedAt))fail("保管负责人应在实际点交核验之后确认归还材料")
        archives.seal(e.orgId,CollectionConst.BIZ_RETURN,e.id.value,"APPROVED_${log.taskId!!.takeLast(40)}",mapOf("proposal" to proposal(e),"approval" to log.toVo()),allFiles(e))
    }
    fun requireApproved(e: CollectionReturnDao): ReturnInspectionVo {
        val result=requirePrepared(e);currentInspectionArchive(e);val log=approval(e)
        val phase="APPROVED_${log.taskId!!.takeLast(40)}"
        val archive=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_RETURN) and
            (CollectionPermanentArchives.bizId eq e.id.value) and (CollectionPermanentArchives.phase eq phase) }.singleOrNull()
            ?: fail("缺少本轮实际归还确认档案")
        archives.verifyExisting(archive,e.orgId,CollectionConst.BIZ_RETURN,e.id.value,phase,
            mapOf("proposal" to proposal(e),"approval" to log.toVo()),allFiles(e))
        return result
    }
    fun recordReport(id: String,v: ReturnReportVo,expectedInspectionArchiveId:String?): String {
        val e=locked(id);val orgId=requireCurrentWrite(e,"归还异常报告")
        if(expectedInspectionArchiveId.isNullOrBlank() || expectedInspectionArchiveId!=currentInspectionArchive(e).id.value)
            fail("归还点交原件版本已变化，请重新打开案卷核对后登记异常报告")
        val item=issue(e,v.collectionId)
        if(v.reportedAt.isBefore(e.inspection!!.inspectedAt) || v.reportedAt.isAfter(LocalDateTime.now()) || listOf(v.authorityName,v.reportNo,v.description,v.fileIds).any(String::isBlank))fail("请如实登记异常发生后的实际报案或报送机关、时间、编号及原件")
        if(v.kind==EnumReturnReportKind.RESULT_FILING && (!item.damaged || v.target!=EnumReturnReportTarget.NATIONAL))fail("核查处理结果备案用于文物损毁国家层级结果备案，其他报告请选择情况报告")
        val result=append(e,"REPORT",v.copy(fileIds=requiredFiles(v.fileIds)),v.fileIds)
        recheckCurrentWrite(e,orgId,"归还异常报告")
        return result
    }
    private fun requireCurrentWrite(e:CollectionReturnDao,label:String):String = e.orgId.also {orgId ->
        CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.STORAGE),label)
    }
    private fun recheckCurrentWrite(e:CollectionReturnDao,orgId:String,label:String) {
        TransactionManager.current().entityCache.flush()
        e.refresh(flush=false)
        if(e.orgId!=orgId)fail("$label 写入期间原归还单所属馆已变化，请重新核对")
        CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.STORAGE),label)
    }
    fun recordResolution(e: CollectionReturnDao,v: ReturnResolutionVo): String {
        val item=issue(e,v.collectionId)
        currentInspectionArchive(e)
        if(e.executionState!=EnumReturnExecutionState.EXECUTED)fail("先登记实际接收保管，再办理异常调查与解除待核查")
        val existing=resolutions(e.id.value).find { it.collectionId==v.collectionId }
        if(existing!=null) {
            if(sameResolution(existing,v) && v.collectionId in splitCollectionIds(e.acceptedCollectionIds)) {
                requireExistingClearanceArchive(e,item,v)
                return e.id.value
            }
            fail("既有处理结果不能覆盖或重复用于改变验收状态；状态与原件不一致时须核实历史记录")
        }
        if(!v.confirmedAuthentic || !v.safeForStorage)fail("真实性未确认或仍不适于安全保管时应继续待核查，不得标记验收完成")
        if(v.completedAt.isBefore(e.inspection!!.inspectedAt) || v.approvedAt.isBefore(v.completedAt) || v.approvedAt.isAfter(LocalDateTime.now()))fail("调查处理和负责人签署时间须在归还点交后如实登记")
        if(listOf(v.cause,v.finalCondition,v.reportDetermination).any(String::isBlank))fail("须查明原因、记录最终状况，并按数量、价值及相关情形说明报告程序判断")
        val responsibility=ReturnResponsibilityEvidence.requireResolution(e,v)
        requireResolutionSigners(e,v)
        val proof=clearanceProof(e,item,v)
        val files=clearanceFiles(v)
        append(e,"RESOLUTION",v,files)
        archives.seal(e.orgId, CollectionConst.BIZ_RETURN, e.id.value, "INCIDENT_CLEARANCE_${IdGenerate.nextId()}", mapOf("resolution" to v, "incidentProof" to proof, "responsibilityId" to responsibility?.record?.id), files)
        return e.id.value
    }
    private fun clearanceProof(e:CollectionReturnDao,item:ReturnItemCheckVo,v:ReturnResolutionVo):Map<String,String> {
        val incidentId=v.incidentId?.takeIf(String::isNotBlank) ?: fail("解除归还异常须关联本单本件已完成处理和本人确认的事故案卷")
        return (incidents ?: fail("事故处置服务未就绪，不能解除归还异常")).requireClearance(
            incidentId,e.orgId,v.collectionId,IncidentSourceVo(CollectionConst.BIZ_RETURN,e.id.value),v.approvedAt,
            v.keeperUserId,v.leaderUserId,clearanceRequirements(e,item,v),
        )
    }
    private fun clearanceRequirements(e:CollectionReturnDao,item:ReturnItemCheckVo,v:ReturnResolutionVo)=
        (requiredReports(e,item)+v.additionalReportTargets.map {it to EnumReturnReportKind.INCIDENT}).distinct().map { (target,kind) ->
            IncidentReportRequirement(v.collectionId,EnumIncidentTarget.valueOf(target.name),EnumIncidentReportKind.valueOf(kind.name))
        }
    private fun clearanceFiles(v:ReturnResolutionVo)=listOf(v.custodianExplanationFileIds,v.returningExplanationFileIds,v.investigationFileIds,v.resultFileIds,v.signedApprovalFileIds,v.reportDeterminationFileIds).map { requiredFiles(it) }.joinToString(",")
    private fun requireExistingClearanceArchive(e:CollectionReturnDao,item:ReturnItemCheckVo,v:ReturnResolutionVo) {
        val responsibility=ReturnResponsibilityEvidence.requireResolution(e,v)
        requireResolutionSigners(e,v)
        val proof=clearanceProof(e,item,v)
        val evidence=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_RETURN) and (CollectionPermanentArchives.bizId eq e.id.value)
        }.filter { it.phase.startsWith("INCIDENT_CLEARANCE_") }.map { archive ->
            val snapshot=try { JsonUtils.mapper.readTree(archive.snapshot) }
                catch (_:tools.jackson.core.JacksonException) { fail("归还事故处置原件无法解析，请核对档案") }
            val saved=try { JsonUtils.fromJson(snapshot.path("resolution").toString(),ReturnResolutionVo::class.java) }
                catch (_:tools.jackson.core.JacksonException) { fail("归还事故处置原件无法解析，请核对档案") }
            archive to (if(saved!=null && sameResolution(saved,v)) snapshot else null)
        }.filter { it.second!=null }
        if(evidence.size!=1)fail("归还事故处置依据原件缺失或重复，请核对档案")
        val (archive,matchedSnapshot)=evidence.single()
        val snapshot=matchedSnapshot ?: fail("归还事故处置依据原件缺失，请核对档案")
        val expectedProof=JsonUtils.mapper.readTree(JsonUtils.toJson(proof)!!)
        val expectedResponsibility=JsonUtils.mapper.readTree(JsonUtils.toJson(mapOf("responsibilityId" to responsibility?.record?.id))!!)
        if(archive.orgId!=e.orgId || archive.digest!=CollectionCareEvidence.digest(archive.snapshot) ||
            snapshot.path("incidentProof")!=expectedProof || snapshot.path("responsibilityId")!=expectedResponsibility.path("responsibilityId"))
            fail("归还事故处置依据原件与当前事故和责任版本不一致，请核对档案")
        archives.verifyExisting(archive,e.orgId,CollectionConst.BIZ_RETURN,e.id.value,archive.phase,
            snapshot,clearanceFiles(v))
    }
    private fun sameResolution(saved:ReturnResolutionVo,requested:ReturnResolutionVo):Boolean =
        saved==JsonUtils.fromJson(JsonUtils.toJson(requested)!!,ReturnResolutionVo::class.java)
    fun requireResolutionSigners(e: CollectionReturnDao,v: ReturnResolutionVo) {
        users.requireRole(v.keeperUserId,e.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(v.leaderUserId,e.orgId,CollectionConst.ROLE_DIRECTOR)
    }
    fun requiredReports(e: CollectionReturnDao,item: ReturnItemCheckVo): List<Pair<EnumReturnReportTarget,EnumReturnReportKind>> = buildList {
        if(item.damaged && item.objectKind!=EnumLoanObjectKind.OTHER) {
            val first=e.sourceSnapshot!!.items.single { it.collectionId==item.collectionId }.content.level==EnumRelicLevel.FIRST
            add((if(first)EnumReturnReportTarget.NATIONAL else EnumReturnReportTarget.PROVINCE) to EnumReturnReportKind.INCIDENT)
            if(!first)add(EnumReturnReportTarget.NATIONAL to EnumReturnReportKind.RESULT_FILING)
        }
        if(item.securityIncident!=EnumReturnSecurityIncident.NONE) { add(EnumReturnReportTarget.POLICE to EnumReturnReportKind.INCIDENT);add(EnumReturnReportTarget.COMPETENT to EnumReturnReportKind.INCIDENT) }
        if(item.damaged && item.objectKind==EnumLoanObjectKind.OTHER) add(EnumReturnReportTarget.COMPETENT to EnumReturnReportKind.INCIDENT)
    }
    fun acceptedIds(e: CollectionReturnDao): Set<String> = if(e.inspection==null) splitCollectionIds(e.collectionIds).toSet() else e.inspection!!.items.filter { !it.abnormal() || resolutions(e.id.value).any { r -> r.collectionId==it.collectionId } }.map { it.collectionId }.toSet()
    fun records(id: String): List<Map<String,Any?>> = rows(id).map { r -> mapOf("id" to r.id.value,"kind" to r.kind,"recordedAt" to r.createTime,"content" to if(r.kind=="REPORT")JsonUtils.fromJson(r.content,ReturnReportVo::class.java) else JsonUtils.fromJson(r.content,ReturnResolutionVo::class.java)) }
    fun pendingRequirements(e: CollectionReturnDao)=e.inspection?.items.orEmpty().filter { it.abnormal() && it.collectionId !in acceptedIds(e) }.map { item -> mapOf("collectionId" to item.collectionId,"reports" to requiredReports(e,item).map { mapOf("target" to it.first,"kind" to it.second) }) }
    private fun reports(id: String)=rows(id).filter { it.kind=="REPORT" }.map { JsonUtils.fromJson(it.content,ReturnReportVo::class.java)!! }
    private fun resolutions(id: String)=rows(id).filter { it.kind=="RESOLUTION" }.map { JsonUtils.fromJson(it.content,ReturnResolutionVo::class.java)!! }
    private fun rows(id:String):List<ReturnComplianceRecordDao> {
        val e=CollectionReturnDao.findById(id) ?: throw BadRequestException("归还单不存在")
        val owner=e.orgId
        return ReturnComplianceRecordDao.find {ReturnComplianceRecords.returnId eq id}.toList().also { rows ->
            if(rows.any {it.orgId!=owner})throw BadRequestException("归还报告或处置记录所属馆与原业务不一致")
            val originals=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_RETURN) and (CollectionPermanentArchives.bizId eq id)}.toList()
            val evidence=originals.filter {it.phase.startsWith("REPORT_") || it.phase.startsWith("RESOLUTION_")}
            if(evidence.map {it.phase}.toSet()!=rows.map {"${it.kind}_${it.id.value}"}.toSet() || evidence.size!=rows.size)fail("归还报告或处置记录与永久归档清单不一致")
            rows.forEach {r ->
                val a=evidence.single {it.phase=="${r.kind}_${r.id.value}"}
                if(a.orgId!=owner || a.digest!=CollectionCareEvidence.digest(a.snapshot))fail("归还报告或处置归档原件摘要不一致")
                try {
                    val declared=when(r.kind) {
                        "REPORT" -> {
                            val value=JsonUtils.fromJson(r.content,ReturnReportVo::class.java)!!
                            if(value!=JsonUtils.fromJson(a.snapshot,ReturnReportVo::class.java))fail("归还报告记录与原件不一致")
                            value.fileIds
                        }
                        "RESOLUTION" -> {
                            val value=JsonUtils.fromJson(r.content,ReturnResolutionVo::class.java)!!
                            if(value!=JsonUtils.fromJson(a.snapshot,ReturnResolutionVo::class.java))fail("归还处置记录与原件不一致")
                            listOf(value.custodianExplanationFileIds,value.returningExplanationFileIds,value.investigationFileIds,value.resultFileIds,value.signedApprovalFileIds,value.reportDeterminationFileIds).joinToString(",")
                        }
                        else -> fail("归还异常原件类型不支持")
                    }
                    CollectionResponsibilityArchiveEvidence.references(a,declared)
                } catch(_:tools.jackson.core.JacksonException) {fail("归还异常归档原件无法解析")}
            }
            try { requireRecordedClearances(e,rows,originals) }
            catch(_:tools.jackson.core.JacksonException) {fail("归还事故处置依据原件无法解析")}
        }
    }
    /** 历史案卷和待办读取同样以逐件事故解除原件为准，不仅核对处置记录自身的归档。 */
    private fun requireRecordedClearances(e:CollectionReturnDao,rows:List<ReturnComplianceRecordDao>,originals:List<CollectionPermanentArchiveDao>) {
        val resolved=rows.filter {it.kind=="RESOLUTION"}.associateBy {row ->
            JsonUtils.fromJson(row.content,ReturnResolutionVo::class.java)?.collectionId ?: fail("归还处置记录无法解析")
        }
        val clearances=originals.filter {it.phase.startsWith("INCIDENT_CLEARANCE_")}
        if(resolved.size!=rows.count {it.kind=="RESOLUTION"} || clearances.size!=resolved.size)
            fail("归还事故处置依据原件缺失、重复或未关联处置记录")
        val matched=mutableSetOf<String>()
        clearances.forEach {archive ->
            if(archive.orgId!=e.orgId || archive.digest!=CollectionCareEvidence.digest(archive.snapshot))
                fail("归还事故处置依据原件所属馆或摘要不一致")
            val snapshot=JsonUtils.mapper.readTree(archive.snapshot)
            val collectionId=snapshot.path("resolution").path("collectionId").asString()
            val row=resolved[collectionId] ?: fail("归还事故处置依据原件与处置记录不一致")
            if(!matched.add(collectionId) || snapshot.path("resolution")!=JsonUtils.mapper.readTree(row.content))
                fail("归还事故处置依据原件与处置记录不一致")
            val value=JsonUtils.fromJson(row.content,ReturnResolutionVo::class.java) ?: fail("归还处置记录无法解析")
            val incidentId=value.incidentId?.takeIf(String::isNotBlank) ?: fail("归还事故处置依据原件缺少关联事故")
            val confirmationId=snapshot.path("incidentProof").path("confirmationId").asString().takeIf(String::isNotBlank)
                ?: fail("归还事故处置依据原件缺少当次确认")
            val proof=(incidents ?: fail("事故处置服务未就绪，不能核对归还异常原件")).archivedClearanceProof(
                incidentId,e.orgId,value.collectionId,IncidentSourceVo(CollectionConst.BIZ_RETURN,e.id.value),value.approvedAt,
                value.keeperUserId,value.leaderUserId,confirmationId,clearanceRequirements(e,issue(e,value.collectionId),value),
            )
            if(snapshot.path("incidentProof")!=JsonUtils.mapper.readTree(JsonUtils.toJson(proof)!!))
                fail("归还事故处置依据原件与已归档事故版本不一致")
            val expectedResponsibility=JsonUtils.mapper.readTree(JsonUtils.toJson(mapOf("responsibilityId" to value.expectedResponsibilityId))!!)
            if(snapshot.path("responsibilityId")!=expectedResponsibility.path("responsibilityId"))
                fail("归还事故处置责任版本与原件不一致")
            val files=listOf(value.custodianExplanationFileIds,value.returningExplanationFileIds,value.investigationFileIds,value.resultFileIds,value.signedApprovalFileIds,value.reportDeterminationFileIds).joinToString(",")
            CollectionResponsibilityArchiveEvidence.references(archive,files)
        }
    }
    private fun issue(e: CollectionReturnDao,id: String)=e.inspection?.items?.find { it.collectionId==id && it.abnormal() } ?: fail("须关联本单逐件验收中已记录的异常")
    private fun append(e: CollectionReturnDao,kind: String,value: Any,fileIds: String): String {
        val json=JsonUtils.toJson(value)!!
        rows(e.id.value).find { it.kind==kind && it.content==json }?.let { return it.id.value }
        val record=ReturnComplianceRecordDao.new { orgId=e.orgId;returnId=e.id.value;this.kind=kind;content=json }
        archives.seal(e.orgId,CollectionConst.BIZ_RETURN,e.id.value,"${kind}_${record.id.value}",value,fileIds)
        return record.id.value
    }
    fun locked(id: String): CollectionReturnDao {
        val e=CollectionReturnDao.find { CollectionReturns.id eq id }.forUpdate().singleOrNull() ?: fail("归还单不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(),e.orgId,"归还办理")
        return e
    }
    fun proposal(e: CollectionReturnDao)=returnProposal(e)
    fun allFiles(e: CollectionReturnDao)=returnFiles(e)
    private fun files(v: String?)=v.orEmpty().split(',').map(String::trim).filter(String::isNotBlank).distinct().map { binder.resolveSingle(it) ?: fail("原件不存在") }.joinToString(",").takeIf(String::isNotBlank)
    private fun requiredFiles(v: String?)=files(v) ?: fail("双方签署及调查报告实际原件必填")
    private fun fail(message: String): Nothing=throw BadRequestException(message)
}

/** 办理与原件继承使用同一实际出库来源核对，不用客户端声明代替关联依据。 */
internal fun returnSourceSnapshot(e: CollectionReturnDao): ReturnSourceSnapshotVo {
    fun fail(message: String): Nothing = throw BadRequestException(message)
    val snapshot = when(e.sourceBizType) {
        CollectionConst.BIZ_LOAN -> {
            val s=LoanDao.findById(e.sourceBizId) ?: fail("借用来源不存在")
            val h=s.handover?.content ?: fail("旧借用来源缺少逐件出库签署原件，请先核实补录来源凭证")
            val d=s.dossier ?: fail("来源借用协议未核实")
            if(s.orgId!=e.orgId || !s.outExecuted || s.flowStatus!=EnumFlowStatus.PASS)fail("归还须关联本馆已实际出借的原单")
            ReturnSourceSnapshotVo(h.handedAt,h.signedHandoverFileIds,d.signedAgreementFileIds,d.keeperUserId,items=(s.baseline ?: fail("来源出库基线缺失，请核对原件")).items.map { i -> ReturnSourceItemVo(i.collectionId,i.content,h.items.singleOrNull { it.collectionId==i.collectionId } ?: fail("来源逐件点交记录缺失或重复，请核对原件")) })
        }
        CollectionConst.BIZ_REQUISITION -> {
            val s=RequisitionDao.findById(e.sourceBizId) ?: fail("提用来源不存在")
            val h=s.handover?.content ?: fail("旧提用来源缺少逐件出库签署原件，请先核实补录来源凭证")
            val d=s.dossier ?: fail("来源提用方案未核实")
            if(s.orgId!=e.orgId || !s.outExecuted || s.flowStatus!=EnumFlowStatus.PASS)fail("归还须关联本馆已实际提用的原单")
            ReturnSourceSnapshotVo(h.handedAt,h.signedHandoverFileIds,d.safetyPlanFileIds,d.keeperUserId,d.departmentGroupId,(s.baseline ?: fail("来源出库基线缺失，请核对原件")).items.map { i -> ReturnSourceItemVo(i.collectionId,i.content,h.items.singleOrNull { it.collectionId==i.collectionId } ?: fail("来源逐件点交记录缺失或重复，请核对原件")) })
        }
        else -> fail("不支持的归还来源")
    }
    val ids=splitCollectionIds(e.collectionIds)
    if(ids.isEmpty() || !snapshot.items.map { it.collectionId }.containsAll(ids))fail("本次归还清单须为原出库清单的实际收到子集")
    return snapshot.copy(items=snapshot.items.filter { it.collectionId in ids })
}
