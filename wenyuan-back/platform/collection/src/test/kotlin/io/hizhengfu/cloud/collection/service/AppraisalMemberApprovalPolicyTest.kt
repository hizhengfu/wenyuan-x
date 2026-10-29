package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.AppraisalMemberVo
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AppraisalMemberApprovalPolicyTest {

    @Test
    fun `selected internal members become self-select committee assignees`() {
        val members = listOf(
            AppraisalMemberVo(userId = "u-1", memberName = "张三"),
            AppraisalMemberVo(userId = "u-2", memberName = "李四"),
            AppraisalMemberVo(userId = "u-1", memberName = "张三"),
            AppraisalMemberVo(memberName = "外部专家"),
        )

        assertEquals(listOf("u-1", "u-2"), AppraisalMemberApprovalPolicy.assignees(members))
    }

    @Test
    fun `submission requires at least one internal member`() {
        assertThrows(BadRequestException::class.java) {
            AppraisalMemberApprovalPolicy.requireSubmittable(emptyList())
        }
        assertThrows(BadRequestException::class.java) {
            AppraisalMemberApprovalPolicy.requireSubmittable(listOf(AppraisalMemberVo(memberName = "外部专家")))
        }
    }
}
