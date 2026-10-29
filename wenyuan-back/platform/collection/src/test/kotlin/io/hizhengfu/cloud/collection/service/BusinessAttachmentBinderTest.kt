package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BusinessAttachmentBinderTest {

    @Test
    fun `binding planner trims splits and de duplicates attachment ids`() {
        val items = BusinessAttachmentBindingPlanner.plan(
            contentId = "movement-1",
            aliasPrefix = "出入库单附件",
            attachmentIds = " file-1, file-2 ,, file-1 ",
        )

        assertEquals(
            listOf(
                BusinessAttachmentBindingItem("file-1", "movement-1", "出入库单附件 1"),
                BusinessAttachmentBindingItem("file-2", "movement-1", "出入库单附件 2"),
            ),
            items,
        )
    }

    @Test
    fun `binding planner accepts common import separators`() {
        val items = BusinessAttachmentBindingPlanner.plan(
            contentId = "appraisal-1",
            aliasPrefix = "鉴定材料附件",
            attachmentIds = "opinion-1，meeting-2; photo-3\nopinion-1",
        )

        assertEquals(
            listOf(
                BusinessAttachmentBindingItem("opinion-1", "appraisal-1", "鉴定材料附件 1"),
                BusinessAttachmentBindingItem("meeting-2", "appraisal-1", "鉴定材料附件 2"),
                BusinessAttachmentBindingItem("photo-3", "appraisal-1", "鉴定材料附件 3"),
            ),
            items,
        )
    }

    @Test
    fun `single binding planner keeps exact alias name`() {
        val items = BusinessAttachmentBindingPlanner.planSingle(
            contentId = "collection-1",
            aliasName = "藏品封面影像",
            fileId = " cover-1 ",
        )

        assertEquals(
            listOf(BusinessAttachmentBindingItem("cover-1", "collection-1", "藏品封面影像")),
            items,
        )
        assertEquals(
            emptyList<BusinessAttachmentBindingItem>(),
            BusinessAttachmentBindingPlanner.planSingle("collection-1", "藏品封面影像", " "),
        )
    }
}
