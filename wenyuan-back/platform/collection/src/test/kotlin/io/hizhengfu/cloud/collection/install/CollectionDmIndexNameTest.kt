package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.dao.*
import org.jetbrains.exposed.v1.core.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionDmIndexNameTest {

    private val installTables: List<Table> = listOf(
        CollectionOrgs,
        Collections,
        CollectionCatalogs,
        CollectionCatalogVersions,
        CollectionRegistrationNumbers,
        CollectionLedgerLogs,
        CollectionBusinessItems,
        CollectionPlacements,
        CollectionOperationLocks,
        CollectionSets,
        CollectionMediaFolders,
        CollectionMedias,
        CollectionAttachments,
        AnnualReportArchives,
        CollectionCategories,
        Warehouses,
        WarehouseLogs,
        WarehouseEntryApplications,
        StorageMovements,
        Deregistrations,
        Appraisals,
        AppraisalMembers,
        Loans,
        Reproductions,
        InventoryBatches,
        Inventories,
        CollectionTransfers,
        AcquisitionPlans,
        Acquisitions,
        EntryVouchers,
        Requisitions,
        CollectionReturns,
        Exhibitions,
        CollectionPrintTemplates,
    )

    @Test
    fun `藏品安装表索引名显式且满足达梦长度与模式级唯一性`() {
        val indexes = installTables.flatMap { table ->
            table.indices.map { index -> table.tableName to index }
        }

        val unnamed = indexes.filter { (_, index) -> index.customName.isNullOrBlank() }
        assertTrue(
            unnamed.isEmpty(),
            "存在依赖 Exposed 自动命名的索引: " +
                unnamed.joinToString { (table, index) -> "$table:${index.columns.joinToString { it.name }}" },
        )

        val overLimit = indexes.filter { (_, index) -> index.indexName.length > DM_IDENTIFIER_LIMIT }
        assertTrue(
            overLimit.isEmpty(),
            "存在超过达梦 $DM_IDENTIFIER_LIMIT 字符限制的索引名: " +
                overLimit.joinToString { (table, index) -> "$table:${index.indexName}" },
        )

        val names = indexes.map { (_, index) -> index.indexName.uppercase() }
        val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertEquals(emptyMap<String, Int>(), duplicates, "达梦模式内存在重复索引或唯一约束名")
    }

    @Test
    fun `藏品安装表数字 CHECK 名满足达梦限制且模式级唯一`() {
        assertDmCheckConstraintNames(installTables)
    }

    private fun assertDmCheckConstraintNames(tables: List<Table>) {
        val constraints = tables.flatMap { table ->
            table.rawCheckConstraintNames().map { name -> table.tableName to name }
        }
        val automatic = constraints.filterNot { (_, name) -> name.startsWith("ck_") }
        assertTrue(automatic.isEmpty(), "存在未使用短名称的 CHECK: $automatic")
        assertTrue(
            constraints.all { (_, name) -> name.length <= DM_IDENTIFIER_LIMIT },
            "存在超过达梦 $DM_IDENTIFIER_LIMIT 字符限制的 CHECK: $constraints",
        )
        val duplicates = constraints.map { (_, name) -> name.uppercase() }
            .groupingBy { it }.eachCount().filterValues { it > 1 }
        assertEquals(emptyMap<String, Int>(), duplicates, "达梦模式内存在重复 CHECK 名")
    }

    @Suppress("UNCHECKED_CAST")
    private fun Table.rawCheckConstraintNames(): List<String> {
        val field = Table::class.java.getDeclaredField("checkConstraints")
        field.isAccessible = true
        return (field.get(this) as List<Pair<String, *>>).map { it.first }
    }

    private companion object {
        const val DM_IDENTIFIER_LIMIT = 30
    }
}
