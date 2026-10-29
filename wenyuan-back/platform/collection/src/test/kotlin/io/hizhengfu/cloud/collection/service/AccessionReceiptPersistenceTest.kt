package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate
import java.time.LocalDateTime

/** Real receipt, source archives, reserved numbers and initial catalog services share one database. */
class AccessionReceiptPersistenceTest {
    @Test fun `repeating an already registered legacy voucher does not invent or rewrite its missing source list`() = fixture { db -> transaction(db) {
        val voucher=EntryVoucherDao["voucher"]
        voucher.voucherStatus=EnumVoucherStatus.REGISTERED;voucher.acquisitionIds=""
        assertEquals(EnumVoucherStatus.REGISTERED,vouchers.execute("voucher").voucherStatus)
        assertEquals("",voucher.acquisitionIds);assertEquals(0L,CollectionDao.count())
    } }
    @Test fun `reusing acquisition material as a receipt signature produces an actionable business error`() = fixture { db -> transaction(db) {
        val error=assertThrows(BadRequestException::class.java) {receipts.record("voucher",receipt().copy(signedReceiptFileIds="source"))}
        assertTrue(error.message!!.contains("本次移交与接收双方签署的独立清册"))
        assertEquals(0L,AccessionReceiptDao.count())
    } }
    @Test fun `a manually created pending ledger links to actual receipt and reaches registered stock without another number`() = fixture { db ->
        val target=transaction(db) {
            val id=collections.create(CollectionVo(name="实物资料one",nature=EnumCollectionNature.OTHER,source=EnumSource.DONATE,
                quantity=1,unit="件",categoryId="paper",era="当代",texture="纸",completeness=EnumCompleteness.COMPLETE,acquireDate=acquired,remark="原台账说明"))
            receipts.record("voucher",receipt());receipts.record("voucher",receipt("two"))
            vouchers.registrationTargets("voucher").getValue("one").single {it.collectionId==id}
        }
        transaction(db) {
            vouchers.execute("voucher",EntryVoucherExecuteVo(mapOf("one" to EntryVoucherExistingCollectionVo(target.collectionId,target.stateHash))))
            val linked=row()
            assertEquals(target.collectionId,linked.id.value);assertEquals(target.totalRegNo,linked.totalRegNo)
            assertEquals("原台账说明",linked.remark);assertEquals("store",linked.warehouseId)
            assertEquals(2L,CollectionDao.count());assertEquals(2L,CollectionRegistrationNumberDao.count())
            assertEquals(target.collectionId,AccessionReceiptDao.find {AccessionReceipts.acquisitionId eq "one"}.single().collectionId)
            assertTrue(catalog.get(linked.id.value).receiptLinked)
            approve(linked)
            assertTrue(linked.registered);assertEquals(EnumCollectionStatus.IN_STORE,linked.lifeStatus)
        }
    }

    @Test fun `late execute failure rolls back both reserved numbers and ledger logs`() = fixture { db ->
        transaction(db) {
            receipts.record("voucher",receipt());receipts.record("voucher",receipt("two"))
            CollectionPermanentArchiveDao.all().single {it.bizType==CollectionConst.BIZ_ACQUISITION && it.bizId=="two" && it.phase=="AGREEMENT"}.delete()
        }
        assertThrows(BadRequestException::class.java) {transaction(db) {vouchers.execute("voucher")}}
        transaction(db) {
            assertEquals(0L,CollectionDao.count());assertEquals(0L,CollectionRegistrationNumberDao.count());assertEquals(0L,CollectionLedgerLogDao.count())
            assertTrue(AccessionReceiptDao.all().all {it.collectionId==null})
            assertTrue(AcquisitionDao.all().all {it.collectionId==null})
            assertEquals(EnumVoucherStatus.DRAFT,EntryVoucherDao["voucher"].voucherStatus)
        }
    }

