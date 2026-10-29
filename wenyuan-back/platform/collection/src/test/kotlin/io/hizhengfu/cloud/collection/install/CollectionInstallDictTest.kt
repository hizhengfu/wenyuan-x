package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.rfid.domain.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CollectionInstallDictTest {

    @Test
    fun `workbench and search are direct entries and lifecycle directories do not duplicate page routes`() {
        val menus = CollectionInstall("single").initMenus()
        val byId = menus.associateBy { it.id }
        val home = byId.getValue("10522")
        val search = byId.getValue("10521")
        assertEquals("藏品工作台", home.name)
        assertEquals("10500", home.parentId)
        assertEquals("10500", search.parentId)
        assertTrue(home.sort < search.sort)
        assertTrue(search.sort < byId.getValue("10530").sort)
        val folders = menus.filter { menu -> menus.any { it.parentId == menu.id } }
        assertTrue(folders.all { it.path.isNullOrBlank() })
        val pages = menus - folders.toSet()
        assertEquals(pages.size, pages.map { it.path }.distinct().size)
        assertEquals(setOf("10556", "10557", "10558"), byId.keys.filter { it!! > "10555" }.toSet())
    }

    @Test
    fun `lifecycle keeps approval entry and return distinct from personnel access and ownership disposal`() {
        val menus = CollectionInstall("single").initMenus().associateBy { it.id }
        fun assertPage(id: String, parent: String, label: String) {
            val page = menus.getValue(id)
            assertEquals(parent, page.parentId)
            assertEquals("0,10500,$parent,", page.parentIds)
            assertEquals(label, page.name)
        }
        assertPage("10513", "10530", "征集申请与审批")
        assertPage("10507", "10530", "鉴定与定级")
        assertPage("10527", "10530", "入馆核收与登账")
        assertTrue(menus.getValue("10513").sort < menus.getValue("10527").sort)
        assertPage("10529", "10531", "编目录入")
        assertPage("10520", "10531", "编目审核")
        assertPage("10537", "10531", "藏品编目卡")
        assertTrue(menus.getValue("10529").sort < menus.getValue("10520").sort)
        assertTrue(menus.getValue("10520").sort < menus.getValue("10537").sort)
        assertPage("10538", "10534", "人员入库与进出登记")
        assertPage("10509", "10532", "馆外借用")
        assertPage("10525", "10532", "归库验收")
        assertPage("10512", "10556", "调拨交换与承接")
        assertPage("10503", "10556", "退出馆藏")
        assertPage("10551", "10557", "事故报告与处置")
        assertPage("10510", "10533", "复制拓印与数字采集")
        assertPage("10539", "10558", "RFID作业入口")
        assertPage("10540", "10558", "RFID基础管理")
        assertPage("10541", "10558", "RFID通行监控")
    }

    @Test
    fun `print type dict covers every formal print type`() {
        val values = CollectionInstall("single")
            .initDicts()
            .filter { it.type == CollectionConst.DICT_PRINT_TYPE }
            .map { it.value }
            .toSet()

        val missing = EnumCollectionPrintType.entries.map { it.name }.filterNot { it in values }

        assertTrue(missing.isEmpty(), "missing print type dict values: $missing")
    }

    @Test
    fun `ui status dicts cover collection status enums and boolean filters`() {
        val dicts = CollectionInstall("single").initDicts()
        fun values(type: String) = dicts.filter { it.type == type }.map { it.value }.toSet()
        fun assertEnumDict(type: String, expected: Set<String>) {
            assertEquals(expected, values(type), "dict $type does not cover enum values")
        }

        assertEnumDict(CollectionConst.DICT_RELIC_LEVEL, EnumRelicLevel.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_SOURCE, EnumSource.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_COMPLETENESS, EnumCompleteness.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_PRESERVE, EnumPreserve.entries.map { it.name }.toSet())
        assertEquals(
            listOf(
                "A" to "状态稳定，不需修复",
                "B" to "部分损腐，需要修复",
                "C" to "腐蚀损毁严重，急需修复",
            ),
            dicts
                .filter { it.type == CollectionConst.DICT_PRESERVE }
                .sortedBy { it.sort }
                .map { it.value to it.label },
        )
        assertEnumDict(CollectionConst.DICT_LIFE_STATUS, EnumCollectionStatus.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_CATALOG_STATUS, EnumCatalogStatus.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_WAREHOUSE_TYPE, EnumWarehouseType.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_STORAGE_LOCATION_TYPE, WarehouseLocationPolicy.storageTypes.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_EXHIBITION_LOCATION_TYPE, WarehouseLocationPolicy.exhibitionTypes.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_LOCATION_SCENE, EnumLocationScene.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_MOVE_TYPE, EnumMoveType.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_MOVE_REASON, EnumMoveReason.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_DEREG_REASON, EnumDeregReason.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_APPROVE_LEVEL, EnumApproveLevel.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_SET_RELATION, EnumSetRelation.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_ORG_LEVEL, EnumOrgLevel.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_OWNERSHIP, EnumOwnership.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_ORG_STATUS, EnumOrgStatus.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_APPLY_STATUS, EnumApplyStatus.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_REP_TYPE, EnumRepType.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_INVENTORY_RESULT, EnumInventoryResult.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_PROCESS_STATE, EnumProcessState.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_MEDIA_TYPE, EnumCollectionMediaType.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_ATTACHMENT_TYPE, EnumCollectionAttachmentType.entries.map { it.name }.toSet())
        assertEnumDict(CollectionConst.DICT_LEDGER_ACTION, EnumLedgerAction.entries.map { it.name }.toSet())
        // DICT_RESTORATION_METHOD_CATEGORY 已迁移至 module-collection-restoration
        assertEquals(setOf("true", "false"), values(CollectionConst.DICT_PRESENCE))
        assertEquals(setOf("true", "false"), values(CollectionConst.DICT_RETURNED))
        assertEquals(setOf("true", "false"), values(CollectionConst.DICT_REGISTERED))
        assertEquals(setOf("true", "false"), values(CollectionConst.DICT_REQUIRED))
        assertEquals(setOf("NATIONAL"), values(CollectionConst.DICT_CATEGORY_SCOPE))
    }

    @Test
    fun `rfid ui dictionaries cover every business enum`() {
        val dicts = CollectionInstall("single").initDicts()
        fun values(type: String) = dicts.filter { it.type == type }.map { it.value }.toSet()
        fun <T : Enum<T>> assertEnumDict(type: String, entries: List<T>) {
            assertEquals(entries.map { it.name }.toSet(), values(type), "dict $type does not cover enum values")
        }

        assertEnumDict(CollectionConst.DICT_RFID_WORKSTATION_CAPABILITY, EnumRfidWorkstationCapability.entries)
        assertEnumDict(CollectionConst.DICT_RFID_WORKSTATION_DEVICE_PURPOSE, EnumRfidWorkstationDevicePurpose.entries)
        assertEquals(setOf("ENABLED", "DISABLED"), values(CollectionConst.DICT_RFID_WORKSTATION_STATUS))
        assertEnumDict(CollectionConst.DICT_RFID_OPERATOR_CARD_STATUS, EnumRfidOperatorCardStatus.entries)
        assertEnumDict(CollectionConst.DICT_RFID_TAG_STATUS, EnumRfidTagStatus.entries)
        assertEnumDict(CollectionConst.DICT_RFID_TAG_OPERATION_TYPE, EnumRfidTagOperationType.entries)
        assertEquals(setOf("true", "false"), values(CollectionConst.DICT_RFID_OPERATION_RESULT))
        assertEnumDict(CollectionConst.DICT_RFID_TAG_JOB_STATUS, EnumRfidTagJobStatus.entries)
        assertEnumDict(CollectionConst.DICT_RFID_SCAN_SESSION_TYPE, EnumRfidScanSessionType.entries)
        assertEnumDict(CollectionConst.DICT_RFID_SCAN_SESSION_STATE, EnumRfidScanSessionState.entries)
        assertEnumDict(CollectionConst.DICT_RFID_DIRECTION, EnumRfidDirection.entries)
        assertEnumDict(CollectionConst.DICT_RFID_READ_CLASSIFICATION, EnumRfidReadClassification.entries)
        assertEquals(
            setOf("COLLECTION_LOAN", "COLLECTION_REQUISITION", "COLLECTION_RETURN"),
            values(CollectionConst.DICT_RFID_PASSAGE_SOURCE_TYPE),
        )
        assertEnumDict(CollectionConst.DICT_RFID_PASSAGE_GRANT_STATUS, EnumRfidPassageGrantStatus.entries)
        assertEnumDict(CollectionConst.DICT_RFID_WHITELIST_SYNC_STATUS, EnumRfidWhitelistSyncStatus.entries)
        assertEnumDict(CollectionConst.DICT_RFID_PASSAGE_DECISION, EnumRfidPassageDecision.entries)
        assertEnumDict(CollectionConst.DICT_RFID_ALARM_STATUS, EnumRfidAlarmStatus.entries)
        assertEnumDict(CollectionConst.DICT_RFID_ANOMALY_TYPE, EnumRfidAnomalyType.entries)
        assertEnumDict(CollectionConst.DICT_RFID_ANOMALY_STATUS, EnumRfidAnomalyStatus.entries)
    }

    @Test
    fun `installed permissions cover collection api authorities`() {
        val installed = CollectionInstall("single")
            .initPermissions()
            .map { it.code }
            .toSet()

        val apiDir = collectionApiDir()
        val used = Files.walk(apiDir).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
        }
            .flatMap { path -> Regex("COLLECTION_[A-Z_]+").findAll(Files.readString(path)).map { it.value } }
            .filter { it != CollectionConst.ADMIN }
            .toSet()

        assertEquals(emptySet<String>(), used - installed)
    }

    private fun collectionApiDir(): Path {
        val userDir = Path.of(System.getProperty("user.dir"))
        return listOf(
            userDir.resolve("src/main/kotlin/io/hizhengfu/cloud/collection/api"),
            userDir.resolve("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/api"),
        ).first { Files.isDirectory(it) }
    }
}
