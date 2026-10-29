package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionDescriptionPolicy
import io.hizhengfu.cloud.collection.dao.AppraisalVo
import io.hizhengfu.cloud.collection.dao.AcquisitionVo
import io.hizhengfu.cloud.collection.dao.CollectionTransferVo
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.dao.DeregistrationVo
import io.hizhengfu.cloud.collection.dao.ExhibitionVo
import io.hizhengfu.cloud.collection.dao.InventoryVo
import io.hizhengfu.cloud.collection.dao.LoanVo
import io.hizhengfu.cloud.collection.dao.MovementVo
import io.hizhengfu.cloud.collection.dao.RequisitionVo
import io.hizhengfu.cloud.collection.dao.ReproductionVo
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.domain.EnumCollectionNature
import io.hizhengfu.cloud.collection.domain.EnumOwnership
import io.hizhengfu.cloud.collection.domain.CollectionPrintFieldVo
import io.hizhengfu.cloud.collection.domain.DimensionVo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.math.BigDecimal

object CollectionPrintFieldBuilder {
    private const val FLOW_BIZ_STATUS = "flow_biz_status"
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun field(label: String, value: String?, dictType: String? = null) =
        CollectionPrintFieldVo(label = label, value = value, dictType = dictType)

    fun attachment(label: String, value: String?): CollectionPrintFieldVo =
        CollectionPrintFieldVo(label = label, value = value,
            fileReferences = BusinessAttachmentBindingPlanner.plan("", label, value).map { it.fileId })

    fun attachments(vararg references: Pair<String, String?>): List<CollectionPrintFieldVo> =
        references.map { (label, value) -> attachment(label, value) }.filter { it.fileReferences.isNotEmpty() }

    private fun date(value: LocalDate?) = value?.toString()
    private fun dateTime(value: LocalDateTime?) = value?.format(dateTimeFormatter)
    private fun transferTransportEvidenceIncomplete(transfer: CollectionTransferVo): Boolean {
        if (transfer.executedAt == null) return false
        val dossier = transfer.dossier ?: return true
        val execution = transfer.execution ?: return true
        return listOf(dossier.transportStandardReference, dossier.transportStandardApplicability,
            dossier.transportCarrierName, dossier.transportCarrierCapabilityFileIds, dossier.transportPlanFileIds,
            execution.appliedTransportStandardReference, execution.packingCheckFileIds).any { it.isNullOrBlank() } ||
            execution.packingStandardConfirmed != true
    }
    private fun transferDesignationNotApplicable(transfer: CollectionTransferVo): Boolean =
        transfer.dossier?.succession?.ownership == EnumOwnership.NON_STATE ||
            transfer.baseline?.nature == EnumCollectionNature.OTHER
    private fun dimensions(dimensions: DimensionVo?): String? {
        dimensions ?: return null
        val fixed = listOfNotNull(
            dimensions.height?.takeIf(String::isNotBlank)?.let { "通高：$it" },
            dimensions.mouth?.takeIf(String::isNotBlank)?.let { "口径：$it" },
            dimensions.bottom?.takeIf(String::isNotBlank)?.let { "底径：$it" },
            dimensions.belly?.takeIf(String::isNotBlank)?.let { "腹径：$it" },
            dimensions.length?.takeIf(String::isNotBlank)?.let { "长：$it" },
            dimensions.width?.takeIf(String::isNotBlank)?.let { "宽：$it" },
            dimensions.diameter?.takeIf(String::isNotBlank)?.let { "直径：$it" },
        )
        val extra = dimensions.extra.orEmpty()
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .map { "${it.key}：${it.value}" }
        return (fixed + extra).joinToString("；").ifBlank { null }
    }

