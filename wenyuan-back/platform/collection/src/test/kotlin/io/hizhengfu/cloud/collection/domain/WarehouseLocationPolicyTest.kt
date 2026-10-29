package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WarehouseLocationPolicyTest {
    @Test
    fun `cabinet descendants use three segment numeric code`() {
        WarehouseLocationPolicy.requireValidCode(EnumWarehouseType.CABINET, "1.2.3")
        WarehouseLocationPolicy.requireValidCode(EnumWarehouseType.LAYER, "12.3.8")
    }

    @Test
    fun `invalid cabinet code is rejected`() {
        assertThrows(BadRequestException::class.java) {
            WarehouseLocationPolicy.requireValidCode(EnumWarehouseType.CABINET, "A-01-02")
        }
    }

    @Test
    fun `validator can report invalid cabinet code without throwing`() {
        assertTrue(WarehouseLocationPolicy.isValidCode(EnumWarehouseType.CABINET, "1.2.3"))
        assertFalse(WarehouseLocationPolicy.isValidCode(EnumWarehouseType.CABINET, "A-01"))
    }

    @Test
    fun `display node types belong to exhibition scene`() {
        assertTrue(WarehouseLocationPolicy.isSceneCompatible(EnumLocationScene.EXHIBITION, EnumWarehouseType.SHOWCASE))
        assertTrue(WarehouseLocationPolicy.isSceneCompatible(EnumLocationScene.EXHIBITION, EnumWarehouseType.EXHIBITION_HALL))
        assertFalse(WarehouseLocationPolicy.isSceneCompatible(EnumLocationScene.STORAGE, EnumWarehouseType.SHOWCASE))
    }

    @Test
    fun `location types are separated by scene`() {
        assertEquals(
            setOf(EnumWarehouseType.WAREHOUSE, EnumWarehouseType.AREA, EnumWarehouseType.CABINET, EnumWarehouseType.LAYER, EnumWarehouseType.BOX),
            WarehouseLocationPolicy.typesForScene(EnumLocationScene.STORAGE),
        )
        assertEquals(
            setOf(EnumWarehouseType.EXHIBITION_HALL, EnumWarehouseType.DISPLAY_AREA, EnumWarehouseType.SHOWCASE),
            WarehouseLocationPolicy.typesForScene(EnumLocationScene.EXHIBITION),
        )
        assertEquals(EnumWarehouseType.EXHIBITION_HALL, WarehouseLocationPolicy.defaultType(EnumLocationScene.EXHIBITION))
    }

    @Test
    fun `storage and exhibition nodes cannot be mixed`() {
        assertThrows(BadRequestException::class.java) {
            WarehouseLocationPolicy.requireSceneCompatible(EnumLocationScene.EXHIBITION, EnumWarehouseType.CABINET)
        }
    }
}
