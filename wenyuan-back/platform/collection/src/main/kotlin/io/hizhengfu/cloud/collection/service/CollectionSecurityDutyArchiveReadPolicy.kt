package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.springframework.stereotype.Component

@Component
class CollectionSecurityDutyArchiveReadPolicy(private val scope: CollectionScopeResolver): CollectionArchiveReadPolicy {
    override val businessTypes = setOf(WarehouseSecurityDutyService.BIZ)
    override fun requireReadable(reference: CollectionArchiveFileReference) {
        val warehouse = WarehouseDao.findById(reference.businessId) ?: throw BadRequestException("值班原件所属库区不存在")
        if (warehouse.orgId != reference.orgId) throw BadRequestException("值班原件所属馆与库区不一致")
        CollectionCurrentOperationAccess.require(scope, reference.orgId, false,
            setOf(CollectionConst.SELECT, CollectionConst.WAREHOUSE_MANAGE), "库区值班原件", setOf(CollectionConst.ROLE_SECURITY))
    }
}
