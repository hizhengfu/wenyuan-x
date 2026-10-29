package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException

/** 藏品生命周期状态机，所有业务状态变更必须经过这里。 */
object CollectionLifecyclePolicy {
    private val allowed = mapOf(
        EnumCollectionStatus.PENDING to setOf(EnumCollectionStatus.IN_STORE, EnumCollectionStatus.DEREGISTERED),
        EnumCollectionStatus.IN_STORE to setOf(
            EnumCollectionStatus.PRE_OUT,
            EnumCollectionStatus.IN_USE,
            EnumCollectionStatus.RESTORING,
            EnumCollectionStatus.ON_LOAN,
            EnumCollectionStatus.MISSING,
            EnumCollectionStatus.DEREGISTERED,
        ),
        EnumCollectionStatus.IN_USE to setOf(EnumCollectionStatus.RETURN_HOLD, EnumCollectionStatus.IN_STORE, EnumCollectionStatus.MISSING, EnumCollectionStatus.DEREGISTERED),
        EnumCollectionStatus.RESTORING to setOf(EnumCollectionStatus.IN_STORE, EnumCollectionStatus.MISSING, EnumCollectionStatus.DEREGISTERED),
        EnumCollectionStatus.ON_LOAN to setOf(EnumCollectionStatus.RETURN_HOLD, EnumCollectionStatus.IN_STORE, EnumCollectionStatus.MISSING, EnumCollectionStatus.DEREGISTERED),
        EnumCollectionStatus.MISSING to setOf(EnumCollectionStatus.IN_STORE, EnumCollectionStatus.DEREGISTERED),
        EnumCollectionStatus.DEREGISTERED to emptySet(),
        EnumCollectionStatus.RETURN_HOLD to setOf(EnumCollectionStatus.IN_STORE),
        EnumCollectionStatus.PRE_OUT to setOf(
            EnumCollectionStatus.IN_STORE,
            EnumCollectionStatus.IN_USE,
            EnumCollectionStatus.ON_LOAN,
            EnumCollectionStatus.MISSING,
            EnumCollectionStatus.DEREGISTERED,
        ),
    )

    fun canTransition(from: EnumCollectionStatus, to: EnumCollectionStatus): Boolean =
        from == to || allowed[from].orEmpty().contains(to)

    fun requireTransition(from: EnumCollectionStatus, to: EnumCollectionStatus) {
        if (from == EnumCollectionStatus.DEREGISTERED && to != EnumCollectionStatus.DEREGISTERED) {
            throw BadRequestException("藏品已退出馆藏，不能再变更生命周期状态")
        }
        if (!canTransition(from, to)) {
            throw BadRequestException("生命周期状态不允许从 ${from.name} 变更为 ${to.name}")
        }
    }
}
