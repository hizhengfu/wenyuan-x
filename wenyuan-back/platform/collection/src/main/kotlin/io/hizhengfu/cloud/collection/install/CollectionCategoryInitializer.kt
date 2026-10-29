package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.dao.CollectionCategoryDao
import io.hizhengfu.cloud.collection.domain.CollectionCategoryStandard
import io.hizhengfu.cloud.common.domain.EnumContentStatus

/** 开发阶段直接采用新目录；经既有首装/版本升级入口执行，不迁移或推断旧业务分类。 */
internal object CollectionCategoryInitializer {
    fun apply() {
        val existing = CollectionCategoryDao.all().toList()
        val retained = mutableSetOf<String>()
        CollectionCategoryStandard.entries.forEach { entry ->
            val node = existing.firstOrNull { it.orgId == null && it.code == entry.code && it.id.value !in retained }
                ?: CollectionCategoryDao.new { name = entry.name }
            node.code = entry.code
            node.name = entry.name
            node.sort = entry.sort
            node.orgId = null
            node.parentId = "0"
            node.parentIds = "0,"
            node.isLeaf = true
            node.status = EnumContentStatus.NORMAL
            retained += node.id.value
        }
        existing.filter { it.id.value !in retained }.forEach { it.delete() }
    }
}
