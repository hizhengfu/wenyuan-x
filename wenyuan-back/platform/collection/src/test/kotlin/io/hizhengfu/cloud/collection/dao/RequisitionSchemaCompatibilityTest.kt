package io.hizhengfu.cloud.collection.dao

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RequisitionSchemaCompatibilityTest {
    @Test
    fun `additional internal reasons preserve all persisted legacy ordinals`() {
        val old=listOf("RESEARCH","EXHIBIT","RESTORE","LOAN","DIGITIZE","INVENTORY","TRANSFER","ACQUISITION","EMERGENCY")
        assertEquals(old,io.hizhengfu.cloud.collection.domain.EnumMoveReason.entries.take(old.size).map { it.name })
        assertEquals(9,io.hizhengfu.cloud.collection.domain.EnumMoveReason.RELOCATION.ordinal)
        assertEquals(10,io.hizhengfu.cloud.collection.domain.EnumMoveReason.OTHER.ordinal)
    }

    @Test
    fun `legacy collection ids column has client default for batch inserts that omit compatibility field`() {
        assertEquals("", Requisitions.collectionIds.defaultValueFun?.invoke())
    }
}
