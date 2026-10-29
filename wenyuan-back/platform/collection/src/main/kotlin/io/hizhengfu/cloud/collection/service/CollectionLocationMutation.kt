package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionOrgs
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.WarehouseLocationPolicy
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.update

/**
 * 调用方必须处于写事务中，并在读取位置结构/引用之前取得同馆锁。
 * 实际更新版本使多进程共享数据库锁；锁随事务提交或回滚释放。
 * 快照隔离发生并发版本冲突时由数据库拒绝旧事务，不继续使用旧结构。
 */
internal object CollectionLocationMutation {
    /** 跨馆业务按稳定顺序锁定全部参与馆，避免互相调拨形成相反锁序。 */
    fun acquireAll(orgIds: Collection<String>) {
        orgIds.distinct().sorted().forEach(::acquire)
    }

    fun acquire(orgId: String) {
        val changed = CollectionOrgs.update({ CollectionOrgs.id eq orgId }) {
            it[locationMutationVersion] = CollectionOrgs.locationMutationVersion + 1L
        }
        if (changed != 1) throw BadRequestException("收藏单位不存在，无法核验位置变更")
    }

    /** 历史位置和展陈位置同样校验真实身份；具体业务另行限制场景。 */
    fun reference(orgId: String, value: String?, allowRetired: Boolean = false): WarehouseDao? {
        val id = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        acquire(orgId)
        val location = WarehouseDao.findById(id) ?: throw BadRequestException("库位不存在")
        try {
            location.refresh(flush = true)
        } catch (_: EntityNotFoundException) {
            throw BadRequestException("库位已被删除，请重新选择位置")
        }
        if (location.orgId != orgId) throw BadRequestException("藏品与库位必须属于同一收藏单位")
        if (!allowRetired && location.status != EnumContentStatus.NORMAL)
            throw BadRequestException("库位已退役，请重新选择有效位置")
        WarehouseTreeStructure.ancestors(location.parentId, orgId, location.sceneType, setOf(id))
        return location
    }

    fun storageReference(orgId: String, value: String?): String? {
        val location = reference(orgId, value) ?: return null
        if (location.sceneType != EnumLocationScene.STORAGE || location.type !in WarehouseLocationPolicy.storageTypes)
            throw BadRequestException("藏品当前库位必须为保管位置")
        return location.id.value
    }
}
