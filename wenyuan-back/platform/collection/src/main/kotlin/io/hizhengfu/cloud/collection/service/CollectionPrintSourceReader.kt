package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst as C
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType as Type
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/** 打印按当前人员与原业务馆别读取；关联名称和数量也不能由其他馆的误关联补入。 */
class CollectionPrintSourceReader(private val scope: CollectionScopeResolver) {
    fun access(type: Type, additionalPermissions: Set<String> = emptySet(), crossOrgPermission: String = C.CROSS_ORG): CollectionSearchReadAccess {
        val permissions = when(type) {
            Type.ACQUISITION, Type.ENTRY_VOUCHER -> setOf(C.ACQUIRE)
            Type.APPRAISAL -> setOf(C.APPRAISE)
            Type.DEREGISTRATION -> setOf(C.DEREGISTER)
            Type.TRANSFER -> setOf(C.CROSS_ORG)
            Type.MOVEMENT, Type.MOUNTING, Type.REQUISITION, Type.LOAN_HANDOVER, Type.INVENTORY,
            Type.REPRODUCTION, Type.EXHIBITION -> setOf(C.STORAGE)
            else -> emptySet()
        }
        return CollectionSearchReadAccess.require(scope, additionalPermissions=permissions+additionalPermissions,
            crossOrgPermission=crossOrgPermission, allowHistoricalOrgs=true)
    }

    fun requireOrg(access: CollectionSearchReadAccess, orgId: String?, subject: String): String {
        val org=orgId?.takeIf(String::isNotBlank) ?: throw BadRequestException("${subject}缺少所属收藏单位，请核实原记录")
        OrgScopedReadPolicy.requireVisible(access.orgIds,org,subject)
        return org
    }

    fun collection(id: String?, orgId: String): CollectionVo? {
        val key=id?.takeIf(String::isNotBlank) ?: return null
        val row=CollectionDao.findById(key) ?: throw BadRequestException("打印关联藏品不存在，请核实原记录")
        if(row.orgId!=orgId)throw BadRequestException("打印关联藏品与原业务所属馆不一致，请核实原记录")
        val vo=row.toModel()
        val category=vo.categoryId?.let(CollectionCategoryDao::findById)
        if(category?.orgId!=null && category.orgId!=orgId)throw BadRequestException("打印藏品分类所属馆不一致，请核实原记录")
        val warehouse=vo.warehouseId?.let(WarehouseDao::findById)
        if(warehouse!=null && warehouse.orgId!=orgId)throw BadRequestException("打印藏品库位所属馆不一致，请核实原记录")
        vo.categoryName=category?.name
        vo.warehouseName=warehouse?.name
        vo.mediaCount=CollectionImagePresence.displayCount(vo.coverImage, CollectionMedias.selectAll().where { (CollectionMedias.collectionId eq key) and (CollectionMedias.orgId eq orgId) }.count())
        vo.attachmentCount=CollectionAttachments.selectAll().where { (CollectionAttachments.collectionId eq key) and (CollectionAttachments.orgId eq orgId) }.count()
        return vo
    }
}
