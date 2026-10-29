package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchiveFiles
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchives
import io.hizhengfu.cloud.collection.service.CollectionArchiveFileIdentity
import io.hizhengfu.cloud.collection.service.CollectionArchiveFileReference
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence
import io.hizhengfu.cloud.collection.service.CollectionPermanentOriginalTransitionRegistry
import io.hizhengfu.cloud.collection.service.CollectionPermanentOriginalRecoveryRegistry
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.EnumFileStorageType
import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.IOException
import java.nio.file.InvalidPathException

/** 只读检查本地永久档案冻结的原生文件身份；不把文件记录或内容摘要存在当作原件恢复。 */
@Component
@Transactional(timeout = 55)
class CollectionPermanentOriginalIdentityDiagnosticCheck(
    private val localStorage: LocalFileObjectStorage? = null,
) : DeploymentDiagnosticCheck {
    override val descriptor = DeploymentCheckDescriptor(
        "collection.files.permanent-original-identity", "FILE_SYSTEM", "藏品管理", "永久档案本地原件身份",
        EnumDeploymentCheckSeverity.CRITICAL, timeoutMillis = 60_000, order = 120,
    )

    override fun check(): DeploymentCheckOutcome = CollectionPermanentOriginalIdentityInspection.inspect(localStorage)
}

