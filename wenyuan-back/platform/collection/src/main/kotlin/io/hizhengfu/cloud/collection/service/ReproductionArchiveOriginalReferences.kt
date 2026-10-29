package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.exception.BadRequestException

/** 复制拓印各阶段仅挂载本阶段冻结表单声明的原件，不从现有引用表反推清单。 */
internal object ReproductionArchiveOriginalReferences {
    private val formPhases = setOf("PLAN", "STARTED", "COMPLETED", "STOPPED")
    private val formPrefixes = listOf("ADMIN_", "SAFETY_", "OUTPUT_", "CUSTODY_", "USE_", "RETURN_")
    private val clearancePhase = Regex("STOP_CLEARED_[0-9]+")

    /** null 保留非本业务生成的扩展阶段；审批只保留事实，不重复挂载方案附件。 */
    fun fileIds(phase: String, snapshot: String): String? {
        if (phase == "SUBMITTED" || phase.startsWith("APPROVED_")) return ""
        val clearance = clearancePhase.matches(phase)
        if (!clearance && phase !in formPhases && formPrefixes.none(phase::startsWith)) return null
        val node = try { JsonUtils.mapper.readTree(snapshot) }
            catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("复制拓印冻结原件清单无法解析，请核对原档案") }
        // 解除快照中的事故依据保留关联事实；本阶段只挂载核对表单的签署原件。
        // sourceSnapshot 为历史事实的序列化字符串，不能展开为本阶段附件。
        return CollectionOriginalJsonFiles.references(if (clearance) node.path("record") else node).joinToString(",")
    }
}
