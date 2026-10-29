package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.flow.domain.FlowDefinitionVo
import io.hizhengfu.cloud.flow.service.FlowDefinitionService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode

/**
 * 藏品流程定义导入器（征集/提用/鉴定/修复/借展/退出馆藏/调拨）。
 *
 * 从 classpath 资源 `flow/collection_*.json` 读取 LogicFlow 模型并发布（与 protection 模块同款约定）。
 * 由 [io.hizhengfu.cloud.collection.install.CollectionInstall] 在 initData/update 时调用，
 * 也可经 `POST /collection/flow/seed` 手动触发；幂等：已 ACTIVE 跳过，馆方在设计器改过的不覆盖。
 */
@Component
class CollectionFlowImporter(
    @param:Lazy private val flowDefinitionService: FlowDefinitionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val operator = "system"

    /**
     * 导入并发布全部藏品流程定义；返回逐条结果（便于排查）。
     * @param force true 时即便已存在 ACTIVE 也新建版本并发布（用于把旧的 ADMIN 链替换为角色链）；
     *              默认 false 幂等：所有已有状态的定义均保留，仅补缺失的流程标识。
     */
    @Transactional
    fun importAll(force: Boolean = false): List<String> {
        val mapper = JsonUtils.mapper
        val resolver = PathMatchingResourcePatternResolver(javaClass.classLoader)
        val resources = resolver.getResources("classpath*:flow/collection_*.json")
        if (resources.isEmpty()) {
            log.warn("未扫描到藏品流程定义资源 flow/collection_*.json，请确认资源已编译进 classpath（IDE 增量构建可能漏拷新增资源，建议重新构建 module-collection）")
            throw IllegalStateException("未找到藏品流程资源，请确认部署产物完整后重试升级")
        }
        return resources.map { resource ->
            runCatching {
                val json = resource.inputStream.bufferedReader().use { it.readText() }
                val root: JsonNode = mapper.readTree(json)
                val key = root.path("key").asString()
                require(key.isNotBlank()) { "流程定义缺少 key" }
                val active = flowDefinitionService.findActiveByKey(key)
                if (active != null && !force) return@map "[$key] 已存在 ACTIVE，跳过"
                if (!force) flowDefinitionService.findByKey(key)?.let { existing ->
                    return@map "[$key] 已存在 ${existing.status}，保留馆方原定义，请核对后自行发布"
                }
                val name = root.path("name").takeIf { !it.isMissingNode && !it.isNull }?.asString() ?: key
                val category = root.path("category").takeIf { !it.isMissingNode && !it.isNull }?.asString()
                val description = root.path("description").takeIf { !it.isMissingNode && !it.isNull }?.asString()
                val modelNode = root.path("model").takeIf { !it.isMissingNode } ?: error("流程定义[$key]缺少 model")
                val modelData = CollectionFlowRoleConfiguration.resolveTemplateCodes(mapper.writeValueAsString(modelNode))
                val vo = FlowDefinitionVo(key = key, name = name, category = category, description = description, modelData = modelData)
                // 只创建新编号；模板角色解析不作用于任何已保存的馆方模型。
                val defId = flowDefinitionService.create(vo, operator).id.value
                flowDefinitionService.publish(defId, operator)
                val msg = if (force && active != null) "已重新发布(force)" else "已发布"
                log.info("藏品流程定义[{}]{}", key, msg)
                "[$key] $msg"
            }.getOrElse {
                log.error("导入藏品流程定义失败：${resource.filename}", it)
                throw IllegalStateException("流程资源 ${resource.filename} 导入失败，本次模块升级未完成", it)
            }
        }
    }
}
