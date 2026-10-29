package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq

/** 调用方先核验对应业务的当前读取权限；候选只包含该馆现账及留存原账。 */
internal object CollectionLedgerReferenceChoices {
    fun list(orgId: String, retainedLedgers: CollectionRetainedLedgerService): List<Map<String, String>> {
        val current = CollectionDao.find { Collections.orgId eq orgId }.associateBy { it.id.value }
        val retained = CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.orgId eq orgId }
            .groupBy { it.collectionId }.mapValues { (_, rows) -> rows.maxBy { it.createTime } }
        val rows = current.map { (id, row) ->
            mapOf("id" to id, "totalRegNo" to row.totalRegNo, "name" to row.name, "source" to "CURRENT_MUSEUM")
        } + retained.filterKeys { it !in current }.map { (id, row) ->
            val original = retainedLedgers.verifiedBoundSnapshotForAuthorizedBusiness(row, orgId).collection
            if (original.id != id || original.orgId != orgId)
                throw BadRequestException("原馆留存账与本馆原藏品不一致，请核实原件")
            mapOf("id" to id, "totalRegNo" to row.totalRegNo, "name" to row.name, "source" to "RETAINED_SOURCE_MUSEUM")
        }
        return rows.sortedWith(compareBy({ it["totalRegNo"] }, { it["id"] }))
    }
}
