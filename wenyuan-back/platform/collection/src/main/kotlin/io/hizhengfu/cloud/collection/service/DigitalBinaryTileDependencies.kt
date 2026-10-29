package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.DigitalFileSnapshot
import io.hizhengfu.cloud.common.exception.BadRequestException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** 顺序读取原件并计算全文件摘要；只保留有上限的 JSON/URI，不把整个瓦片载入内存。 */
internal object DigitalBinaryTileDependencies {
    val TYPES = setOf("b3dm", "i3dm", "pnts", "cmpt")
    private const val MAX_JSON = 32 * 1024 * 1024L
    private const val MAX_URI = 64 * 1024L

    fun read(file: DigitalFileSnapshot, input: InputStream): DigitalModelDependencies.Inspected = Reader(file, input).read()

    private class Reader(private val file: DigitalFileSnapshot, private val input: InputStream) {
        private val digest = MessageDigest.getInstance("SHA-256")
        private val references = linkedSetOf<String>()
        private var position = 0L
        private var tiles = 0
        private val buffer = ByteArray(65536)

        private fun fail(reason: String): Nothing = throw BadRequestException("数字成果 ${file.name}：$reason")
        private fun uint(bytes: ByteArray, offset: Int) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(offset).toUInt().toLong()
        private fun within(length: Long, end: Long) {
            if (length < 0 || position > end || length > end - position) fail("瓦片内容越界或长度不一致")
        }
        private fun readInto(bytes: ByteArray, offset: Int, length: Int) {
            var used = 0
            while (used < length) {
                var n = input.read(bytes, offset + used, length - used)
                if (n < 0) fail("瓦片原件不完整")
                if (n == 0) {
                    val one = input.read()
                    if (one < 0) fail("瓦片原件不完整")
                    bytes[offset + used] = one.toByte(); n = 1
                }
                digest.update(bytes, offset + used, n)
                position += n; used += n
            }
        }
        private fun bytes(length: Long, end: Long): ByteArray {
            within(length, end)
            if (length > MAX_JSON) fail("瓦片 JSON 超过 32 MiB 依赖解析上限")
            return ByteArray(length.toInt()).also { readInto(it, 0, it.size) }
        }
        private fun consume(length: Long, end: Long, padding: Boolean = false) {
            within(length, end)
            var remaining = length
            while (remaining > 0) {
                val n = minOf(remaining, buffer.size.toLong()).toInt()
                readInto(buffer, 0, n)
                if (padding && (0 until n).any { buffer[it] != 0.toByte() && buffer[it] != 32.toByte() })
                    fail("内嵌 GLB 尾部填充无效")
                remaining -= n
            }
        }
        fun read(): DigitalModelDependencies.Inspected {
            tile(file.size, 0, root = true)
            if (position != file.size || input.read() != -1) fail("实际字节长度与文件记录不一致")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            return DigitalModelDependencies.Inspected(file.copy(sha256 = hash), references)
        }
        private fun tile(parentEnd: Long, depth: Int, root: Boolean = false) {
            if (depth > 64 || ++tiles > 10000) fail("复合瓦片嵌套或数量超过解析上限")
            val start = position
            val header = bytes(12, parentEnd)
            val type = header.copyOfRange(0, 4).toString(Charsets.US_ASCII)
            val length = uint(header, 8)
            if (type !in TYPES || uint(header, 4) != 1L || length < 12 || length > parentEnd - start)
                fail("瓦片头、版本或文件长度无效")
            val end = start + length
            if (root && end != parentEnd) fail("瓦片文件长度与记录不一致")
            if (type == "cmpt") {
                val count = uint(bytes(4, end), 0)
                if (count == 0L || count > 10000L) fail("复合瓦片数量无效")
                repeat(count.toInt()) { tile(end, depth + 1) }
            } else {
                val fields = bytes(if (type == "i3dm") 20 else 16, end)
                for (i in 0..3) {
                    val n = uint(fields, i * 4)
                    if (i % 2 == 0 && n > 0) {
                        val json = bytes(n, end)
                        val tree = try { JsonUtils.mapper.readTree(json.toString(Charsets.UTF_8).trimEnd('\u0000', ' ')) }
                            catch (_: Exception) { fail("瓦片属性 JSON 无法解析") }
                        if (!tree.isObject) fail("瓦片属性 JSON 须为对象")
                    } else consume(n, end)
                }
                if (type == "i3dm" && uint(fields, 16) == 0L) {
                    if (end - position > MAX_URI) fail("i3dm 模型地址超过 64 KiB 解析上限")
                    val value = bytes(end - position, end)
                    val uri = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString().trimEnd('\u0000', ' ') }
                        catch (_: Exception) { fail("i3dm 模型地址不是有效 UTF-8") }
                    if (uri.isBlank()) fail("i3dm 缺少外部模型地址")
                    references.add(uri)
                } else if (type != "pnts") {
                    if (type == "i3dm" && uint(fields, 16) != 1L) fail("i3dm 模型格式标识无效")
                    glb(end)
                }
            }
            if (position != end) fail("瓦片内容与声明长度不一致")
        }
        private fun glb(tileEnd: Long) {
            val start = position
            val header = bytes(20, tileEnd)
            val length = uint(header, 8)
            val jsonLength = uint(header, 12)
            if (uint(header, 0) != 0x46546c67L || uint(header, 4) != 2L || length < 20 || length > tileEnd - start ||
                uint(header, 16) != 0x4e4f534aL || jsonLength == 0L || jsonLength % 4 != 0L || jsonLength > length - 20)
                fail("内嵌 GLB 头或 JSON 分块无效")
            val end = start + length
            references.addAll(DigitalModelDependencies.references(file, bytes(jsonLength, end), true))
            if (position < end) {
                val binary = bytes(8, end)
                val n = uint(binary, 0)
                if (uint(binary, 4) != 0x004e4942L || n % 4 != 0L || n != end - position) fail("内嵌 GLB 二进制分块无效")
                consume(n, end)
            }
            if (tileEnd - end > 7) fail("内嵌 GLB 尾部填充长度无效")
            consume(tileEnd - end, tileEnd, padding = true)
        }
    }
}
