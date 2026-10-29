package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.file.service.FileService
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class OtherImportNamesRuntimeTest {
    private fun fixture(body: (CollectionScopeResolver, BusinessAttachmentBinder, FileService) -> Unit) {
        val db = Database.connect("jdbc:sqlite:file:other-import-${System.nanoTime()}?mode=memory&cache=shared", driver="org.sqlite.JDBC")
        StaticApplicationContext().use { context ->
            context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            context.refresh(); SpringContextHolder().setApplicationContext(context)
            try { transaction(db) {
                originalReaderSchema()
                SchemaUtils.create(CollectionOrgCodes, CollectionOrgCodeChanges, CollectionOrgs, Collections, CollectionCategories, Warehouses, Areas, Files)
                originalReader("reader", CollectionConst.LEDGER_CREATE)
                actorRole("reader", "IMPORT", setOf(CollectionConst.SELECT, CollectionConst.CROSS_ORG, CollectionConst.STORAGE, CollectionConst.ACQUIRE))
                val scope = CollectionScopeResolver("multi", "museum")
                val user = CollectionActorDirectory(scope).requireActive("reader")
                SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
                listOf("museum", "other").forEach { org ->
                    CollectionOrgDao.new(org) { name = org; code = org; parentId="0"; parentIds="0,"; systemGroupId=org }
                    CollectionCategoryDao.new("category-$org") { orgId=if(org=="museum") null else org; name="瓷器"; code="03"; parentId="0"; parentIds="0," }
                    WarehouseDao.new("store-$org") { orgId=org; name="一号库房"; code="W-1"; parentId="0"; parentIds="0," }
                    WarehouseDao.new("cabinet-$org") { orgId=org; name="一号柜"; code="001"; parentId="store-$org"; parentIds="0,store-$org,"; type=EnumWarehouseType.CABINET }
                    CollectionDao.new("item-$org") { orgId=org; name="同名藏品"; totalRegNo="000001"; categoryId="category-$org" }
                }
                AreaDao.new("area") { name="东城区"; code="110101"; parentId="0"; parentIds="0," }
                FileDao.new("file") { name="意见.pdf"; path="proof.pdf"; hash="hash"; size=10; ext="pdf" }
                val files=mock(FileService::class.java)
                `when`(files.resolveFileId(anyString())).thenAnswer { it.getArgument<String>(0) }
                `when`(files.readableMetadata(anyList())).thenReturn(emptyList())
                body(scope, BusinessAttachmentBinder(files), files)
            } } finally { SecurityContextHolder.clearContext() }
        }
    }
    private fun acquisition(scope: CollectionScopeResolver, binder: BusinessAttachmentBinder) = AcquisitionService(
        mock(FlowInstanceService::class.java), scope, binder, mock(AcquisitionComplianceService::class.java), mock(CollectionRetainedLedgerService::class.java))
    private fun inventory(scope: CollectionScopeResolver, binder: BusinessAttachmentBinder) = InventoryService(
        mock(FlowInstanceService::class.java), scope, mock(CollectionService::class.java), binder,
        mock(InventoryBatchService::class.java), mock(CollectionOperationLockService::class.java), mock(InventoryComplianceService::class.java))

    @Test fun `acquisition resolves current category and all attachment columns with readable files`() = fixture { scope, binder, files ->
        val row=AcquisitionImportRowVo(rowNo=4, name="拟征集", unit="件", source="捐赠", acquireDate=java.time.LocalDate.of(2020,1,1), holder="捐赠人", reason="符合征集方向", categoryName="瓷器", imageNames="意见.pdf", expertOpinionAttachmentNames="意见.pdf",
            approvalAttachmentNames="意见.pdf", contractAttachmentNames="意见.pdf", invoiceAttachmentNames="意见.pdf")
        val result=acquisition(scope,binder).validateImport(listOf(row))
        assertTrue(result.valid,result.errors.toString())
        val item=result.items.single()
        assertEquals("category-museum",item.categoryId)
        assertEquals(listOf("file","file","file","file","file"),listOf(item.images,item.expertOpinionAttachmentIds,item.approvalAttachmentIds,item.contractAttachmentIds,item.invoiceAttachmentIds))
        verify(files, times(5)).readableMetadata(listOf("file"))
        CollectionCategoryDao["category-museum"].status=EnumContentStatus.DELETE
        assertFalse(acquisition(scope,binder).validateImport(listOf(row)).valid)
        assertFalse(acquisition(scope,binder).validateImport(listOf(row.copy(categoryName=null,categoryId="category-other"))).valid)
    }
    @Test fun `attachment duplicate unreadable and conflicting references report row errors`() = fixture { scope, binder, files ->
        val service=acquisition(scope,binder)
        val row=AcquisitionImportRowVo(rowNo=7,name="拟征集", unit="件", source="捐赠", acquireDate=java.time.LocalDate.of(2020,1,1), holder="捐赠人", reason="符合征集方向",imageNames="意见.pdf")
        doThrow(BadRequestException("权限撤销")).`when`(files).readableMetadata(listOf("file"))
        val denied=service.validateImport(listOf(row))
        assertFalse(denied.valid);assertEquals(7,denied.errors.single().rowNo);assertEquals("imageNames",denied.errors.single().field)
        reset(files)
        `when`(files.resolveFileId(anyString())).thenAnswer { it.getArgument<String>(0) }
        val conflict=service.validateImport(listOf(row.copy(images="other-file")))
        assertFalse(conflict.valid);assertTrue(conflict.errors.single().message.contains("不一致"))
        FileDao.new("duplicate") { name="意见.pdf";path="other.pdf";hash="other";size=10;ext="pdf" }
        assertTrue(service.validateImport(listOf(row)).errors.single().message.contains("重名"))
    }
    @Test fun `multiple named files use separators preserve roles and reject missing files`() = fixture { scope, binder, _ ->
        FileDao.new("photo") { name="照片.png";path="photo.png";hash="photo";size=10;ext="png" }
        val row=AcquisitionImportRowVo(name="拟征集",unit="件",source="捐赠",acquireDate=java.time.LocalDate.of(2020,1,1),holder="捐赠人",reason="征集依据",
            imageNames="意见.pdf；照片.png", expertOpinionAttachmentNames="意见.pdf", images="photo,file")
        val result=acquisition(scope,binder).validateImport(listOf(row))
        assertTrue(result.valid,result.errors.toString())
        assertEquals("file,photo",result.items.single().images)
        assertEquals("file",result.items.single().expertOpinionAttachmentIds)
        assertTrue(acquisition(scope,binder).validateImport(listOf(row.copy(imageNames="未上传.png"))).errors.any { it.field=="imageNames" })
    }

    @Test fun `retired movement import rejects template validation and submission consistently`() {
        val service=StorageMovementService(mock(CollectionScopeResolver::class.java),mock(BusinessAttachmentBinder::class.java),mock(CollectionOperationLockService::class.java))
        assertThrows(BadRequestException::class.java) { service.importTemplate() }
        assertThrows(BadRequestException::class.java) { service.validateImport(emptyList()) }
        assertThrows(BadRequestException::class.java) { service.commitImport(emptyList()) }
    }

    @Test fun `warehouse path code and thumbnail name resolve within current museum`() = fixture { scope, binder, _ ->
        val service=WarehouseService(scope,binder)
        val row=WarehouseImportRowVo(name="新库位",code="1.1.1",parentName="一号库房/一号柜",thumbnailName="意见.pdf",type="层格")
        val result=service.validateImport(listOf(row))
        assertTrue(result.valid,result.errors.toString());assertEquals("cabinet-museum",result.items.single().parentId);assertEquals("file",result.items.single().thumbnail)
        assertFalse(service.validateImport(listOf(row.copy(parentCode="W-1"))).valid)
        assertFalse(service.validateImport(listOf(row.copy(parentName=null,parentId="cabinet-other"))).valid)
        assertTrue(service.validateImport(listOf(row.copy(parentName=null,parentId="cabinet-museum",thumbnailName=null,thumbnail="old-path"))).valid)
    }
    @Test fun `organization supports parent names location paths and area names or codes`() = fixture { scope, _, _ ->
        val service=CollectionOrgService(scope)
        val row=CollectionOrgImportRowVo(code="BRANCH",name="新分馆",parentName="museum",areaCode="110101",locationRootName="一号库房/一号柜")
        // Two visible museums have identical location paths: fail instead of selecting one.
        assertFalse(service.validateImport(listOf(row)).valid)
        WarehouseDao["store-other"].name="外馆库房"
        val result=service.validateImport(listOf(row))
        assertTrue(result.valid,result.errors.toString())
        assertEquals("museum",result.items.single().parentId);assertEquals("area",result.items.single().areaId);assertEquals("cabinet-museum",result.items.single().locationRootId)
        assertEquals("area",service.validateImport(listOf(row.copy(areaCode=null,areaName="东城区"))).items.single().areaId)
        assertFalse(service.validateImport(listOf(row.copy(areaCode="999999"))).valid)
        assertFalse(service.validateImport(listOf(row.copy(areaName="不存在区划"))).valid)
        AreaDao.new("area-duplicate") { name="东城区";code="999999";parentId="0";parentIds="0," }
        assertFalse(service.validateImport(listOf(row.copy(areaCode=null,areaName="东城区"))).valid)
        AreaDao["area"].enabled=false
        assertFalse(service.validateImport(listOf(row)).valid)
        AreaDao["area"].enabled=true
        assertTrue(service.validateImport(listOf(row.copy(parentName=null,parentId="museum",areaCode=null,areaId="area",locationRootName=null,locationRootId="cabinet-museum"))).valid)
    }
    @Test fun `inventory with two visible museums uses only current registration number and checks attachments`() = fixture { scope, binder, _ ->
        val service=inventory(scope,binder)
        val row=InventoryImportRowVo(batchName="盘点",totalRegNo="000001",result="账物相符",attachmentNames="意见.pdf")
        val result=service.validateImport(listOf(row))
        assertTrue(result.valid,result.errors.toString());assertEquals("item-museum",result.items.single().collectionId);assertEquals("file",result.items.single().attachmentIds)
        assertFalse(service.validateImport(listOf(row.copy(collectionId="item-other",totalRegNo=null))).valid)
        assertFalse(service.validateImport(listOf(row.copy(collectionId="item-museum",totalRegNo="wrong"))).valid)
        val unique = Collections.indices.first { it.unique && it.columns.toSet() == setOf(Collections.orgId, Collections.totalRegNo) }
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("DROP INDEX "+unique.indexName)
        CollectionDao.new("duplicate-item") { orgId="museum";name="同号";totalRegNo="000001";categoryId="category-museum" }
        assertThrows(BadRequestException::class.java) { service.validateImport(listOf(row)) }
    }
}
