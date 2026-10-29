package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionJointSafetyPlanDao
import io.hizhengfu.cloud.collection.dao.CollectionJointSafetyRoundDao
import io.hizhengfu.cloud.collection.dao.RequisitionDao
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Resolve an original only through a record and phase visible in this requisition's verified history. */
@Component
@Transactional(readOnly = true)
class CollectionJointSafetyOriginalService(
    private val joint: CollectionJointSafetyService,
    private val archives: CollectionPermanentArchiveService,
) {
    fun requireArchive(requisitionId: String, recordId: String, phase: String, fileId: String): String {
        val detail = joint.detail(requisitionId)
        val orgId = detail["orgId"] as? String ?: throw BadRequestException("共同检查所属馆缺失")
        fun containsCheck(rows: Any?): Boolean = (rows as? List<*>)?.any { (it as? Map<*, *>)?.get("id") == recordId } == true
        val linkedCheck = containsCheck(detail["availableChecks"]) || containsCheck(detail["custodyChecks"]) ||
            (detail["rounds"] as? List<*>)?.any { containsCheck((it as? Map<*, *>)?.get("checks")) } == true
        val bizType = when {
            recordId == requisitionId -> {
                val requisition = RequisitionDao.findById(requisitionId) ?: throw BadRequestException("提用来源不存在")
                val recall = RequisitionRecallEvidence.entries(requisition).any { phase == "${RequisitionRecallEvidence.PREFIX}${it.id}" }
                val custody = RequisitionCustodyEvidence.entries(requisition).any { entry ->
                    phase == "${RequisitionCustodyEvidence.PREFIX}P_${entry.record.id}" ||
                        entry.signatures.keys.any { role -> phase == "${RequisitionCustodyEvidence.PREFIX}${role}_${entry.record.id}" }
                }
                if (!recall && !custody) throw BadRequestException("原件阶段不属于本次在用责任或提前归库记录")
                CollectionConst.BIZ_REQUISITION
            }
            phase == "CHECKED" && linkedCheck -> CollectionConst.BIZ_CARE
            CollectionJointSafetyPlanDao.findById(recordId)?.let { it.requisitionId == requisitionId && it.orgId == orgId } == true -> {
                val plan = CollectionJointSafetyPlanDao[recordId]
                val valid = phase == "PLAN" || CollectionJointSafetyEvidence.signatures(plan).any { (role, _, signature) ->
                    phase == role && signature != null
                }
                if (!valid) throw BadRequestException("原件阶段不属于本次共同检查方案")
                CollectionJointSafetyEvidence.BIZ
            }
            CollectionJointSafetyRoundDao.findById(recordId)?.let { it.requisitionId == requisitionId && it.orgId == orgId } == true -> {
                val round = CollectionJointSafetyRoundDao[recordId]
                val plan = CollectionJointSafetyPlanDao[round.planId]
                val signed = CollectionJointSafetyEvidence.signatures(plan, round).any { (role, _, signature) ->
                    phase == role && signature != null
                }
                val handover = CollectionJointReportHandoverEvidence.entries(round).any { entry ->
                    phase == "${CollectionJointReportHandoverEvidence.PREFIX}P_${entry.record.id}" ||
                        entry.signatures.keys.any { role -> phase == "${CollectionJointReportHandoverEvidence.PREFIX}${role}_${entry.record.id}" }
                }
                val valid = phase == "ROUND" || signed ||
                    (phase == "DIRECTOR_REPORT" && round.report != null) ||
                    (phase == "DIRECTOR" && round.director != null) ||
                    (phase == CollectionJointReportHandoverEvidence.CURRENT_BASIS && CollectionJointReportHandoverEvidence.currentBasis(round) != null) ||
                    handover
                if (!valid) throw BadRequestException("原件阶段不属于本轮共同检查或报告")
                CollectionJointSafetyEvidence.BIZ
            }
            else -> throw BadRequestException("原件记录不属于本次提用")
        }
        val archive = archives.phaseForAuthorizedOrg(bizType, recordId, orgId, phase)
            ?: throw BadRequestException("指定阶段永久原件不存在")
        @Suppress("UNCHECKED_CAST")
        val files = archive["files"] as? List<Map<String, Any?>> ?: throw BadRequestException("永久原件文件索引缺失")
        if (files.none { it["fileId"] == fileId }) throw BadRequestException("文件不属于指定阶段永久原件")
        return archive["id"] as? String ?: throw BadRequestException("永久原件编号缺失")
    }
}
