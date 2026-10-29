package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Component
@Transactional
class AccessionReceiptService(
    private val scope: CollectionScopeResolver,
    private val acquisitionCompliance: AcquisitionComplianceService,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
    private val mainFileReadPolicy: CollectionMainFileReadPolicy,
) {
    companion object { const val BIZ = "COLLECTION_ACCESSION_RECEIPT" }
    private val evidence = AccessionReceiptEvidence(archives,mainFileReadPolicy)

    fun hasRecords(voucherId: String) = AccessionReceiptDao.find { AccessionReceipts.voucherId eq voucherId }.any()

    fun detail(voucherId: String): Map<String,Any?> {
        val access=CollectionSearchReadAccess.require(scope,acquisition=true,
            additionalPermissions=setOf(CollectionConst.LEDGER_CREATE,CollectionConst.STORAGE))
        val voucher=EntryVoucherDao.findById(voucherId) ?: fail("入馆凭证不存在")
        OrgScopedReadPolicy.requireVisible(access.orgIds,voucher.orgId,"入馆核收")
        val rows=rows(voucherId)
        val ids=splitCollectionIds(voucher.acquisitionIds)
        if(rows.any { it.orgId!=voucher.orgId || it.acquisitionId !in ids })fail("实际核收条目或所属馆与入馆凭证不一致，请核实原关联")
        val verified=rows.associate { it.id.value to evidence.requireRead(it,voucher,verifyContent=false) }
        val items=ids.map { id ->
            val acquisition=AcquisitionDao.findById(id) ?: fail("征集单不存在")
            if(acquisition.orgId!=voucher.orgId)fail("征集对象所属馆与入馆凭证不一致，请核实原关联")
            val collection=acquisition.collectionId?.takeIf(String::isNotBlank)?.let { CollectionDao.findById(it) ?: fail("已关联的登记记录不存在，请核实原关联") }
            val history=rows.filter { it.acquisitionId==id }
            val current=history.lastOrNull()
            // 有核收原件时由其归档和原馆移交链核定登记事实；旧式无原件记录仍只接受同馆现账。
            if(collection!=null && current==null && collection.orgId!=voucher.orgId)
                fail("登记记录所属馆与入馆凭证不一致，请核实原关联")
            val registration=current?.let { verified.getValue(it.id.value).registeredCollection }
                ?: if(current==null) collection?.toModel() else null
            if(current!=null && registration?.id!=acquisition.collectionId)
                fail("征集对象与核收原件关联的登记记录不一致")
            val acquired=current?.let { verified.getValue(it.id.value).actualAcquiredOn } ?: acquisitionCompliance.completedSource(acquisition)?.actualAcquiredOn
            mapOf("acquisitionId" to id,"name" to acquisition.name,"quantity" to acquisition.quantity,"unit" to acquisition.unit,
                "actualAcquiredOn" to acquired,"deadline" to acquired?.plusMonths(3),
                "overdue" to (acquired!=null && (current?.content?.takeIf { it.accepted==true }?.checkedAt?.toLocalDate() ?: LocalDate.now()).isAfter(acquired.plusMonths(3))),
                "current" to current?.let { view(it,verified.getValue(it.id.value)) },"history" to history.map { view(it,verified.getValue(it.id.value)) },
                "collectionId" to acquisition.collectionId,"totalRegNo" to registration?.totalRegNo,
                "registeredAt" to registration?.registeredAt, "historicalConfirmedAt" to registration?.historicalConfirmedAt,"catalogStatus" to registration?.catalogStatus)
        }
        return mapOf("voucher" to voucher.toModel(),"items" to items,"legacyUnverified" to (voucher.voucherStatus==EnumVoucherStatus.REGISTERED && rows.isEmpty()))
    }

    /** 当前凭证详情先完成馆别和完整核收链核验，再定位本版冻结档案文件。 */
    fun requireArchivedOriginal(voucherId:String,recordId:String,archiveId:String,fileId:String) {
        if (listOf(voucherId,recordId,archiveId,fileId).any { it.isBlank() || it.length>100 }) fail("核收归档原件标识不正确")
        @Suppress("UNCHECKED_CAST")
        val items=detail(voucherId)["items"] as List<Map<String,Any?>>
        @Suppress("UNCHECKED_CAST")
        val record=items.flatMap { it["history"] as List<Map<String,Any?>> }
            .singleOrNull { it["id"]==recordId } ?: fail("该核收版本不属于当前入馆凭证")
        @Suppress("UNCHECKED_CAST")
        val archive=(record["archives"] as List<Map<String,Any?>>)
            .singleOrNull { it["id"]==archiveId } ?: fail("该永久档案不属于所选核收版本")
        @Suppress("UNCHECKED_CAST")
        val files=archive["files"] as? List<Map<String,Any?>> ?: fail("核收归档文件清单无效")
        if (files.none { it["fileId"]==fileId }) fail("该文件不是所选核收永久档案的原件")
    }

    fun record(voucherId: String, value: AccessionReceiptVo): String {
        val voucher=voucher(voucherId,true)
        CollectionCurrentOperationAccess.require(scope,voucher.orgId,true,
            setOf(CollectionConst.ACQUIRE,CollectionConst.STORAGE),"入馆核收")
        val correctingPending=voucher.voucherStatus==EnumVoucherStatus.RECEIVED
        if(voucher.voucherStatus!=EnumVoucherStatus.DRAFT && !correctingPending)
            fail("正式登记后的核收依据不能覆盖，请通过正式变更及补录程序留存原件")
        if(value.acquisitionId !in splitCollectionIds(voucher.acquisitionIds))fail("核收对象不在本凭证内")
        // 实收地点可以不同于凭证计划地点；与位置维护按馆协调，并沿用凭证、馆、征集单的锁顺序。
        CollectionLocationMutation.acquire(voucher.orgId)
        CollectionCurrentOperationAccess.require(scope,voucher.orgId,true,
            setOf(CollectionConst.ACQUIRE,CollectionConst.STORAGE),"入馆核收")
        val acquisition=AcquisitionDao.find { Acquisitions.id eq value.acquisitionId }.forUpdate().singleOrNull() ?: fail("征集单不存在")
        if(acquisition.orgId!=voucher.orgId || acquisition.flowStatus!=EnumFlowStatus.PASS)
            fail("须为本馆已批准的征集对象")
        val linkedCollection=if(correctingPending) {
            val collectionId=acquisition.collectionId ?: fail("已接收凭证订正须关联实际生成的待登记对象")
            val collection=CollectionDao.find { Collections.id eq collectionId }.forUpdate().singleOrNull()
                ?: fail("核收关联的待登记对象不存在")
            if(collection.orgId!=voucher.orgId || collection.entryVoucherNo!=voucher.voucherNo ||
                collection.lifeStatus!=EnumCollectionStatus.PENDING || collection.registered ||
                collection.catalogStatus !in setOf(EnumCatalogStatus.DRAFT,EnumCatalogStatus.REJECTED) || collection.published ||
                CollectionCatalogVersionDao.find {(CollectionCatalogVersions.collectionId eq collectionId) and
                    (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED)}.any())
                fail("仅未提交编目审核、尚未正式登记的同馆对象可追加核收订正；已提交须先退回，已登记须另走正式变更")
            collection
        } else {
            if(acquisition.collectionId!=null)fail("已生成藏品记录的征集对象不能重新核收")
            null
        }
        val acquired=acquisitionCompliance.requirePrepared(acquisition).actualAcquiredOn
        val history=rows(voucherId).filter { it.acquisitionId==value.acquisitionId }
        val prior=history.lastOrNull()
        history.forEach {evidence.requireRead(it,voucher)}
        if(prior?.content==value)return prior.id.value
        if(prior!=null && (value.replacementReason.isNullOrBlank() || value.correctionFileIds.isNullOrBlank()))fail("补充实收或订正核收清册须说明原因并保留签署原件，旧事实不能删除")
        if(correctingPending) {
            val previous=prior ?: fail("已接收凭证订正须有原核收版本")
            if(previous.collectionId!=linkedCollection!!.id.value || previous.content.accepted!=true)
                fail("核收订正须对应已实际接收的同一待登记对象")
            if(value.checkedAt!=previous.content.checkedAt)
                fail("核收订正不能重写实际核收时间")
            val unchangedPieces=value.pieces.size==previous.content.pieces.size &&
                value.pieces.zip(previous.content.pieces).all { (current,old) ->
                    current.copy(photoFileIds=old.photoFileIds, actualQuantity=old.actualQuantity, actualQuantityUnit=old.actualQuantityUnit)==old &&
                        (old.actualQuantity==null || current.actualQuantity==old.actualQuantity) &&
                        (old.actualQuantityUnit==null || current.actualQuantityUnit==old.actualQuantityUnit)
                }
            if(!unchangedPieces || value.copy(pieces=previous.content.pieces,
                    signedReceiptFileIds=previous.content.signedReceiptFileIds,
                    replacementReason=previous.content.replacementReason,
                    correctionFileIds=previous.content.correctionFileIds)!=previous.content)
                fail("待登记阶段仅能订正照片、签署原件或凭新签署原件补录原缺失的实际件数；已有件数及实物、人员、地点和取得事实须保持原样")
            val oldFiles=history.flatMap {splitCollectionIds(it.content.signedReceiptFileIds)}.toSet()
            val sourceFiles=listOf(acquisition.expertOpinionAttachmentIds,acquisition.approvalAttachmentIds,
                acquisition.contractAttachmentIds,acquisition.invoiceAttachmentIds,acquisition.images).flatMap(::splitCollectionIds).toSet()
            val signedFiles=splitCollectionIds(value.signedReceiptFileIds)
            if(signedFiles.any { it in oldFiles || it in sourceFiles ||
                    CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.fileId eq it }.any() })
                fail("订正后的核收签署原件须独立于旧清册及征集合同、审批原件")
            val previousPhotos=history.flatMap { it.content.pieces.flatMap { piece -> splitCollectionIds(piece.photoFileIds) } }.toSet()
            val replacementPhotos=value.pieces.zip(previous.content.pieces).flatMap { (current,old) ->
                if(current.photoFileIds==old.photoFileIds) emptyList() else splitCollectionIds(current.photoFileIds)
            }
            if(replacementPhotos.distinct().size!=replacementPhotos.size || replacementPhotos.any { fileId ->
                    fileId in previousPhotos || fileId in sourceFiles || fileId in signedFiles ||
                        CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.fileId eq fileId }.any()
                }) fail("订正照片须逐件使用未归入其他永久档案的独立新原件")
        }
        val source=source(acquisition,acquired)
        if(history.any {it.sourceSnapshot!=source})fail("征集对象及取得依据发生变化，请先核实原始入藏手续")
        validate(voucher,acquisition,value,acquired,prior?.content)
        if (acquisition.designatedReceptionId == null) {
            val sourceFiles = acquisitionCompliance.sourceFileIds(acquisition)
            if (splitCollectionIds(value.signedReceiptFileIds).any { it in sourceFiles })
                fail("核收须上传本次移交与接收双方签署的独立清册，不能复用征集合同、审批及专项原件")
        }
        DesignatedReceptionEvidence.requireReceipt(acquisition,value)
        val row=AccessionReceiptDao.new {
            orgId=voucher.orgId;this.voucherId=voucherId;acquisitionId=value.acquisitionId
            version=(prior?.version ?: 0)+1;sourceSnapshot=source;content=value
        }
        archives.seal(voucher.orgId,BIZ,row.id.value,"RECORDED",mapOf("voucherNo" to voucher.voucherNo,"source" to source,"content" to value),files(value))
        if(linkedCollection!=null) {
            val counts=AccessionRegistrationFacts.actualQuantity(value)
            linkedCollection.actualQuantity=counts?.first
            linkedCollection.actualQuantityUnit=counts?.second
            linkedCollection.entryDate=AccessionRegistrationFacts.entryDate(value)
            linkedCollection.receiptCompletedAt=value.checkedAt
            CollectionCatalogDao.find { CollectionCatalogs.collectionId eq linkedCollection.id.value }.forEach { catalog ->
                catalog.content=catalog.content.copy(actualQuantity=counts?.first, actualQuantityUnit=counts?.second,
                    entryDate=linkedCollection.entryDate, receiptCompletedAt=value.checkedAt)
            }
            bindCreated(row,linkedCollection.id.value)
        }
        return row.id.value
    }

    private fun validate(voucher: EntryVoucherDao, acquisition: AcquisitionDao, value: AccessionReceiptVo, acquired: LocalDate, prior: AccessionReceiptVo?) {
        val now=LocalDateTime.now()
        if(value.checkedAt.isBefore(acquired.atStartOfDay()) || value.checkedAt.isAfter(now))fail("核收时间须在实际取得之后且不得填写未来时间")
        if(prior!=null && value.checkedAt.isBefore(prior.checkedAt))fail("后续核收或订正时间不得早于已记录的核收事实")
        val warehouse=CollectionLocationMutation.reference(voucher.orgId,value.warehouseId) ?: fail("实际接收保管库位不存在")
        if(warehouse.id.value!=value.warehouseId)fail("实际接收库位编号不一致，请重新选择实际位置")
        if(warehouse.orgId!=voucher.orgId || warehouse.sceneType!=EnumLocationScene.STORAGE)fail("实际接收库位须为本馆保管场所")
        if(value.keeperUserIds.isEmpty() || value.handoverNames.isEmpty() || value.handoverNames.any(String::isBlank))fail("须明确实际接收保管人员及移交人员姓名、单位")
        value.keeperUserIds.forEach { users.requireMuseumMember(it,voucher.orgId);users.requireRole(it,voucher.orgId,CollectionConst.ROLE_KEEPER) }
        if(value.pieces.isEmpty())fail("请记录已经实际收到的物品；尚未收到不能填写完成事实")
        if(value.pieces.map { it.pieceNo.trim() }.distinct().size!=value.pieces.size)fail("每件或每套须有不同的清册临时标识")
        value.pieces.forEach { piece ->
            if(piece.pieceNo.isBlank() || piece.pieceNo.length>100 || piece.identification.isBlank() || piece.condition.isBlank() || piece.photoFileIds.isBlank())fail("每件须填写清册标识、实物识别、保存状况和实际照片")
            if(piece.receivedAt.isBefore(acquired.atStartOfDay()) || piece.receivedAt.isAfter(value.checkedAt))fail("逐件实收时间须在实际取得和本次核收之间")
            if(piece.identityMatches==null || piece.conditionMatches==null)fail("须逐件明确核实身份和保存状况是否与取得依据一致")
            if((piece.identityMatches!=true || piece.conditionMatches!=true) && piece.discrepancy.isNullOrBlank())fail("逐件不符情况须如实说明")
        }
        if(value.signedReceiptFileIds.isBlank() || value.accepted==null)fail("须有真实双方签署清册，并明确是否完成入藏核收")
        if(value.accepted==true) {
            AccessionRegistrationFacts.requireActualQuantity(value)
            if(value.pieces.size!=acquisition.quantity || value.pieces.any { it.identityMatches!=true || it.conditionMatches!=true })fail("未收齐、数量超出或实物存在未解决差异，不能标为核收完成")
            if(value.checkedAt.toLocalDate().isAfter(acquired.plusMonths(3)) && (value.lateReason.isNullOrBlank() || value.lateHandlingFileIds.isNullOrBlank()))fail("已超过取得后三个月，须如实记录迟办原因及处理原件，不能倒填按期完成")
        } else if(value.issueDescription.isNullOrBlank() || value.protectiveMeasures.isNullOrBlank() || value.followUpAt==null || !value.followUpAt.isAfter(value.checkedAt)) {
            fail("尚未完成核收须说明未收齐或差异情况、实际防护措施及后续核查时间")
        }
    }

    fun requireCompleted(voucher: EntryVoucherDao): Map<String,AccessionReceiptDao> = splitCollectionIds(voucher.acquisitionIds).associateWith { id ->
        val row=rows(voucher.id.value).lastOrNull { it.acquisitionId==id } ?: fail("请先为凭证内每份征集对象登记实际逐件核收原件")
        val acquisition=AcquisitionDao.findById(id) ?: fail("征集单不存在")
        val acquired=acquisitionCompliance.requirePrepared(acquisition).actualAcquiredOn
        DesignatedReceptionEvidence.requireReceipt(acquisition,row.content)
        if(row.content.accepted!=true)fail("仍有未完成核收的对象，请处理实收差异后再生成待登记记录")
        if(row.sourceSnapshot!=source(acquisition,acquired))fail("取得依据与核收原件不一致")
        evidence.requireRead(row,voucher)
        row
    }

    fun bindCreated(row: AccessionReceiptDao, collectionId: String) {
        if(row.collectionId!=null && row.collectionId!=collectionId)fail("核收原件已经关联其他藏品，不能重复生成记录")
        row.collectionId=collectionId
        val collection=CollectionDao.findById(collectionId) ?: fail("待登记藏品记录不存在")
        if(collection.orgId!=row.orgId)fail("待登记记录与核收所属馆不一致")
        archives.seal(row.orgId,BIZ,row.id.value,"LINKED",mapOf("collectionId" to collectionId,"totalRegNo" to collection.totalRegNo,"content" to row.content),files(row.content))
    }

    /** 核收原件可以支持待登记阶段的馆内鉴选，不能以它代替正式定级。 */
    fun requireReceived(collection: CollectionDao): AccessionReceiptDao {
        val linked=AccessionReceiptDao.find { AccessionReceipts.collectionId eq collection.id.value }.sortedBy {it.version}
        val row=linked.lastOrNull() ?: fail("须有真实逐件核收及入馆凭证原件；历史纸质资料请按补录程序核实")
        val history=AccessionReceiptDao.find { AccessionReceipts.acquisitionId eq row.acquisitionId }.sortedBy {it.version}
        val voucher=voucher(row.voucherId)
        if(row.orgId!=collection.orgId || row.content.accepted!=true || collection.entryVoucherNo!=voucher.voucherNo)fail("藏品与实际核收凭证不一致")
        val acquisition=AcquisitionDao.findById(row.acquisitionId) ?: fail("原征集对象不存在")
        val original=acquisitionCompliance.requirePrepared(acquisition)
        if(acquisition.orgId!=collection.orgId || acquisition.collectionId!=collection.id.value || row.sourceSnapshot!=source(acquisition,original.actualAcquiredOn))fail("取得依据与核收原件不一致")
        if(history.map {it.version}!=(1..row.version).toList() || history.lastOrNull()?.id?.value!=row.id.value ||
            history.any {it.voucherId!=row.voucherId || it.orgId!=row.orgId ||
                it.sourceSnapshot!=row.sourceSnapshot ||
                (it.collectionId!=null && it.collectionId!=collection.id.value)})
            fail("核收订正版本链与原取得及入馆凭证不一致")
        history.forEach {evidence.requireRead(it,voucher)}
        return row
    }

    fun requireForRegistration(collection: CollectionDao, content: CollectionCatalogContentVo) {
        val row=requireReceived(collection)
        val acquisition=AcquisitionDao[row.acquisitionId]
        val original=acquisitionCompliance.requirePrepared(acquisition)
        if(content.quantity!=acquisition.quantity || content.unit!=acquisition.unit || content.quantity!=row.content.pieces.size)
            fail("登记件套数及单位须与征集和核收清册一致")
        val actual=AccessionRegistrationFacts.requireActualQuantity(row.content)
        if(content.actualQuantity!=actual.first || content.actualQuantityUnit!=actual.second)
            fail("实际数量及单位须与逐件核收原件一致，套数不能替代套内实际件数")
        if(content.entryDate!=AccessionRegistrationFacts.entryDate(row.content) || content.receiptCompletedAt!=row.content.checkedAt)
            fail("实收入馆日期及核收完成时间须与核收原件一致")
        if(content.warehouseId!=row.content.warehouseId)fail("初始登记库位须与实际接收库位一致，不能用编目审核代替实物移动")
        if(!content.acquireYear.isNullOrBlank() && content.acquireYear.trim()!=original.actualAcquiredOn.year.toString())fail("初始登记取得年度须与实际取得日期一致")
        if(!content.acquireDateRange.isNullOrBlank() && content.acquireDateRange.trim()!=original.actualAcquiredOn.toString())fail("已知实际取得日期的初始登记不能另填冲突的时间范围")
        if(content.source!=acquisition.source || content.nature!=original.nature || content.acquireDate!=original.actualAcquiredOn)fail("初始登记的来源、性质及实际取得日期须与核收和取得原件一致")
    }

    fun recordRegistration(collection: CollectionDao) {
        val linked=AccessionReceiptDao.find { AccessionReceipts.collectionId eq collection.id.value }.sortedBy {it.version}
        val row=linked.lastOrNull() ?: return
        archives.seal(row.orgId,BIZ,row.id.value,"REGISTERED",
            mapOf("collectionId" to collection.id.value,"totalRegNo" to collection.totalRegNo,
                "registeredAt" to collection.registeredAt, "historicalConfirmedAt" to collection.historicalConfirmedAt,
                "supersededReceiptIds" to linked.dropLast(1).map { it.id.value }),files(row.content))
        val voucher=voucher(row.voucherId,true)
        if(splitCollectionIds(voucher.acquisitionIds).all { id -> AcquisitionDao.findById(id)?.collectionId?.let { CollectionDao.findById(it)?.registeredAt }!=null })voucher.voucherStatus=EnumVoucherStatus.REGISTERED
    }

    private fun view(row: AccessionReceiptDao, verified: AccessionReceiptEvidence.Verified) = mapOf(
        "id" to row.id.value,"version" to row.version,"content" to row.content,"collectionId" to row.collectionId,
        "archives" to verified.archives,"legacyFileContentUnverified" to verified.legacyFileContentUnverified)
    private fun rows(voucherId: String)=AccessionReceiptDao.find { AccessionReceipts.voucherId eq voucherId }.sortedBy { it.version }
    private fun voucher(id: String,write: Boolean=false): EntryVoucherDao {
        val orgIds=if(write)scope.writableOrgIds() else scope.readableOrgIds()
        val row=(if(write)EntryVoucherDao.find { EntryVouchers.id eq id }.forUpdate().singleOrNull() else EntryVoucherDao.findById(id)) ?: fail("入馆凭证不存在")
        OrgScopedReadPolicy.requireVisible(orgIds,row.orgId,"入馆核收")
        return row
    }
    private fun source(acquisition: AcquisitionDao, acquired: LocalDate)=JsonUtils.toJson(mapOf("id" to acquisition.id.value,"orgId" to acquisition.orgId,"name" to acquisition.name,"quantity" to acquisition.quantity,"unit" to acquisition.unit,"era" to acquisition.era,"texture" to acquisition.texture,"completeness" to acquisition.completeness,"source" to acquisition.source,"nature" to acquisitionCompliance.requirePrepared(acquisition).nature,"actualAcquiredOn" to acquired))!!
    private fun files(value: AccessionReceiptVo)=(value.pieces.map { it.photoFileIds }+listOfNotNull(value.signedReceiptFileIds,value.lateHandlingFileIds,value.correctionFileIds)).joinToString(",")
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
