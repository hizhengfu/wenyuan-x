package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.LocalDate

/** 核收清册一行代表一件或一套；实际数量来自签署清册的明确记载，不按行数推断。 */
object AccessionRegistrationFacts {
    fun actualQuantity(receipt: AccessionReceiptVo): Pair<Int, String>? {
        if (receipt.pieces.isEmpty() || receipt.pieces.any { (it.actualQuantity ?: 0) <= 0 || it.actualQuantityUnit.isNullOrBlank() }) return null
        val units = receipt.pieces.map { it.actualQuantityUnit!!.trim() }.distinct()
        if (units.size != 1 || units.single() == "套") return null
        val total = receipt.pieces.sumOf { it.actualQuantity!!.toLong() }
        if (total > Int.MAX_VALUE) return null
        return total.toInt() to units.single()
    }

    fun requireActualQuantity(receipt: AccessionReceiptVo): Pair<Int, String> = actualQuantity(receipt)
        ?: throw BadRequestException("须凭核收签署原件逐件或逐套记录正整数实际数量及一致单位；历史缺项请补录原件")

    /** 同一登记对象分批接收时为最后一件实际接收日，逐件日期仍完整保留在核收清册。 */
    fun entryDate(receipt: AccessionReceiptVo): LocalDate = receipt.pieces.maxOfOrNull { it.receivedAt.toLocalDate() }
        ?: throw BadRequestException("无逐件实收事实，不能生成实收入馆日期")
}
