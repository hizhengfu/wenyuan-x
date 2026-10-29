package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class PaperLedgerPersistenceTest {
    private val at=LocalDateTime.of(2026,1,2,12,0,0,123000000)
    private val scope=CollectionScopeResolver("multi", "")
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val books=PaperLedgerService(scope,users,archives)
    private val retainedLedgers=CollectionRetainedLedgerService(scope,archives)
    private val reconciliation=LedgerReconciliationService(scope,users,books,archives,retainedLedgers)
    @BeforeEach fun setup() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context);login()
    }
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun login(org: String="museum", permissions: List<String> = listOf(CollectionConst.LEDGER_FINANCE)) {
        val user=SecurityUser(id="officer",name="经办",username="officer",password="",groupId=org,groupIds=setOf(org),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=permissions.map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    private fun fixture(block:(Database,String)->Unit) {
        val path=java.nio.file.Files.createTempFile("paper-ledger-",".sqlite")
        val db=Database.connect("jdbc:sqlite:$path",driver="org.sqlite.JDBC")
        try {
            val book=transaction(db) {
                originalReaderSchema();originalReader("officer",CollectionConst.LEDGER_FINANCE)
                actorRole("officer","PAPER_WRITER",setOf(CollectionConst.LEDGER_CORRECT))
                GroupDao.new("target") { name="他馆";type='1';parentId="0";parentIds="0," }
                SchemaUtils.create(CollectionOrgs,Collections,CollectionRetainedLedgers,PaperLedgerBooks,PaperLedgerCustodies,LedgerReconciliations,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
                CollectionOrgDao.new("museum"){name="本馆";code="M"};CollectionOrgDao.new("target"){name="他馆";code="T"}
                listOf("contents","custody","handover","handling","sign","paper","finance","revision").forEach { id -> FileDao.new(id){name="$id.pdf";this.path="/files/$id.pdf";hash=id;size=100;ext="pdf"} }
                CollectionDao.new("one"){orgId="museum";totalRegNo="A1";name="铜器";quantity=2;unit="件";registeredAt=at.minusYears(1);nature=EnumCollectionNature.RELIC;lifeStatus=EnumCollectionStatus.IN_STORE}
                books.register("museum",book())
            }
            block(db,book)
        } finally { java.nio.file.Files.deleteIfExists(path) }
    }
    private fun custody()=PaperLedgerCustodyVo(at,"ledger","档案室01柜",EnumPaperLedgerCondition.GOOD,"纸张完整、装订完好","custody")
    private fun book()=PaperLedgerBookVo("总账01","纸质总登记账第一册",at.toLocalDate().minusYears(1),200,"A1至A100原目录","contents",custody())
    private fun entry()=LedgerAccountEntryVo("A1","铜器",2,"件")
    private fun report(book:String)=LedgerReconciliationVo("核对01",at.toLocalDate().minusMonths(1),at.toLocalDate(),at,"ledger","financeActor","本次第1册第1页文物资源资产",setOf(book),"sign","paper","finance",at.toLocalDate().plusMonths(1),listOf(LedgerAccountComparisonVo(1,book,"第1页","财务资产卡01","one",entry(),entry())))
    @Test fun `book custody and finance originals require exact frozen archive under current duty`()=fixture { db,bookId -> transaction(db) {
        val first=PaperLedgerCustodyDao.all().single {it.bookId==bookId}
        val secondBook=books.register("museum",book().copy(bookNo="总账02"))
        val financeId=reconciliation.record("museum",report(bookId))
        fun archive(id:String,phase:String)=CollectionPermanentArchiveDao.all().single {it.bizId==id && it.phase==phase}.id.value
        val registered=archive(bookId,"REGISTERED")
        val custodyArchive=archive(first.id.value,"RECORDED")
        val financeArchive=archive(financeId,"CHECKED")
        assertDoesNotThrow {books.requireArchivedOriginal("museum",bookId,registered,"contents")}
        assertDoesNotThrow {books.requireArchivedOriginal("museum",bookId,custodyArchive,"custody")}
        assertDoesNotThrow {reconciliation.requireArchivedOriginal("museum",financeId,financeArchive,"sign")}
        for((archiveId,file) in listOf(custodyArchive to "contents",archive(secondBook,"REGISTERED") to "contents",financeArchive to "sign"))
            assertThrows(BadRequestException::class.java) {books.requireArchivedOriginal("museum",bookId,archiveId,file)}
        assertThrows(BadRequestException::class.java) {reconciliation.requireArchivedOriginal("museum",financeId,registered,"contents")}
        assertThrows(BadRequestException::class.java) {reconciliation.requireArchivedOriginal("target",financeId,financeArchive,"sign")}
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) {books.requireArchivedOriginal("museum",bookId,registered,"contents")}
        assertThrows(BadRequestException::class.java) {reconciliation.requireArchivedOriginal("museum",financeId,financeArchive,"sign")}
    } }
    @Test fun `change page reference requires archived local book and a real page within current extent`()=fixture { db,bookId -> transaction(db) {
        assertEquals(PaperLedgerService.PageReference(bookId,"总账01",200,200),books.requirePageReference("museum",bookId,200))
        for(page in listOf(0,201))assertThrows(BadRequestException::class.java){books.requirePageReference("museum",bookId,page)}
        assertThrows(BadRequestException::class.java){books.requirePageReference("target",bookId,1)}
        assertThrows(BadRequestException::class.java){books.requirePageReference("museum",null,1)}
        PaperLedgerBookDao[bookId].content=PaperLedgerBookDao[bookId].content.copy(pageCount=999)
        assertThrows(BadRequestException::class.java){books.requirePageReference("museum",bookId,1)}
    } }
    @ParameterizedTest @ValueSource(strings=["book", "bookNumber", "custody", "financial", "financialNumber", "systemReference", "bookReference"])
    fun `paper and financial lists refuse business rows diverging from frozen originals`(changed: String)=fixture { db,bookId -> transaction(db) {
        val financialId=reconciliation.record("museum",report(bookId))
        assertEquals(1,books.list("museum").size)
        assertEquals(1,reconciliation.list("museum").size)
        when(changed) {
            "book" -> PaperLedgerBookDao[bookId].content=PaperLedgerBookDao[bookId].content.copy(title="被改写的账册")
            "bookNumber" -> PaperLedgerBookDao[bookId].bookNo="被改写的册号"
            "custody" -> PaperLedgerCustodyDao.all().single().let { it.content=it.content.copy(storageLocation="被改写的库位") }
            "financial" -> LedgerReconciliationDao[financialId].let { it.content=it.content.copy(scopeDescription="被改写的核对范围") }
            "financialNumber" -> LedgerReconciliationDao[financialId].referenceNo="被改写的核对编号"
            "systemReference" -> LedgerReconciliationDao[financialId].systemReferences=emptyList()
            else -> LedgerReconciliationDao[financialId].bookReferences=LedgerBookReferencesVo(emptyMap())
        }
        if(changed=="book" || changed=="bookNumber" || changed=="custody") assertThrows(BadRequestException::class.java) { books.list("museum") }
        else assertThrows(BadRequestException::class.java) { reconciliation.list("museum") }
    } }
    @ParameterizedTest @ValueSource(strings=["REGISTERED", "RECORDED", "CHECKED"])
    fun `paper and financial lists refuse a missing frozen stage`(phase: String)=fixture { db,bookId -> transaction(db) {
        reconciliation.record("museum",report(bookId))
        assertEquals(1,books.list("museum").size)
        assertEquals(1,reconciliation.list("museum").size)
        val archive=CollectionPermanentArchiveDao.all().single { it.phase==phase }
        CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.forEach { it.delete() }
        archive.delete()
        if(phase=="CHECKED") assertThrows(BadRequestException::class.java) { reconciliation.list("museum") }
        else assertThrows(BadRequestException::class.java) { books.list("museum") }
    } }
    @ParameterizedTest @ValueSource(strings=["altered", "missing"])
    fun `financial reconciliation refuses an unverified earlier paper custody`(fault: String)=fixture { db,bookId -> transaction(db) {
        books.custody("museum",bookId,custody().copy(checkedAt=at.plusDays(1),reason="后续保管复核"))
        val first=PaperLedgerCustodyDao.all().minBy { it.version }
        if(fault=="altered") first.content=first.content.copy(storageLocation="被改写的旧保管位置")
        else {
            val archive=CollectionPermanentArchiveDao.all().single { it.bizId==first.id.value && it.phase=="RECORDED" }
            CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.forEach { it.delete() }
            archive.delete()
        }
        val archiveCount=CollectionPermanentArchiveDao.count()
        assertThrows(BadRequestException::class.java) { reconciliation.record("museum",report(bookId)) }
        assertEquals(0L,LedgerReconciliationDao.count())
        assertEquals(archiveCount,CollectionPermanentArchiveDao.count())
    } }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("register,revoked", "register,disabled", "register,moved", "register,finance", "custody,revoked", "custody,disabled", "custody,moved", "custody,finance")
    fun `paper writes require current dedicated duty`(operation: String, change: String)=fixture { db,id -> transaction(db) {
        when(change) {
            "revoked" -> RolePermissions.deleteAll()
            "disabled" -> UserDao["officer"].enabled=false
            "moved" -> UserDao["officer"].groupId="target"
            "finance" -> { RolePermissions.deleteAll();actorRole("officer","FINANCE_ONLY",setOf(CollectionConst.LEDGER_FINANCE)) }
        }
        fun originals()=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }.sortedBy { it.first }
        val before=originals();val booksBefore=PaperLedgerBookDao.count();val custodyBefore=PaperLedgerCustodyDao.count()
        assertThrows(BadRequestException::class.java){
            if(operation=="register") books.register("museum",book()) else books.custody("museum",id,custody())
        }
        assertEquals(before,originals());assertEquals(booksBefore,PaperLedgerBookDao.count());assertEquals(custodyBefore,PaperLedgerCustodyDao.count())
    } }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("revoked,false", "revoked,true", "disabled,false", "disabled,true", "moved,false", "moved,true")
    fun `financial writes reject stale login after current eligibility changes`(change: String, repeat: Boolean)=fixture { db,id -> transaction(db) {
        val value=report(id)
        if(repeat) reconciliation.record("museum",value)
        when(change) {
            "revoked" -> RolePermissions.deleteAll()
            "disabled" -> UserDao["officer"].enabled=false
            "moved" -> UserDao["officer"].groupId="target"
        }
        val before=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }
        val count=LedgerReconciliationDao.count()
        assertThrows(BadRequestException::class.java){reconciliation.record("museum",value)}
        assertEquals(count,LedgerReconciliationDao.count())
        assertEquals(before,CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) })
    } }
    @Test fun `current revocation blocks paper ledger originals despite old login`()=fixture { db,_ -> transaction(db) {
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){books.list("museum")}
    } }
    @Test fun `finance choices preserve original museum ledger and require current scope`()=fixture { db,_ -> transaction(db) {
        assertEquals("CURRENT_MUSEUM", reconciliation.collections("museum").single()["source"])
        val collection=CollectionDao["one"]
        val frozen=CollectionRetainedLedgerSnapshot(collection.toModel(),
            identity=CollectionRetainedLedgerIdentity("museum","one","choice-transfer","target"))
        val retained=CollectionRetainedLedgerDao.new {
            orgId="museum";collectionId="one";transferId="choice-transfer";totalRegNo="A1";name="铜器";targetOrgId="target";archiveId=""
            snapshot=frozen
        }
        retained.archiveId=archives.seal("museum","COLLECTION_RETAINED_LEDGER",retained.id.value,"TRANSFERRED",frozen,null)
        collection.orgId="target"
        assertEquals(mapOf("id" to "one", "totalRegNo" to "A1", "name" to "铜器", "source" to "RETAINED_SOURCE_MUSEUM"), reconciliation.collections("museum").single())
        assertThrows(BadRequestException::class.java) { reconciliation.collections("target") }
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { reconciliation.collections("museum") }
    } }
    @Test fun `finance choices and recording reject a retained ledger diverging from its archive`()=fixture { db,bookId -> transaction(db) {
        val collection=CollectionDao["one"]
        val original=collection.toModel()
        val frozen=CollectionRetainedLedgerSnapshot(original,
            identity=CollectionRetainedLedgerIdentity("museum","one","finance-transfer","target"))
        val retained=CollectionRetainedLedgerDao.new {
            orgId="museum";collectionId="one";transferId="finance-transfer";totalRegNo="A1";name="铜器"
            targetOrgId="target";archiveId="";snapshot=frozen
        }
        retained.archiveId=archives.seal("museum","COLLECTION_RETAINED_LEDGER",retained.id.value,"TRANSFERRED",frozen,null)
        collection.orgId="target"
        assertEquals("A1",reconciliation.collections("museum").single()["totalRegNo"])
        retained.name="被改写的原馆名称"
        retained.snapshot=frozen.copy(collection=original.copy(name="被改写的原馆名称"))
        val archiveCount=CollectionPermanentArchiveDao.count()
        assertThrows(BadRequestException::class.java) { reconciliation.collections("museum") }
        assertThrows(BadRequestException::class.java) { reconciliation.record("museum",report(bookId)) }
        assertEquals(0L,LedgerReconciliationDao.count())
        assertEquals(archiveCount,CollectionPermanentArchiveDao.count())
    } }
    @Test fun `current revocation blocks financial reconciliation originals despite old login`()=fixture { db,id -> transaction(db) {
        reconciliation.record("museum",report(id));RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){reconciliation.list("museum")}
    } }
    @Test fun `foreign custody cannot appear in a local permanent paper ledger`()=fixture { db,_ -> transaction(db) {
        PaperLedgerCustodyDao.all().single().orgId="target"
        assertThrows(BadRequestException::class.java){books.list("museum")}
    } }

    @Test fun `current cross grant permits historical books but cannot replace finance duty or single museum boundary`()=fixture { db,id -> transaction(db) {
        reconciliation.record("museum",report(id))
        CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        assertThrows(BadRequestException::class.java){books.list("museum")}
        assertThrows(BadRequestException::class.java){reconciliation.list("museum")}
        actorRole("officer","CURRENT_CROSS",setOf(CollectionConst.CROSS_ORG))
        assertEquals(1,books.list("museum").size);assertEquals(1,reconciliation.list("museum").size)
        val single=CollectionScopeResolver("single","target")
        assertThrows(BadRequestException::class.java){PaperLedgerService(single,users,archives).list("museum")}
        assertThrows(BadRequestException::class.java){LedgerReconciliationService(single,users,books,archives,CollectionRetainedLedgerService(single,archives)).list("museum")}
        RolePermissions.deleteAll();actorRole("officer","GENERAL_READ",setOf(CollectionConst.SELECT,CollectionConst.CROSS_ORG))
        assertEquals(1,books.list("museum").size)
        assertThrows(BadRequestException::class.java){reconciliation.list("museum")}
    } }
    @Test fun `disabled reader cannot use ledger or financial originals`()=fixture { db,id -> transaction(db) {
        reconciliation.record("museum",report(id));UserDao["officer"].enabled=false
        assertThrows(BadRequestException::class.java){books.list("museum")}
        assertThrows(BadRequestException::class.java){reconciliation.list("museum")}
    } }
    @Test fun `metadata lookup verifies existing correction without resealing and preserves frozen financial references`()=fixture { db,id -> transaction(db) {
        val financial=reconciliation.record("museum",report(id))
        val corrected=PaperLedgerMetadataVo("订正册01","订正账册",book().openedOn,210,"实际范围","contents")
        books.custody("museum",id,custody().copy(checkedAt=at.plusDays(1),reason="资料订正",correctedMetadata=corrected,metadataCorrectionFileIds="revision"))
        val checking=spy(archives);val reader=PaperLedgerService(scope,users,checking)
        val before=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }
        assertEquals("订正册01",reader.list("museum").single()["bookNo"])
        assertTrue(mockingDetails(checking).invocations.none { it.method.name.startsWith("seal") })
        assertEquals(before,CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) })
        CollectionDao["one"].orgId="target";CollectionDao["one"].name="调入后的名称"
        assertEquals(financial,reconciliation.list("museum").single()["id"])
        val correction=PaperLedgerCustodyDao.all().maxBy { it.version }
        correction.content=correction.content.copy(correctedMetadata=corrected.copy(title="篡改"))
        assertThrows(BadRequestException::class.java){reader.list("museum")}
    } }

    @Test fun `actual paper register is immutable permanent and independent of collection state`()=fixture { db,id -> transaction(db) {
        assertEquals(id,books.register("museum",book()));assertEquals(1L,PaperLedgerBookDao.count());assertEquals(1L,PaperLedgerCustodyDao.count())
        assertEquals("PERMANENT",books.list("museum").single()["retention"])
        assertThrows(BadRequestException::class.java){books.register("museum",book().copy(title="覆盖原账"))}
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("contents")}
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
    } }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `repeated registration rejects missing or altered original without rebuilding it`(altered:Boolean)=fixture { db,id -> transaction(db) {
        val original=CollectionPermanentArchiveDao.all().single { it.bizId==id && it.phase=="REGISTERED" }
        if(altered) original.snapshot="{}" else original.delete()
        val remaining=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }
        assertThrows(BadRequestException::class.java) { books.register("museum",book()) }
        assertEquals(remaining,CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) })
        assertEquals(book(),PaperLedgerBookDao[id].content)
        assertEquals(1L,PaperLedgerCustodyDao.count())
    } }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `repeated custody rejects missing or altered custody original without rebuilding it`(altered:Boolean)=fixture { db,id -> transaction(db) {
        val value=custody().copy(checkedAt=at.plusDays(1),reason="例行核查",signedCustodyFileIds="revision")
        val custodyId=books.custody("museum",id,value)
        val original=CollectionPermanentArchiveDao.all().single { it.bizId==custodyId && it.phase=="RECORDED" }
        if(altered) original.snapshot="{}" else original.delete()
        val remaining=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }
        assertThrows(BadRequestException::class.java) { books.custody("museum",id,value) }
        assertEquals(remaining,CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) })
        assertEquals(value,PaperLedgerCustodyDao[custodyId].content)
        assertEquals(2L,PaperLedgerCustodyDao.count())
    } }
    @Test fun `paper book metadata correction preserves original and all number aliases`()=fixture { db,id -> transaction(db) {
        val original=book();val metadata=PaperLedgerMetadataVo("实册01","核实后的账册名称",original.openedOn,210,"实际目录范围","contents")
        val financialId=reconciliation.record("museum",report(id))
        val update=custody().copy(checkedAt=at.plusDays(1),reason="原登记册号和页数误录，核实原件订正",correctedMetadata=metadata)
        assertThrows(BadRequestException::class.java){books.custody("museum",id,update)}
        books.custody("museum",id,update.copy(metadataCorrectionFileIds="revision"))
        assertEquals(original,PaperLedgerBookDao[id].content);assertEquals(metadata,books.list("museum").single()["book"])
        assertEquals("实册01",books.list("museum").single()["bookNo"])
        assertEquals("总账01",LedgerReconciliationDao[financialId].bookReferences.items[id]!!.bookNo)
        assertThrows(BadRequestException::class.java){books.register("museum",original.copy(bookNo="实册01"))}
        assertThrows(BadRequestException::class.java){books.register("museum",original.copy(title="用旧号新建同一纸账"))}
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("revision")}
    } }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `repeated financial reconciliation rejects missing or altered original`(altered:Boolean)=fixture { db,id -> transaction(db) {
        val value=report(id)
        val reportId=reconciliation.record("museum",value)
        val original=CollectionPermanentArchiveDao.all().single { it.bizId==reportId && it.phase=="CHECKED" }
        if(altered) original.snapshot="{}" else original.delete()
        val remaining=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }
        assertThrows(BadRequestException::class.java) { reconciliation.record("museum",value) }
        assertEquals(remaining,CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) })
        assertEquals(value,LedgerReconciliationDao[reportId].content)
        assertEquals(1L,LedgerReconciliationDao.count())
    } }
    @Test fun `metadata cannot take another book number or erase custody original`()=fixture { db,id -> transaction(db) {
        val another=book().copy(bookNo="总账02");books.register("museum",another)
        val metadata=PaperLedgerMetadataVo(another.bookNo,"第二册",another.openedOn,200,"第二册","contents")
        val value=custody().copy(checkedAt=at.plusDays(1),reason="错误关联",correctedMetadata=metadata,metadataCorrectionFileIds="revision")
        assertThrows(BadRequestException::class.java){books.custody("museum",id,value)}
        assertEquals(2L,PaperLedgerCustodyDao.count());assertEquals("总账01",books.currentMetadata(PaperLedgerBookDao[id]).bookNo)
    } }
    @Test fun `paper custody handover requires old keeper and new originals without erasing history`()=fixture { db,id -> transaction(db) {
        val next=custody().copy(checkedAt=at.plusDays(1),keeperUserId="newLedger",reason="人员实际交接")
        assertThrows(BadRequestException::class.java){books.custody("museum",id,next)}
        assertThrows(BadRequestException::class.java){books.custody("museum",id,next.copy(fromKeeperUserId="notOld",handoverFileIds="handover"))}
        val value=next.copy(fromKeeperUserId="ledger",handoverFileIds="handover")
        val receipt=books.custody("museum",id,value);assertEquals(receipt,books.custody("museum",id,value))
        assertEquals(2L,PaperLedgerCustodyDao.count());assertEquals(custody(),PaperLedgerCustodyDao.all().minBy { it.version }.content)
        verify(users,atLeastOnce()).requireRole("newLedger","museum",CollectionConst.ROLE_LEDGER_CUSTODIAN)
    } }
    @Test fun `paper damage and later recovery retain handling originals and overdue facts`()=fixture { db,id -> transaction(db) {
        val issue=custody().copy(checkedAt=at.plusDays(1),condition=EnumPaperLedgerCondition.ISSUE,conditionDescription="受潮",reason="实际检查")
        assertThrows(BadRequestException::class.java){books.custody("museum",id,issue)}
        val actual=issue.copy(handlingDescription="移至干燥保存柜并请专业人员处理",handlingFileIds="handling",nextCheckOn=at.toLocalDate().plusDays(3))
        books.custody("museum",id,actual)
        val restored=custody().copy(checkedAt=at.plusDays(4),reason="处理后复核")
        assertThrows(BadRequestException::class.java){books.custody("museum",id,restored)}
        books.custody("museum",id,restored.copy(handlingDescription="按处理原件复核恢复",handlingFileIds="handling"))
        val versions=books.list("museum").single()["custodies"] as List<Map<String,Any?>>
        assertEquals(true,versions[1]["overdue"]);assertEquals(EnumPaperLedgerCondition.ISSUE,PaperLedgerCustodyDao.all().first { it.version==2 }.content.condition)
    } }
    @Test fun `paper custody checks scope real duties and future dates`()=fixture { db,id -> transaction(db) {
        assertThrows(BadRequestException::class.java){books.custody("museum",id,custody().copy(checkedAt=LocalDateTime.now().plusDays(1),reason="虚填"))}
        doThrow(BadRequestException("库房兼岗")).`when`(users).requireRole("keeper","museum",CollectionConst.ROLE_LEDGER_CUSTODIAN)
        assertThrows(BadRequestException::class.java){books.custody("museum",id,custody().copy(keeperUserId="keeper",reason="不相容岗位",fromKeeperUserId="ledger",handoverFileIds="handover"))}
        UserDao["officer"].groupId="target";login("target");assertThrows(BadRequestException::class.java){books.list("museum")};assertThrows(BadRequestException::class.java){books.custody("target",id,custody())}
    } }
    @Test fun `financial comparison computes mismatches and preserves original when later reconciled`()=fixture { db,book -> transaction(db) {
        val original=report(book);val wrong=original.rows.single().copy(financial=entry().copy(quantity=1,unit="套"))
        assertThrows(BadRequestException::class.java){reconciliation.record("museum",original.copy(rows=listOf(wrong)))}
        val first=original.copy(rows=listOf(wrong.copy(issueExplanation="逐件核对差异原因",plannedAction="核实财务资产卡与纸账后依程序订正")))
        val firstId=reconciliation.record("museum",first);assertEquals(firstId,reconciliation.record("museum",first))
        assertEquals(listOf("数量不一致","计量单位不一致"),LedgerAccountComparison.differences(LedgerReconciliationDao[firstId].content.rows.single()))
        val checked=original.copy(checkedAt=at.plusDays(3),replacementReason="财务卡实际订正后双方复核",replacementFileIds="revision")
        val second=reconciliation.record("museum",checked);assertEquals(2,LedgerReconciliationDao[second].version)
        assertTrue(LedgerAccountComparison.differences(LedgerReconciliationDao[second].content.rows.single()).isEmpty());assertEquals(first,LedgerReconciliationDao[firstId].content)
        assertEquals(2,CollectionDao["one"].quantity)
    } }
    @Test fun `review cannot silently drop discrepancy lines or replace checked book scope`()=fixture { db,book -> transaction(db) {
        val first=report(book);reconciliation.record("museum",first)
        val revision=first.copy(checkedAt=at.plusDays(1),replacementReason="发现误录行",replacementFileIds="revision",rows=emptyList(),emptyScopeReason="原行属于其他核对期间")
        assertThrows(BadRequestException::class.java){reconciliation.record("museum",revision)}
        assertThrows(BadRequestException::class.java){reconciliation.record("museum",revision.copy(periodTo=at.toLocalDate().plusDays(1)))}
        val id=reconciliation.record("museum",revision.copy(omittedLineReasons=mapOf(1 to "原行误录，本次核对范围为空，附双方订正证明")))
        assertEquals(setOf(1),LedgerReconciliationDao[id].content.omittedLineReasons.keys)
        assertEquals(2L,LedgerReconciliationDao.count())
    } }
    @Test fun `recording captures separate system reference without inventing past data`()=fixture { db,book -> transaction(db) {
        CollectionDao["one"].name="现时订正名称"
        val id=reconciliation.record("museum",report(book))
        val row=LedgerReconciliationDao[id];assertEquals("铜器",row.content.rows.single().paper!!.name)
        assertEquals("现时订正名称",row.systemReferences.single().item.name);assertTrue(row.systemReferences.single().capturedAt.isAfter(at));assertEquals("CURRENT_AT_RECORDING",row.systemReferences.single().source)
        CollectionDao["one"].name="后续名称";assertEquals("现时订正名称",row.systemReferences.single().item.name)
    } }
    @Test fun `unlinked historical lines stay visible and foreign system links are rejected`()=fixture { db,book -> transaction(db) {
        val value=report(book);val unlinked=value.copy(rows=value.rows.map { it.copy(collectionId=null) })
        reconciliation.record("museum",unlinked)
        assertEquals(listOf(1),reconciliation.list("museum").single()["unlinkedLines"])
        CollectionDao["one"].orgId="target"
        assertThrows(BadRequestException::class.java){reconciliation.record("museum",value.copy(referenceNo="核对02"))}
        login("target");assertThrows(BadRequestException::class.java){reconciliation.record("target",value)}
    } }
    @Test fun `financial originals require explicit permission and actual separate duty actors`()=fixture { db,book -> transaction(db) {
        RolePermissions.deleteAll();actorRole("officer","GENERAL",setOf(CollectionConst.SELECT,CollectionConst.CROSS_ORG))
        login(permissions=listOf(CollectionConst.SELECT,CollectionConst.CROSS_ORG))
        assertThrows(BadRequestException::class.java){reconciliation.list("museum")};assertThrows(BadRequestException::class.java){reconciliation.record("museum",report(book))}
        login();assertThrows(BadRequestException::class.java){reconciliation.record("museum",report(book).copy(financeUserId="ledger"))}
        doThrow(BadRequestException("未承担实际财务职责")).`when`(users).requireRole("financeActor","museum",CollectionConst.ROLE_FINANCE)
        assertThrows(BadRequestException::class.java){reconciliation.record("museum",report(book))}
    } }
    @Test fun `negative counts missing counterpart and empty rows remain evidence cases`()=fixture { db,book -> transaction(db) {
        val value=report(book);val line=value.rows.single().copy(paper=null,financial=entry().copy(quantity=-1))
        assertEquals(listOf("纸质总账缺对应记录","账面数量为负，须核实"),LedgerAccountComparison.differences(line))
        assertThrows(BadRequestException::class.java){reconciliation.record("museum",value.copy(rows=emptyList()))}
        reconciliation.record("museum",value.copy(rows=listOf(line.copy(issueExplanation="财务数据异常并缺纸账",plannedAction="核实来源依法处理"))))
        assertEquals(2,(reconciliation.list("museum").single()["differences"] as List<Map<String,Any?>>).single()["items"].let { it as List<*> }.size)
    } }
    @Test fun `archive tampering and invalid chronology block a clean replacement`()=fixture { db,book -> transaction(db) {
        val value=report(book)
        assertThrows(BadRequestException::class.java){reconciliation.record("museum",value.copy(checkedAt=LocalDateTime.now().plusDays(1)))}
        assertThrows(BadRequestException::class.java){reconciliation.record("museum",value.copy(nextCheckOn=at.toLocalDate()))}
        val id=reconciliation.record("museum",value);LedgerReconciliationDao[id].content=value.copy(scopeDescription="篡改范围")
        assertThrows(BadRequestException::class.java){reconciliation.record("museum",value.copy(replacementReason="复核",replacementFileIds="revision"))}
    } }
    @Test fun `failed originals roll back and milliseconds round trip`()=fixture { db,book ->
        assertThrows(BadRequestException::class.java){transaction(db){reconciliation.record("museum",report(book).copy(signedReportFileIds="missing"))}}
        transaction(db){assertEquals(0L,LedgerReconciliationDao.count());assertEquals(1L,PaperLedgerBookDao.count())}
        val value=report(book);assertEquals(value,JsonUtils.fromJson(JsonUtils.toJson(value)!!,LedgerReconciliationVo::class.java))
        val id=transaction(db){reconciliation.record("museum",value)}
        transaction(db){assertEquals(value,LedgerReconciliationDao[id].content);assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("finance")}}
    }
}
