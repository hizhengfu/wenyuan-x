package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 所有永久档案原件的公共读取边界；尚无业务策略的来源不降为普通公开文件。 */
@Component
@Transactional(noRollbackFor = [io.hizhengfu.cloud.common.exception.BadRequestException::class])
class CollectionPermanentArchiveFileReadGuard(
    private val policies: ObjectProvider<CollectionArchiveReadPolicy>,
    private val localStorage: LocalFileObjectStorage? = null,
    private val authorization: CollectionFileReadAuthorization? = null,
) : FileReadArchiveEvidenceGuard, FileReadMetadataBatchAuthorizationGuard {
    /** 逐条核验并消费命中记录，避免共享原件的历史元数据全部驻留内存。 */
    private fun visitReferences(
        path: String,
        storageType: String?,
        enforceNativeIdentity: Boolean = true,
        accept: (CollectionArchiveFileReference, tools.jackson.databind.JsonNode) -> Unit = { _, _ -> },
    ): Boolean {
        // 单次扫描只复用近期路径；不能随全馆历史文件数量无限保留派生身份。
        val locations = object : LinkedHashMap<Pair<String, String?>, Set<String>>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String?>, Set<String>>?) = size > 256
        }
        fun keys(p: String, s: String?) = locations.getOrPut(p to s) {
            try { FileReadPath.keys(p, s, localStorage) }
            catch (_: BadRequestException) { setOf(FileReadPath.key(p, s)) }
            catch (_: java.io.IOException) { setOf(FileReadPath.key(p, s)) }
        }
        val requested = keys(path, storageType)
        var found = false
        val a = CollectionPermanentArchives; val f = CollectionPermanentArchiveFiles
        val matched = mutableListOf<Triple<CollectionArchiveFileReference, tools.jackson.databind.JsonNode, Set<String>>>()
        run {
            // 缺失档案头或当前文件行仍保留引用；冻结路径与身份必须继续参与保护。
            val references = f.join(a, JoinType.LEFT, f.archiveId, a.id)
                .join(Files, JoinType.LEFT, f.fileId, Files.id)
                .select(a.id, a.orgId, a.bizType, a.bizId, a.phase,
                    f.id, f.orgId, f.bizId, f.fileId, f.metadata, Files.path, Files.storageType)
                .orderBy(f.id).fetchSize(200)
            for (r in references) {
                val ref = CollectionArchiveFileReference(r.getOrNull(a.id)?.value.orEmpty(), r.getOrNull(a.orgId).orEmpty(), r.getOrNull(a.bizType).orEmpty(), r.getOrNull(a.bizId).orEmpty(), r.getOrNull(a.phase).orEmpty(), r[f.fileId], r[f.metadata])
                val metadata = try { JsonUtils.mapper.readTree(ref.metadata) }
                    catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("永久原件文件元数据无法解析，请核对原档案") }
                val frozen = metadata.path("path").asString(); val type = metadata.path("storageType").asString()
                val saved = CollectionArchiveFileIdentity.candidates(metadata)
                val currentPath = r.getOrNull(Files.path)
                val currentStorage = r.getOrNull(Files.storageType)?.name
                if (saved.any(requested::contains) || (frozen.isNotBlank() && keys(frozen, type).any(requested::contains)) ||
                    (frozen.isNotBlank() && LegacyUploadCopyPath.matches(path, storageType, frozen, type)) ||
                    (currentPath != null && keys(currentPath, currentStorage).any(requested::contains))) {
                    if (ref.archiveId.isEmpty()) throw BadRequestException("永久原件所属档案缺失，请核对历史原件")
                    val frozenKeys = CollectionArchiveFileIdentity.keys(ref, metadata)
                    if (r[f.orgId] != ref.orgId || r[f.bizId] != ref.businessId) throw BadRequestException("永久原件归属与档案不一致")
                    found = true
                    matched += Triple(ref, metadata, frozenKeys)
                }
            }
        }
        // 先耗尽历史文件游标，再做可能查询任职和业务原件的授权；避免在同一连接的游标仍打开时重入查询。
        val transitions = mutableMapOf<String, CollectionPermanentOriginalTransitionRegistry.Evidence?>()
        val recoveries = mutableMapOf<String, CollectionPermanentOriginalRecoveryRegistry.Evidence?>()
        matched.forEach { (ref, metadata, frozenKeys) ->
            if (enforceNativeIdentity && FileReadPath.storageType(metadata.path("path").asString(), metadata.path("storageType").asString()) == EnumFileStorageType.LOCAL) {
                val frozenNative = frozenKeys.filterTo(hashSetOf()) { it.startsWith("n") || it.startsWith("u") }
                if (frozenNative.isNotEmpty() && requested.none(frozenNative::contains)) {
                    val transition = transitions.getOrPut(ref.fileId) { CollectionPermanentOriginalTransitionRegistry.one(ref.fileId) }
                    val baseline = CollectionArchiveFileIdentity.content(metadata)
                    val deviceAccepted = CollectionPermanentOriginalTransitionRegistry.accepts(
                        transition, ref.fileId, ref.orgId, frozenNative, requested, baseline)
                    val recoveryAccepted = CollectionPermanentOriginalRecoveryRegistry.accepts(
                        recoveries.getOrPut(ref.fileId) { CollectionPermanentOriginalRecoveryRegistry.one(ref.fileId) },
                        ref.fileId, ref.orgId, frozenNative, requested, baseline)
                    if (!deviceAccepted && !recoveryAccepted)
                        throw BadRequestException("永久原件本机文件身份与归档记录不一致，请核对原件与备份")
                }
            }
            accept(ref, metadata)
        }
        return found
    }

    override fun isProtected(path: String, storageType: String?) = visitReferences(path, storageType)

    override fun contentBaselines(path: String, storageType: String?): List<FileContentBaseline> {
        val values = linkedSetOf<FileContentBaseline>()
        visitReferences(path, storageType) { _, metadata ->
            CollectionArchiveFileIdentity.content(metadata)?.let(values::add)
        }
        return values.toList()
    }

    override fun objectRevisions(path: String, storageType: String?): List<FileObjectRevision> {
        val values = linkedSetOf<FileObjectRevision>()
        visitReferences(path, storageType) { ref, metadata ->
            effectiveRevision(ref, metadata)?.let(values::add)
        }
        return values.toList()
    }

    override fun requireReadable(path: String, storageType: String?) {
        checkReadable(path, storageType)
    }

    override fun checkReadable(path: String, storageType: String?): Boolean {
        val available = policies.orderedStream().toList()
        return visitReferences(path, storageType) { ref, _ -> authorize(ref, available) }
    }

    override fun checkMetadataReadable(path: String, storageType: String?): Boolean {
        val available = policies.orderedStream().toList()
        return visitReferences(path, storageType, enforceNativeIdentity = false) { ref, _ -> authorize(ref, available) }
    }

    override fun checkMetadataReadableMany(locations: List<FileMetadataLocation>): Map<FileMetadataLocation, Result<Boolean>> {
        if (locations.isEmpty()) return emptyMap()
        val state = locations.distinct().associateWith { Result.success(false) }.toMutableMap()
        val resolved = object : LinkedHashMap<Pair<String,String?>,Set<String>>(2048,0.75f,true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String,String?>,Set<String>>?) = size > 2048
        }
        fun keys(path:String,type:String?) = resolved.getOrPut(path to type) {
            try { FileReadPath.keys(path,type,localStorage) }
            catch (_: BadRequestException) { setOf(FileReadPath.key(path,type)) }
            catch (_: java.io.IOException) { setOf(FileReadPath.key(path,type)) }
        }
        val requested = state.keys.associateWith { keys(it.path,it.storageType) }
        val requestedByKey = mutableMapOf<String,MutableSet<FileMetadataLocation>>()
        requested.forEach { (location,values) -> values.forEach {key->
            requestedByKey.getOrPut(key) {linkedSetOf()}.add(location)
        } }
        val copies=requested.keys.mapNotNull {location->
            LegacyUploadCopyPath.prepare(location.path,location.storageType)?.let {location to it}
        }
        val matched = mutableListOf<Pair<CollectionArchiveFileReference,List<FileMetadataLocation>>>()
        try {
            val a=CollectionPermanentArchives;val f=CollectionPermanentArchiveFiles
            val rows=f.join(a,JoinType.LEFT,f.archiveId,a.id).join(Files,JoinType.LEFT,f.fileId,Files.id)
                .select(a.id,a.orgId,a.bizType,a.bizId,a.phase,f.id,f.orgId,f.bizId,f.fileId,f.metadata,Files.path,Files.storageType)
                .orderBy(f.id).fetchSize(200)
            for(r in rows) {
                val ref=CollectionArchiveFileReference(r.getOrNull(a.id)?.value.orEmpty(),r.getOrNull(a.orgId).orEmpty(),
                    r.getOrNull(a.bizType).orEmpty(),r.getOrNull(a.bizId).orEmpty(),r.getOrNull(a.phase).orEmpty(),r[f.fileId],r[f.metadata])
                val metadata=try {JsonUtils.mapper.readTree(ref.metadata)}
                    catch (_: tools.jackson.core.JacksonException) {throw BadRequestException("永久原件文件元数据无法解析，请核对原档案")}
                val frozen=metadata.path("path").asString();val type=metadata.path("storageType").asString()
                val saved=CollectionArchiveFileIdentity.candidates(metadata)
                val current=r.getOrNull(Files.path)
                val candidates=linkedSetOf<FileMetadataLocation>()
                fun match(values:Set<String>) = values.forEach {key->requestedByKey[key]?.let(candidates::addAll)}
                match(saved)
                if(candidates.size<requested.size && frozen.isNotBlank()) {
                    match(keys(frozen,type))
                    if(candidates.size<requested.size && copies.isNotEmpty() && FileReadPath.storageType(frozen,type)==EnumFileStorageType.LOCAL) {
                        val normalized=FileReadPath.normalized(frozen,type)
                        copies.forEach { (location,copy) ->
                            if(location !in candidates && copy.matchesNormalizedLocalSource(normalized))candidates.add(location)
                        }
                    }
                }
                if(candidates.size<requested.size && current!=null)match(keys(current,r.getOrNull(Files.storageType)?.name))
                if(candidates.isEmpty())continue
                try {
                    if(ref.archiveId.isEmpty())throw BadRequestException("永久原件所属档案缺失，请核对历史原件")
                    CollectionArchiveFileIdentity.keys(ref,metadata)
                    if(r[f.orgId]!=ref.orgId || r[f.bizId]!=ref.businessId)throw BadRequestException("永久原件归属与档案不一致")
                    matched += ref to candidates.toList()
                } catch(e:BadRequestException) {candidates.forEach {state[it]=Result.failure(e)}}
            }
        } catch(e:BadRequestException) {
            // 与单份扫描一样，无法解析的留存索引不降级为公开文件。
            return state.mapValues {Result.failure(e)}
        }
        // 先耗尽游标，再逐来源重新授权；目录批结果不进入字节读取流程。
        val available=policies.orderedStream().toList()
        matched.forEach { (ref,candidates) ->
            val pending=candidates.filter {state.getValue(it).isSuccess}
            if(pending.isNotEmpty()) {
                // 一份档案同时命中原路径和规范路径，只在本批授权一次；每份来源仍单独核查。
                val reading=try {authorize(ref,available);Result.success(true)}
                    catch(e:BadRequestException) {Result.failure(e)}
                pending.forEach {state[it]=reading}
            }
        }
        return state
    }

    override fun readEvidence(path: String, storageType: String?): FileReadEvidence {
        val available = policies.orderedStream().toList()
        val baselines = linkedSetOf<FileContentBaseline>()
        val revisions = linkedSetOf<FileObjectRevision>()
        val protected = visitReferences(path, storageType) { ref, metadata ->
            authorize(ref, available)
            CollectionArchiveFileIdentity.content(metadata)?.let(baselines::add)
            effectiveRevision(ref, metadata)?.let(revisions::add)
        }
        return FileReadEvidence(protected, baselines.toList(), revisions.toList())
    }

    override fun readArchiveEvidence(path: String, storageType: String?, archiveId: String, fileId: String): FileReadEvidence {
        val available = policies.orderedStream().toList()
        val baselines = linkedSetOf<FileContentBaseline>()
        val revisions = linkedSetOf<FileObjectRevision>()
        var selected = 0
        var selectedCustody = false
        val sharedOutgoing = linkedSetOf<CollectionArchiveFileReference>()
        visitReferences(path, storageType) { ref, metadata ->
            (authorization ?: throw BadRequestException("永久原件部署馆别读取边界尚未配置"))
                .requireDeploymentScope(setOf(ref.orgId), "永久档案原件")
            if (ref.archiveId == archiveId && ref.fileId == fileId) {
                authorize(ref, available)
                selected++
                selectedCustody = ref.businessType == io.hizhengfu.cloud.collection.common.CollectionConst.BIZ_REQUISITION &&
                    ref.phase.matches(Regex("CUSTODY_(P|KEEPER|USE|DIRECTOR)_[0-9]+"))
            }
            if (ref.phase == "OUT_EXECUTED" && ref.businessType in setOf(
                    io.hizhengfu.cloud.collection.common.CollectionConst.BIZ_REQUISITION,
                    io.hizhengfu.cloud.collection.common.CollectionConst.BIZ_LOAN,
                )) sharedOutgoing.add(ref)
            CollectionArchiveFileIdentity.content(metadata)?.let(baselines::add)
            effectiveRevision(ref, metadata)?.let(revisions::add)
        }
        if (selected > 1) throw BadRequestException("所选永久档案的原件引用重复，请核对档案")
        if (selected == 1) {
            val archive = CollectionPermanentArchiveDao.findById(archiveId)
                ?: throw BadRequestException("所选永久档案已不存在，请重新核对原件")
            if (archive.digest != CollectionCareEvidence.digest(archive.snapshot))
                throw BadRequestException("所选永久档案快照摘要不一致，请重新核对原件")
        }
        // 在用承接共享实际出库原件时，承接读取资格不能代替原出库读取资格。
        // 仅处理本次同原件扫描命中的出库引用，不扩大其他所选业务档案的职责。
        if (selected == 1 && selectedCustody) sharedOutgoing.forEach { authorize(it, available) }
        return FileReadEvidence(selected == 1, baselines.toList(), revisions.toList())
    }

    private fun authorize(ref: CollectionArchiveFileReference, available: List<CollectionArchiveReadPolicy>) {
        val matches = available.filter { ref.businessType in it.businessTypes }
        if (matches.size != 1) throw BadRequestException("该业务永久原件的读取规则尚未唯一配置，请核对业务模块")
        matches.single().requireReadable(ref)
    }

    private fun effectiveRevision(ref: CollectionArchiveFileReference, metadata: tools.jackson.databind.JsonNode): FileObjectRevision? =
        CollectionPermanentRemoteRecoveryRegistry.effective(ref.fileId, ref.orgId,
            metadata.path("path").asString(), CollectionArchiveFileIdentity.revision(metadata),
            CollectionArchiveFileIdentity.content(metadata))
}
