package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.file.dao.FileContents
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.*
import tools.jackson.databind.JsonNode

/** 业务字段及普通附件关联中的真实文件来源；读取身份不表示审批或签署完成。 */
data class CollectionOriginalFileReference(val orgId: String, val businessType: String, val businessId: String, val file: String, val recordId: String = businessId)

interface CollectionOriginalFileSource {
    val tables: List<CollectionOriginalFileTable>
    val businessLabels: List<CollectionOriginalBusinessLabel> get() = emptyList()
    fun references(businessType: String? = null, businessId: String? = null): List<CollectionOriginalFileReference> =
        tables.filter { businessType == null || it.businessType == businessType }.flatMap { it.references(businessId) }.distinct()
}

/** 只选取明确声明的附件列，JSON 中只读取命名为文件引用的字段，不从正文猜测文件标识。 */
data class CollectionOriginalFileTable(
    val table: IdTable<String>,
    val orgId: Column<String>,
    val businessType: String,
    val files: List<Column<*>> = emptyList(),
    val json: List<Column<*>> = emptyList(),
    val rawJson: List<Column<*>> = emptyList(),
    val owner: Column<String>? = null,
    val bindings: Boolean = true,
) {
    fun references(businessId: String?): List<CollectionOriginalFileReference> = buildList {
        fun ownerId(r: ResultRow) = owner?.let { r[it] } ?: r[table.id].value
        fun condition(): Op<Boolean> = if (businessId == null) Op.TRUE else owner?.eq(businessId) ?: (table.id eq businessId)
        fun addFiles(r: ResultRow, raw: String?) {
            BusinessAttachmentBindingPlanner.plan(ownerId(r), "业务原件", raw).forEach { add(CollectionOriginalFileReference(r[orgId], businessType, ownerId(r), it.fileId, r[table.id].value)) }
        }
        val columns = (listOf(table.id, orgId) + listOfNotNull(owner) + files + json + rawJson).distinct()
        var offset = 0L
        while (true) {
            val batch = table.select(columns).where { condition() }.orderBy(table.id).limit(200).offset(offset).toList()
            if (batch.isEmpty()) break
            for (r in batch) {
                files.forEach { addFiles(r, r[it] as? String) }
                json.forEach { column -> r[column]?.let { value -> CollectionOriginalJsonFiles.references(JsonUtils.mapper.valueToTree(value)).forEach { addFiles(r, it) } } }
                rawJson.forEach { column -> (r[column] as? String)?.takeIf(String::isNotBlank)?.let { raw ->
                    val node = try { JsonUtils.mapper.readTree(raw) } catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("业务原件字段格式异常，请核对原记录") }
                    CollectionOriginalJsonFiles.references(node).forEach { addFiles(r, it) }
                } }
            }
            if (batch.size < 200) break
            offset += batch.size
        }
        if (bindings) {
            table.join(FileContents, JoinType.INNER, table.id, FileContents.contentId)
                .select((listOf(table.id, orgId, FileContents.fileId) + listOfNotNull(owner)).distinct()).where { condition() }
                .forEach { r -> addFiles(r, r[FileContents.fileId]) }
        }
    }.distinct()
}

object CollectionOriginalJsonFiles {
    private val fileFieldNames = setOf("fileId", "fileIds", "attachmentIds", "coverImage", "images", "conditionPhotoIds", "damagePhotoIds", "signImageId")
    private fun isFile(name: String) = name.endsWith("FileId") || name.endsWith("FileIds") || name.endsWith("AttachmentIds") ||
        name in fileFieldNames
    fun references(node: JsonNode): List<String> = buildList {
        fun values(value: JsonNode) {
            if (value.isString) add(value.asString()) else if (value.isArray) value.forEach { values(it) }
        }
        fun visit(value: JsonNode) {
            if (value.isObject) value.properties().forEach { (name, child) -> if (isFile(name)) values(child) else visit(child) }
            else if (value.isArray) value.forEach { visit(it) }
        }
        visit(node)
    }
}
