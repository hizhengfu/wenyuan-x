package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.install.CollectionInstall
import io.hizhengfu.cloud.common.annotation.Install
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.collection.dao.EntryVoucherVo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EntryVoucherPolicyTest {

    @Test
    fun `voucher number continues daily sequence and honors manual input`() {
        val today = LocalDate.of(2026, 7, 21)
        assertEquals("RJ-20260721-0001", EntryVoucherNumberGenerator.resolve(null, today, emptySet()))
        assertEquals(
            "RJ-20260721-0003",
            EntryVoucherNumberGenerator.resolve(null, today, setOf("RJ-20260721-0002", "RJ-20260721-0001")),
        )
        assertEquals("RJ-X", EntryVoucherNumberGenerator.resolve("RJ-X", today, setOf("RJ-20260721-0001")))
    }

    @Test
    fun `acquisition auto registration stays disabled because voucher is mandatory`() {
        org.junit.jupiter.api.Assertions.assertFalse(AcquisitionRegistrationPolicy.shouldAutoRegister(true, null))
        org.junit.jupiter.api.Assertions.assertFalse(AcquisitionRegistrationPolicy.shouldAutoRegister(true, ""))
        org.junit.jupiter.api.Assertions.assertFalse(AcquisitionRegistrationPolicy.shouldAutoRegister(true, "coll-1"))
        org.junit.jupiter.api.Assertions.assertFalse(AcquisitionRegistrationPolicy.shouldAutoRegister(false, null))
    }

    @Test
    fun `voucher item must be approved unregistered and unheld`() {
        EntryVoucherItemPolicy.requireRegistrable("a", EnumFlowStatus.PASS, null, heldByOtherVoucher = false)

        assertThrows(BadRequestException::class.java) {
            EntryVoucherItemPolicy.requireRegistrable("a", EnumFlowStatus.AUDIT, null, heldByOtherVoucher = false)
        }
        assertThrows(BadRequestException::class.java) {
            EntryVoucherItemPolicy.requireRegistrable("a", EnumFlowStatus.PASS, "coll-1", heldByOtherVoucher = false)
        }
        assertThrows(BadRequestException::class.java) {
            EntryVoucherItemPolicy.requireRegistrable("a", EnumFlowStatus.PASS, null, heldByOtherVoucher = true)
        }
    }

    @Test
    fun `voucher execution requires handover signatures and signed scan`() {
        EntryVoucherExecutionPolicy.requireExecutable(
            EntryVoucherVo(
                basis = "征集审批通过",
                entryDate = LocalDate.of(2026, 8, 9),
                handoverUser = "user-a",
                receiverUser = "user-b",
                custodian = "user-c",
                warehouseId = "warehouse-1",
                attachmentIds = "signed-voucher-file",
                acquisitionIds = "a,b",
                verifiedAcquisitionIds = "b,a",
            ),
        )

        assertThrows(BadRequestException::class.java) {
            EntryVoucherExecutionPolicy.requireExecutable(
                EntryVoucherVo(
                    basis = "征集审批通过",
                    entryDate = LocalDate.of(2026, 8, 9),
                    handoverUser = "user-a",
                    receiverUser = "user-b",
                    custodian = "user-c",
                    warehouseId = "warehouse-1",
                    acquisitionIds = "a",
                    verifiedAcquisitionIds = "a",
                ),
            )
        }

        assertThrows(BadRequestException::class.java) {
            EntryVoucherExecutionPolicy.requireExecutable(
                EntryVoucherVo(
                    basis = "征集审批通过",
                    entryDate = LocalDate.of(2026, 8, 9),
                    handoverUser = "user-a",
                    receiverUser = "user-b",
                    custodian = "user-c",
                    warehouseId = "warehouse-1",
                    attachmentIds = "signed-voucher-file",
                    acquisitionIds = "a,b",
                    verifiedAcquisitionIds = "a",
                ),
            )
        }
    }

    @Test
    fun `collection install exposes collection config tab with Chinese label`() {
        val install = CollectionInstall::class.java.getAnnotation(Install::class.java)

        assertEquals(CollectionConst.CONFIG_MODULE, install.module)
        assertEquals("藏品管理", install.label)
    }
}
