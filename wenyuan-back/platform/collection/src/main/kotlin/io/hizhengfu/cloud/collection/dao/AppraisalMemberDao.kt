package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.EnumAppraisalMemberRole
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/** 每次鉴定独立保存成员快照；内部成员保留 userId，外部专家保留姓名、单位和职称。 */
object AppraisalMembers : OrgAuditUIDTable("col_appraisal_member") {
    val appraisalId = varchar("appraisal_id", 64).index(shortIndexName("appraisal_id"))
    val userId = varchar("user_id", 64).nullable().index(shortIndexName("user_id"))
    val memberName = varchar("member_name", 100).comment("成员姓名快照")
    val memberRole = enumerationByName<EnumAppraisalMemberRole>("member_role", 20)
        .default(EnumAppraisalMemberRole.MEMBER)
    val organization = varchar("organization", 200).nullable()
    val professionalTitle = varchar("professional_title", 100).nullable()
    val specialty = varchar("specialty", 200).nullable()
    val opinion = text("opinion").nullable()
    val signatureFileId = varchar("signature_file_id", 64).nullable()
    val sort = integer("sort", checkConstraintName = shortCheckConstraintName("sort")).default(0)

    init {
        index(shortIndexName("org_id_appraisal_id"), false, orgId, appraisalId)
    }
}

class AppraisalMemberDao(id: EntityID<String>) : OrgAuditUIDEntity(id, AppraisalMembers) {
    companion object : OrgAuditUIDEntityClass<AppraisalMemberDao>(AppraisalMembers)

    var appraisalId by AppraisalMembers.appraisalId
    var userId by AppraisalMembers.userId
    var memberName by AppraisalMembers.memberName
    var memberRole by AppraisalMembers.memberRole
    var organization by AppraisalMembers.organization
    var professionalTitle by AppraisalMembers.professionalTitle
    var specialty by AppraisalMembers.specialty
    var opinion by AppraisalMembers.opinion
    var signatureFileId by AppraisalMembers.signatureFileId
    var sort by AppraisalMembers.sort

    fun toModel() = AppraisalMemberVo(
        id = id.value,
        appraisalId = appraisalId,
        userId = userId,
        memberName = memberName,
        memberRole = memberRole,
        organization = organization,
        professionalTitle = professionalTitle,
        specialty = specialty,
        opinion = opinion,
        signatureFileId = signatureFileId,
        sort = sort,
    )
}

data class AppraisalMemberVo(
    val id: String? = null,
    val appraisalId: String? = null,
    val userId: String? = null,
    @field:NotBlank val memberName: String? = null,
    val memberRole: EnumAppraisalMemberRole? = null,
    val organization: String? = null,
    val professionalTitle: String? = null,
    val specialty: String? = null,
    val opinion: String? = null,
    val signatureFileId: String? = null,
    val sort: Int? = null,
)
