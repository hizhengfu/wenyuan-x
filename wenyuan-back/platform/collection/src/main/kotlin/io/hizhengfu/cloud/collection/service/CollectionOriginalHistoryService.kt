package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.domain.RequisitionReplacementRecord
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.sys.file.service.storage.FileObjectStorageService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.file.dao.FileDao
import jakarta.servlet.http.HttpServletResponse
import org.jetbrains.exposed.v1.core.*
import org.springframework.http.ContentDisposition
import org.springframework.stereotype.Component
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

data class CollectionOriginalHistoryItem(
    val id: String,
    val businessType: String,
    val businessId: String,
    val orgId: String,
    val orgName: String,
    val name: String,
    val retainedAt: LocalDateTime,
    val availability: String,
    val contentIntegrity: String,
    val businessLabel: String? = null,
)

/** 历史材料查阅只返回当前有权查阅的原件，不以旧上传人或通用文件权限放行。 */
@Component
class CollectionOriginalHistoryService(
    private val directory: CollectionActorDirectory,
    private val policy: CollectionOriginalIdentityReadPolicy,
    private val access: FileReadAccess,
    private val storage: FileObjectStorage,
    manager: PlatformTransactionManager,
    private val localStorage: LocalFileObjectStorage? = null,
    private val authorization: CollectionFileReadAuthorization? = null,
    private val mainFiles: CollectionMainFileReadPolicy? = null,
    private val archives: CollectionPermanentArchiveService? = null,
    private val businessPolicy: CollectionBusinessArchiveReadPolicy? = null,
    private val sources: ObjectProvider<CollectionOriginalFileSource>? = null,
) {
    private data class Entry(val archive: CollectionPermanentArchiveDao, val reference: CollectionArchiveFileReference,
        val metadata: JsonNode, val businessType: String, val keys: Set<String>, val rowId: String, val replacement: Boolean)
    private data class Location(val path: String?, val storageType: String, val availability: String)
    private data class DownloadRead(val actorId: String, val reference: CollectionArchiveFileReference,
        val snapshot: String, val digest: String, val location: Location, val name: String,
        val expected: FileContentBaseline?, val revision: FileObjectRevision?)
    private val reads = TransactionTemplate(manager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        timeout = 25
    }
    private val withoutTransaction = TransactionTemplate(manager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
    }

    private fun actor() = directory.requireActive(SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请登录后查阅历史材料"))
    private fun replacementStage(archive: CollectionPermanentArchiveDao) =
        archive.bizType == CollectionConst.BIZ_REQUISITION && archive.phase in setOf(
            RequisitionReplacementEvidence.REPLACED, RequisitionReplacementEvidence.CREATED)

    /** 只读取已核实责任变更两端的原件；不将任意阶段快照提升为历史身份。 */
    private fun replacementFile(archive: CollectionPermanentArchiveDao, referenceId: String,
                                files: List<CollectionPermanentArchiveFileDao>): CollectionPermanentArchiveFileDao {
        if (!replacementStage(archive)) throw BadRequestException("该阶段不是提用责任变更原件")
        val service = archives ?: throw BadRequestException("提用责任变更原件核验服务未配置")
        val business = RequisitionDao.findById(archive.bizId) ?: throw BadRequestException("提用责任变更申请缺失")
        if (business.orgId != archive.orgId) throw BadRequestException("提用责任变更原件所属馆不一致")
        RequisitionReplacementEvidence(service).verify(business)
        val record = try { JsonUtils.fromJson(archive.snapshot, RequisitionReplacementRecord::class.java) }
            catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("提用责任变更原件无法解析") }
            ?: throw BadRequestException("提用责任变更原件缺失")
        val expectedId = if (archive.phase == RequisitionReplacementEvidence.REPLACED) record.sourceId else record.replacementId
        if (archive.bizId != expectedId || archive.digest != CollectionCareEvidence.digest(archive.snapshot))
            throw BadRequestException("提用责任变更原件与新旧申请不一致")
        service.requireFileReferences(archive, "${record.request.appointmentFileIds},${record.request.withdrawalFileIds}")
        return files.singleOrNull { it.id.value == referenceId && it.archiveId == archive.id.value }
            ?: throw BadRequestException("所选文件引用不属于该责任变更原件")
    }

    private fun read(archive: CollectionPermanentArchiveDao, metadataAccess:Boolean=true,
                     retainedFiles:List<CollectionPermanentArchiveFileDao>?=null, referenceId:String?=null): Entry {
        val replacement = referenceId != null
        val retained = retainedFiles ?: CollectionPermanentArchiveFileDao.find {
            CollectionPermanentArchiveFiles.archiveId eq archive.id.value
        }.toList()
        val file = if (replacement) replacementFile(archive, referenceId!!, retained) else {
            if (archive.bizType != CollectionBusinessOriginalFiles.BIZ || !archive.phase.startsWith(CollectionBusinessOriginalFiles.PHASE))
                throw BadRequestException("该记录不是历史材料留存记录")
            retained.singleOrNull() ?: throw BadRequestException("历史材料文件引用缺失或不完整")
        }
        if (file.orgId != archive.orgId || file.bizId != archive.bizId) throw BadRequestException("历史材料所属业务不一致")
        val reference = CollectionArchiveFileReference(archive.id.value, archive.orgId, archive.bizType, archive.bizId, archive.phase, file.fileId, file.metadata)
        if (replacement) (businessPolicy ?: throw BadRequestException("提用责任变更原件读取规则未配置")).requireReadable(reference)
        else policy.requireReadable(reference)
        val metadata = try { JsonUtils.mapper.readTree(file.metadata) }
            catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("历史材料文件登记无法解析") }
        val keys = CollectionArchiveFileIdentity.keys(reference, metadata)
        val frozenPath = metadata.path("path").asString()
        if (frozenPath.isBlank()) throw BadRequestException("历史材料缺少留存位置")
        if(metadataAccess) {
            if (!access.requireMetadataReadable(frozenPath, metadata.path("storageType").asString()))
                throw BadRequestException("历史材料未接入原件读取保护，请核对归档来源")
            FileDao.findById(file.fileId)?.let {
                // 同一次来源核验中，相同存储位置不重复扫描；改位或存储类型改变仍单独核验。
                val currentType=it.storageType.name
                val frozenType=metadata.path("storageType").asString()
                if(FileReadPath.storageType(it.path,currentType)!=FileReadPath.storageType(frozenPath,frozenType) ||
                    FileReadPath.normalized(it.path,currentType)!=FileReadPath.normalized(frozenPath,frozenType))
                    access.requireMetadataReadable(it.path,currentType)
            }
        }
        return Entry(archive, reference, metadata,
            if (replacement) CollectionConst.BIZ_REQUISITION else JsonUtils.mapper.readTree(archive.snapshot).path("businessType").asString(),
            keys, if (replacement) "${archive.id.value}:${file.id.value}" else archive.id.value, replacement)
    }

    private fun effectiveRevision(entry: Entry): FileObjectRevision? =
        CollectionPermanentRemoteRecoveryRegistry.effective(entry.reference.fileId, entry.reference.orgId,
            entry.metadata.path("path").asString(), CollectionArchiveFileIdentity.revision(entry.metadata),
            CollectionArchiveFileIdentity.content(entry.metadata))

    private fun locate(entry: Entry): Location {
        val path = entry.metadata.path("path").asString()
        val type = entry.metadata.path("storageType").asString()
        if (FileReadPath.storageType(path, type) != EnumFileStorageType.LOCAL) {
            val revision = effectiveRevision(entry)
            val currentIdentity = (storage as? FileObjectStorageService)?.currentRemoteStorageIdentity()
            if (currentIdentity != null && revision != null && revision.storageIdentity != currentIdentity)
                return Location(null, type, "IDENTITY_UNVERIFIED")
            return Location(path, type, if (revision?.versionId != null) "REMOTE_VERSION_RECORDED" else "RECORDED_LOCATION")
        }
        val native = entry.keys.filter { it.startsWith("n") || it.startsWith("u") }.toSet()
        if (native.isEmpty() || localStorage == null) return Location(null, type, "IDENTITY_UNVERIFIED")
        val current = FileDao.findById(entry.reference.fileId)
        // 本机身份直接匹配时已有归档依据；只有身份改变才读取设备号承接或恢复证明。
        val transition by lazy {CollectionPermanentOriginalTransitionRegistry.one(entry.reference.fileId)}
        val baseline by lazy {CollectionArchiveFileIdentity.content(entry.metadata)}
        val candidates = listOf(path) + listOfNotNull(current?.takeIf { it.storageType == EnumFileStorageType.LOCAL }?.path)
        for (candidate in candidates.distinct()) {
            val keys = try { FileReadPath.keys(candidate, type, localStorage) }
                catch (_: java.io.IOException) { emptySet() }
                catch (_: BadRequestException) { emptySet() }
            if (keys.any(native::contains) || CollectionPermanentOriginalTransitionRegistry.accepts(
                    transition, entry.reference.fileId, entry.reference.orgId, native, keys, baseline) ||
                CollectionPermanentOriginalRecoveryRegistry.accepts(
                    CollectionPermanentOriginalRecoveryRegistry.one(entry.reference.fileId),
                    entry.reference.fileId, entry.reference.orgId, native, keys, baseline))
                return Location(candidate, type, "AVAILABLE")
        }
        return Location(null, type, "IDENTITY_UNVERIFIED")
    }

    @Transactional
    fun page(businessType: String?, businessId: String?, keyword: String?, pageNum: Long = 1, pageSize: Int = 20): GenericPage<CollectionOriginalHistoryItem> {
        fun read(): GenericPage<CollectionOriginalHistoryItem> = mainFiles?.withMetadataReadProofs {
            pageData(businessType, businessId, keyword, pageNum, pageSize)
        } ?: pageData(businessType, businessId, keyword, pageNum, pageSize)
        return authorization?.withMetadataReadIdentity(::read) ?: read()
    }

    private fun pageData(businessType: String?, businessId: String?, keyword: String?, pageNum: Long, pageSize: Int): GenericPage<CollectionOriginalHistoryItem> {
        fun identity(user: io.hizhengfu.cloud.common.domain.SecurityUser)=listOf(user.id,user.roles,user.roleCodes,user.permissions,user.groupId,user.groupIds,user.dataType)
        val currentActor=identity(actor())
        val rows = CollectionPermanentArchiveDao.find {
            ((CollectionPermanentArchives.bizType eq CollectionBusinessOriginalFiles.BIZ) or
                ((CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and
                    (CollectionPermanentArchives.phase inList listOf(RequisitionReplacementEvidence.REPLACED, RequisitionReplacementEvidence.CREATED)))) and
                (businessId?.trim()?.takeIf(String::isNotBlank)?.let { CollectionPermanentArchives.bizId eq it } ?: Op.TRUE)
        }.sortedWith(compareByDescending<CollectionPermanentArchiveDao> { it.createTime }.thenByDescending { it.id.value })
        val type = businessType?.trim()?.takeIf(String::isNotBlank)
        val search = keyword?.trim()?.takeIf(String::isNotBlank)
        val selected = rows.filter { archive ->
            // 指定来源先过滤，避免为与本次查询无关的历史材料扫描全部原件来源。
            if(type!=null) {
                val recordedType=try {if (replacementStage(archive)) CollectionConst.BIZ_REQUISITION else JsonUtils.mapper.readTree(archive.snapshot).path("businessType").asString()}
                    catch (_: tools.jackson.core.JacksonException) {return@filter false}
                if(type!=recordedType)return@filter false
            }
            true
        }
        // 同一事务按有界批次读取引用，避免每份历史材料单独查询；缺失或多份引用仍按原规则拒绝。
        val retainedFiles = selected.map {it.id.value}.chunked(200).flatMap {archiveIds ->
            CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId inList archiveIds}.toList()
        }.groupBy {it.archiveId}
        val inspected = selected.flatMap { archive ->
            val references = retainedFiles[archive.id.value].orEmpty()
            val selections = if (replacementStage(archive)) references.map { it.id.value } else listOf<String?>(null)
            selections.mapNotNull { referenceId ->
                // 单条缺少查阅资格或原件异常时不泄露文件名、来源、路径或计数。
                val entry = try { read(archive,metadataAccess=false,retainedFiles=references,referenceId=referenceId) }
                    catch (_: BadRequestException) { return@mapNotNull null }
                val name = entry.metadata.path("name").asString().ifBlank { "原始材料" }
                entry to name
            }
        }
        fun locations(entry:Entry):List<FileMetadataLocation> {
            val frozen=FileMetadataLocation(entry.metadata.path("path").asString(),entry.metadata.path("storageType").asString())
            val current=FileDao.findById(entry.reference.fileId)?.let {FileMetadataLocation(it.path,it.storageType.name)}
            return listOfNotNull(frozen,current).distinct()
        }
        val requested=inspected.associate {(entry,_)->entry.rowId to locations(entry)}
        // 同一原件的多个历史阶段共用本次目录核验；按位置分批，全部来源仍参与授权。
        // 此结果仅属于这次查询，之后的请求和下载重新读取来源及权限。
        val metadataReads=requested.values.flatten().distinct().chunked(128)
            .flatMap {access.requireMetadataReadableMany(it).entries}.associate {it.key to it.value}
        val allowed=inspected.mapNotNull { (entry,name)->
            try {
                val paths=requested.getValue(entry.rowId)
                if(!metadataReads.getValue(paths.first()).getOrThrow())throw BadRequestException("历史材料未接入原件读取保护，请核对归档来源")
                paths.drop(1).forEach {metadataReads.getValue(it).getOrThrow()}
                val archive=entry.archive
                CollectionOriginalHistoryItem(entry.rowId,entry.businessType,archive.bizId,archive.orgId,
                    CollectionOrgDao.findById(archive.orgId)?.name ?: archive.orgId,name,archive.createTime,locate(entry).availability,
                    if(CollectionArchiveFileIdentity.content(entry.metadata)==null)"NOT_RECORDED" else "SHA256_RECORDED")
            } catch (_:BadRequestException) {null}
        }
        val businessKeys = allowed.map { CollectionOriginalBusinessKey(it.orgId, it.businessType, it.businessId) }.toSet()
        val labels = sources?.orderedStream()?.toList().orEmpty().flatMap { it.businessLabels }.flatMap { it.read(businessKeys).entries }
            .groupBy({ it.key }, { it.value }).mapValues { (_, values) -> values.distinct().singleOrNull() }
        val labeled = allowed.map { it.copy(businessLabel=labels[CollectionOriginalBusinessKey(it.orgId, it.businessType, it.businessId)]) }
            .filter { search == null || listOfNotNull(it.name, it.businessLabel, it.businessId).any { text -> text.contains(search, ignoreCase=true) } }
        if(identity(actor())!=currentActor)throw BadRequestException("查询期间历史材料查阅身份已变化，请重新核查")
        return GenericPage<CollectionOriginalHistoryItem>().apply {
            this.pageNum = pageNum; this.pageSize = pageSize; total = labeled.size.toLong()
            val from = if (this.pageSize == 0) 0 else ((this.pageNum - 1) * this.pageSize).coerceAtMost(labeled.size.toLong()).toInt()
            list = if (this.pageSize == 0) labeled else labeled.subList(from, (from.toLong() + this.pageSize).coerceAtMost(labeled.size.toLong()).toInt())
        }
    }

    private fun prepareDownload(id: String): DownloadRead = requireNotNull(reads.execute {
        val user = actor()
        val compound = if (':' in id) {
            if (!id.matches(Regex("[0-9]{15,20}:[0-9]{15,20}"))) throw BadRequestException("历史材料引用格式不正确")
            id.split(':')
        } else null
        val archive = CollectionPermanentArchiveDao.findById(compound?.first() ?: id) ?: throw BadRequestException("历史材料不存在或不可查阅")
        val entry = read(archive, referenceId=compound?.last())
        val location = locate(entry)
        val path = location.path ?: throw BadRequestException("历史材料的文件身份待核实，不能用当前其他文件代替")
        if (entry.replacement) access.readArchiveEvidence(path, location.storageType, archive.id.value, entry.reference.fileId)
        else access.requireReadable(path, location.storageType)
        DownloadRead(user.id, entry.reference, archive.snapshot, archive.digest, location,
            entry.metadata.path("name").asString(), CollectionArchiveFileIdentity.content(entry.metadata),
            effectiveRevision(entry))
    })

    fun download(id: String, response: HttpServletResponse) {
        // 暂挂范围本身不写库；先恢复调用者上下文，再原样抛出客户端 IOException 等失败。
        requireNotNull(withoutTransaction.execute { runCatching { writeDownload(id, response) } }).getOrThrow()
    }

    private fun writeDownload(id: String, response: HttpServletResponse) {
        // 不让网络、内容校验或客户端写出持有旧事务快照；调用方已有事务也必须挂起。
        val original = prepareDownload(id)
        val path = requireNotNull(original.location.path)
        val expected = original.expected
        fun output(input: java.io.InputStream) {
            // 重新加载独立事务中的账号、原件引用和位置，不把旧 DAO 带到新事务复验。
            if (prepareDownload(id) != original) throw BadRequestException("下载期间历史材料登记或查阅身份发生变化，请重新核查")
            response.setHeader("Cache-Control", "private, no-store")
            response.setHeader("X-Content-Type-Options", "nosniff")
            response.setHeader("X-Collection-Content-Integrity", if (expected == null) "not-recorded" else "sha256-verified")
            response.contentType = "application/octet-stream"
            val name = original.name.replace(Regex("[\\r\\n]"), "").ifBlank { "原始材料" }
            response.setHeader("Content-Disposition", ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
            input.copyTo(response.outputStream)
        }
        val revision = original.revision
        val stream = if (revision == null) storage.openStream(path, original.location.storageType) else storage.openRevision(path, original.location.storageType, revision)
        stream.use { input ->
            if (expected == null) output(input)
            else expected.withVerifiedCopy(input) { copy ->
                java.nio.file.Files.newInputStream(copy).use(::output)
            }
        }
    }
}
