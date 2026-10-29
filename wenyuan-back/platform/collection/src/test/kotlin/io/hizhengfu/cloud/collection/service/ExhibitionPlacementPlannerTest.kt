package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.ExhibitionPlacementVo
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ExhibitionPlacementPlannerTest {

    @Test
    fun `one showcase can contain multiple collections`() {
        val plan = ExhibitionPlacementPlanner.plan(
            listOf(
                ExhibitionPlacementVo(collectionId = "collection-1", locationId = "showcase-1"),
                ExhibitionPlacementVo(collectionId = "collection-2", locationId = "showcase-1"),
                ExhibitionPlacementVo(collectionId = "collection-3", locationId = "showcase-2"),
            ),
        )

        assertEquals(3, plan.size)
        assertEquals(listOf("showcase-1", "showcase-1", "showcase-2"), plan.map { it.locationId })
    }

    @Test
    fun `one collection cannot be assigned twice in the same exhibition`() {
        assertThrows(BadRequestException::class.java) {
            ExhibitionPlacementPlanner.plan(
                listOf(
                    ExhibitionPlacementVo(collectionId = "collection-1", locationId = "showcase-1"),
                    ExhibitionPlacementVo(collectionId = "collection-1", locationId = "showcase-2"),
                ),
            )
        }
    }

    @Test
    fun `every selected placement needs a showcase`() {
        assertThrows(BadRequestException::class.java) {
            ExhibitionPlacementPlanner.plan(
                listOf(ExhibitionPlacementVo(collectionId = "collection-1", locationId = "")),
            )
        }
    }
}
