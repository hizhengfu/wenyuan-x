package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumCollectionNature
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CollectionStatBuilderTest {
    @Test fun `registration status survives snapshots and is derived again when dates are copied`() {
        val original=CollectionVo(registeredAt=null,historicalConfirmedAt=LocalDateTime.of(2026,10,14,10,0),lifeStatus=EnumCollectionStatus.IN_STORE)
        val restored=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.fromJson(io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(original),CollectionVo::class.java)!!
        assertEquals(true,restored.registered)
        assertEquals(false,restored.copy(historicalConfirmedAt=null).registered)
        val falseClaim=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.fromJson("{\"registered\":true}",CollectionVo::class.java)!!
        assertEquals(false,falseClaim.registered)
    }


    @Test
    fun `ungraded work includes pending and unverified relics but excludes other collections and retired records`() {
        val stat = CollectionStatBuilder.build(listOf(
            CollectionVo(nature = EnumCollectionNature.RELIC, level = EnumRelicLevel.UNRATED,
                lifeStatus = EnumCollectionStatus.PENDING),
            CollectionVo(nature = EnumCollectionNature.RELIC, level = null,
                lifeStatus = EnumCollectionStatus.IN_STORE),
            CollectionVo(nature = EnumCollectionNature.RELIC, level = EnumRelicLevel.UNRATED,
                lifeStatus = EnumCollectionStatus.IN_STORE, registeredAt = LocalDateTime.of(2020, 1, 1, 0, 0)),
            CollectionVo(nature = EnumCollectionNature.RELIC, level = EnumRelicLevel.UNRATED,
                lifeStatus = EnumCollectionStatus.DEREGISTERED),
            CollectionVo(nature = EnumCollectionNature.OTHER, level = EnumRelicLevel.UNRATED),
            CollectionVo(nature = null, level = EnumRelicLevel.UNRATED),
            CollectionVo(nature = EnumCollectionNature.SCIENTIFIC_FOSSIL, level = EnumRelicLevel.UNRATED),
            CollectionVo(nature = EnumCollectionNature.RELIC, level = EnumRelicLevel.FIRST,
                lifeStatus = EnumCollectionStatus.PENDING),
        ))
        assertEquals(3, stat.ungradedRelicTotal)
        assertEquals(1, stat.total)
        assertEquals(1, stat.byRelicLevel["UNRATED"])
    }

    @Test
    fun `summary includes category source warehouse and new counts`() {
        val stat = CollectionStatBuilder.build(
            listOf(
                CollectionVo(
                    categoryId = "cat-porcelain",
                    nature = EnumCollectionNature.RELIC,
                    quantity = 2,
                    unit = "件",
                    era = "明",
                    source = EnumSource.PURCHASE,
                    warehouseId = "wh-1",
                    level = EnumRelicLevel.FIRST,
                    lifeStatus = EnumCollectionStatus.IN_STORE,
                    coverImage = "cover-1",
                    registeredAt = LocalDateTime.of(2026, 6, 1, 10, 0),
                    attachmentCount = 1,
                ),
                CollectionVo(
                    categoryId = "cat-porcelain",
                    nature = EnumCollectionNature.RELIC,
                    quantity = 1,
                    unit = "套",
                    era = "明",
                    source = EnumSource.DONATE,
                    warehouseId = "wh-2",
                    level = EnumRelicLevel.SECOND,
                    lifeStatus = EnumCollectionStatus.ON_LOAN,
                    registeredAt = LocalDateTime.of(2026, 1, 3, 10, 0),
                    mediaCount = 2,
                ),
                CollectionVo(
                    categoryId = null,
                    nature = EnumCollectionNature.OTHER,
                    quantity = 3,
                    unit = "件",
                    era = null,
                    source = null,
                    warehouseId = null,
                    level = EnumRelicLevel.GENERAL,
                    lifeStatus = EnumCollectionStatus.PENDING,
                    registeredAt = LocalDateTime.of(2025, 12, 31, 10, 0),
                ),
            ),
            today = LocalDate.of(2026, 6, 24),
        )

        assertEquals(3, stat.total)
        assertEquals(2, stat.byNature["RELIC"])
        assertEquals(1, stat.byNature["OTHER"])
        assertEquals(1, stat.byRelicLevel["FIRST"])
        assertEquals(1, stat.byRelicLevel["SECOND"])
        assertEquals(null, stat.byRelicLevel["GENERAL"])
        assertEquals(5L, stat.quantityByUnit["件"])
        assertEquals(1L, stat.quantityByUnit["套"])
        assertEquals(0, stat.quantityUnverifiedTotal)
        assertEquals(2, stat.byCategory["cat-porcelain"])
        assertEquals(1, stat.byCategory["未分类"])
        assertEquals(1, stat.bySource["PURCHASE"])
        assertEquals(1, stat.bySource["未设置"])
        assertEquals(1, stat.byWarehouse["wh-1"])
        assertEquals(1, stat.byWarehouse["未登记库位"])
        assertEquals(2, stat.byEra["明"])
        assertEquals(1, stat.byEra["未标注"])
        assertEquals(2, stat.imageCount)
        assertEquals(1, stat.attachmentCount)
        assertEquals(67, stat.imageRate)
        assertEquals(33, stat.attachmentRate)
        assertEquals(2, stat.registeredThisYear)
        assertEquals(1, stat.registeredThisMonth)
    }

    @Test
    fun `formal snapshot excludes pending and retired records without inventing nature or quantity`() {
        val stat = CollectionStatBuilder.build(listOf(
            CollectionVo(registeredAt = LocalDateTime.of(2026, 1, 1, 0, 0),
                lifeStatus = EnumCollectionStatus.ON_LOAN, nature = null, quantity = null, unit = null),
            CollectionVo(registeredAt = null, lifeStatus = EnumCollectionStatus.PENDING,
                nature = EnumCollectionNature.RELIC, quantity = 4, unit = "件"),
            CollectionVo(registeredAt = LocalDateTime.of(2025, 1, 1, 0, 0),
                lifeStatus = EnumCollectionStatus.DEREGISTERED, nature = EnumCollectionNature.RELIC,
                quantity = 2, unit = "件"),
        ))

        assertEquals(1, stat.total)
        assertEquals(1, stat.byStatus["ON_LOAN"])
        assertEquals(1, stat.byNature["性质待核实"])
        assertEquals(emptyMap<String, Int>(), stat.byRelicLevel)
        assertEquals(emptyMap<String, Long>(), stat.quantityByUnit)
        assertEquals(1, stat.quantityUnverifiedTotal)
        assertEquals(1, stat.pendingRegistrationTotal)
        assertEquals(1, stat.retiredTotal)
    }
}
