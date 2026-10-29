package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.domain.FILE_PREFIX
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileMetadataLocation
import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage

/** 单批目录位置索引。来源由守卫当次查询，授权逐位置重做；不供文件字节读取使用。 */
class CollectionFileMetadataBatch<T>(locations: List<FileMetadataLocation>, localStorage: LocalFileObjectStorage?) {
    private val state = locations.distinct().associateWith { Result.success(false) }.toMutableMap()
    private val paths = mutableMapOf<String, MutableSet<FileMetadataLocation>>()
    private val keys = mutableMapOf<String, MutableSet<FileMetadataLocation>>()
    private val references = state.keys.associateWith { linkedSetOf<T>() }

    init {
        state.keys.forEach { location ->
            try {
                val normalized = FileReadPath.normalized(location.path, location.storageType)
                val identities = FileReadPath.keys(location.path, location.storageType, localStorage)
                setOf(location.path, normalized, "/$normalized", "$FILE_PREFIX$normalized",
                    normalized.removePrefix("rustfs/"), "$FILE_PREFIX${normalized.removePrefix("rustfs/")}")
                    .forEach { paths.getOrPut(it) { linkedSetOf() }.add(location) }
                identities.forEach { keys.getOrPut(it) { linkedSetOf() }.add(location) }
            } catch (e: BadRequestException) {
                state[location] = Result.failure(e)
            }
        }
    }

    fun add(reference: T, pathKey: String?, currentPath: String?) {
        val matches = linkedSetOf<FileMetadataLocation>()
        pathKey?.let { keys[it]?.let(matches::addAll) }
        currentPath?.let { paths[it]?.let(matches::addAll) }
        matches.forEach { references.getValue(it).add(reference) }
    }

    fun results(requireReadable: (List<T>) -> Unit): Map<FileMetadataLocation, Result<Boolean>> {
        state.keys.forEach { location ->
            val refs = references.getValue(location)
            if (state.getValue(location).isSuccess && refs.isNotEmpty()) {
                state[location] = try {
                    requireReadable(refs.toList())
                    Result.success(true)
                } catch (e: BadRequestException) { Result.failure(e) }
            }
        }
        return state.toMap()
    }
}
