package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils

/** 按当前账号、职责及实际机构核对业务访问；登录快照只用于取得账号 ID。 */
internal object CollectionCurrentOperationAccess {
    fun require(scope:CollectionScopeResolver,orgId:String,write:Boolean,permissions:Set<String>,label:String,roleCodes:Set<String> = emptySet()): SecurityUser {
        val id=SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请登录后访问$label")
        val user=CollectionActorDirectory(scope).requireActive(id)
        if(EnumRole.ADMIN.value !in user.roles && user.permissions.none {it in permissions || it in setOf("ADMIN",CollectionConst.ADMIN)} && user.roleCodes.none {it in roleCodes})
            throw BadRequestException("当前账号没有${label}权限")
        val ids=if(write)scope.currentWritableOrgIds(user) else scope.currentReadableOrgIds(user,CollectionConst.CROSS_ORG)
        if(!scope.isMulti() && scope.currentOrgId()!=orgId)throw BadRequestException("$label 不属于当前配置的收藏单位")
        OrgScopedReadPolicy.requireVisible(ids,orgId,label)
        val org=CollectionOrgDao.findById(orgId) ?: throw BadRequestException("$label 所属收藏单位不存在")
        if((write || ids!=null) && (org.status!=EnumContentStatus.NORMAL || org.orgStatus!=EnumOrgStatus.ENABLED))
            throw BadRequestException("$label 所属收藏单位已停用")
        return user
    }
}