    fun collectionArchiveFields(collection: CollectionVo): List<CollectionPrintFieldVo> = listOf(
        field("总登记号", collection.totalRegNo),
        field("分类号", collection.classNo),
        field("辅助编号", collection.auxNo),
        field("名称", collection.name),
        field("原名", collection.originalName),
        field("文物分类", collection.categoryName ?: collection.categoryId),
        field("年代类型", CollectionDescriptionPolicy.eraMeanings[collection.registrationDetails?.eraMeaning]),
        field("年代表示方式", CollectionDescriptionPolicy.eraNotations[collection.registrationDetails?.eraNotation]),
        field("具体年代", collection.era),
        field("质地类别", collection.textureCategory1),
        field("质地细类", collection.textureCategory2),
        field("质地", collection.texture, CollectionConst.DICT_TEXTURE),
        field("级别", collection.level?.name, CollectionConst.DICT_RELIC_LEVEL),
        field("数量", listOfNotNull(collection.quantity?.toString(), collection.unit).joinToString(" ").ifBlank { null }),
        field("来源", collection.source?.name, CollectionConst.DICT_SOURCE),
        field("来源说明", collection.sourceDesc),
        field("取得时间范围", collection.acquireDateRange ?: date(collection.acquireDate)),
        field("取得年度", collection.acquireYear ?: collection.acquireDate?.year?.toString()),
        field("取得日期", date(collection.acquireDate)),
        field("完残程度", collection.completeness?.name, CollectionConst.DICT_COMPLETENESS),
        field("完残状况", collection.completeStatus),
        field("尺寸", CollectionRegistrationPrintFields.dimensions(CollectionCatalogMapper.fromCollection(collection))),
        field("质量范围", collection.weightRange),
        field("具体质量", CollectionRegistrationPrintFields.mass(CollectionCatalogMapper.fromCollection(collection))),
        field("质量单位", collection.weightUnit),
        field("地域", collection.region),
        field("容积", listOfNotNull(collection.volume, collection.volumeUnit).joinToString(" ").ifBlank { null }),
        field("色泽", collection.color),
        field("来源号", collection.sourceNo),
        field("国别", collection.country),
        field("人文类型", collection.culture),
        field("RFID标签", collection.rfidTag),
        field("入馆凭证号", collection.entryVoucherNo),
        field("退出凭证号", collection.deregVoucherNo),
        field("铭文款识", collection.inscription),
        field("特征描述", collection.feature),
        field("著者", collection.author),
        field("版本", collection.edition),
        field("存卷", collection.volumeNo),
        field("保存状态", collection.preserveState?.name, CollectionConst.DICT_PRESERVE),
        field("估价（元）", collection.valuation?.let { BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString() }),
        field("当前库位", collection.warehouseName ?: collection.warehouseId),
        field("编目状态", collection.catalogStatus?.name, CollectionConst.DICT_CATALOG_STATUS),
        field("藏品状态", collection.lifeStatus?.name, CollectionConst.DICT_LIFE_STATUS),
        field("登记时间", if (collection.historicalConfirmedAt != null && collection.registeredAt == null) "原账未载／待考" else dateTime(collection.registeredAt)),
        field("备注", collection.remark),
    )

    fun acquisitionFields(acquisition: AcquisitionVo): List<CollectionPrintFieldVo> = listOf(
        field("拟征集名称", acquisition.name),
        field("文物分类", acquisition.categoryId),
        field("年代", acquisition.era),
        field("质地", acquisition.texture, CollectionConst.DICT_TEXTURE),
        field("拟定级别", acquisition.proposedLevel?.name, CollectionConst.DICT_RELIC_LEVEL),
        field("征集方式", acquisition.source?.name, CollectionConst.DICT_SOURCE),
        field("数量", listOfNotNull(acquisition.quantity?.toString(), acquisition.unit).joinToString(" ").ifBlank { null }),
        field("尺寸", dimensions(acquisition.dimensions)),
        field("质量/重量", acquisition.weight),
        field("完残程度", acquisition.completeness?.name, CollectionConst.DICT_COMPLETENESS),
        field("完残状况", acquisition.completeStatus),
        field("持有人/卖方", acquisition.holder),
        field("拟征集价（元）", acquisition.proposedPrice?.let { BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString() }),
        field("评估价（元）", acquisition.appraisedValue?.let { BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString() }),
        field("征集理由", acquisition.reason),
        attachment("证据/影像", acquisition.images),
        attachment("专家鉴定意见表", acquisition.expertOpinionAttachmentIds),
        attachment("征集审批表/清单", acquisition.approvalAttachmentIds),
        attachment("征集合同", acquisition.contractAttachmentIds),
        attachment("发票", acquisition.invoiceAttachmentIds),
        field("登账藏品", acquisition.collectionId),
        field("流程状态", acquisition.flowStatus?.name, FLOW_BIZ_STATUS),
    )