    @Test fun `existing target confirmation rejects stale values without linking another collection`() = fixture { db ->
        val target=transaction(db) {
            collections.create(CollectionVo(name="实物资料one",nature=EnumCollectionNature.OTHER,source=EnumSource.DONATE,quantity=1,unit="件",acquireDate=acquired))
            receipts.record("voucher",receipt());receipts.record("voucher",receipt("two"))
            vouchers.registrationTargets("voucher").getValue("one").single()
        }
        transaction(db) {CollectionDao[target.collectionId].remark="选择后已修改"}
        assertThrows(BadRequestException::class.java) {transaction(db) {
            vouchers.execute("voucher",EntryVoucherExecuteVo(mapOf("one" to EntryVoucherExistingCollectionVo(target.collectionId,target.stateHash))))
        }}
        transaction(db) {assertEquals(1L,CollectionDao.count());assertNull(AcquisitionDao["one"].collectionId)}
    }
    private val at=LocalDateTime.of(2026,8,1,10,0)
    private val acquired=LocalDate.of(2026,7,1)
    private val scope=CollectionScopeResolver("multi","")
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val mainFileReadPolicy=CollectionMainFileReadPolicy(mock(CollectionFileReadAuthorization::class.java),scope,mock(CollectionActorDirectory::class.java))
    private val compliance=AcquisitionComplianceService(scope,binder,archives)
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val designated=DesignatedReceptionService(scope,users,archives)
    private val institution=InstitutionSuccessionCaseService(scope,users,archives)
    private val acquisitions=AcquisitionService(mock(FlowInstanceService::class.java),scope,binder,compliance,CollectionRetainedLedgerService(scope,archives))
    private val receipts=AccessionReceiptService(scope,compliance,users,archives,mainFileReadPolicy)
    private val locks=CollectionOperationLockService()
    private val catalog=CollectionCatalogService(scope,locks,CollectionGradingService(scope,archives),receipts,binder)
    private val collections=CollectionService(scope,binder,CollectionRegistrationNumberService(),locks,catalog,mock(CollectionChangeService::class.java),CollectionRetainedLedgerService(scope,archives),mock(CollectionDisclosureService::class.java))
    private val vouchers=EntryVoucherService(scope,collections,binder,mock(CollectionPrintService::class.java),compliance,receipts)
    private val appraisals=AppraisalService(mock(FlowInstanceService::class.java),scope,mock(CollectionUserResolver::class.java),mock(CollectionChangeService::class.java),binder,locks,receipts)
    private fun login(id: String="editor", org: String="museum", permission:String=CollectionConst.LEDGER_CREATE) {
        val user=SecurityUser(id=id,name=id,username=id,password="",groupId=org,groupIds=setOf(org),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(permission)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    @BeforeEach fun setup() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        login()
    }
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun fixture(block: (Database)->Unit) {
        val file=java.nio.file.Files.createTempFile("accession-",".sqlite")
        val db=Database.connect("jdbc:sqlite:$file",driver="org.sqlite.JDBC")
        try {
            transaction(db) {
                originalReaderSchema()
                SchemaUtils.create(io.hizhengfu.cloud.collection.dao.CollectionLedgerStatEvents,Collections,CollectionOrgs,CollectionGradings,CollectionCatalogs,CollectionCatalogVersions,
                    CollectionMedias,CollectionAttachments,CollectionCategories,Warehouses,CollectionOperationLocks,CollectionLedgerLogs,
                    CollectionRegistrationNumbers,CollectionRetainedLedgers,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,
                    DesignatedReceptions,DesignatedReceptionRecords,Acquisitions,AcquisitionComplianceCases,AcquisitionExpertOpinions,EntryVouchers,StorageMovements,AccessionReceipts,Appraisals,AppraisalMembers,
                    InstitutionSuccessionCases,InstitutionSuccessionItems,InstitutionSuccessionRecords,CollectionAuxiliaries,CollectionTransfers)
                CollectionCategoryDao.new("paper") { name="文件、宣传品"; code="24"; parentId="0" }
                CollectionOrgDao.new("museum") { name="本馆";code="M";systemGroupId="museum" }
                CollectionOrgDao.new("other") { name="外馆";code="O";systemGroupId="other" }
                listOf("editor","reviewer","reader").forEach {originalReader(it,CollectionConst.LEDGER_CREATE)}
                actorRole("editor","RECEIPT_OPERATOR",setOf(CollectionConst.ACQUIRE))
                actorRole("editor","CATALOG_EDITOR",setOf(CollectionConst.CATALOG_EDIT))
                actorRole("reviewer","CATALOG_REVIEWER",setOf(CollectionConst.CATALOG_AUDIT))
                listOf("officer","keeper").forEach {originalReader(it,CollectionConst.ACQUIRE)}
                originalReader("foreign",CollectionConst.ACQUIRE,"other")
                WarehouseDao.new("store") { orgId="museum";name="库房";sceneType=EnumLocationScene.STORAGE }
                WarehouseDao.new("other-store") { orgId="other";name="外馆库房";sceneType=EnumLocationScene.STORAGE }
                listOf("source","photo","new-photo","sign","correction","late").forEach { id -> FileDao.new(id) { name="$id.pdf";path="/files/$id.pdf";hash=id;size=100;ext="pdf" } }
                acquisition("one");acquisition("two")
                EntryVoucherDao.new("voucher") { orgId="museum";voucherNo="RJ-1";acquisitionIds="one,two";verifiedAcquisitionIds="one,two";attachmentIds="sign" }
            }
            block(db)
        } finally { java.nio.file.Files.deleteIfExists(file) }
    }
    private fun acquisition(id: String, quantity: Int=1, date: LocalDate=acquired, unit:String="件") {
        val a=AcquisitionDao.new(id) { orgId="museum";name="实物资料$id";source=EnumSource.DONATE;this.quantity=quantity;this.unit=unit;era="当代";texture="纸";categoryId="paper";completeness=EnumCompleteness.COMPLETE;images="photo" }
        compliance.saveDossier(id,AcquisitionDossierVo(EnumCollectionNature.OTHER,date.minusDays(2),"source","source","source",true,"source"))
        compliance.complete(id,AcquisitionCompletionVo(date.minusDays(1),"source","source",date.minusDays(1),date,"source","年报实际公示",donation=DonationAgreementTermsVo("详见一条","二条","三条","四条","五条","六条","七条")))
        a.flowStatus=EnumFlowStatus.PASS
    }
    private fun receipt(id: String="one", count: Int=1, checked: LocalDateTime=at) = AccessionReceiptVo(
        id,checked,"store",setOf("keeper"),setOf("捐赠单位张某"),
        (1..count).map { AccessionPieceVo("$id-$it",checked.minusHours(1),"实物编号及尺寸$it","保存完好",true,true,"photo",actualQuantity=1,actualQuantityUnit="件") },true,"sign")
    @Test fun `one set with two actual pieces remains distinct through receipt catalog approval and ledger event`()=fixture { db ->
        transaction(db) {
            acquisition("set",unit="套")
            EntryVoucherDao["voucher"].acquisitionIds="set,two";EntryVoucherDao["voucher"].verifiedAcquisitionIds="set,two"
            val input=receipt("set").copy(pieces=receipt("set").pieces.map {it.copy(actualQuantity=2,actualQuantityUnit="件")})
            receipts.record("voucher",input);receipts.record("voucher",receipt("two"));vouchers.execute("voucher")
            val collection=row("set")
            assertEquals(1,collection.quantity);assertEquals("套",collection.unit)
            assertEquals(2,collection.actualQuantity);assertEquals("件",collection.actualQuantityUnit)
            assertEquals(at.minusHours(1).toLocalDate(),collection.entryDate);assertEquals(at,collection.receiptCompletedAt)
            approve(collection)
        }
        transaction(db) {
            val collection=row("set")
            assertNotNull(collection.registeredAt);assertEquals(1,collection.quantity);assertEquals("套",collection.unit)
            assertEquals(2,collection.actualQuantity);assertEquals("件",collection.actualQuantityUnit)
            val event=CollectionLedgerStatEventDao.all().single {it.collectionId==collection.id.value}
            assertEquals(1L,event.item.quantity);assertEquals("套",event.item.unit);assertEquals(2L,event.item.actualQuantity)
        }
    }
    private fun receiveAll() { receipts.record("voucher",receipt());receipts.record("voucher",receipt("two"));vouchers.execute("voucher") }
    private fun row(id: String="one")=CollectionDao[AcquisitionDao[id].collectionId!!]
    private fun approve(row: CollectionDao) {
        login("editor");catalog.save(row.id.value,CollectionCatalogMapper.fromCollection(row.toModel()).copy(classificationNo="C-${row.totalRegNo}", dimensions=DimensionVo(height="12"), dimensionUnit="cm", weight="100", weightUnit="g", completeStatus="完好", textureCategory1="单一质地",textureCategory2="有机质/植物质材料",preserveState=EnumPreserve.entries.first(), feature="实物识别特征", coverImage="photo", registrationDetails=CollectionRegistrationDetailsVo(coverIsFrontView=true,weightExcludesAccessories=true,eraMeaning=EnumCollectionEraMeaning.MANUFACTURED,eraNotation=EnumCollectionEraNotation.GREGORIAN)));catalog.submit(row.id.value)
        login("reviewer");catalog.review(row.id.value,CollectionCatalogReviewVo(EnumCatalogStatus.APPROVED,"已核实实际核收原件"))
    }

    @Test fun `receipt original requires current voucher version archive file and museum` ()=fixture { db -> transaction(db) {
        val id=receipts.record("voucher",receipt())
        val archive=CollectionPermanentArchiveDao.all().single {
            it.bizType==AccessionReceiptService.BIZ && it.bizId==id && it.phase=="RECORDED"
        }.id.value
        receipts.requireArchivedOriginal("voucher",id,archive,"sign")
        assertThrows(BadRequestException::class.java){receipts.requireArchivedOriginal("voucher","missing",archive,"sign")}
        assertThrows(BadRequestException::class.java){receipts.requireArchivedOriginal("voucher",id,"missing","sign")}
        assertThrows(BadRequestException::class.java){receipts.requireArchivedOriginal("voucher",id,archive,"missing")}
        login("foreign","other",CollectionConst.ACQUIRE)
        assertThrows(BadRequestException::class.java){receipts.requireArchivedOriginal("voucher",id,archive,"sign")}
    } }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("revoked,false", "revoked,true", "disabled,false", "disabled,true", "moved,false", "moved,true")
    fun `receipt writes reject stale login after current eligibility changes`(change: String, repeat: Boolean)=fixture { db -> transaction(db) {
        login("officer", permission=CollectionConst.ACQUIRE)
        val value=receipt()
        if(repeat) receipts.record("voucher",value)
        when(change) {
            "revoked" -> io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
            "disabled" -> io.hizhengfu.cloud.sys.system.dao.UserDao["officer"].enabled=false
            "moved" -> io.hizhengfu.cloud.sys.system.dao.UserDao["officer"].groupId="other"
        }
        val before=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }
        val count=AccessionReceiptDao.count()
        assertThrows(BadRequestException::class.java){receipts.record("voucher",value)}
        assertEquals(count,AccessionReceiptDao.count())
        assertEquals(before,CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) })
    } }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "create,revoked", "create,disabled", "create,moved",
        "update,revoked", "update,disabled", "update,moved",
        "remove,revoked", "remove,disabled", "remove,moved",
        "execute,revoked", "execute,disabled", "execute,moved",
        "repeat,revoked", "repeat,disabled", "repeat,moved")
    fun `voucher writes reject stale current eligibility`(operation: String, change: String)=fixture { db ->
        val value=transaction(db) {
        val value=EntryVoucherDao["voucher"].toModel().copy(basis="改动凭证依据")
        if(operation=="create") EntryVoucherDao["voucher"].delete()
        if(operation in setOf("execute","repeat")) {
            receipts.record("voucher",receipt());receipts.record("voucher",receipt("two"))
            if(operation=="repeat") vouchers.execute("voucher")
        }
        value
        }
        transaction(db) {
        when(change) {
            "revoked" -> io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
            "disabled" -> io.hizhengfu.cloud.sys.system.dao.UserDao["editor"].enabled=false
            "moved" -> io.hizhengfu.cloud.sys.system.dao.UserDao["editor"].groupId="other"
        }
        fun snapshot():List<List<String>> {
            TransactionManager.current().entityCache.flush()
            return listOf(EntryVouchers,Acquisitions,AccessionReceipts,Collections,CollectionRegistrationNumbers,
                CollectionLedgerLogs,CollectionPermanentArchives,CollectionPermanentArchiveFiles,StorageMovements).map { table ->
                table.selectAll().map { row -> table.columns.joinToString("|") { row[it].toString() } }.sorted()
            }
        }
        val before=snapshot()
        assertThrows(BadRequestException::class.java) {
            when(operation) {
                "create" -> vouchers.save(value.copy(id=null))
                "update" -> vouchers.save(value)
                "remove" -> vouchers.remove("voucher")
                else -> vouchers.execute("voucher")
            }
        }
        assertEquals(before,snapshot())
    } }

    @Test fun `detail rejects altered receipt facts without repairing the permanent archive`()=fixture { db -> transaction(db) {
        val id=receipts.record("voucher",receipt())
        val row=AccessionReceiptDao[id]
        val count=CollectionPermanentArchiveDao.count()
        row.content=row.content.copy(handoverNames=setOf("未经原件支持的新移交人"))
        assertThrows(BadRequestException::class.java){receipts.detail("voucher")}
        assertEquals(count,CollectionPermanentArchiveDao.count())
    } }
    @Test fun `print rejects changed receipt facts instead of issuing an unsupported current copy`()=fixture { db -> transaction(db) {
        actorRole("editor","PRINT_READER",setOf(CollectionConst.SELECT))
        val id=receipts.record("voucher",receipt())
        AccessionReceiptDao[id].content=receipt().copy(accepted=false,issueDescription="未经归档支持的新事实")
        val printer=CollectionPrintService(collections,scope,mock(CollectionPrintTemplateService::class.java),mock(LoanStatutoryService::class.java),receiptEvidence=AccessionReceiptEvidence(archives,mainFileReadPolicy))
        assertThrows(BadRequestException::class.java){printer.entryVoucherPrint("voucher")}
    } }

    @org.junit.jupiter.api.io.TempDir lateinit var originalsDir:java.nio.file.Path
    @Test fun `actual original bytes are checked on detail and printing without resetting the baseline`()=fixture { db -> transaction(db) {
        actorRole("editor","PRINT_READER",setOf(CollectionConst.SELECT))
        listOf("photo","sign").forEach { id ->
            FileDao[id].path="$id.pdf"
            java.nio.file.Files.writeString(originalsDir.resolve("$id.pdf"),"actual-original-$id")
        }
        val realArchives=CollectionPermanentArchiveService(binder,scope,io.hizhengfu.cloud.common.file.LocalFileObjectStorage(originalsDir.toString()),readAccess=unprotectedArchiveReadAccessProvider())
        val reader=AccessionReceiptService(scope,compliance,users,realArchives,mainFileReadPolicy)
        val id=reader.record("voucher",receipt())
        val printer=CollectionPrintService(collections,scope,mock(CollectionPrintTemplateService::class.java),mock(LoanStatutoryService::class.java),receiptEvidence=AccessionReceiptEvidence(realArchives,mainFileReadPolicy))
        val original=AccessionReceiptEvidence(realArchives,mainFileReadPolicy).requireRead(AccessionReceiptDao[id],EntryVoucherDao["voucher"])
        assertFalse(original.legacyFileContentUnverified)
        assertNotNull(reader.detail("voucher"));assertNotNull(printer.entryVoucherPrint("voucher"))
        val before=CollectionPermanentArchiveFileDao.all().associate { it.id.value to it.metadata }
        java.nio.file.Files.writeString(originalsDir.resolve("sign.pdf"),"replaced-bytes")
        assertUnavailableOriginals(reader.detail("voucher"))
        assertThrows(BadRequestException::class.java){reader.record("voucher",receipt())}
        assertThrows(BadRequestException::class.java){printer.entryVoucherPrint("voucher")}
        assertEquals(before,CollectionPermanentArchiveFileDao.all().associate {it.id.value to it.metadata})
    } }
    @Test fun `missing linked or registered original cannot be repaired by reading`()=fixture { db -> transaction(db) {
        receiveAll();approve(row())
        val receipt=AccessionReceiptDao.all().single { it.acquisitionId=="one" }
        val originals=CollectionPermanentArchiveDao.all().filter {it.bizType==AccessionReceiptService.BIZ && it.bizId==receipt.id.value}
        for(phase in listOf("LINKED","REGISTERED")) {
            val original=originals.single {it.phase==phase}
            val saved=original.phase;original.phase="REMOVED"
            val count=CollectionPermanentArchiveDao.count()
            assertThrows(BadRequestException::class.java){receipts.detail("voucher")}
            assertEquals(count,CollectionPermanentArchiveDao.count());assertEquals("REMOVED",original.phase)
            original.phase=saved
            // 恢复本阶段后再模拟下一阶段缺失，避免两条待写记录争用同一临时阶段唯一键。
            TransactionManager.current().entityCache.flush()
        }
        assertNotNull(receipts.detail("voucher"))
    } }
    @Test fun `historical files without byte baseline remain explicitly unverified and missing references fail`()=fixture { db -> transaction(db) {
        val id=receipts.record("voucher",receipt())
        val verified=AccessionReceiptEvidence(archives,mainFileReadPolicy).requireRead(AccessionReceiptDao[id],EntryVoucherDao["voucher"])
        assertTrue(verified.legacyFileContentUnverified)
        val detail=receipts.detail("voucher")
        val item=(detail["items"] as List<*>).first() as Map<*,*>
        assertEquals(true,(item["current"] as Map<*,*>)["legacyFileContentUnverified"])
        val original=CollectionPermanentArchiveDao.all().single {it.bizType==AccessionReceiptService.BIZ && it.bizId==id}
        CollectionPermanentArchiveFileDao.all().first {it.archiveId==original.id.value}.delete()
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        val count=CollectionPermanentArchiveFiles.selectAll().count()
        assertThrows(BadRequestException::class.java){receipts.detail("voucher")}
        assertEquals(count,CollectionPermanentArchiveFiles.selectAll().count())
    } }
    @Test fun `all retained receipt versions and their digests are verified before returning a current history`()=fixture { db -> transaction(db) {
        acquisition("partial",2);EntryVoucherDao["voucher"].acquisitionIds="partial"
        val old=receipts.record("voucher",receipt("partial").copy(accepted=false,issueDescription="未收齐",protectiveMeasures="保管",followUpAt=at.plusDays(1)))
        receipts.record("voucher",receipt("partial",2,at.plusDays(1)).copy(replacementReason="补齐",correctionFileIds="correction"))
        val original=CollectionPermanentArchiveDao.all().single {it.bizType==AccessionReceiptService.BIZ && it.bizId==old}
        original.snapshot=original.snapshot+" "
        assertThrows(BadRequestException::class.java){receipts.detail("voucher")}
        assertEquals(2L,AccessionReceiptDao.count())
    } }

    @Test fun `historical receipt deadline uses the verified acquisition date instead of changed current source`()=fixture { db -> transaction(db) {
        receipts.record("voucher",receipt())
        val source=compliance.case("one")!!
        source.completion=source.completion!!.copy(actualAcquiredOn=acquired.plusMonths(1))
        val item=(receipts.detail("voucher")["items"] as List<*>).first() as Map<*,*>
        assertEquals(acquired,item["actualAcquiredOn"])
        assertEquals(acquired.plusMonths(3),item["deadline"])
    } }

    @Test fun `printing historical receipt without byte baseline exposes warning without sealing new content`()=fixture { db ->
      transaction(db) {
          actorRole("editor","PRINT_READER",setOf(CollectionConst.SELECT))
          receipts.record("voucher",receipt())
      }
      transaction(db) {
        val item=(receipts.detail("voucher")["items"] as List<*>).first() as Map<*,*>
        assertEquals(true,(item["current"] as Map<*,*>)["legacyFileContentUnverified"])
        fun retainedState()=listOf(CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,
            AccessionReceipts,EntryVouchers,Acquisitions).associate { table ->
            table.tableName to table.selectAll().map { row ->
                table.columns.joinToString("|") { row[it].toString() }
            }.sorted()
        }
        val before=retainedState()
        val printer=CollectionPrintService(collections,scope,mock(CollectionPrintTemplateService::class.java),
            mock(LoanStatutoryService::class.java),receiptEvidence=AccessionReceiptEvidence(archives,mainFileReadPolicy))
        val printed=printer.entryVoucherPrint("voucher")
        val warning=printed.fields.single { it.label=="条目1（实物资料one） 历史附件核验" }
        assertEquals("原件内容基线未留存，实际文件一致性尚待核实；本次读取未补建基线",warning.value)
        assertEquals(before,retainedState())
        println("RECEIPT_UNKNOWN_BASELINE_PRINT="+JsonUtils.toJson(mapOf("businessId" to printed.businessId,
            "warningLabel" to warning.label,"warningValue" to warning.value,
            "legacyFileContentUnverified" to true,"retainedTableCount" to before.size,"retainedStateUnchanged" to true)))
    } }

    @Test fun `old checked boxes and approved acquisition cannot fabricate actual receipt`()=fixture { db -> transaction(db) {
        assertThrows(BadRequestException::class.java) { vouchers.execute("voucher") }
        assertEquals(0L,CollectionDao.count());assertEquals(0L,StorageMovementDao.count());assertEquals(EnumVoucherStatus.DRAFT,EntryVoucherDao["voucher"].voucherStatus)
    } }
    @Test fun `partial facts and subsequent signed correction preserve both original snapshots`()=fixture { db -> transaction(db) {
        acquisition("partial",2);EntryVoucherDao["voucher"].acquisitionIds="partial"
        val first=receipt("partial").copy(accepted=false,issueDescription="另一件尚未交付",protectiveMeasures="独立保管已收到物品",followUpAt=at.plusDays(1))
        val old=receipts.record("voucher",first);assertEquals(old,receipts.record("voucher",first))
        assertThrows(BadRequestException::class.java) { vouchers.execute("voucher") }
        val next=receipt("partial",2,at.plusDays(1))
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",next) }
        val id=receipts.record("voucher",next.copy(replacementReason="补齐第二件后再次逐件核收",correctionFileIds="correction"))
        assertEquals(2,AccessionReceiptDao[id].version);assertEquals(first,AccessionReceiptDao[old].content)
        assertEquals(2L,AccessionReceiptDao.count());assertTrue(archives.list(AccessionReceiptService.BIZ,old).isNotEmpty())
        assertThrows(BadRequestException::class.java) { CollectionArchiveFileRetentionGuard().requireDeletable("sign") }
        vouchers.execute("voucher");assertEquals(EnumCollectionStatus.PENDING,row("partial").lifeStatus)
        assertEquals(2,receipts.requireReceived(row("partial")).version)
        approve(row("partial"))
        assertNotNull(row("partial").registeredAt)
        assertEquals(setOf("RECORDED"),CollectionPermanentArchiveDao.all().filter {
            it.bizType==AccessionReceiptService.BIZ && it.bizId==old
        }.map {it.phase}.toSet())
        assertEquals(setOf("RECORDED","LINKED","REGISTERED"),CollectionPermanentArchiveDao.all().filter {
            it.bizType==AccessionReceiptService.BIZ && it.bizId==id
        }.map {it.phase}.toSet())
        assertEquals(2,(receipts.detail("voucher")["items"] as List<*>).first().let {
            ((it as Map<*,*>)["history"] as List<*>).size
        })
    } }
    @Test fun `received but unregistered collection can append an independent signed receipt correction`()=fixture { db -> transaction(db) {
        receiveAll()
        val collection=row()
        val original=receipts.requireReceived(collection)
        val oldDigest=CollectionPermanentArchiveDao.all().single {
            it.bizType==AccessionReceiptService.BIZ && it.bizId==original.id.value && it.phase=="RECORDED"
        }.digest
        val revised=original.content.copy(signedReceiptFileIds="correction",
            replacementReason="原签署清册误关联征集资料，补充独立签署原件",correctionFileIds="correction")
        collection.catalogStatus=EnumCatalogStatus.SUBMITTED
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",revised) }
        collection.catalogStatus=EnumCatalogStatus.DRAFT
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",revised.copy(signedReceiptFileIds="sign")) }
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",revised.copy(signedReceiptFileIds="photo")) }
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",revised.copy(checkedAt=at.plusDays(1))) }
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",revised.copy(pieces=emptyList())) }
        val correctedId=receipts.record("voucher",revised)
        assertEquals(2,AccessionReceiptDao[correctedId].version)
        assertEquals(collection.id.value,AccessionReceiptDao[correctedId].collectionId)
        assertEquals(original.content,AccessionReceiptDao[original.id.value].content)
        assertEquals(correctedId,receipts.requireReceived(collection).id.value)
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",revised.copy(signedReceiptFileIds="sign")) }
        assertEquals(listOf(1,2),((receipts.detail("voucher")["items"] as List<*>).first() as Map<*,*>)
            .let { item -> (item["history"] as List<*>).map { (it as Map<*,*>)["version"] } })
        assertEquals(oldDigest,CollectionPermanentArchiveDao.all().single {
            it.bizType==AccessionReceiptService.BIZ && it.bizId==original.id.value && it.phase=="RECORDED"
        }.digest)
        approve(collection)
        assertNotNull(collection.registeredAt)
        assertEquals(setOf("RECORDED","LINKED","REGISTERED"),CollectionPermanentArchiveDao.all().filter {
            it.bizType==AccessionReceiptService.BIZ && it.bizId==correctedId
        }.map {it.phase}.toSet())
        assertEquals(setOf("RECORDED","LINKED"),CollectionPermanentArchiveDao.all().filter {
            it.bizType==AccessionReceiptService.BIZ && it.bizId==original.id.value
        }.map {it.phase}.toSet())
        assertEquals(2,receipts.detail("voucher").let { detail ->
            ((detail["items"] as List<*>).first() as Map<*,*>)["history"].let { (it as List<*>).size }
        })
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",revised.copy(checkedAt=at.plusDays(2))) }
        val sealedOriginal=CollectionPermanentArchiveDao.all().single {
            it.bizType==AccessionReceiptService.BIZ && it.bizId==original.id.value && it.phase=="RECORDED"
        }
        sealedOriginal.snapshot=sealedOriginal.snapshot+" "
        assertThrows(BadRequestException::class.java) {receipts.requireReceived(collection)}
    } }
    @Test fun `pending receipt may correct a mislinked piece photo while preserving the old original`()=fixture { db -> transaction(db) {
        receiveAll()
        val collection=row()
        val original=receipts.requireReceived(collection)
        val oldDigest=CollectionPermanentArchiveDao.all().single {
            it.bizType==AccessionReceiptService.BIZ && it.bizId==original.id.value && it.phase=="RECORDED"
        }.digest
        val revised=original.content.copy(
            pieces=original.content.pieces.map { it.copy(photoFileIds="new-photo") },
            signedReceiptFileIds="correction",
            replacementReason="旧照片误复用于其他业务，补充本件独立照片并保留旧原件",
            correctionFileIds="correction")
        collection.catalogStatus=EnumCatalogStatus.SUBMITTED
        assertThrows(BadRequestException::class.java) {receipts.record("voucher",revised)}
        collection.catalogStatus=EnumCatalogStatus.DRAFT
        assertThrows(BadRequestException::class.java) {receipts.record("voucher",revised.copy(pieces=revised.pieces.map {it.copy(condition="改变旧实收状况") }))}
        assertThrows(BadRequestException::class.java) {receipts.record("voucher",revised.copy(pieces=revised.pieces.map {it.copy(photoFileIds="sign") }))}
        assertThrows(BadRequestException::class.java) {receipts.record("voucher",revised.copy(pieces=revised.pieces.map {it.copy(photoFileIds="correction") }))}
        val correctedId=receipts.record("voucher",revised)
        assertEquals(original.content,AccessionReceiptDao[original.id.value].content)
        assertEquals(oldDigest,CollectionPermanentArchiveDao.all().single {
            it.bizType==AccessionReceiptService.BIZ && it.bizId==original.id.value && it.phase=="RECORDED"
        }.digest)
        assertEquals(correctedId,receipts.requireReceived(collection).id.value)
        approve(collection)
        assertNotNull(collection.registeredAt)
        assertEquals(setOf("RECORDED","LINKED"),CollectionPermanentArchiveDao.all().filter {
            it.bizType==AccessionReceiptService.BIZ && it.bizId==original.id.value
        }.map {it.phase}.toSet())
        assertEquals(setOf("RECORDED","LINKED","REGISTERED"),CollectionPermanentArchiveDao.all().filter {
            it.bizType==AccessionReceiptService.BIZ && it.bizId==correctedId
        }.map {it.phase}.toSet())
        assertEquals(2,receipts.detail("voucher").let { detail ->
            ((detail["items"] as List<*>).first() as Map<*,*>)["history"].let { (it as List<*>).size }
        })
    } }
    @Test fun `future times duplicate pieces unconfirmed facts and unresolved discrepancies cannot complete`()=fixture { db -> transaction(db) {
        val value=receipt()
        listOf(value.copy(checkedAt=LocalDateTime.now().plusDays(1)),value.copy(pieces=value.pieces+value.pieces),value.copy(accepted=null),value.copy(pieces=listOf(value.pieces[0].copy(identityMatches=null))),value.copy(pieces=listOf(value.pieces[0].copy(conditionMatches=false,discrepancy="裂纹不符"))),value.copy(pieces=listOf(value.pieces[0].copy(receivedAt=at.plusMinutes(1))))).forEach { v ->
            assertThrows(BadRequestException::class.java) { receipts.record("voucher",v) }
        }
        assertEquals(0L,AccessionReceiptDao.count())
    } }
    @Test fun `late actual completion is recordable with handling evidence and stays overdue`()=fixture { db -> transaction(db) {
        acquisition("late-object",date=LocalDate.of(2026,1,1));EntryVoucherDao["voucher"].acquisitionIds="late-object"
        val value=receipt("late-object")
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",value) }
        receipts.record("voucher",value.copy(lateReason="原始手续迟办",lateHandlingFileIds="late"))
        vouchers.execute("voucher")
        val item=(receipts.detail("voucher")["items"] as List<*>).single() as Map<*,*>
        assertEquals(true,item["overdue"]);assertEquals(LocalDate.of(2026,4,1),item["deadline"])
        assertEquals(acquired.minusMonths(6),row("late-object").acquireDate)
    } }
    @Test fun `actual museum keepers and storage scope are checked when recording`()=fixture { db -> transaction(db) {
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",receipt().copy(warehouseId="other-store")) }
        doThrow(BadRequestException("不是本馆保管人员")).`when`(users).requireMuseumMember("outsider","museum")
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",receipt().copy(keeperUserIds=setOf("outsider"))) }
        receipts.record("voucher",receipt());verify(users).requireRole("keeper","museum",CollectionConst.ROLE_KEEPER)
        io.hizhengfu.cloud.sys.system.dao.UserDao["editor"].groupId="other"
        login(org="other");assertThrows(BadRequestException::class.java) { receipts.detail("voucher") }
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",receipt("two")) }
    } }
    @Test fun `receipt generation reserves numbers and formal registration completes voucher only after every item`()=fixture { db ->
        transaction(db) { receiveAll() }
        transaction(db) {
            val first=row();val second=row("two")
            assertNull(first.registeredAt);assertEquals(EnumCollectionStatus.PENDING,first.lifeStatus)
            assertEquals(EnumRelicLevel.UNRATED,first.level);assertEquals(EnumVoucherStatus.RECEIVED,EntryVoucherDao["voucher"].voucherStatus)
            assertEquals(2L,CollectionLedgerLogDao.all().count { it.action==EnumLedgerAction.RESERVE_REG_NO }.toLong())
            assertEquals(0,CollectionLedgerLogDao.all().count { it.action==EnumLedgerAction.REGISTER });assertEquals(0L,StorageMovementDao.count())
            vouchers.execute("voucher");assertEquals(2L,CollectionDao.count())
            approve(first);assertNotNull(first.registeredAt);assertNull(second.registeredAt);assertEquals(EnumVoucherStatus.RECEIVED,EntryVoucherDao["voucher"].voucherStatus)
            approve(second);assertEquals(EnumVoucherStatus.REGISTERED,EntryVoucherDao["voucher"].voucherStatus)
            assertEquals(2L,CollectionLedgerStatEventDao.count());assertEquals(2,CollectionLedgerLogDao.all().count { it.action==EnumLedgerAction.REGISTER });assertEquals(EnumCollectionStatus.IN_STORE,first.lifeStatus)
        }
        transaction(db) { assertEquals(EnumVoucherStatus.REGISTERED,EntryVoucherDao["voucher"].voucherStatus);assertTrue(CollectionDao.all().all { it.registeredAt!=null }) }
    }
    /** 11-C01: real committed receipt/catalog stages, then the actual scoped SQL summary service. */
    @Test fun `four accession stages use actual collection summary without counting unregistered pieces`()=fixture { db ->
        val acquisitionId="statistics-two-pieces"
        transaction(db) {
            acquisition(acquisitionId,2)
            EntryVoucherDao["voucher"].acquisitionIds=acquisitionId
            EntryVoucherDao["voucher"].verifiedAcquisitionIds=acquisitionId
            actorRole("reader","STAGE_STAT_READER",setOf(CollectionConst.STAT))
        }
        fun observe(stage:String,expectedRows:Int,expectedFormal:Int,expectedPending:Int,
                    voucherStatus:EnumVoucherStatus,expectedPieces:Int,expectedVersion:Int) = transaction(db) {
            login("reader",permission=CollectionConst.STAT)
            val observedTables=listOf<org.jetbrains.exposed.v1.core.Table>(EntryVouchers,AccessionReceipts,Collections,CollectionRegistrationNumbers,
                CollectionCatalogs,CollectionCatalogVersions,CollectionLedgerLogs,CollectionLedgerStatEvents)
            fun persistedState()=observedTables.map { table ->
                table.selectAll().map { r -> table.columns.joinToString("|") { r[it].toString() } }.sorted()
            }
            val before=persistedState()
            val stat=collections.summary()
            assertEquals(before,persistedState(),"summary must not materialize, register or change source records")
            assertEquals(expectedRows,CollectionDao.count().toInt())
            assertEquals(voucherStatus,EntryVoucherDao["voucher"].voucherStatus)
            assertEquals(expectedFormal,stat.total)
            assertEquals(expectedPending,stat.pendingRegistrationTotal)
            assertEquals(0,stat.unverifiedRegistrationTotal)
            assertEquals(0,stat.retiredTotal)
            assertEquals(expectedFormal,stat.registeredThisYear)
            assertEquals(expectedFormal,stat.registeredThisMonth)
            assertEquals(expectedFormal,CollectionDao.all().count { it.registeredAt!=null })
            assertEquals(expectedFormal,CollectionLedgerLogDao.all().count { it.action==EnumLedgerAction.REGISTER })
            assertEquals(expectedFormal,CollectionLedgerStatEventDao.count().toInt())
            assertEquals(expectedRows,CollectionRegistrationNumberDao.count().toInt())
            assertEquals(if(expectedFormal==0) emptyMap<String,Long>() else mapOf("件" to 2L),stat.quantityByUnit)
            assertEquals(if(expectedFormal==0) emptyMap<String,Int>() else mapOf("OTHER" to 1),stat.byNature)
            assertEquals(if(expectedFormal==0) emptyMap<String,Int>() else mapOf("DONATE" to 1),stat.bySource)
            assertEquals(if(expectedFormal==0) emptyMap<String,Int>() else mapOf("IN_STORE" to 1),stat.byStatus)
            val receiptRows=AccessionReceiptDao.all().filter { it.acquisitionId==acquisitionId }.sortedBy { it.version }
            val currentReceipt=receiptRows.lastOrNull()
            assertEquals(expectedVersion,currentReceipt?.version ?: 0)
            assertEquals(expectedPieces,currentReceipt?.content?.pieces?.size ?: 0)
            val rows=CollectionDao.all().map { item ->
                assertEquals(acquired,item.acquireDate)
                if(expectedFormal==0) assertNull(item.registeredAt) else assertEquals(LocalDate.now(),item.registeredAt!!.toLocalDate())
                mapOf("id" to item.id.value,"quantity" to item.quantity,"unit" to item.unit,
                    "acquireDate" to item.acquireDate,"registeredAt" to item.registeredAt,
                    "lifeStatus" to item.lifeStatus,"catalogStatus" to item.catalogStatus)
            }
            println("REGISTRATION_STAGE_STAT="+JsonUtils.toJson(mapOf(
                "stage" to stage,"readAfterCommittedStage" to true,"scopeOrgId" to "museum","readerId" to "reader",
                "plannedQuantity" to 2,"actualReceiptPieceCount" to expectedPieces,"receiptVersion" to expectedVersion,
                "receiptAccepted" to currentReceipt?.content?.accepted,"voucherStatus" to voucherStatus,
                "collectionRows" to rows,"reservedNumberCount" to expectedRows,"registerLedgerLogCount" to expectedFormal,
                "registrationStatEventCount" to expectedFormal,"summary" to stat)))
        }
        observe("before_actual_receipt",0,0,0,EnumVoucherStatus.DRAFT,0,0)
        val partial=receipt(acquisitionId).copy(accepted=false,issueDescription="第二件尚未交付",
            protectiveMeasures="已实收一件独立保管",followUpAt=at.plusDays(1))
        val originalReceipt=transaction(db) { login();receipts.record("voucher",partial) }
        observe("partial_actual_receipt",0,0,0,EnumVoucherStatus.DRAFT,1,1)
        transaction(db) {
            login()
            receipts.record("voucher",receipt(acquisitionId,2,at.plusDays(1)).copy(
                replacementReason="实际补齐第二件后共同核收",correctionFileIds="correction"))
            vouchers.execute("voucher")
            assertEquals(partial,AccessionReceiptDao[originalReceipt].content)
        }
        observe("received_pending_registration",1,0,1,EnumVoucherStatus.RECEIVED,2,2)
        transaction(db) { approve(row(acquisitionId)) }
        observe("formally_registered",1,1,0,EnumVoucherStatus.REGISTERED,2,2)
        transaction(db) { assertEquals(partial,AccessionReceiptDao[originalReceipt].content) }
    }
    @Test fun `initial catalog cannot substitute source nature date quantity or effective unit`()=fixture { db -> transaction(db) {
        receiveAll();val r=row();val content=CollectionCatalogMapper.fromCollection(r.toModel())
        listOf(content.copy(source=EnumSource.PURCHASE),content.copy(nature=EnumCollectionNature.RELIC),content.copy(acquireDate=acquired.minusYears(1)),content.copy(acquireDateRange="2000-01-01"),content.copy(acquireYear="2000"),content.copy(warehouseId="other-store"),content.copy(actualQuantity=2),content.copy(actualQuantityUnit="套")).forEach { v ->
            assertThrows(BadRequestException::class.java) { catalog.applyStatutoryChange(r,v,"change","初始登记","editor","leader",at) }
        }
        assertNull(r.registeredAt);assertEquals(0L,CollectionCatalogVersionDao.count());assertEquals(EnumCollectionStatus.PENDING,r.lifeStatus)
        receipts.requireForRegistration(r,content)
    } }
    @Test fun `statutory change materializer cannot replace initial catalog approval`()=fixture { db -> transaction(db) {
        receiveAll()
        val collection=row()
        val content=CollectionCatalogMapper.fromCollection(collection.toModel()).copy(formalName="绕过首次审核的名称")
        receipts.requireForRegistration(collection,content)
        val ledgerCount=CollectionLedgerLogDao.count()
        assertThrows(BadRequestException::class.java) {
            catalog.applyStatutoryChange(collection,content,"change-without-review","试图以订正首次登记","editor","leader",at)
        }
        assertNull(collection.registeredAt)
        assertEquals(EnumCatalogStatus.DRAFT,collection.catalogStatus)
        assertEquals(0L,CollectionCatalogVersionDao.count())
        assertEquals(ledgerCount,CollectionLedgerLogDao.count())
    } }
    @Test fun `sealed receipt cannot be altered after association or used by another collection`()=fixture { db -> transaction(db) {
        receiveAll();val r=row();val receipt=receipts.requireReceived(r)
        receipt.content=receipt.content.copy(checkedAt=at.plusDays(1))
        assertThrows(BadRequestException::class.java) { receipts.requireReceived(r) }
        assertThrows(BadRequestException::class.java) { receipts.requireReceived(CollectionDao.new { orgId="museum";totalRegNo="fake";name="另件" }) }
    } }
    @Test fun `receipt originals freeze voucher edits deletion and further replacement`()=fixture { db -> transaction(db) {
        receipts.record("voucher",receipt())
        assertThrows(BadRequestException::class.java) { vouchers.remove("voucher") }
        assertThrows(BadRequestException::class.java) { vouchers.save(EntryVoucherDao["voucher"].toModel().copy(remark="覆盖")) }
        receipts.record("voucher",receipt("two"));vouchers.execute("voucher")
        assertThrows(BadRequestException::class.java) { receipts.record("voucher",receipt().copy(replacementReason="覆盖",correctionFileIds="correction")) }
    } }
    @Test fun `legacy registered vouchers are explicitly unverified instead of fabricated receipts`()=fixture { db -> transaction(db) {
        EntryVoucherDao["voucher"].voucherStatus=EnumVoucherStatus.REGISTERED
        assertEquals(true,receipts.detail("voucher")["legacyUnverified"])
        vouchers.execute("voucher");assertEquals(0L,AccessionReceiptDao.count());assertEquals(0L,CollectionDao.count())
    } }
    @Test fun `received pending items can enter preliminary appraisal but unreceived drafts cannot`()=fixture { db -> transaction(db) {
        receiveAll();val r=row()
        originalReader("appraiser",CollectionConst.APPRAISE)
        login("appraiser",permission=CollectionConst.APPRAISE)
        assertThrows(BadRequestException::class.java) { receipts.detail("voucher") }
        val id=appraisals.save(AppraisalVo(collectionId=r.id.value,resultName=r.name,conclusion="初步鉴选"))
        assertEquals(r.id.value,AppraisalDao[id].collectionId);assertNull(r.registeredAt)
        val draft=CollectionDao.new { orgId="museum";totalRegNo="draft";name="未核收";lifeStatus=EnumCollectionStatus.PENDING }
        assertThrows(BadRequestException::class.java) { appraisals.save(AppraisalVo(collectionId=draft.id.value)) }
        r.inventoryHold=true;assertThrows(BadRequestException::class.java) { appraisals.save(AppraisalVo(collectionId=r.id.value)) }
    } }
    @Test fun `printed receipt distinguishes proposals reserved numbers and actual piece evidence`()=fixture { db -> transaction(db) {
        listOf("editor","reviewer").forEach { actorRole(it,"PRINT_READER",setOf(CollectionConst.SELECT)) }
        AcquisitionDao["one"].proposedLevel=EnumRelicLevel.FIRST
        val print=CollectionPrintService(collections,scope,mock(CollectionPrintTemplateService::class.java),mock(LoanStatutoryService::class.java),receiptEvidence=AccessionReceiptEvidence(archives,mainFileReadPolicy))
        val draft=print.entryVoucherPrint("voucher")
        assertEquals(EnumRelicLevel.UNRATED,draft.collections[0].level)
        assertTrue(draft.fields.any { it.value?.contains("尚无新版实际核收原件")==true })
        assertFalse(draft.fields.any { it.label=="逐件核对数" })
        receiveAll()
        val received=print.entryVoucherPrint("voucher")
        assertTrue(received.fields.any { it.value?.contains("已预留，待正式登记")==true })
        assertTrue(received.fields.any { it.label.contains("清册标识 one-1") && it.value?.contains("身份相符")==true })
        assertTrue(received.fields.any { it.label.contains("清册影像 one-1") && it.fileReferences==listOf("photo") })
        assertTrue(received.fields.any { it.label.contains("实际签署原件") && it.fileReferences==listOf("sign") })
        approve(row())
        assertTrue(print.entryVoucherPrint("voucher").fields.any { it.value?.contains("正式登记：")==true })
    } }
    @Test fun `failed archive transaction leaves no receipt and timestamps round trip to exact milliseconds`()=fixture { db ->
        val value=receipt().copy(checkedAt=at.withNano(123000000),signedReceiptFileIds="missing")
        assertEquals(value,JsonUtils.fromJson(JsonUtils.toJson(value)!!,AccessionReceiptVo::class.java))
        assertThrows(BadRequestException::class.java) { transaction(db) { receipts.record("voucher",value) } }
        transaction(db) { assertEquals(0L,AccessionReceiptDao.count());assertEquals(0L,CollectionDao.count());assertEquals(EnumVoucherStatus.DRAFT,EntryVoucherDao["voucher"].voucherStatus) }
    }
    private fun notice(quantity:Int=1)=DesignatedNoticeVo(orgId="museum",authorityLevel=EnumAuthorityLevel.PROVINCE,authorityCode="province-ncha",authorityName="省文物局",documentNo="指定〔2026〕1号",issuedOn=acquired.minusDays(3),notifiedOn=acquired.minusDays(2),designatedReceiverName="本馆",senderKind=EnumDesignatedSender.ARCHAEOLOGY,senderName="考古单位",senderIdentity="sender-code",transferLegalBasis="真实考古移交及行政指定依据",documentFileIds="source",item=DesignatedItemVo("source-item-1","移交实物",EnumCollectionNature.OTHER,quantity,"件",EnumCompleteness.COMPLETE,"保存完好","photo",era="当代",texture="纸"))
    private fun assessment(fit:Boolean=true)=DesignatedAssessmentVo(acquired.minusDays(1).atTime(10,0),"officer","source","逐项核对本馆收藏任务和入藏标准",fit,listOf(DesignatedProfessionalOpinionVo("专业人员","评估单位","相关研究方向","source","实物真伪鉴定","价值评估",fit,"sign")),"sign")
    private fun custody(count:Int=1)=DesignatedCustodyVo(at.minusDays(1),"officer","store","keeper",setOf("原单位移交人"),(1..count).map {AccessionPieceVo("appointed-$it",at.minusDays(2),"原移交物身份特征$it","保存完好",true,true,"photo",actualQuantity=1,actualQuantityUnit="件")},"sign")
    private fun acceptance()=DesignatedAcceptanceVo(acquired,"source",at,"keeper","依据原指定决定及实际取得原件核对，后续办理入馆核收","sign")

    @Test fun `designated location choices exclude retired and incompatible storage` ()=fixture { db -> transaction(db) {
        login("officer", permission=CollectionConst.ACQUIRE)
        WarehouseDao.new("retired") { orgId="museum";name="已退役的旧库房";sceneType=EnumLocationScene.STORAGE;status=EnumContentStatus.DELETE }
        WarehouseDao.new("wrong-type") { orgId="museum";name="错误类型的保管位置";sceneType=EnumLocationScene.STORAGE;type=EnumWarehouseType.SHOWCASE }
        @Suppress("UNCHECKED_CAST")
        val choices=designated.context()["locations"] as List<Map<String,Any?>>
        val ids=choices.map { it["value"] }.toSet()
        assertTrue("store" in ids)
        assertFalse("retired" in ids)
        assertFalse("wrong-type" in ids)
        val id=appointed()
        for (badId in listOf("retired","wrong-type")) {
            val beforeRecords=DesignatedReceptionRecordDao.count()
            val beforeArchives=CollectionPermanentArchiveDao.count()
            assertThrows(BadRequestException::class.java) { designated.custody(id,custody().copy(warehouseId=badId)) }
            assertEquals(beforeRecords,DesignatedReceptionRecordDao.count())
            assertEquals(beforeArchives,CollectionPermanentArchiveDao.count())
        }
    } }
    private fun appointed(fit:Boolean=true,quantity:Int=1):String {
        CollectionOrgDao["museum"].ownership=EnumOwnership.STATE
        login("officer",permission=CollectionConst.ACQUIRE)
        val id=designated.create(notice(quantity));designated.assess(id,assessment(fit));return id
    }
    @Test fun `designated acceptance uses actual authority and assessment then follows real accession and registration without fake contracts`()=fixture {db->transaction(db) {
        val id=appointed();designated.custody(id,custody());login("keeper",permission=CollectionConst.ACQUIRE)
        val acq=designated.accept(id,acceptance())
        assertEquals(acq,designated.accept(id,acceptance()))
        val source=AcquisitionDao[acq]
        assertNull(source.flowInstanceId);assertNull(source.contractAttachmentIds);assertNull(compliance.case(acq))
        assertEquals(EnumSource.DESIGNATED,source.source);assertEquals(id,source.designatedReceptionId)
        assertEquals(AcquisitionPreparedSource(EnumCollectionNature.OTHER,acquired),compliance.requirePrepared(source))
        assertEquals(0L,CollectionDao.count(),"指定接收核对不等于入馆核收或正式登账")
        val voucher=vouchers.save(EntryVoucherVo(acquisitionIds=acq,basis="行政指定及取得原件",entryDate=at.toLocalDate(),handoverUser="handover",receiverUser="receiver",custodian="keeper",warehouseId="store",attachmentIds="sign",verifiedAcquisitionIds=acq))
        assertThrows(BadRequestException::class.java){vouchers.execute(voucher)}
        assertThrows(BadRequestException::class.java){receipts.record(voucher,receipt(acq))}
        receipts.record(voucher,receipt(acq).copy(pieces=custody().pieces))
        login("editor")
        vouchers.execute(voucher)
        val c=row(acq);assertEquals(EnumCollectionStatus.PENDING,c.lifeStatus);assertNull(c.registeredAt)
        assertEquals(EnumSource.DESIGNATED,c.source);assertEquals(acquired,c.acquireDate)
        login("editor")
        assertThrows(BadRequestException::class.java){catalog.submit(c.id.value)}
        catalog.save(c.id.value,CollectionCatalogMapper.fromCollection(c.toModel()).copy(categoryId="paper", classificationNo="C-1", dimensions=DimensionVo(height="12"), dimensionUnit="cm", weight="100", weightUnit="g", completeStatus="完好", textureCategory1="单一质地",textureCategory2="有机质/植物质材料",preserveState=EnumPreserve.A, feature="移交实物特征", coverImage="photo", registrationDetails=CollectionRegistrationDetailsVo(coverIsFrontView=true,weightExcludesAccessories=true,eraMeaning=EnumCollectionEraMeaning.MANUFACTURED,eraNotation=EnumCollectionEraNotation.GREGORIAN)))
        catalog.submit(c.id.value)
        login("reviewer")
        catalog.review(c.id.value,CollectionCatalogReviewVo(EnumCatalogStatus.APPROVED,"已核实指定接收及实际核收原件"))
        assertNotNull(c.registeredAt)
        login("officer",permission=CollectionConst.ACQUIRE)
        assertEquals(c.id.value,designated.detail(id)["collectionId"])
        assertTrue(CollectionPermanentArchiveDao.all().any {it.bizType==DesignatedReceptionEvidence.BIZ && it.bizId==id && it.phase=="READY"})
    }}
    @Test fun `unfit designated item must be reported to original authority and retains real custody through new recipient handover`()=fixture {db->transaction(db) {
        val id=appointed(false);designated.custody(id,custody())
        actorRole("officer","FILE_officer",setOf(CollectionConst.STORAGE))
        login("officer",permission=CollectionConst.STORAGE)
        val parent=institution.create(InstitutionSuccessionCaseVo("museum",EnumInstitutionSuccession.TERMINATION,"本馆","登记标识","机构决定",acquired,acquired,"source","真实非营利依据","source","source","keeper","liquidator","sign"))
        institution.capture(parent,InstitutionScopeCaptureVo(institution.detail(parent)["scanDigest"] as String,LocalDateTime.now(),"officer","本次逐库及未登记对象清点","source","sign"))
        val resolution=InstitutionItemResolutionVo("DESIGNATED:$id",EnumSuccessionResolution.DESIGNATED_REASSIGNMENT,id,LocalDateTime.now(),"officer","核实指定对象已经真实另行承接","sign")
        assertThrows(BadRequestException::class.java){institution.resolve(parent,resolution)}
        login("keeper",permission=CollectionConst.ACQUIRE)
        assertThrows(BadRequestException::class.java){designated.accept(id,acceptance())}
        login("officer",permission=CollectionConst.ACQUIRE)
        assertEquals("MUST_REFER",designated.detail(id)["state"])
        val referral=DesignatedReferralVo(at,"officer","province-ncha","报请1号","不符合本馆标准，报请另行指定","source","sign")
        assertThrows(BadRequestException::class.java){designated.refer(id,referral.copy(authorityCode="another"))}
        designated.refer(id,referral)
        assertEquals("AWAIT_AUTHORITY",designated.detail(id)["state"])
        val decision=DesignatedReassignmentVo(at.toLocalDate(),"officer","province-ncha","省文物局","另行指定2号","新接收单位","new-unit-code","指定接收单位","source-item-1","source")
        assertThrows(BadRequestException::class.java){designated.reassign(id,decision.copy(authorityCode="another"))}
        designated.reassign(id,decision)
        val handover=DesignatedSettlementVo(at.plusHours(1),"officer",EnumDesignatedHandover.FROM_THIS_MUSEUM,"新接收单位","new-unit-code",setOf("本馆实际交出人员"),setOf("新单位实际接收人员"),custody().pieces,"据原件确认实际承接完成","sign")
        assertThrows(BadRequestException::class.java){designated.settle(id,handover.copy(mode=EnumDesignatedHandover.DIRECT_FROM_SENDER))}
        assertThrows(BadRequestException::class.java){designated.settle(id,handover.copy(recipientIdentity="wrong"))}
        assertThrows(BadRequestException::class.java){designated.settle(id,handover.copy(pieces=handover.pieces.map {it.copy(pieceNo="unrelated")}))}
        val final=designated.settle(id,handover);assertEquals(final,designated.settle(id,handover))
        assertEquals("REASSIGNED_RECEIVED",designated.detail(id)["state"])
        assertNull(DesignatedReceptionDao[id].acquisitionId);assertEquals(0L,CollectionDao.count())
        assertThrows(BadRequestException::class.java){designated.custody(id,custody())}
        login("officer",permission=CollectionConst.STORAGE)
        assertEquals(false,institution.detail(parent)["scopeFresh"])
        assertThrows(BadRequestException::class.java){institution.resolve(parent,resolution)}
        institution.resolve(parent,resolution.copy(recordedAt=LocalDateTime.now()))
        @Suppress("UNCHECKED_CAST") val entries=institution.detail(parent,1,0)["items"] as List<Map<String,Any?>>
        val entry=entries.single {it["id"]==id}
        assertEquals(false,entry["currentPresent"]);assertEquals(true,entry["resolved"]);assertNotNull(entry["baseline"])
        assertEquals(2,institution.detail(parent)["pending"]) // 两条既有征集来源仍分别保留，未随指定接收完成而消失。
    }}
    @Test fun `direct reassignment needs authority decision and receiving evidence but does not invent museum possession`()=fixture {db->transaction(db) {
        val id=appointed(false)
        val handover=DesignatedSettlementVo(at.plusHours(1),"officer",EnumDesignatedHandover.DIRECT_FROM_SENDER,"新接收单位","new-unit-code",setOf("原移交人"),setOf("新单位实际接收人"),custody().pieces,"原移交单位已据行政决定直接交接","sign")
        assertThrows(BadRequestException::class.java){designated.settle(id,handover)}
        designated.refer(id,DesignatedReferralVo(at,"officer","province-ncha","报请1号","不符合标准","source","sign"))
        designated.reassign(id,DesignatedReassignmentVo(at.toLocalDate(),"officer","province-ncha","省文物局","另指定2号","新接收单位","new-unit-code","指定接收单位","source-item-1","source"))
        assertThrows(BadRequestException::class.java){designated.settle(id,handover.copy(mode=EnumDesignatedHandover.FROM_THIS_MUSEUM))}
        designated.settle(id,handover)
        assertNull(DesignatedReceptionEvidence.last(id,EnumDesignatedRecord.CUSTODY))
        assertEquals(0L,CollectionDao.count());assertEquals(0L,StorageMovementDao.count())
    }}
    @Test fun `designated partial custody and corrected custody preserve originals and cannot bypass required personal keeper confirmation`()=fixture {db->transaction(db) {
        val id=appointed(quantity=2)
        val first=designated.custody(id,custody(1))
        login("keeper",permission=CollectionConst.ACQUIRE)
        assertThrows(BadRequestException::class.java){designated.accept(id,acceptance())}
        login("officer",permission=CollectionConst.ACQUIRE)
        assertThrows(BadRequestException::class.java){designated.custody(id,custody(2))}
        designated.custody(id,custody(2).copy(previousId=first,correctionReason="实际收齐并核对",correctionFileIds="correction"))
        assertEquals(2,DesignatedReceptionEvidence.rows(id).count {it.kind==EnumDesignatedRecord.CUSTODY})
        assertThrows(BadRequestException::class.java){designated.accept(id,acceptance())}
        login("keeper",permission=CollectionConst.ACQUIRE)
        designated.accept(id,acceptance())
        verify(users,atLeastOnce()).requireRole("keeper","museum",CollectionConst.ROLE_STORAGE_HEAD)
    }}
    @Test fun `designated cases reject non state recipients duplicate identity foreign scope and ordinary acquisition bypass`()=fixture {db->transaction(db) {
        login("officer",permission=CollectionConst.ACQUIRE)
        assertThrows(BadRequestException::class.java){designated.create(notice())}
        CollectionOrgDao["museum"].ownership=EnumOwnership.NON_STATE
        assertThrows(BadRequestException::class.java){designated.create(notice())}
        CollectionOrgDao["museum"].ownership=EnumOwnership.STATE
        assertThrows(BadRequestException::class.java){designated.create(notice().copy(authorityLevel=EnumAuthorityLevel.COMPETENT))}
        val id=designated.create(notice());assertEquals(id,designated.create(notice()))
        assertThrows(BadRequestException::class.java){designated.create(notice().copy(item=notice().item.copy(name="替换对象")))}
        assertThrows(BadRequestException::class.java){acquisitions.save(AcquisitionVo(source=EnumSource.DESIGNATED,name="绕过指定",quantity=1,unit="件"))}
        val fake=AcquisitionDao.new {orgId="museum";name="没有原件的指定对象";source=EnumSource.DESIGNATED;quantity=1;unit="件";flowStatus=EnumFlowStatus.PASS}
        assertThrows(BadRequestException::class.java){compliance.requirePrepared(fake)}
        login("foreign","other",CollectionConst.ACQUIRE)
        assertThrows(BadRequestException::class.java){designated.detail(id)}
        login("reader",permission=CollectionConst.LEDGER_CREATE)
        assertThrows(BadRequestException::class.java){designated.detail(id)}
    }}
    @Test fun `designated permanent originals and prepared source cannot be replaced after actual keeper acceptance`()=fixture {db->transaction(db) {
        val id=appointed();designated.custody(id,custody());login("keeper",permission=CollectionConst.ACQUIRE)
        val acq=designated.accept(id,acceptance())
        assertThrows(BadRequestException::class.java){compliance.requireEditable(AcquisitionDao[acq])}
        assertThrows(BadRequestException::class.java){designated.refer(id,DesignatedReferralVo(at,"keeper","province-ncha","变更","替换路径","source","sign"))}
        AcquisitionDao[acq].acquireDate=acquired.plusDays(1)
        assertThrows(BadRequestException::class.java){compliance.requirePrepared(AcquisitionDao[acq])}
        assertThrows(BadRequestException::class.java){designated.detail(id)}
    }}

    @Test fun `designated assessment corrections preserve originals and reject unsupported conclusions and dates`()=fixture {db->transaction(db) {
        CollectionOrgDao["museum"].ownership=EnumOwnership.STATE;login("officer",permission=CollectionConst.ACQUIRE)
        val id=designated.create(notice())
        assertThrows(BadRequestException::class.java){designated.assess(id,assessment().copy(opinions=emptyList()))}
        assertThrows(BadRequestException::class.java){designated.assess(id,assessment().copy(assessedAt=notice().notifiedOn.minusDays(1).atStartOfDay()))}
        assertThrows(BadRequestException::class.java){designated.assess(id,assessment().copy(opinions=listOf(assessment().opinions.single().copy(meetsStandards=false))))}
        assertThrows(BadRequestException::class.java){designated.assess(id,assessment().copy(opinions=assessment().opinions+assessment().opinions))}
        val old=designated.assess(id,assessment(false))
        assertThrows(BadRequestException::class.java){designated.assess(id,assessment())}
        val corrected=assessment().copy(previousId=old,correctionReason="专业人员订正原鉴定误记",correctionFileIds="correction")
        val next=designated.assess(id,corrected);assertEquals(next,designated.assess(id,corrected))
        assertEquals(assessment(false),DesignatedReceptionEvidence.value<DesignatedAssessmentVo>(DesignatedReceptionRecordDao[old]))
        assertThrows(BadRequestException::class.java){designated.assess(id,corrected.copy(standardsConclusion="旧版本另行替换"))}
        assertEquals(2,DesignatedReceptionEvidence.rows(id).count {it.kind==EnumDesignatedRecord.ASSESSMENT})
        assertThrows(BadRequestException::class.java){designated.custody(id,custody().copy(warehouseId="other-store"))}
        designated.custody(id,custody());login("keeper",permission=CollectionConst.ACQUIRE)
        assertThrows(BadRequestException::class.java){designated.accept(id,acceptance().copy(actualAcquiredOn=notice().issuedOn.minusDays(1)))}
        assertThrows(BadRequestException::class.java){designated.accept(id,acceptance().copy(actualAcquiredOn=assessment().assessedAt.toLocalDate().minusDays(1)))}
        assertThrows(BadRequestException::class.java){designated.accept(id,acceptance().copy(confirmedAt=custody().checkedAt.minusHours(1)))}
        val acq=designated.accept(id,acceptance())
        assertThrows(BadRequestException::class.java){acquisitions.submit(acq)}
        assertThrows(BadRequestException::class.java){acquisitions.cancel(acq)}
        val event=io.hizhengfu.cloud.flow.domain.FlowReactivatedEvent("fake","old","旧流程",acq,CollectionConst.BIZ_ACQUISITION,"COMPLETED","review","复核",null,"keeper","保管员","keeper","保管员",emptyMap())
        assertThrows(BadRequestException::class.java){acquisitions.onFlowReactivated(AcquisitionDao[acq],event)}
        assertEquals(EnumFlowStatus.PASS,AcquisitionDao[acq].flowStatus);assertNull(AcquisitionDao[acq].flowInstanceId)
    }}
    @Test fun `designated missing permanent files roll back notice and actual acceptance without leaving ready acquisition`()=fixture {db->
        transaction(db){CollectionOrgDao["museum"].ownership=EnumOwnership.STATE}
        login("officer",permission=CollectionConst.ACQUIRE)
        assertThrows(BadRequestException::class.java){transaction(db){designated.create(notice().copy(documentFileIds="missing"))}}
        transaction(db){assertEquals(0L,DesignatedReceptionDao.count());assertFalse(CollectionPermanentArchiveDao.all().any {it.bizType==DesignatedReceptionEvidence.BIZ})}
        val id=transaction(db){val created=appointed();designated.custody(created,custody());created}
        login("keeper",permission=CollectionConst.ACQUIRE)
        val v=acceptance().copy(confirmedAt=at.withNano(123000000),signedFileIds="missing")
        assertEquals(v,JsonUtils.fromJson(JsonUtils.toJson(v)!!,DesignatedAcceptanceVo::class.java))
        assertThrows(BadRequestException::class.java){transaction(db){designated.accept(id,v)}}
        transaction(db){
            assertNull(DesignatedReceptionDao[id].acquisitionId)
            assertNull(DesignatedReceptionEvidence.last(id,EnumDesignatedRecord.ACCEPTANCE))
            assertFalse(AcquisitionDao.all().any {it.source==EnumSource.DESIGNATED})
            val acq=designated.accept(id,v.copy(signedFileIds="sign"));assertEquals(id,AcquisitionDao[acq].designatedReceptionId)
            val assessment=DesignatedReceptionEvidence.last(id,EnumDesignatedRecord.ASSESSMENT)!!
            assessment.content=DesignatedReceptionEvidence.json(assessment().copy(standardsConclusion="篡改原件"))
            assertThrows(BadRequestException::class.java){designated.detail(id)}
        }
    }

}
