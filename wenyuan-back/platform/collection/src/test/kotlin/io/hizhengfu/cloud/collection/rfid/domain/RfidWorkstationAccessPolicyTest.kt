package io.hizhengfu.cloud.collection.rfid.domain

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RfidWorkstationAccessPolicyTest {
    @Test
    fun `maps every workstation capability to the required system authorities`() {
        assertEquals(
            setOf(
                CollectionConst.RFID_DESK,
                CollectionConst.SELECT,
                CollectionConst.RFID_SCAN,
                CollectionConst.RFID_FINALIZE,
            ),
            RfidWorkstationAccessPolicy.requiredAuthorities(
                setOf(EnumRfidWorkstationCapability.BORROW_RETURN, EnumRfidWorkstationCapability.INVENTORY),
            ),
        )
        assertEquals(
            setOf(CollectionConst.RFID_DESK, CollectionConst.SELECT, CollectionConst.RFID_SCAN, CollectionConst.RFID_TAG_WRITE),
            RfidWorkstationAccessPolicy.requiredAuthorities(setOf(EnumRfidWorkstationCapability.TAG_ISSUE)),
        )
        assertEquals(
            setOf(CollectionConst.RFID_DESK, CollectionConst.RFID_PASSAGE),
            RfidWorkstationAccessPolicy.requiredAuthorities(setOf(EnumRfidWorkstationCapability.PASSAGE_MONITOR)),
        )
    }

    @Test
    fun `requires all workstation authorities while preserving administrator bypass`() {
        val required = setOf(
            CollectionConst.RFID_DESK,
            CollectionConst.SELECT,
            CollectionConst.RFID_SCAN,
            CollectionConst.RFID_FINALIZE,
        )
        assertThrows(BadRequestException::class.java) {
            RfidWorkstationAccessPolicy.requireAllowed(
                setOf(CollectionConst.RFID_DESK, CollectionConst.SELECT, CollectionConst.RFID_SCAN),
                required,
            )
        }
        assertTrue(RfidWorkstationAccessPolicy.isAllowed(setOf("ADMIN"), required))
        assertTrue(RfidWorkstationAccessPolicy.isAllowed(setOf(CollectionConst.ADMIN), required))
        assertTrue(RfidWorkstationAccessPolicy.isAllowed(emptySet(), required, administrative = true))
    }
}
