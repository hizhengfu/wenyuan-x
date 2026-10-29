package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.nio.file.Path

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
class CollectionPrintCurrentReadRuntimeTest {
    @TempDir lateinit var dir:Path
    private fun fixture(body:(CollectionPrintService)->Unit) {
        val db=Database.connect("jdbc:sqlite:${dir.resolve("print.db")}","org.sqlite.JDBC")
        val stale=SecurityUser(id="reader",name="旧登录",username="reader",password="",groupId="museum",groupIds=setOf("museum"),
            postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf("ADMIN",CollectionConst.SELECT,CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(stale,null,stale.authorities)
        try {transaction(db) {
            originalReaderSchema(); originalReader("reader",CollectionConst.SELECT)
            SchemaUtils.create(CollectionOrgs,Collections,CollectionCategories,Warehouses,CollectionMedias,CollectionAttachments,
                StorageMovements,Appraisals,AppraisalMembers,Reproductions,Requisitions,Loans,LoanComplianceRecords,Deregistrations,
                DeregistrationExpertOpinions,DeregistrationNotices,CollectionAdministrativeRecords,Inventories,Exhibitions,
                Acquisitions,EntryVouchers,AccessionReceipts,CollectionTransfers,CollectionRetainedLedgers,CollectionPermanentArchives,CollectionPermanentArchiveFiles,io.hizhengfu.cloud.sys.file.dao.Files)
            for(org in listOf("museum","other")) {
                CollectionOrgDao.new(org){name=org;code=org;systemGroupId=org}
                CollectionDao.new("collection-$org"){orgId=org;name="藏品-$org";totalRegNo="REG-$org"}
                StorageMovementDao.new("move-$org"){orgId=org;type=EnumMoveType.IN;collectionId="collection-$org"}
                AppraisalDao.new("appraisal-$org"){orgId=org;collectionId="collection-$org"}
                ReproductionDao.new("reproduction-$org"){orgId=org;collectionId="collection-$org"}
                RequisitionDao.new("requisition-$org"){orgId=org;collectionIds="collection-$org"}
                LoanDao.new("loan-$org"){orgId=org;collectionIds="collection-$org"}
                DeregistrationDao.new("deregistration-$org"){orgId=org;collectionId="collection-$org"}
                InventoryDao.new("inventory-$org"){orgId=org;collectionId="collection-$org"}
                ExhibitionDao.new("exhibition-$org"){title="展览-$org";orgId=org;collectionIds="collection-$org"}
                AcquisitionDao.new("acquisition-$org"){orgId=org;name="征集-$org";source=io.hizhengfu.cloud.collection.domain.EnumSource.PURCHASE}
                EntryVoucherDao.new("voucher-$org"){orgId=org;voucherNo="RJ-$org";acquisitionIds="acquisition-$org"}
                CollectionTransferDao.new("transfer-$org"){orgId=org;fromOrgId=org;toOrgId="third";collectionId="collection-$org"}
            }
            val templates=mock(CollectionPrintTemplateService::class.java) { invocation ->
                if(invocation.method.name=="resolve") CollectionPrintTemplateRegistry.resolve(invocation.getArgument(0)) else RETURNS_DEFAULTS.answer(invocation)
            }
            body(CollectionPrintService(mock(CollectionService::class.java),CollectionScopeResolver("multi",""),templates,mock(LoanStatutoryService::class.java),archives=CollectionPermanentArchiveService(mock(BusinessAttachmentBinder::class.java),CollectionScopeResolver("multi",""))))
        }}finally{SecurityContextHolder.clearContext()}
    }
    private fun key(type:EnumCollectionPrintType,org:String="museum")=when(type) {
        EnumCollectionPrintType.COLLECTION_ARCHIVE -> "collection"
        EnumCollectionPrintType.MOVEMENT,EnumCollectionPrintType.MOUNTING -> "move"
        EnumCollectionPrintType.LOAN_HANDOVER -> "loan"
        EnumCollectionPrintType.ENTRY_VOUCHER -> "voucher"
        else -> type.name.lowercase()
    }+"-$org"

    @Test fun `movement print distinguishes old current identity from persisted event identity`()=fixture { printer ->
        val movement=StorageMovementDao["move-museum"]
        val legacy=printer.movementPrint(movement.id.value)
        assertTrue(legacy.fields.single { it.label=="藏品明细数据来源" }.value!!.contains("旧流水未完整留存"))

        movement.totalRegNoAtMovement="REG-AT-MOVEMENT"
        movement.collectionNameAtMovement="出入库时名称"
        CollectionDao["collection-museum"].apply {totalRegNo="REG-CURRENT";name="现名"}

        val printed=printer.movementPrint(movement.id.value)
        assertEquals("REG-AT-MOVEMENT",printed.collections.single().totalRegNo)
        assertEquals("出入库时名称",printed.collections.single().name)
        assertTrue(printed.fields.single { it.label=="藏品明细数据来源" }.value!!.contains("取本次流水留存值"))
        assertEquals("REG-AT-MOVEMENT",StorageMovementDao[movement.id.value].toModel().totalRegNo)
    }

    @Test fun `ordinary movement cannot borrow a foreign museum location label`()=fixture { printer ->
        WarehouseDao.new("foreign-location"){orgId="other";name="外馆位置"}
        StorageMovementDao["move-museum"].toWarehouseId="foreign-location"
        assertThrows(BadRequestException::class.java){printer.movementPrint("move-museum")}
    }

    @Test fun `receiving museum print can name the source location only for its executed transfer`()=fixture { printer ->
        WarehouseDao.new("source-location"){orgId="museum";name="原馆库房"}
        WarehouseDao.new("target-location"){orgId="other";name="接收馆库房"}
        CollectionTransferDao["transfer-museum"].apply {
            toOrgId="other";executedAt=java.time.LocalDateTime.of(2026,9,26,9,0)
        }
        CollectionDao["collection-museum"].orgId="other"
        StorageMovementDao["move-other"].apply {
            collectionId="collection-museum";reason=EnumMoveReason.TRANSFER;refBizId="transfer-museum"
            fromWarehouseId="source-location";toWarehouseId="target-location"
        }
        UserDao["reader"].groupId="other"
        val printed=printer.movementPrint("move-other")
        val fields=printed.fields.associate {it.label to it.value}
        assertEquals("原馆库房（source-location）",fields["原库位"])
        assertEquals("接收馆库房（target-location）",fields["目标库位"])
        assertEquals("target-location",printed.collections.single().warehouseId)
    }

    @Test fun `receiving museum reprints its archived intake after a later transfer`()=fixture { printer ->
        val transfer=CollectionTransferDao["transfer-museum"].apply {
            toOrgId="other";executedAt=java.time.LocalDateTime.of(2026,9,20,10,0)
        }
        val collection=CollectionDao["collection-museum"]
        val source=collection.toModel()
        val archivedSource=CollectionRetainedLedgerSnapshot(source,
            identity=CollectionRetainedLedgerIdentity("museum",collection.id.value,transfer.id.value,"other"))
        val archiveService=CollectionPermanentArchiveService(mock(BusinessAttachmentBinder::class.java),CollectionScopeResolver("multi",""))
        val retained=CollectionRetainedLedgerDao.new("intake-source") {
            orgId="museum";collectionId=collection.id.value;transferId=transfer.id.value
            totalRegNo=source.totalRegNo!!;name=source.name!!;targetOrgId="other"
            archiveId="";snapshot=archivedSource
        }
        retained.archiveId=archiveService.seal("museum","COLLECTION_RETAINED_LEDGER",retained.id.value,"TRANSFERRED",archivedSource,null)
        collection.apply { orgId="other";name="接收时名称" }
        val intake=collection.toModel()
        val received=archiveService.seal("other",CollectionConst.BIZ_TRANSFER,transfer.id.value,"RECEIVED",
            mapOf("transferId" to transfer.id.value,"collection" to intake,"sourceOriginalArchiveId" to retained.archiveId),null)
        StorageMovementDao["move-other"].apply {
            collectionId=collection.id.value;reason=EnumMoveReason.TRANSFER;refBizId=transfer.id.value
            totalRegNoAtMovement=source.totalRegNo;collectionNameAtMovement=intake.name
        }
        UserDao["reader"].groupId="other"
        collection.name="接收后改名"
        assertEquals("接收时名称",printer.movementPrint("move-other").collections.single().name)
        collection.apply { orgId="third";name="下一馆现名" }
        val print=printer.movementPrint("move-other")
        assertEquals("接收时名称",print.collections.single().name)
        assertEquals("other",print.collections.single().orgId)
        assertEquals(source.totalRegNo,print.collections.single().totalRegNo)
        assertTrue(print.fields.single {it.label=="藏品明细数据来源"}.value!!.contains("接收馆永久档案"))

        StorageMovementDao["move-other"].collectionNameAtMovement="错误的流水身份"
        assertThrows(BadRequestException::class.java){printer.movementPrint("move-other")}
        StorageMovementDao["move-other"].collectionNameAtMovement=intake.name
        val receipt=CollectionPermanentArchiveDao[received]
        val originalReceipt=receipt.snapshot
        receipt.snapshot=originalReceipt.replace(retained.archiveId,"wrong-source-archive")
        receipt.digest=CollectionCareEvidence.digest(receipt.snapshot)
        assertThrows(BadRequestException::class.java){printer.movementPrint("move-other")}
        receipt.snapshot=originalReceipt
        receipt.digest=CollectionCareEvidence.digest(originalReceipt)
        receipt.snapshot="{}"
        assertThrows(BadRequestException::class.java){printer.movementPrint("move-other")}
    }

    @Test fun `source museum reprints its bound departure after the collection returns`()=fixture { printer ->
        actorRole("reader","STORAGE_READER",setOf(CollectionConst.SELECT,CollectionConst.STORAGE,CollectionConst.CROSS_ORG))
        val transfer=CollectionTransferDao["transfer-museum"].apply {
            toOrgId="other";executedAt=java.time.LocalDateTime.of(2026,9,20,10,0)
        }
        val collection=CollectionDao["collection-museum"]
        val original=collection.toModel()
        val snapshot=CollectionRetainedLedgerSnapshot(original,
            identity=CollectionRetainedLedgerIdentity("museum",collection.id.value,transfer.id.value,"other"))
        val archiveService=CollectionPermanentArchiveService(mock(BusinessAttachmentBinder::class.java),CollectionScopeResolver("multi",""))
        val retained=CollectionRetainedLedgerDao.new("outbound-source") {
            orgId="museum";collectionId=collection.id.value;transferId=transfer.id.value
            totalRegNo=original.totalRegNo!!;name=original.name!!;targetOrgId="other"
            archiveId="";this.snapshot=snapshot
        }
        retained.archiveId=archiveService.seal("museum","COLLECTION_RETAINED_LEDGER",retained.id.value,"TRANSFERRED",snapshot,null)
        StorageMovementDao["move-museum"].apply {
            type=EnumMoveType.OUT;reason=EnumMoveReason.TRANSFER;refBizId=transfer.id.value
        }
        collection.apply { orgId="other";name="接收馆改名" }
        collection.apply { orgId="museum";name="再回原馆的新名称" }
        val print=printer.movementPrint("move-museum")
        assertEquals(original.name,print.collections.single().name)
        assertEquals("museum",print.collections.single().orgId)
    }

    @Test fun `former museum prints requisition loan and inventory from the first later sealed departure`()=fixture { printer ->
        actorRole("reader","STORAGE_READER",setOf(CollectionConst.SELECT,CollectionConst.STORAGE,CollectionConst.CROSS_ORG))
        val occurred=java.time.LocalDateTime.of(2026,9,10,10,0)
        val transfer=CollectionTransferDao["transfer-museum"].apply {
            toOrgId="other";executedAt=occurred.plusDays(10)
        }
        val collection=CollectionDao["collection-museum"]
        val requisition=RequisitionDao["requisition-museum"].apply {
            outExecuted=true;outExecutedTime=occurred
        }
        val loan=LoanDao["loan-museum"].apply {
            outExecuted=true;outExecutedTime=occurred.plusDays(1)
        }
        InventoryDao["inventory-museum"].checkedAt=occurred.plusDays(2)
        StorageMovementDao["move-museum"].apply {
            type=EnumMoveType.OUT;refBizId=requisition.id.value;operatedAt=occurred
            collectionNameAtMovement="提用时名称";totalRegNoAtMovement="REG-museum"
        }
        StorageMovementDao.new("loan-out") {
            orgId="museum";collectionId=collection.id.value;type=EnumMoveType.OUT
            refBizId=loan.id.value;operatedAt=occurred.plusDays(1)
            collectionNameAtMovement="借用时名称";totalRegNoAtMovement="REG-museum"
        }
        val archiveService=CollectionPermanentArchiveService(mock(BusinessAttachmentBinder::class.java),CollectionScopeResolver("multi",""))
        archiveService.seal("museum",CollectionConst.BIZ_REQUISITION,requisition.id.value,"OUT_EXECUTED",mapOf("request" to requisition.toModel()),null)
        val loanArchiveId=archiveService.seal("museum",CollectionConst.BIZ_LOAN,loan.id.value,"OUT_EXECUTED",mapOf("request" to loan.toModel()),null)
        collection.name="首次转馆时名称"
        val original=collection.toModel()
        val snapshot=CollectionRetainedLedgerSnapshot(original,
            identity=CollectionRetainedLedgerIdentity("museum",collection.id.value,transfer.id.value,"other"))
        val retained=CollectionRetainedLedgerDao.new("historic-print-ledger") {
            orgId="museum";collectionId=collection.id.value;transferId=transfer.id.value
            totalRegNo=original.totalRegNo!!;name=original.name!!;targetOrgId="other"
            archiveId="";this.snapshot=snapshot
        }
        retained.archiveId=archiveService.seal("museum","COLLECTION_RETAINED_LEDGER",retained.id.value,"TRANSFERRED",snapshot,null)
        collection.apply { orgId="other";name="接收馆现名" }

        val reqPrint=printer.requisitionPrint(requisition.id.value)
        assertEquals("提用时名称",reqPrint.collections.single().name)
        assertTrue(reqPrint.fields.any { it.label=="藏品明细数据来源" && it.value!!.contains("随后转馆") })
        val loanPrint=printer.loanPrint(loan.id.value)
        assertEquals("借用时名称",loanPrint.collections.single().name)
        val inventoryPrint=printer.inventoryPrint("inventory-museum")
        assertEquals("首次转馆时名称",inventoryPrint.collections.single().name)
        assertEquals(null,inventoryPrint.collections.single().warehouseId)
        assertTrue(inventoryPrint.fields.any { it.label=="藏品明细数据来源" && it.value!!.contains("不代表本业务发生时状态") })

        StorageMovementDao["move-museum"].collectionNameAtMovement=null
        val legacy=printer.requisitionPrint(requisition.id.value)
        assertEquals("首次转馆时名称",legacy.collections.single().name)
        assertTrue(legacy.fields.any { it.label=="藏品明细数据来源" && it.value!!.contains("不能认定为本单发生时值") })
        StorageMovementDao["move-museum"].collectionNameAtMovement="提用时名称"
        requisition.outExecutedTime=occurred.plusMinutes(1)
        assertThrows(BadRequestException::class.java){printer.requisitionPrint(requisition.id.value)}
        requisition.outExecutedTime=occurred
        val earlierDeparture=CollectionTransferDao.new("earlier-departure") {
            this.orgId="museum";collectionId=collection.id.value;fromOrgId="museum";toOrgId="other"
            executedAt=occurred.minusDays(1)
        }
        assertThrows(BadRequestException::class.java){printer.requisitionPrint(requisition.id.value)}
        earlierDeparture.delete()
        val loanArchive=CollectionPermanentArchiveDao[loanArchiveId]
        val originalLoanArchive=loanArchive.snapshot
        loanArchive.snapshot=originalLoanArchive.replace("collection-museum","collection-other")
        loanArchive.digest=CollectionCareEvidence.digest(loanArchive.snapshot)
        assertThrows(BadRequestException::class.java){printer.loanPrint(loan.id.value)}
        loanArchive.snapshot=originalLoanArchive
        loanArchive.digest=CollectionCareEvidence.digest(originalLoanArchive)
        retained.snapshot=snapshot.copy(collection=original.copy(name="伪造的转馆快照"))
        assertThrows(BadRequestException::class.java){printer.requisitionPrint(requisition.id.value)}
    }

    @ParameterizedTest @EnumSource(value=EnumCollectionPrintType::class,names=["RESTORATION"],mode=EnumSource.Mode.EXCLUDE)
    fun `all native prints use current role and museum rather than stale administrator login`(type:EnumCollectionPrintType)=fixture { printer ->
        if(type==EnumCollectionPrintType.ACQUISITION)actorRole("reader","ACQUIRER",setOf(CollectionConst.ACQUIRE))
        assertEquals(key(type),printer.print(type,key(type)).businessId)
        assertThrows(BadRequestException::class.java){printer.print(type,key(type,"other"))}
        UserDao["reader"].groupId="other"
        assertThrows(BadRequestException::class.java){printer.print(type,key(type))}
        assertEquals(key(type,"other"),printer.print(type,key(type,"other")).businessId)
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){printer.print(type,key(type,"other"))}
        actorRole("reader","RESTORED",setOf(CollectionConst.SELECT));UserDao["reader"].enabled=false
        assertThrows(BadRequestException::class.java){printer.print(type,key(type,"other"))}
    }

    @Test fun `foreign exit expert originals reject printing without changing retained records`()=fixture { printer ->
        val expert=DeregistrationExpertOpinionDao.new {
            orgId="other";deregistrationId="deregistration-museum";expertKey="expert"
            content=DeregistrationExpertOpinionVo(expertKey="expert",expertName="测试专家",organization="机构",professionalTitle="高级",specialty="测试",seniorQualificationFileIds="qualification",independenceStatementFileIds="statement",opinion="测试意见",agree=true,signedOpinionFileIds="opinion",assessedAt=java.time.LocalDate.of(2026,9,1))
        }
        assertThrows(BadRequestException::class.java){printer.deregistrationPrint("deregistration-museum")}
        assertEquals("other",expert.orgId)
        expert.orgId="museum"
        // 修正馆别不能代替尚缺的封存原件。
        assertThrows(BadRequestException::class.java){printer.deregistrationPrint("deregistration-museum")}
    }

    @Test fun `missing restoration deployment rejects provided payload instead of using client data`()=fixture { printer ->
        val stale=CollectionPrintVo(type=EnumCollectionPrintType.RESTORATION,title="旧副本",businessId="project",documentNo="project",orgName="测试馆")
        assertThrows(BadRequestException::class.java){CollectionPrintRenderRequestResolver.resolve(CollectionPrintRenderRequest(data=stale),printer::print)}
    }

    @Test fun `receiving museum prints original retained ledger after transfer without reading current foreign collection`()=fixture { printer ->
        val transfer=CollectionTransferDao["transfer-museum"];transfer.toOrgId="other"
        transfer.approvalDocNo="省批〔2026〕1号"
        transfer.executedAt=java.time.LocalDateTime.of(2026,9,25,10,0)
        val original=CollectionDao["collection-museum"].toModel()
        val snapshot=CollectionRetainedLedgerSnapshot(original,
            identity=CollectionRetainedLedgerIdentity("museum",original.id!!,transfer.id.value,"other"))
        val retained=CollectionRetainedLedgerDao.new("retained") {
            orgId="museum";collectionId="collection-museum";transferId="transfer-museum";totalRegNo="REG-museum";
            name=original.name!!;targetOrgId="other";archiveId="";this.snapshot=snapshot
        }
        val archiveService=CollectionPermanentArchiveService(mock(BusinessAttachmentBinder::class.java),CollectionScopeResolver("multi",""))
        retained.archiveId=archiveService.seal("museum","COLLECTION_RETAINED_LEDGER",retained.id.value,"TRANSFERRED",snapshot,null)
        CollectionDao["collection-museum"].apply {orgId="other";name="当前变更名称"}
        UserDao["reader"].groupId="other"
        val print = printer.transferPrint("transfer-museum")
        assertEquals(original.name,print.collections.single().name)
        assertEquals("博物馆藏品跨馆转移交接表",print.title)
        assertEquals("案卷编号",print.template?.documentLabel)
        assertEquals("transfer-museum",print.documentNo)
        val fields=print.fields.associate { it.label to it.value }
        assertEquals("transfer-museum",fields["接收馆入馆凭证号"])
        assertEquals("省批〔2026〕1号",fields["批文号"])
        CollectionRetainedLedgerDao["retained"].targetOrgId="third"
        assertThrows(BadRequestException::class.java){printer.transferPrint("transfer-museum")}
    }

    @ParameterizedTest
    @ValueSource(strings=["snapshot","archive-digest","archive-body","missing-archive","archive-owner","missing-ledger","target","collection"])
    fun `transfer print refuses damaged retained originals and preserves its source ledger`(damage:String)=fixture { printer ->
        val transfer=CollectionTransferDao["transfer-museum"].apply {
            toOrgId="other";executedAt=java.time.LocalDateTime.of(2026,9,25,10,0)
        }
        val original=CollectionDao["collection-museum"].toModel()
        val snapshot=CollectionRetainedLedgerSnapshot(original,
            identity=CollectionRetainedLedgerIdentity("museum",original.id!!,transfer.id.value,"other"))
        val retained=CollectionRetainedLedgerDao.new("retained") {
            orgId="museum";collectionId=original.id!!;transferId=transfer.id.value
            totalRegNo=original.totalRegNo!!;name=original.name!!;targetOrgId="other";archiveId="";this.snapshot=snapshot
        }
        val archives=CollectionPermanentArchiveService(mock(BusinessAttachmentBinder::class.java),CollectionScopeResolver("multi",""))
        retained.archiveId=archives.seal("museum","COLLECTION_RETAINED_LEDGER",retained.id.value,"TRANSFERRED",snapshot,null)
        val frozen=CollectionPermanentArchiveDao[retained.archiveId]
        CollectionDao[original.id!!].apply {orgId="other";name="接收馆现名"}
        UserDao["reader"].groupId="other"
        when(damage) {
            "snapshot" -> {retained.snapshot=snapshot.copy(collection=original.copy(name="被替换的留存名"));retained.name="被替换的留存名"}
            "archive-digest" -> frozen.digest="0".repeat(64)
            "archive-body" -> {
                frozen.snapshot=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(snapshot.copy(collection=original.copy(name="改写的冻结原件")))!!
                frozen.digest=java.security.MessageDigest.getInstance("SHA-256").digest(frozen.snapshot.toByteArray()).joinToString("") {"%02x".format(it)}
            }
            "missing-archive" -> retained.archiveId="missing"
            "archive-owner" -> frozen.bizId="another-ledger"
            "missing-ledger" -> {
                retained.delete()
                CollectionDao[original.id!!].apply {orgId="museum";name="回流后的现名"}
                UserDao["reader"].groupId="museum"
            }
            "target" -> retained.targetOrgId="third"
            "collection" -> retained.snapshot=snapshot.copy(collection=original.copy(id="collection-other"))
        }
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.clear()
        fun state()=listOf(CollectionRetainedLedgers,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Collections).map { table ->
            table.selectAll().map { row -> table.columns.map {row[it]} }
        }
        val before=state()
        assertThrows(BadRequestException::class.java){printer.transferPrint(transfer.id.value)}
        assertEquals(before,state(),"拒绝打印不能修复、重封或改写留存账和原件")
    }

    @Test fun `original voucher prints sealed source ledger after collection moves and refuses tampering`()=fixture { printer ->
        actorRole("reader","ACQUIRER",setOf(CollectionConst.ACQUIRE))
        val c=CollectionDao["collection-museum"]
        c.entryVoucherNo="RJ-museum"
        c.registeredAt=java.time.LocalDateTime.of(2026,9,1,10,0)
        AcquisitionDao["acquisition-museum"].collectionId=c.id.value
        val original=c.toModel()
        val snapshot=CollectionRetainedLedgerSnapshot(original,identity=CollectionRetainedLedgerIdentity("museum",c.id.value,"transfer-museum","other"))
        val archiveService=CollectionPermanentArchiveService(mock(BusinessAttachmentBinder::class.java),CollectionScopeResolver("multi",""))
        val retained=CollectionRetainedLedgerDao.new("voucher-retained") {
            orgId="museum";collectionId=c.id.value;transferId="transfer-museum";totalRegNo=c.totalRegNo
            name=c.name;targetOrgId="other";archiveId="";this.snapshot=snapshot
        }
        retained.archiveId=archiveService.seal("museum","COLLECTION_RETAINED_LEDGER",retained.id.value,"TRANSFERRED",snapshot,null)
        c.orgId="other";c.name="接收馆私有新名称";c.totalRegNo="TARGET-001"
        val result=printer.entryVoucherPrint("voucher-museum")
        assertEquals(original.name,result.collections.single().name)
        assertEquals("REG-museum",result.collections.single().totalRegNo)
        assertTrue(result.fields.any {it.label.endsWith("登记号与状态") && it.value!!.startsWith("REG-museum；正式登记：")})
        retained.snapshot=snapshot.copy(collection=original.copy(name="被篡改的名称"))
        assertThrows(BadRequestException::class.java){printer.entryVoucherPrint("voucher-museum")}
        retained.snapshot=snapshot.copy(collection=original.copy(entryVoucherNo="OTHER-VOUCHER"))
        assertThrows(BadRequestException::class.java){printer.entryVoucherPrint("voucher-museum")}
        retained.snapshot=snapshot
        val sealedId=retained.archiveId
        retained.archiveId="missing-archive"
        assertThrows(BadRequestException::class.java){printer.entryVoucherPrint("voucher-museum")}
        retained.archiveId=sealedId
        c.delete()
        assertEquals(original.name,printer.entryVoucherPrint("voucher-museum").collections.single().name)
    }

    @Test fun `original acquisition print uses its voucher bound retained ledger after transfer`()=fixture { printer ->
        RolePermissions.deleteAll()
        actorRole("reader","ACQUIRER",setOf(CollectionConst.ACQUIRE))
        val acquisition=AcquisitionDao["acquisition-museum"]
        val collection=CollectionDao["collection-museum"]
        acquisition.collectionId=collection.id.value
        collection.entryVoucherNo="RJ-museum"
        EntryVoucherDao["voucher-museum"].voucherStatus=EnumVoucherStatus.REGISTERED
        val original=collection.toModel()
        val snapshot=CollectionRetainedLedgerSnapshot(original,
            identity=CollectionRetainedLedgerIdentity("museum",collection.id.value,"transfer-museum","other"))
        val archiveService=CollectionPermanentArchiveService(mock(BusinessAttachmentBinder::class.java),CollectionScopeResolver("multi",""))
        val retained=CollectionRetainedLedgerDao.new("acquisition-retained") {
            orgId="museum";collectionId=collection.id.value;transferId="transfer-museum";totalRegNo=collection.totalRegNo
            name=collection.name;targetOrgId="other";archiveId="";this.snapshot=snapshot
        }
        retained.archiveId=archiveService.seal("museum","COLLECTION_RETAINED_LEDGER",retained.id.value,"TRANSFERRED",snapshot,null)
        val laterSnapshot=snapshot.copy(
            collection=original.copy(entryVoucherNo="RJ-return",name="再次调出前的名称"),
            identity=CollectionRetainedLedgerIdentity("museum",collection.id.value,"transfer-again","other"))
        val laterRetained=CollectionRetainedLedgerDao.new("acquisition-retained-again") {
            orgId="museum";collectionId=collection.id.value;transferId="transfer-again";totalRegNo=collection.totalRegNo
            name="再次调出前的名称";targetOrgId="other";archiveId="";this.snapshot=laterSnapshot
        }
        laterRetained.archiveId=archiveService.seal("museum","COLLECTION_RETAINED_LEDGER",laterRetained.id.value,"TRANSFERRED",laterSnapshot,null)
        collection.orgId="other";collection.name="接收馆当前名称";collection.totalRegNo="TARGET-001"
        val print=printer.acquisitionPrint(acquisition.id.value)
        assertEquals("museum",print.orgId)
        assertEquals(original.name,print.collections.single().name)
        assertEquals(original.totalRegNo,print.collections.single().totalRegNo)
        EntryVoucherDao["voucher-museum"].acquisitionIds="acquisition-other"
        assertThrows(BadRequestException::class.java){printer.acquisitionPrint(acquisition.id.value)}
        EntryVoucherDao["voucher-museum"].acquisitionIds=acquisition.id.value
        retained.snapshot=snapshot.copy(collection=original.copy(name="伪造原馆名称"))
        assertThrows(BadRequestException::class.java){printer.acquisitionPrint(acquisition.id.value)}
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["duplicate", "legacy-unbound", "foreign-org", "wrong-collection"])
    fun `voucher historical lookup refuses ambiguous or unbound originals`(fault: String)=fixture { printer ->
        actorRole("reader","ACQUIRER",setOf(CollectionConst.ACQUIRE))
        val c=CollectionDao["collection-museum"]
        c.entryVoucherNo="RJ-museum"
        AcquisitionDao["acquisition-museum"].collectionId=c.id.value
        val original=c.toModel()
        val archiveService=CollectionPermanentArchiveService(mock(BusinessAttachmentBinder::class.java),CollectionScopeResolver("multi",""))
        for (index in 1..if(fault=="duplicate") 2 else 1) {
            val transfer="retained-transfer-$index"
            val sourceOrg=if(fault=="foreign-org") "other" else "museum"
            val snapshot=CollectionRetainedLedgerSnapshot(
                original.copy(id=if(fault=="wrong-collection") "another-collection" else original.id),
                identity=if(fault=="legacy-unbound") null else CollectionRetainedLedgerIdentity(sourceOrg,c.id.value,transfer,"other"))
            val row=CollectionRetainedLedgerDao.new("retained-$index") {
                orgId=sourceOrg;collectionId=c.id.value;transferId=transfer;totalRegNo=c.totalRegNo
                name=c.name;targetOrgId="other";archiveId="";this.snapshot=snapshot
            }
            row.archiveId=archiveService.seal(sourceOrg,"COLLECTION_RETAINED_LEDGER",row.id.value,"TRANSFERRED",snapshot,null)
        }
        c.orgId="other"
        val originals=CollectionPermanentArchiveDao.all().map { it.snapshot }
        assertThrows(BadRequestException::class.java){printer.entryVoucherPrint("voucher-museum")}
        assertEquals(originals,CollectionPermanentArchiveDao.all().map { it.snapshot })
        assertEquals("other",c.orgId)
    }

    @Test fun `foreign collection and metadata cannot enrich a current authorized business`()=fixture { printer ->
        actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))
        ReproductionDao["reproduction-museum"].collectionId="collection-other"
        assertThrows(BadRequestException::class.java){printer.reproductionPrint("reproduction-museum")}
        CollectionCategoryDao.new("foreign"){orgId="other";name="外馆分类"}
        CollectionDao["collection-museum"].categoryId="foreign"
        assertThrows(BadRequestException::class.java){printer.collectionArchivePrint("collection-museum")}
        CollectionDao["collection-museum"].categoryId=null
        WarehouseDao.new("foreign"){orgId="other";name="外馆库房";sceneType=EnumLocationScene.STORAGE}
        CollectionDao["collection-museum"].warehouseId="foreign"
        assertThrows(BadRequestException::class.java){printer.collectionArchivePrint("collection-museum")}
        CollectionDao["collection-museum"].warehouseId=null
        for(org in listOf("museum","other")) {
            CollectionMediaDao.new {orgId=org;collectionId="collection-museum";fileId="media-$org"}
            CollectionAttachmentDao.new {orgId=org;collectionId="collection-museum";fileId="attachment-$org"}
        }
        val result=printer.collectionArchivePrint("collection-museum").collections.single()
        assertEquals(1L,result.mediaCount);assertEquals(1L,result.attachmentCount)
    }

    @Test fun `historical inactive museum requires explicit current cross museum authorization`()=fixture { printer ->
        CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        assertThrows(BadRequestException::class.java){printer.collectionArchivePrint("collection-museum")}
        actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))
        assertEquals("collection-museum",printer.collectionArchivePrint("collection-museum").businessId)
        assertThrows(BadRequestException::class.java){printer.acquisitionPrint("acquisition-museum")}
    }
}
