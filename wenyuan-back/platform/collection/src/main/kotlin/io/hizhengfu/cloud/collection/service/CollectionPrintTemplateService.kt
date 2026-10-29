package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionPrintTemplateDao
import io.hizhengfu.cloud.collection.dao.CollectionPrintTemplateSaveVo
import io.hizhengfu.cloud.collection.dao.CollectionPrintTemplates
import io.hizhengfu.cloud.collection.dao.toSignatureText
import io.hizhengfu.cloud.collection.domain.CollectionPrintTemplateVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.file.service.FileService
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files

@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE])
class CollectionPrintTemplateService(
    private val scope: CollectionScopeResolver,
    private val fileService: FileService,
) {
    fun list(): List<CollectionPrintTemplateVo> {
        val orgId = scope.currentOrgId()
        val custom = CollectionPrintTemplateDao.find { CollectionPrintTemplates.orgId eq orgId }
            .associateBy { it.type }
        return EnumCollectionPrintType.entries.map { type ->
            custom[type]?.toModel() ?: CollectionPrintTemplateRegistry.get(type)
        }.sortedBy { it.sort }
    }

    fun get(type: EnumCollectionPrintType): CollectionPrintTemplateVo =
        findCurrent(type)?.toModel() ?: CollectionPrintTemplateRegistry.get(type)

    fun resolve(print: CollectionPrintVo): CollectionPrintTemplateVo {
        val base = CollectionPrintTemplateRegistry.resolve(print)
        val orgId = print.orgId ?: runCatching { scope.currentOrgId() }.getOrNull()
        val custom = orgId?.let { findEnabled(type = print.type, orgId = it) } ?: return base
        return custom.toModel().copy(
            title = custom.title.takeIf { it.isNotBlank() } ?: base.title,
            documentLabel = custom.documentLabel.takeIf { it.isNotBlank() } ?: base.documentLabel,
            collectionTitle = custom.collectionTitle.takeIf { it.isNotBlank() } ?: base.collectionTitle,
            signatures = custom.signatures?.takeIf { it.isNotBlank() }?.let { custom.toModel().signatures }
                ?: base.signatures,
            sort = base.sort,
            enabled = true,
            custom = true,
        )
    }

    @CacheEvict(allEntries = true)
    fun save(type: EnumCollectionPrintType, input: CollectionPrintTemplateSaveVo): String {
        val base = CollectionPrintTemplateRegistry.get(type)
        val values = TemplateValues.from(input, base)
        val orgId = scope.currentOrgId()
        val existing = findCurrent(type)
        val entity = existing ?: CollectionPrintTemplateDao.new {
            this.orgId = orgId
            this.type = type
        }
        entity.apply(values)
        bindTemplateFile(entity)
        return entity.id.value
    }

    @CacheEvict(allEntries = true)
    fun reset(type: EnumCollectionPrintType) {
        val orgId = scope.currentOrgId()
        CollectionPrintTemplates.deleteWhere {
            (CollectionPrintTemplates.orgId eq orgId) and (CollectionPrintTemplates.type eq type)
        }
    }

    fun usage(type: EnumCollectionPrintType) = CollectionPrintFieldUsageRegistry.usage(type)

    fun loadTemplateBytes(template: CollectionPrintTemplateVo): ByteArray? {
        if (!template.custom || template.templatePath.isBlank()) return null
        val path = fileService.getUserFilesPath(template.templatePath).normalize()
        val base = fileService.basePath.normalize()
        if (!path.startsWith(base) || !Files.exists(path)) return null
        return Files.readAllBytes(path)
    }

    private fun findCurrent(type: EnumCollectionPrintType): CollectionPrintTemplateDao? {
        val orgId = scope.currentOrgId()
        return CollectionPrintTemplateDao.find {
            (CollectionPrintTemplates.orgId eq orgId) and (CollectionPrintTemplates.type eq type)
        }.firstOrNull()
    }

    private fun findEnabled(type: EnumCollectionPrintType, orgId: String): CollectionPrintTemplateDao? =
        CollectionPrintTemplateDao.find {
            (CollectionPrintTemplates.orgId eq orgId) and
                (CollectionPrintTemplates.type eq type) and
                (CollectionPrintTemplates.enabled eq true)
        }.firstOrNull()

    private fun bindTemplateFile(entity: CollectionPrintTemplateDao) {
        val path = entity.templatePath?.trim().orEmpty()
        if (path.isBlank()) return
        val file = io.hizhengfu.cloud.sys.file.dao.FileDao.find { io.hizhengfu.cloud.sys.file.dao.Files.path eq path }
            .firstOrNull()
        if (file != null) fileService.insertFileContent(file.id.value, entity.id.value, entity.name)
    }

    private data class TemplateValues(
        val name: String,
        val title: String,
        val documentLabel: String,
        val collectionTitle: String,
        val templatePath: String?,
        val signatures: String?,
        val enabled: Boolean,
        val sort: Int,
        val remark: String?,
    ) {
        companion object {
            fun from(input: CollectionPrintTemplateSaveVo, base: CollectionPrintTemplateVo): TemplateValues {
                val path = input.templatePath?.trim()?.takeIf { it.isNotBlank() }
                if (path != null && !path.endsWith(".docx", ignoreCase = true)) {
                    throw BadRequestException("打印模板仅支持 .docx 文件")
                }
                return TemplateValues(
                    name = input.name?.trim()?.takeIf { it.isNotBlank() } ?: base.name,
                    title = input.title?.trim()?.takeIf { it.isNotBlank() } ?: base.title,
                    documentLabel = input.documentLabel?.trim()?.takeIf { it.isNotBlank() } ?: base.documentLabel,
                    collectionTitle = input.collectionTitle?.trim()?.takeIf { it.isNotBlank() } ?: base.collectionTitle,
                    templatePath = path,
                    signatures = input.signatures.toSignatureText() ?: base.signatures.toSignatureText(),
                    enabled = input.enabled ?: true,
                    sort = input.sort ?: base.sort,
                    remark = input.remark?.trim()?.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    private fun CollectionPrintTemplateDao.apply(values: TemplateValues) {
        name = values.name
        title = values.title
        documentLabel = values.documentLabel
        collectionTitle = values.collectionTitle
        templatePath = values.templatePath
        signatures = values.signatures
        enabled = values.enabled
        sort = values.sort
        remark = values.remark
    }
}
