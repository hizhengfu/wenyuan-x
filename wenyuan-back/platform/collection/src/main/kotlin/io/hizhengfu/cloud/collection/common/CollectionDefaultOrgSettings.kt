package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.Configs
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Component

/** 默认馆由后台系统设置管理，每次办理在当前事务中读取，不依赖启动配置或缓存。 */
@Component
class CollectionDefaultOrgSettings {
    fun resolve(): String {
        val rows = Configs.select(Configs.content, Configs.enable).where { Configs.name eq KEY }.limit(2).toList()
        if (rows.size > 1) throw BadRequestException("默认收藏单位设置重复，请核实后端系统设置")
        val setting = rows.singleOrNull()
        val selected = setting?.get(Configs.content)?.trim()?.takeIf(String::isNotBlank)
        val museum = if (selected != null) {
            if (setting[Configs.enable] != true) throw BadRequestException("后端默认收藏单位设置已停用，请核实后再办理")
            CollectionOrgDao.findById(selected) ?: throw BadRequestException("后端设置的默认收藏单位不存在，请重新选择")
        } else {
            val candidates = CollectionOrgDao.all().limit(2).toList()
            if (candidates.isEmpty()) throw BadRequestException("尚未初始化收藏单位")
            if (candidates.size != 1) throw BadRequestException("单馆模式存在多个收藏单位，请在后端系统设置中选择默认收藏单位后再办理")
            candidates.single()
        }
        if (museum.status != EnumContentStatus.NORMAL || museum.orgStatus != EnumOrgStatus.ENABLED) {
            throw BadRequestException("默认收藏单位已停用或不可用，请核实后端系统设置")
        }
        return museum.id.value
    }

    companion object {
        const val KEY = "COLLECTION_DEFAULT_ORG_ID"

        /** 升级只更新字段说明，不覆盖管理员已保存的选择。 */
        fun seed() {
            fun schema(row: UpdateBuilder<*>) {
                row[Configs.fieldType] = "STRING"
                row[Configs.module] = CollectionConst.CONFIG_MODULE
                row[Configs.fieldGroup] = "藏品管理"
                row[Configs.label] = "默认收藏单位ID（单馆模式）"
                row[Configs.required] = false
                row[Configs.sort] = 1
            }
            if (Configs.select(Configs.id).where { Configs.name eq KEY }.empty()) {
                Configs.insert {
                    it[name] = KEY
                    it[content] = ""
                    it[enable] = true
                    it[isPrivate] = true
                    it[remarks] = "单馆模式可指定收藏单位ID；留空时仅自动选择唯一收藏单位。多馆模式按人员主机构映射决定，不使用此项。修改保存后生效，无需配置文件或重启。"
                    schema(it)
                }
            } else Configs.update({ Configs.name eq KEY }) { schema(it) }
        }
    }
}