    fun transferFields(transfer: CollectionTransferVo): List<CollectionPrintFieldVo> = listOf(
        field("源收藏单位", transfer.fromOrgId),
        field("目标收藏单位", transfer.toOrgId),
        field("调拨事由", transfer.reason),
        field("批文号", transfer.approvalDocNo),
        field("接收馆入馆凭证号", transfer.id?.takeIf { transfer.executedAt != null }),
        attachment("附件", transfer.attachmentIds),
        *attachments("交换协议原件" to transfer.dossier?.exchangeAgreementFileIds).toTypedArray(),
        *attachments("捐赠协议原件" to transfer.dossier?.donationAgreementFileIds).toTypedArray(),
        *attachments("捐赠人通知原件" to transfer.dossier?.donorNoticeFileIds).toTypedArray(),
        *attachments("捐赠人书面同意原件" to transfer.dossier?.donorWrittenConsentFileIds).toTypedArray(),
        *attachments("省级管辖依据" to transfer.dossier?.provincialJurisdictionFileIds).toTypedArray(),
        *attachments("转移法律依据" to transfer.dossier?.legalBasisFileIds).toTypedArray(),
        field("转移类别", transfer.dossier?.kind?.let { when(it.name) { "STATE_EXCHANGE" -> "国有馆藏文物交换"; "STATE_ALLOCATION" -> "国有馆藏文物调拨"; "INSTITUTION_SUCCESSION" -> "博物馆终止或合并承接"; else -> "其他依法转移" } }),
        field("源馆保管负责人", transfer.dossier?.sourceKeeperUserId),
        field(if(transfer.dossier?.succession!=null) "源馆清算或合并依法授权人员" else "源馆主要负责人", transfer.dossier?.sourceLeaderUserId),
        field("接收馆保管负责人", transfer.dossier?.targetKeeperUserId),
        field("接收馆主要负责人", transfer.dossier?.targetLeaderUserId),
        field("接收库位", transfer.dossier?.targetWarehouseId),
        *(if (transfer.dossier?.succession != null) listOf(
        field("终止合并事项", transfer.dossier.succession?.eventKind?.let { if(it.name=="TERMINATION") "终止" else "合并" }),
        field("机构决定文号", transfer.dossier.succession?.eventDocumentNo),
        field("机构决定日期", date(transfer.dossier.succession?.decidedOn)),
        field("决定生效安排", date(transfer.dossier.succession?.effectiveOn)),
        attachment("机构决定原件", transfer.dossier.succession?.eventFileIds),
        field("非营利组织处置依据", transfer.dossier.succession?.nonprofitLegalBasis),
        *attachments("非营利处置法律原件" to transfer.dossier.succession?.legalBasisFileIds).toTypedArray(),
        attachment("清算或合并处置方案", transfer.dossier.succession?.liquidationOrMergerPlanFileIds),
        *attachments("完整清点清册原件" to transfer.dossier.succession?.inventoryFileIds).toTypedArray(),
        field("本件权属", transfer.dossier.succession?.ownership?.let { if(it.name=="STATE") "国家所有" else "非国家所有" }),
        attachment("逐件权属原件", transfer.dossier.succession?.propertyBasisFileIds),
        field("公益用途", transfer.dossier.succession?.publicBenefitPurpose),
        field("第三方权利处理", transfer.dossier.succession?.thirdPartyRightsConclusion),
        *attachments("第三方权利处理原件" to transfer.dossier.succession?.thirdPartyRightsFileIds).toTypedArray(),
        field("权利事项已依法依约处理", transfer.dossier.succession?.thirdPartyRightsResolved?.let { if(it) "是" else "否" }),
        attachment("依法授权原件", transfer.dossier.succession?.representativeAuthorizationFileIds),
        ) else emptyList()).toTypedArray(),
        *(if (transfer.dossier?.succession != null &&
            (!transferDesignationNotApplicable(transfer) || transfer.dossier?.succession?.designation != null)) listOf(
        field("省级指定部门", transfer.dossier.succession?.designation?.authorityName),
        field("省级指定文号", transfer.dossier.succession?.designation?.documentNo),
        field("省级指定接收馆", transfer.dossier.succession?.designation?.targetMuseumName),
        field("省级指定原总登记号", transfer.dossier.succession?.designation?.totalRegNo),
        attachment("省级指定决定原件", transfer.dossier.succession?.designation?.fileIds),
        ) else if (transfer.dossier?.succession != null) listOf(
            field("省级指定适用性", "本件不属于国家所有可移动文物，省级文物指定决定字段不适用"),
        ) else emptyList()).toTypedArray(),
        field("源馆纸质账对应", transfer.dossier?.sourcePaperLedgerRef),
        field("接收馆纸质账对应", transfer.dossier?.targetPaperLedgerRef),
        field("整组交换申请", transfer.dossier?.exchangeTransferIds?.joinToString("、")),
        attachment("档案交接资料", transfer.dossier?.archiveHandoverFileIds),
        field("包装运输标准及版本", transfer.dossier?.transportStandardReference),
        field("标准适用核对依据", transfer.dossier?.transportStandardApplicability),
        field("承运馆或机构", transfer.dossier?.transportCarrierName),
        attachment("承运资信能力原件", transfer.dossier?.transportCarrierCapabilityFileIds),
        attachment("包装运输方案原件", transfer.dossier?.transportPlanFileIds),
        field("实际采用运输标准", transfer.execution?.appliedTransportStandardReference),
        field("包装标准核对完成", transfer.execution?.packingStandardConfirmed?.let { if(it) "是" else "否" }),
        attachment("逐件包装核验原件", transfer.execution?.packingCheckFileIds),
        *(if (transferTransportEvidenceIncomplete(transfer)) listOf(
            field("包装运输材料状态", "现存方案或交接材料待核实；已交接状态不能证明包装运输材料齐备"),
        ) else emptyList()).toTypedArray(),
        field("实际交接日期", transfer.execution?.handedOn?.toString()),
        field("点交状况", transfer.execution?.conditionDescription),
        *attachments("点交状况影像" to transfer.execution?.conditionImageFileIds).toTypedArray(),
        attachment("签署交接单", transfer.execution?.signedHandoverFileIds),
        attachment("签署变更表", transfer.execution?.signedLedgerChangeFileIds),
        attachment("接收总账凭据", transfer.execution?.receivingLedgerReceiptFileIds),
        field("过户时间", dateTime(transfer.executedAt)),
        field("结果公告归档", dateTime(transfer.announcedAt)),
        field("流程状态", transfer.flowStatus?.name, FLOW_BIZ_STATUS),
    )

