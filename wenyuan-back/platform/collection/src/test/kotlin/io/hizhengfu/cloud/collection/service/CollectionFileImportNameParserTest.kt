package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumCollectionAttachmentType
import io.hizhengfu.cloud.collection.domain.EnumCollectionMediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionFileImportNameParserTest {

    @Test
    fun `parses registration number and chinese media type from survey image name`() {
        val fileName = "000123__正面__01.jpg"

        assertEquals("000123", CollectionFileImportNameParser.totalRegNo(null, fileName))
        assertEquals(EnumCollectionMediaType.FRONT, CollectionFileImportNameParser.mediaType(fileName))
    }

    @Test
    fun `explicit registration number wins over file name and enum token is accepted`() {
        val fileName = "legacy-name__THREE_D__02.glb"

        assertEquals("馆藏-009", CollectionFileImportNameParser.totalRegNo(" 馆藏-009 ", fileName))
        assertEquals(EnumCollectionMediaType.THREE_D, CollectionFileImportNameParser.mediaType(fileName))
    }

    @Test
    fun `attachment type is derived from chinese token`() {
        assertEquals(
            EnumCollectionAttachmentType.APPRAISAL_OPINION,
            CollectionFileImportNameParser.attachmentType("000123__鉴定意见__01.pdf"),
        )
    }

    @Test
    fun `plain file stem is accepted as registration number`() {
        assertEquals("000123", CollectionFileImportNameParser.totalRegNo(null, "folder/000123.jpg"))
        assertEquals(EnumCollectionMediaType.OTHER, CollectionFileImportNameParser.mediaType("000123.jpg"))
    }
}
