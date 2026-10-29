package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** An emergency original must belong to a stage actually visible in this museum's verified dossier. */
@Component
@Transactional(readOnly = true)
class CollectionEmergencyOriginalService(
    private val emergency: CollectionEmergencyService,
    private val archives: CollectionPermanentArchiveService,
) {
    fun requireArchive(orgId: String, recordId: String, phase: String, fileId: String): String {
        val detail = emergency.detail(orgId)
        fun rows(value: Any?): List<Map<*, *>> = (value as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        val allowed = rows(detail["plans"]).any { plan ->
            plan["id"] == recordId && (phase == "ISSUED" || rows(plan["signatures"]).any {
                it["role"] == phase && it["signature"] != null
            })
        } || rows(detail["drills"]).any { drill ->
            rows(drill["reports"]).any { report ->
                report["id"] == recordId && (
                    phase == (if (report["revision"] == null) "DRILL" else "DRILL_REVISION") ||
                        phase == "EVALUATOR" && report["evaluator"] != null ||
                        phase == "DIRECTOR" && report["director"] != null
                )
            } || rows(drill["issues"]).any { issue ->
                rows(issue["records"]).any { record ->
                    record["id"] == recordId && (phase == "IMPROVEMENT" || phase == "REVIEW" && record["review"] != null)
                }
            }
        } || rows(detail["duties"]).any { duty ->
            rows(duty["records"]).any { record ->
                record["id"] == recordId && (phase == "DUTY_APPOINTED" ||
                    phase == "DUTY_HANDOVER" && record["handover"] != null ||
                    phase == "DUTY_ACCEPTED" && record["acceptance"] != null)
            }
        }
        if (!allowed) throw BadRequestException("原件记录或阶段不属于本馆应急案卷")
        val archive = archives.phaseForAuthorizedOrg(CollectionEmergencyEvidence.BIZ, recordId, orgId, phase)
            ?: throw BadRequestException("指定阶段永久原件不存在")
        val files = rows(archive["files"])
        if (files.none { it["fileId"] == fileId }) throw BadRequestException("文件不属于指定阶段永久原件")
        return archive["id"] as? String ?: throw BadRequestException("永久原件编号缺失")
    }
}
