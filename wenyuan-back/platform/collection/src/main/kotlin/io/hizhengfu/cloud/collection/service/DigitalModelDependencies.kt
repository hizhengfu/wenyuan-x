package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.DigitalFileSnapshot
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileReadPath
import tools.jackson.databind.JsonNode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** 从本次计算摘要的同一条流解析清单，不把用户填写的技术说明当作依赖依据。 */
internal object DigitalModelDependencies {
    private const val MAX_MANIFEST_BYTES = 32 * 1024 * 1024
    data class Inspected(val snapshot: DigitalFileSnapshot, val references: Set<String>)

    fun read(file: DigitalFileSnapshot, extension: String?, input: InputStream, packageNames: Collection<String> = emptyList()): Inspected {
        val types = (listOf(extension.orEmpty(), file.name.substringAfterLast('.', ""), file.path.substringAfterLast('.', "")) +
            packageNames.map { it.substringAfterLast('.', "") }).map { it.lowercase() }
        val source = PushbackInputStream(input, 4)
        val magicBytes = source.readNBytes(4)
        source.unread(magicBytes)
        val magic = magicBytes.toString(Charsets.US_ASCII)
        if (magic in DigitalBinaryTileDependencies.TYPES || types.any { it in DigitalBinaryTileDependencies.TYPES })
            return DigitalBinaryTileDependencies.read(file, source)
        val glb = "glb" in types || magic == "glTF"
        val gltf = "gltf" in types
        val json = "json" in types || "tileset" in types
        val prefix = if (glb || gltf || json) ByteArrayOutputStream() else null
        var prefixLimit = if (glb) 20 else MAX_MANIFEST_BYTES + 1
        val md = MessageDigest.getInstance("SHA-256")
        var size = 0L
        val buffer = ByteArray(65536)
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            md.update(buffer, 0, count); size += count
            if (prefix != null) {
                var offset = 0
                while (offset < count && prefix.size() < prefixLimit) {
                    val copied = minOf(count - offset, prefixLimit - prefix.size())
                    prefix.write(buffer, offset, copied); offset += copied
                    if (glb && prefixLimit == 20 && prefix.size() == 20) {
                        val header = ByteBuffer.wrap(prefix.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
                        if (header.int != 0x46546c67 || header.int != 2 || header.int.toUInt().toLong() != file.size)
                            fail(file, "GLB 头或文件长度不一致")
                        val length = header.int.toUInt().toLong()
                        if (header.int != 0x4e4f534a || length == 0L || length % 4 != 0L || length > MAX_MANIFEST_BYTES || length + 20 > file.size)
                            fail(file, "GLB JSON 分块无效或超过 32 MiB 解析上限")
                        prefixLimit = 20 + length.toInt()
                    }
                }
            }
        }
        if (size == 0L || size != file.size) fail(file, "实际字节长度与文件记录不一致")
        val snapshot = file.copy(sha256 = md.digest().joinToString("") { "%02x".format(it) })
        if (prefix == null) return Inspected(snapshot, emptySet())
        val bytes = prefix.toByteArray()
        if (glb && (bytes.size < 20 || bytes.size != prefixLimit)) fail(file, "GLB JSON 分块不完整")
        if (!glb && bytes.size > MAX_MANIFEST_BYTES) fail(file, "JSON 超过 32 MiB 依赖解析上限，须核实并整理清单后登记")
        return Inspected(snapshot, references(file, if (glb) bytes.copyOfRange(20, bytes.size) else bytes, gltf || glb))
    }