    fun appraisalFields(appraisal: AppraisalVo): List<CollectionPrintFieldVo> = listOf(
        field("鉴定性质", "馆内鉴定意见；文物行政定级、备案和正式信息变更按相应程序另行办理"),
        field("鉴定定名", appraisal.resultName),
        field("断代", appraisal.resultEra),
        field("质地", appraisal.resultTexture, CollectionConst.DICT_TEXTURE),
        field("建议级别", appraisal.resultLevel?.name, CollectionConst.DICT_RELIC_LEVEL),
        field("鉴定委员会", appraisal.committee),
        field("鉴定结论", appraisal.conclusion),
        field("驳回原因", appraisal.rejectReason),
        attachment("附件", appraisal.attachmentIds),
        field("流程状态", appraisal.flowStatus?.name, FLOW_BIZ_STATUS),
        field("馆内鉴定完成时间", dateTime(appraisal.appraisedAt)),
    ) + appraisal.members.flatMapIndexed { i, member -> listOf(
        field("鉴定成员 ${i+1}", listOfNotNull(member.memberName, member.organization, member.professionalTitle, member.specialty).joinToString("；")),
        field("成员 ${i+1} 意见", member.opinion),
        attachment("成员 ${i+1} 签名原件", member.signatureFileId),
    ) }

