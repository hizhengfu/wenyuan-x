package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.dao.CollectionCalendarVersionDao
import io.hizhengfu.cloud.collection.dao.CollectionCalendarVersions
import io.hizhengfu.cloud.collection.domain.CollectionCalendarYearVo
import io.hizhengfu.cloud.collection.domain.CollectionWorkingCalendar
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
@Transactional
class CollectionCalendarService(private val scope: CollectionScopeResolver, private val binder: BusinessAttachmentBinder, private val archives: CollectionPermanentArchiveService) {
    fun year(orgId: String, year: Int): CollectionCalendarYearVo = CollectionCalendarVersionDao.find {
        (CollectionCalendarVersions.orgId eq orgId) and (CollectionCalendarVersions.year eq year)
    }.maxByOrNull { it.revision }?.content ?: CollectionWorkingCalendar.national(year)

    fun get(orgId: String, year: Int): CollectionCalendarYearVo {
        OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), orgId, "收藏单位工作日历")
        return year(orgId, year)
    }

    fun save(orgId: String, vo: CollectionCalendarYearVo): String {
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), orgId, "收藏单位工作日历")
        if (CollectionOrgDao.findById(orgId) == null) throw BadRequestException("收藏单位不存在")
        vo.validate()
        val files=BusinessAttachmentBindingPlanner.plan(orgId, "工作日历通知", vo.evidenceFileIds).map { binder.resolveSingle(it.fileId)!! }.joinToString(",")
        if (files.isBlank()) throw BadRequestException("请上传正式工作日历通知依据")
        val revision=(CollectionCalendarVersionDao.find { (CollectionCalendarVersions.orgId eq orgId) and (CollectionCalendarVersions.year eq vo.year) }
            .maxOfOrNull { it.revision } ?: 0) + 1
        val row=CollectionCalendarVersionDao.new { this.orgId=orgId; year=vo.year; this.revision=revision; content=vo.copy(evidenceFileIds=files) }
        archives.seal(orgId, "COLLECTION_CALENDAR", row.id.value, "REGISTERED", row.content, files)
        return row.id.value
    }

    fun completedDays(orgId: String, start: LocalDate, end: LocalDate, today: LocalDate = LocalDate.now()): Int =
        CollectionWorkingCalendar.completedDays(start, end, today) { year(orgId, it) }
    fun deadline(orgId: String, start: LocalDate, days: Int): LocalDate = CollectionWorkingCalendar.addWorkingDays(start, days) { year(orgId, it) }
}
