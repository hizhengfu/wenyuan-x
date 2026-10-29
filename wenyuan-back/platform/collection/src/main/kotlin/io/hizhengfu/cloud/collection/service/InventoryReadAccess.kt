package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver

/** 清点列表、统计和原件共用当前查看或保管职责，保留明确授权的历史馆别。 */
internal fun CollectionScopeResolver.inventoryReadAccess() = CollectionSearchReadAccess.require(this,
    additionalPermissions=setOf(CollectionConst.STORAGE),allowHistoricalOrgs=true)

/** 同馆损坏证据继续提示调查；归属错位不能借异常页开放外馆原件。 */
internal fun CollectionPermanentArchiveService.inventoryOriginals(orgId:String,biz:String,id:String,errors:MutableList<String>):List<Map<String,Any?>> {
    if(CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq biz) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.orgId neq orgId)
    }.any()) throw BadRequestException("清点原件所属馆与业务记录不一致，请核对原始归属")
    return try { listForAuthorizedOrg(biz,id,orgId) }
    catch (e:BadRequestException) { errors+=e.message ?: "清点原件待核对"; emptyList() }
}
