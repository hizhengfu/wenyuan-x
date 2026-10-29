package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionImagePresenceTest {

    @Test
    fun `cover image counts as image even without media rows`() {
        assertTrue(CollectionImagePresence.hasImage("cover-file-1", 0))
        assertEquals(1, CollectionImagePresence.displayCount("cover-file-1", 0))
    }

    @Test
    fun `media rows keep their real count without double counting cover image`() {
        assertTrue(CollectionImagePresence.hasImage("cover-file-1", 3))
        assertEquals(3, CollectionImagePresence.displayCount("cover-file-1", 3))
    }

    @Test
    fun `blank cover and no media rows means no image`() {
        assertFalse(CollectionImagePresence.hasImage("  ", 0))
        assertEquals(0, CollectionImagePresence.displayCount(null, 0))
    }
}
