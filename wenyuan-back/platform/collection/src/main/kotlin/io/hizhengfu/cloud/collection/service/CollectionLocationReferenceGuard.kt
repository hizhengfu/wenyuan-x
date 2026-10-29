package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.museum.device.dao.MuseumLocationSources
import io.hizhengfu.cloud.collection.install.CollectionInstall
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.install.IInstall
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.util.ClassUtils
import tools.jackson.databind.JsonNode

/** 扩展模块声明非标准字段和仅在旧部署中存在的历史表；不据此创建或迁移表。 */
interface CollectionLocationReferenceSchema {
    fun locationReferenceColumns(): Set<Column<*>> = emptySet()
    fun locationReferenceJsonColumns(): Set<Column<*>> = emptySet()
    fun legacyLocationReferenceTables(): Set<Table> = emptySet()
}

/** 删除和重新分类前核验当前及历史引用；跨馆历史同样保护，不返回其他馆的业务内容。 */
@Component
class CollectionLocationReferenceGuard(private val installers: ObjectProvider<IInstall>? = null) {
    private val coreTables = CollectionInstall("single").initTables()
    private val rawJsonColumns = setOf<Column<*>>(
        CollectionLedgerLogs.before, CollectionLedgerLogs.after, InstitutionSuccessionItems.baseline,
        LoanComplianceRecords.content, ReturnComplianceRecords.content, DesignatedReceptionRecords.content,
        InstitutionSuccessionRecords.content, InventoryAnnualReports.content,
    )
    private val locationColumn = Regex("(^|_)(warehouse|location|venue)(_root)?_ids?$")

    private fun registered() = installers?.orderedStream()?.toList().orEmpty()
        .filter { ClassUtils.getUserClass(it).name.startsWith("io.hizhengfu.cloud.collection.") }

    /** 只取得已注册藏品模块的表定义，不运行任何初始化、升级或数据写入方法。 */
    internal fun tables(): List<Table> = tables(registered())
    private fun tables(installed: List<IInstall>): List<Table> = (coreTables + installed.flatMap { it.initTables().orEmpty() } +
        installed.filterIsInstance<CollectionLocationReferenceSchema>().flatMap { it.legacyLocationReferenceTables() }.filter { it.exists() })
        .distinctBy { it.tableName }.filter { it != Warehouses }

    fun requireUnreferenced(id: String) = requireUnreferenced(setOf(id))

    fun requireUnreferenced(ids: Set<String>) {
        requireReferences(ids, includeLocationSources = true)
    }

    /** 退役保留原位置及其采集映射，只允许没有其他业务引用的位置退出当前目录。 */
    fun requireNoBusinessReferences(id: String) {
        requireReferences(setOf(id), includeLocationSources = false)
    }

    private fun requireReferences(ids: Set<String>, includeLocationSources: Boolean) {
        if (ids.isEmpty() || ids.any(String::isBlank)) throw BadRequestException("待核验的位置集合不能为空")
        try { checkReferences(ids, includeLocationSources) }
        catch (e: java.sql.SQLException) {
            throw BadRequestException("位置引用核验所需的数据库结构或查询异常，请先核对模块升级状态").apply { initCause(e) }
        }
    }
    private fun checkReferences(ids: Set<String>, includeLocationSources: Boolean) {
        val installed = registered()
        val extensions = installed.filterIsInstance<CollectionLocationReferenceSchema>()
        val directColumns = extensions.flatMap { it.locationReferenceColumns() }.toSet()
        val jsonColumns = rawJsonColumns + extensions.flatMap { it.locationReferenceJsonColumns() }
        val tables = tables(installed)
        // 统一采集位置使用独立ID。只有带明确来源类型的映射才关联藏品位置；停用映射仍保留历史。
        if (includeLocationSources && MuseumLocationSources.exists() && ids.chunked(200).any { batch ->
                MuseumLocationSources.select(MuseumLocationSources.id).where {
                    (MuseumLocationSources.sourceType eq "BUSINESS_MASTER") and
                        (MuseumLocationSources.sourceCode eq "COL_WAREHOUSE") and (MuseumLocationSources.externalId inList batch)
                }.limit(1).any()
            }) referenced()
        // 常见现用引用先走数据库等值查询；不按当前可见馆过滤历史记录。
        for (table in tables) for (column in table.columns.filter { it != RfidWorkstations.locationId && (locationColumn.containsMatchIn(it.name) || it in directColumns) }) {
            @Suppress("UNCHECKED_CAST") val field = column as Column<String?>
            val found = if (column.name.endsWith("_ids")) anyValue(table,listOf(column)) { _, value -> tokens(value?.toString(),ids) }
                else ids.chunked(200).any { batch -> table.select(field).where { field inList batch }.limit(1).any() }
            if (found) referenced()
        }
        // 任职及岗位移交等档案直接以位置ID为业务ID；图片读取身份可在未使用位置删除后继续保留。
        if (ids.chunked(200).any { batch -> CollectionPermanentArchives.select(CollectionPermanentArchives.id).where {
                (CollectionPermanentArchives.bizId inList batch) and (CollectionPermanentArchives.bizType neq CollectionBusinessOriginalFiles.BIZ)
            }.limit(1).any() }) referenced()
        for (table in tables) {
            val columns = table.columns.filter { it.columnType is JsonColumnMarker || it in jsonColumns ||
                (it.columnType is TextColumnType && (it.name == "snapshot" || it.name.contains("_snapshot"))) }
            if (columns.isNotEmpty() && anyValue(table,columns) { column, value ->
                // 旧总账 before/after 同时存在纯文本和JSON，不能把旧名称当成损坏快照。
                if (value == null) false else contains(if(value is String && column !in setOf(CollectionLedgerLogs.before,CollectionLedgerLogs.after)) parse(value) else JsonUtils.mapper.valueToTree(value),ids)
            }) referenced()
        }
    }

    private fun anyValue(table: Table, columns: List<Column<*>>, predicate: (Column<*>, Any?) -> Boolean): Boolean {
        var offset = 0L
        val order = table.primaryKey?.columns.orEmpty().map { it to SortOrder.ASC }.toTypedArray()
        while (true) {
            val batch = table.select(columns).orderBy(*order).limit(200).offset(offset).toList()
            if (batch.isEmpty()) return false
            if (batch.any { row -> columns.any { predicate(it,row[it]) } }) return true
            offset += batch.size
        }
    }
    private fun parse(value: String): JsonNode? {
        if (value.isBlank()) return null
        return try { JsonUtils.mapper.readTree(value) }
        catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("位置引用所需的历史快照无法解析，请核对原业务后再办理") }
    }
    private fun tokens(value: String?, ids: Set<String>) = value?.split(Regex("[,;，；\\s]+"))?.any { it in ids } == true
    private fun contains(value: JsonNode?, ids: Set<String>): Boolean = when {
        value == null || value.isNull -> false
        value.isObject -> value.properties().any { (key, child) -> key in ids || contains(child,ids) }
        value.isArray -> value.any { contains(it,ids) }
        value.isString -> {
            val text = value.asString()
            tokens(text,ids) || if (text.trimStart().startsWith("{") || text.trimStart().startsWith("[")) {
                val nested = try { JsonUtils.mapper.readTree(text) } catch (_: tools.jackson.core.JacksonException) { null }
                contains(nested,ids)
            } else false
        }
        value.isIntegralNumber -> value.toString() in ids
        else -> false
    }
    private fun referenced(): Nothing = throw BadRequestException("位置已被现用或历史业务引用，不能删除或直接改变位置类别、场景、父级结构")
}
