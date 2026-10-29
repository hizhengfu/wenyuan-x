package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.CollectionPrintTemplateVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime

/** 馆内可维护的打印模板配置。系统默认模板仍由 CollectionPrintTemplateRegistry 提供。 */
object CollectionPrintTemplates : OrgAuditUIDTable("col_print_template") {
    val type = enumeration<EnumCollectionPrintType>("type").comment("打印表单类型")
    val name = varchar("name", 100).comment("模板名称")
    val title = varchar("title", 200).comment("表单标题")
    val documentLabel = varchar("document_label", 100).comment("单号字段名")
    val collectionTitle = varchar("collection_title", 100).comment("藏品清单标题")
    val templatePath = varchar("template_path", 512).nullable().comment("上传的Word模板路径")
    val signatures = text("signatures").nullable().comment("签字栏，逗号分隔")
    val enabled = bool("enabled").default(true).comment("是否启用")
    val sort = integer("sort", checkConstraintName = shortCheckConstraintName("sort")).default(0).comment("排序")
    val remark = varchar("remark", 1000).nullable().comment("备注")

    init {
        index(shortIndexName("org_id_type"), false, orgId, type)
    }
}

class CollectionPrintTemplateDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionPrintTemplates) {
    companion object : OrgAuditUIDEntityClass<CollectionPrintTemplateDao>(CollectionPrintTemplates)

    var type by CollectionPrintTemplates.type
    var name by CollectionPrintTemplates.name
    var title by CollectionPrintTemplates.title
    var documentLabel by CollectionPrintTemplates.documentLabel
    var collectionTitle by CollectionPrintTemplates.collectionTitle
    var templatePath by CollectionPrintTemplates.templatePath
    var signatures by CollectionPrintTemplates.signatures
    var enabled by CollectionPrintTemplates.enabled
    var sort by CollectionPrintTemplates.sort
    var remark by CollectionPrintTemplates.remark

    fun toModel(): CollectionPrintTemplateVo = CollectionPrintTemplateVo(
        code = type.name,
        type = type,
        name = name,
        title = title,
        documentLabel = documentLabel,
        collectionTitle = collectionTitle,
        templatePath = templatePath.orEmpty(),
        signatures = signatures.toSignatureList(),
        sort = sort,
        enabled = enabled,
        orgId = orgId,
        remark = remark,
        custom = true,
        createTime = createTime,
    )
}

data class CollectionPrintTemplateSaveVo(
    val id: String? = null,
    @field:NotBlank val name: String? = null,
    val title: String? = null,
    val documentLabel: String? = null,
    val collectionTitle: String? = null,
    val templatePath: String? = null,
    val signatures: List<String> = emptyList(),
    val enabled: Boolean? = true,
    val sort: Int? = null,
    val remark: String? = null,
    val createTime: LocalDateTime? = null,
)

internal fun Collection<String>.toSignatureText(): String? =
    map { it.trim() }.filter { it.isNotBlank() }.joinToString("，").takeIf { it.isNotBlank() }

internal fun String?.toSignatureList(): List<String> =
    orEmpty()
        .split(",", "，", ";", "；")
        .map { it.trim() }
        .filter { it.isNotBlank() }
