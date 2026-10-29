package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.ExhibitionVo
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumProcessState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ExhibitionValuesTest {

    @Test
    fun `exhibition values keep project fields and evidence`() {
        val startDate = LocalDate.of(2026, 7, 1)
        val endDate = LocalDate.of(2026, 8, 15)
        val values = ExhibitionRecordValues.from(
            ExhibitionVo(
                title = "瓷器精品展",
                exhibitionNo = "EX-2026-001",
                venueId = "hall-1",
                venue = "一号展厅",
                organizer = "陈列部",
                collectionIds = "c1,c2",
                coverImage = "cover-file",
                attachmentIds = "plan-file,approval-file",
                startDate = startDate,
                endDate = endDate,
                state = EnumProcessState.ONGOING,
                remark = "暑期专题展",
            ),
        )

        assertEquals("瓷器精品展", values.title)
        assertEquals("EX-2026-001", values.exhibitionNo)
        assertEquals("hall-1", values.venueId)
        assertEquals("一号展厅", values.venue)
        assertEquals("陈列部", values.organizer)
        assertEquals("c1,c2", values.collectionIds)
        assertEquals("cover-file", values.coverImage)
        assertEquals("plan-file,approval-file", values.attachmentIds)
        assertEquals(startDate, values.startDate)
        assertEquals(endDate, values.endDate)
        assertEquals(EnumProcessState.ONGOING, values.state)
        assertEquals("暑期专题展", values.remark)
    }

    @Test
    fun `approved exhibition can derive a linked requisition draft`() {
        val endDate = LocalDate.of(2026, 8, 15)
        val values = RequisitionRecordValues.fromExhibition(
            ExhibitionVo(
                id = "ex-1",
                orgId = "org-1",
                title = "瓷器精品展",
                exhibitionNo = "EX-2026-001",
                venue = "一号展厅",
                organizer = "陈列部",
                collectionIds = "c1,c2",
                attachmentIds = "plan-file",
                endDate = endDate,
            ),
            operator = "initiator-1",
        )

        assertEquals(CollectionConst.BIZ_EXHIBITION, values.sourceBizType)
        assertEquals("ex-1", values.sourceBizId)
        assertEquals("c1,c2", values.collectionIds)
        assertEquals(EnumMoveReason.EXHIBIT, values.purpose)
        assertEquals("展览：瓷器精品展（EX-2026-001）", values.basis)
        assertEquals("陈列部", values.department)
        assertEquals("initiator-1", values.operator)
        assertEquals("plan-file", values.attachmentIds)
        assertEquals(endDate, values.expectReturnDate)
    }

    @Test
    fun `exhibition values default to draft when state omitted`() {
        val values = ExhibitionRecordValues.from(ExhibitionVo(title = "临时展"))

        assertEquals("临时展", values.title)
        assertEquals(EnumProcessState.DRAFT, values.state)
        assertNull(values.venue)
    }

    @Test
    fun `exhibition filters parse query conditions`() {
        val filters = ExhibitionQueryFilters.from { key ->
            mapOf(
                "keyword" to "瓷器",
                "title" to "精品展",
                "venueId" to "hall-1",
                "venue" to "一号展厅",
                "organizer" to "陈列部",
                "state" to "ONGOING",
                "hasAttachment" to "true",
                "hasImage" to "false",
                "startDateStart" to "2026-07-01",
                "startDateEnd" to "2026-07-31",
                "endDateStart" to "2026-08-01",
                "endDateEnd" to "2026-08-31",
            )[key]
        }

        assertEquals("瓷器", filters.keyword)
        assertEquals("精品展", filters.title)
        assertEquals("hall-1", filters.venueId)
        assertEquals("一号展厅", filters.venue)
        assertEquals("陈列部", filters.organizer)
        assertEquals(EnumProcessState.ONGOING, filters.state)
        assertTrue(filters.hasAttachment!!)
        assertEquals(false, filters.hasImage)
        assertEquals(LocalDate.of(2026, 7, 1), filters.startDateStart)
        assertEquals(LocalDate.of(2026, 7, 31), filters.startDateEnd)
        assertEquals(LocalDate.of(2026, 8, 1), filters.endDateStart)
        assertEquals(LocalDate.of(2026, 8, 31), filters.endDateEnd)
    }

    @Test
    fun `exhibition filters ignore invalid enum and dates`() {
        val filters = ExhibitionQueryFilters.from { key ->
            mapOf(
                "state" to "BAD",
                "hasAttachment" to "yes",
                "startDateStart" to "2026/07/01",
            )[key]
        }

        assertNull(filters.state)
        assertNull(filters.hasAttachment)
        assertNull(filters.startDateStart)
    }
}