    internal fun references(file: DigitalFileSnapshot, bytes: ByteArray, expectGltf: Boolean): Set<String> {
        val tree = try { JsonUtils.mapper.readTree(bytes) }
            catch (_: Exception) { fail(file, "模型或 JSON 清单无法解析") }
        val tileset = tree.path("root").isObject && tree.path("asset").isObject
        val isGltf = expectGltf || tree.path("asset").path("version").asString() == "2.0" &&
            (tree.has("buffers") || tree.has("images") || tree.has("scenes"))
        if (!tileset && !isGltf) return emptySet()
        if (isGltf && tree.path("asset").path("version").asString() != "2.0") fail(file, "glTF 版本信息缺失或不支持")
        val refs = linkedSetOf<String>()
        fun collect(node: JsonNode) {
            if (node.isArray) node.forEach(::collect)
            else if (node.isObject) node.properties().forEach { (key, value) ->
                // extras 是用户自定义元数据，不作为加载器的标准依赖字段。
                if (key != "extras") {
                    if (key in setOf("uri", "url", "schemaUri")) {
                        if (!value.isString || value.asString().isBlank()) fail(file, "依赖地址必须是非空字符串")
                        val valueText = value.asString()
                        if (valueText.startsWith("data:", ignoreCase = true)) {
                            if (!isGltf) fail(file, "内嵌瓦片清单尚未完成依赖核验，请整理为包内文件")
                        } else refs.add(valueText)
                    } else collect(value)
                }
            }
        }
        collect(tree)
        return refs
    }

    /** 依赖必须按实际存储路径属于本次文件集合，文件名相同或同目录均不授予资格。 */
    fun requireClosed(files: List<Inspected>, packageFiles: Map<String, String>? = null) {
        if (packageFiles == null) return requireClosedPaths(files)
        val byId = files.associateBy { it.snapshot.fileId }
        if (packageFiles.isEmpty() || packageFiles.values.toSet() != byId.keys || byId.size != files.size)
            throw BadRequestException("模型包清单必须准确覆盖本阶段所有成果文件，不能遗漏或加入未选择的文件")
        val virtualFiles = packageFiles.map { (path, id) ->
            requirePackagePath(path)
            val source = byId.getValue(id)
            // 仅用于包内 URI 解析；返回及归档的快照始终保留真实存储类型与路径。
            source.copy(snapshot = source.snapshot.copy(path = "package/$path", storageType = "LOCAL"))
        }
        requireClosedPaths(virtualFiles)
    }

    private fun requirePackagePath(path: String) {
        if (path.isBlank() || path.startsWith('/') || path.contains('\\') || path.contains(':') ||
            path.any { it.code < 32 || it.code == 127 } || path.split('/').any { it.isEmpty() || it == "." || it == ".." })
            throw BadRequestException("模型包路径须为不含空段、点段或盘符的相对文件路径")
    }

    private fun requireClosedPaths(files: List<Inspected>) {
        val byPath = files.groupBy { FileReadPath.key(it.snapshot.path, it.snapshot.storageType) }
        files.filter { it.references.isNotEmpty() }.forEach { source ->
            val file = source.snapshot
            source.references.forEach { reference ->
                val uri = try { URI(reference) } catch (_: Exception) { fail(file, "依赖地址无效或含尚未展开的模板") }
                if (uri.isAbsolute || uri.rawAuthority != null || uri.rawPath.isNullOrBlank() || uri.rawPath.startsWith("/") ||
                    uri.rawQuery != null || uri.rawFragment != null || reference.contains('\\'))
                    fail(file, "依赖须使用包内相对地址，外部地址或带查询条件的引用不能作为已冻结原件")
                val base = URI(null, null, "/" + FileReadPath.normalized(file.path, file.storageType), null)
                val resolved = base.resolve(uri).path
                if (resolved.contains('\\') || resolved.any { it.code < 32 }) fail(file, "依赖路径含非法字符")
                val matches = byPath[FileReadPath.key(resolved, file.storageType)].orEmpty()
                if (matches.size != 1) fail(file, "依赖未唯一纳入本次成果文件：$reference")
            }
        }
    }

    private fun fail(file: DigitalFileSnapshot, reason: String): Nothing =
        throw BadRequestException("数字成果 ${file.name}：$reason")
}
