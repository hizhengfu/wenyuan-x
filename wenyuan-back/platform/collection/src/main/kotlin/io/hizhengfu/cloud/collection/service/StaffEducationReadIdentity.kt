package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq

/** 身份归属错误不能借助异常核查界面或旧文件索引开放他人原件。内容核验仍由教育服务负责。 */
internal object StaffEducationReadIdentity {
    fun valid(e: CollectionStaffEducationDao): Boolean = try {
        val original = e.original
        original.id == e.id.value && original.orgId == e.orgId && original.request.userId == e.userId &&
            original.request.requestKey == e.requestKey &&
            CollectionStaffEducationVersionDao.find { CollectionStaffEducationVersions.educationId eq e.id.value }.all {
                it.orgId == e.orgId && it.content.educationId == e.id.value && it.content.version == it.version
            }
    } catch (_: tools.jackson.core.JacksonException) { false }

    fun require(e: CollectionStaffEducationDao) {
        if (!valid(e)) throw BadRequestException("教育记录人员或版本归属与原件不一致，不能返回原始材料")
    }
}
