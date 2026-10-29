package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.DeregistrationDao
import io.hizhengfu.cloud.collection.dao.DeregistrationVo
import io.hizhengfu.cloud.collection.dao.Deregistrations
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchiveDao
import io.hizhengfu.cloud.collection.dao.CollectionAdministrativeRecordDao
import io.hizhengfu.cloud.collection.dao.CollectionAdministrativeRecords
import io.hizhengfu.cloud.collection.domain.CollectionOutboundPolicy
import io.hizhengfu.cloud.collection.domain.CollectionChangePolicy
import io.hizhengfu.cloud.collection.domain.CollectionChangeExecutionVo
import io.hizhengfu.cloud.collection.domain.CollectionDutySeparationPolicy
import io.hizhengfu.cloud.collection.domain.DeregistrationCompliancePolicy
import io.hizhengfu.cloud.collection.domain.DeregistrationExecutionVo
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.flow.domain.FlowReactivatedEvent
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime
import io.hizhengfu.cloud.collection.domain.EnumApproveLevel
import io.hizhengfu.cloud.collection.domain.EnumDeregReason
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.dao.FlowTaskDao
import io.hizhengfu.cloud.flow.dao.FlowTasks
import io.hizhengfu.cloud.flow.domain.TaskStatus
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.flow.service.FlowService
import io.hizhengfu.cloud.flow.service.FlowUserOperation
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class DeregistrationCollectionInfo(
    val totalRegNo: String?,
    val name: String?,
)

object DeregistrationCollectionInfoEnricher {
    fun enrich(items: List<DeregistrationVo>, collectionInfoById: Map<String, DeregistrationCollectionInfo>): List<DeregistrationVo> =
        items.map { item ->
            val info = item.collectionId?.let { collectionInfoById[it] }
            item.copy(
                collectionTotalRegNo = info?.totalRegNo,
                collectionName = info?.name,
            )
        }
}

