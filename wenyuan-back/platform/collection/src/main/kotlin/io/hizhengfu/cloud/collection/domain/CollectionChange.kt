package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.LocalDate
import java.time.LocalDateTime

data class CollectionChangeVo(
    val id: String? = null,
    val orgId: String? = null,
    val collectionId: String? = null,
    val totalRegNo: String? = null,
    val collectionName: String? = null,
    val collectionEvidenceIssue: String? = null,
    val reason: String? = null,
    val content: CollectionCatalogContentVo? = null,
    val before: CollectionCatalogContentVo? = null,
    val keeperUserId: String? = null,
    val leaderUserId: String? = null,
    val evidenceFileIds: String? = null,
    val signedChangeFileIds: String? = null,
    val paperLedgerRef: String? = null,
    val paperLedgerBookId: String? = null,
    val paperLedgerPageNo: Int? = null,
    val paperUpdatedAt: LocalDate? = null,
    val appliedAt: LocalDateTime? = null,
    val appliedBy: String? = null,
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createTime: LocalDateTime? = null,
    val correction: CollectionChangeCorrectionOrigin? = null,
    val setAmendment: CollectionSetAmendment? = null,
)

/** 服务端记录的补正来由；不通过普通草稿保存入口写入。 */
data class CollectionChangeCorrectionOrigin(
    val previousChangeId: String,
    val previousInstanceId: String,
    val previousStatus: EnumFlowStatus,
    val submittedArchiveId: String,
    val submittedDigest: String,
    val reason: String,
    val evidenceFileIds: String,
    val recordedBy: String,
    val recordedAt: LocalDateTime,
)

data class CollectionChangeCorrectionRequest(
    val requestKey: String,
    val expectedInstanceId: String,
    val reason: String,
    val evidenceFileIds: String,
)

data class CollectionChangeExecutionVo(
    val signedChangeFileIds: String? = null,
    val paperLedgerRef: String? = null,
    val paperUpdatedAt: LocalDate? = null,
    val paperLedgerBookId: String? = null,
    val paperLedgerPageNo: Int? = null,
)

/** 总登记号不是可订正的业务字段；历史错号应保留并通过原藏号/备注说明。 */
object CollectionIdentityPolicy {
    fun requireUnchanged(existing: String, requested: String?) {
        if (requested != null && requested != existing && requested.trim() != existing) {
            throw BadRequestException("总登记号确定后永久使用，不能更改；历史错号请通过原藏号或备注说明")
        }
    }
}

object CollectionChangePolicy {
    fun requireActors(applicant: String, keeper: String?, leader: String?) {
        if (keeper.isNullOrBlank() || leader.isNullOrBlank()) throw BadRequestException("请选择保管部门负责人和法定代表人或主要负责人")
        if (setOf(applicant, keeper, leader).size != 3) throw BadRequestException("经办人、保管部门负责人和主要负责人必须分别办理")
    }

    fun requireExecution(vo: CollectionChangeExecutionVo, today: LocalDate = LocalDate.now()) {
        if (vo.signedChangeFileIds.isNullOrBlank()) throw BadRequestException("经办人签字或盖章的藏品信息变更表必填")
        if (vo.paperLedgerBookId.isNullOrBlank() || vo.paperLedgerPageNo == null)
            throw BadRequestException("纸质总登记账册号和订正页码必填")
        val date = vo.paperUpdatedAt ?: throw BadRequestException("纸质账订正日期必填")
        if (date.isAfter(today)) throw BadRequestException("纸质账订正日期不能晚于今天")
    }

    fun correct(content: CollectionCatalogContentVo, field: String?, value: String?): CollectionCatalogContentVo {
        val clean = value?.trim()?.takeIf(String::isNotBlank) ?: throw BadRequestException("更正值必填")
        return when (field) {
            "totalRegNo" -> throw BadRequestException("总登记号确定后永久使用，不能更改")
            "name" -> content.copy(formalName = clean, totalName = clean)
            "level" -> content.copy(level = enumValue<EnumRelicLevel>(clean, "级别"))
            "source" -> content.copy(source = enumValue<EnumSource>(clean, "来源方式"))
            "acquireDate" -> content.copy(acquireDate = runCatching { LocalDate.parse(clean) }.getOrElse { throw BadRequestException("取得日期格式不正确") })
            "acquireDateRange" -> content.copy(acquireDateRange = clean)
            "acquireYear" -> content.copy(acquireYear = clean)
            else -> throw BadRequestException("字段「$field」不支持快捷更正，请使用信息变更申请")
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String, label: String): T =
        runCatching { enumValueOf<T>(value) }.getOrElse { throw BadRequestException("$label 不正确") }
}
