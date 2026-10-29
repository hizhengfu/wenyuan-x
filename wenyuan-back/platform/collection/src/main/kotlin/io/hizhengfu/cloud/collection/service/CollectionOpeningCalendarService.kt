package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.CollectionOpeningCalendarVo
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class CollectionOpeningCalendarService(private val scope: CollectionScopeResolver) {
    private fun latest(orgId: String, year: Int) = CollectionOpeningCalendarVersionDao.find {
        (CollectionOpeningCalendarVersions.orgId eq orgId) and (CollectionOpeningCalendarVersions.year eq year)
    }.orderBy(CollectionOpeningCalendarVersions.revision to SortOrder.DESC).limit(1).singleOrNull()

    fun get(orgId: String, year: Int): CollectionOpeningCalendarVo? {
        OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), orgId, "收藏单位开放与休馆日历")
        if (year !in 1900..2200) throw BadRequestException("日历年度不正确")
        return latest(orgId, year)?.content
    }
    fun save(orgId: String, value: CollectionOpeningCalendarVo): CollectionOpeningCalendarVo {
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), orgId, "收藏单位开放与休馆日历")
        value.validate()
        // Lock the organization so concurrent saves cannot allocate the same revision.
        if (CollectionOrgs.selectAll().where { CollectionOrgs.id eq orgId }.forUpdate().singleOrNull() == null) throw BadRequestException("收藏单位不存在")
        val revision = latest(orgId, value.year)?.revision ?: 0
        if (value.revision != revision) throw BadRequestException("开放日历已被其他人更新，请重新加载后核对")
        val saved = value.copy(revision = revision + 1)
        CollectionOpeningCalendarVersionDao.new {
            this.orgId = orgId
            year = saved.year
            this.revision = saved.revision
            content = saved
        }
        return saved
    }
}
