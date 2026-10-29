package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.AppraisalVo
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppraisalRecordValuesTest {
    @Test
    fun `preliminary appraisal may omit suggested grade without bypassing required object and conclusion`() {
        jakarta.validation.Validation.buildDefaultValidatorFactory().use { factory ->
            val value=AppraisalVo(collectionId="one",resultName="陶器",conclusion="初步鉴选意见，行政级别另行办理")
            assertEquals(emptySet<jakarta.validation.ConstraintViolation<AppraisalVo>>(),factory.validator.validate(value))
            assertEquals(null,AppraisalRecordValues.fromApply(value).resultLevel)
            org.junit.jupiter.api.Assertions.assertTrue(factory.validator.validate(value.copy(collectionId=null)).isNotEmpty())
        }
    }

    @Test
    fun `apply values keep appraisal conclusion and attachments`() {
        val values = AppraisalRecordValues.fromApply(
            AppraisalVo(
                resultName = "青花瓷盘",
                resultEra = "明代",
                resultTexture = "PORCELAIN",
                resultLevel = EnumRelicLevel.SECOND,
                committee = "张三、李四、王五",
                conclusion = "定为二级文物",
                attachmentIds = "opinion-1,sign-2",
            ),
        )

        assertEquals("青花瓷盘", values.resultName)
        assertEquals("明代", values.resultEra)
        assertEquals("PORCELAIN", values.resultTexture)
        assertEquals(EnumRelicLevel.SECOND, values.resultLevel)
        assertEquals("张三、李四、王五", values.committee)
        assertEquals("定为二级文物", values.conclusion)
        assertEquals("opinion-1,sign-2", values.attachmentIds)
    }

}
