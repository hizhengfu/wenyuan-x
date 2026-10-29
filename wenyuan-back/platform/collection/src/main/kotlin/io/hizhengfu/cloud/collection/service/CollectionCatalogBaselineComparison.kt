package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

/** 仅核对当前业务依据。永久档案自身的字节摘要和原件一致性仍须使用原有严格校验。 */
internal object CollectionCatalogBaselineComparison {
    fun sameCatalog(frozen: CollectionCatalogContentVo, current: CollectionCatalogContentVo): Boolean =
        sameCatalogTree(JsonUtils.mapper.readTree(JsonUtils.toJson(frozen)), JsonUtils.mapper.readTree(JsonUtils.toJson(current)))

    fun sameSource(frozen: String, current: String): Boolean {
        if (frozen == current) return true
        val before = JsonUtils.mapper.readTree(frozen) as? ObjectNode ?: return false
        val now = JsonUtils.mapper.readTree(current) as? ObjectNode ?: return false
        if (!sameCatalogTree(before.get("catalog"), now.get("catalog"))) return false
        before.remove("catalog")
        now.remove("catalog")
        return before == now
    }

    private fun sameCatalogTree(frozen: JsonNode?, current: JsonNode?): Boolean {
        if (frozen == current) return true
        val before = (frozen?.deepCopy() as? ObjectNode) ?: return false
        val now = (current?.deepCopy() as? ObjectNode) ?: return false
        // 旧映射只产生这三个隐式默认值。新字段有真实值时，仍必须与批准依据完全一致。
        for ((key, default) in listOf("actualQuantity" to now.get("quantity"), "actualQuantityUnit" to now.get("unit"),
            "dimensionUnit" to JsonUtils.mapper.readTree("\"cm\""))) {
            if ((now.get(key) == null || now.get(key).isNull) && before.get(key) == default) before.remove(key)
        }
        for (key in listOf("actualQuantity", "actualQuantityUnit", "dimensionUnit", "entryDate", "receiptCompletedAt", "registrationDetails")) {
            if (before.get(key)?.isNull == true) before.remove(key)
            if (now.get(key)?.isNull == true) now.remove(key)
        }
        return before == now
    }
}
