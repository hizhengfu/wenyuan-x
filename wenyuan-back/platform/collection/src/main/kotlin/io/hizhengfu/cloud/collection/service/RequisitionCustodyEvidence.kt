package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import io.hizhengfu.cloud.collection.service.CollectionJointSafetyEvidence as J

internal object RequisitionCustodyEvidence {
    const val PREFIX = "CUSTODY_"
    data class Entry(val record: RequisitionCustodyRecord, val digest: String, val signatures: MutableMap<String, CollectionCareSignature> = linkedMapOf()) {
        val complete get() = signatures.keys.containsAll(setOf("KEEPER", "USE", "DIRECTOR"))
        val effectiveAt get() = if (complete) signatures.values.maxOf { it.confirmedAt } else null
    }
    fun actors(r: RequisitionDao, entry: Entry? = null): Map<String, String> {
        val d = r.dossier ?: C.fail("缺少提用职责原件")
        val v = entry?.record?.request
        return mapOf("KEEPER" to (v?.keeperUserId ?: d.keeperUserId), "USE" to (v?.useUserId ?: d.useResponsibleUserId), "DIRECTOR" to (v?.directorUserId ?: d.leaderUserId))
    }
    fun snapshot(base: String, entry: Entry?) = if (entry == null) base else C.json(mapOf("useSource" to base, "custodyRecordId" to entry.record.id, "custodyDigest" to entry.digest))
    fun files(v: RequisitionCustodyRequest) = "${v.appointmentFileIds},${v.handoverFileIds}"
    fun entries(r: RequisitionDao): List<Entry> = try {
        val rows = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and (CollectionPermanentArchives.bizId eq r.id.value) }
            .filter { it.phase.startsWith(PREFIX) }.sortedWith(compareBy({ it.createTime }, { it.id.value }))
        val result = mutableListOf<Entry>()
        for (a in rows) {
            if (a.orgId != r.orgId || a.digest != C.digest(a.snapshot)) C.fail("在用责任承接所属馆或原件摘要不一致")
            if (a.phase.startsWith("${PREFIX}P_")) {
                val v = JsonUtils.fromJson(a.snapshot, RequisitionCustodyRecord::class.java) ?: C.fail("在用承接登记原件缺失")
                if (a.phase != "${PREFIX}P_${v.id}" || v.requisitionId != r.id.value || v.orgId != r.orgId || result.any { it.record.id == v.id } ||
                    v.request.previousId != result.lastOrNull()?.record?.id || v.request.expectedEffectiveId != result.lastOrNull { it.complete }?.record?.id ||
                    v.sourceSnapshot != J.baseSource(r).snapshot || v.request.sourceRevision != C.digest(snapshot(v.sourceSnapshot,result.lastOrNull { it.complete })) ||
                    v.registeredAt.isBefore(v.request.handedAt) || result.lastOrNull()?.record?.registeredAt?.let { v.registeredAt.isBefore(it) } == true)
                    C.fail("在用责任承接版本、来源或时间依据不一致")
                val out = CollectionPermanentArchiveDao.findById(v.outboundArchiveId) ?: C.fail("承接缺少原出库依据")
                if (out.bizType != CollectionConst.BIZ_REQUISITION || out.bizId != r.id.value || out.orgId != r.orgId || out.phase != "OUT_EXECUTED" || out.digest != v.outboundDigest || out.digest != C.digest(out.snapshot)) C.fail("承接与原出库原件不一致")
                checks(r,v.request).also { if (J.checkSnapshot(it) != v.checksSnapshot) C.fail("承接所依据逐件现状原件已变化") }
                CollectionResponsibilityArchiveEvidence.references(a,files(v.request)); result.add(Entry(v,a.digest))
            } else {
                val e = result.lastOrNull() ?: C.fail("承接确认缺少登记原件")
                val role = actors(r,e).keys.singleOrNull { a.phase == "${PREFIX}${it}_${e.record.id}" } ?: C.fail("承接确认不属于最新登记版本")
                val sig = JsonUtils.fromJson(a.snapshot,CollectionCareSignature::class.java) ?: C.fail("承接本人确认缺失")
                if (e.signatures.containsKey(role) || sig.actorUserId != actors(r,e)[role] || sig.confirmedAt.isBefore(e.record.registeredAt) ||
                    (role == "DIRECTOR" && (e.signatures.keys.containsAll(setOf("KEEPER","USE"))).not()) ||
                    (role == "DIRECTOR" && e.signatures.values.any { sig.confirmedAt.isBefore(it.confirmedAt) })) C.fail("承接确认人员、顺序或时间不一致")
                CollectionResponsibilityArchiveEvidence.references(a,sig.record.fileIds); e.signatures[role] = sig
            }
        }
        result
    } catch (_: tools.jackson.core.JacksonException) { C.fail("在用责任承接原件无法解析，请核实历史记录") }
    fun checks(r: RequisitionDao, v: RequisitionCustodyRequest): List<CollectionCareCheckDao> {
        val ids = splitCollectionIds(r.collectionIds).toSet()
        val handed = r.handover?.content?.handedAt ?: C.fail("在用承接缺少实际出库点交")
        if (v.collectionIds.isEmpty() || !ids.containsAll(v.collectionIds) || v.handedAt.isBefore(handed)) C.fail("在用承接范围或实际交接时间与提用不一致")
        val cc = v.checkIds.map { CollectionCareCheckDao.findById(it) ?: C.fail("承接逐件现状检查不存在") }
        if (cc.size != v.collectionIds.size || cc.map { it.collectionId }.toSet() != v.collectionIds) C.fail("实际交接须逐件覆盖尚在用藏品，不能漏件、重复或混入其他实物")
        cc.forEach { c ->
            if (c.orgId != r.orgId || c.content.checkedAt.isBefore(handed) || c.content.checkedAt.isAfter(v.handedAt)) C.fail("承接现状核对须属于本馆且发生于出库后、交接前")
            C.check(c)
        }
        return cc
    }
}
