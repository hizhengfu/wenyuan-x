package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionAdministrativeEvidenceVo
import io.hizhengfu.cloud.collection.domain.EnumAdministrativeAction
import io.hizhengfu.cloud.collection.domain.EnumAuthorityLevel
import io.hizhengfu.cloud.collection.domain.EnumApproveLevel

/** 行政原件是现行依据；旧字段只用于尚无独立行政记录的历史申请。 */
internal data class DeregistrationAdministrativeDisplay(val level: EnumApproveLevel?, val documentNo: String?) {
    companion object {
        fun from(
            evidence: List<CollectionAdministrativeEvidenceVo>,
            legacyLevel: EnumApproveLevel? = null,
            legacyDocumentNo: String? = null,
        ): DeregistrationAdministrativeDisplay {
            if (evidence.isEmpty()) return DeregistrationAdministrativeDisplay(legacyLevel, legacyDocumentNo)
            val approval = evidence.lastOrNull { it.action == EnumAdministrativeAction.APPROVAL }
            val level = when (approval?.level) {
                EnumAuthorityLevel.PROVINCE -> EnumApproveLevel.PROVINCE
                EnumAuthorityLevel.NATIONAL, EnumAuthorityLevel.STATE_COUNCIL -> EnumApproveLevel.NATIONAL
                else -> null
            }
            return DeregistrationAdministrativeDisplay(level, (approval ?: evidence.last()).documentNo)
        }
    }
}
