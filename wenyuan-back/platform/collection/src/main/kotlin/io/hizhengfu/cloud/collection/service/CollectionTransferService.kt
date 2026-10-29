package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.rfid.dao.RfidTags
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidTagStatus
import io.hizhengfu.cloud.flow.domain.FlowCanceledEvent
import io.hizhengfu.cloud.flow.domain.FlowRejectedEvent
import io.hizhengfu.cloud.flow.domain.FlowReactivatedEvent
import io.hizhengfu.cloud.flow.domain.FlowTaskCompletedEvent
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.flow.service.FlowService
import io.hizhengfu.cloud.flow.service.FlowUserOperation
import io.hizhengfu.cloud.common.utils.SecurityUtils
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
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

internal data class CollectionTransferRecordValues(
    val toOrgId: String?,
    val reason: String?,
    val approvalDocNo: String?,
    val attachmentIds: String?,
) {
    companion object {
        fun fromSave(vo: CollectionTransferVo) = CollectionTransferRecordValues(
            toOrgId = vo.toOrgId,
            reason = vo.reason,
            approvalDocNo = vo.approvalDocNo,
            attachmentIds = vo.attachmentIds,
        )
    }
}

internal data class CollectionTransferMovementValues(
    val documentNo: String?,
    val basis: String?,
    val attachmentIds: String?,
    val operator: String?,
    val refBizId: String,
) {
    companion object {
        fun fromRecord(
            approvalDocNo: String?,
            reason: String?,
            attachmentIds: String?,
            operator: String?,
            refBizId: String,
        ) = CollectionTransferMovementValues(
            documentNo = approvalDocNo,
            basis = reason,
            attachmentIds = attachmentIds,
            operator = operator,
            refBizId = refBizId,
        )
    }
}

data class CollectionTransferInfo(
    val totalRegNo: String?,
    val name: String?,
)

object CollectionTransferInfoEnricher {
    fun enrich(items: List<CollectionTransferVo>, collectionInfoById: Map<String, CollectionTransferInfo>): List<CollectionTransferVo> =
        items.map { item ->
            val info = item.collectionId?.let { collectionInfoById[it] }
            item.copy(
                collectionTotalRegNo = if (item.executedAt != null) item.collectionTotalRegNo else info?.totalRegNo,
                collectionName = item.baseline?.formalName ?: info?.name.takeIf { item.executedAt == null },
            )
        }
}

