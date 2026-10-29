package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.collection.dao.AcquisitionVo
import io.hizhengfu.cloud.collection.dao.AppraisalVo
import io.hizhengfu.cloud.collection.dao.CollectionTransferVo
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.dao.DeregistrationVo
import io.hizhengfu.cloud.collection.dao.InventoryVo
import io.hizhengfu.cloud.collection.dao.LoanVo
import io.hizhengfu.cloud.collection.dao.MovementVo
import io.hizhengfu.cloud.collection.dao.ReproductionVo
import io.hizhengfu.cloud.collection.dao.RequisitionVo
// RestorationVo 已迁移至 module-collection-restoration
import jakarta.validation.Validation
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionBusinessValidationTest {

    private val validator = Validation.byDefaultProvider()
        .configure()
        .messageInterpolator(ParameterMessageInterpolator())
        .buildValidatorFactory()
        .validator

    @Test
    fun `collection register requires core catalog fields`() {
        assertViolationFields(
            value = CollectionVo(
                name = "青釉瓷碗",
                categoryId = "",
                quantity = 0,
                unit = "",
            ),
            // 入藏日期不再单独做 Bean 校验；服务层要求 acquireDate / acquireDateRange 至少一个有值。
            expected = setOf("categoryId", "level", "quantity", "unit", "source"),
        )
    }

    @Test
    fun `collection process requests require fields that make the business document valid`() {
        assertViolationFields(
            value = AcquisitionVo(name = "", quantity = 0, unit = "", holder = "", reason = ""),
            expected = setOf("name", "source", "quantity", "unit", "acquireDate", "holder", "reason"),
        )
        // collectionId 不再是 Bean 校验必填：可用 rfidTag 替代，由服务端二选一裁决
        assertViolationFields(
            value = MovementVo(documentNo = "", collectionId = "", type = null, reason = null),
            expected = setOf("documentNo", "type", "reason"),
        )
        assertViolationFields(
            value = RequisitionVo(collectionIds = "", purpose = null, basis = "", department = "", operator = ""),
            expected = setOf("collectionIds", "purpose", "basis", "department", "operator"),
        )
        assertViolationFields(
            value = LoanVo(title = "", collectionIds = "", borrower = "", loanDate = null),
            expected = setOf("title", "collectionIds", "borrower", "loanDate"),
        )
        assertViolationFields(
            value = InventoryVo(batchName = "", result = null),
            expected = setOf("batchName", "result"),
        )
    }

    @Test
    fun `special collection workflows require their decision fields`() {
        assertViolationFields(
            value = AppraisalVo(collectionId = "", resultLevel = null, conclusion = ""),
            expected = setOf("collectionId", "resultLevel", "conclusion"),
        )
        // 修复 VO 校验用例已随模块迁移至 module-collection-restoration
        assertViolationFields(
            value = ReproductionVo(collectionId = "", repType = null, purpose = "", repDate = null),
            expected = setOf("collectionId", "repType", "purpose", "repDate"),
        )
        assertViolationFields(
            value = DeregistrationVo(collectionId = "", reason = null, detail = ""),
            expected = setOf("collectionId", "reason", "detail"),
        )
        assertViolationFields(
            value = CollectionTransferVo(collectionId = "", toOrgId = "", reason = ""),
            expected = setOf("collectionId", "toOrgId", "reason"),
        )
    }

    private fun assertViolationFields(value: Any, expected: Set<String>) {
        val actual = validator.validate(value).map { it.propertyPath.toString() }.toSet()
        assertTrue(
            actual.containsAll(expected),
            "Expected validation errors for $expected, but got $actual",
        )
    }
}
