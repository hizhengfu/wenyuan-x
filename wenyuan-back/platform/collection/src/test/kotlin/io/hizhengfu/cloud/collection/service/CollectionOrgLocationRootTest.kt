package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.dao.CollectionOrgVo
import io.hizhengfu.cloud.collection.dao.CollectionOrgCodes
import io.hizhengfu.cloud.collection.dao.CollectionOrgCodeChanges
import io.hizhengfu.cloud.collection.dao.CollectionOrgs
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.dao.Warehouses
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.EnumWarehouseType
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class CollectionOrgLocationRootTest {

    @Test
    fun `collection unit persists an explicit physical location root and returns its name`() {
        val database = Database.connect(
            "jdbc:sqlite:file:collection-org-location-root-${System.nanoTime()}?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC",
        )
        val service = CollectionOrgService(CollectionScopeResolver("single", "MUSEUM"))
        val warehouseService = WarehouseService(
            CollectionScopeResolver("single", "MUSEUM"),
            mock(BusinessAttachmentBinder::class.java),
        )

        transaction(database) {
            SchemaUtils.create(CollectionOrgCodes, CollectionOrgCodeChanges, CollectionOrgs, Warehouses)
            CollectionOrgDao.new("MUSEUM") { code="MUSEUM";name="测试馆" }
            WarehouseDao.new("BUILDING-1") {
                orgId = "MUSEUM"
                code = "BUILDING-1"
                name = "一号楼"
                sceneType = EnumLocationScene.STORAGE
                type = EnumWarehouseType.AREA
                parentId = "0"
                parentIds = "0,"
                isLeaf = true
            }

            val orgId = service.create(
                CollectionOrgVo(
                    code = "BUILDING-UNIT",
                    name = "一号楼收藏单位",
                    locationRootId = "BUILDING-1",
                ),
            )

            assertEquals("BUILDING-1", CollectionOrgDao.findById(orgId)?.locationRootId)
            assertEquals("一号楼", service.get(orgId)?.locationRootName)
            assertThrows(BadRequestException::class.java) {
                service.update(orgId, CollectionOrgVo(locationRootId = "MISSING"))
            }
            assertEquals("BUILDING-1", CollectionOrgDao.findById(orgId)?.locationRootId)
            val deleteError = assertThrows(BadRequestException::class.java) {
                warehouseService.delete("BUILDING-1")
            }
            assertEquals("该位置已作为收藏单位「一号楼收藏单位」的关联安装位置，不能删除", deleteError.message)
        }
    }
}
