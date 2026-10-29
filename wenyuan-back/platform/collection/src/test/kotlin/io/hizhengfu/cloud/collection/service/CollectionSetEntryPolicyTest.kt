package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionSetEntryPartVo
import io.hizhengfu.cloud.collection.dao.CollectionSetEntryVo
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSetRelation
import io.hizhengfu.cloud.collection.domain.EnumSource
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionSetEntryPolicyTest {
    private fun main() = CollectionVo(
        name = "成套茶具",
        categoryId = "category-1",
        level = EnumRelicLevel.GENERAL,
        source = EnumSource.DONATE,
        quantity = 1,
        unit = "套",
    )

    @Test
    fun `valid main and independent parts pass`() {
        val entry = CollectionSetEntryVo(
            main = main(),
            parts = listOf(
                CollectionSetEntryPartVo(partNo = "01", partName = "茶壶"),
                CollectionSetEntryPartVo(partNo = "02", partName = "茶杯", quantity = 4),
            ),
        )
        assertDoesNotThrow { CollectionSetEntryPolicy.requireValid(entry) }
    }

    @Test
    fun `duplicate part number or main relation is rejected`() {
        assertThrows(BadRequestException::class.java) {
            CollectionSetEntryPolicy.requireValid(
                CollectionSetEntryVo(
                    main = main(),
                    parts = listOf(
                        CollectionSetEntryPartVo(partNo = "01", partName = "茶壶"),
                        CollectionSetEntryPartVo(partNo = "01", partName = "茶杯"),
                    ),
                ),
            )
        }
        assertThrows(BadRequestException::class.java) {
            CollectionSetEntryPolicy.requireValid(
                CollectionSetEntryVo(
                    main = main(),
                    parts = listOf(
                        CollectionSetEntryPartVo(partNo = "01", partName = "错误主件", relation = EnumSetRelation.MAIN),
                    ),
                ),
            )
        }
    }
}
