package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.domain.CollectionPrintRenderRequest
import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import io.hizhengfu.cloud.sys.file.converter.WordFileConverter
import io.hizhengfu.cloud.sys.system.service.DictService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

enum class CollectionPrintFileFormat(
    val extension: String,
    val contentType: String,
) {
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
}

data class CollectionPrintFileResult(
    val prints: List<CollectionPrintVo>,
    val format: CollectionPrintFileFormat,
    val fileName: String,
    val bytes: ByteArray,
) {
    val contentType: String get() = format.contentType
}

@Component
@Transactional(readOnly = true)
class CollectionPrintFileService(
    private val printService: CollectionPrintService,
    private val templateService: CollectionPrintTemplateService,
    private val dictService: DictService,
    private val wordFileConverter: WordFileConverter,
    private val attachmentResolver: CollectionPrintAttachmentResolver,
) {
    fun render(
        request: CollectionPrintRenderRequest,
        format: CollectionPrintFileFormat,
    ): CollectionPrintFileResult {
        val prints = CollectionPrintRenderRequestResolver.resolve(
            request,
            printService::print,
        ) { print -> print.copy(template = templateService.resolve(print)) }
        val dicts = loadDicts(prints)
        val docxBytes = ByteArrayOutputStream().use { out ->
            CollectionPrintDocxRenderer.renderBatch(
                prints, dicts, out, templateService::loadTemplateBytes, attachmentResolver::resolve,
            )
            out.toByteArray()
        }
        val bytes = when (format) {
            CollectionPrintFileFormat.DOCX -> docxBytes
            CollectionPrintFileFormat.PDF -> ByteArrayOutputStream().use { out ->
                wordFileConverter.convert(ByteArrayInputStream(docxBytes), "pdf", out)
                out.toByteArray()
            }
        }
        return CollectionPrintFileResult(
            prints = prints,
            format = format,
            fileName = fileName(prints, format),
            bytes = bytes,
        )
    }

    fun render(
        type: EnumCollectionPrintType,
        id: String,
        format: CollectionPrintFileFormat,
    ): CollectionPrintFileResult =
        render(CollectionPrintRenderRequest(type = type, id = id), format)

    fun renderBatch(
        type: EnumCollectionPrintType,
        ids: List<String>,
        format: CollectionPrintFileFormat,
    ): CollectionPrintFileResult =
        render(CollectionPrintRenderRequest(type = type, ids = ids), format)

    private fun fileName(prints: List<CollectionPrintVo>, format: CollectionPrintFileFormat): String {
        val first = prints.firstOrNull()
        val title = when {
            prints.size > 1 -> "${first?.title ?: "藏品打印表单"}_批量${prints.size}份"
            else -> first?.title ?: "藏品打印表单"
        }
        val code = when {
            prints.size > 1 -> first?.type?.name ?: "PRINT"
            else -> first?.documentNo?.takeIf { it.isNotBlank() } ?: first?.type?.name ?: "PRINT"
        }
        return "${title}_${code}.${format.extension}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun loadDicts(prints: List<CollectionPrintVo>): Map<String, Map<String, String>> {
        val types = (prints.flatMap { it.fields.mapNotNull { field -> field.dictType } } + listOf(
            CollectionConst.DICT_RELIC_LEVEL,
            CollectionConst.DICT_TEXTURE,
        )).distinct()
        return types.associateWith { type ->
            dictService.findByType(type).mapNotNull { dict ->
                val value = dict.value
                val label = dict.label
                if (value.isNullOrBlank() || label.isNullOrBlank()) null else value to label
            }.toMap()
        }
    }
}
