package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.inList

/** 单次来源扫描的批量位置解析；不缓存来源、权限或本机文件身份。 */
internal object CollectionOriginalFileLocations {
    data class Location(val path: String, val storageType: String)

    fun find(references: Iterable<String>): Map<String, Location> = buildMap {
        references.distinct().chunked(200).forEach { batch ->
            // 保留原先“先按 ID，再按唯一旧路径”的优先级及歧义处理。
            FileDao.find { Files.id inList batch }.forEach { file ->
                put(file.id.value, Location(file.path, file.storageType.name))
            }
            val missing = batch.filterNot(::containsKey)
            if (missing.isNotEmpty()) {
                FileDao.find { Files.path inList missing }.groupBy { it.path }.forEach { (path, files) ->
                    files.singleOrNull()?.let { put(path, Location(it.path, it.storageType.name)) }
                }
            }
        }
    }
}
