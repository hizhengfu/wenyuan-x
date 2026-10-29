package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.WarehouseVo
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.EnumWarehouseType
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ExhibitionVenueSelectionPolicyTest {

    @Test
    fun `selected exhibition hall resolves stable id and display name`() {
        val result = ExhibitionVenueSelectionPolicy.resolve(
            venueId = "hall-1",
            legacyVenue = "旧地点",
            orgId = "org-1",
            selected = venue(type = EnumWarehouseType.EXHIBITION_HALL),
        )

        assertEquals("hall-1", result.id)
        assertEquals("第一展厅", result.name)
    }

    @Test
    fun `legacy free text remains readable when no venue id was supplied`() {
        val result = ExhibitionVenueSelectionPolicy.resolve(
            venueId = null,
            legacyVenue = " 馆外临展点 ",
            orgId = "org-1",
            selected = null,
        )

        assertNull(result.id)
        assertEquals("馆外临展点", result.name)
    }

    @Test
    fun `showcase cannot be used as exhibition venue`() {
        assertThrows(BadRequestException::class.java) {
            ExhibitionVenueSelectionPolicy.resolve(
                venueId = "hall-1",
                legacyVenue = null,
                orgId = "org-1",
                selected = venue(type = EnumWarehouseType.SHOWCASE),
            )
        }
    }

    @Test
    fun `venue must belong to exhibition organization`() {
        assertThrows(BadRequestException::class.java) {
            ExhibitionVenueSelectionPolicy.resolve(
                venueId = "hall-1",
                legacyVenue = null,
                orgId = "org-2",
                selected = venue(type = EnumWarehouseType.EXHIBITION_HALL),
            )
        }
    }

    private fun venue(type: EnumWarehouseType) = WarehouseVo(
        id = "hall-1",
        orgId = "org-1",
        name = "第一展厅",
        sceneType = EnumLocationScene.EXHIBITION,
        type = type,
    )
}