/** 跨馆调拨（流程驱动）。审批只完成确认；实际签署执行才过户并留存双馆账。 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE])
class CollectionTransferService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val collectionService: CollectionService,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val operationLockService: CollectionOperationLockService,
    private val statutory: CollectionTransferStatutoryService,
    private val archives: CollectionPermanentArchiveService,
    private val catalogs: CollectionCatalogService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) : FlowService<CollectionTransferDao>(CollectionTransferDao, flowInstanceService, CollectionConst.CACHE) {

    override fun businessType() = CollectionConst.BIZ_TRANSFER
    override fun definitionKey() = CollectionConst.FLOW_TRANSFER
    override fun businessTypeName() = "跨馆调拨"
    override fun todoListRoute() = "/collection/transfer"
    override fun authorizeUserOperation(entity: CollectionTransferDao, operation: FlowUserOperation) {
        statutory.requireAccess(entity, operation != FlowUserOperation.FLOW_INFO)
        if (operation == FlowUserOperation.SAVE_DRAFT) statutory.requireEditable(entity)
        if (operation in setOf(FlowUserOperation.CANCEL,FlowUserOperation.VOID)) statutory.requireNoExecutedExchange(entity)
    }
    override fun userOperationVerifier(entity: CollectionTransferDao, operation: FlowUserOperation): () -> Unit =
        { statutory.requireAccess(entity, operation != FlowUserOperation.FLOW_INFO) }

    override fun buildTitle(entity: CollectionTransferDao) = "交换调拨：${entity.baseline?.formalName ?: entity.collectionId}"
    override fun buildFormData(entity: CollectionTransferDao): Map<String,Any?> = statutory.proposalSnapshot(entity) + mapOf(
        "administrativeEvidence" to statutory.administrative(entity.id.value), "execution" to entity.execution,
    )
    override fun buildSelfSelectAssignees(entity: CollectionTransferDao) = statutory.expectedActors(entity).mapValues { listOf(it.value.userId) }
    override fun beforeSubmit(entity: CollectionTransferDao) {
        if (entity.flowInstanceId != null) throw BadRequestException("已提交的交换调拨申请须保留原件，请新建申请")
        statutory.requirePrepared(entity)
        originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_TRANSFER, entity.id.value)
        archives.seal(entity.orgId,businessType(),entity.id.value,"SUBMITTED",buildFormData(entity),statutory.allFiles(entity))
    }

    protected override fun acquireApprovalResources(entity: CollectionTransferDao) =
        operationLockService.acquire(entity.orgId, businessType(), entity.id.value, listOf(entity.collectionId))

    protected override fun releaseApprovalResources(entity: CollectionTransferDao) {
        if (entity.flowStatus == EnumFlowStatus.PASS && entity.executedAt == null) return
        operationLockService.release(businessType(), entity.id.value)
    }

    fun findPage(request: HttpServletRequest): GenericPage<CollectionTransferVo> {
        val page = GenericPage<CollectionTransferVo>().build(request)
        val q = CollectionTransfers.selectAll()
        val readableOrgIds = statutory.readableOrgIds()
        q.andWhere { (CollectionTransfers.orgId inList readableOrgIds) or (CollectionTransfers.toOrgId inList readableOrgIds) }
        request.getParameter("keyword")?.trim()?.takeIf { it.isNotBlank() }?.let { kw ->
            // 已执行记录按原馆当时留账检索，后来的主档订正不能改变历史匹配结果。
            val retainedTransferIds = CollectionRetainedLedgers.selectAll().where {
                (CollectionRetainedLedgers.name like "%$kw%") or (CollectionRetainedLedgers.totalRegNo like "%$kw%")
            }.map { it[CollectionRetainedLedgers.transferId] }
            val retainedMatch = if (retainedTransferIds.isNotEmpty()) CollectionTransfers.id inList retainedTransferIds
                else CollectionTransfers.id eq "__NO_RETAINED_MATCH__"
            val matchedCollectionIds = collectionIdsMatchingKeyword(kw)
            val collectionMatch =
                if (matchedCollectionIds.isNotEmpty()) CollectionTransfers.collectionId inList matchedCollectionIds
                else CollectionTransfers.collectionId eq "__NO_COLLECTION_MATCH__"
            q.andWhere {
                (CollectionTransfers.collectionId like "%$kw%") or
                    (collectionMatch and CollectionTransfers.executedAt.isNull()) or
                    (retainedMatch and CollectionTransfers.executedAt.isNotNull()) or
                    (CollectionTransfers.fromOrgId like "%$kw%") or
                    (CollectionTransfers.toOrgId like "%$kw%") or
                    (CollectionTransfers.reason like "%$kw%") or
                    (CollectionTransfers.approvalDocNo like "%$kw%") or
                    (CollectionTransfers.attachmentIds like "%$kw%")
            }
        }
        request.getParameter("collectionId")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { CollectionTransfers.collectionId eq it } }
        request.getParameter("fromOrgId")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { CollectionTransfers.fromOrgId eq it } }
        request.getParameter("toOrgId")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { CollectionTransfers.toOrgId eq it } }
        request.getParameter("flowStatus")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumFlowStatus.valueOf(value) }.getOrNull()?.let { q.andWhere { CollectionTransfers.flowStatus eq it } }
        }
        request.getParameter("approvalDocNo")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { CollectionTransfers.approvalDocNo like "%$it%" } }
        request.getParameter("createTimeStart")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            parseCollectionDateParam(value)?.atStartOfDay()?.let { q.andWhere { CollectionTransfers.createTime greaterEq it } }
        }
        request.getParameter("createTimeEnd")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            parseCollectionDateParam(value)?.plusDays(1)?.atStartOfDay()?.minusNanos(1)
                ?.let { q.andWhere { CollectionTransfers.createTime lessEq it } }
        }
        request.getParameter("hasAttachment")?.trim()?.takeIf { it.isNotBlank() }?.toBooleanStrictOrNull()?.let { has ->
            if (has) q.andWhere { CollectionTransfers.attachmentIds.isNotNull() and CollectionTransfers.attachmentIds.neq("") }
            else q.andWhere { CollectionTransfers.attachmentIds.isNull() or CollectionTransfers.attachmentIds.eq("") }
        }
        q.orderBy(CollectionTransfers.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<CollectionTransferDao>)
        page.list = daoPage.data?.map(statutory::summary)?.let { enrichCollectionInfo(it) }
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<CollectionTransferVo>
    }

    fun get(id: String): CollectionTransferVo {
        val entity=statutory.scoped(id)
        val current=CollectionActorDirectory(scope).requireActive(SecurityUtils.getUserDetail().id)
        val source=CollectionOrgDao.findById(entity.orgId)
        val writable=(scope.currentWritableOrgIds(current)?.contains(entity.orgId) ?: true) &&
            (scope.isMulti() || scope.currentOrgId()==entity.orgId) &&
            source?.status==EnumContentStatus.NORMAL && source.orgStatus==EnumOrgStatus.ENABLED
        val editable=writable && statutory.proposalEditable(entity)
        return statutory.enrich(entity).copy(sourceWritable=writable,proposalEditable=editable)
    }

    /** 双馆仅可经本申请下载现有方案或验真的历史案卷附件，接收馆主档附件不借此入口开放。 */
    fun archive(id: String): List<Map<String, Any?>> {
        get(id)
        val entity = CollectionTransferDao[id]
        val result = archives.readForDisplay(businessType(), id, setOfNotNull(entity.orgId, entity.toOrgId)).archives
            .filter { it["phase"] != "RECEIVED" }
        get(id)
        return result
    }

    fun requireOriginal(id: String, fileId: String) {
        get(id)
        if (!fileId.matches(Regex("[0-9]{15,20}"))) throw BadRequestException("原件文件标识不正确")
        val entity = CollectionTransferDao[id]
        val current = statutory.allFiles(entity).split(',').map(String::trim).filter(String::isNotBlank).toSet()
        val archived = archives.listForAuthorizedOrgs(businessType(), id, setOfNotNull(entity.orgId, entity.toOrgId))
            .filter { it["phase"] != "RECEIVED" }
            .flatMap { archive ->
                @Suppress("UNCHECKED_CAST")
                (archive["files"] as List<Map<String, Any?>>).mapNotNull { it["fileId"] as? String }
            }.toSet()
        if (fileId !in current && fileId !in archived)
            throw BadRequestException("该文件不是本交换调拨申请的原件")
    }

    @CacheEvict(allEntries = true)
    fun save(vo: CollectionTransferVo): String {
        val collectionId = vo.collectionId?.takeIf { it.isNotBlank() } ?: throw BadRequestException("藏品必填")
        val toOrgId = vo.toOrgId?.takeIf { it.isNotBlank() } ?: throw BadRequestException("目标收藏单位必填")
        vo.id?.let { statutory.requireEditable(statutory.scoped(it,true)) }
        val target=CollectionOrgDao.findById(toOrgId) ?: throw BadRequestException("接收收藏单位不存在")
        if (target.orgStatus != EnumOrgStatus.ENABLED) throw BadRequestException("接收收藏单位已停用")
        val coll = CollectionDao.findById(collectionId) ?: throw BadRequestException("藏品不存在")
        statutory.requireWrite(coll.orgId)
        if (coll.orgId == toOrgId) throw BadRequestException("目标收藏单位不能与源单位相同")
        CollectionLocationMutation.acquireAll(listOf(coll.orgId, toOrgId))
        vo.dossier?.let { CollectionLocationMutation.storageReference(toOrgId, it.targetWarehouseId) }
        CollectionOutboundPolicy.requireInStore(coll.lifeStatus, "调拨", coll.inventoryHold)
        operationLockService.requireUnlocked(collectionId)
        val values = CollectionTransferRecordValues.fromSave(vo)
        val entity = saveDraft(vo.id, "调拨记录不存在", createEntity = {
            dao.new {
                orgId = coll.orgId
                this.collectionId = collectionId
                fromOrgId = coll.orgId
                createBy=SecurityUtils.getUserId(); createTime=LocalDateTime.now()
            }
        }) {
            statutory.requireWrite(orgId)
            if (this.collectionId != collectionId || orgId != coll.orgId) throw BadRequestException("不能更换申请对应的藏品或源馆")
            if (!vo.id.isNullOrBlank()) originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_TRANSFER, id.value)
            this.collectionId = collectionId
            fromOrgId = coll.orgId
            this.toOrgId = values.toOrgId
            reason = values.reason
            approvalDocNo = values.approvalDocNo
            attachmentIds = values.attachmentIds
            dossier = vo.dossier
            baseline = CollectionCatalogMapper.fromCollection(coll.toModel())
        }
        originalFiles?.remember(CollectionConst.BIZ_TRANSFER, entity.id.value)
        return entity.id.value
    }

    override fun onFlowTaskCompleted(entity: CollectionTransferDao,event: FlowTaskCompletedEvent) {
        if (event.action == "APPROVE") statutory.requireActor(entity,event.nodeKey,event.operator,event.comment)
    }
    override fun onFlowCompleted(entity: CollectionTransferDao, event: FlowCompletedEvent) {
        if (entity.flowInstanceId != event.instanceId || entity.executedAt != null) throw BadRequestException("审批实例与当前申请不一致")
        statutory.confirmApproval(entity,event.instanceId)
        super.onFlowCompleted(entity,event)
    }
    override fun onFlowCanceled(entity: CollectionTransferDao,event: FlowCanceledEvent) {
        statutory.requireNoExecutedExchange(entity)
        super.onFlowCanceled(entity,event)
    }
    override fun onFlowRejected(entity: CollectionTransferDao,event: FlowRejectedEvent) {
        statutory.requireNoExecutedExchange(entity)
        super.onFlowRejected(entity,event)
    }
    override fun beforeFlowReentry(entity: CollectionTransferDao, targetNodeKey: String) {
        statutory.requireNoExecutedExchange(entity)
        if (targetNodeKey != "source_keeper") throw BadRequestException("重新审批须从源馆保管负责人开始，不得复用上一轮意见")
        statutory.requirePrepared(entity)
    }

    @CacheEvict(cacheNames = [CollectionConst.CACHE,CollectionConst.CACHE_CATALOG], allEntries = true)
    fun execute(id: String,vo: CollectionTransferExecutionVo): String {
        val visible = CollectionTransferDao.findById(id) ?: throw BadRequestException("交换调拨申请不存在")
        fun requireCurrentLedgerAccess(orgId: String) {
            val current = CollectionCurrentOperationAccess.require(scope,orgId,true,
                setOf(CollectionConst.LEDGER_CORRECT),"调拨执行")
            CollectionDutySeparationPolicy.requireLedgerOperation(current.permissions,current.roleCodes)
        }
        requireCurrentLedgerAccess(visible.orgId)
        CollectionLocationMutation.acquireAll(listOfNotNull(visible.orgId, visible.toOrgId))
        // 锁定申请行后检查幂等状态，整次交接与双方账务在同一事务内完成。
        val entity=CollectionTransferDao.find { CollectionTransfers.id eq id }.forUpdate().singleOrNull()
            ?: throw BadRequestException("交换调拨申请不存在")
        // 等待双馆及申请锁期间可能撤权或变更任职，重复办理也必须重新核验。
        requireCurrentLedgerAccess(entity.orgId)
        CollectionTransferTerminationEvidence.requireExecutable(id)
        if (entity.executedAt != null) {
            verifyExecutedOriginals(entity,vo)
            return id
        }
        if (entity.flowStatus != EnumFlowStatus.PASS) throw BadRequestException("双方批准后才能办理实际签署交接")
        entity.dossier?.let { CollectionLocationMutation.storageReference(entity.toOrgId ?: throw BadRequestException("接收收藏单位必填"), it.targetWarehouseId) }
        val signed=statutory.requireExecution(entity,vo)
        val collection=statutory.requireBaseline(entity)
        operationLockService.requireUnlocked(collection.id.value,businessType(),id)
        val user=SecurityUtils.getUserId() ?: throw BadRequestException("请登录后办理")
        if (user in statutory.expectedActors(entity).values.map { it.userId } || user in listOf(signed.sourceCustodianUserId,signed.targetCustodianUserId)) throw BadRequestException("总账经办应与本次责任审批及实物保管交接分开")
        val source=entity.orgId
        val target=entity.toOrgId!!
        val d=entity.dossier!!
        val beforeWarehouse=collection.warehouseId
        val approvedAt=statutory.requireCurrentApprovalEvidence(entity)
        val content=CollectionCatalogVersionDao.find { (CollectionCatalogVersions.collectionId eq collection.id.value) and (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED) }.maxByOrNull { it.version }!!.content.copy(warehouseId=d.targetWarehouseId)
        originalFiles?.rememberExistingMain(collection.id.value)
        val signedFiles=listOf(signed.signedHandoverFileIds,signed.conditionImageFileIds,signed.signedLedgerChangeFileIds,signed.receivingLedgerReceiptFileIds,signed.packingCheckFileIds).joinToString(",")
        val mainSource=CollectionMainOriginalFileSource()
        val mainFiles=(mainSource.references(CollectionMainOriginalFileSource.BIZ,collection.id.value).map { it.file }+mainSource.historicalFileIds(collection.id.value)).joinToString(",")
        val preparedFiles=archives.prepareFiles(statutory.allFiles(entity)+","+signedFiles+","+mainFiles)
        val executedAt=LocalDateTime.now()
        entity.executedAt=executedAt; entity.executedBy=user; entity.execution=signed
        entity.flush()
        fun movement(org: String,moveType: EnumMoveType) = StorageMovementDao.new {
            orgId=org; collectionId=entity.collectionId; documentNo=id; type=moveType; reason=EnumMoveReason.TRANSFER
            totalRegNoAtMovement=collection.totalRegNo; collectionNameAtMovement=collection.name
            basis=entity.reason; operator=user; custodian=if (moveType == EnumMoveType.OUT) signed.sourceCustodianUserId else signed.targetCustodianUserId
            handoverUser=if (moveType == EnumMoveType.OUT) signed.targetCustodianUserId else signed.sourceCustodianUserId
            fromWarehouseId=beforeWarehouse; toWarehouseId=d.targetWarehouseId
            reviewOpinion=signed.conditionDescription.take(500); attachmentIds=signedFiles; operatedAt=signed.handedOn.atStartOfDay(); refBizId=id
        }
        val outbound=movement(source,EnumMoveType.OUT)
        CollectionMovementFacts.capture(outbound,archives)
        // 先冻结原馆总账、编目和附件，再物化接收馆数据。所有写入处于同一事务。
        CollectionLedgerStatRecorder.record(collection, io.hizhengfu.cloud.collection.domain.EnumLedgerStatEvent.TRANSFER_OUT, id, signed.handedOn.atStartOfDay())
        collectionService.changeOrg(entity.collectionId,target,"交换调拨：${entity.reason}",id)
        CollectionMediaDao.find { CollectionMedias.collectionId eq entity.collectionId }.forEach { it.orgId=target }
        CollectionMediaFolderDao.find { CollectionMediaFolders.collectionId eq entity.collectionId }.forEach { it.orgId=target }
        CollectionAttachmentDao.find { CollectionAttachments.collectionId eq entity.collectionId }.forEach { it.orgId=target }
        RfidTags.update({ (RfidTags.collectionId eq entity.collectionId) and (RfidTags.tagStatus eq EnumRfidTagStatus.ACTIVE) }) { it[orgId]=target }
        CollectionLedgerFieldChangeRecorder.warehouse(collection,d.targetWarehouseId,"交换调拨实际接收库位",id)
        catalogs.applyStatutoryChange(collection,content,id,entity.reason!!,entity.createBy,d.targetLeaderUserId,approvedAt,"交换调拨", recordLedgerChange=false, recordOriginals=false)
        CollectionLedgerFieldChangeRecorder.entryVoucher(collection,id,"交换调拨接收馆入馆凭证",id)
        CollectionLedgerStatRecorder.record(collection, io.hizhengfu.cloud.collection.domain.EnumLedgerStatEvent.TRANSFER_IN, id, signed.handedOn.atStartOfDay(), statReason=if(d.kind==io.hizhengfu.cloud.collection.domain.EnumCollectionTransferKind.STATE_EXCHANGE) EnumLedgerChangeReason.EXCHANGE else EnumLedgerChangeReason.TRANSFER_IN)
        val inbound=movement(target,EnumMoveType.IN)
        CollectionMovementFacts.capture(inbound,archives)
        // 接收馆也持有当时签收总账快照，之后订正不改变本次接收事实。
        val successionEvidence=if(d.kind==io.hizhengfu.cloud.collection.domain.EnumCollectionTransferKind.INSTITUTION_SUCCESSION) mapOf("proposal" to statutory.proposalSnapshot(entity),"administrative" to statutory.administrative(id)) else emptyMap()
        val retainedOriginals=CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.transferId eq id }.singleOrNull()
            ?: throw BadRequestException("缺少本次调拨的原馆原件留存清单")
        val receivingFiles=(if(successionEvidence.isNotEmpty()) statutory.allFiles(entity)+","+signedFiles else signedFiles)+","+retainedOriginals.snapshot.originalFileIds.joinToString(",")
        archives.sealPrepared(listOf(
            CollectionPermanentArchiveService.SealRequest(source,businessType(),id,"EXECUTED",statutory.enrich(entity),statutory.allFiles(entity)+","+signedFiles),
            CollectionPermanentArchiveService.SealRequest(target,businessType(),id,"RECEIVED",mapOf("transferId" to id,"collection" to collection.toModel(),"execution" to signed,
                "sourceOriginalArchiveId" to retainedOriginals.archiveId, "originalFileIds" to retainedOriginals.snapshot.originalFileIds)+successionEvidence,receivingFiles)
        ),preparedFiles)
        listOf(outbound.id.value to "交换调拨出库原件",inbound.id.value to "交换调拨接收入库原件",
            id to "交换调拨签署原件",collection.id.value to "交换调拨签署原件").forEach { (targetId,label) ->
            BusinessAttachmentBindingPlanner.plan(targetId,label,signedFiles).forEach { file ->
                if (originalFiles != null) originalFiles.bindExecutedTransferReference(id,targetId,file.fileId,file.aliasName)
                else attachmentBinder.bindSingle(targetId,file.aliasName,file.fileId)
            }
        }
        // 新签署材料已通过双方执行归档的引用资格及上面的受限主档关联；按接收馆当前主档留存读取身份。
        originalFiles?.rememberExistingMain(collection.id.value)
        operationLockService.release(businessType(),id)
        return id
    }

    /** 重复办理仍须核对原馆执行、接收馆签收和原馆留账；后续公告或再次过户不能改写当次冻结事实。 */
    private fun verifyExecutedOriginals(entity: CollectionTransferDao, vo: CollectionTransferExecutionVo) {
        fun fail(): Nothing = throw BadRequestException("调拨已执行状态与双馆永久原件不一致，请核查原记录")
        fun node(value: Any?) = JsonUtils.mapper.readTree(JsonUtils.toJson(value))
        fun canonical(files: String?): String? = files?.split(',')?.map(String::trim)?.filter(String::isNotBlank)
            ?.toSet()?.map { attachmentBinder.resolveSingle(it) ?: fail() }?.joinToString(",")
        val signed=entity.execution ?: fail()
        if (vo != signed) {
            val normalized=vo.copy(signedHandoverFileIds=canonical(vo.signedHandoverFileIds) ?: fail(),
                conditionImageFileIds=canonical(vo.conditionImageFileIds) ?: fail(),
                signedLedgerChangeFileIds=canonical(vo.signedLedgerChangeFileIds) ?: fail(),
                receivingLedgerReceiptFileIds=canonical(vo.receivingLedgerReceiptFileIds) ?: fail(),
                packingCheckFileIds=canonical(vo.packingCheckFileIds))
            if (normalized != signed) fail()
        }
        val target=entity.toOrgId ?: fail()
        val dossier=entity.dossier ?: fail()
        val retained=CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.transferId eq entity.id.value }.singleOrNull() ?: fail()
        if (retained.orgId!=entity.orgId || retained.collectionId!=entity.collectionId || retained.targetOrgId!=target) fail()
        val frozen=CollectionRetainedLedgerService(scope,archives).verifiedBoundSnapshotForAuthorizedBusiness(retained,entity.orgId)
        val originals=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq businessType()) and (CollectionPermanentArchives.bizId eq entity.id.value)
        }.filter { it.phase=="EXECUTED" || it.phase=="RECEIVED" }.associateBy { it.phase }
        if (originals.size!=2) fail()
        val source=originals.getValue("EXECUTED")
        val receiving=originals.getValue("RECEIVED")
        val sourceRoot=try { JsonUtils.mapper.readTree(source.snapshot) } catch (_: tools.jackson.core.JacksonException) { fail() }
        val receivingRoot=try { JsonUtils.mapper.readTree(receiving.snapshot) } catch (_: tools.jackson.core.JacksonException) { fail() }
        val current=node(entity.toModel())
        for (key in listOf("id","orgId","collectionId","fromOrgId","toOrgId","reason","approvalDocNo",
            "attachmentIds","dossier","baseline","execution","executedAt","executedBy",
            "flowStatus","flowInstanceId","createTime","createBy")) {
            if (sourceRoot.get(key)!=current.get(key)) fail()
        }
        if (sourceRoot.get("collectionTotalRegNo")!=node(retained.totalRegNo) ||
            receivingRoot.get("transferId")!=node(entity.id.value) ||
            receivingRoot.get("execution")!=node(signed) ||
            receivingRoot.get("sourceOriginalArchiveId")!=node(retained.archiveId) ||
            (receivingRoot.get("originalFileIds")!=node(frozen.originalFileIds) &&
                !(receivingRoot.get("originalFileIds")==null && frozen.originalFileIds.isEmpty()))) fail()
        val receivedCollection=receivingRoot.get("collection") ?: fail()
        if (receivedCollection.get("id")!=node(entity.collectionId) ||
            receivedCollection.get("orgId")!=node(target) ||
            receivedCollection.get("totalRegNo")!=node(retained.totalRegNo) ||
            receivedCollection.get("entryVoucherNo")!=node(entity.id.value) ||
            receivedCollection.get("warehouseId")!=node(dossier.targetWarehouseId)) fail()
        val signedFiles=listOfNotNull(signed.signedHandoverFileIds,signed.conditionImageFileIds,signed.signedLedgerChangeFileIds,
            signed.receivingLedgerReceiptFileIds,signed.packingCheckFileIds).joinToString(",")
        val sourceFiles=statutory.allFiles(entity)+","+signedFiles
        val receivingFiles=(if(dossier.kind==EnumCollectionTransferKind.INSTITUTION_SUCCESSION)
            statutory.allFiles(entity)+","+signedFiles else signedFiles)+","+frozen.originalFileIds.joinToString(",")
        archives.verifyExisting(source,entity.orgId,businessType(),entity.id.value,"EXECUTED",sourceRoot,sourceFiles)
        archives.verifyExisting(receiving,target,businessType(),entity.id.value,"RECEIVED",receivingRoot,receivingFiles)
    }

    private fun enrichCollectionInfo(items: List<CollectionTransferVo>): List<CollectionTransferVo> {
        val ids = items.mapNotNull { it.collectionId }.toSet()
        if (ids.isEmpty()) return items
        val infoById = Collections
            .selectAll()
            .where { Collections.id inList ids }
            .associate {
                it[Collections.id].value to CollectionTransferInfo(
                    totalRegNo = it[Collections.totalRegNo],
                    name = it[Collections.name],
                )
            }
        return CollectionTransferInfoEnricher.enrich(items, infoById)
    }

    private fun collectionIdsMatchingKeyword(keyword: String): List<String> {
        val q = Collections.selectAll()
        // 最终记录在 findPage 按双方权限过滤；接收方也须能搜索尚属源馆的申请藏品。
        q.andWhere {
            (Collections.totalRegNo like "%$keyword%") or
                (Collections.name like "%$keyword%")
        }
        return q.map { it[Collections.id].value }
    }
}
