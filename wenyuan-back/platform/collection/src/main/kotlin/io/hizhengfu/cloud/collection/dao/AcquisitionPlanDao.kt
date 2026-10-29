package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.collection.domain.EnumPlanPeriod
import io.hizhengfu.cloud.collection.domain.AcquisitionPlanOfflineApprovalVo
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime

/**
 * 征集计划（流程驱动）：年度/月度计划编制→鉴定委员会评审→分管馆长审批。
 * 征集单（Acquisitions.planId）挂计划，计划详情可看执行进度。
 */
object AcquisitionPlans : FlowUIDTable("col_acquisition_plan") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位")
    val title = varchar("title", 200).comment("计划名称")
    val planType = enumeration<EnumPlanPeriod>("plan_type").default(EnumPlanPeriod.YEAR).comment("年度/月度")
    val year = integer("year", checkConstraintName = shortCheckConstraintName("year")).comment("计划年度")
    val month = integer("month", checkConstraintName = shortCheckConstraintName("month"))
        .nullable().comment("计划月份（月度计划）")
    val content = text("content").nullable().comment("计划说明/征集方向")
    val budget = long("budget", checkConstraintName = shortCheckConstraintName("budget", typeCode = "l"))
        .nullable().comment("计划预算（分）")
    val attachmentIds = text("attachment_ids").nullable().comment("附件 fileId，逗号分隔")

    init {
        index(shortIndexName("org_id_year"), false, orgId, year)
    }
}

class AcquisitionPlanDao(id: EntityID<String>) : FlowUIDEntity(id, AcquisitionPlans) {
    companion object : FlowUIDEntityClass<AcquisitionPlanDao>(AcquisitionPlans)

    var orgId by AcquisitionPlans.orgId
    var title by AcquisitionPlans.title
    var planType by AcquisitionPlans.planType
    var year by AcquisitionPlans.year
    var month by AcquisitionPlans.month
    var content by AcquisitionPlans.content
    var budget by AcquisitionPlans.budget
    var attachmentIds by AcquisitionPlans.attachmentIds

    fun toModel() = AcquisitionPlanVo(
        id = id.value,
        orgId = orgId,
        title = title,
        planType = planType,
        year = year,
        month = month,
        content = content,
        budget = budget,
        attachmentIds = attachmentIds,
        flowStatus = flowStatus,
        flowInstanceId = flowInstanceId,
        createTime = createTime,
        createBy = createBy,
    )
}

data class AcquisitionPlanVo(
    val id: String? = null,
    val orgId: String? = null,
    @field:NotBlank val title: String? = null,
    @field:NotNull val planType: EnumPlanPeriod? = null,
    @field:NotNull val year: Int? = null,
    val month: Int? = null,
    val content: String? = null,
    val budget: Long? = null,
    val attachmentIds: String? = null,
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createTime: LocalDateTime? = null,
    val createBy: String? = null,
    /** 挂在该计划下的征集单数 / 已登账数（enrich 注入） */
    var acquisitionCount: Long = 0,
    var registeredCount: Long = 0,
    /** 线下已批准原件的归档事实，与线上流程记录分别展示。 */
    var offlineApproval: AcquisitionPlanOfflineApprovalVo? = null,
)
