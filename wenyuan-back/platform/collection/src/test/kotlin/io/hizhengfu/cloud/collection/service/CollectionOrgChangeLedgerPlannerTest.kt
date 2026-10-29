package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumLedgerAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionOrgChangeLedgerPlannerTest {

    @Test
    fun `cross org change creates source and target ledger entries`() {
        val entries = CollectionOrgChangeLedgerPlanner.plan(
            fromOrgId = "org-source",
            toOrgId = "org-target",
            reason = "跨馆调拨",
            refBizId = "transfer-1",
        )

        assertEquals(2, entries.size)
        assertEquals("org-source", entries[0].orgId)
        assertEquals("org-target", entries[1].orgId)
        entries.forEach {
            assertEquals(EnumLedgerAction.CORRECT, it.action)
            assertEquals("orgId", it.field)
            assertEquals("org-source", it.before)
            assertEquals("org-target", it.after)
            assertEquals("跨馆调拨", it.reason)
            assertEquals("transfer-1", it.refBizId)
        }
    }
}
