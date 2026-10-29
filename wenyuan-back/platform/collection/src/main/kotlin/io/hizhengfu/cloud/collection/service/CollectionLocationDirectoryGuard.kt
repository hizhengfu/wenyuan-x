package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionOrgs
import io.hizhengfu.cloud.collection.dao.Warehouses
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.device.dao.MuseumLocations
import io.hizhengfu.cloud.museum.device.dao.MuseumLocationSources
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*

/** 收藏单位目录和物理位置树的跨模块事务边界；不得用于普通只读查询。 */
object CollectionLocationDirectoryGuard {
    fun lockForSynchronization(orgId: String) {
        val root = currentRoot(orgId)
        val owner = root?.let(::rootOwner) ?: orgId
        CollectionLocationMutation.acquireAll(listOf(orgId, owner))
        if (currentRoot(orgId) != root) changed()
        if (root != null) {
            if (rootOwner(root) != owner) changed()
            CollectionLocationMutation.reference(owner, root)
        }
    }

    fun lockRootChange(orgId: String, targetRoot: String?) {
        val previous = currentRoot(orgId)
        val owners = listOfNotNull(orgId, previous?.let(::optionalRootOwner), targetRoot?.let(::rootOwner))
        CollectionLocationMutation.acquireAll(owners)
        if (currentRoot(orgId) != previous) changed()
        if (targetRoot != null && rootOwner(targetRoot) !in owners) changed()
        if (previous != targetRoot && hasSynchronizedLocations(orgId))
            throw BadRequestException("收藏单位已有同步位置及来源历史，不能直接更换或清除安装根；请核对位置迁移")
    }

    fun hasInstallationData(orgId: String): Boolean =
        (MuseumLocations.exists() && MuseumLocations.select(MuseumLocations.id).where { MuseumLocations.orgId eq orgId }.limit(1).any()) ||
            (MuseumLocationSources.exists() && MuseumLocationSources.select(MuseumLocationSources.id).where { MuseumLocationSources.orgId eq orgId }.limit(1).any())

    private fun hasSynchronizedLocations(orgId: String): Boolean = MuseumLocationSources.exists() &&
        MuseumLocationSources.select(MuseumLocationSources.id).where {
            (MuseumLocationSources.orgId eq orgId) and (MuseumLocationSources.sourceType eq "BUSINESS_MASTER") and
                (MuseumLocationSources.sourceCode eq "COL_WAREHOUSE")
        }.limit(1).any()

    private fun currentRoot(orgId: String): String? = CollectionOrgs.select(CollectionOrgs.locationRootId)
        .where { CollectionOrgs.id eq orgId }.singleOrNull()?.get(CollectionOrgs.locationRootId)
        ?: run {
            if (CollectionOrgs.select(CollectionOrgs.id).where { CollectionOrgs.id eq orgId }.empty())
                throw BadRequestException("收藏单位不存在")
            null
        }

    private fun optionalRootOwner(id: String): String? = Warehouses.select(Warehouses.orgId)
        .where { Warehouses.id eq id }.singleOrNull()?.get(Warehouses.orgId)

    private fun rootOwner(id: String): String = Warehouses.select(Warehouses.orgId).where {
        (Warehouses.id eq id) and (Warehouses.status eq EnumContentStatus.NORMAL)
    }.singleOrNull()?.get(Warehouses.orgId) ?: throw BadRequestException("关联安装位置不存在或已停用")

    private fun changed(): Nothing = throw BadRequestException("安装根配置在办理期间已变化，请重新读取后重试")
}
