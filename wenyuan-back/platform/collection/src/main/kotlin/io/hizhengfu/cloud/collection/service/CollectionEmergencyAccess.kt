package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.system.dao.GroupDao
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/** 应急原件、演练及责任承接共用当前账号授权，旧登录仅提供账号标识。 */
internal class CollectionEmergencyAccess(scope:CollectionScopeResolver) {
    private val access=CollectionInstitutionOperationAccess(scope,"藏品应急预案与演练",setOf(CollectionConst.EMERGENCY),setOf(CollectionConst.EMERGENCY))
    fun require(org:String,write:Boolean=false)=access.require(org,write)
    fun readableOrgs()=access.readableOrgs()

    fun finish(org:String,actor:String?) {
        TransactionManager.current().entityCache.flush()
        if(actor==null || SecurityUtils.getNullUserDetail()?.id!=actor)CollectionCareEvidence.fail("办理账号已变化，请重新核对应急原件")
        val current=access.user(true)
        // 归档期间关系表可能变化，不能沿用入口处缓存的机构或收藏单位状态。
        (current.groupIds+setOfNotNull(current.groupId)).forEach {GroupDao.findById(it)?.refresh(flush=false)}
        CollectionOrgDao.findById(org)?.refresh(flush=false)
        access.require(org,true)
    }
}