/**
 * 退出申请：法定证据齐备、责任人确认后，由经办人签署执行；总登记号和原账永久保留。
 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE])
class DeregistrationService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val collectionService: CollectionService,
    private val printService: CollectionPrintService,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val operationLockService: CollectionOperationLockService,
    private val statutory: DeregistrationStatutoryService,
    private val archives: CollectionPermanentArchiveService,
    private val auxiliary: CollectionAuxiliaryService,
    private val paperLedgers: PaperLedgerService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) : FlowService<DeregistrationDao>(DeregistrationDao, flowInstanceService, CollectionConst.CACHE) {

    override fun businessType() = CollectionConst.BIZ_DEREGISTER
    override fun definitionKey() = CollectionConst.FLOW_DEREGISTER
    override fun businessTypeName() = "藏品退出"
    override fun authorizeUserOperation(entity: DeregistrationDao, operation: FlowUserOperation) {
        val write=operation!=FlowUserOperation.FLOW_INFO
        CollectionCurrentOperationAccess.require(scope,entity.orgId,write,if(write)setOf(CollectionConst.DEREGISTER)else setOf(CollectionConst.DEREGISTER,CollectionConst.SELECT),"退出申请")
        if (operation == FlowUserOperation.SAVE_DRAFT) statutory.requireEditable(entity)
    }
    override fun buildTitle(entity: DeregistrationDao) = "藏品退出执行确认"
    override fun buildFormData(entity: DeregistrationDao):Map<String,Any?> {
        DeregistrationReadEvidence.verify(entity,archives)
        return mapOf(
        "collectionId" to entity.collectionId,
        "reason" to entity.reason?.name,
        "detail" to entity.detail,
        "baseline" to entity.baseline,
        "approveLevel" to entity.approveLevel?.name,
        "approvalDocNo" to entity.approvalDocNo,
        "expertOpinion" to entity.expertOpinion,
        "approvedBy" to entity.approvedBy,
        "destination" to entity.destination,
        "attachmentIds" to entity.attachmentIds,
        "dossier" to entity.dossier,
        "experts" to statutory.experts(entity.id.value),
        "notices" to statutory.notices(entity.id.value),
        "administrativeEvidence" to statutory.administrative(entity.id.value),
    )
    }

    protected override fun acquireApprovalResources(entity: DeregistrationDao) =
        operationLockService.acquire(entity.orgId, businessType(), entity.id.value, listOf(entity.collectionId))

    override fun buildSelfSelectAssignees(entity: DeregistrationDao) = mapOf(
        "keeper" to listOfNotNull(entity.dossier?.keeperUserId), "leader" to listOfNotNull(entity.dossier?.leaderUserId),
    )
    protected override fun releaseApprovalResources(entity: DeregistrationDao) {
        if (entity.flowStatus == EnumFlowStatus.PASS && entity.executedAt == null) return
        operationLockService.release(businessType(), entity.id.value)
    }

    private fun administrativeDisplays(orgIds: Collection<String>): Map<String, DeregistrationAdministrativeDisplay> =
        CollectionAdministrativeRecordDao.find {
            (CollectionAdministrativeRecords.bizType eq CollectionConst.BIZ_DEREGISTER) and
                (CollectionAdministrativeRecords.orgId inList orgIds)
        }.toList().groupBy { it.bizId }.mapValues { (_, rows) ->
            DeregistrationAdministrativeDisplay.from(rows.sortedBy { it.createTime }.map { it.content })
        }

    fun findPage(request: HttpServletRequest): GenericPage<DeregistrationVo> {
        val access=CollectionSearchReadAccess.require(scope,additionalPermissions=setOf(CollectionConst.DEREGISTER),allowHistoricalOrgs=true)
        val administrative by lazy { administrativeDisplays(access.orgIds) }
        val page = GenericPage<DeregistrationVo>().build(request)
        val q = Deregistrations.selectAll()
        q.andWhere { Deregistrations.orgId inList access.orgIds }
        request.getParameter("orgId")?.trim()?.takeIf(String::isNotBlank)?.let { org->
            OrgScopedReadPolicy.requireVisible(access.orgIds,org,"退出申请")
            q.andWhere {Deregistrations.orgId eq org}
        }
        request.getParameter("keyword")?.trim()?.takeIf { it.isNotBlank() }?.let { kw ->
            val administrativeIds=administrative.filterValues { it.documentNo?.contains(kw,ignoreCase=true)==true }.keys
            val oldDocumentMatch=if(administrative.isEmpty()) Deregistrations.approvalDocNo like "%$kw%"
                else (Deregistrations.id notInList administrative.keys) and (Deregistrations.approvalDocNo like "%$kw%")
            val pairs=Collections.select(Collections.id,Collections.orgId).where {
                (Collections.orgId inList access.orgIds) and ((Collections.totalRegNo like "%$kw%") or (Collections.name like "%$kw%"))
            }.groupBy({it[Collections.orgId]},{it[Collections.id].value})
            val collectionMatch=pairs.entries.map { (org,ids)-> (Deregistrations.orgId eq org) and (Deregistrations.collectionId inList ids) }
                .reduceOrNull {left,right->left or right} ?: (Deregistrations.collectionId eq "__NO_COLLECTION_MATCH__")
            q.andWhere {
                (Deregistrations.collectionId like "%$kw%") or
                    collectionMatch or
                    (Deregistrations.detail like "%$kw%") or
                    (Deregistrations.id inList administrativeIds.ifEmpty {setOf("__NO_ADMIN_DOCUMENT_MATCH__")}) or
                    oldDocumentMatch or
                    (Deregistrations.attachmentIds like "%$kw%")
            }
        }
        request.getParameter("collectionId")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { Deregistrations.collectionId eq it } }
        request.getParameter("reason")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumDeregReason.valueOf(value) }.getOrNull()?.let { q.andWhere { Deregistrations.reason eq it } }
        }
        request.getParameter("approveLevel")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumApproveLevel.valueOf(value) }.getOrNull()?.let { level ->
                val matched=administrative.filterValues { it.level==level }.keys
                val oldLevelMatch=if(administrative.isEmpty()) Deregistrations.approveLevel eq level
                    else (Deregistrations.id notInList administrative.keys) and (Deregistrations.approveLevel eq level)
                q.andWhere { (Deregistrations.id inList matched.ifEmpty {setOf("__NO_ADMIN_LEVEL_MATCH__")}) or oldLevelMatch }
            }
        }
        request.getParameter("flowStatus")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumFlowStatus.valueOf(value) }.getOrNull()?.let { q.andWhere { Deregistrations.flowStatus eq it } }
        }
        request.getParameter("approvalDocNo")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            val matched=administrative.filterValues { it.documentNo?.contains(value,ignoreCase=true)==true }.keys
            val oldDocumentMatch=if(administrative.isEmpty()) Deregistrations.approvalDocNo like "%$value%"
                else (Deregistrations.id notInList administrative.keys) and (Deregistrations.approvalDocNo like "%$value%")
            q.andWhere { (Deregistrations.id inList matched.ifEmpty {setOf("__NO_ADMIN_DOCUMENT_MATCH__")}) or oldDocumentMatch }
        }
        request.getParameter("createTimeStart")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            parseCollectionDateParam(value)?.atStartOfDay()?.let { q.andWhere { Deregistrations.createTime greaterEq it } }
        }
        request.getParameter("createTimeEnd")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            parseCollectionDateParam(value)?.plusDays(1)?.atStartOfDay()?.minusNanos(1)
                ?.let { q.andWhere { Deregistrations.createTime lessEq it } }
        }
        request.getParameter("hasAttachment")?.trim()?.takeIf { it.isNotBlank() }?.toBooleanStrictOrNull()?.let { has ->
            if (has) q.andWhere { Deregistrations.attachmentIds.isNotNull() and Deregistrations.attachmentIds.neq("") }
            else q.andWhere { Deregistrations.attachmentIds.isNull() or Deregistrations.attachmentIds.eq("") }
        }
        q.orderBy(Deregistrations.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<DeregistrationDao>)
        page.list = daoPage.data?.onEach {DeregistrationReadEvidence.verify(it,archives,verifyContent=false)}?.map { entity ->
            val row=statutory.summary(entity)
            withAdministrativeDisplay(row,statutory.administrative(entity.id.value))
        }?.let { enrichCollectionInfo(it) }
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<DeregistrationVo>
    }

    fun get(id: String): DeregistrationVo? {
        val orgId=Deregistrations.select(Deregistrations.orgId).where {Deregistrations.id eq id}.singleOrNull()?.get(Deregistrations.orgId) ?: return null
        CollectionCurrentOperationAccess.require(scope,orgId,false,setOf(CollectionConst.SELECT,CollectionConst.DEREGISTER),"退出申请")
        val row=DeregistrationDao[id]
        DeregistrationReadEvidence.verify(row,archives,verifyContent=false)
        val evidence=archives.readForDisplay(businessType(),id,orgId)
        val enriched=statutory.enrich(row)
        val result=enrichCollectionInfo(listOf(withAdministrativeDisplay(enriched,enriched.administrativeEvidence))).single()
        val user=SecurityUtils.getUserDetail()
        val cachedGate=user.permissions.any {it in setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.DEREGISTER)} || SecurityUtils.isAdminRole()
        val canOperate=cachedGate && runCatching {CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.DEREGISTER),"退出办理")}.isSuccess && result.sourceIssue==null
        return result.copy(canOperate=canOperate && evidence.issues.isEmpty(),evidenceErrors=evidence.issues)
    }

    /** 仅本案当前方案或验真永久档案中的文件可走案卷原件入口。 */
    fun requireOriginal(id: String, fileId: String) {
        val detail = get(id) ?: throw BadRequestException("退出申请不存在")
        if (!fileId.matches(Regex("[0-9]{15,20}"))) throw BadRequestException("原件文件标识不正确")
        val current = statutory.allFiles(DeregistrationDao[id]).split(',').map(String::trim).toSet()
        val archived = archives.listForAuthorizedOrg(businessType(), id, detail.orgId!!)
            .flatMap { archive ->
                @Suppress("UNCHECKED_CAST")
                (archive["files"] as List<Map<String, Any?>>).mapNotNull { it["fileId"] as? String }
            }.toSet()
        if (fileId !in current && fileId !in archived)
            throw BadRequestException("该文件不是本退出申请的原件")
    }
    fun print(id: String): io.hizhengfu.cloud.collection.domain.CollectionPrintVo {
        get(id) ?: throw BadRequestException("退出申请不存在")
        return printService.deregistrationPrint(id)
    }

    /** 新建/编辑退出馆藏申请（草稿）。 */
    @CacheEvict(allEntries = true)
    fun save(vo: DeregistrationVo): String {
        val collectionId = vo.collectionId?.takeIf { it.isNotBlank() } ?: throw BadRequestException("藏品必填")
        val collectionOrgId=Collections.select(Collections.orgId).where {Collections.id eq collectionId}.singleOrNull()?.get(Collections.orgId) ?: throw BadRequestException("藏品不存在")
        CollectionCurrentOperationAccess.require(scope,collectionOrgId,true,setOf(CollectionConst.DEREGISTER),"退出方案")
        vo.id?.takeIf(String::isNotBlank)?.let {statutory.requireAccess(it,true)}
        val coll = CollectionDao[collectionId]
        statutory.requireNoVeto(collectionId)
        DeregistrationCompliancePolicy.requireLifecycle(vo.reason,coll.lifeStatus)
        val dossier=statutory.normalizeDossier(vo.dossier)
        DeregistrationCompliancePolicy.requireReason(vo.reason,coll.nature,dossier.legalBasisFileIds)
        val entity = saveDraft(vo.id, "退出馆藏申请不存在", createEntity = {
            dao.new {
                orgId = coll.orgId
                this.collectionId = collectionId
            }
        }) {
            CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.DEREGISTER),"退出方案")
            if (coll.orgId != orgId) throw BadRequestException("退出申请不能改用其他收藏单位的藏品")
            if (!vo.id.isNullOrBlank()) originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_DEREGISTER, id.value)
            this.collectionId = collectionId
            reason = vo.reason
            detail = vo.detail
            this.dossier = dossier
            baseline = CollectionCatalogMapper.fromCollection(coll.toModel())
            createBy = createBy ?: SecurityUtils.getUserId()
            approveLevel = vo.approveLevel
            approvalDocNo = vo.approvalDocNo
            expertOpinion = vo.expertOpinion
            destination = vo.destination
            attachmentIds = vo.attachmentIds
        }
        originalFiles?.remember(CollectionConst.BIZ_DEREGISTER, entity.id.value)
        BusinessAttachmentBindingPlanner.plan(entity.id.value, "退出馆藏材料附件", entity.attachmentIds).forEach { item ->
            if (originalFiles?.bindRetainedBusinessReference(CollectionConst.BIZ_DEREGISTER, entity.id.value, entity.orgId, item.fileId, item.aliasName) != true)
                attachmentBinder.bindSingle(entity.id.value, item.aliasName, item.fileId)
        }
        return entity.id.value
    }

    /** 馆内确认通过后仍等待签署及实际退出，不能由流程完成直接退出馆藏。 */
    override fun onFlowCompleted(entity: DeregistrationDao, event: FlowCompletedEvent) {
        if (event.instanceId != entity.flowInstanceId) throw BadRequestException("退出申请与审批实例不一致")
        statutory.confirmApproval(entity,event.instanceId)
        super.onFlowCompleted(entity,event)
    }

    override fun beforeSubmit(entity: DeregistrationDao) {
        statutory.requirePrepared(entity)
        originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_DEREGISTER, entity.id.value)
        archives.seal(entity.orgId,businessType(),entity.id.value,"SUBMITTED",buildFormData(entity),statutory.allFiles(entity))
    }

    override fun beforeFlowReentry(entity: DeregistrationDao, targetNodeKey: String) {
        statutory.requireNoVeto(entity.collectionId)
        if (entity.executedAt != null) throw BadRequestException("已退出藏品不能重新激活原审批")
        if (targetNodeKey != "keeper") throw BadRequestException("重新确认退出必须从保管负责人开始")
        statutory.requirePrepared(entity)
    }

    @CacheEvict(allEntries = true)
    fun execute(id: String,vo: DeregistrationExecutionVo): String {
        val orgId=Deregistrations.select(Deregistrations.orgId).where {Deregistrations.id eq id}.singleOrNull()?.get(Deregistrations.orgId)
            ?: throw BadRequestException("退出申请不存在")
        CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.DEREGISTER),"退出执行")
        val current=CollectionActorDirectory(scope).requireActive(SecurityUtils.getUserId()!!)
        CollectionDutySeparationPolicy.requireLedgerOperation(current.permissions,current.roleCodes)
        val entity=DeregistrationDao[id]
        if(Collections.select(Collections.orgId).where {Collections.id eq entity.collectionId}.singleOrNull()?.get(Collections.orgId)!=orgId)
            throw BadRequestException("退出藏品与申请不属于同一收藏单位")
        DeregistrationReadEvidence.verify(entity,archives)
        if (entity.executedAt != null) {
            verifyExecutedOriginal(entity,vo)
            return id
        }
        if (entity.flowStatus != EnumFlowStatus.PASS) throw BadRequestException("馆内退出确认通过后才能执行")
        statutory.requirePrepared(entity)
        val approvedAt=statutory.requireCurrentApprovalEvidence(entity)
        CollectionChangePolicy.requireActors(SecurityUtils.getUserId()!!,entity.dossier!!.keeperUserId,entity.dossier!!.leaderUserId)
        CollectionChangePolicy.requireExecution(CollectionChangeExecutionVo(
            vo.signedChangeFileIds, vo.paperLedgerRef, vo.paperUpdatedAt, vo.paperLedgerBookId, vo.paperLedgerPageNo))
        val paperPage=paperLedgers.requirePageReference(entity.orgId,vo.paperLedgerBookId,vo.paperLedgerPageNo)
        if (!vo.paperLedgerRef.isNullOrBlank() && vo.paperLedgerRef.trim()!=paperPage.display)
            throw BadRequestException("纸质账文字引用与已核实的册号、页码不一致")
        if (vo.paperUpdatedAt.isBefore(approvedAt.toLocalDate())) throw BadRequestException("纸质账退出订正日期不能早于批准日期")
        val recordedExecution=vo.copy(paperLedgerRef=paperPage.display,paperLedgerBookId=paperPage.bookId,paperLedgerPageNo=paperPage.pageNo)
        val signed=requiredExitFiles(vo.signedChangeFileIds)
        val receipt=requiredExitFiles(vo.dispositionReceiptFileIds)
        operationLockService.requireUnlocked(entity.collectionId,businessType(),id)
        val now=LocalDateTime.now()
        val changed=Deregistrations.update({ (Deregistrations.id eq id) and Deregistrations.executedAt.isNull() and (Deregistrations.flowStatus eq EnumFlowStatus.PASS) }) { it[executedAt]=now }
        if (changed != 1) throw BadRequestException("退出已由其他人员办理，请刷新")
        val doc=statutory.administrative(id).lastOrNull()?.documentNo ?: entity.approvalDocNo
        collectionService.executeDeregister(entity.collectionId,entity.detail,id,doc,entity.reason)
        entity.refresh()
        auxiliary.registerExit(entity,receipt)
        archives.seal(entity.orgId,businessType(),id,"EXECUTED",mapOf("request" to statutory.enrich(entity),"execution" to recordedExecution,"lifecycleVersion" to 1,
            "proposalArchive" to DeregistrationLifecycleEvidence.reference(entity,archives,"PROPOSAL"),
            "approvalArchive" to DeregistrationLifecycleEvidence.reference(entity,archives,DeregistrationLifecycleEvidence.currentApprovalPhase(entity))),
            listOf(statutory.allFiles(entity),signed,receipt).joinToString(","))
        BusinessAttachmentBindingPlanner.plan(entity.collectionId,"退出签署和账物处置凭据",listOf(signed,receipt).joinToString(",")).forEach { file ->
            if (originalFiles != null) originalFiles.bindExecutedExitReference(id,entity.collectionId,file.fileId,file.aliasName)
            else attachmentBinder.bindSingle(entity.collectionId,file.aliasName,file.fileId)
        }
        operationLockService.release(businessType(),id)
        return id
    }

    /** 重复执行必须与封存的签署、纸账订正和处置回执相同，并重验当次原件。 */
    private fun verifyExecutedOriginal(entity: DeregistrationDao, vo: DeregistrationExecutionVo) {
        fun fail(): Nothing = throw BadRequestException("退出重复执行内容与永久原件不一致，请核对原记录")
        val id=entity.id.value
        val entry=archives.phaseForAuthorizedOrg(businessType(),id,entity.orgId,"EXECUTED") ?: fail()
        val archive=CollectionPermanentArchiveDao[entry["id"] as String]
        val root=try { JsonUtils.mapper.readTree(archive.snapshot) }
            catch (_: tools.jackson.core.JacksonException) { fail() }
        val recorded=root.get("execution") ?: fail()
        val originalPaperRef=recorded.get("paperLedgerRef")?.takeUnless {it.isNull}?.asText()
        val normalized=vo.copy(paperLedgerRef=vo.paperLedgerRef?.trim()?.takeIf(String::isNotBlank) ?: originalPaperRef)
        if (recorded!=JsonUtils.mapper.readTree(JsonUtils.toJson(normalized))) fail()
        val signed=recorded.get("signedChangeFileIds")?.asText() ?: fail()
        val receipt=recorded.get("dispositionReceiptFileIds")?.asText() ?: fail()
        archives.verifyExisting(archive,entity.orgId,businessType(),id,"EXECUTED",root,
            listOf(statutory.allFiles(entity),signed,receipt).joinToString(","))
    }

    fun archive(id: String): List<Map<String,Any?>> {
        val row=get(id) ?: throw BadRequestException("退出申请不存在")
        return archives.readForDisplay(businessType(),id,row.orgId!!).archives
    }

    private fun requiredExitFiles(value: String): String = BusinessAttachmentBindingPlanner.plan("","",value).map {
        attachmentBinder.resolveSingle(it.fileId) ?: throw BadRequestException("退出凭据文件不存在")
    }.joinToString(",").takeIf(String::isNotBlank) ?: throw BadRequestException("退出签署和处置凭据必填")

    private fun enrichCollectionInfo(items: List<DeregistrationVo>): List<DeregistrationVo> {
        val ids=items.mapNotNull {it.collectionId}.toSet()
        if(ids.isEmpty())return items
        val info=Collections.select(Collections.id,Collections.orgId,Collections.totalRegNo,Collections.name).where {Collections.id inList ids}
            .associateBy {it[Collections.orgId] to it[Collections.id].value}
        return items.map {item->
            val current=info[item.orgId to item.collectionId]
            item.copy(collectionName=item.collectionName ?: current?.get(Collections.name),collectionTotalRegNo=current?.get(Collections.totalRegNo),
                sourceIssue=if(current==null)"关联藏品已转馆或当前主档缺失，按退出方案留存资料查阅"else null)
        }
    }

    private fun withAdministrativeDisplay(row: DeregistrationVo, evidence: List<io.hizhengfu.cloud.collection.domain.CollectionAdministrativeEvidenceVo>): DeregistrationVo {
        val display=DeregistrationAdministrativeDisplay.from(evidence,row.approveLevel,row.approvalDocNo)
        return row.copy(approveLevel=display.level,approvalDocNo=display.documentNo)
    }
}
