package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.collection.service.CollectionFileImportNameParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollectionMediaStandardTest {
    @Test fun `fullwidth registration punctuation round trips without renumbering`() {
        for (number in listOf("1：2", "1，2", "1／2", "1＼2", "1＊2", "1？2", "1（2）", "1［2］", "1｜2", "58-31.1234")) {
            val name=CollectionMediaStandard.fileName("11010221800001",number,CollectionMediaCaptureVo(view=EnumCollectionPhotoView.A,sequence=1),"jpg")
            assertEquals(number,CollectionMediaStandard.parse(name)?.totalRegNo)
            assertEquals(number,CollectionFileImportNameParser.totalRegNo(null,name))
        }
        for (number in listOf("1:2","1,2","1(2)","1\\2","1\n2","1\u30002")) {
            assertThrows(BadRequestException::class.java) { CollectionMediaStandard.fileName("11010221800001",number,CollectionMediaCaptureVo(view=EnumCollectionPhotoView.A,sequence=1),"jpg") }
            assertNull(CollectionMediaStandard.parse("11010221800001-$number-A-1.jpg"))
        }
    }
    @Test fun `standard photo filename preserves hyphenated registration number and normalizes extension`() {
        val name="11010221800001-58-31.1234-A-001.JPG"
        val parsed=CollectionMediaStandard.parse(name)!!
        assertEquals("58-31.1234",parsed.totalRegNo)
        assertEquals(EnumCollectionPhotoView.A,parsed.view)
        assertEquals(1,parsed.sequence)
        assertEquals("58-31.1234",CollectionFileImportNameParser.totalRegNo(null,name))
        assertEquals(EnumCollectionMediaType.FRONT,CollectionFileImportNameParser.mediaType(name))
        assertEquals("11010221800001-58-31.1234-A-1.jpg",CollectionMediaStandard.fileName(parsed.institutionCode,parsed.totalRegNo,CollectionMediaCaptureVo(view=parsed.view,sequence=parsed.sequence),parsed.extension))
    }
    @Test fun `legacy filenames remain supported and bottom view never means back`() {
        assertEquals("REG-1",CollectionFileImportNameParser.totalRegNo(null,"REG-1__正面__1.jpg"))
        assertEquals(EnumCollectionMediaType.FRONT,CollectionFileImportNameParser.mediaType("REG-1__正面__1.jpg"))
        assertEquals(EnumCollectionMediaType.BOTTOM,CollectionFileImportNameParser.mediaType("11010221800001-1-F-1.jpg"))
        assertThrows(BadRequestException::class.java) {CollectionMediaStandard.validate(CollectionMediaCaptureVo(view=EnumCollectionPhotoView.F,sequence=1),EnumCollectionMediaType.BACK)}
    }
    @Test fun `invalid codes identity paths and missing capture are rejected for exchange`() {
        val capture=CollectionMediaCaptureVo(view=EnumCollectionPhotoView.A,sequence=1)
        assertThrows(BadRequestException::class.java) {CollectionMediaStandard.fileName("DEFAULT","1",capture,"jpg")}
        for(no in listOf("藏1","1/2","1 2","")) assertThrows(BadRequestException::class.java) {CollectionMediaStandard.fileName("11010221800001",no,capture,"jpg")}
        assertEquals(6,CollectionMediaStandard.missing(null).size)
        assertNull(CollectionMediaStandard.parse("11010221800001-1-A-0.jpg"))
        assertNull(CollectionMediaStandard.parse("11010221800001-1-A-99999999999999.jpg"))
    }
}
