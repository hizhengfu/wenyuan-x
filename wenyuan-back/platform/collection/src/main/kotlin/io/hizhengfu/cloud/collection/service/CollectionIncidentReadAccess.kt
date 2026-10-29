package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.*

/** 事故资料需当前事故职责；普通藏品查看权不代替事故授权。 */
internal class CollectionIncidentReadAccess private constructor(val orgIds:Set<String>) {
    companion object {
        fun require(scope:CollectionScopeResolver,write:Boolean=false):CollectionIncidentReadAccess {
            val id=SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请登录后读取事故资料")
            val user=CollectionActorDirectory(scope).requireActive(id)
            if(EnumRole.ADMIN.value !in user.roles && user.permissions.none {it in setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.INCIDENT)})
                throw BadRequestException("当前账号没有事故业务权限")
            val current=if(write)scope.currentWritableOrgIds(user)else scope.currentReadableOrgIds(user,CollectionConst.CROSS_ORG)
            val configured=if(scope.isMulti())null else scope.currentOrgId()
            return CollectionIncidentReadAccess(CollectionOrgDao.all().filter {
                ((it.status==EnumContentStatus.NORMAL && it.orgStatus==EnumOrgStatus.ENABLED)||(!write && current==null)) &&
                    (current==null || it.id.value in current) && (configured==null || configured==it.id.value)
            }.map {it.id.value}.toSet())
        }
    }
    fun requireOrg(orgId:String) = OrgScopedReadPolicy.requireVisible(orgIds,orgId,"事故与应急资料")
    fun require(orgId:String,biz:String,id:String) {
        requireOrg(orgId)
        if(CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq biz) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.orgId neq orgId)}.any())
            throw BadRequestException("事故相关原件所属馆与业务记录不一致")
    }
}

/** 仅核对关联身份；不依赖当前藏品所属馆，以保留合法移交后的原馆事故历史。 */
internal fun incidentReadCaseFacts(orgId:String,ids:Set<String>):Map<String,String> {
    if(ids.isEmpty())return emptyMap()
    val cases=CollectionIncidentDao.find {CollectionIncidents.id inList ids}.toList()
    if(cases.size!=ids.size || cases.any {it.orgId!=orgId})throw BadRequestException("关联事故案卷缺失或所属馆不一致")
    val rows=CollectionIncidentRecordDao.find {(CollectionIncidentRecords.incidentId inList ids) and (CollectionIncidentRecords.kind eq io.hizhengfu.cloud.collection.domain.EnumIncidentRecordKind.FACTS)}.toList()
    if(rows.any {it.orgId!=orgId})throw BadRequestException("关联事故事实所属馆与案卷不一致")
    rows.forEach { row ->
        val original=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_INCIDENT) and (CollectionPermanentArchives.bizId eq row.incidentId) and (CollectionPermanentArchives.phase eq "${row.kind}_${row.id.value}")}.singleOrNull()
            ?: throw BadRequestException("关联事故事实缺少永久原件")
        if(original.orgId!=orgId || original.digest!=CollectionCareEvidence.digest(original.snapshot) ||
            JsonUtils.mapper.readTree(original.snapshot)!=JsonUtils.mapper.readTree(row.content))
            throw BadRequestException("关联事故事实与永久原件不一致")
    }
    return rows.groupBy {it.incidentId}.mapValues {(_,values)->values.maxBy {it.sequence}.id.value}
}
internal fun incidentReadAffectedCases(orgId:String,collectionId:String):Set<String> {
    val rows=CollectionIncidentItemDao.find {(CollectionIncidentItems.collectionId eq collectionId) and (CollectionIncidentItems.restricted eq true)}.toList()
    if(rows.any {it.orgId!=orgId})throw BadRequestException("事故限制明细所属馆与业务不一致")
    val ids=rows.map {it.incidentId}.toSet()
    incidentReadCaseFacts(orgId,ids)
    return ids
}
