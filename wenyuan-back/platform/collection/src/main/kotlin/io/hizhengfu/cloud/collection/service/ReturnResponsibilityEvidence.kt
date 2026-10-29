package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C

internal object ReturnResponsibilityEvidence {
    const val PREFIX = "RESPONSIBILITY_"
    data class Entry(val record: ReturnResponsibilityRecord, val signatures: MutableMap<String,CollectionCareSignature> = linkedMapOf()) {
        val complete get() = signatures.keys.containsAll(setOf("KEEPER","DIRECTOR"))
        val effectiveAt get() = signatures.values.maxOf { it.confirmedAt }
    }
    fun files(v:ReturnResponsibilityVo) = "${v.appointmentFileIds},${v.handoverFileIds}"
    fun actors(e:Entry) = mapOf("KEEPER" to e.record.request.keeperUserId,"DIRECTOR" to e.record.request.directorUserId)
    private fun archives(e:CollectionReturnDao) = CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_RETURN) and (CollectionPermanentArchives.bizId eq e.id.value)
    }.toList()
    fun received(e:CollectionReturnDao):CollectionPermanentArchiveDao {
        if(e.executionState!=EnumReturnExecutionState.EXECUTED || e.inspection==null)C.fail("须先完成实际接收并保留逐件归还点交原件")
        val a=archives(e).singleOrNull {it.phase=="RECEIVED"} ?: C.fail("实际接收归档原件缺失或重复")
        try {
            if(a.orgId!=e.orgId || a.digest!=C.digest(a.snapshot))C.fail("实际接收原件所属馆或摘要不一致")
            val tree=JsonUtils.mapper.readTree(a.snapshot)
            val proposal=returnProposal(e)
            val expected=JsonUtils.mapper.readTree(JsonUtils.toJson(proposal)!!)
            proposal.keys.forEach {key -> if(tree.path(key)!=expected.path(key))C.fail("实际接收原件与原归还方案不一致")}
            val initiallyAccepted=e.inspection!!.items.filter {!it.abnormal()}.map {it.collectionId}.toSet()
            if(splitCollectionIds(tree.path("acceptedCollectionIds").asString()).toSet()!=initiallyAccepted)C.fail("实际接收原件初始验收清单不一致")
            CollectionResponsibilityArchiveEvidence.references(a,returnFiles(e))
        } catch(_:tools.jackson.core.JacksonException) {C.fail("实际接收原件无法解析")}
        return a
    }
    fun entries(e:CollectionReturnDao):List<Entry> = try {
        val rows=archives(e).filter {it.phase.startsWith(PREFIX)}.sortedWith(compareBy({it.createTime},{it.id.value}))
        val result=mutableListOf<Entry>()
        if(rows.isNotEmpty()) {
            val original=received(e)
            val abnormal=e.inspection!!.items.filter {it.abnormal()}.map {it.collectionId}.toSet()
            for(a in rows) {
                if(a.orgId!=e.orgId || a.digest!=C.digest(a.snapshot))C.fail("归还异常承接原件所属馆或摘要不一致")
                if(a.phase.startsWith("${PREFIX}P_")) {
                    val r=JsonUtils.fromJson(a.snapshot,ReturnResponsibilityRecord::class.java) ?: C.fail("异常责任承接原件缺失")
                    val v=r.request;val effective=result.lastOrNull {it.complete}
                    if(r.returnId!=e.id.value || r.orgId!=e.orgId || a.phase!="${PREFIX}P_${r.id}" || result.any {it.record.id==r.id} ||
                        v.previousId!=result.lastOrNull()?.record?.id || v.expectedEffectiveId!=effective?.record?.id ||
                        r.receivedArchiveId!=original.id.value || v.receivedDigest!=original.digest ||
                        v.collectionIds.isEmpty() || !abnormal.containsAll(v.collectionIds) ||
                        r.registeredBy!=v.keeperUserId || v.keeperUserId==v.directorUserId ||
                        v.handedAt.isBefore(e.inspection!!.inspectedAt) || r.registeredAt.isBefore(v.handedAt) ||
                        effective?.effectiveAt?.let {v.handedAt.isBefore(it)}==true ||
                        result.lastOrNull()?.record?.registeredAt?.let {r.registeredAt.isBefore(it)}==true ||
                        listOf(v.reason,v.actualInventory,v.outstandingMatters).any(String::isBlank))C.fail("归还异常承接版本、来源、人员或清点移交记录不一致")
                    CollectionResponsibilityArchiveEvidence.references(a,files(v));result.add(Entry(r))
                } else {
                    val entry=result.lastOrNull() ?: C.fail("归还异常承接确认缺少登记原件")
                    val role=actors(entry).keys.singleOrNull {a.phase=="$PREFIX${it}_${entry.record.id}"} ?: C.fail("归还异常承接确认版本不一致")
                    val sig=JsonUtils.fromJson(a.snapshot,CollectionCareSignature::class.java) ?: C.fail("归还异常承接本人确认原件缺失")
                    if(role in entry.signatures || sig.actorUserId!=actors(entry)[role] || sig.confirmedAt.isBefore(entry.record.registeredAt) ||
                        sig.record.opinion.isBlank() || (role=="DIRECTOR" && entry.signatures["KEEPER"]?.confirmedAt?.let {sig.confirmedAt.isBefore(it)}!=false))C.fail("归还异常承接本人确认身份、意见或顺序不一致")
                    CollectionResponsibilityArchiveEvidence.references(a,sig.record.fileIds);entry.signatures[role]=sig
                }
            }
        }
        result
    } catch(_:tools.jackson.core.JacksonException) {C.fail("归还异常承接原件无法解析")}
    fun effective(e:CollectionReturnDao,collectionId:String) = entries(e).lastOrNull {it.complete && collectionId in it.record.request.collectionIds}
    fun requireResolution(e:CollectionReturnDao,v:ReturnResolutionVo):Entry? {
        val entry=effective(e,v.collectionId)
        if(v.expectedResponsibilityId!=entry?.record?.id)C.fail("归还异常责任版本已变化，请重新核对已生效的承接记录")
        if(v.keeperUserId!=(entry?.record?.request?.keeperUserId ?: e.keeperUserId) || v.leaderUserId==v.keeperUserId ||
            (entry!=null && (v.leaderUserId!=entry.record.request.directorUserId || v.approvedAt.isBefore(entry.effectiveAt))))C.fail("须由本件已生效的保管负责人和不同的主要负责人核实签署处理结果；不能倒填承接前时间")
        return entry
    }
}

internal fun returnProposal(e:CollectionReturnDao)=mapOf("id" to e.id.value,"orgId" to e.orgId,"sourceBizType" to e.sourceBizType,"sourceBizId" to e.sourceBizId,"sourceDocumentNo" to e.sourceDocumentNo,"collectionIds" to e.collectionIds,"operator" to e.operator,"keeperUserId" to e.keeperUserId,"toWarehouseId" to e.toWarehouseId,"inspection" to e.inspection,"sourceSnapshot" to e.sourceSnapshot)
internal fun returnFiles(e:CollectionReturnDao)=(listOfNotNull(e.sourceSnapshot?.outSignedFileIds,e.sourceSnapshot?.agreementFileIds,e.inspection?.returningAuthorizationFileIds,e.inspection?.signedHandoverFileIds,e.inspection?.transportCarrierCapabilityFileIds,e.inspection?.transportSupervisionFileIds,e.inspection?.packingCheckFileIds,e.attachmentIds)+e.inspection?.items.orEmpty().mapNotNull {it.imageFileIds}).joinToString(",")