    // restorationFields 已迁移至 module-collection-restoration.service.RestorationPrintFieldBuilder

    fun reproductionFields(reproduction: ReproductionVo): List<CollectionPrintFieldVo> = listOf(
        field("藏品", reproduction.collectionId),
        field("复制类型", reproduction.repType?.name, CollectionConst.DICT_REP_TYPE),
        field("用途", reproduction.purpose),
        attachment("产物影像", reproduction.images),
        field("采集日期", date(reproduction.repDate)),
        field("流程状态", reproduction.flowStatus?.name, FLOW_BIZ_STATUS),
    )

    fun movementFields(
        movement: MovementVo,
        fromWarehouseLabel: String? = movement.fromWarehouseId,
        toWarehouseLabel: String? = movement.toWarehouseId,
    ): List<CollectionPrintFieldVo> = listOf(
        field("出入库类型", movement.type?.name, CollectionConst.DICT_MOVE_TYPE),
        field("事由", movement.reason?.name, CollectionConst.DICT_MOVE_REASON),
        field("依据", movement.basis),
        field("部门", movement.department),
        field("原库位", fromWarehouseLabel),
        field("目标库位", toWarehouseLabel),
        field("经办人", movement.operator),
        field("复核人", movement.reviewer),
        field("保管员", movement.custodian),
        field("交接人", movement.handoverUser),
        field("审核/验收意见", movement.reviewOpinion),
        attachment("附件", movement.attachmentIds),
        field("操作时间", dateTime(movement.operatedAt)),
    )

    fun requisitionFields(req: RequisitionVo): List<CollectionPrintFieldVo> = listOf(
        field("来源重建后本提用已终止，新来源项目", req.sourceRetirement?.context?.replacementSourceId),
        field("原提用执行已终止，后续须重新审批", req.supersededById),
        field("责任变更所依据的原提用单", req.replacesId),
        field("提用事由", req.purpose?.name, CollectionConst.DICT_MOVE_REASON),
        field("提用依据", req.basis),
        field("提用部门", req.department),
        field("经办人", req.operator),
        field("复核人", req.reviewer),
        field("保管员", req.custodian),
        field("交接人", req.handoverUser),
        field("审核/验收意见", req.reviewOpinion),
        attachment("附件", req.attachmentIds),
        field("出库前完残状况", req.conditionNote),
        attachment("多角度状况照片", req.conditionPhotoIds),
        attachment("签署出库凭证", req.signedVoucherAttachmentIds),
        field("现场出库状态", req.outExecuted.toString()),
        field("现场出库时间", dateTime(req.outExecutedTime)),
        field("预计归还日期", date(req.expectReturnDate)),
        field("实际归还日期", date(req.returnDate)),
        field("是否归还", req.returned.toString(), CollectionConst.DICT_RETURNED),
    ) + req.dossier?.let { d -> listOf(
        field("提用部门负责人",d.departmentHeadUserId), field("保管部门负责人",d.keeperUserId),
        field("法定代表人或主要负责人",d.leaderUserId), field("使用安全责任人",d.useResponsibleUserId),
        field("使用方式",d.usageMethod),field("具体使用目的",d.purposeDescription),field("实际使用地点",d.usageSite),
        field("使用和保管安全要求",d.safetyRequirements),attachment("使用与安全方案原件", d.safetyPlanFileIds),
        field("是否需要包装运输",d.transportRequired?.let { if(it) "是" else "否" }),
        field("包装运输判断依据",d.transportDecisionReason),field("适用包装运输标准及版本",d.transportStandardReference),
        field("标准适用依据",d.transportStandardApplicability),field("承运馆或机构",d.transportCarrierName),
        attachment("承运能力依据", d.transportCarrierCapabilityFileIds),attachment("包装运输方案原件", d.transportPlanFileIds),
        field("预计出库时间",dateTime(d.expectedOutAt)),field("预计归库时间",dateTime(d.expectedReturnAt)),
        field("归库保管位置",req.toWarehouseId),
    ) }.orEmpty() + req.handover?.content?.let { h -> listOf(
        field("保管负责人审查时间",dateTime(h.reviewedAt)),field("审查并指派人员的负责人",h.reviewerUserId),
        attachment("审查和指派原件", h.reviewAndAssignmentFileIds),field("实际点交时间",dateTime(h.handedAt)),
        field("实际点交保管人员",h.custodianUserIds.joinToString("、")),field("实际接收提用人员",h.receivingUserIds.joinToString("、")),
        field("安全防护措施已落实",if(h.safetyMeasuresConfirmed) "是" else "否"),field("场所具备安全条件",if(h.siteSafetyConfirmed) "是" else "否"),
        attachment("安全条件核实依据", h.safetyCheckFileIds),attachment("双方签署逐件点交单", h.signedHandoverFileIds),
        field("实际采用包装运输标准",h.appliedTransportStandardReference),
        field("逐件包装核验确认",h.packingStandardConfirmed?.let { if(it) "是" else "否" }),
        attachment("签署包装核验原件", h.packingCheckFileIds),
    ) + h.items.flatMap { item ->
        val name=req.baseline?.items?.find { it.collectionId==item.collectionId }?.content?.formalName ?: item.collectionId
        listOf(field("$name：点交保存现状",item.condition),field("$name：文字需影像补充",if(item.textNeedsImages) "是" else "否"),attachment("$name：点交影像", item.imageFileIds))
    } }.orEmpty()

