package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/** 鉴定（定名/断代/定级，委员会会签；通过后保留结论，正式信息变更另行审批）。 */
object Appraisals : FlowUIDTable("col_appraisal") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位")
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id"))
    val resultName = varchar("result_name", 500).nullable().comment("定名")
    val resultEra = varchar("result_era", 100).nullable().comment("断代")
    val resultTexture = varchar("result_texture", 100).nullable().comment("质地")
    val resultLevel = enumeration<EnumRelicLevel>("result_level").nullable().comment("定级")
    val committee = text("committee").nullable().comment("鉴定委员会成员")
    val conclusion = text("conclusion").nullable().comment("鉴定结论")
    val rejectReason = text("reject_reason").nullable().comment("驳回原因")
    val attachmentIds = text("attachment_ids").nullable().comment("鉴定意见/会签材料附件 fileId，逗号分隔")
    val appraisedAt = datetime("appraised_at").nullable()
}

class AppraisalDao(id: EntityID<String>) : FlowUIDEntity(id, Appraisals) {
    companion object : FlowUIDEntityClass<AppraisalDao>(Appraisals)

    var orgId by Appraisals.orgId
    var collectionId by Appraisals.collectionId
    var resultName by Appraisals.resultName
    var resultEra by Appraisals.resultEra
    var resultTexture by Appraisals.resultTexture
    var resultLevel by Appraisals.resultLevel
    var committee by Appraisals.committee
    var conclusion by Appraisals.conclusion
    var rejectReason by Appraisals.rejectReason
    var attachmentIds by Appraisals.attachmentIds
    var appraisedAt by Appraisals.appraisedAt

    fun toModel() = AppraisalVo(
        id = id.value, orgId = orgId, collectionId = collectionId, resultName = resultName, resultEra = resultEra,
        resultTexture = resultTexture, resultLevel = resultLevel, committee = committee,
        conclusion = conclusion, rejectReason = rejectReason, attachmentIds = attachmentIds,
        appraisedAt = appraisedAt, flowStatus = flowStatus, flowInstanceId = flowInstanceId,
        createTime = createTime, createBy = createBy,
    )
}

data class AppraisalVo(
    val id: String? = null,
    val orgId: String? = null,
    @field:NotBlank val collectionId: String? = null,
    val collectionTotalRegNo: String? = null,
    val collectionName: String? = null,
    val resultName: String? = null,
    val resultEra: String? = null,
    val resultTexture: String? = null,
    val resultLevel: EnumRelicLevel? = null,
    val committee: String? = null,
    val members: List<AppraisalMemberVo> = emptyList(),
    @field:NotBlank val conclusion: String? = null,
    val rejectReason: String? = null,
    val attachmentIds: String? = null,
    val appraisedAt: LocalDateTime? = null,
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createTime: LocalDateTime? = null,
    val createBy: String? = null,
)
