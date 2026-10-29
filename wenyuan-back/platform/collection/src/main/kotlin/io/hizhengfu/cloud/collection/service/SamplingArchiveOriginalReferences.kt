package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.exception.BadRequestException

/** 取样阶段的附件清单来自本阶段冻结表单，不能由当前引用表自行证明完整。 */
internal object SamplingArchiveOriginalReferences {
    private val formPhases = setOf("PLAN", "STARTED", "EXTRACTION_ENDED", "COMPLETED", "EXCEPTION_CLEARED")
    private val formPrefixes = listOf("ADMIN_", "SAMPLE_", "ANALYSIS_", "CONTINUATION_")
    private val eventPhase = Regex("(?:STOPPEDV|NOSAMPLEV)[0-9]+|(?:SAMPLEEVENT|SAMPLECUSTODY|ANALYSISPLAN)[0-9a-f]{32}V[0-9]+")

    /** null 表示非本业务生成的扩展阶段；空串表示只封存事实或前案关联，不重复挂载原件。 */
    fun fileIds(phase: String, snapshot: String): String? {
        if (phase in setOf("SUBMITTED", "PREDECESSOR_AT_START", "COMPLETION_SAMPLES") || phase.startsWith("APPROVED_"))
            return ""
        if (phase !in formPhases && formPrefixes.none(phase::startsWith) && !eventPhase.matches(phase)) return null
        val node = try { JsonUtils.mapper.readTree(snapshot) }
            catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("取样冻结原件清单无法解析，请核对原档案") }
        // sourceSnapshot、priorWorkSnapshot 是历史事实的序列化字符串，不展开为本阶段附件。
        return CollectionOriginalJsonFiles.references(node).joinToString(",")
    }
}
