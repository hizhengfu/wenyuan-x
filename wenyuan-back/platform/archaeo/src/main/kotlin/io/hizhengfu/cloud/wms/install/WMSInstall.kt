package io.hizhengfu.cloud.wms.install

import io.hizhengfu.cloud.common.annotation.Install
import io.hizhengfu.cloud.common.const.EnumPermission
import io.hizhengfu.cloud.common.domain.Dict
import io.hizhengfu.cloud.common.domain.Menu
import io.hizhengfu.cloud.common.domain.Permission
import io.hizhengfu.cloud.common.install.AbstractInstall
import io.hizhengfu.cloud.wms.dao.*
import io.hizhengfu.cloud.wms.domain.*
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component

@Install("考古工地文物库房管理初始化")
@Component
class WMSInstall : AbstractInstall() {

    /**
     * 版本升级钩子（随 app.version 变化触发）。
     *
     * 1.0.7 的 content 列建成 TEXT(64KB)，图片排版/型式分期会嵌入 base64 图片极易超长，
     * createMissingTablesAndColumns 不一定把 TEXT 改成 LONGTEXT，这里对 MySQL/MariaDB 显式 MODIFY。
     * 幂等：重复执行无副作用；非 MySQL 库忽略。
     */
    override fun update(version: String) {
        runCatching {
            val vendor = TransactionManager.current().db.vendor.lowercase()
            if (vendor.contains("mysql") || vendor.contains("maria")) {
                TransactionManager.current().exec("ALTER TABLE wms_image_layout MODIFY content LONGTEXT")
                TransactionManager.current().exec("ALTER TABLE wms_typology_table MODIFY content LONGTEXT")
            }
        }
    }

    override fun initTables(): Set<Table>? {
        val tables = mutableSetOf<Table>()
        //库房相关
        tables.addAll(listOf(WmsStorageLocations, WmsStorageRooms, WmsStorageAreas))
        //文物基础数据
        tables.add(WmsArtifacts)
        tables.add(WmsArtifactExtends)
        //项目信息
        tables.add(WmsProjects)
        tables.add(WmsProjectCells)
        tables.add(WmsProjectPlots)
        //文物库存表
        tables.add(WmsArtifactStocks)
        //库房操作与记录
        tables.add(WmsOperateRecords)
        tables.add(WmsArtifactOperateRecords)
        tables.add(WmsOperateInbounds)
        tables.add(WmsOperateOutbounds)
        tables.add(WmsOperateTransfers)
        tables.add(WmsOperateReturns)
        //文物研究：型式分期 / 图片排版
        tables.add(WmsTypologyTables)
        tables.add(WmsImageLayouts)
        return tables
    }

    override fun initPermissions(): Set<Permission> {
        val permissions = mutableSetOf<Permission>()

        val root = mutableSetOf<Triple<String, String, String>>()

        //基础数据
        root.add(Triple("1894762548920672263", "WMS_ARTIFACT", "文物登记"))
        root.add(Triple("1894762548920672264", "WMS_ARTIFACT_EXTEND", "文物研究"))
        //型式分期
        root.add(Triple("1894762548920672270", "WMS_TYPOLOGY", "型式分期"))
        //图片排版
        root.add(Triple("1894762548920672271", "WMS_IMAGE_LAYOUT", "图片排版"))
        //出土器物登记
        root.add(Triple("1894762548920672272", "WMS_REGISTER_TABLE", "器物登记"))

        //遗址、探方、遗迹管理
        root.add(Triple("1894762548920672260", "WMS_PROJECT", "遗址、探方、遗迹管理"))

        //库房设置
        root.add(Triple("1894762548920672261", "WMS_STORAGE", "库房管理"))


        root.forEach { (first, second, third) ->
            enumValues<EnumPermission>().forEachIndexed { index, p ->
                val id = if (index > 0) "${first}_$index" else first
                val pId = if (index > 0) first else "0"
                val pIds = if (index > 0) "0,$first," else "0,"
                permissions.add(
                    Permission(
                        id = id,
                        code = "${second}_${p.name}",
                        name = "$third${p.value}",
                        parentId = pId,
                        parentIds = pIds
                    )
                )
            }
        }

        //出入库操作
        val operationId = "1894762548920672255"
        permissions.add(
            Permission(id = operationId, code = "WMS_ARTIFACT_OPERATION_ALL", name = "库房操作")
        )
        //查询操作
        permissions.add(
            Permission(
                id = "1894762548920672256",
                parentId = operationId,
                parentIds = "0,$operationId,",
                code = "WMS_ARTIFACT_OPERATION_SELECT",
                name = "操作查询"
            )
        )

        //操作类型
        enumValues<EnumStorageOperationType>().forEachIndexed { index, p ->
            val id = "${operationId}_$index"
            val pId = operationId
            val pIds = "0,$operationId,"
            permissions.add(
                Permission(
                    id = id,
                    code = "WMS_ARTIFACT_OPERATION_${p.name}",
                    name = "库房操作${p.description}",
                    parentId = pId,
                    parentIds = pIds
                )
            )
        }

        permissions.add(Permission(id = "1894762548920672262", code = "wms_analysis", name = "数据分析"))

        return permissions
    }

