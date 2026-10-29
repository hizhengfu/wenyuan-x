package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.wms.dao.*
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 图片排版图服务
 */
@Service
@Transactional
@CacheConfig(cacheNames = ["WMS_IMAGE_LAYOUT"], keyGenerator = "keyGenerator")
class WmsImageLayoutService :
    CrudService<WmsImageLayoutDao>(WmsImageLayoutDao, "WMS_IMAGE_LAYOUT") {

    @Cacheable
    fun findPage(params: WmsImageLayoutVo, request: HttpServletRequest): GenericPage<WmsImageLayoutVo> {
        val q = WmsImageLayouts.selectAll().orderBy(WmsImageLayouts.createTime, SortOrder.DESC)
        if (!params.projectId.isNullOrBlank()) q.andWhere { WmsImageLayouts.projectId eq params.projectId }
        if (!params.title.isNullOrBlank()) {
            q.andWhere { WmsImageLayouts.title like "%${params.title}%" }
        }
        val page = findPage(request, q)
        val result = GenericPage<WmsImageLayoutVo>()
        result.pageSize = page.pageSize
        result.pageNum = page.pageNum
        result.total = page.total
        result.list = page.data?.map { it.toModel() }
        return result
    }

    fun get(id: String): WmsImageLayoutVo =
        findById(id)?.toModel() ?: throw BadRequestException("记录不存在")

    @CacheEvict(allEntries = true)
    fun create(vo: WmsImageLayoutVo): String {
        if (vo.title.isNullOrBlank()) throw BadRequestException("图名不能为空")
        val userId = runCatching { SecurityUtils.getUserId() }.getOrNull()
        val dao = new {
            title = vo.title
            projectId = vo.projectId
            projectName = vo.projectName
            layout = vo.layout ?: "A4_PORTRAIT"
            width = vo.width ?: 794
            height = vo.height ?: 1123
            content = vo.content
            thumbnailUrl = vo.thumbnailUrl
            createBy = userId
            createTime = LocalDateTime.now()
        }
        return dao.id.value
    }

    @CacheEvict(allEntries = true)
    fun update(vo: WmsImageLayoutVo) {
        val id = vo.id ?: throw BadRequestException("ID 不能为空")
        val dao = findById(id) ?: throw BadRequestException("记录不存在")
        val userId = runCatching { SecurityUtils.getUserId() }.getOrNull()
        vo.title?.let { dao.title = it }
        vo.projectId?.let { dao.projectId = it }
        vo.projectName?.let { dao.projectName = it }
        vo.layout?.let { dao.layout = it }
        vo.width?.let { dao.width = it }
        vo.height?.let { dao.height = it }
        vo.content?.let { dao.content = it }
        vo.thumbnailUrl?.let { dao.thumbnailUrl = it }
        dao.updateBy = userId
        dao.updateTime = LocalDateTime.now()
    }
}
