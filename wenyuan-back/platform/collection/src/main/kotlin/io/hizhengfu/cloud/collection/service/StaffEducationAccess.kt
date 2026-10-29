package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.springframework.stereotype.Component

@Component
class StaffEducationAccess(private val scope:CollectionScopeResolver,private val directory:CollectionActorDirectory) {
    fun actor()=directory.requireActive(SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请登录后查阅岗位教育记录"))
    fun manages(actor:SecurityUser)=actor.permissions.any {it in setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.STAFF_EDUCATION_MANAGE)} ||
        actor.roleCodes.any {it in setOf(CollectionConst.ROLE_STORAGE_HEAD,CollectionConst.ROLE_DIRECTOR)}
    fun responsible(actor:SecurityUser,orgId:String)=scope.isMuseumMember(actor,orgId) && actor.roleCodes.any {it in setOf(CollectionConst.ROLE_STORAGE_HEAD,CollectionConst.ROLE_DIRECTOR)}
    fun configured(orgId:String)=scope.isMulti() || orgId==scope.currentOrgId()
    fun canWrite(actor:SecurityUser,orgId:String):Boolean {
        if(!configured(orgId))return false
        if(!manages(actor) || scope.currentWritableOrgIds(actor)?.contains(orgId)==false)return false
        val org=CollectionOrgDao.findById(orgId) ?: return false
        return org.status==EnumContentStatus.NORMAL && org.orgStatus==EnumOrgStatus.ENABLED
    }
    fun require(orgId:String,userId:String?=null,write:Boolean=false):SecurityUser {
        val a=actor()
        if(!configured(orgId))throw BadRequestException("岗位教育记录不属于当前配置收藏单位")
        val allowed=manages(a) || (!write && (CollectionConst.STAFF_EDUCATION_VIEW in a.permissions || a.id==userId))
        if(!allowed)throw BadRequestException("当前账号缺少该人员岗位教育记录权限")
        OrgScopedReadPolicy.requireVisible(if(write)scope.currentWritableOrgIds(a) else scope.currentReadableOrgIds(a,CollectionConst.CROSS_ORG),orgId,"岗位培训与继续教育")
        val org=CollectionOrgDao.findById(orgId) ?: throw BadRequestException("收藏单位不存在")
        if(write && (org.status!=EnumContentStatus.NORMAL || org.orgStatus!=EnumOrgStatus.ENABLED))throw BadRequestException("收藏单位已停用，不能新增教育办理事实")
        return a
    }
}
