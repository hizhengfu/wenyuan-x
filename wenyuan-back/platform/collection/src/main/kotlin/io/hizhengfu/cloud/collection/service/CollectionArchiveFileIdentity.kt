package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileObjectRevision
import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.common.file.EnumFileStorageType
import io.hizhengfu.cloud.sys.file.dao.FileDao
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

/** 读取身份与可用的服务端内容基线分别保存；旧元数据保持可读，不原地补造历史摘要。 */
object CollectionArchiveFileIdentity {
    private const val KEYS = "_archiveReadKeys"
    private const val DIGEST = "_archiveReadIdentityDigest"
    private const val CONTENT = "_archiveContent"
    private const val REVISION = "_archiveObjectRevision"
    /** 仅用于定位候选来源；匹配后仍须校验完整身份，不能把这些键本身当作授权。 */
    fun candidates(metadata: JsonNode): Set<String> = metadata.path(KEYS).filter { it.isString }.map { it.asString() }.toSet()
    private fun digest(org: String, biz: String, id: String, file: String, path: String, storage: String, keys: Set<String>, content: CollectionOriginalContent? = null, revision: FileObjectRevision? = null): String {
        val identity = listOf(org, biz, id, file, path, storage, keys.sorted())
        val contentIdentity = if (content == null) identity else identity + listOf("SHA-256", content.sha256, content.size)
        val revisionIdentity = if (revision == null) contentIdentity else contentIdentity + listOf("S3_REVISION", revision.storageIdentity, revision.versionId, revision.eTag)
        return CollectionCareEvidence.digest(JsonUtils.toJson(revisionIdentity)!!)
    }

    fun metadata(file: FileDao, org: String, biz: String, id: String, keys: Set<String>, content: CollectionOriginalContent? = null, revision: FileObjectRevision? = null): String {
        val node = JsonUtils.mapper.readTree(JsonUtils.toJson(file.toModel())) as ObjectNode
        node.set(KEYS, JsonUtils.mapper.valueToTree(keys.sorted()))
        if (content != null) node.set(CONTENT, JsonUtils.mapper.valueToTree(mapOf("algorithm" to "SHA-256", "sha256" to content.sha256, "size" to content.size)))
        if (revision != null) node.set(REVISION, JsonUtils.mapper.valueToTree(mapOf("storageIdentity" to revision.storageIdentity, "versionId" to revision.versionId, "eTag" to revision.eTag)))
        node.put(DIGEST, digest(org, biz, id, file.id.value, file.path, file.storageType.name, keys, content, revision))
        return node.toString()
    }

    fun keys(ref: CollectionArchiveFileReference, metadata: JsonNode): Set<String> {
        // 旧格式可以没有内容摘要，但仍须保留完整的文件定位身份；空对象不能冒充旧档案。
        if (!metadata.isObject || metadata.path("id").asString() != ref.fileId ||
            !metadata.path("path").isString || metadata.path("path").asString().isBlank() ||
            EnumFileStorageType.entries.none { it.name == metadata.path("storageType").asString() })
            throw BadRequestException("永久原件文件定位身份缺失或不一致，请核对原档案")
        val saved = metadata.get(KEYS)
        if (saved == null && metadata.get(DIGEST) == null && metadata.get(CONTENT) == null && metadata.get(REVISION) == null) return emptySet()
        if (saved == null || !saved.isArray) throw BadRequestException("永久原件读取身份格式不完整")
        val keys = saved.mapNotNull { if (it.isString) it.asString() else null }.toSet()
        if (keys.isEmpty() || keys.size != saved.size() || keys.any { it.length != 64 } ||
            metadata.path(DIGEST).asString() != digest(ref.orgId, ref.businessType, ref.businessId, ref.fileId,
                metadata.path("path").asString(), metadata.path("storageType").asString(), keys, content(metadata), revision(metadata)))
            throw BadRequestException("永久原件读取身份摘要不一致，请核对归档文件")
        return keys
    }

    /** 调用前须校验 keys，使内容基线与所属馆、业务和文件身份一起验证。 */
    fun content(metadata: JsonNode): CollectionOriginalContent? {
        val value = metadata.get(CONTENT) ?: return null
        val hash = value.path("sha256").asString()
        val size = value.get("size")
        if (!value.isObject || value.path("algorithm").asString() != "SHA-256" || !hash.matches(Regex("[0-9a-f]{64}")) ||
            size == null || !size.isIntegralNumber || !size.canConvertToLong() || size.longValue() < 0)
            throw BadRequestException("永久原件内容摘要格式异常，请核对原档案")
        return CollectionOriginalContent(hash, size.longValue())
    }
    fun revision(metadata: JsonNode): FileObjectRevision? {
        val value = metadata.get(REVISION) ?: return null
        val identity = value.get("storageIdentity")
        val version = value.get("versionId")
        val tag = value.get("eTag")
        if (!value.isObject || identity == null || !identity.isString ||
            (version != null && !version.isNull && !version.isString) || (tag != null && !tag.isNull && !tag.isString) ||
            content(metadata) == null || FileReadPath.storageType(metadata.path("path").asString(), metadata.path("storageType").asString()) != EnumFileStorageType.RUSTFS)
            throw BadRequestException("永久原件远端版本信息格式异常")
        return FileObjectRevision(identity.asString(), version?.takeIf { it.isString }?.asString(), tag?.takeIf { it.isString }?.asString())
    }

}
