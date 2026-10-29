package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionCategoryDao
import io.hizhengfu.cloud.collection.domain.CollectionCategoryStandard
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException

internal object CollectionCategoryPolicy {
    fun isStandard(category: CollectionCategoryDao): Boolean = category.orgId == null &&
        category.parentId == "0" && category.status == EnumContentStatus.NORMAL &&
        CollectionCategoryStandard.matches(category.code, category.name)

    fun valid(id: String?): Boolean = id?.takeIf { it.isNotBlank() }?.let(CollectionCategoryDao::findById)?.let(::isStandard) == true

    /** 草稿可暂缺分类；填入时必须引用规范目录，不根据材质或旧名称自动猜测。 */
    fun requireOptional(id: String?) {
        if (!id.isNullOrBlank()) requireSelected(id)
    }

    fun requireSelected(id: String?) {
        if (!valid(id)) throw BadRequestException("文物分类须选择WW/T 0017—2013附录D规定的35类之一")
    }
}
