package io.hizhengfu.cloud.collection.rfid.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RfidPoliciesTest {
    @Test
    fun hexNormalizationRemovesSeparatorsAndRejectsOddLength() {
        assertEquals("E28011AA", RfidHex.normalize("e2:80-11 aa", "epc"))
        assertThrows(BadRequestException::class.java) { RfidHex.normalize("ABC", "epc") }
    }

    @Test
    fun classifierDistinguishesExpectedExtraUnknownConflictAndMissing() {
        val result = RfidScanClassifier.classify(
            expected = listOf(
                RfidExpectedSnapshot("c1", "E001", "A001"),
                RfidExpectedSnapshot("c2", "E002", null),
                RfidExpectedSnapshot("c3", "E003", null),
            ),
            observed = listOf(
                RfidObservedSnapshot("E001", "A001", "c1"),
                RfidObservedSnapshot("E002", "FFFF", "c2"),
                RfidObservedSnapshot("E004", null, "c4"),
                RfidObservedSnapshot("E005", null, null),
                RfidObservedSnapshot("E001", "B001", "c1"),
            ),
        )

        // 同一 EPC 的最后一次观测 TID 与冻结 TID 不一致，必须阻断而不是按已读放行。
        assertEquals(EnumRfidReadClassification.CONFLICT, result.reads["E001"])
        assertEquals(EnumRfidReadClassification.EXPECTED, result.reads["E002"])
        assertEquals(EnumRfidReadClassification.EXTRA, result.reads["E004"])
        assertEquals(EnumRfidReadClassification.UNKNOWN, result.reads["E005"])
        assertEquals(setOf("c1", "c3"), result.missingCollectionIds)
    }

    @Test
    fun stateMachineRejectsFinalizingBeforeReaderStop() {
        assertThrows(BadRequestException::class.java) {
            RfidScanSessionStateMachine.requireTransition(
                EnumRfidScanSessionState.READING,
                EnumRfidScanSessionState.FINALIZING,
            )
        }
    }
}