    override fun initMenus(): Set<Menu>? {
        val menus = mutableSetOf<Menu>()
        //@formatter:off
        menus.add(Menu(id = "1893285066424012800", parentId = "0", parentIds = "0,", sort = 1, name = "文物管理", path = "/wms/artifact", icon = "h:wwgl"));
        menus.add(Menu(id = "1893285066424012801", parentId = "1893285066424012800", parentIds = "0,1893285066424012800,", sort = 1, name = "文物登记", path = "/wms/artifact/register", icon = "h:cpdj"))
        menus.add(Menu(id = "1893285066424012810", parentId = "0", parentIds = "0,", sort = 2, name = "库房管理", path = "/wms/manage", icon = "h:kfgl"));
        menus.add(Menu(id = "1893285066424012811", parentId = "1893285066424012810", parentIds = "0,1893285066424012810,", sort = 1, name = "入库管理", path = "/wms/manage/inbound", icon = "h:rk"));
        menus.add(Menu(id = "1893285066424012812", parentId = "1893285066424012810", parentIds = "0,1893285066424012810,", sort = 2, name = "出库管理", path = "/wms/manage/outbound", icon = "h:ck"));
        menus.add(Menu(id = "1893285066424012813", parentId = "1893285066424012810", parentIds = "0,1893285066424012810,", sort = 3, name = "移库管理", path = "/wms/manage/transfer", icon = "h:yk"));
        menus.add(Menu(id = "1893285066424012814", parentId = "1893285066424012810", parentIds = "0,1893285066424012810,", sort = 4, name = "归库管理", path = "/wms/manage/return", icon = "h:gk"))
        menus.add(Menu(id = "1893285066424012880", parentId = "0", parentIds = "0,", sort = 3, name = "文物账册", path = "/wms/book", icon = "h:wwzc"));
        menus.add(Menu(id = "1893285066424012881", parentId = "1893285066424012880", parentIds = "0,1893285066424012880,", sort = 100, name = "文物总账", path = "/wms/book/wwzz", icon = "h:wwzz"));
        menus.add(Menu(id = "1893285066424012882", parentId = "1893285066424012880", parentIds = "0,1893285066424012880,", sort = 100, name = "文物分账", path = "/wms/book/wwfz", icon = "h:wwfz"))

        //文物研究
        menus.add(Menu(id = "1893285066424012860", parentId = "0", parentIds = "0,", sort = 4, name = "文物研究", path = "/wms/research", icon = "h:wwyj"));
        menus.add(Menu(id = "1893285066424012861", parentId = "1893285066424012860", parentIds = "0,1893285066424012860,", sort = 1, name = "文物卡片", path = "/wms/research/card", icon = "h:wwkp"));
        menus.add(Menu(id = "1893285066424012862", parentId = "1893285066424012860", parentIds = "0,1893285066424012860,", sort = 2, name = "型式分期", path = "/wms/research/typology", icon = "h:xsfq"));
        menus.add(Menu(id = "1893285066424012863", parentId = "1893285066424012860", parentIds = "0,1893285066424012860,", sort = 3, name = "图片排版", path = "/wms/research/imageLayout", icon = "h:tppb"));
        menus.add(Menu(id = "1893285066424012864", parentId = "1893285066424012860", parentIds = "0,1893285066424012860,", sort = 4, name = "器物登记", path = "/wms/research/register", icon = "h:qwdjb"));

        menus.add(Menu(id = "1893285066424012870", parentId = "0", parentIds = "0,", sort = 5, name = "统计分析", path = "/wms/analysis", icon = "h:tj"))
        menus.add(Menu(id = "1893285066424012890", parentId = "0", parentIds = "0,", sort = 4, name = "基础配置", path = "/wms/config", icon = "h:jcsj"));
        menus.add(Menu(id = "1893285066424012894", parentId = "1893285066424012890", parentIds = "0,1893285066424012890,", sort = 1, name = "遗址配置", path = "/wms/config/project", icon = "h:yz"));
        menus.add(Menu(id = "1893285066424012895", parentId = "1893285066424012890", parentIds = "0,1893285066424012890,", sort = 2, name = "探方配置", path = "/wms/config/plot", icon = "h:tf"));
        menus.add(Menu(id = "1893285066424012891", parentId = "1893285066424012890", parentIds = "0,1893285066424012890,", sort = 3, name = "库区配置", path = "/wms/config/area", icon = "h:kq"));
        menus.add(Menu(id = "1893285066424012892", parentId = "1893285066424012890", parentIds = "0,1893285066424012890,", sort = 4, name = "库房配置", path = "/wms/config/room", icon = "h:kf"));
        menus.add(Menu(id = "1893285066424012893", parentId = "1893285066424012890", parentIds = "0,1893285066424012890,", sort = 5, name = "位置配置", path = "/wms/config/location", icon = "h:wz"))
        menus.add(Menu(id = "1893285066424012896", parentId = "1893285066424012890", parentIds = "0,1893285066424012890,", sort = 6, name = "打印机配置", path = "/wms/config/print", icon = "h:print"))
        //@formatter:on

        return menus
    }

