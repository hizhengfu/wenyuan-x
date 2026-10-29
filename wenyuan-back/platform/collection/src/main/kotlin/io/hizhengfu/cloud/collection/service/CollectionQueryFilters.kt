package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumInventoryResult
import io.hizhengfu.cloud.collection.domain.EnumLedgerAction
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.collection.domain.EnumApproveLevel
import io.hizhengfu.cloud.collection.domain.EnumProcessState
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

internal fun hasTextAttachment(value: String?): Boolean = !value.isNullOrBlank()

private fun param(get: (String) -> String?, key: String): String? =
    get(key)?.trim()?.takeIf { it.isNotBlank() }

private inline fun <reified T : Enum<T>> enumParam(noinline get: (String) -> String?, key: String): T? =
    param(get, key)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

private fun boolParam(get: (String) -> String?, key: String): Boolean? =
    param(get, key)?.toBooleanStrictOrNull()

internal fun parseCollectionDateParam(value: String?): LocalDate? {
    val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { LocalDate.parse(clean) }.getOrNull()
        ?: runCatching { Instant.ofEpochMilli(clean.toLong()).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
}

private fun dateParam(get: (String) -> String?, key: String): LocalDate? =
    parseCollectionDateParam(param(get, key))

private fun startOfDayParam(get: (String) -> String?, key: String): LocalDateTime? =
    dateParam(get, key)?.atStartOfDay()

private fun endOfDayParam(get: (String) -> String?, key: String): LocalDateTime? =
    dateParam(get, key)?.plusDays(1)?.atStartOfDay()?.minusNanos(1)

internal data class RequisitionQueryFilters(
    val keyword: String? = null,
    val sourceBizType: String? = null,
    val sourceBizId: String? = null,
    val collectionId: String? = null,
    val documentNo: String? = null,
    val purpose: EnumMoveReason? = null,
    val basis: String? = null,
    val department: String? = null,
    val operator: String? = null,
    val reviewer: String? = null,
    val flowStatus: EnumFlowStatus? = null,
    val returned: Boolean? = null,
    val hasAttachment: Boolean? = null,
    val expectReturnDateStart: LocalDate? = null,
    val expectReturnDateEnd: LocalDate? = null,
    val createTimeStart: LocalDateTime? = null,
    val createTimeEnd: LocalDateTime? = null,
) {
    companion object {
        fun from(get: (String) -> String?) = RequisitionQueryFilters(
            keyword = param(get, "keyword"),
            sourceBizType = param(get, "sourceBizType"),
            sourceBizId = param(get, "sourceBizId"),
            collectionId = param(get, "collectionId"),
            documentNo = param(get, "documentNo"),
            purpose = enumParam(get, "purpose"),
            basis = param(get, "basis"),
            department = param(get, "department"),
            operator = param(get, "operator"),
            reviewer = param(get, "reviewer"),
            flowStatus = enumParam(get, "flowStatus"),
            returned = boolParam(get, "returned"),
            hasAttachment = boolParam(get, "hasAttachment"),
            expectReturnDateStart = dateParam(get, "expectReturnDateStart"),
            expectReturnDateEnd = dateParam(get, "expectReturnDateEnd"),
            createTimeStart = startOfDayParam(get, "createTimeStart"),
            createTimeEnd = endOfDayParam(get, "createTimeEnd"),
        )
    }
}

internal data class LoanQueryFilters(
    val keyword: String? = null,
    val collectionId: String? = null,
    val title: String? = null,
    val borrower: String? = null,
    val protocolNo: String? = null,
    val approveLevel: EnumApproveLevel? = null,
    val approvalDocNo: String? = null,
    val operator: String? = null,
    val reviewer: String? = null,
    val flowStatus: EnumFlowStatus? = null,
    val returned: Boolean? = null,
    val hasAttachment: Boolean? = null,
    val loanDateStart: LocalDate? = null,
    val loanDateEnd: LocalDate? = null,
) {
    companion object {
        fun from(get: (String) -> String?) = LoanQueryFilters(
            keyword = param(get, "keyword"),
            collectionId = param(get, "collectionId"),
            title = param(get, "title"),
            borrower = param(get, "borrower"),
            protocolNo = param(get, "protocolNo"),
            approveLevel = enumParam(get, "approveLevel"),
            approvalDocNo = param(get, "approvalDocNo"),
            operator = param(get, "operator"),
            reviewer = param(get, "reviewer"),
            flowStatus = enumParam(get, "flowStatus"),
            returned = boolParam(get, "returned"),
            hasAttachment = boolParam(get, "hasAttachment"),
            loanDateStart = dateParam(get, "loanDateStart"),
            loanDateEnd = dateParam(get, "loanDateEnd"),
        )
    }
}

internal data class InventoryQueryFilters(
    val keyword: String? = null,
    val batchName: String? = null,
    val collectionId: String? = null,
    val result: EnumInventoryResult? = null,
    val flowStatus: EnumFlowStatus? = null,
    val hasAttachment: Boolean? = null,
    val checkedAtStart: LocalDateTime? = null,
    val checkedAtEnd: LocalDateTime? = null,
) {
    companion object {
        fun from(get: (String) -> String?) = InventoryQueryFilters(
            keyword = param(get, "keyword"),
            batchName = param(get, "batchName"),
            collectionId = param(get, "collectionId"),
            result = enumParam(get, "result"),
            flowStatus = enumParam(get, "flowStatus"),
            hasAttachment = boolParam(get, "hasAttachment"),
            checkedAtStart = startOfDayParam(get, "checkedAtStart"),
            checkedAtEnd = endOfDayParam(get, "checkedAtEnd"),
        )
    }
}

internal data class MovementQueryFilters(
    val keyword: String? = null,
    val collectionId: String? = null,
    val documentNo: String? = null,
    val type: EnumMoveType? = null,
    val reason: EnumMoveReason? = null,
    val operator: String? = null,
    val reviewer: String? = null,
    val department: String? = null,
    val hasAttachment: Boolean? = null,
    val operatedAtStart: LocalDateTime? = null,
    val operatedAtEnd: LocalDateTime? = null,
) {
    companion object {
        fun from(get: (String) -> String?) = MovementQueryFilters(
            keyword = param(get, "keyword"),
            collectionId = param(get, "collectionId"),
            documentNo = param(get, "documentNo"),
            type = enumParam(get, "type"),
            reason = enumParam(get, "reason"),
            operator = param(get, "operator"),
            reviewer = param(get, "reviewer"),
            department = param(get, "department"),
            hasAttachment = boolParam(get, "hasAttachment"),
            operatedAtStart = startOfDayParam(get, "operatedAtStart"),
            operatedAtEnd = endOfDayParam(get, "operatedAtEnd"),
        )
    }
}

internal data class LedgerLogQueryFilters(
    val keyword: String? = null,
    val collectionId: String? = null,
    val totalRegNo: String? = null,
    val refBizId: String? = null,
    val action: EnumLedgerAction? = null,
    val field: String? = null,
    val createTimeStart: LocalDateTime? = null,
    val createTimeEnd: LocalDateTime? = null,
) {
    companion object {
        fun from(get: (String) -> String?) = LedgerLogQueryFilters(
            keyword = param(get, "keyword"),
            collectionId = param(get, "collectionId"),
            totalRegNo = param(get, "totalRegNo"),
            refBizId = param(get, "refBizId"),
            action = enumParam(get, "action"),
            field = param(get, "field"),
            createTimeStart = startOfDayParam(get, "createTimeStart"),
            createTimeEnd = endOfDayParam(get, "createTimeEnd"),
        )
    }
}

// RestorationMethodQueryFilters 已迁移至 module-collection-restoration.service.RestorationQueryFilters

internal data class ExhibitionQueryFilters(
    val keyword: String? = null,
    val collectionId: String? = null,
    val title: String? = null,
    val exhibitionNo: String? = null,
    val venueId: String? = null,
    val venue: String? = null,
    val organizer: String? = null,
    val state: EnumProcessState? = null,
    val flowStatus: EnumFlowStatus? = null,
    val hasAttachment: Boolean? = null,
    val hasImage: Boolean? = null,
    val startDateStart: LocalDate? = null,
    val startDateEnd: LocalDate? = null,
    val endDateStart: LocalDate? = null,
    val endDateEnd: LocalDate? = null,
) {
    companion object {
        fun from(get: (String) -> String?) = ExhibitionQueryFilters(
            keyword = param(get, "keyword"),
            collectionId = param(get, "collectionId"),
            title = param(get, "title"),
            exhibitionNo = param(get, "exhibitionNo"),
            venueId = param(get, "venueId"),
            venue = param(get, "venue"),
            organizer = param(get, "organizer"),
            state = enumParam(get, "state"),
            flowStatus = enumParam(get, "flowStatus"),
            hasAttachment = boolParam(get, "hasAttachment"),
            hasImage = boolParam(get, "hasImage"),
            startDateStart = dateParam(get, "startDateStart"),
            startDateEnd = dateParam(get, "startDateEnd"),
            endDateStart = dateParam(get, "endDateStart"),
            endDateEnd = dateParam(get, "endDateEnd"),
        )
    }
}
