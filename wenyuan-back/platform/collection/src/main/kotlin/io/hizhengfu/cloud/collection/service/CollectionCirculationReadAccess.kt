package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.CollectionSummaryVo
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.*

/** 提借用及归还每次请求共用当前授权范围，关联索引仍须对应业务所属馆。 */
internal class CollectionCirculationReadAccess private constructor(val orgIds:Set<String>) {
    companion object {
        fun require(scope:CollectionScopeResolver)=CollectionCirculationReadAccess(CollectionSearchReadAccess.require(scope,
            additionalPermissions=setOf(CollectionConst.STORAGE),allowHistoricalOrgs=true).orgIds)
    }
    fun require(orgId:String,biz:String,id:String) {
        OrgScopedReadPolicy.requireVisible(orgIds,orgId,"提借用与归还资料")
        if(CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq biz) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.orgId neq orgId)
        }.any()) throw BadRequestException("提借用或归还原件所属馆与业务记录不一致")
    }
    fun summaries(ids:Set<String>):Map<String,Map<String,CollectionSummaryVo>> {
        if(ids.isEmpty())return emptyMap()
        return Collections.selectAll().where {(Collections.id inList ids) and (Collections.orgId inList orgIds)}
            .groupBy {it[Collections.orgId]}.mapValues {(_,rows)->rows.associate {
                it[Collections.id].value to CollectionSummaryVo(it[Collections.id].value,it[Collections.totalRegNo],it[Collections.name])
            }}
    }
    fun matchingItems(org:Column<String>,id:Column<EntityID<String>>,biz:String,value:String,keyword:Boolean):Op<Boolean> {
        val items=CollectionBusinessItems
        val matches=mutableMapOf<String,MutableSet<String>>()
        fun add(rows:Iterable<ResultRow>) {rows.forEach {matches.getOrPut(it[items.orgId]){mutableSetOf()}.add(it[items.bizId])}}
        // 精确藏品标识可以定位合法留存记录；不需要读取移交后他馆的当前藏品资料。
        add(items.selectAll().where {(items.orgId inList orgIds) and (items.bizType eq biz) and (items.collectionId eq value)})
        if(keyword) add(items.join(Collections,JoinType.INNER,items.collectionId,Collections.id).selectAll().where {
            (items.orgId inList orgIds) and (items.bizType eq biz) and (items.orgId eq Collections.orgId) and
                ((Collections.totalRegNo like "%$value%") or (Collections.name like "%$value%"))
        })
        var condition:Op<Boolean> = Op.FALSE
        matches.forEach {(owner,ids)->condition=condition or ((org eq owner) and (id inList ids))}
        return condition
    }
}

/** 仅在相应案卷详情完成当前馆别与职责校验后，定位该案卷的冻结阶段文件。 */
internal object CollectionCirculationArchivedOriginal {
    fun require(archives:List<Map<String,Any?>>,archiveId:String,fileId:String) {
        if(!archiveId.matches(Regex("[0-9]{15,20}")) || !fileId.matches(Regex("[0-9]{15,20}")))
            throw BadRequestException("归档原件标识不正确")
        val archive=archives.singleOrNull {it["id"]==archiveId}
            ?: throw BadRequestException("永久档案不属于本案卷")
        val files=archive["files"] as? List<*>
            ?: throw BadRequestException("永久档案缺少原件清单")
        if(files.none {(it as? Map<*,*>)?.get("fileId")==fileId})
            throw BadRequestException("文件不属于所选永久档案阶段")
    }
}

/** 归还进度只由原业务同馆、原清单内的实际接收记录形成；不查询移交后他馆主档。 */
internal object CollectionCirculationReadIntegrity {
    fun requireReturnSource(row:CollectionReturnDao) {
        val ids=splitCollectionIds(row.collectionIds).toSet()
        if(!ids.containsAll(splitCollectionIds(row.acceptedCollectionIds)) ||
            row.inspection?.items.orEmpty().any {it.collectionId !in ids})
            throw BadRequestException("归还接收或核验明细不属于本单清单")
        val source=when(row.sourceBizType) {
            CollectionConst.BIZ_REQUISITION -> RequisitionDao.findById(row.sourceBizId)?.let {it.orgId to splitCollectionIds(it.collectionIds).toSet()}
            CollectionConst.BIZ_LOAN -> LoanDao.findById(row.sourceBizId)?.let {it.orgId to splitCollectionIds(it.collectionIds).toSet()}
            else -> null
        }
        if(source!=null && (source.first!=row.orgId || !source.second.containsAll(ids) ||
                row.sourceSnapshot?.items.orEmpty().any {it.collectionId !in source.second}))
            throw BadRequestException("归还单与原提借用业务的所属馆或藏品清单不一致")
    }
}