    override fun initDicts(): Set<Dict>? {
        val dicts = mutableSetOf<Dict>()
//@formatter:off
        EnumArtifactStatus.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = type.description, value = type.name, type = "wms_artifact_storage_status", sort = index, remarks = "文物库房状态"))
        }
        EnumStorageStatus.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = type.description, value = type.name, type = "wms_storage_type", sort = index, remarks = "可用状态类型"))
        }
        EnumStorageSecurityLevel.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = type.description, value = type.name, type = "wms_storage_security_level", sort = index, remarks = "安全等级类型"))
        }
        EnumStorageOperationType.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = type.description, value = type.name, type = "wms_storage_operation_type", sort = index, remarks = "库房操作类型"))
        }
        EnumArtifactPreserveStatus.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = type.description, value = type.description, type = "wms_artifact_preserve_status", sort = index, remarks = "文物出土状态"))
        }
        EnumArtifactSaveStatus.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = type.description, value = type.description, type = "wms_artifact_save_status", sort = index, remarks = "文物保存状态"))
        }
        EnumArtifactMaterialType.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = type.description, value = type.name, type = "wms_artifact_material_type", sort = index, remarks = "文物材质类型"))
        }
        EnumArtifactUnitType.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = type.description, value = type.description, type = "wms_artifact_unit_type", sort = index, remarks = "文物数量单位"))
        }
        EnumArtifactOutboundType.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = type.description, value = type.description, type = "wms_artifact_outbound_type", sort = index, remarks = "文物出库类型"))
        }
        EnumArtifactTransferType.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = type.description, value = type.description, type = "wms_artifact_transfer_type", sort = index, remarks = "文物入库类型"))
        }
        EnumArtifactCellType.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = "${type.description}(${type.name.uppercase()})", value = type.name.uppercase(), type = "wms_project_cell_type", sort = index, remarks = "遗迹代号"))
        }
        EnumsAnalysisType.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = type.description, value = type.name, type = "wms_analysis_type", sort = index, remarks = "库存文物分析类型"))
        }
        EnumsRegisterAnalysisType.entries.forEachIndexed { index, type ->
            dicts.add(Dict(label = type.description, value = type.name, type = "wms_register_analysis_type", sort = index, remarks = "登记文物分析类型"))
        }
        //@formatter:on
        dicts.addAll(initYear())

        return dicts
    }

    /**
     * 文物年代
     * */
    fun initYear(): List<Dict> {
        val dicts = mutableListOf<Dict>()
        val type = "wms_artifact_year"
        //@formatter:off
        dicts.add(Dict(id = "18932850664240010", label = "旧石器时代", value = "旧石器时代", type = type, remarks = "距今200-1.2万年"))
        dicts.add(Dict(id = "18932850664240011", parentId = "18932850664240010", label = "初期", value = "旧石器时代/初期", type = type, remarks = "距今200-100万年"))
        dicts.add(Dict(id = "18932850664240012", parentId = "18932850664240010", label = "早期", value = "旧石器时代/早期", type = type, remarks = "距今100-20万年"))
        dicts.add(Dict(id = "18932850664240013", parentId = "18932850664240010", label = "中期", value = "旧石器时代/中期", type = type, remarks = "距今20-5万年"))
        dicts.add(Dict(id = "18932850664240014", parentId = "18932850664240010", label = "晚期", value = "旧石器时代/晚期", type = type, remarks = "距今5-1.2万年"))

        dicts.add(Dict(id = "18932850664240020", label = "新石器时代", value = "新石器时代", type = type, remarks = "距今1.2-0.4万年"))
        dicts.add(Dict(id = "18932850664240021", parentId = "18932850664240020", label = "初期", value = "新石器时代/初期", type = type, remarks = "距今1.2-0.9万年"))
        dicts.add(Dict(id = "18932850664240022", parentId = "18932850664240020", label = "早期", value = "新石器时代/早期", type = type, remarks = "距今0.9-0.7万年"))
        dicts.add(Dict(id = "18932850664240023", parentId = "18932850664240020", label = "中期", value = "新石器时代/中期", type = type, remarks = "距今0.7-0.5万年"))
        dicts.add(Dict(id = "18932850664240024", parentId = "18932850664240020", label = "后期", value = "新石器时代/后期", type = type, remarks = "距今0.5-0.4万年"))

        dicts.add(Dict(id = "18932850664240030", label = "夏", value = "夏", type = type, remarks = "约前2070-前1300年"))

        dicts.add(Dict(id = "18932850664240040", label = "商", value = "商", type = type, remarks = "约前1600-前1046年"))
        dicts.add(Dict(id = "18932850664240041", parentId = "18932850664240040", label = "前期", value = "商/前期", type = type, remarks = "约前1600-前1300年"))
        dicts.add(Dict(id = "18932850664240042", parentId = "18932850664240040", label = "后期", value = "商/早期", type = type, remarks = "约前1300-前1046年"))

        dicts.add(Dict(id = "18932850664240050", label = "西周", value = "西周", type = type, remarks = "前1046-前771年"))
        dicts.add(Dict(id = "18932850664240051", parentId = "18932850664240050", label = "早期", value = "西周/早期", type = type, remarks = "前1046-前922年"))
        dicts.add(Dict(id = "18932850664240052", parentId = "18932850664240050", label = "中期", value = "西周/中期", type = type, remarks = "前922-前878年"))
        dicts.add(Dict(id = "18932850664240053", parentId = "18932850664240050", label = "晚期", value = "西周/晚期", type = type, remarks = "前877-前771年"))

        dicts.add(Dict(id = "18932850664240060", label = "东周", value = "东周", type = type, remarks = "前770-前221年"))
        dicts.add(Dict(id = "18932850664240061", parentId = "18932850664240060", label = "春秋", value = "西周/春秋", type = type, remarks = "前770-前476年"))
        dicts.add(Dict(id = "18932850664240062", parentId = "18932850664240060", label = "战国", value = "西周/战国", type = type, remarks = "前475-前221年"))

        dicts.add(Dict(id = "18932850664240070", label = "秦", value = "秦", type = type, remarks = "前221-前206年"))

        dicts.add(Dict(id = "18932850664240080", label = "西汉", value = "西汉", type = type, remarks = "前206-25年"))
        dicts.add(Dict(id = "18932850664240081", parentId = "18932850664240080", label = "早期", value = "西汉/早期", type = type, remarks = "前206-前118年"))
        dicts.add(Dict(id = "18932850664240082", parentId = "18932850664240080", label = "中期", value = "西汉/中期", type = type, remarks = "前118-前74年"))
        dicts.add(Dict(id = "18932850664240083", parentId = "18932850664240080", label = "晚期", value = "西汉/晚期", type = type, remarks = "前73-25年"))

        dicts.add(Dict(id = "18932850664240090", label = "东汉", value = "东汉", type = type, remarks = "25-220年"))
        dicts.add(Dict(id = "18932850664240091", parentId = "18932850664240090", label = "前期", value = "东汉/前期", type = type, remarks = "25-105年"))
        dicts.add(Dict(id = "18932850664240092", parentId = "18932850664240090", label = "后期", value = "东汉/后期", type = type, remarks = "106-220年"))

        dicts.add(Dict(id = "18932850664240100", label = "魏晋南北朝", value = "魏晋南北朝", type = type, remarks = "220-589年"))
        dicts.add(Dict(id = "18932850664240101", parentId = "18932850664240100", label = "魏晋", value = "魏晋南北朝/魏晋", type = type, remarks = "220-316年"))
        dicts.add(Dict(id = "18932850664240102", parentId = "18932850664240101", label = "魏", value = "魏晋南北朝/魏晋/魏", type = type, remarks = "220-280年"))
        dicts.add(Dict(id = "18932850664240103", parentId = "18932850664240101", label = "蜀", value = "魏晋南北朝/魏晋/蜀", type = type, remarks = "220-280年"))
        dicts.add(Dict(id = "18932850664240104", parentId = "18932850664240101", label = "吴", value = "魏晋南北朝/魏晋/吴", type = type, remarks = "220-280年"))
        dicts.add(Dict(id = "18932850664240105", parentId = "18932850664240101", label = "西晋", value = "魏晋南北朝/魏晋/西晋", type = type, remarks = "280-316年"))

        dicts.add(Dict(id = "18932850664240106", parentId = "18932850664240100", label = "十六国", value = "魏晋南北朝/十六国", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240107", parentId = "18932850664240106", label = "成汉", value = "魏晋南北朝/十六国/成汉", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240108", parentId = "18932850664240106", label = "前赵", value = "魏晋南北朝/十六国/前赵", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240109", parentId = "18932850664240106", label = "后赵", value = "魏晋南北朝/十六国/后赵", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240110", parentId = "18932850664240106", label = "前秦", value = "魏晋南北朝/十六国/前秦", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240111", parentId = "18932850664240106", label = "后秦", value = "魏晋南北朝/十六国/后秦", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240112", parentId = "18932850664240106", label = "西秦", value = "魏晋南北朝/十六国/西秦", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240113", parentId = "18932850664240106", label = "前燕", value = "魏晋南北朝/十六国/前燕", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240114", parentId = "18932850664240106", label = "后燕", value = "魏晋南北朝/十六国/后燕", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240115", parentId = "18932850664240106", label = "南燕", value = "魏晋南北朝/十六国/南燕", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240116", parentId = "18932850664240106", label = "北燕", value = "魏晋南北朝/十六国/北燕", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240117", parentId = "18932850664240106", label = "前凉", value = "魏晋南北朝/十六国/前凉", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240118", parentId = "18932850664240106", label = "后凉", value = "魏晋南北朝/十六国/后凉", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240119", parentId = "18932850664240106", label = "南凉", value = "魏晋南北朝/十六国/南凉", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240120", parentId = "18932850664240106", label = "北凉", value = "魏晋南北朝/十六国/北凉", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240121", parentId = "18932850664240106", label = "西凉", value = "魏晋南北朝/十六国/西凉", type = type, remarks = "317-439年"))
        dicts.add(Dict(id = "18932850664240122", parentId = "18932850664240106", label = "夏", value = "魏晋南北朝/十六国/夏", type = type, remarks = "317-439年"))

        dicts.add(Dict(id = "18932850664240123", parentId = "18932850664240100", label = "北朝", value = "魏晋南北朝/北朝", type = type, remarks = "439-581年"))
        dicts.add(Dict(id = "18932850664240124", parentId = "18932850664240123", label = "北魏", value = "魏晋南北朝/北朝/北魏", type = type, remarks = "439-581年"))
        dicts.add(Dict(id = "18932850664240125", parentId = "18932850664240123", label = "东魏", value = "魏晋南北朝/北朝/东魏", type = type, remarks = "439-581年"))
        dicts.add(Dict(id = "18932850664240126", parentId = "18932850664240123", label = "西魏", value = "魏晋南北朝/北朝/西魏", type = type, remarks = "439-581年"))
        dicts.add(Dict(id = "18932850664240127", parentId = "18932850664240123", label = "北齐", value = "魏晋南北朝/北朝/北齐", type = type, remarks = "439-581年"))
        dicts.add(Dict(id = "18932850664240128", parentId = "18932850664240123", label = "北周", value = "魏晋南北朝/北朝/北周", type = type, remarks = "439-581年"))
        dicts.add(Dict(id = "18932850664240129", parentId = "18932850664240100", label = "东晋", value = "魏晋南北朝/东晋", type = type, remarks = "280-420年"))

        dicts.add(Dict(id = "18932850664240200", label = "南朝", value = "魏晋南北朝/南朝", type = type, remarks = "420-589年"))
        dicts.add(Dict(id = "18932850664240201", parentId = "18932850664240200", label = "宋", value = "魏晋南北朝/南朝/宋", type = type, remarks = "420-589年"))
        dicts.add(Dict(id = "18932850664240202", parentId = "18932850664240200", label = "齐", value = "魏晋南北朝/南朝/齐", type = type, remarks = "420-589年"))
        dicts.add(Dict(id = "18932850664240203", parentId = "18932850664240200", label = "梁", value = "魏晋南北朝/南朝/梁", type = type, remarks = "420-589年"))
        dicts.add(Dict(id = "18932850664240204", parentId = "18932850664240200", label = "陈", value = "魏晋南北朝/南朝/陈", type = type, remarks = "420-589年"))

        dicts.add(Dict(id = "18932850664240300", label = "隋", value = "隋", type = type, remarks = "581-618年"))

        dicts.add(Dict(id = "18932850664240400", label = "唐代", value = "唐代", type = type, remarks = "约7世纪初期-10世纪初"))
        dicts.add(Dict(id = "18932850664240401", parentId = "18932850664240400", label = "初唐", value = "初唐", type = type, remarks = "约7世纪初期-7世纪晚期"))
        dicts.add(Dict(id = "18932850664240402", parentId = "18932850664240400", label = "盛唐", value = "盛唐", type = type, remarks = "约7世纪晚期-8世纪中期"))
        dicts.add(Dict(id = "18932850664240403", parentId = "18932850664240400", label = "中晚唐", value = "中晚唐", type = type, remarks = "约8世纪中期-10世纪初"))

        dicts.add(Dict(id = "18932850664240600", label = "宋", value = "宋", type = type, remarks = "960-1234年"))
        dicts.add(Dict(id = "18932850664240601", parentId = "18932850664240600", label = "宋", value = "宋/宋", type = type, remarks = "960-1276年"))
        dicts.add(Dict(id = "18932850664240602", parentId = "18932850664240600", label = "辽", value = "宋/辽", type = type, remarks = "916-1125年"))
        dicts.add(Dict(id = "18932850664240603", parentId = "18932850664240600", label = "西夏", value = "宋/西夏", type = type, remarks = "1038-1227年"))
        dicts.add(Dict(id = "18932850664240604", parentId = "18932850664240600", label = "金", value = "宋/金", type = type, remarks = "1115-1234年"))

        dicts.add(Dict(id = "18932850664240700", label = "元", value = "元", type = type))
        dicts.add(Dict(id = "18932850664240800", label = "明", value = "明", type = type))
        dicts.add(Dict(id = "18932850664240900", label = "清", value = "清", type = type))
        dicts.add(Dict(id = "18932850664241100", label = "中华民国", value = "中华民国", type = type))
        dicts.add(Dict(id = "18932850664241200", label = "中华人民共和国", value = "中华人民共和国", type = type))
        //@formatter:on
        return dicts
    }

}

