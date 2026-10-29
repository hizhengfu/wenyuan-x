package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException

/** 藏品登记与编目状态保护，避免已进入业务流程的档案被普通编辑/复审改写。 */
object CollectionRecordPolicy {
    private val activeStatuses = setOf(
        EnumCollectionStatus.IN_USE,
        EnumCollectionStatus.RESTORING,
        EnumCollectionStatus.ON_LOAN,
        EnumCollectionStatus.PRE_OUT,
        EnumCollectionStatus.RETURN_HOLD,
        EnumCollectionStatus.MISSING,
        EnumCollectionStatus.DEREGISTERED,
    )

    fun requireEditable(catalogStatus: EnumCatalogStatus, lifeStatus: EnumCollectionStatus) {
        if (catalogStatus == EnumCatalogStatus.SUBMITTED || catalogStatus == EnumCatalogStatus.PENDING) {
            throw BadRequestException("编目已提交审核，普通编辑已锁定")
        }
        if (catalogStatus == EnumCatalogStatus.APPROVED) {
            throw BadRequestException("编目已审核通过，普通编辑已锁定；关键字段请走总账更正")
        }
        if (lifeStatus in activeStatuses) {
            throw BadRequestException("藏品当前状态[$lifeStatus]不允许编辑登记信息")
        }
    }

    fun requireCatalogAuditable(catalogStatus: EnumCatalogStatus, lifeStatus: EnumCollectionStatus) {
        if (catalogStatus != EnumCatalogStatus.SUBMITTED && catalogStatus != EnumCatalogStatus.PENDING) {
            throw BadRequestException("仅已提交的编目可以审核")
        }
        if (lifeStatus in activeStatuses) {
            throw BadRequestException("藏品当前状态[$lifeStatus]不允许进行编目审核")
        }
    }

    /** 驳回只改变编目状态，不把已经办理入馆的藏品从在库状态退回待定。 */
    fun lifeStatusAfterCatalogDecision(
        current: EnumCollectionStatus,
        decision: EnumCatalogStatus,
    ): EnumCollectionStatus = when (decision) {
        EnumCatalogStatus.APPROVED -> EnumCollectionStatus.IN_STORE
        EnumCatalogStatus.REJECTED -> current
        EnumCatalogStatus.PENDING,
        EnumCatalogStatus.DRAFT,
        EnumCatalogStatus.SUBMITTED -> throw BadRequestException("草稿或待审核状态不是审核结论")
    }
}
