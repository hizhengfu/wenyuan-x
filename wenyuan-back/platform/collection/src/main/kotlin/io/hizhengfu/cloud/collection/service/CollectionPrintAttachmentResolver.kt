package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileObjectStorage
import io.hizhengfu.cloud.common.file.FileReadAccess
import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.file.dao.Files
import net.coobird.thumbnailator.Thumbnails
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO

/** 仅服务端渲染内部使用；不从打印请求接收元数据或字节。path 为存储相对路径。 */
data class CollectionPrintAttachment(
    val label: String,
    val name: String,
    val path: String,
    val imageBytes: ByteArray? = null,
    val status: String? = null,
)

/** 原业务重新读取成功以后解析显式附件字段；普通文字、记录号和责任人不会被猜作文件。 */
@Component
class CollectionPrintAttachmentResolver(
    private val storage: FileObjectStorage,
    private val access: FileReadAccess,
    private val directory: CollectionActorDirectory,
) {
    private data class Source(val reference: String, val id: String?, val path: String,
        val storageType: String?, val name: String, val ext: String, val hash: String?, val size: Long?)

    fun resolve(print: CollectionPrintVo): List<CollectionPrintAttachment> {
        return print.fields.flatMap { field -> field.fileReferences.distinct().map { resolve(field.label, it) } }
    }

    private fun lookupSource(reference: String): Source? {
        val clean = reference.trim()
        if (clean.isBlank() || clean.contains('\\') || clean.any { it.code < 32 } ||
            clean.contains(":") || clean.startsWith("//") || clean.contains('?') || clean.contains('#')) return null
        val relative = FileReadPath.relative(clean)
        if (relative.split('/').any { it == ".." || it == "." }) return null
        val row = Files.selectAll().where { Files.id eq clean }.singleOrNull() ?: run {
            val paths = listOf(clean, relative, "/$relative", "/userfiles/$relative", "userfiles/$relative").distinct()
            val rows = Files.selectAll().where { Files.path inList paths }.limit(2).toList()
            if (rows.size > 1) throw BadRequestException("打印附件路径对应多个文件登记，请核对原件")
            rows.singleOrNull()
        }
        if (row != null) return Source(clean, row[Files.id].value, row[Files.path], row[Files.storageType].name,
            row[Files.name], row[Files.ext], row[Files.hash], row[Files.size])
        // 旧业务可合法保存存储相对路径；没有登记的普通编号仍只是缺失引用。
        if (!relative.contains('/') && !relative.contains('.')) return null
        return Source(clean, null, clean, null, relative.substringAfterLast('/'), relative.substringAfterLast('.', ""), null, null)
    }

    private fun requireActor(protected: Boolean): String {
        val id = SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请登录后读取打印附件")
        val current = directory.requireActive(id)
        if (!protected && EnumRole.ADMIN.value !in current.roles &&
            current.permissions.none { it in setOf("ADMIN", "FILE_ALL", "FILE_SELECT") })
            throw BadRequestException("当前账号无权读取打印附件")
        return id
    }

    private fun resolve(label: String, reference: String): CollectionPrintAttachment {
        val source = lookupSource(reference) ?: return CollectionPrintAttachment(label, "", "", status = "附件引用缺失或无效")
        // 此处先授权，再展示任何名称、路径或存储信息。拒权/基线冲突不能降级成可见图像。
        access.requireMetadataReadable(source.path, source.storageType)
        val evidence = access.readEvidence(source.path, source.storageType)
        val actorId = requireActor(evidence.location.protected)
        fun recheck() {
            if (requireActor(evidence.location.protected) != actorId || lookupSource(source.reference) != source ||
                access.readEvidence(source.path, source.storageType) != evidence)
                throw BadRequestException("打印附件身份、访问资格或原件基线已变化，请重新读取")
        }
        val path = FileReadPath.normalized(evidence.location.path, source.storageType)
        if (path.startsWith('/') || path.contains(':') || path.split('/').any { it == ".." })
            throw BadRequestException("打印附件存储路径无效")
        val name = source.name.replace(Regex("[\\r\\n]"), "").substringAfterLast('/').substringAfterLast('\\')
            .ifBlank { path.substringAfterLast('/') }
        val safeName = if (source.ext.matches(Regex("[A-Za-z0-9]{1,20}")) && !name.endsWith(".${source.ext}", true)) "$name.${source.ext}" else name
        val result = CollectionPrintAttachment(label, safeName, path)
        try {
            if (evidence.revision == null && !storage.exists(source.path, source.storageType)) {
                recheck()
                return result.copy(status = "原文件缺失，请核对存储与备份")
            }
            val metadata = evidence.revision?.let { storage.metadataAtRevision(source.path, source.storageType, it) }
                ?: storage.metadata(source.path, source.storageType)
            recheck()
            if (source.ext.lowercase() !in IMAGE_EXTENSIONS) return result
            if (metadata.size > MAX_IMAGE_BYTES || metadata.size < 0 || (evidence.baseline?.size ?: 0) > MAX_IMAGE_BYTES)
                return result.copy(status = "图像超过打印缩略图大小限制，保留文件信息")
            val original = evidence.revision?.let { storage.openRevision(source.path, source.storageType, it) }
                ?: storage.openStream(source.path, source.storageType)
            val bytes = original.use { input ->
                evidence.baseline?.withVerifiedCopy(input) { copy -> java.nio.file.Files.readAllBytes(copy) }
                    ?: input.readNBytes(MAX_IMAGE_BYTES + 1)
            }
            recheck()
            if (bytes.size > MAX_IMAGE_BYTES) return result.copy(status = "图像超过打印缩略图大小限制，保留文件信息")
            val thumb = thumbnail(bytes)
            recheck()
            return result.copy(imageBytes = thumb.bytes, status = thumb.status ?:
                if (evidence.baseline == null) "原件内容基线未留存；仅显示当前文件缩略图" else null)
        } catch (_: IOException) {
            // 权限/身份/摘要失败是 BadRequestException，不能在此吞掉并输出被篡改原件。
            recheck()
            return result.copy(status = "原文件缺失或图像无法解码，请核对存储")
        }
    }

    private data class Thumbnail(val bytes: ByteArray?, val status: String? = null)
    private fun thumbnail(bytes: ByteArray): Thumbnail {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
            if (stream == null) return Thumbnail(null, "图像无法解码")
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return Thumbnail(null, "图像格式不支持打印缩略图")
            val reader = readers.next()
            try {
                reader.input = stream
                val width = reader.getWidth(0); val height = reader.getHeight(0)
                if (width <= 0 || height <= 0 || width.toLong() * height > MAX_IMAGE_PIXELS)
                    return Thumbnail(null, "图像像素超过打印缩略图限制，保留文件信息")
            } finally { reader.dispose() }
        }
        // 使用现有库的 EXIF 朝向处理，原件像素已在完整解码之前核定。
        val image = Thumbnails.of(ByteArrayInputStream(bytes)).size(THUMBNAIL_EDGE, THUMBNAIL_EDGE)
            .useExifOrientation(true).asBufferedImage()
        val white = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val graphics = white.createGraphics()
        try { graphics.color = Color.WHITE; graphics.fillRect(0, 0, white.width, white.height); graphics.drawImage(image, 0, 0, null) }
        finally { graphics.dispose() }
        val out = ByteArrayOutputStream()
        if (!ImageIO.write(white, "png", out)) return Thumbnail(null, "图像无法生成打印缩略图")
        return Thumbnail(out.toByteArray())
    }

    companion object {
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        const val MAX_IMAGE_PIXELS = 16_000_000L
        const val THUMBNAIL_EDGE = 600
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "tif", "tiff", "psd", "tga")
    }
}
