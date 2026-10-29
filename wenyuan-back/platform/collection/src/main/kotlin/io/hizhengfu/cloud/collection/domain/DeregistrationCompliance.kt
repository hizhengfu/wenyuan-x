package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.LocalDate

/** 每条意见对应一个外部专家、一次对单件藏品的独立书面评估。提交后不可修改。 */
data class DeregistrationExpertOpinionVo(
    val id: String? = null,
    val expertKey: String,
    val expertName: String,
    val organization: String,
    val employerOrgId: String? = null,
    val userId: String? = null,
    val professionalTitle: String,
    val specialty: String,
    val seniorQualificationFileIds: String,
    val independenceStatementFileIds: String,
    val opinion: String,
    val agree: Boolean,
    val signedOpinionFileIds: String,
    val assessedAt: LocalDate,
)

enum class EnumExitNoticeChannel { AUTHORITY_WEBSITE, MUSEUM_ONSITE, MUSEUM_WEBSITE }
enum class EnumExitNoticeKind { CONSULTATION, RESULT }
enum class EnumExitDestination { AUXILIARY_ACCOUNT, STATE_TEACHING_RESEARCH, STATE_MUSEUM, LOST, OTHER_LEGAL }
enum class EnumAuthorityLevel { COMPETENT, PROVINCE, NATIONAL, STATE_COUNCIL }
enum class EnumAdministrativeAction { APPROVAL, FILING }

data class CollectionAdministrativeEvidenceVo(
    val id: String? = null,
    val level: EnumAuthorityLevel,
    val action: EnumAdministrativeAction,
    val authorityName: String,
    val documentNo: String,
    val issuedAt: LocalDate,
    val fileIds: String,
)

data class DeregistrationNoticeVo(
    val id: String? = null,
    val kind: EnumExitNoticeKind,
    val channel: EnumExitNoticeChannel,
    val startedOn: LocalDate,
    val endedOn: LocalDate? = null,
    val urlOrLocation: String,
    val publicContentFileIds: String,
    val publicationEvidenceFileIds: String,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val disclosureReviewId: String? = null,
)

data class DeregistrationExecutionVo(
    val signedChangeFileIds: String,
    val paperLedgerRef: String? = null,
    val paperUpdatedAt: LocalDate,
    val dispositionReceiptFileIds: String,
    val paperLedgerBookId: String? = null,
    val paperLedgerPageNo: Int? = null,
)

object DeregistrationCompliancePolicy {
    val evaluatedReasons = setOf(EnumDeregReason.DAMAGE_NO_VALUE, EnumDeregReason.OTHER_BELOW_STANDARD)
    val legalReasons = evaluatedReasons + setOf(EnumDeregReason.FORCE_MAJEURE, EnumDeregReason.STATE_EXCHANGE_TRANSFER, EnumDeregReason.OTHER_LEGAL)

    fun requireLifecycle(reason: EnumDeregReason?, status: EnumCollectionStatus) {
        if (reason == EnumDeregReason.FORCE_MAJEURE) {
            if (status !in setOf(EnumCollectionStatus.IN_STORE,EnumCollectionStatus.MISSING)) throw BadRequestException("当前藏品状态不能办理不可抗力灭失退出")
        } else CollectionOutboundPolicy.requireInStore(status,"退出")
    }

    fun requireReason(reason: EnumDeregReason?, nature: EnumCollectionNature?, legalBasisFileIds: String?) {
        if (reason !in legalReasons) throw BadRequestException("请按新办法选择法定退出情形；被盗、丢失等应先履行事故报告，不得直接普通退出馆藏")
        if (nature == null) throw BadRequestException("请先核实藏品性质，并通过信息变更补录文物藏品或其他藏品")
        if (reason == EnumDeregReason.OTHER_BELOW_STANDARD && nature != EnumCollectionNature.OTHER) throw BadRequestException("不够入藏标准这一退出情形仅适用于化石、标本、实物资料等其他藏品")
        if (reason == EnumDeregReason.OTHER_LEGAL && legalBasisFileIds.isNullOrBlank()) throw BadRequestException("其他法定情形必须提供明确法律法规依据")
    }

