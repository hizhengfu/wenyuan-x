package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.AppraisalDao
import io.hizhengfu.cloud.collection.dao.AcquisitionDao
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.dao.CollectionTransferDao
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.dao.DeregistrationDao
import io.hizhengfu.cloud.collection.dao.EntryVoucherDao
import io.hizhengfu.cloud.collection.dao.ExhibitionDao
import io.hizhengfu.cloud.collection.dao.InventoryDao
import io.hizhengfu.cloud.collection.dao.InventoryVo
import io.hizhengfu.cloud.collection.dao.Inventories
import io.hizhengfu.cloud.collection.dao.LoanDao
import io.hizhengfu.cloud.collection.dao.MovementVo
import io.hizhengfu.cloud.collection.dao.RequisitionDao
import io.hizhengfu.cloud.collection.dao.ReproductionDao
import io.hizhengfu.cloud.collection.dao.StorageMovementDao
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.dao.StorageMovements
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.domain.CollectionPrintFieldVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.collection.domain.EnumVoucherStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 打印数据集。前端负责版式，后端保证字段稳定和业务含义一致。 */
@Component
@Transactional(readOnly = true)
class CollectionPrintService(
    @Suppress("UNUSED_PARAMETER") collectionService: CollectionService,
    private val scope: CollectionScopeResolver,
    private val templateService: CollectionPrintTemplateService,
    private val loanStatutory: LoanStatutoryService,
    private val extensions: List<CollectionPrintExtension> = emptyList(),
    private val receiptEvidence: AccessionReceiptEvidence? = null,
    private val archives: CollectionPermanentArchiveService? = null,
) {
    private val sourceReader = CollectionPrintSourceReader(scope)
    private val historicalSource = CollectionHistoricalPrintSource(scope, archives, sourceReader)

    fun print(type: EnumCollectionPrintType, id: String): CollectionPrintVo = when (type) {
        EnumCollectionPrintType.COLLECTION_ARCHIVE -> collectionArchivePrint(id)
        EnumCollectionPrintType.MOVEMENT -> movementPrint(id)
        EnumCollectionPrintType.REQUISITION -> requisitionPrint(id)
        EnumCollectionPrintType.LOAN_HANDOVER -> loanPrint(id)
        EnumCollectionPrintType.DEREGISTRATION -> deregistrationPrint(id)
        EnumCollectionPrintType.INVENTORY -> inventoryPrint(id)
        EnumCollectionPrintType.APPRAISAL -> appraisalPrint(id)
        EnumCollectionPrintType.TRANSFER -> transferPrint(id)
        EnumCollectionPrintType.RESTORATION -> externalPrint(type,id)
        EnumCollectionPrintType.ACQUISITION -> acquisitionPrint(id)
        EnumCollectionPrintType.REPRODUCTION -> reproductionPrint(id)
        EnumCollectionPrintType.EXHIBITION -> exhibitionPrint(id)
        EnumCollectionPrintType.MOUNTING -> mountingPrint(id)
        EnumCollectionPrintType.ENTRY_VOUCHER -> entryVoucherPrint(id)
    }

    fun collectionArchivePrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.COLLECTION_ARCHIVE)
        val record = CollectionDao.findById(id) ?: throw BadRequestException("藏品不存在")
        val orgId = sourceReader.requireOrg(access,record.orgId,"藏品档案")
        val collection = sourceReader.collection(id,orgId)!!
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.COLLECTION_ARCHIVE,
            title = "藏品登记表",
            businessId = collection.id,
            documentNo = collection.totalRegNo ?: collection.id,
            orgId = collection.orgId,
            orgName = collection.orgId?.let { CollectionOrgDao.findById(it)?.name },
            fields = CollectionPrintFieldBuilder.collectionArchiveFields(collection),
            collections = listOf(collection),
        ))
    }

    fun acquisitionPrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.ACQUISITION)
        val acquisition = AcquisitionDao.findById(id)?.toModel() ?: throw BadRequestException("征集申请不存在")
        AcquisitionComplianceService.requireConfidentialAccess(scope,acquisition.orgId)
        if(acquisition.designatedReceptionId!=null)throw BadRequestException("行政指定接收请查阅原行政决定与指定接收专项原件，不生成普通征集审批表")
        val orgId = sourceReader.requireOrg(access, acquisition.orgId, "征集申请")
        val collection = acquisition.collectionId?.let { collectionId ->
            if (CollectionDao.findById(collectionId)?.orgId == orgId) sourceReader.collection(collectionId, orgId)
            else {
                val vouchers = EntryVoucherDao.find { EntryVouchers.orgId eq orgId }
                    .filter { id in splitCollectionIds(it.acquisitionIds) }
                val voucher = vouchers.singleOrNull()?.takeIf { it.voucherStatus == EnumVoucherStatus.REGISTERED }
                    ?: throw BadRequestException("缺少唯一已登记的原馆入馆凭证，请核实征集与藏品关联")
                entryVoucherCollection(collectionId, orgId, voucher.voucherNo)
            }
        }
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.ACQUISITION,
            title = "馆藏文物征集入藏审批登记表",
            businessId = acquisition.id,
            documentNo = acquisition.id,
            orgId = acquisition.orgId,
            orgName = acquisition.orgId?.let { CollectionOrgDao.findById(it)?.name },
            fields = CollectionPrintFieldBuilder.acquisitionFields(acquisition),
            collections = listOfNotNull(collection),
        ))
    }

    fun transferPrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.TRANSFER)
        val transfer = CollectionTransferDao.findById(id)?.toModel() ?: throw BadRequestException("调拨记录不存在")
        if (transfer.orgId !in access.orgIds && transfer.toOrgId !in access.orgIds) throw BadRequestException("无权查看该交换调拨申请")
        val orgId=transfer.orgId ?: throw BadRequestException("交换调拨缺少原馆记录")
        if(!transfer.fromOrgId.isNullOrBlank() && transfer.fromOrgId!=orgId)throw BadRequestException("交换调拨原馆记录不一致")
        val retainedRows=CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.transferId eq id }.toList()
        if(retainedRows.size>1)throw BadRequestException("调拨留存总账存在多个版本，请核实原记录")
        val retained=retainedRows.singleOrNull()
        if(retained!=null && (retained.orgId!=orgId || retained.targetOrgId!=transfer.toOrgId || retained.collectionId!=transfer.collectionId ||
            retained.snapshot.collection.orgId!=orgId || retained.snapshot.collection.id!=transfer.collectionId))throw BadRequestException("调拨留存总账与原业务不一致")
        val collection = if(retained!=null) {
            val originalService=CollectionRetainedLedgerService(scope,
                archives ?: throw BadRequestException("调拨留存原件核验服务未配置"))
            originalService.verifiedBoundSnapshotForAuthorizedBusiness(retained,orgId).collection
        } else {
            if(transfer.executedAt!=null)throw BadRequestException("已执行调拨缺少冻结留存总账，请核对原账")
            sourceReader.collection(transfer.collectionId,orgId)
        }
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.TRANSFER,
            title = "博物馆藏品跨馆转移交接表",
            businessId = transfer.id,
            documentNo = transfer.id,
            orgId = transfer.fromOrgId ?: transfer.orgId,
            orgName = transfer.fromOrgId?.let { CollectionOrgDao.findById(it)?.name },
            fields = CollectionPrintFieldBuilder.transferFields(transfer),
            collections = listOfNotNull(collection),
        ))
    }

    fun appraisalPrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.APPRAISAL)
        val row = AppraisalDao.findById(id) ?: throw BadRequestException("鉴定记录不存在")
        val appraisal = CollectionAppraisalPrintFacts.model(row)
        val orgId = sourceReader.requireOrg(access, appraisal.orgId, "鉴定记录")
        val frozen = CollectionAppraisalPrintFacts.read(appraisal, archives)
        val collection = frozen?.collection ?: appraisal.collectionId?.let { sourceReader.collection(it,orgId) }
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.APPRAISAL,
            title = "馆藏文物馆内鉴定意见表",
            businessId = appraisal.id,
            documentNo = appraisal.id,
            orgId = collection?.orgId ?: appraisal.orgId,
            orgName = collection?.orgId?.let { CollectionOrgDao.findById(it)?.name },
            fields = CollectionPrintFieldBuilder.appraisalFields(appraisal) + CollectionPrintFieldVo("著录来源", if(frozen != null) "馆内鉴定完成时冻结记录" else "参考当前主档；该记录未留存完成时著录快照"),
            collections = listOfNotNull(collection),
        ))
    }

    // 修复打印已迁移至 module-collection-restoration.service.RestorationPrintService

    fun reproductionPrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.REPRODUCTION)
        val reproduction = ReproductionDao.findById(id)?.toModel() ?: throw BadRequestException("复制拓印记录不存在")
        val orgId = sourceReader.requireOrg(access, reproduction.orgId, "复制拓印记录")
        val collection = reproduction.collectionId?.let { sourceReader.collection(it,orgId) }
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.REPRODUCTION,
            title = "馆藏文物复制拓印影像采集记录表",
            businessId = reproduction.id,
            documentNo = reproduction.id,
            orgId = collection?.orgId ?: reproduction.orgId,
            orgName = collection?.orgId?.let { CollectionOrgDao.findById(it)?.name },
            fields = CollectionPrintFieldBuilder.reproductionFields(reproduction),
            collections = listOfNotNull(collection),
        ))
    }

    fun movementPrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.MOVEMENT)
        val movement = StorageMovementDao.findById(id)?.toModel() ?: throw BadRequestException("出入库记录不存在")
        val orgId = sourceReader.requireOrg(access, movement.orgId, "出入库记录")
        val movements = movementPrintBatch(movement)
        val collections = MovementPrintRowBuilder.build(movements,
            { movementCollection(it, orgId) }, { row, location -> movementWarehouseLabel(row, location, orgId) })
        val paired=CollectionCirculationPrintFields.build(movement,archives)
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.MOVEMENT,
            title = if(paired.isNotEmpty()) "馆藏文物出入库关联单" else if (movement.type?.name == "IN") "馆藏文物入库单" else "馆藏文物出库单",
            businessId = movement.id,
            documentNo = movement.documentNo,
            orgId = movement.orgId,
            orgName = movement.orgId?.let { CollectionOrgDao.findById(it)?.name },
            fields = CollectionPrintFieldBuilder.movementFields(movement,
                movementWarehouseLabel(movement, movement.fromWarehouseId, orgId),
                movementWarehouseLabel(movement, movement.toWarehouseId, orgId)) +
                movementIdentitySource(movements) +
                CollectionPrintFieldVo("本单藏品数", movements.size.toString()) + paired,
            collections = collections,
        ))
    }

    /** 装裱凭证：以出入库记录为载体（书画提用装裱场景），同单号同向的流水合并为一张凭证。 */
    fun mountingPrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.MOUNTING)
        val movement = StorageMovementDao.findById(id)?.toModel() ?: throw BadRequestException("出入库记录不存在")
        val orgId = sourceReader.requireOrg(access, movement.orgId, "出入库记录")
        val movements = movementPrintBatch(movement)
        val collections = MovementPrintRowBuilder.build(movements,
            { movementCollection(it, orgId) }, { row, location -> movementWarehouseLabel(row, location, orgId) })
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.MOUNTING,
            title = "书画装裱交接凭证",
            businessId = movement.id,
            documentNo = movement.documentNo,
            orgId = movement.orgId,
            orgName = movement.orgId?.let { CollectionOrgDao.findById(it)?.name },
            fields = CollectionPrintFieldBuilder.movementFields(movement,
                movementWarehouseLabel(movement, movement.fromWarehouseId, orgId),
                movementWarehouseLabel(movement, movement.toWarehouseId, orgId)) +
                movementIdentitySource(movements) +
                CollectionPrintFieldVo("装裱藏品数", movements.size.toString()),
            collections = collections,
        ))
    }

    /** 凭证副本明确区分征集清单、实际核收与正式登记，不把草稿勾选当作实收事实。 */
    fun entryVoucherPrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.ENTRY_VOUCHER)
        val voucherRecord = EntryVoucherDao.findById(id) ?: throw BadRequestException("入馆凭证不存在")
        val voucher = voucherRecord.toModel()
        val orgId = sourceReader.requireOrg(access, voucher.orgId, "入馆凭证")
        val acquisitions = voucher.acquisitionIds?.split(",")?.map { it.trim() }?.filter(String::isNotBlank)
            ?.map { AcquisitionDao.findById(it)?.toModel() ?: throw BadRequestException("原征集对象不存在") }.orEmpty()
        if (acquisitions.any { it.orgId != voucher.orgId }) throw BadRequestException("征集对象所属馆与入馆凭证不一致，请核实原关联")
        val registered = acquisitions.mapNotNull { it.collectionId?.takeIf(String::isNotBlank) }.distinct().associateWith {
            entryVoucherCollection(it, orgId, voucher.voucherNo)
        }
        fun warehouseName(warehouseId: String?): String? {
            val warehouse = warehouseId?.let(WarehouseDao::findById)
            if (warehouse != null && warehouse.orgId != voucher.orgId) throw BadRequestException("接收库位所属馆与入馆凭证不一致，请核实原关联")
            return warehouse?.name ?: warehouseId
        }
        warehouseName(voucher.warehouseId)
        val rows = AccessionReceiptDao.find { AccessionReceipts.voucherId eq id }.toList()
        if (rows.any { it.orgId != voucher.orgId }) throw BadRequestException("实际核收原件所属馆与凭证不一致")
        val acquisitionIds = acquisitions.mapNotNull { it.id }.toSet()
        if (rows.any { it.acquisitionId !in acquisitionIds }) throw BadRequestException("实际核收条目与入馆凭证不一致，请核实原关联")
        val verified = rows.associate { row -> row.id.value to
            (receiptEvidence ?: throw BadRequestException("实际核收原件核验服务未配置，不能生成核收副本")).requireRead(row,voucherRecord) }
        val latest = rows.groupBy { it.acquisitionId }.mapValues { (_, values) -> values.maxBy { it.version } }
        acquisitions.forEach { acquisition ->
            latest[acquisition.id]?.let { row ->
                if (verified.getValue(row.id.value).registeredCollection?.id != acquisition.collectionId)
                    throw BadRequestException("征集对象与核收原件关联的登记记录不一致")
            }
        }
        val collections = acquisitions.map { acq ->
            acq.collectionId?.takeIf(String::isNotBlank)?.let { registered.getValue(it) } ?: CollectionVo(
                name = acq.name, era = acq.era, texture = acq.texture,
                level = io.hizhengfu.cloud.collection.domain.EnumRelicLevel.UNRATED,
                quantity = acq.quantity, unit = acq.unit, source = acq.source, acquireDate = acq.acquireDate,
                dimensions = acq.dimensions, weight = acq.weight, completeness = acq.completeness,
                completeStatus = acq.completeStatus, sourceDesc = acq.holder?.let { "持有人/移交方：$it" },
            )
        }
        val fields = mutableListOf(
            CollectionPrintFieldVo("入馆凭证号", voucher.voucherNo),
            CollectionPrintFieldVo("入藏依据", voucher.basis),
            CollectionPrintFieldVo("凭证状态", voucher.voucherStatus?.name, CollectionConst.DICT_VOUCHER_STATUS),
            CollectionPrintFieldVo("征集条目数", acquisitions.size.toString()),
            CollectionPrintFieldVo("著录来源", "下列著录参考当前有效主档或经核验的原馆留存总账；实际接收数量、日期、现状及签署以各版核收原件为据"),
            CollectionPrintFieldVo("实际核收说明", if (rows.isEmpty()) "尚无新版实际核收原件；以下为征集清单，历史资料须另行核实" else "以下为各征集条目最新实际核收清册副本，签署原件及旧版本永久保留"),
            CollectionPrintFieldVo("备注", voucher.remark),
            *CollectionPrintFieldBuilder.attachments("入馆凭证原件" to voucher.attachmentIds).toTypedArray(),
        )
        acquisitions.forEachIndexed { index, acq ->
            val prefix = "条目${index + 1}（${acq.name}）"
            val row = latest[acq.id]
            val collection = acq.collectionId?.let(registered::get)
            fields += CollectionPrintFieldVo("$prefix 登记号与状态", collection?.let { "${it.totalRegNo}；${if (!it.registered) "已预留，待正式登记" else if (it.registeredAt == null) "已登账·历史迁入，原登记日期待考" else "正式登记：${it.registeredAt}"}" } ?: "尚未生成待登记记录")
            if (row != null) {
                val value = row.content
                if (verified.getValue(row.id.value).legacyFileContentUnverified)
                    fields += CollectionPrintFieldVo("$prefix 历史附件核验", "原件内容基线未留存，实际文件一致性尚待核实；本次读取未补建基线")
                fields += CollectionPrintFieldVo("$prefix 实际核收", "第${row.version}版；时间：${value.checkedAt}；实收${value.pieces.size}${acq.unit}；${if (value.accepted == true) "核收完成" else "未完成核收"}")
                fields += CollectionPrintFieldVo("$prefix 实际接收库位", warehouseName(value.warehouseId))
                fields += CollectionPrintFieldVo("$prefix 实际点交人员", "移交：${value.handoverNames.joinToString("、")}；接收保管人员：${value.keeperUserIds.joinToString("、")}")
                value.pieces.forEach { piece ->
                    fields += CollectionPrintFieldVo("$prefix 清册标识 ${piece.pieceNo}", "实收：${piece.receivedAt}；实际数量：${piece.actualQuantity?.toString() ?: "待核实"} ${piece.actualQuantityUnit.orEmpty()}；识别：${piece.identification}；现状：${piece.condition}；身份相符：${piece.identityMatches}；现状相符：${piece.conditionMatches}；差异：${piece.discrepancy.orEmpty()}")
                    fields += CollectionPrintFieldBuilder.attachments("$prefix 清册影像 ${piece.pieceNo}" to piece.photoFileIds)
                }
                fields += CollectionPrintFieldBuilder.attachment("$prefix 实际签署原件", value.signedReceiptFileIds)
                value.issueDescription?.let { fields += CollectionPrintFieldVo("$prefix 未完成及防护", "$it；${value.protectiveMeasures}；后续核查：${value.followUpAt}") }
                value.lateReason?.let { fields += CollectionPrintFieldVo("$prefix 迟办处理", it) }
                fields += CollectionPrintFieldBuilder.attachments("$prefix 迟办处理原件" to value.lateHandlingFileIds)
                value.replacementReason?.let { fields += CollectionPrintFieldVo("$prefix 补充或订正", it) }
                fields += CollectionPrintFieldBuilder.attachments("$prefix 补充或订正原件" to value.correctionFileIds)
            }
        }
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.ENTRY_VOUCHER, title = "藏品入馆凭证及实际核收清册",
            businessId = voucher.id, documentNo = voucher.voucherNo, orgId = voucher.orgId,
            orgName = voucher.orgId?.let { CollectionOrgDao.findById(it)?.name }, fields = fields, collections = collections,
        ))
    }

    /** 移馆凭证只使用经核验且明确绑定本凭证的原馆快照，不借用接收馆当前资料。 */
    private fun entryVoucherCollection(id: String, orgId: String, voucherNo: String?): CollectionVo {
        if (CollectionDao.findById(id)?.let { it.orgId == orgId && it.entryVoucherNo == voucherNo } == true)
            return sourceReader.collection(id, orgId) ?: throw BadRequestException("凭证关联藏品不存在")
        if (voucherNo.isNullOrBlank()) throw BadRequestException("历史凭证缺少凭证号，不能推定原馆藏品关联")
        val candidates = CollectionRetainedLedgerDao.find {
            (CollectionRetainedLedgers.orgId eq orgId) and (CollectionRetainedLedgers.collectionId eq id)
        }.filter { it.snapshot.collection.entryVoucherNo == voucherNo }
        val retained = candidates.singleOrNull() ?: throw BadRequestException("缺少唯一对应本凭证的原馆留存总账，请核实原关联")
        val service = CollectionRetainedLedgerService(scope, archives ?: throw BadRequestException("历史原账核验服务未配置"))
        return service.verifiedBoundSnapshot(retained.id.value, orgId).collection
    }

    private fun movementPrintBatch(seed: MovementVo): List<MovementVo> {
        val documentNo = seed.documentNo?.trim().orEmpty()
        val orgId = seed.orgId
        val type = seed.type
        if (documentNo.isBlank() || orgId.isNullOrBlank() || type == null) return listOf(seed)

        val candidates = StorageMovementDao.find {
            (StorageMovements.orgId eq orgId) and
                (StorageMovements.documentNo eq documentNo) and
                (StorageMovements.type eq type)
        }.map { it.toModel() }
        return MovementPrintBatchScope.filterBatch(seed, candidates)
    }

    private fun movementWarehouseLabel(movement: MovementVo, id: String?, orgId: String): String? {
        val locationId = id?.takeIf(String::isNotBlank) ?: return null
        CollectionMovementFacts.read(movement,archives)?.let { facts ->
            val name=if(locationId==movement.fromWarehouseId) facts.fromWarehouseName else if(locationId==movement.toWarehouseId) facts.toWarehouseName else null
            return name?.let { "$it（$locationId）" } ?: locationId
        }
        val location = WarehouseDao.findById(locationId) ?: return locationId
        if (location.orgId != orgId) {
            val transfer = verifiedMovementTransfer(movement, orgId)
            val counterpart = when {
                movement.type == EnumMoveType.OUT && id == movement.toWarehouseId -> transfer.toOrgId
                movement.type == EnumMoveType.IN && id == movement.fromWarehouseId -> transfer.fromOrgId
                else -> null
            }
            if (location.orgId != counterpart)
                throw BadRequestException("出入库库位所属馆与业务记录不一致，请核实原记录")
        }
        return "${location.name}（$locationId）"
    }

    private fun verifiedMovementTransfer(movement: MovementVo, orgId: String): CollectionTransferDao {
        if (movement.reason != EnumMoveReason.TRANSFER)
            throw BadRequestException("非调拨流水不能引用异馆库位或留存账")
        val transferId = movement.refBizId?.takeIf(String::isNotBlank)
            ?: throw BadRequestException("调拨出入库流水缺少对应案卷")
        val transfer = CollectionTransferDao.findById(transferId)
            ?: throw BadRequestException("调拨出入库流水对应案卷不存在")
        val expectedOrg = when (movement.type) {
            EnumMoveType.OUT -> transfer.fromOrgId
            EnumMoveType.IN -> transfer.toOrgId
            null -> null
        }
        if (transfer.orgId != transfer.fromOrgId || transfer.executedAt == null ||
            transfer.collectionId != movement.collectionId || expectedOrg != orgId)
            throw BadRequestException("调拨出入库流水与已执行案卷不一致")
        val approvedTargetLocation = transfer.dossier?.targetWarehouseId
        if (!approvedTargetLocation.isNullOrBlank() && movement.toWarehouseId != approvedTargetLocation)
            throw BadRequestException("调拨出入库目标库位与已执行案卷不一致")
        return transfer
    }

    private fun movementCollection(movement: MovementVo, orgId: String): CollectionVo? {
        val collectionId = movement.collectionId?.takeIf(String::isNotBlank) ?: return null
        CollectionMovementFacts.read(movement,archives)?.let { return it.collection }
        if (movement.type == EnumMoveType.OUT && movement.reason == EnumMoveReason.TRANSFER)
            return transferredSourceMovementCollection(movement, orgId, collectionId)
        if (movement.type == EnumMoveType.IN && movement.reason == EnumMoveReason.TRANSFER) {
            val transfer = verifiedMovementTransfer(movement, orgId)
            val received = archives?.phaseForAuthorizedOrg(CollectionConst.BIZ_TRANSFER, transfer.id.value, orgId, "RECEIVED")
            if (received != null)
                return transferredReceivingMovementCollection(movement, orgId, collectionId, transfer, received)
        }
        if (CollectionDao.findById(collectionId)?.orgId == orgId)
            return sourceReader.collection(collectionId, orgId)
        throw BadRequestException("历史出入库关联藏品已不属本馆，且缺少可核验的发生时档案")
    }

    private fun transferredSourceMovementCollection(movement: MovementVo, orgId: String, collectionId: String): CollectionVo {
        val transfer = verifiedMovementTransfer(movement, orgId)
        val transferId = transfer.id.value
        val retained = CollectionRetainedLedgerDao.find {
            (CollectionRetainedLedgers.transferId eq transferId) and
                (CollectionRetainedLedgers.orgId eq orgId) and
                (CollectionRetainedLedgers.collectionId eq collectionId)
        }.singleOrNull() ?: throw BadRequestException("缺少唯一对应调拨案卷的原馆留存总账")
        if (retained.targetOrgId != transfer.toOrgId)
            throw BadRequestException("原馆出库流水与留存总账接收馆不一致")
        val saved = CollectionRetainedLedgerService(scope,
            archives ?: throw BadRequestException("历史原账核验服务未配置"))
            .verifiedBoundSnapshotForReader(retained.id.value, orgId,
                setOf(CollectionConst.STORAGE), CollectionConst.CROSS_ORG).collection
        requireMovementIdentityMatches(movement, saved)
        return saved
    }

    /** 接收后再次转出的旧入库单只读本次接收馆的冻结档案，不借用下一馆主档。 */
    private fun transferredReceivingMovementCollection(movement: MovementVo, orgId: String, collectionId: String,
        transfer: CollectionTransferDao, received: Map<String, Any?>): CollectionVo {
        val transferId = transfer.id.value
        val original = CollectionRetainedLedgerDao.find {
            (CollectionRetainedLedgers.transferId eq transferId) and
                (CollectionRetainedLedgers.orgId eq transfer.orgId) and
                (CollectionRetainedLedgers.collectionId eq collectionId)
        }.singleOrNull() ?: throw BadRequestException("接收入库流水缺少唯一对应的原馆留存总账")
        if (original.targetOrgId != orgId)
            throw BadRequestException("接收入库流水与原馆留存总账接收馆不一致")
        val snapshot = try { JsonUtils.mapper.readTree(received["snapshot"] as String) }
            catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("永久接收档案内容无法解析") }
        if (snapshot.path("transferId").asString() != transferId ||
            snapshot.path("sourceOriginalArchiveId").asString() != original.archiveId ||
            snapshot.path("collection").path("id").asString() != collectionId ||
            snapshot.path("collection").path("orgId").asString() != orgId)
            throw BadRequestException("接收入库流水与本馆永久接收档案身份不一致")
        val saved = try { JsonUtils.mapper.treeToValue(snapshot.path("collection"), CollectionVo::class.java) }
            catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("永久接收档案藏品快照无法解析") }
        requireMovementIdentityMatches(movement, saved)
        return saved
    }

    private fun requireMovementIdentityMatches(movement: MovementVo, saved: CollectionVo) {
        if ((!movement.totalRegNo.isNullOrBlank() && movement.totalRegNo != saved.totalRegNo) ||
            (!movement.collectionName.isNullOrBlank() && movement.collectionName != saved.name))
            throw BadRequestException("出入库发生时身份与永久留存记录不一致，请核对原件")
    }

    private fun movementIdentitySource(movements: List<MovementVo>): CollectionPrintFieldVo {
        val frozen = movements.count { CollectionMovementFacts.read(it,archives) != null }
        if(frozen == movements.size) return CollectionPrintFieldVo("藏品明细数据来源","本单所有藏品均使用出入库执行登记时冻结的身份、数量、尺寸、质量等著录；后续主档订正不改变本单")
        val hasMissingSnapshot = movements.any { it.totalRegNo.isNullOrBlank() || it.collectionName.isNullOrBlank() }
        val description = if (hasMissingSnapshot)
            "旧流水未完整留存当时的总登记号或名称；缺失字段和其他藏品属性按关联主档或经核验的原馆留存账、接收馆永久档案显示，须结合原件核对"
        else
            "总登记号、名称取本次流水留存值；其他藏品属性按关联主档或经核验的原馆留存账、接收馆永久档案显示，须结合原件核对"
        return CollectionPrintFieldVo("藏品明细数据来源", "${frozen}/${movements.size}条使用执行时完整冻结著录。其余：$description")
    }

    fun requisitionPrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.REQUISITION)
        val req = RequisitionDao.findById(id)?.toModel() ?: throw BadRequestException("提用单不存在")
        val orgId = sourceReader.requireOrg(access, req.orgId, "提用单")
        val resolved = req.collectionIds
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.map { historicalSource.resolve(it, orgId, req.outExecutedTime ?: req.createTime,
                if (req.outExecuted == true) CollectionConst.BIZ_REQUISITION else null,
                if (req.outExecuted == true) id else null) }
            ?: emptyList()
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.REQUISITION,
            title = "馆藏文物提用单",
            businessId = req.id,
            documentNo = req.documentNo,
            orgId = req.orgId,
            orgName = req.orgId?.let { CollectionOrgDao.findById(it)?.name },
            fields = CollectionPrintFieldBuilder.requisitionFields(req) + historicalSourceFields(resolved),
            collections = resolved.map { it.collection },
        ))
    }

    fun loanPrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.LOAN_HANDOVER)
        val record = LoanDao.findById(id) ?: throw BadRequestException("借用单不存在")
        val orgId = sourceReader.requireOrg(access, record.orgId, "借用单")
        if (LoanComplianceRecordDao.find { LoanComplianceRecords.loanId eq id }.any { it.orgId != orgId })
            throw BadRequestException("借用行政原件所属馆不一致，请核实原记录")
        val loan = record.toModel().copy(administrativeEvidence = loanStatutory.administrativeViews(id),overseasExtensions=loanStatutory.extensionViews(record),overseasPorts=loanStatutory.portViews(record),overseasFilings=loanStatutory.overseasFilingViews(record),exhibitionEnds=loanStatutory.exhibitionEndViews(record))
        val resolved = loan.collectionIds
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.map { historicalSource.resolve(it, orgId, loan.outExecutedTime ?: loan.createTime,
                if (loan.outExecuted == true) CollectionConst.BIZ_LOAN else null,
                if (loan.outExecuted == true) id else null) }
            ?: emptyList()
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.LOAN_HANDOVER,
            title = "馆藏藏品借用出馆交接单",
            businessId = loan.id,
            documentNo = loan.protocolNo,
            orgId = loan.orgId,
            orgName = loan.orgId?.let { CollectionOrgDao.findById(it)?.name },
            fields = CollectionPrintFieldBuilder.loanHandoverFields(loan) + historicalSourceFields(resolved),
            collections = resolved.map { it.collection },
        ))
    }

    fun deregistrationPrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.DEREGISTRATION)
        val sourceOrg=Deregistrations.select(Deregistrations.orgId).where {Deregistrations.id eq id}.singleOrNull()?.get(Deregistrations.orgId) ?: throw BadRequestException("退出申请不存在")
        val orgId = sourceReader.requireOrg(access, sourceOrg, "退出馆藏申请")
        val entity=DeregistrationDao[id]
        DeregistrationReadEvidence.verify(entity,archives ?: throw BadRequestException("退出原件核验服务未配置"))
        val record=entity.toModel()
        val experts = DeregistrationExpertOpinionDao.find { DeregistrationExpertOpinions.deregistrationId eq id }.toList()
        val notices = DeregistrationNoticeDao.find { DeregistrationNotices.deregistrationId eq id }.toList()
        val administrative = CollectionAdministrativeRecordDao.find { (CollectionAdministrativeRecords.bizType eq CollectionConst.BIZ_DEREGISTER) and (CollectionAdministrativeRecords.bizId eq id) }.toList()
        if (experts.any { it.orgId != orgId } || notices.any { it.orgId != orgId } || administrative.any { it.orgId != orgId })
            throw BadRequestException("退出关联原件所属馆不一致，请核实原记录")
        val administrativeEvidence = administrative.sortedBy { it.createTime }.map { it.content }
        val administrativeDisplay = DeregistrationAdministrativeDisplay.from(administrativeEvidence, record.approveLevel, record.approvalDocNo)
        val deregistration = record.copy(
            experts=experts.sortedBy { it.createTime }.map { it.content },
            notices=notices.sortedBy { it.createTime }.map { it.content },
            administrativeEvidence=administrativeEvidence,
            approveLevel=administrativeDisplay.level,
            approvalDocNo=administrativeDisplay.documentNo,
        )
        val proposal=archives!!.listForAuthorizedOrg(CollectionConst.BIZ_DEREGISTER,id,orgId).singleOrNull {it["phase"]=="PROPOSAL"}
        val historical=proposal?.let {DeregistrationPrintEvidence.select(entity,io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(it["snapshot"] as String))}
        val collection=historical?.collection ?: deregistration.collectionId?.let {sourceReader.collection(it,orgId)}
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.DEREGISTRATION,
            title = "藏品退出执行确认及处置记录",
            businessId = deregistration.id,
            documentNo = deregistration.approvalDocNo,
            orgId = deregistration.orgId,
            orgName = if(historical!=null)historical.orgName else deregistration.orgId?.let { CollectionOrgDao.findById(it)?.name },
            fields = listOf(CollectionPrintFieldVo("打印资料依据",historical?.basis ?: "未固定方案的草稿当前资料"))+CollectionPrintFieldBuilder.deregistrationFields(deregistration),
            collections = listOfNotNull(collection),
        ))
    }

    fun inventoryPrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.INVENTORY)
        val inventory = InventoryDao.findById(id)?.toModel() ?: throw BadRequestException("盘点记录不存在")
        val orgId = sourceReader.requireOrg(access, inventory.orgId, "盘点记录")
        val inventories = inventoryPrintBatch(inventory, orgId)
        val resolved = inventories.mapNotNull { row -> row.collectionId?.let {
            historicalSource.resolve(it,orgId,row.checkedAt ?: row.createTime)
        } }
        val collections = resolved.groupBy { it.collection.id }.values.map { versions ->
            if (versions.map { it.transferId }.distinct().size != 1)
                throw BadRequestException("同一盘点单含跨越不同转馆期间的同件藏品，请逐次核对原件")
            versions.first().collection
        }
        val batchSummary = InventoryBatchSummaryBuilder.build(inventories).firstOrNull()
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.INVENTORY,
            title = "馆藏文物盘点清查记录表",
            businessId = inventory.id,
            documentNo = inventory.batchName,
            orgId = orgId,
            orgName = CollectionOrgDao.findById(orgId)?.name,
            fields = CollectionPrintFieldBuilder.inventoryFields(inventory) + listOf(
                CollectionPrintFieldVo("核验依据", "本表汇总观察记录；实际账物卡照核验、离任签署及年度报送以清点批次永久档案为准"),
                CollectionPrintFieldVo("本批盘点数", inventories.size.toString()),
                CollectionPrintFieldVo("账物相符", (batchSummary?.matchCount ?: 0).toString()),
                CollectionPrintFieldVo("盘盈", (batchSummary?.surplusCount ?: 0).toString()),
                CollectionPrintFieldVo("盘亏", (batchSummary?.missingCount ?: 0).toString()),
                CollectionPrintFieldVo("状态异常", (batchSummary?.abnormalCount ?: 0).toString()),
            ) + historicalSourceFields(resolved),
            collections = collections,
        ))
    }

    private fun historicalSourceFields(rows: List<CollectionHistoricalPrintSource.Resolved>): List<CollectionPrintFieldVo> {
        val notes = rows.mapNotNull { it.source }.distinct()
        return if (notes.isEmpty()) emptyList() else listOf(CollectionPrintFieldVo("藏品明细数据来源", notes.joinToString("；")))
    }

    fun exhibitionPrint(id: String): CollectionPrintVo {
        val access = sourceReader.access(EnumCollectionPrintType.EXHIBITION)
        val exhibition = ExhibitionDao.findById(id)?.toModel() ?: throw BadRequestException("展览项目不存在")
        val orgId = sourceReader.requireOrg(access, exhibition.orgId, "展览项目")
        val collections = exhibition.collectionIds
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { sourceReader.collection(it,orgId) }
            ?: emptyList()
        return withTemplate(CollectionPrintVo(
            type = EnumCollectionPrintType.EXHIBITION,
            title = "馆藏文物展览记录表",
            businessId = exhibition.id,
            documentNo = exhibition.exhibitionNo ?: exhibition.id,
            orgId = exhibition.orgId,
            orgName = exhibition.orgId?.let { CollectionOrgDao.findById(it)?.name },
            fields = CollectionPrintFieldBuilder.exhibitionFields(exhibition),
            collections = collections,
        ))
    }

    private fun inventoryPrintBatch(seed: InventoryVo, orgId: String?): List<InventoryVo> {
        val batchName = seed.batchName?.trim().orEmpty()
        if (batchName.isBlank() || orgId.isNullOrBlank()) return listOf(seed)

        val candidates = InventoryDao.find {
            (Inventories.orgId eq orgId) and
                (Inventories.batchName eq batchName)
        }.map { it.toModel() }
        return InventoryPrintBatchScope.filterBatch(
            seed = seed,
            seedOrgId = orgId,
            candidates = candidates,
            orgIdOf = { orgId },
        )
    }

    private fun externalPrint(type: EnumCollectionPrintType, id: String): CollectionPrintVo {
        val provider=extensions.filter { it.type==type }.singleOrNull()
            ?: throw BadRequestException("该业务打印服务未配置或配置重复，请核对部署")
        val result=provider.fetch(id)
        if(result.type!=type || result.businessId!=id)throw BadRequestException("打印业务读取结果不一致")
        return withTemplate(result)
    }

    private fun withTemplate(print: CollectionPrintVo): CollectionPrintVo =
        CollectionRegistrationPrintFields.supplement(print).let { it.copy(template = templateService.resolve(it)) }
}
