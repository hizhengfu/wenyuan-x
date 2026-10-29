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
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.EnumSource as NatureSource
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.mock.web.MockHttpServletRequest
import java.time.LocalDate
import java.time.LocalDateTime

class CollectionGradingPersistenceTest {
    private val at = LocalDateTime.of(2026, 8, 1, 10, 0)
    private val scope = CollectionScopeResolver("multi", "")
    private val binder = mock(BusinessAttachmentBinder::class.java).also {
        `when`(it.resolveSingle(anyString())).thenAnswer { call -> call.getArgument<String>(0) }
    }
    private val archives = CollectionPermanentArchiveService(binder, scope, readAccess=unprotectedArchiveReadAccessProvider())
    private val service = CollectionGradingService(scope, archives)
    private val catalog = CollectionCatalogService(scope, CollectionOperationLockService(), service, mock(AccessionReceiptService::class.java),binder)
    private lateinit var database: Database
    private fun login(id: String = "editor") {
        val user = SecurityUser(id=id, name=id, username=id, password="", groupId="museum", groupIds=setOf("museum"),
            postIds=emptySet(), dataType=0, roles=emptySet(), authorities=listOf(SimpleGrantedAuthority(CollectionConst.LEDGER_CREATE)), enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    @BeforeEach fun setup() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        login()
        database=Database.connect("jdbc:sqlite:file:grading-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
    }
    @AfterEach fun clear() = SecurityContextHolder.clearContext()
    private fun fixture(block: (CollectionDao) -> Unit) = transaction(database) {
        originalReaderSchema()
        listOf("editor","reviewer").forEach {
            originalReader(it,CollectionConst.LEDGER_CREATE)
            actorRole(it,"GRADING_DATA_$it",setOf(CollectionConst.APPRAISE))
            actorRole(it,"CATALOG_$it",setOf(if(it=="editor") CollectionConst.CATALOG_EDIT else CollectionConst.CATALOG_AUDIT))
        }
        SchemaUtils.create(io.hizhengfu.cloud.collection.dao.CollectionLedgerStatEvents,Collections,CollectionOrgs,CollectionGradings,CollectionCatalogs,CollectionCatalogVersions,
            CollectionMedias,CollectionAttachments,CollectionCategories,Warehouses,CollectionOperationLocks,CollectionLedgerLogs,
            CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        CollectionOrgDao.new("museum") { name="本馆";code="M";systemGroupId="museum" }
        CollectionOrgDao.new("other") { name="外馆";code="O";systemGroupId="other" }
        listOf("preliminary","photo","application","receipt","result","provincial","submission","national").forEach { id ->
            FileDao.new(id) { name="$id.pdf";path="/files/$id.pdf";hash=id;size=100;ext="pdf" }
        }
        CollectionCategoryDao.new("ceramic") {name="瓷器";code="03"}
        WarehouseDao.new("catalog-store") {orgId="museum";name="编目库房";sceneType=EnumLocationScene.STORAGE}
        val row=CollectionDao.new("one") {
            orgId="museum";totalRegNo="C001";name="瓷瓶";nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.UNRATED
            categoryId="ceramic";era="清";texture="瓷";source=EnumSource.DONATE;acquireDate=LocalDate.of(2026,7,1)
            completeness=EnumCompleteness.COMPLETE;lifeStatus=EnumCollectionStatus.IN_STORE;catalogStatus=EnumCatalogStatus.DRAFT
        }
        CollectionDao.new("two") { orgId="other";totalRegNo="O001";name="外馆藏品" }
        block(row)
    }
    private fun application() = GradingApplicationVo(at,"preliminary",EnumRelicLevel.SECOND,"瓷瓶本件识别特征及现状","photo","省文物局","定申2026-1",at.plusDays(1),"application","receipt")
    private fun result(level: EnumRelicLevel = EnumRelicLevel.SECOND) = GradingResultVo("省文物局","定级2026-1",at.plusDays(5),level,"result","省备2026-1",at.plusDays(6),"provincial")
    private fun national() = GradingNationalFilingVo("省文物局","国家文物局",at.plusDays(7),"submission","国备2026-1",at.plusDays(9),"national")
    private fun content(row: CollectionDao,level: EnumRelicLevel=EnumRelicLevel.SECOND) = CollectionCatalogMapper.fromCollection(row.toModel()).copy(level=level,classificationNo="C-1",actualQuantity=1,actualQuantityUnit="件",entryDate=at.toLocalDate(),warehouseId="catalog-store",textureCategory1="单一质地",textureCategory2="无机质/非金属材料",preserveState=EnumPreserve.A,completeStatus="完好",feature="识别特征",coverImage="photo",registrationDetails=CollectionRegistrationDetailsVo(eraMeaning=EnumCollectionEraMeaning.MANUFACTURED,eraNotation=EnumCollectionEraNotation.CHINESE_HISTORY,coverIsFrontView=true,dimensionUnknownReason="暂不能测量",weightUnknownReason="暂不能称重"))

    @Test fun `grading archived original stays bound to collection record stage and file`()=fixture { row ->
        val fileId="2102704108634521999"
        FileDao.new(fileId) { name="省级定级申请.pdf";path="/files/grading-stage.pdf";hash="stage";size=100;ext="pdf" }
        val gradingId=service.apply(row.id.value,application().copy(applicationFileIds=fileId))
        @Suppress("UNCHECKED_CAST")
        val record=(service.detail(row.id.value)["records"] as List<Map<String,Any?>>).single()
        @Suppress("UNCHECKED_CAST")
        val archived=(record["archives"] as List<Map<String,Any?>>).single { it["phase"]=="APPLICATION" }
        val archiveId=archived["id"] as String
        assertDoesNotThrow { service.requireArchivedOriginal(row.id.value,gradingId,archiveId,fileId) }
        assertThrows(BadRequestException::class.java) { service.requireArchivedOriginal("two",gradingId,archiveId,fileId) }
        assertThrows(BadRequestException::class.java) { service.requireArchivedOriginal(row.id.value,"2102704108634521998",archiveId,fileId) }
        assertThrows(BadRequestException::class.java) { service.requireArchivedOriginal(row.id.value,gradingId,"2102704108634521997",fileId) }
        assertThrows(BadRequestException::class.java) { service.requireArchivedOriginal(row.id.value,gradingId,archiveId,"2102704108634521996") }
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { service.requireArchivedOriginal(row.id.value,gradingId,archiveId,fileId) }
    }

    @Test fun `original museum retains grading archive read after collection transfers`()=fixture { row ->
        val fileId="2102704108634521999"
        FileDao.new(fileId) { name="原馆定级申请.pdf";path="/files/original-grading.pdf";hash="stage";size=100;ext="pdf" }
        val gradingId=service.apply(row.id.value,application().copy(applicationFileIds=fileId))
        row.orgId="other"
        val request=MockHttpServletRequest().apply { addParameter("keyword","C001") }
        val history=service.historicalPage(request)
        assertEquals(1,history.total)
        @Suppress("UNCHECKED_CAST")
        val listed = history.list as List<Map<String, Any?>>
        assertEquals(gradingId,listed.single()["id"])
        val detail=service.historicalDetail(gradingId)
        assertEquals("museum",detail["orgId"])
        assertEquals("C001",detail["totalRegNo"])
        @Suppress("UNCHECKED_CAST")
        val archived=(detail["archives"] as List<Map<String,Any?>>).single { it["phase"]=="APPLICATION" }
        val archiveId=archived["id"] as String
        assertDoesNotThrow { service.requireHistoricalArchivedOriginal(gradingId,archiveId,fileId) }
        assertThrows(BadRequestException::class.java) { service.requireHistoricalArchivedOriginal(gradingId,archiveId,"2102704108634521998") }
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { service.historicalDetail(gradingId) }
        assertThrows(BadRequestException::class.java) { service.requireHistoricalArchivedOriginal(gradingId,archiveId,fileId) }
    }

    @Test fun `catalog approval consumes valid grading without administrative recording duty`()=fixture {row ->

        val id=service.apply(row.id.value,application());service.result(id,result())
        RolePermissions.deleteAll()
        listOf("editor","reviewer").forEach {actorRole(it,"LEDGER_ONLY_$it",setOf(CollectionConst.LEDGER_CREATE,if(it=="editor") CollectionConst.CATALOG_EDIT else CollectionConst.CATALOG_AUDIT))}
        assertThrows(BadRequestException::class.java){service.result(id,result())}
        catalog.save(row.id.value,content(row));catalog.submit(row.id.value);login("reviewer")
        catalog.review(row.id.value,CollectionCatalogReviewVo(EnumCatalogStatus.APPROVED,"依据真实行政原件正式登账"))
        assertEquals(EnumRelicLevel.SECOND,row.level);assertNotNull(row.registeredAt)
        assertTrue(archives.list(CollectionGradingService.BIZ,id).any {(it["phase"] as String).startsWith("USED_")})
    }

    @ParameterizedTest @CsvSource("application,detail","application,apply","application,result","application,national","application,use",
        "result,detail","result,apply","result,result","result,national","result,use",
        "national,detail","national,apply","national,result","national,national","national,use",
        "clear_result,detail","clear_result,apply","clear_result,result","clear_result,national","clear_result,use",
        "clear_national,detail","clear_national,apply","clear_national,result","clear_national,national","clear_national,use",
        "missing_application,detail","missing_application,apply","missing_application,result","missing_application,national","missing_application,use",
        "missing_result,detail","missing_result,apply","missing_result,result","missing_result,national","missing_result,use",
        "missing_national,detail","missing_national,apply","missing_national,result","missing_national,national","missing_national,use",
        "missing_file,detail","missing_file,apply","missing_file,result","missing_file,national","missing_file,use",
        "used_snapshot,detail","used_snapshot,apply","used_snapshot,result","used_snapshot,national","used_snapshot,use",
        "used_phase,detail","used_phase,apply","used_phase,result","used_phase,national","used_phase,use",
        "used_reference,detail","used_reference,apply","used_reference,result","used_reference,national","used_reference,use")
    fun `grading recorded stages cannot be bypassed by reads repeats or use`(damage:String,entry:String)=fixture { row ->
        val id=service.apply(row.id.value,application());service.result(id,result(EnumRelicLevel.FIRST));service.national(id,national())
        if(damage.startsWith("used_"))service.recordUse(row,id,"original-use",EnumRelicLevel.FIRST)
        service.detail(row.id.value)
        val grading=CollectionGradingDao[id]
        fun archive(phase:String)=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionGradingService.BIZ && it.bizId==id && it.phase==phase}
        when(damage) {
            "application"->grading.application=grading.application.copy(itemDescription="改写识别依据")
            "result"->grading.result=grading.result!!.copy(documentNo="改写行政文号")
            "national"->grading.nationalFiling=grading.nationalFiling!!.copy(filingNo="改写国家备案号")
            "clear_result"->grading.result=null
            "clear_national"->grading.nationalFiling=null
            "missing_application"->archive("APPLICATION").delete()
            "missing_result"->archive("PROVINCIAL_RESULT").delete()
            "missing_national"->archive("NATIONAL_FILING").delete()
            "missing_file"->{val a=archive("PROVINCIAL_RESULT");CollectionPermanentArchiveFileDao.all().filter {it.archiveId==a.id.value}.forEach {it.delete()} }
            "used_snapshot"->{
                val a=archive("USED_original-use")
                a.snapshot=JsonUtils.toJson(mapOf("collectionId" to row.id.value,"totalRegNo" to row.totalRegNo,"operationId" to "original-use",
                    "result" to grading.result!!.copy(documentNo="改写使用依据"),"nationalFiling" to grading.nationalFiling))!!
                a.digest=CollectionCareEvidence.digest(a.snapshot)
            }
            "used_phase"->archive("USED_original-use").phase="USED_other-use"
            "used_reference"->{val a=archive("USED_original-use");CollectionPermanentArchiveFileDao.all().filter {it.archiveId==a.id.value}.forEach {it.delete()} }

        }
        fun state():Any {
            TransactionManager.current().entityCache.flush()
            TransactionManager.current().entityCache.clear()
            return Triple(CollectionPermanentArchiveDao.all().map {listOf(it.id.value,it.phase,it.snapshot,it.digest)}.sortedBy {it.first()},
            CollectionPermanentArchiveFileDao.all().map {listOf(it.id.value,it.archiveId,it.fileId,it.metadata)}.sortedBy {it.first()},
            JsonUtils.toJson(listOf(grading.application,grading.result,grading.nationalFiling)))
        }
        val before=state()
        assertThrows(BadRequestException::class.java) {
            when(entry) {
                "detail"->service.detail(row.id.value)
                "apply"->service.apply(row.id.value,grading.application)
                "result"->service.result(id,grading.result ?: result(EnumRelicLevel.FIRST))
                "national"->service.national(id,grading.nationalFiling ?: national())
                else->service.recordUse(row,id,"new-catalog-use",EnumRelicLevel.FIRST)
            }
        }
        assertEquals(before,state(),"Rejected read or repeat must not repair, reseal or create a use archive")
    }

    @ParameterizedTest @ValueSource(strings=["application","result","national"])
    fun `grading recorded partial and complete stages remain readable without resealing`(stage:String)=fixture {row ->
        val id=service.apply(row.id.value,application())
        if(stage!="application")service.result(id,result(EnumRelicLevel.FIRST))
        if(stage=="national")service.national(id,national())
        fun originalState()=Pair(CollectionPermanentArchiveDao.all().map {listOf(it.id.value,it.phase,it.snapshot,it.digest)},
            CollectionPermanentArchiveFileDao.all().map {listOf(it.id.value,it.fileId,it.metadata)})
        val before=originalState()
        repeat(2) {
            val records=service.detail(row.id.value)["records"] as List<Map<String,Any?>>
            assertEquals(stage=="national",records.single()["ready"])
            assertEquals(id,service.apply(row.id.value,application()))
            if(stage!="application")assertEquals(id,service.result(id,result(EnumRelicLevel.FIRST)))
            if(stage=="national")assertEquals(id,service.national(id,national()))
        }
        assertEquals(before,originalState(),"Legacy metadata without content baseline remains unchanged")
    }

    @Test fun `grading recorded later result cannot be cleared to reuse an earlier determination`()=fixture {row ->
        val first=service.apply(row.id.value,application());service.result(first,result())
        val second=service.apply(row.id.value,application().copy(applicationNo="新申",submittedAt=at.plusDays(10),replacementReason="重新定级"))
        service.result(second,result(EnumRelicLevel.THIRD).copy(documentNo="新定",determinedAt=at.plusDays(11),provincialFiledAt=at.plusDays(12)))
        CollectionGradingDao[second].result=null
        assertThrows(BadRequestException::class.java){service.requireContent(row,content(row),true)}
        assertNull(row.registeredAt);assertEquals(EnumRelicLevel.UNRATED,row.level)
    }

    @ParameterizedTest @CsvSource("reordered,detail","reordered,content_old","reordered,repeat_result","reordered,use_old","reordered,new_apply",
        "missing_latest,detail","missing_latest,content_old","missing_latest,repeat_result","missing_latest,use_old","missing_latest,new_apply",
        "missing_latest_and_stages,detail","missing_latest_and_stages,content_old","missing_latest_and_stages,repeat_result","missing_latest_and_stages,use_old","missing_latest_and_stages,new_apply")
    fun `grading history cannot silently reorder or lose newer administrative versions`(damage:String,entry:String)=fixture {row ->
        val first=service.apply(row.id.value,application());service.result(first,result())
        val second=service.apply(row.id.value,application().copy(applicationNo="第二申请",submittedAt=at.plusDays(10),replacementReason="重新定级"))
        service.result(second,result(EnumRelicLevel.THIRD).copy(documentNo="第二结果",determinedAt=at.plusDays(11),provincialFiledAt=at.plusDays(12)))
        service.detail(row.id.value)
        when(damage) {
            "reordered"->{
                CollectionGradings.update({CollectionGradings.id eq first}){it[version]=99}
                CollectionGradings.update({CollectionGradings.id eq second}){it[version]=1}
                CollectionGradings.update({CollectionGradings.id eq first}){it[version]=2}
            }
            "missing_latest"->CollectionGradingDao[second].delete()
            "missing_latest_and_stages"->{
                CollectionGradingDao[second].delete()
                CollectionPermanentArchiveDao.all().filter {it.bizType==CollectionGradingService.BIZ && it.bizId==second}.forEach {it.delete()}
            }
        }
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        fun state()=Triple(CollectionGradingDao.all().map {listOf(it.id.value,it.version.toString(),JsonUtils.toJson(it.application),JsonUtils.toJson(it.result))},
            CollectionPermanentArchiveDao.all().map {listOf(it.id.value,it.phase,it.snapshot,it.digest)},
            CollectionPermanentArchiveFileDao.all().map {listOf(it.id.value,it.archiveId,it.metadata)})
        val before=state()
        assertThrows(BadRequestException::class.java) {
            when(entry) {
                "detail"->service.detail(row.id.value)
                "content_old"->service.requireContent(row,content(row),true)
                "repeat_result"->service.result(first,result())
                "use_old"->service.recordUse(row,first,"new-use",EnumRelicLevel.SECOND)
                else->service.apply(row.id.value,application().copy(applicationNo="再申请",submittedAt=at.plusDays(15),replacementReason="继续定级"))
            }
        }
        assertEquals(before,state());assertNull(row.registeredAt)
    }

    private fun legacyGradingFormat() {
        // 仅用于构造升级前夹具；产品读取路径不得如此改写原件。
        CollectionPermanentArchiveDao.all().filter {it.bizType==CollectionGradingHistoryEvidence.BIZ}.forEach {it.delete()}
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        CollectionGradingDao.all().forEach { row ->
            val archive=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionGradingService.BIZ && it.bizId==row.id.value && it.phase=="APPLICATION"}
            archive.snapshot=JsonUtils.toJson(mapOf("collectionId" to row.collectionId,"totalRegNo" to row.totalRegNo,"application" to row.application))!!
            archive.digest=CollectionCareEvidence.digest(archive.snapshot)
        }
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
    }
    private fun twoGradingVersions(row:CollectionDao):Pair<String,String> {
        val first=service.apply(row.id.value,application());service.result(first,result())
        val second=service.apply(row.id.value,application().copy(applicationNo="第二申请",submittedAt=at.plusDays(10),replacementReason="重新定级"))
        service.result(second,result(EnumRelicLevel.THIRD).copy(documentNo="第二结果",determinedAt=at.plusDays(11),provincialFiledAt=at.plusDays(12)))
        return first to second
    }
    @Test fun `grading history legacy originals are not rewritten when a new version anchors the prefix`()=fixture {row ->
        twoGradingVersions(row);legacyGradingFormat()
        val old=CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)}
        service.detail(row.id.value);assertEquals(2L,CollectionGradingDao.count())
        assertEquals(0,CollectionPermanentArchiveDao.all().count {it.bizType==CollectionGradingHistoryEvidence.BIZ})
        val third=service.apply(row.id.value,application().copy(applicationNo="第三申请",submittedAt=at.plusDays(15),replacementReason="后续定级"))
        service.result(third,result(EnumRelicLevel.GENERAL).copy(documentNo="第三结果",determinedAt=at.plusDays(16),provincialFiledAt=at.plusDays(17)))
        assertEquals(third,service.requireContent(row,content(row,EnumRelicLevel.GENERAL),true));service.detail(row.id.value)
        old.forEach {(id,value)->assertEquals(value,CollectionPermanentArchiveDao[id].let {it.snapshot to it.digest})}
        assertEquals(2,CollectionPermanentArchiveDao.all().count {it.bizType==CollectionGradingHistoryEvidence.BIZ})
    }
    @ParameterizedTest @ValueSource(strings=["reordered","missing_latest","ambiguous_time"])
    fun `grading history legacy uncertainty is rejected without manufacturing an index`(damage:String)=fixture {row ->
        val (first,second)=if(damage=="ambiguous_time") {
            val first=service.apply(row.id.value,application())
            first to service.apply(row.id.value,application().copy(applicationNo="同时补正",replacementReason="补正"))
        } else twoGradingVersions(row)
        legacyGradingFormat()
        if(damage=="reordered") {
            CollectionGradings.update({CollectionGradings.id eq first}){it[version]=99}
            CollectionGradings.update({CollectionGradings.id eq second}){it[version]=1}
            CollectionGradings.update({CollectionGradings.id eq first}){it[version]=2}
        } else if(damage=="missing_latest") CollectionGradingDao[second].delete()
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        val before=CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)}
        assertThrows(BadRequestException::class.java){service.detail(row.id.value)}
        assertThrows(BadRequestException::class.java){service.apply(row.id.value,application().copy(applicationNo="新申",submittedAt=at.plusDays(15),replacementReason="后续申请"))}
        assertEquals(before,CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)})
    }
    @Test fun `grading history supports overlapping pending applications and later administrative results`()=fixture {row ->
        val first=service.apply(row.id.value,application())
        val second=service.apply(row.id.value,application().copy(applicationNo="同日第二申请",replacementReason="同日实际补正"))
        service.detail(row.id.value)
        service.result(first,result())
        service.result(second,result(EnumRelicLevel.THIRD).copy(documentNo="第二结果",determinedAt=at.plusDays(7),provincialFiledAt=at.plusDays(8)))
        assertEquals(second,service.requireContent(row,content(row,EnumRelicLevel.THIRD),true))
        service.detail(row.id.value)
    }
    @ParameterizedTest @ValueSource(strings=["missing_version_proof","modified_prefix","missing_result_and_stage","empty_proof","foreign_proof","unknown_format"])
    fun `grading history independent evidence cannot be dropped or rewritten`(damage:String)=fixture {row ->
        val (_,second)=twoGradingVersions(row)
        val proof=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionGradingHistoryEvidence.BIZ && it.phase=="APPLICATION_2"}
        when(damage) {
            "missing_version_proof"->proof.delete()
            "modified_prefix"->{
                val value=JsonUtils.fromJson(proof.snapshot,CollectionGradingHistoryEvidence.Snapshot::class.java)!!
                proof.snapshot=JsonUtils.toJson(value.copy(entries=value.entries.reversed()))!!;proof.digest=CollectionCareEvidence.digest(proof.snapshot)
            }
            "missing_result_and_stage"->{
                CollectionGradingDao[second].result=null
                CollectionPermanentArchiveDao.all().single {it.bizType==CollectionGradingService.BIZ && it.bizId==second && it.phase=="PROVINCIAL_RESULT"}.delete()
            }
            "empty_proof"->{proof.snapshot="";proof.digest=CollectionCareEvidence.digest(proof.snapshot)}
            "foreign_proof"->proof.orgId="other"
            else->{
                val app=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionGradingService.BIZ && it.bizId==second && it.phase=="APPLICATION"}
                app.snapshot=app.snapshot.replace("\"formatVersion\":2","\"formatVersion\":\"unknown\"");app.digest=CollectionCareEvidence.digest(app.snapshot)
            }
        }
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        assertThrows(BadRequestException::class.java){service.detail(row.id.value)}
        assertThrows(BadRequestException::class.java){service.requireContent(row,content(row),true)}
    }

    @ParameterizedTest @ValueSource(strings=["provincial_missing_result","national_missing_result","national_missing_filing",
        "application_future_result","application_future_filing","provincial_future_filing","prior_filing_without_result"])
    fun `grading proof completion stage requires its own original references`(damage:String)=fixture {row ->
        val id=service.apply(row.id.value,application());service.result(id,result(EnumRelicLevel.FIRST));service.national(id,national())
        service.apply(row.id.value,application().copy(applicationNo="后续申请",submittedAt=at.plusDays(12),replacementReason="继续核实"))
        service.detail(row.id.value)
        val phase=when(damage) {
            "provincial_missing_result","provincial_future_filing"->"PROVINCIAL_RESULT_1"
            "national_missing_result","national_missing_filing"->"NATIONAL_FILING_1"
            "prior_filing_without_result"->"APPLICATION_2"
            else->"APPLICATION_1"
        }
        fun original(phase:String)=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionGradingService.BIZ && it.bizId==id && it.phase==phase}
            .let {CollectionGradingHistoryEvidence.Original(it.id.value,it.digest)}
        val proof=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionGradingHistoryEvidence.BIZ && it.phase==phase}
        val saved=JsonUtils.fromJson(proof.snapshot,CollectionGradingHistoryEvidence.Snapshot::class.java)!!
        val entry=saved.entries.first()
        val changed=when(damage) {
            "provincial_missing_result","national_missing_result","prior_filing_without_result"->entry.copy(provincialResult=null)
            "national_missing_filing"->entry.copy(nationalFiling=null)
            "application_future_result"->entry.copy(provincialResult=original("PROVINCIAL_RESULT"))
            else->entry.copy(nationalFiling=original("NATIONAL_FILING"))
        }
        proof.snapshot=JsonUtils.toJson(saved.copy(entries=listOf(changed)+saved.entries.drop(1)))!!
        proof.digest=CollectionCareEvidence.digest(proof.snapshot)
        val before=CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)}
        assertThrows(BadRequestException::class.java){service.detail(row.id.value)}
        assertThrows(BadRequestException::class.java){service.result(id,result(EnumRelicLevel.FIRST))}
        assertThrows(BadRequestException::class.java){service.requireContent(row,content(row,EnumRelicLevel.FIRST),true)}
        assertThrows(BadRequestException::class.java){service.recordUse(row,id,"new-use",EnumRelicLevel.FIRST)}
        assertEquals(before,CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)})
    }

    @Test fun `current revocation rejects grading originals despite old login`()=fixture { row ->
        service.apply(row.id.value,application());RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.detail(row.id.value)}
    }

    @Test fun `grading current membership cross grant and configured museum replace old login scope`()=fixture { row ->
        service.apply(row.id.value,application());UserDao["editor"].groupId="other"
        assertThrows(BadRequestException::class.java){service.detail(row.id.value)}
        actorRole("editor","CURRENT_CROSS",setOf(CollectionConst.CROSS_ORG))
        assertEquals(row.id.value,service.detail(row.id.value)["collectionId"])
        assertThrows(BadRequestException::class.java){CollectionGradingService(CollectionScopeResolver("single","other"),archives).detail(row.id.value)}
        RolePermissions.deleteAll();actorRole("editor","CROSS_ONLY",setOf(CollectionConst.CROSS_ORG))
        assertThrows(BadRequestException::class.java){service.detail(row.id.value)}
    }
    @Test fun `grading disabled identity and mismatched registration or archive reference fail closed`()=fixture { row ->
        val id=service.apply(row.id.value,application())
        UserDao["editor"].enabled=false;assertThrows(BadRequestException::class.java){service.detail(row.id.value)}
        UserDao["editor"].enabled=true
        CollectionGradingDao[id].totalRegNo="OTHER-NO"
        assertThrows(BadRequestException::class.java){service.detail(row.id.value)}
        CollectionGradingDao[id].totalRegNo=row.totalRegNo
        CollectionPermanentArchiveFileDao.all().first().orgId="other"
        assertThrows(BadRequestException::class.java){service.detail(row.id.value)}
    }

    @ParameterizedTest
    @NatureSource(value=EnumCollectionNature::class,names=["RELIC","SCIENTIFIC_FOSSIL"])
    fun `internal proposed level cannot approve initial catalog without administrative originals`(nature:EnumCollectionNature) = fixture { row ->
        row.nature=nature
        catalog.save(row.id.value,content(row));catalog.submit(row.id.value);login("reviewer")
        assertThrows(BadRequestException::class.java) { catalog.review(row.id.value,CollectionCatalogReviewVo(EnumCatalogStatus.APPROVED,"同意")) }
        assertNull(row.registeredAt);assertEquals(EnumCatalogStatus.SUBMITTED,row.catalogStatus);assertEquals(EnumRelicLevel.UNRATED,row.level)
        val id=service.apply(row.id.value,application());service.result(id,result())
        assertEquals(EnumRelicLevel.UNRATED,row.level)
        catalog.review(row.id.value,CollectionCatalogReviewVo(EnumCatalogStatus.APPROVED,"核实正式原件"))
        assertEquals(EnumRelicLevel.SECOND,row.level);assertNotNull(row.registeredAt)
        assertTrue(archives.list(CollectionGradingService.BIZ,id).any { (it["phase"] as String).startsWith("USED_") })
    }
    @ParameterizedTest
    @NatureSource(value=EnumCollectionNature::class,names=["RELIC","SCIENTIFIC_FOSSIL"])
    fun `first grade requires provincial submission and actual national receipt`(nature:EnumCollectionNature) = fixture { row ->
        row.nature=nature
        val id=service.apply(row.id.value,application());service.result(id,result(EnumRelicLevel.FIRST))
        assertThrows(BadRequestException::class.java) { service.requireContent(row,content(row,EnumRelicLevel.FIRST),true) }
        assertThrows(BadRequestException::class.java) { service.national(id,national().copy(submittingProvincialAuthority="博物馆")) }
        service.national(id,national());service.national(id,national())
        assertEquals(id,service.requireContent(row,content(row,EnumRelicLevel.FIRST),true))
        assertThrows(BadRequestException::class.java) { service.national(id,national().copy(filingNo="替换旧备案")) }
        assertThrows(BadRequestException::class.java) { CollectionArchiveFileRetentionGuard().requireDeletable("national") }
    }
    @Test fun `administrative stages enforce chronology authority and immutable originals`() = fixture { row ->
        assertThrows(BadRequestException::class.java) { service.apply(row.id.value,application().copy(submittedAt=at.minusDays(1))) }
        val id=service.apply(row.id.value,application());assertEquals(id,service.apply(row.id.value,application()))
        assertThrows(BadRequestException::class.java) { service.result(id,result().copy(provincialAuthority="另一机关")) }
        assertThrows(BadRequestException::class.java) { service.result(id,result().copy(level=EnumRelicLevel.UNRATED)) }
        assertThrows(BadRequestException::class.java) { service.result(id,result().copy(determinedAt=LocalDateTime.now().plusDays(1))) }
        service.result(id,result());service.result(id,result())
        assertThrows(BadRequestException::class.java) { service.result(id,result(EnumRelicLevel.THIRD)) }
        assertThrows(BadRequestException::class.java) { service.national(id,national()) }
        assertThrows(BadRequestException::class.java) { CollectionArchiveFileRetentionGuard().requireDeletable("result") }
    }
    @Test fun `later formal determination governs future use without rewriting historical grade`() = fixture { row ->
        val first=service.apply(row.id.value,application());service.result(first,result())
        row.level=EnumRelicLevel.SECOND;row.registeredAt=at;row.catalogStatus=EnumCatalogStatus.APPROVED
        assertNull(service.requireContent(row,content(row).copy(formalName="名称订正"),false))
        assertThrows(BadRequestException::class.java) { service.apply(row.id.value,application().copy(applicationNo="新申请")) }
        val second=service.apply(row.id.value,application().copy(applicationNo="新申请",submittedAt=at.plusDays(10),replacementReason="后续重新定级"))
        service.result(second,result(EnumRelicLevel.THIRD).copy(documentNo="新结果",determinedAt=at.plusDays(11),provincialFiledAt=at.plusDays(12)))
        assertEquals(EnumRelicLevel.SECOND,row.level)
        assertThrows(BadRequestException::class.java) { service.requireContent(row,content(row,EnumRelicLevel.FIRST),false) }
        catalog.applyStatutoryChange(row,content(row,EnumRelicLevel.THIRD),"actual-change","经批准订正","editor","leader",at.plusDays(13))
        assertEquals(EnumRelicLevel.THIRD,row.level)
        assertEquals(2,(service.detail(row.id.value)["records"] as List<*>).size)
        assertTrue(archives.list(CollectionGradingService.BIZ,second).any { it["phase"]=="USED_actual-change" })
    }
    @Test fun `ordinary corrections preserve existing legacy facts but level changes cannot bypass grade`() = fixture { row ->
        row.level=EnumRelicLevel.GENERAL;row.catalogStatus=EnumCatalogStatus.APPROVED;row.registeredAt=at
        assertNull(service.requireContent(row,content(row,EnumRelicLevel.GENERAL).copy(formalName="名称订正"),false))
        assertThrows(BadRequestException::class.java) { catalog.applyStatutoryChange(row,content(row),"change","改变级别","editor","leader",at) }
        assertEquals(EnumRelicLevel.GENERAL,row.level)
        assertEquals(0,CollectionCatalogVersionDao.count())
    }
    @Test fun `other collections have no relic grade and unknown nature needs verification`() = fixture { row ->
        assertThrows(BadRequestException::class.java) { service.requireContent(row,content(row).copy(nature=null),true) }
        assertThrows(BadRequestException::class.java) { service.requireContent(row,content(row).copy(nature=EnumCollectionNature.OTHER),true) }
        assertNull(service.requireContent(row,content(row,EnumRelicLevel.UNRATED).copy(nature=EnumCollectionNature.OTHER),true))
    }
    @Test fun `museum scope and transferred identity do not borrow other museum originals`() = fixture { row ->
        assertThrows(BadRequestException::class.java) { service.detail("two") }
        assertThrows(BadRequestException::class.java) { service.apply("two",application()) }
        val id=service.apply(row.id.value,application())
        row.orgId="other"
        assertThrows(BadRequestException::class.java) { service.result(id,result()) }
    }
    @Test fun `changed stored administrative data cannot replace the sealed original`() = fixture { row ->
        val id=service.apply(row.id.value,application());service.result(id,result())
        CollectionGradingDao[id].result=result(EnumRelicLevel.FIRST)
        CollectionGradingDao[id].nationalFiling=national()
        assertThrows(BadRequestException::class.java) { service.requireContent(row,content(row,EnumRelicLevel.FIRST),true) }
        CollectionGradingDao[id].result=result().copy(documentNo="改写文书")
        CollectionGradingDao[id].nationalFiling=null
        assertThrows(BadRequestException::class.java) { service.requireContent(row,content(row),true) }
        assertNull(row.registeredAt);assertEquals(EnumRelicLevel.UNRATED,row.level)
    }
    @Test fun `entry receipt never copies acquisition proposed level into the master`() = fixture { row ->
        SchemaUtils.create(Acquisitions,AcquisitionComplianceCases,EntryVouchers,StorageMovements,AccessionReceipts)
        WarehouseDao.new("store") { orgId="museum";name="库房";sceneType=EnumLocationScene.STORAGE }
        val acquisition=AcquisitionDao.new("acq") { orgId="museum";name="瓷瓶";source=EnumSource.DONATE;quantity=1;unit="件";proposedLevel=EnumRelicLevel.FIRST;flowStatus=io.hizhengfu.cloud.common.domain.EnumFlowStatus.PASS }
        val case=AcquisitionComplianceCaseDao.new { orgId="museum";acquisitionId="acq";subject="原始对象";dossier=AcquisitionDossierVo(EnumCollectionNature.RELIC,at.toLocalDate(),"preliminary","preliminary","preliminary",true,"preliminary") }
        val voucher=EntryVoucherDao.new("voucher") { orgId="museum";voucherNo="RJ-1";acquisitionIds="acq";verifiedAcquisitionIds="acq";basis="已核实入馆依据";entryDate=at.toLocalDate();handoverUser="handover";receiverUser="receiver";custodian="keeper";warehouseId="store";attachmentIds="receipt" }
        val compliance=mock(AcquisitionComplianceService::class.java)
        `when`(compliance.requirePrepared(acquisition)).thenReturn(AcquisitionPreparedSource(case.dossier.nature,at.toLocalDate()))
        var received:CollectionVo?=null
        val collections=mock(CollectionService::class.java) { call ->
            if (call.method.name.substringBefore('$') == "createForAccession") {
                assertEquals("acq", call.getArgument<String>(2))
                received=call.getArgument(1);row.id.value
            }
            else RETURNS_DEFAULTS.answer(call)
        }
        val receipt=AccessionReceiptDao.new { orgId="museum";voucherId="voucher";acquisitionId="acq";version=1;sourceSnapshot="核收依据";content=AccessionReceiptVo("acq",at,"store",setOf("keeper"),setOf("移交人"),listOf(AccessionPieceVo("one",at,"实物识别","完好",true,true,"photo",actualQuantity=1,actualQuantityUnit="件")),true,"receipt") }
        val receiptService=mock(AccessionReceiptService::class.java)
        `when`(receiptService.requireCompleted(voucher)).thenReturn(mapOf("acq" to receipt))
        EntryVoucherService(scope,collections,binder,mock(CollectionPrintService::class.java),compliance,receiptService).execute(voucher.id.value)
        assertEquals(EnumRelicLevel.FIRST,acquisition.proposedLevel)
        assertEquals(EnumRelicLevel.UNRATED,received!!.level)
        assertEquals(row.id.value,acquisition.collectionId)
        assertEquals(0L,StorageMovementDao.count())
        assertEquals(EnumCollectionStatus.PENDING,received!!.lifeStatus)
        assertEquals(EnumVoucherStatus.RECEIVED,voucher.voucherStatus)
    }
    @Test fun `formal evidence preserves exact millisecond dates`() {
        val value=application().copy(submittedAt=at.plusDays(1).withNano(123000000))
        assertEquals(value,JsonUtils.fromJson(JsonUtils.toJson(value)!!,GradingApplicationVo::class.java))
    }
}