    fun inventoryFields(inventory: InventoryVo): List<CollectionPrintFieldVo> = listOf(
        field("盘点批次", inventory.batchName),
        field("盘点结果", inventory.result?.name, CollectionConst.DICT_INVENTORY_RESULT),
        field("备注", inventory.note),
        attachment("附件", inventory.attachmentIds),
        field("盘点时间", dateTime(inventory.checkedAt)),
        field("流程状态", inventory.flowStatus?.name, FLOW_BIZ_STATUS),
    )

    fun exhibitionFields(exhibition: ExhibitionVo): List<CollectionPrintFieldVo> = listOf(
        field("展览名称", exhibition.title),
        field("展览编号", exhibition.exhibitionNo),
        field("地点", exhibition.venue),
        field("主办/承办", exhibition.organizer),
        field("开展日期", date(exhibition.startDate)),
        field("结束日期", date(exhibition.endDate)),
        attachment("封面图", exhibition.coverImage),
        attachment("附件", exhibition.attachmentIds),
        field("状态", exhibition.state?.name, CollectionConst.DICT_PROCESS_STATE),
        field("流程状态", exhibition.flowStatus?.name, FLOW_BIZ_STATUS),
        field("备注", exhibition.remark),
    )

    fun loanHandoverFields(loan: LoanVo): List<CollectionPrintFieldVo> = if (loan.dossier != null) LoanCompliancePrintFields.build(loan) else listOf(
        field("展览/事由", loan.title),
        field("借入方", loan.borrower),
        field("协议号", loan.protocolNo),
        field("审批层级", loan.approveLevel?.name, CollectionConst.DICT_APPROVE_LEVEL),
        field("出馆批文号", loan.approvalDocNo),
        field("保险", loan.insurance),
        field("借出日期", date(loan.loanDate)),
        field("预计/实际返还日期", date(loan.returnDate)),
        field("返还状态", loan.returned.toString(), CollectionConst.DICT_RETURNED),
        field("经办人", loan.operator),
        field("复核人", loan.reviewer),
        attachment("附件", loan.attachmentIds),
        field("出库前完残状况", loan.conditionNote),
        attachment("多角度状况照片", loan.conditionPhotoIds),
        attachment("签署出库凭证", loan.signedVoucherAttachmentIds),
        field("现场出库状态", loan.outExecuted.toString()),
        field("现场出库时间", dateTime(loan.outExecutedTime)),
    )