internal object CollectionPermanentOriginalIdentityInspection {
    fun inspect(localStorage: LocalFileObjectStorage?, sampleLimit: Int = 10): DeploymentCheckOutcome {
        require(sampleLimit in 1..10)
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Permanent original identity scan interrupted")
        val archive = CollectionPermanentArchives
        val reference = CollectionPermanentArchiveFiles
        val totalBefore = reference.selectAll().count()
        var scanned = 0L
        var local = 0L
        var remote = 0L
        var brokenReferences = 0L
        var invalidMetadata = 0L
        var missingNativeIdentity = 0L
        var unverifiedStorage = 0L
        var nativeMismatch = 0L
        var acceptedTransitions = 0L
        var acceptedRecoveries = 0L
        var distinctMismatchedFiles = 0L
        var lastMismatchedFileId: String? = null
        val samples = mutableListOf<Map<String, String>>()
        val sampledIssues = mutableSetOf<Pair<String, String>>()
        val verifiedHeads = object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 256
        }
        val transitions = CollectionPermanentOriginalTransitionRegistry.all()
        val recoveries = CollectionPermanentOriginalRecoveryRegistry.all()
        val rows = reference.join(archive, JoinType.LEFT, reference.archiveId, archive.id)
            .join(Files, JoinType.LEFT, reference.fileId, Files.id)
            .select(reference.id, reference.archiveId, reference.orgId, reference.bizId,
                reference.fileId, reference.metadata, archive.id, archive.orgId, archive.bizType,
                archive.bizId, archive.phase, archive.snapshot, archive.digest,
                Files.id, Files.path, Files.storageType)
            .orderBy(reference.fileId to SortOrder.ASC, reference.id to SortOrder.ASC).fetchSize(200)
        for (row in rows) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Permanent original identity scan interrupted")
            scanned++
            val archiveId = row[reference.archiveId]
            val fileId = row[reference.fileId]
            fun issue(reason: String) {
                if (samples.size < sampleLimit && sampledIssues.add(fileId to reason)) samples += mapOf(
                    "archiveId" to archiveId, "fileId" to fileId, "reason" to reason,
                )
            }
            val headId = row.getOrNull(archive.id)?.value
            val fileExists = row.getOrNull(Files.id) != null
            val headValid = headId != null && verifiedHeads.getOrPut(headId) {
                row[archive.digest] == CollectionCareEvidence.digest(row[archive.snapshot])
            }
            if (!headValid || !fileExists || row[reference.orgId] != row.getOrNull(archive.orgId) ||
                row[reference.bizId] != row.getOrNull(archive.bizId)) {
                brokenReferences++; issue("BROKEN_REFERENCE"); continue
            }
            val metadata = try { JsonUtils.mapper.readTree(row[reference.metadata]) }
                catch (_: tools.jackson.core.JacksonException) { null }
            val frozen = if (metadata != null) try {
                CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(
                    archiveId, row[archive.orgId], row[archive.bizType], row[archive.bizId],
                    row[archive.phase], fileId, row[reference.metadata],
                ), metadata)
            } catch (_: BadRequestException) { null } else null
            if (frozen == null) { invalidMetadata++; issue("INVALID_METADATA"); continue }
            val path = metadata!!.path("path").asString()
            val type = metadata.path("storageType").asString()
            if (FileReadPath.storageType(path, type) != EnumFileStorageType.LOCAL) {
                remote++; continue
            }
            local++
            val native = frozen.filter { it.startsWith("n") || it.startsWith("u") }.toSet()
            if (native.isEmpty()) { missingNativeIdentity++; issue("NO_FROZEN_NATIVE_IDENTITY"); continue }
            if (localStorage == null) { unverifiedStorage++; issue("LOCAL_STORAGE_UNAVAILABLE"); continue }
            val currentPath = row.getOrNull(Files.path)
                ?.takeIf { row.getOrNull(Files.storageType) == EnumFileStorageType.LOCAL }
            val candidates = listOfNotNull(path, currentPath).distinct()
            var acceptedTransition = false
            var acceptedRecovery = false
            val matched = candidates.any { candidate ->
                try {
                    val observed = FileReadPath.keys(candidate, "LOCAL", localStorage)
                    if (observed.any(native::contains)) true else {
                        val accepted = CollectionPermanentOriginalTransitionRegistry.accepts(
                            transitions[fileId], fileId, row[archive.orgId], native, observed,
                            CollectionArchiveFileIdentity.content(metadata))
                        if (accepted) acceptedTransition = true
                        if (accepted) true else CollectionPermanentOriginalRecoveryRegistry.accepts(
                            recoveries[fileId], fileId, row[archive.orgId], native, observed,
                            CollectionArchiveFileIdentity.content(metadata)).also { if (it) acceptedRecovery = true }
                    }
                }
                catch (_: BadRequestException) { false }
                catch (_: IOException) { false }
                catch (_: InvalidPathException) { false }
            }
            if (acceptedTransition && matched) acceptedTransitions++
            if (acceptedRecovery && matched) acceptedRecoveries++
            if (!matched) {
                nativeMismatch++
                if (lastMismatchedFileId != fileId) {
                    distinctMismatchedFiles++
                    lastMismatchedFileId = fileId
                }
                issue("NATIVE_IDENTITY_MISMATCH")
            }
        }
        val totalAfter = reference.selectAll().count()
        val facts = listOf(
            DeploymentSafeFact("scannedArchiveFiles", scanned.toString()),
            DeploymentSafeFact("localReferences", local.toString()),
            DeploymentSafeFact("remoteReferencesNotByteChecked", remote.toString()),
            DeploymentSafeFact("brokenReferences", brokenReferences.toString()),
            DeploymentSafeFact("invalidMetadata", invalidMetadata.toString()),
            DeploymentSafeFact("missingNativeIdentity", missingNativeIdentity.toString()),
            DeploymentSafeFact("localStorageUnavailable", unverifiedStorage.toString()),
            DeploymentSafeFact("nativeIdentityMismatch", nativeMismatch.toString()),
            DeploymentSafeFact("acceptedDeviceTransitionReferences", acceptedTransitions.toString()),
            DeploymentSafeFact("acceptedRecoveryReferences", acceptedRecoveries.toString()),
            DeploymentSafeFact("distinctMismatchedFiles", distinctMismatchedFiles.toString()),
            DeploymentSafeFact("inspectionScope", "frozen archive linkage and local native identity only; not original bytes, remote revisions or business authorization"),
        ) + samples.mapIndexed { index, sample ->
            DeploymentSafeFact("issueSample${index + 1}", JsonUtils.toJson(sample)!!)
        }
        val remediation = listOf(DeploymentRemediation(
            EnumDeploymentRemediationType.FILE_SYSTEM, "核对永久档案原文件与备份，不改写冻结身份",
            listOf("在隔离副本核对归档时的文件身份、当前挂载、文件记录和备份。",
                "保留原档案与摘要；有据恢复原件或按完整业务引用图处理开发异常数据后重新扫描。",
                "本项不读取文件字节，也不验证远端对象版本；须另行完成内容与读取权限核验。"),
        ))
        return when {
            totalBefore != scanned || totalAfter != scanned -> DeploymentCheckOutcome(
                EnumDeploymentCheckState.ERROR, "永久档案文件扫描期间记录数变化或扫描不完整，请暂停写入后复查",
                "COLLECTION_PERMANENT_ORIGINAL_SCAN_INCOMPLETE", facts, remediation,
            )
            brokenReferences + invalidMetadata + missingNativeIdentity + unverifiedStorage + nativeMismatch > 0 ->
                DeploymentCheckOutcome(EnumDeploymentCheckState.FAIL,
                    "永久档案本地原件身份或冻结关联存在待核事项，不能认定原件可读取",
                    "COLLECTION_PERMANENT_ORIGINAL_IDENTITY_UNRESOLVED", facts, remediation,
                )
            else -> DeploymentCheckOutcome(EnumDeploymentCheckState.PASS,
                "永久档案本地原件冻结身份与当前挂载一致；内容和远端版本仍须另行核验",
                safeFacts = facts,
            )
        }
    }
}
