package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.file.dao.FileContents
import io.hizhengfu.cloud.sys.file.dao.FileUsers
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/** 藏品入口不能用旧登录管理员身份豁免当前上传归属；已有本件引用仍可沿用。 */
internal object CollectionFileReferenceAccess {
    fun require(scope: CollectionScopeResolver, fileId: String, collectionId: String, existing: Boolean = false) {
        val actor = CollectionActorDirectory(scope).requireActive(SecurityUtils.getUserId() ?: throw BadRequestException("请登录后引用藏品文件"))
        if (EnumRole.ADMIN.value in actor.roles || existing) return
        if (FileContents.selectAll().where { (FileContents.fileId eq fileId) and (FileContents.contentId eq collectionId) }.limit(1).count() > 0) return
        if (FileUsers.selectAll().where { (FileUsers.fileId eq fileId) and (FileUsers.userId eq actor.id) }.limit(1).count() > 0) return
        throw BadRequestException("当前账号无权引用该文件，请使用本人上传或本件已有的原件")
    }
}
