package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchiveDao
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchives
import io.hizhengfu.cloud.collection.domain.CollectionCareClearanceSnapshot
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

/** Read every sealed clearance version without rolling back the current disposition or resealing history. */
internal object CollectionFragileClearanceEvidence {
    fun versions(org: String, id: String, prefix: String, current: CollectionCareClearanceSnapshot?): List<CollectionCareClearanceSnapshot> {
        val rows = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_FRAGILITY) and (CollectionPermanentArchives.bizId eq id)
        }.filter { it.phase.startsWith(prefix) }.map { archive ->
            if (archive.orgId != org || archive.digest != CollectionCareEvidence.digest(archive.snapshot))
                CollectionFragilityEvidence.fail("监测处置历史原件所属馆或摘要不一致")
            val value = try { JsonUtils.fromJson(archive.snapshot, CollectionCareClearanceSnapshot::class.java) }
                catch (_: tools.jackson.core.JacksonException) { CollectionFragilityEvidence.fail("监测处置历史原件无法解析") }
                ?: CollectionFragilityEvidence.fail("监测处置历史原件缺失")
            if (value.version < 1 || archive.phase != "$prefix${value.version}")
                CollectionFragilityEvidence.fail("监测处置历史版本与归档阶段不一致")
            value
        }.sortedBy { it.version }
        if (rows.withIndex().any { (index, row) -> row.version != index + 1 } || rows.lastOrNull() != current)
            CollectionFragilityEvidence.fail("监测处置历史原件缺失或当前版本已回退")
        if (rows.zipWithNext().any { (old, next) -> next.record.confirmedAt.isBefore(old.record.confirmedAt) })
            CollectionFragilityEvidence.fail("监测处置历史时间顺序不一致")
        return rows
    }
}