    fun deregistrationFields(deregistration: DeregistrationVo): List<CollectionPrintFieldVo> = if (deregistration.dossier != null) statutoryDeregistrationFields(deregistration) else listOf(
        field("退出馆藏原因", deregistration.reason?.name, CollectionConst.DICT_DEREG_REASON),
        field("审批层级", deregistration.approveLevel?.name, CollectionConst.DICT_APPROVE_LEVEL),
        field("批文号", deregistration.approvalDocNo),
        field("专家评委会意见", deregistration.expertOpinion),
        field("最终审批人", deregistration.approvedBy),
        field("退出馆藏后去向", deregistration.destination),
        field("退出馆藏说明", deregistration.detail),
        field("流程状态", deregistration.flowStatus?.name, FLOW_BIZ_STATUS),
        attachment("附件", deregistration.attachmentIds),
    )

    private fun statutoryDeregistrationFields(record: DeregistrationVo): List<CollectionPrintFieldVo> {
        val dossier=record.dossier!!
        return listOf(
            *attachments("其他退出原件" to record.attachmentIds).toTypedArray(),
            field("法定退出情形",record.reason?.name,CollectionConst.DICT_DEREG_REASON),
            field("逐件退出理由",record.detail),field("方案实际形成日期",dossier.proposedOn?.toString()),
            field("处置去向",when(dossier.destinationType?.name) { "AUXILIARY_ACCOUNT" -> "纳入辅助账册"; "STATE_TEACHING_RESEARCH" -> "交国有教学及科研单位保管使用"; "LOST" -> "不可抗力灭失"; else -> "依适用法律法规处置" }),
            *attachments("接收单位国有性质依据" to dossier.recipientStateEvidenceFileIds).toTypedArray(),
            *attachments("退出法律依据" to dossier.legalBasisFileIds).toTypedArray(),
            *attachments("退出处置方案原件" to dossier.dispositionPlanFileIds).toTypedArray(),
            field("接收单位",dossier.recipient),field("辅助账编号与保管位置",listOfNotNull(dossier.auxiliaryRecordNo,dossier.auxiliaryLocation).joinToString(" / ")),
            field("保管部门负责人",dossier.keeperUserId),field("法定代表人或主要负责人",dossier.leaderUserId),
            field("流程状态",record.flowStatus?.name,FLOW_BIZ_STATUS),field("实际退出时间",dateTime(record.executedAt)),field("账物处置公告完成时间",dateTime(record.announcedAt)),
        ) + record.experts.flatMapIndexed { index,expert -> listOf(
            field("专家${index+1}身份", "${expert.expertName} / ${expert.organization} / ${expert.professionalTitle} / ${expert.specialty}"),
            field("专家${index+1}逐件独立意见", "${expert.assessedAt} / ${if(expert.agree) "同意退出" else "不同意退出"} / ${expert.opinion}"),
            attachment("专家${index+1}职称依据", expert.seniorQualificationFileIds),
            attachment("专家${index+1}独立声明", expert.independenceStatementFileIds),
            attachment("专家${index+1}签署意见原件", expert.signedOpinionFileIds),
        ) } + record.notices.flatMapIndexed { index,notice -> listOf(field("公示公告${index+1}",
            "${if(notice.kind.name=="RESULT") "退出结果公告" else "退出前公示"} / ${when(notice.channel.name) { "AUTHORITY_WEBSITE" -> "主管文物部门官网"; "MUSEUM_ONSITE" -> "博物馆现场"; else -> "博物馆官网" }} / ${notice.startedOn} 至 ${notice.endedOn ?: "持续中"} / ${notice.urlOrLocation}")
            , attachment("公示公告${index+1}发布正文", notice.publicContentFileIds), attachment("公示公告${index+1}发布凭据", notice.publicationEvidenceFileIds))
        } + record.administrativeEvidence.flatMapIndexed { index,document -> listOf(
            field("行政文书${index+1}","${document.authorityName} / ${document.documentNo} / ${if(document.action.name=="APPROVAL") "批准" else "备案"} / ${document.issuedAt}"),
            attachment("行政文书${index+1}原件", document.fileIds)) }
    }

}
