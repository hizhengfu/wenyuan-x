package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.CollectionTransferExecutionVo
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/** 只对实际逐文件留存及签收原件连通的馆别共享查阅，不把目标馆字段当作接收证明。 */
@Component
class CollectionMainFileReadPolicy(
    private val authorization: CollectionFileReadAuthorization,
    private val scope: CollectionScopeResolver,
    private val directory: CollectionActorDirectory,
) : CollectionArchiveReadPolicy {
    private data class MetadataProof(val facts: List<Any?>, val result: Result<Pair<String, String>>)
    private class MetadataProofs(val filePaths: List<List<Any?>>) {
        val values = mutableMapOf<String, MetadataProof>()
    }
    private val metadataProofs = ThreadLocal<MetadataProofs?>()
    private fun filePaths(): List<List<Any?>> = Files.select(Files.id, Files.path).orderBy(Files.id)
        .map { listOf(it[Files.id], it[Files.path]) }
    private fun proofFacts(id: String): List<Any?> {
        val ledger = CollectionRetainedLedgers.selectAll().where { CollectionRetainedLedgers.id eq id }.singleOrNull() ?: fail()
        val transferId = ledger[CollectionRetainedLedgers.transferId]
        val a = CollectionPermanentArchives
        val archives = a.selectAll().where {
            ((a.bizType eq RETAINED) and (a.bizId eq id)) or
                ((a.bizType eq CollectionConst.BIZ_TRANSFER) and (a.bizId eq transferId))
        }.orderBy(a.id).toList()
        val archiveIds = archives.map { it[a.id].value }
        val f = CollectionPermanentArchiveFiles
        return listOf(
            CollectionRetainedLedgers.columns.map { ledger[it] },
            CollectionTransfers.selectAll().where { CollectionTransfers.id eq transferId }
                .map { row -> CollectionTransfers.columns.map { row[it] } },
            archives.map { row -> a.columns.map { row[it] } },
            if (archiveIds.isEmpty()) emptyList<List<Any?>>() else f.selectAll().where { f.archiveId inList archiveIds }
                .orderBy(f.id).map { row -> f.columns.map { row[it] } },
        )
    }
    /** 复用一次目录查询中的调拨核验；完整来源事实在返回前重新读取，任何变化拒绝整次输出。 */
    internal fun <T> withMetadataReadProofs(body: () -> T): T {
        if (metadataProofs.get() != null) return body()
        val read = MetadataProofs(filePaths())
        metadataProofs.set(read)
        try {
            val result = body()
            if (read.values.any { (id, proof) -> proofFacts(id) != proof.facts } ||
                (read.values.isNotEmpty() && filePaths() != read.filePaths))
                throw BadRequestException("查询期间调拨留存原件事实已变化，请重新核查")
            return result
        } finally {
            metadataProofs.remove()
        }
    }
    companion object {
        const val RETAINED = "COLLECTION_RETAINED_LEDGER"
        private val MAIN_PERMISSIONS = setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.SELECT, CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT, CollectionConst.CATALOG_EDIT, CollectionConst.CATALOG_AUDIT)
        private val RETAINED_PERMISSIONS = setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.SELECT, CollectionConst.LEDGER_CORRECT, CollectionConst.CATALOG_EDIT, CollectionConst.CATALOG_AUDIT)
    }
    override val businessTypes = setOf(CollectionMainOriginalFileSource.BIZ)
    override fun requireReadable(reference: CollectionArchiveFileReference) = requireReadable(reference, false)
    fun requireRetainedReadable(reference: CollectionArchiveFileReference) = requireReadable(reference, true)

    /** 接收档案中的主档原件按逐文件移交链查阅；签署及其他业务材料仍由调拨规则处理。 */
    fun requireReceivedOriginalReadable(reference: CollectionArchiveFileReference): Boolean {
        if (reference.phase != "RECEIVED") return false
        val row = CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.transferId eq reference.businessId }.singleOrNull()
            ?: return false
        if (fileIdentity(reference.fileId) !in row.snapshot.originalFileIds) return false
        verifiedEdge(row)
        if (reference.orgId != row.targetOrgId ||
            archived(row.targetOrgId, CollectionConst.BIZ_TRANSFER, row.transferId, "RECEIVED").id.value != reference.archiveId) fail()
        requireReadable(reference.copy(businessType = CollectionMainOriginalFileSource.BIZ, businessId = row.collectionId), false)
        return true
    }

    private fun requireReadable(reference: CollectionArchiveFileReference, retained: Boolean) {
        val permissions = if (retained) RETAINED_PERMISSIONS else MAIN_PERMISSIONS
        // 先核对当前资格。原馆自身查阅不以之后完成调拨为前提，归档事务也可据此读取原件。
        authorization.requireReadable(emptySet(), permissions, CollectionConst.CROSS_ORG, "藏品主档原件")
        val actor = authorization.currentReadActor() ?: directory.requireActive(SecurityUtils.getNullUserDetail()?.id ?: fail())
        val visible = authorization.readableOrgIds(actor, CollectionConst.CROSS_ORG)
        if (visible == null) {
            authorization.requireReadable(setOf(reference.orgId), permissions, CollectionConst.CROSS_ORG, "藏品主档原件")
            return
        }
        val fileId = fileIdentity(reference.fileId)
        if (!retained && reference.phase == "CURRENT_FILE" && reference.orgId in visible) {
            // 已知属于实际接收清单的原件，不能因原馆文件引用丢失而仅凭现馆字段放行。
            relatedTransfers(reference.businessId, fileId).filter { it.targetOrgId == reference.orgId }.forEach { verifiedEdge(it) }
        }
        fun allowed(org: String): Boolean = try {
            authorization.requireReadable(setOf(org), permissions, CollectionConst.CROSS_ORG, "藏品主档原件")
            true
        } catch (_: BadRequestException) { false }
        if (reference.orgId in visible && allowed(reference.orgId)) return
        val collectionId = if (retained) {
            val row = CollectionRetainedLedgerDao.findById(reference.businessId) ?: fail()
            if (row.orgId != reference.orgId || row.archiveId != reference.archiveId) fail()
            row.collectionId
        } else reference.businessId
        // 新调拨尚在同一事务中写入时，不能抹去以前已成立的查阅关系；
        // 只有逐项核对成功的移交才能形成路径，缺失或异常的移交不会新增任何馆别权限。
        val edges = relatedTransfers(collectionId, fileId).mapNotNull {
            try { verifiedEdge(it) } catch (_: BadRequestException) { null }
        }
        val connected = mutableSetOf(reference.orgId)
        var changed: Boolean
        do {
            changed = false
            edges.forEach { (source, target) ->
                if (source in connected || target in connected) {
                    if (connected.add(source)) changed = true
                    if (connected.add(target)) changed = true
                }
            }
        } while (changed)
        val readable = connected.filter { it in visible }
        for (org in readable) {
            // 停用机构和真实权限仍走公共授权规则，不因持有历史记录而豁免。
            if (allowed(org)) return
        }
        fail()
    }

    private fun fileIdentity(value: String): String = FileDao.findById(value)?.id?.value
        ?: FileDao.find { Files.path eq value }.singleOrNull()?.id?.value ?: value

    private fun relatedTransfers(collectionId: String, fileId: String) =
        CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.collectionId eq collectionId }
            .filter { fileId in retainedFiles(it) || fileId in signedFiles(CollectionTransferDao.findById(it.transferId)?.execution) }

    private fun retainedFiles(row: CollectionRetainedLedgerDao): Set<String> =
        (row.snapshot.originalFileIds + listOfNotNull(row.snapshot.collection.coverImage) + row.snapshot.media.mapNotNull { it.fileId } + row.snapshot.attachments.mapNotNull { it.fileId })
            .map(::fileIdentity).toSet()

    private fun signedFiles(signing: CollectionTransferExecutionVo?): Set<String> = signing?.let { value ->
        listOfNotNull(value.signedHandoverFileIds, value.conditionImageFileIds, value.signedLedgerChangeFileIds,
            value.receivingLedgerReceiptFileIds, value.packingCheckFileIds)
            .flatMap { BusinessAttachmentBindingPlanner.plan("", "调拨签署原件", it) }.map { fileIdentity(it.fileId) }.toSet()
    }.orEmpty()

    private fun archived(org: String, biz: String, id: String, phase: String): CollectionPermanentArchiveDao {
        val archive = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq biz) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase)
        }.singleOrNull() ?: fail()
        if (archive.orgId != org || archive.digest != CollectionCareEvidence.digest(archive.snapshot)) fail()
        return archive
    }

    private fun tree(value: Any): JsonNode = JsonUtils.mapper.readTree(JsonUtils.toJson(value))
    private fun requireFileReferences(archive: CollectionPermanentArchiveDao, required: Set<String>) {
        val files = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.toList()
        if (required.isEmpty() || !files.map { it.fileId }.containsAll(required)) fail()
        files.filter { it.fileId in required }.forEach { file ->
            if (file.orgId != archive.orgId || file.bizId != archive.bizId) fail()
            val metadata = JsonUtils.mapper.readTree(file.metadata)
            if (metadata.path("path").asString().isBlank()) fail()
            CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(archive.id.value, archive.orgId, archive.bizType,
                archive.bizId, archive.phase, file.fileId, file.metadata), metadata)
        }
    }
    internal fun verifiedEdge(row: CollectionRetainedLedgerDao): Pair<String, String> {
        val read = metadataProofs.get() ?: return verifyEdge(row)
        return read.values.getOrPut(row.id.value) {
            val facts = proofFacts(row.id.value)
            val result = try { Result.success(verifyEdge(row)) } catch (e: BadRequestException) { Result.failure(e) }
            MetadataProof(facts, result)
        }.result.getOrThrow()
    }

    private fun verifyEdge(row: CollectionRetainedLedgerDao): Pair<String, String> {
        val snapshot = row.snapshot
        snapshot.identity?.let { if(it!=CollectionRetainedLedgerIdentity(row.orgId,row.collectionId,row.transferId,row.targetOrgId))fail() }
        if (CollectionOrgDao.findById(row.orgId) == null || CollectionOrgDao.findById(row.targetOrgId) == null) fail()
        if (snapshot.collection.id != row.collectionId || snapshot.collection.orgId != row.orgId ||
            snapshot.collection.totalRegNo != row.totalRegNo || row.orgId == row.targetOrgId ||
            snapshot.media.any { it.collectionId != row.collectionId || it.orgId != row.orgId } ||
            snapshot.attachments.any { it.collectionId != row.collectionId || it.orgId != row.orgId }) fail()
        val retained = archived(row.orgId, RETAINED, row.id.value, "TRANSFERRED")
        val frozen = JsonUtils.mapper.readTree(retained.snapshot)
        val currentSnapshot = tree(snapshot) as tools.jackson.databind.node.ObjectNode
        // 旧版没有该字段时只承认其当时明确保存的封面/影像/附件，不能推造普通关联。
        if (!frozen.has("originalFileIds") && snapshot.originalFileIds.isEmpty()) currentSnapshot.remove("originalFileIds")
        if (!frozen.has("identity") && snapshot.identity==null) currentSnapshot.remove("identity")
        if (retained.id.value != row.archiveId || frozen != currentSnapshot) fail()
        retainedFiles(row).takeIf { it.isNotEmpty() }?.let { requireFileReferences(retained, it) }
        val transfer = CollectionTransferDao.findById(row.transferId) ?: fail()
        if (transfer.orgId != row.orgId || transfer.fromOrgId != row.orgId || transfer.toOrgId != row.targetOrgId ||
            transfer.collectionId != row.collectionId || transfer.flowStatus != EnumFlowStatus.PASS || transfer.execution == null ||
            transfer.executedAt == null || transfer.executedAt!!.isAfter(LocalDateTime.now()) || transfer.executedBy.isNullOrBlank()) fail()
        val signing = transfer.execution!!
        if (!signing.authenticityVerified || signing.sourceCustodianUserId.isBlank() || signing.targetCustodianUserId.isBlank() ||
            signing.sourceCustodianUserId == signing.targetCustodianUserId || signing.conditionDescription.isBlank() ||
            signing.handedOn.isAfter(transfer.executedAt!!.toLocalDate())) fail()
        val signed = listOf(signing.signedHandoverFileIds, signing.conditionImageFileIds, signing.signedLedgerChangeFileIds, signing.receivingLedgerReceiptFileIds)
        if (signed.any(String::isBlank)) fail()
        val required = signedFiles(signing)
        val executedArchive = archived(row.orgId, CollectionConst.BIZ_TRANSFER, row.transferId, "EXECUTED")
        requireFileReferences(executedArchive, required)
        val executed = JsonUtils.mapper.readTree(executedArchive.snapshot)
        val current = tree(transfer.toModel())
        for (key in listOf("id", "orgId", "fromOrgId", "toOrgId", "collectionId", "flowStatus", "execution", "executedAt", "executedBy")) {
            if (executed.get(key) != current.get(key)) fail()
        }
        val receivedArchive = archived(row.targetOrgId, CollectionConst.BIZ_TRANSFER, row.transferId, "RECEIVED")
        requireFileReferences(receivedArchive, required)
        val received = JsonUtils.mapper.readTree(receivedArchive.snapshot)
        if (frozen.has("originalFileIds")) {
            if (received.path("sourceOriginalArchiveId").asString() != row.archiveId || received.get("originalFileIds") != tree(snapshot.originalFileIds)) fail()
            snapshot.originalFileIds.toSet().takeIf { it.isNotEmpty() }?.let { requireFileReferences(receivedArchive, it) }
        }
        if (received.path("transferId").asString() != row.transferId || received.get("execution") != tree(transfer.execution!!) ||
            received.path("collection").path("id").asString() != row.collectionId ||
            received.path("collection").path("orgId").asString() != row.targetOrgId ||
            received.path("collection").path("totalRegNo").asString() != row.totalRegNo) fail()
        return row.orgId to row.targetOrgId
    }

    private fun fail(): Nothing = throw BadRequestException("缺少与本原件一致的实际调拨留存及接收证据，不能跨馆查阅")
}
