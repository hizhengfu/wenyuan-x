package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.EnumFileStorageType
import io.hizhengfu.cloud.common.file.FileContentBaseline
import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.file.dao.FileDao
import org.jetbrains.exposed.v1.core.eq
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.HexFormat

data class CollectionPermanentOriginalTransitionRequest(
    val fileId: String,
    val formerDeviceHex: String,
    val reason: String,
)

/** 仅用于同一本机文件在卷设备号重新编号后的留痕承接，不是跨主机备份恢复入口。 */
@Component
class CollectionPermanentOriginalTransitionService(private val storage: LocalFileObjectStorage) {
    private fun key(prefix: String, value: String): String = prefix + HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))).take(63)

    @Transactional
    fun record(request: CollectionPermanentOriginalTransitionRequest): String {
        val actor = SecurityUtils.getUserDetail()
        if (SecurityContextHolder.getContext().authentication?.authorities?.none { it.authority == "ADMIN" } != false)
            throw BadRequestException("仅系统管理员可登记永久原件设备号承接")
        if (!request.fileId.matches(Regex("[A-Za-z0-9-]{1,64}")) ||
            !request.formerDeviceHex.matches(Regex("[0-9a-fA-F]{1,16}")) ||
            request.reason.trim().length !in 20..2000)
            throw BadRequestException("承接编号、旧设备号或核验说明格式无效")
        val oldDevice = request.formerDeviceHex.lowercase().trimStart('0').ifEmpty { "0" }
        val file = FileDao.findById(request.fileId) ?: throw BadRequestException("承接文件不存在")
        if (file.storageType != EnumFileStorageType.LOCAL) throw BadRequestException("此入口仅承接本地原件")
        val references = CollectionPermanentArchiveFileDao.find {
            CollectionPermanentArchiveFiles.fileId eq request.fileId
        }.toList()
        if (references.isEmpty()) throw BadRequestException("文件没有永久档案引用")
        val orgs = linkedSetOf<String>()
        val frozenKeys = linkedSetOf<String>()
        val baselines = linkedSetOf<FileContentBaseline>()
        val uploadTimes = linkedSetOf<Long>()
        for (reference in references) {
            val archive = CollectionPermanentArchiveDao.findById(reference.archiveId)
                ?: throw BadRequestException("永久档案头缺失")
            if (archive.digest != CollectionCareEvidence.digest(archive.snapshot) ||
                reference.orgId != archive.orgId || reference.bizId != archive.bizId)
                throw BadRequestException("永久档案或引用摘要不一致")
            val metadata = try { JsonUtils.mapper.readTree(reference.metadata) }
                catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("永久原件冻结元数据无法解析") }
            val frozen = CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(
                archive.id.value, archive.orgId, archive.bizType, archive.bizId, archive.phase,
                reference.fileId, reference.metadata), metadata)
            if (metadata.path("path").asString() != file.path ||
                metadata.path("storageType").asString() != EnumFileStorageType.LOCAL.name)
                throw BadRequestException("原件当前路径或存储类型与全部冻结档案不一致")
            val uploaded = metadata.path("createTime")
            if (!uploaded.isIntegralNumber || !uploaded.canConvertToLong())
                throw BadRequestException("原件上传时间缺少可核验基线")
            orgs += archive.orgId
            frozenKeys += frozen.filter { it.startsWith("n") }
            CollectionArchiveFileIdentity.content(metadata)?.let(baselines::add)
            uploadTimes += uploaded.longValue()
        }
        if (orgs.size != 1 || frozenKeys.size != 1 || baselines.size != 1 || uploadTimes.size != 1 ||
            references.any { JsonUtils.mapper.readTree(it.metadata).get("_archiveContent") == null })
            throw BadRequestException("共享原件的馆别、冻结身份或内容基线不一致，不能自动承接")
        val before = storage.readIdentity(file.path)
        val native = before.nativeIdentity ?: throw BadRequestException("当前文件缺少本机身份")
        val stable = before.stableIdentity ?: throw BadRequestException("当前文件缺少稳定身份")
        val divider = native.lastIndexOf('\u0000')
        val parsed = (if (divider >= 0) Regex("\\(dev=([0-9a-fA-F]+),ino=([0-9]+)\\)")
            .matchEntire(native.substring(divider + 1)) else null)
            ?: throw BadRequestException("本机文件系统身份格式无法核验")
        val rootPrefix = native.substring(0, divider + 1)
        val currentDevice = parsed.groupValues[1].lowercase()
        val inode = parsed.groupValues[2]
        if (oldDevice == currentDevice) throw BadRequestException("设备号未变化，无需承接")
        val oldNative = key("n", "$rootPrefix(dev=$oldDevice,ino=$inode)")
        if (oldNative != frozenKeys.single()) throw BadRequestException("旧设备号与永久档案冻结身份不符")
        val birthMillis = stable.substringAfterLast("created=", "").toLongOrNull()
            ?: throw BadRequestException("文件创建时间无法核验")
        if (kotlin.math.abs(birthMillis - uploadTimes.single()) >= 1000)
            throw BadRequestException("文件创建时间与归档上传记录不一致")
        val baseline = baselines.single()
        storage.openStream(file.path).use { baseline.requireMatch(FileContentBaseline.read(it, maximumBytes = baseline.size)) }
        val after = storage.readIdentity(file.path)
        if (before != after) throw BadRequestException("核验期间文件身份发生变化，请重新检查")
        val proposed = CollectionPermanentOriginalTransitionRegistry.Evidence(
            request.fileId, orgs.single(), oldNative, key("u", stable), baseline.sha256,
            baseline.size, oldDevice, currentDevice, inode, birthMillis,
            request.reason.trim(), actor.id, "",
        )
        val sealed = proposed.copy(digest = CollectionPermanentOriginalTransitionRegistry.seal(proposed))
        CollectionPermanentOriginalTransitionDao.findById(request.fileId)?.let { current ->
            val saved = CollectionPermanentOriginalTransitionRegistry.one(request.fileId)
            if (saved != sealed) throw BadRequestException("该文件已有不同承接记录，不能覆盖")
            return current.id.value
        }
        CollectionPermanentOriginalTransitionDao.new(request.fileId) {
            orgId = sealed.orgId
            fileId = sealed.fileId
            frozenNativeKey = sealed.frozenNativeKey
            stableKey = sealed.stableKey
            contentSha256 = sealed.contentSha256
            contentSize = sealed.contentSize
            formerDevice = sealed.formerDevice
            this.currentDevice = sealed.currentDevice
            this.inode = sealed.inode
            this.birthMillis = sealed.birthMillis
            reason = sealed.reason
            createBy = sealed.assessor
            evidenceDigest = sealed.digest
        }
        return request.fileId
    }
}
