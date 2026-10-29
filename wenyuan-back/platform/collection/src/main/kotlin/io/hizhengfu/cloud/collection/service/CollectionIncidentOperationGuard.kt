package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionIncidentItemDao
import io.hizhengfu.cloud.collection.dao.CollectionIncidentItems
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component

interface CollectionOperationSafetyGuard {
    fun requireAllowed(collectionId: String, bizType: String?, bizId: String?)
}

/** 业务模块提供按精确业务及实物绑定的专项授权；不删除事故、清点或其他占用。 */
interface CollectionRestrictedOperationAuthorization {
    fun permits(collectionId: String, bizType: String, bizId: String): Boolean
}

@Component
class CollectionRestrictedOperationService(private val providers: List<CollectionRestrictedOperationAuthorization> = emptyList()) {
    fun permits(collectionId: String, bizType: String?, bizId: String?): Boolean =
        !bizType.isNullOrBlank() && !bizId.isNullOrBlank() && providers.any { it.permits(collectionId,bizType,bizId) }
}

/** 事故限制独立于普通审批锁；释放来源锁或结束审批不会移除事故限制。 */
@Component
class CollectionIncidentOperationGuard(private val restricted: CollectionRestrictedOperationService = CollectionRestrictedOperationService()) : CollectionOperationSafetyGuard {
    override fun requireAllowed(collectionId: String, bizType: String?, bizId: String?) {
        // 接收、清点和履责信息订正仍可办理；各业务自己的原件、职责及审批锁继续约束。
        if (bizType in setOf(CollectionConst.BIZ_RETURN, CollectionConst.BIZ_INVENTORY, CollectionConst.BIZ_CHANGE)) return
        if (CollectionIncidentItemDao.find { (CollectionIncidentItems.collectionId eq collectionId) and (CollectionIncidentItems.restricted eq true) }.any() && !restricted.permits(collectionId,bizType,bizId)) {
            throw BadRequestException("藏品仍有事故安全限制，请先核实处置及实际安全条件；普通审批通过不能解除")
        }
    }
}
