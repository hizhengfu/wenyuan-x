package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionCategories
import io.hizhengfu.cloud.collection.dao.CollectionCategoryDao
import io.hizhengfu.cloud.collection.dao.CollectionCategoryVo
import io.hizhengfu.cloud.collection.domain.CollectionCategoryStandard
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.domain.GenericUpdateSort
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.TreeCrudService
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.cache.annotation.CacheConfig
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 所有馆共用登录规范附录D固定目录；名称、序号及顺序不可由业务接口修改。 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE_CATEGORY])
@Suppress("UNUSED_PARAMETER")
class CollectionCategoryService(scope: CollectionScopeResolver) :
    TreeCrudService<CollectionCategoryDao>(CollectionCategoryDao, CollectionConst.CACHE_CATEGORY) {

    fun findAll(): List<CollectionCategoryVo> = dao.all().filter(CollectionCategoryPolicy::isStandard)
        .sortedBy { it.code }.map { it.toModel() }

    fun findPage(keyword: String?, request: HttpServletRequest): GenericPage<CollectionCategoryVo> {
        val page = GenericPage<CollectionCategoryVo>().build(request)
        val q = CollectionCategories.selectAll().where {
            CollectionCategories.orgId.isNull() and (CollectionCategories.parentId eq "0") and
                (CollectionCategories.status eq EnumContentStatus.NORMAL) and
                CollectionCategoryStandard.entries.map { entry ->
                    (CollectionCategories.code eq entry.code) and (CollectionCategories.name eq entry.name)
                }.compoundOr()
        }
        if (!keyword.isNullOrBlank()) q.andWhere {
            (CollectionCategories.name like "%$keyword%") or (CollectionCategories.code like "%$keyword%")
        }
        q.orderBy(CollectionCategories.code to SortOrder.ASC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<CollectionCategoryDao>)
        page.list = daoPage.data?.map { it.toModel() }
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<CollectionCategoryVo>
    }

    fun create(vo: CollectionCategoryVo): String = fixedDirectory()
    fun update(id: String, vo: CollectionCategoryVo): String = fixedDirectory()
    fun updateSort(sorts: List<GenericUpdateSort>): Unit = fixedDirectory()
    override fun delete(id: String): CollectionCategoryDao = fixedDirectory()

    private fun fixedDirectory(): Nothing = throw BadRequestException("文物分类固定采用WW/T 0017—2013附录D的35类，不支持新增、改名、删除或调整顺序")
}
