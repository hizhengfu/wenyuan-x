package io.hizhengfu.cloud.museum.install

import EnumAlarmRuleType
import EnumAlarmStatus
import EnumDeviceType
import EnumLayerType
import EnumTaskProcessType
import EnumTaskType
import io.hizhengfu.cloud.common.annotation.Install
import io.hizhengfu.cloud.common.domain.Dict
import io.hizhengfu.cloud.common.domain.Menu
import io.hizhengfu.cloud.common.install.AbstractInstall
import io.hizhengfu.cloud.museum.dao.*
import org.jetbrains.exposed.v1.core.Table
import org.springframework.stereotype.Component

@Install("博物馆模块初始化")
@Component
class MuseumInstall : AbstractInstall() {
    override fun initTables(): Set<Table>? {
        val tables = mutableSetOf<Table>()
        tables.add(MuPlaces)
        tables.add(MuRooms)
        tables.add(MuDevices)
        tables.add(MuTours)
        tables.add(MuLayers)
        tables.add(MuPointPositions)
        tables.add(MuTasks)
        tables.add(MuTaskDetails)
        tables.add(MuHistoryDatas)

        tables.add(MuEvents)
        tables.add(MuAlarmEvents)
        tables.add(MuAlarmRules)
        tables.add(MuAlarmDisposeLogs)

        return tables
    }

    override fun initMenus(): Set<Menu>? {
        val menus = mutableSetOf<Menu>()
        val pid = "mu"
        val childIds = "0,$pid,"
        menus.add(Menu(id = pid, parentId = "0", parentIds = "0,", sort = 1, name = "功能管理", path = "/manage", icon = "mdi:widgets"))
        menus.add(Menu(id = "mu-1", parentId = pid, parentIds = childIds, sort = 1, name = "预警查询", path = "/manage/alarmEvent", icon = "mdi:cloud-question"))
        menus.add(Menu(id = "mu-2", parentId = pid, parentIds = childIds, sort = 2, name = "数据查询", path = "/manage/historyData", icon = "mdi:database"))
        menus.add(Menu(id = "mu-3", parentId = pid, parentIds = childIds, sort = 3, name = "导览管理", path = "/manage/tour", icon = "mdi:account-supervisor"))
        menus.add(Menu(id = "mu-4", parentId = pid, parentIds = childIds, sort = 4, name = "问答管理", path = "/manage/question", icon = "mdi:information"))
        menus.add(Menu(id = "mu-5", parentId = pid, parentIds = childIds, sort = 5, name = "游客管理", path = "/manage/visitor", icon = "mdi:city-variant"))
        menus.add(Menu(id = "mu-6", parentId = pid, parentIds = childIds, sort = 6, name = "异常管理", path = "/manage/handle", icon = "mdi:alarm-light"))
        menus.add(Menu(id = "mu-7", parentId = pid, parentIds = childIds, sort = 7, name = "场馆管理", path = "/manage/place", icon = "mdi:city-variant"))
        menus.add(Menu(id = "mu-8", parentId = pid, parentIds = childIds, sort = 8, name = "展厅管理", path = "/manage/room", icon = "mdi:view-dashboard-variant"))
        menus.add(Menu(id = "mu-9", parentId = pid, parentIds = childIds, sort = 9, name = "设备管理", path = "/manage/device", icon = "mdi:chip"))
        menus.add(Menu(id = "mu-10", parentId = pid, parentIds = childIds, sort = 10, name = "机器人管理", path = "/manage/robot", icon = "mdi:robot"))
        menus.add(Menu(id = "mu-11", parentId = pid, parentIds = childIds, sort = 11, name = "任务管理", path = "/manage/task", icon = "mdi:clock-check"))
        menus.add(Menu(id = "mu-12", parentId = pid, parentIds = childIds, sort = 12, name = "可视化标记", path = "/manage/layer", icon = "mdi:layers"))
        return menus
    }

    override fun initDicts(): Set<Dict>? {
        val dicts = mutableSetOf<Dict>()
        EnumDeviceType.values().forEachIndexed { index, postType ->
            dicts.add(Dict(label = postType.description, value = postType.name, type = "mu_device_type", sort = index, remarks = "设备类型"))
        }

        EnumTaskType.values().forEachIndexed { index, postType ->
            dicts.add(Dict(label = postType.description, value = postType.name, type = "mu_task_type", sort = index, remarks = "任务类型"))
        }
        EnumTaskProcessType.values().forEachIndexed { index, postType ->
            dicts.add(Dict(label = postType.description, value = postType.name, type = "mu_task_process_type", sort = index, remarks = "任务类型"))
        }

        EnumLayerType.values().forEachIndexed { index, postType ->
            dicts.add(Dict(label = postType.description, value = postType.name, type = "mu_layer_type", sort = index, remarks = "图层类型"))
        }


        EnumAlarmStatus.values().forEachIndexed { index, postType ->
            dicts.add(Dict(label = postType.description, value = postType.name, type = "mu_alarm_status", sort = index, remarks = "预警事件类型"))
        }

        EnumAlarmRuleType.values().forEachIndexed { index, postType ->
            dicts.add(Dict(label = postType.description, value = postType.name, type = "mu_alarm_rule_type", sort = index, remarks = "预警规则类型"))
        }
        return dicts
    }
}