    fun requireExpert(opinion: DeregistrationExpertOpinionVo, orgId: String, today: LocalDate = LocalDate.now()) {
        if (listOf(opinion.expertKey, opinion.expertName, opinion.organization, opinion.professionalTitle, opinion.specialty,
                opinion.seniorQualificationFileIds, opinion.independenceStatementFileIds, opinion.opinion, opinion.signedOpinionFileIds).any(String::isBlank)) {
            throw BadRequestException("专家身份、研究方向、高级职称证明、馆外独立声明和逐件签署意见必须完整")
        }
        if (opinion.employerOrgId == orgId) throw BadRequestException("本馆人员不得担任退出评估专家")
        if (opinion.assessedAt.isAfter(today)) throw BadRequestException("评估日期不能晚于今天")
    }

    fun requireUnanimous(opinions: List<DeregistrationExpertOpinionVo>) {
        if (opinions.any { !it.agree }) throw BadRequestException("有专家不同意退出，程序必须终止，五年内不得对同一件藏品重新启动")
        if (opinions.map { it.expertKey.trim() }.distinct().size < 5) throw BadRequestException("逐件退出评估必须有至少五名不同的馆外文物博物高级职称专家")
    }

    fun requireDestination(reason: EnumDeregReason, destination: EnumExitDestination?, recipient: String?) {
        if (destination == null) throw BadRequestException("退出处置去向必填")
        when {
            reason in evaluatedReasons && destination !in setOf(EnumExitDestination.AUXILIARY_ACCOUNT, EnumExitDestination.STATE_TEACHING_RESEARCH) ->
                throw BadRequestException("此类退出藏品仅可纳入辅助账册，或者交国有教学及科研单位保管使用")
            reason == EnumDeregReason.STATE_EXCHANGE_TRANSFER && destination != EnumExitDestination.STATE_MUSEUM ->
                throw BadRequestException("交换、调拨退出须移交国有博物馆")
            reason == EnumDeregReason.FORCE_MAJEURE && destination != EnumExitDestination.LOST ->
                throw BadRequestException("不可抗力灭失须登记灭失事实和处置记录")
        }
        if (destination in setOf(EnumExitDestination.STATE_TEACHING_RESEARCH, EnumExitDestination.STATE_MUSEUM) && recipient.isNullOrBlank()) {
            throw BadRequestException("接收单位及其国有性质依据必填")
        }
    }

    fun requireAdministrativeEvidence(evidence: CollectionAdministrativeEvidenceVo, today: LocalDate = LocalDate.now()) {
        if (evidence.authorityName.isBlank() || evidence.documentNo.isBlank() || evidence.fileIds.isBlank()) throw BadRequestException("行政机关、批复或备案文号和原件必填")
        if (evidence.issuedAt.isAfter(today)) throw BadRequestException("行政批复或备案日期不能晚于今天")
    }

    fun requireClearance(reason: EnumDeregReason, level: EnumRelicLevel, evidence: List<CollectionAdministrativeEvidenceVo>) {
        if (reason in evaluatedReasons) {
            if (evidence.none { it.level == EnumAuthorityLevel.PROVINCE && it.action == EnumAdministrativeAction.APPROVAL }) {
                throw BadRequestException("该退出情形须取得省级文物行政部门批准")
            }
        } else if (evidence.none { it.level == EnumAuthorityLevel.COMPETENT && it.action == EnumAdministrativeAction.FILING }) {
            throw BadRequestException("该退出情形须保留逐级报文物行政部门备案的凭证")
        }
        if (level == EnumRelicLevel.FIRST && evidence.none { it.level == EnumAuthorityLevel.NATIONAL && it.action == EnumAdministrativeAction.FILING }) {
            throw BadRequestException("退出馆藏一级文物须报国务院文物行政部门备案")
        }
    }
}

/** 专家评估开始前固定的退出理由与处置方案；行政凭证和公示记录另行追加。 */
data class DeregistrationDossierVo(
    val proposedOn: LocalDate? = null,
    val keeperUserId: String? = null,
    val leaderUserId: String? = null,
    val destinationType: EnumExitDestination? = null,
    val recipient: String? = null,
    val recipientStateEvidenceFileIds: String? = null,
    val legalBasisFileIds: String? = null,
    val dispositionPlanFileIds: String? = null,
    val transferId: String? = null,
    val auxiliaryRecordNo: String? = null,
    val auxiliaryLocation: String? = null,
)
