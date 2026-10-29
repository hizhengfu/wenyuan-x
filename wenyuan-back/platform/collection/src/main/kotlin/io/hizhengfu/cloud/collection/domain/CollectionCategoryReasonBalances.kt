package io.hizhengfu.cloud.collection.domain

/** 按发生时的类别、来源和数量归集；更正跨类别/单位时分别减旧增新，保持勾稽关系。 */
object CollectionCategoryReasonBalances {
    fun reason(kind: EnumLedgerStatEvent, source: EnumSource?): EnumLedgerChangeReason? = when (kind) {
        EnumLedgerStatEvent.REGISTER -> when (source) {
            EnumSource.HANDOVER -> EnumLedgerChangeReason.HANDOVER
            EnumSource.ALLOCATE, EnumSource.TRANSFER -> EnumLedgerChangeReason.TRANSFER_IN
            EnumSource.DONATE -> EnumLedgerChangeReason.DONATE
            EnumSource.EXCHANGE -> EnumLedgerChangeReason.EXCHANGE
            null -> null
            else -> EnumLedgerChangeReason.OTHER_IN
        }
        EnumLedgerStatEvent.TRANSFER_OUT -> EnumLedgerChangeReason.TRANSFER_OUT
        else -> null
    }

    fun deregistrationReason(reason: EnumDeregReason?): EnumLedgerChangeReason? = when (reason) {
        EnumDeregReason.DAMAGE, EnumDeregReason.DAMAGE_NO_VALUE -> EnumLedgerChangeReason.DAMAGE
        EnumDeregReason.TRANSFER, EnumDeregReason.STATE_EXCHANGE_TRANSFER -> EnumLedgerChangeReason.TRANSFER_OUT
        null -> null
        else -> EnumLedgerChangeReason.OTHER_OUT
    }

    fun build(
        opening: List<LedgerStatItemVo>, closing: List<LedgerStatItemVo>, events: List<AnnualLedgerEventVo>,
        changes: List<Pair<LedgerStatItemVo, LedgerStatItemVo>>,
    ): List<CategoryReasonBalanceVo> {
        val all = opening + closing + events.map { it.item } + changes.flatMap { listOf(it.first, it.second) }
        return all.map { it.categoryId to it.unit }.distinct().sortedWith(compareBy({ it.first }, { it.second })).map { (category, unit) ->
            fun belongs(item: LedgerStatItemVo) = item.categoryId == category && item.unit == unit
            fun amount(items: List<LedgerStatItemVo>) = items.filter(::belongs).sumOf { it.quantity }
            val increases = events.filter { it.kind in setOf(EnumLedgerStatEvent.REGISTER, EnumLedgerStatEvent.TRANSFER_IN) && belongs(it.item) }
            val decreases = events.filter { it.kind in setOf(EnumLedgerStatEvent.DEREGISTER, EnumLedgerStatEvent.TRANSFER_OUT) && belongs(it.item) }
            fun sum(rows: List<AnnualLedgerEventVo>, expected: EnumLedgerChangeReason?) = rows.filter {
                (it.item.changeReason ?: reason(it.kind, it.item.source)) == expected
            }.sumOf { it.item.quantity }
            CategoryReasonBalanceVo(category, all.lastOrNull { it.categoryId == category && !it.categoryName.isNullOrBlank() }?.categoryName, unit,
                amount(opening), sum(increases, EnumLedgerChangeReason.HANDOVER), sum(increases, EnumLedgerChangeReason.TRANSFER_IN),
                sum(increases, EnumLedgerChangeReason.DONATE), sum(increases, EnumLedgerChangeReason.EXCHANGE), sum(increases, EnumLedgerChangeReason.OTHER_IN),
                sum(increases, null), increases.sumOf { it.item.quantity }, sum(decreases, EnumLedgerChangeReason.DAMAGE),
                sum(decreases, EnumLedgerChangeReason.TRANSFER_OUT), sum(decreases, EnumLedgerChangeReason.OTHER_OUT),
                sum(decreases, null), decreases.sumOf { it.item.quantity },
                changes.sumOf { (before, after) -> (if (belongs(after)) after.quantity else 0L) - (if (belongs(before)) before.quantity else 0L) }, amount(closing))
        }
    }
}
