package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

/** 新旧两端均保存同一承接原件；批准历史保留，旧单不再获得执行资格。 */
internal class RequisitionReplacementEvidence(private val archives: CollectionPermanentArchiveService) {
    companion object { const val REPLACED = "RESPONSIBILITY_REPLACED"; const val CREATED = "RESPONSIBILITY_CREATED" }
    private fun fail(): Nothing = throw BadRequestException("提用责任变更与新旧申请或归档原件不一致，请核对历史记录")
    fun rows(id: String) = CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and (CollectionPermanentArchives.bizId eq id)
    }.toList()
    fun source(e: RequisitionDao): List<CollectionPermanentArchiveDao> {
        verify(e)
        return try { originalRows(e) } catch (_: tools.jackson.core.JacksonException) { fail() }
    }
    private fun originalRows(e: RequisitionDao): List<CollectionPermanentArchiveDao> {
        val all = rows(e.id.value)
        val submitted = all.singleOrNull { it.phase == "SUBMITTED" } ?: fail()
        if (JsonUtils.mapper.readTree(submitted.snapshot) != JsonUtils.mapper.readTree(JsonUtils.toJson(requisitionProposal(e))!!)) fail()
        all.forEach { row ->
            if (row.orgId != e.orgId || row.digest != CollectionCareEvidence.digest(row.snapshot)) fail()
            if (row.phase == "SUBMITTED" || row.phase.startsWith("APPROVED_")) archives.requireFileReferences(row, "${e.attachmentIds.orEmpty()},${e.dossier?.safetyPlanFileIds.orEmpty()}")
        }
        return all
    }
    fun record(e: RequisitionDao): RequisitionReplacementRecord? {
        verify(e)
        val row = rows(e.id.value).singleOrNull { it.phase == REPLACED } ?: return null
        return JsonUtils.fromJson(row.snapshot, RequisitionReplacementRecord::class.java) ?: fail()
    }
    fun requireActive(e: RequisitionDao) {
        verify(e)
        if (e.sourceRetirement != null) throw BadRequestException("来源业务已重建，本提用单不能继续审批、点交或出库")
        if (e.supersededById != null) throw BadRequestException("本提用单已保留并由新申请承接，不能再审批、点交或出库")
    }
    fun errors(e: RequisitionDao): List<String> = try { verify(e); emptyList() } catch (ex: BadRequestException) { listOf(ex.message ?: "责任变更原件待核对") }
    fun verify(e: RequisitionDao) {
        RequisitionSourceRetirementEvidence(archives).verify(e)
        val seen = mutableSetOf<String>()
        var current: RequisitionDao? = e
        while (current != null) {
            val record = current
            if (!seen.add(record.id.value)) fail()
            verifyLinks(record)
            current = record.replacesId?.let { RequisitionDao.findById(it) ?: fail() }
        }
    }
    private fun verifyLinks(e: RequisitionDao) {
        try {
            val own = rows(e.id.value)
            for ((phase, link) in listOf(REPLACED to e.supersededById, CREATED to e.replacesId)) {
                val row = own.singleOrNull { it.phase == phase }
                if ((row == null) != (link == null)) fail()
                if (row == null) continue
                val v = JsonUtils.fromJson(row.snapshot, RequisitionReplacementRecord::class.java) ?: fail()
                val source = RequisitionDao.findById(v.sourceId) ?: fail()
                val child = RequisitionDao.findById(v.replacementId) ?: fail()
                if (source.id == child.id || source.orgId != e.orgId || child.orgId != e.orgId ||
                    source.supersededById != child.id.value || child.replacesId != source.id.value ||
                    (if (phase == REPLACED) source.id != e.id || child.id.value != link else child.id != e.id || source.id.value != link)) fail()
                if (splitCollectionIds(child.collectionIds).toSet() != splitCollectionIds(source.collectionIds).toSet() || child.sourceBizType != source.sourceBizType || child.sourceBizId != source.sourceBizId || child.purpose != source.purpose) fail()
                if (source.flowInstanceId != v.request.expectedInstanceId || v.sourceArchiveDigests.isEmpty()) fail()
                for ((id, digest) in v.sourceArchiveDigests) {
                    val original = CollectionPermanentArchiveDao.findById(id) ?: fail()
                    if (original.orgId != e.orgId || original.bizType != CollectionConst.BIZ_REQUISITION || original.bizId != source.id.value || original.digest != digest || digest != CollectionCareEvidence.digest(original.snapshot)) fail()
                }
                val submitted = originalRows(source).single { it.phase == "SUBMITTED" }
                if (v.sourceArchiveDigests[submitted.id.value] != v.request.expectedSubmittedDigest) fail()
                val peer = rows(link!!).singleOrNull { it.phase == if (phase == REPLACED) CREATED else REPLACED } ?: fail()
                for (proof in listOf(row, peer)) {
                    if (proof.orgId != e.orgId || proof.snapshot != row.snapshot || proof.digest != CollectionCareEvidence.digest(proof.snapshot)) fail()
                    archives.requireFileReferences(proof, "${v.request.appointmentFileIds},${v.request.withdrawalFileIds}")
                }
            }
        } catch (_: tools.jackson.core.JacksonException) { fail() }
    }
}
