package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq

data class CollectionAppraisalPrintFactsVo(val appraisal: AppraisalVo, val collection: CollectionVo)

/** 馆内鉴定完成时留存对象和逐人意见；不据此产生行政定级或修改主账。 */
internal object CollectionAppraisalPrintFacts {
    const val PHASE = "COMPLETED_FACTS"
    fun model(row: AppraisalDao): AppraisalVo {
        val members = AppraisalMemberDao.find { AppraisalMembers.appraisalId eq row.id.value }
            .onEach { if (it.orgId != row.orgId) throw BadRequestException("鉴定成员与原记录所属馆不一致") }
            .sortedWith(compareBy<AppraisalMemberDao> { it.sort }.thenBy { it.memberName }).map { it.toModel() }
        return row.toModel().copy(members=members)
    }
    fun capture(row: AppraisalDao, archives: CollectionPermanentArchiveService) {
        val collection = CollectionDao.findById(row.collectionId) ?: throw BadRequestException("鉴定藏品不存在")
        if (collection.orgId != row.orgId) throw BadRequestException("鉴定完成时藏品所属馆已变化")
        val model = model(row)
        // 成员签名和意见附件沿用业务原件留存；流程回调仅冻结事实，不借机重新授予文件权限。
        archives.seal(row.orgId, CollectionConst.BIZ_APPRAISAL, row.id.value, PHASE,
            CollectionAppraisalPrintFactsVo(model, collection.toModel().copy(coverImage=null)), null)
    }
    fun read(row: AppraisalVo, archives: CollectionPermanentArchiveService?): CollectionAppraisalPrintFactsVo? {
        val stored = archives?.phaseForAuthorizedOrg(CollectionConst.BIZ_APPRAISAL, row.id!!, row.orgId!!, PHASE) ?: return null
        val facts = (stored["snapshot"] as? String)?.let { JsonUtils.fromJson(it, CollectionAppraisalPrintFactsVo::class.java) }
            ?: throw BadRequestException("鉴定冻结事实无法读取")
        if (JsonUtils.toJson(facts.appraisal) != JsonUtils.toJson(row) || facts.collection.id != row.collectionId || facts.collection.orgId != row.orgId)
            throw BadRequestException("鉴定记录与完成时冻结事实不一致，请核对原件")
        return facts
    }
}
