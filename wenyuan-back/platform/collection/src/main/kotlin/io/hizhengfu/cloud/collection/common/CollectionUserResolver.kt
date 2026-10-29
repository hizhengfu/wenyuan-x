package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

/**
 * 系统用户 id → 用户名 解析（用于经办人/复核人等展示）。
 * 复用 module-sys 的 sys_user 表；调用方须处于事务中（FlowService/CrudService 已是 @Transactional）。
 */
@Component
class CollectionUserResolver {

    fun names(ids: Collection<String?>): Map<String, String> {
        val clean = ids.filterNotNull().map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (clean.isEmpty()) return emptyMap()
        val map = HashMap<String, String>(clean.size)
        clean.forEach { id ->
            Users.selectAll().where { Users.id eq id }.firstOrNull()?.let { map[id] = it[Users.name] }
        }
        return map
    }
}
