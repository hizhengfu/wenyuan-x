package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files as StoredFiles
import io.hizhengfu.cloud.sys.file.service.FileService
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipFile

class CollectionExchangeServiceTest {
    @TempDir lateinit var root:Path
    private val photo=byteArrayOf(1,2,3,4,5)
    private val query=CollectionQueryVo()
    private fun fixture(block:(CollectionExchangeService,FileReadAccess)->Unit) {
        Files.write(root.resolve("front.jpg"),photo)
        val db=Database.connect("jdbc:sqlite:${root.resolve("exchange.db")}",driver="org.sqlite.JDBC")
        try { transaction(db) {
            SchemaUtils.create(CollectionOrgs,CollectionCatalogs,CollectionCatalogVersions,StoredFiles,CollectionCategories)
            CollectionCategoryDao.new("cat") { name="瓷器";code="03";parentId="0" }
            CollectionOrgDao.new("org") { code="M";name="本馆";exchangeCode="11010221800001";exchangeCodeBasis="原赋码通知" }
            FileDao.new("front") { name="原始.jpg";path="front.jpg";ext="jpg";hash="legacy";size=photo.size.toLong() }
            val c=CollectionVo(id="c",orgId="org",totalRegNo="REG-1",classNo="C-1",name="=青花对瓶",categoryId="cat",era="清",texture="瓷",level=EnumRelicLevel.THIRD,
                quantity=1,unit="套",actualQuantity=2,actualQuantityUnit="件",source=EnumSource.OLD,acquireDate=LocalDate.of(2020,1,1),entryDate=LocalDate.of(2020,1,2),
                registeredAt=LocalDateTime.of(2020,1,3,12,0),catalogStatus=EnumCatalogStatus.APPROVED,lifeStatus=EnumCollectionStatus.IN_STORE,
                dimensions=DimensionVo(height="125",extra=mapOf("厚" to "5")),dimensionUnit="mm",weight="350.5",weightUnit="g",completeStatus="完整",completeness=EnumCompleteness.COMPLETE,
                preserveState=EnumPreserve.entries.first(),feature="青花纹饰",warehouseId="store",coverImage="front",
                registrationDetails=CollectionRegistrationDetailsVo(coverIsFrontView=true,weightExcludesAccessories=true))
            val content=CollectionCatalogMapper.fromCollection(c).copy(colophon="题跋仅在编目中留存")
            CollectionCatalogDao.new("catalog") { orgId="org";collectionId="c";catalogStatus=EnumCatalogStatus.APPROVED;version=1;this.content=content }
            CollectionCatalogVersionDao.new("version") { orgId="org";collectionId="c";catalogId="catalog";catalogStatus=EnumCatalogStatus.APPROVED;version=1;this.content=content }
            val collections=mock(CollectionService::class.java)
            `when`(collections.exchangeCollections(query)).thenReturn(listOf(c))
            val media=mock(CollectionFileService::class.java)
            `when`(media.listMedia("c")).thenReturn(listOf(CollectionMediaVo(id="media",collectionId="c",orgId="org",fileId="front",mediaType=EnumCollectionMediaType.FRONT,
                capture=CollectionMediaCaptureVo(EnumCollectionPhotoView.A,1,institution="本馆",location="摄影室",photographedOn=LocalDate.of(2020,1,2),photographer="摄影人"))))
            val files=mock(FileService::class.java)
            `when`(files.resolveFileId("front")).thenReturn("front")
            `when`(files.fileObjectStorage).thenReturn(LocalFileObjectStorage(root.toString()))
            val access=mock(FileReadAccess::class.java)
            `when`(access.readEvidence("front.jpg","LOCAL")).thenReturn(FileReadAccess.ReadEvidence(FileReadAccess.ReadLocation("front.jpg",true),FileContentBaseline.read(photo.inputStream()),null))
            TransactionManager.current().entityCache.flush()
            block(CollectionExchangeService(collections,media,files,access),access)
        } } finally { TransactionManager.closeAndUnregister(db) }
    }
    @Test fun `package keeps original photo bytes complete catalog and independent quantities`()=fixture { service,_ ->
        assertTrue(service.check(query).valid)
        val result=service.build(query)
        result.use { packageFile -> ZipFile(packageFile.path.toFile()).use { zip ->
            assertArrayEquals(photo,zip.getInputStream(zip.getEntry("photos/11010221800001-REG-1-A-1.jpg")).readBytes())
            val json=JsonUtils.mapper.readTree(zip.getInputStream(zip.getEntry("collections.json")).readAllBytes())
            assertEquals(1,json[0]["data"]["quantity"].asInt());assertEquals(2,json[0]["data"]["actualQuantity"].asInt())
            assertEquals("题跋仅在编目中留存",json[0]["catalog"]["colophon"].asString())
            assertEquals(FileContentBaseline.read(photo.inputStream()).sha256,json[0]["photos"][0]["sha256"].asString())
            val csv=zip.getInputStream(zip.getEntry("collections.csv")).bufferedReader().readText()
            assertTrue(csv.contains("厚：5"));assertTrue(csv.contains("0.351 kg"));assertTrue(csv.contains("'=青花对瓶"))
        } }
        assertFalse(Files.exists(result.path))
    }
    @Test fun `missing assigned code is reported and cannot be replaced with internal code`()=fixture { service,_ ->
        CollectionOrgDao["org"].exchangeCode=null
        val check=service.check(query)
        assertFalse(check.valid);assertTrue(check.items.single().issues.any { it.contains("14位") })
        assertThrows(BadRequestException::class.java) { service.build(query) }
    }
    @Test fun `read access or original baseline changes stop download before any package is returned`()=fixture { service,access ->
        `when`(access.readEvidence("front.jpg","LOCAL")).thenReturn(FileReadAccess.ReadEvidence(FileReadAccess.ReadLocation("front.jpg",true),FileContentBaseline.read("different".byteInputStream()),null))
        assertThrows(BadRequestException::class.java) { service.build(query) }
    }
}
