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
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import org.sqlite.SQLiteDataSource
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path
import java.time.LocalDateTime

class WarehouseEntryTimelinePersistenceTest {
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    @TempDir lateinit var dir:Path
    private val at=LocalDateTime.of(2026,9,1,12,0)
    private val user=SecurityUser(id="verifier",name="核验人员",username="verifier",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.WAREHOUSE_ENTRY_VERIFY)),enabled=true)
    private class Runtime(val service:WarehouseEntryTimelineService,val tx:TransactionTemplate,val binder:BusinessAttachmentBinder,val directory:CollectionActorDirectory,val scope:CollectionScopeResolver) {fun <T> db(block:()->T):T=tx.execute {block()}}
    private fun fixture(body:(Runtime)->Unit) {
        val source=SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("timeline.db")}"}
        val manager=SpringTransactionManager(source,DatabaseConfig {})
        val scope=mock(CollectionScopeResolver::class.java)
        `when`(scope.isMulti()).thenReturn(true)
        `when`(scope.currentReadableOrgIds(any() ?: user,anyString())).thenReturn(setOf("museum"))
        `when`(scope.currentWritableOrgIds(any() ?: user)).thenReturn(setOf("museum"))
        `when`(scope.readableOrgIds()).thenReturn(setOf("museum"))
        val directory=mock(CollectionActorDirectory::class.java);`when`(directory.requireActive("verifier")).thenReturn(user)
        val binder=mock(BusinessAttachmentBinder::class.java);`when`(binder.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        try {AnnotationConfigApplicationContext().use {context->
            context.beanFactory.registerSingleton("transactionManager",manager);context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            context.beanFactory.registerSingleton("scope",scope);context.beanFactory.registerSingleton("directory",directory);context.beanFactory.registerSingleton("binder",binder)
            context.register(Transactions::class.java,CollectionPermanentArchiveService::class.java, io.hizhengfu.cloud.common.file.FileReadAccess::class.java,WarehouseEntryTimelineService::class.java)
            context.refresh();SpringContextHolder().setApplicationContext(context)
            val r=Runtime(context.getBean(WarehouseEntryTimelineService::class.java),TransactionTemplate(manager),binder,directory,scope)
            r.db {
                SchemaUtils.create(CollectionOrgs,WarehouseEntryApplications,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
                CollectionOrgDao.new("museum"){name="本馆";code="M"}
                FileDao.new("proof"){name="模拟核查材料";path="proof";hash="proof";size=1;ext="pdf"}
                WarehouseEntryApplicationDao.new("visit"){orgId="museum";applicationNo="IN-1";applicantId="operator";entrantIds="operator";warehouseIds="store";purpose="实际在库办理";plannedStart=at.minusHours(2);plannedEnd=at.plusHours(2);actualEntryAt=at.minusHours(1);actualExitAt=at.minusMinutes(1)}
                val attendance=WarehouseAttendanceVo(at.minusHours(1),setOf("operator"),identityChecked=true,continuousAccompaniment=true,signedRecordFileIds="original")
                original("diary",WarehouseDiaryService.BIZ,"log","RECORDED_1",mapOf("entries" to WarehouseDiaryEntriesVo(listOf(WarehouseDiaryVisitVo("visit","IN-1","实际工作",attendance,null,at.minusHours(1),at.plusHours(2),exitedAt=null)))))
            }
            body(r)
        }} finally {SecurityContextHolder.clearContext()}
    }
    private fun original(id:String,biz:String,bizId:String,phase:String,value:Any,org:String="museum") {
        val text=JsonUtils.toJson(value)!!
        CollectionPermanentArchiveDao.new(id){orgId=org;bizType=biz;this.bizId=bizId;this.phase=phase;snapshot=text;digest=CollectionCareEvidence.digest(text)}
    }
    private fun operation(id:String,biz:String=CollectionConst.BIZ_CARE,operator:String="operator",time:LocalDateTime=at,org:String="museum") {
        val start=mapOf("onSiteWarehouseLogId" to "log","operatorUserId" to operator,"startedAt" to time)
        original(id,biz,id,"STARTED",if(biz==CollectionConst.BIZ_CARE)mapOf("started" to start) else start,org)
    }
    private fun review(view:WarehouseEntryTimelineView)=WarehouseEntryTimelineReviewVo("review-key-001",view.baseline,at.plusHours(1),true,"已逐项核对出入库、日记与操作签署，时间差异尚待原经办核实","联系原经办确认实际时刻并保留补充证明，不覆盖原记录","proof")
    @Test fun `all four original operation types remain visible from permanent evidence`()=fixture {r->
        r.db {listOf(CollectionConst.BIZ_CARE,ReproductionComplianceService.BIZ,DigitalAcquisitionService.BIZ,CollectionConst.BIZ_SAMPLING).forEachIndexed {i,biz->operation("op-$i",biz)};operation("foreign",org="other");operation("different-person",operator="other");operation("before-exit",time=at.minusMinutes(2))}
        val view=r.service.get("visit")
        assertEquals(4,view.conflicts.size);assertTrue(view.evidenceErrors.isEmpty())
        assertFalse(view.conflicts.any {it.archiveId in setOf("foreign","different-person","before-exit")})
    }
    @Test fun `recording investigation is immutable idempotent and retains every original conflict`()=fixture {r->
        r.db {operation("op")};val before=r.db {WarehouseEntryApplicationDao["visit"].toModel()}
        val view=r.service.get("visit");val value=review(view)
        val id=r.service.review("visit",value);assertEquals(id,r.service.review("visit",value))
        assertThrows(BadRequestException::class.java){r.service.review("visit",value.copy(statement="替换旧核查结论"))}
        val after=r.service.get("visit");assertEquals(view.conflicts,after.conflicts);assertEquals(1,after.reviews.size)
        assertEquals(before,r.db {WarehouseEntryApplicationDao["visit"].toModel()})
    }
    @Test fun `timeline review original requires current reviewer case and file` ()=fixture {r->
        r.db {operation("op")}
        val archive=r.service.review("visit",review(r.service.get("visit")))
        r.service.requireArchivedOriginal("visit",archive,"proof")
        assertThrows(BadRequestException::class.java){r.service.requireArchivedOriginal("visit","missing","proof")}
        assertThrows(BadRequestException::class.java){r.service.requireArchivedOriginal("visit",archive,"missing")}
        val denied=SecurityUser(id="verifier",name="核验人员",username="verifier",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=emptyList(),enabled=true)
        `when`(r.directory.requireActive("verifier")).thenReturn(denied)
        assertThrows(BadRequestException::class.java){r.service.requireArchivedOriginal("visit",archive,"proof")}
    }
    @Test fun `changed timeline refuses stale review without recording a result`()=fixture {r->
        r.db {operation("op")};val value=review(r.service.get("visit"))
        r.db {WarehouseEntryApplicationDao["visit"].actualExitAt=at.minusMinutes(2)}
        assertThrows(BadRequestException::class.java){r.service.review("visit",value)}
        assertTrue(r.service.get("visit").reviews.isEmpty())
    }
    @Test fun `corrupted related evidence cannot silently become a clean timeline`()=fixture {r->
        r.db {operation("op");CollectionPermanentArchiveDao["op"].digest="changed"}
        val view=r.service.get("visit");assertTrue(view.evidenceErrors.isNotEmpty())
        assertThrows(BadRequestException::class.java){r.service.review("visit",review(view))}
    }
    @Test fun `current permission and museum scope are rechecked`()=fixture {r->
        r.db {operation("op")};val value=review(r.service.get("visit"))
        `when`(r.directory.requireActive("verifier")).thenReturn(SecurityUser(id="verifier",name="核验人员",username="verifier",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=emptyList(),enabled=true))
        assertThrows(BadRequestException::class.java){r.service.get("visit")}
        assertThrows(BadRequestException::class.java){r.service.review("visit",value)}
        `when`(r.directory.requireActive("verifier")).thenReturn(user)
        `when`(r.scope.currentWritableOrgIds(user)).thenReturn(emptySet())
        assertThrows(BadRequestException::class.java){r.service.review("visit",value)}
    }
    @Test fun `failed attachment binding rolls investigation archive back`()=fixture {r->
        r.db {operation("op")};val value=review(r.service.get("visit"))
        doThrow(BadRequestException("模拟原件关联失败")).`when`(r.binder).bindSingle(anyString(),anyString(),anyString())
        assertThrows(BadRequestException::class.java){r.service.review("visit",value)}
        assertTrue(r.service.get("visit").reviews.isEmpty())
        r.db {assertEquals(0L,CollectionPermanentArchiveFileDao.count())}
    }
    @Test fun `damaged investigation archive is shown as unverified instead of recorded evidence`()=fixture {r->
        r.db {operation("op")};val value=review(r.service.get("visit"));val id=r.service.review("visit",value)
        r.db {CollectionPermanentArchiveDao[id].snapshot="{}"}
        val result=r.service.get("visit");assertTrue(result.reviews.isEmpty());assertTrue(result.evidenceErrors.isNotEmpty());assertEquals(1,result.conflicts.size)
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["not-compared","no-statement","no-file","future","before-records"])
    fun `investigation requires actual comparison time content and original materials`(kind:String)=fixture {r->
        r.db {operation("op")};val original=review(r.service.get("visit"))
        val value=when(kind){
            "not-compared"->original.copy(comparedOriginals=false)
            "no-statement"->original.copy(statement="")
            "no-file"->original.copy(fileIds="")
            "future"->original.copy(reviewedAt=LocalDateTime.now().plusDays(1))
            else->original.copy(reviewedAt=at.minusDays(1))
        }
        assertThrows(BadRequestException::class.java){r.service.review("visit",value)}
        assertTrue(r.service.get("visit").reviews.isEmpty())
    }

    @Test fun `keyset scan retains related operations beyond the first metadata batch`()=fixture {r->
        r.db {repeat(205){operation("filler-${it.toString().padStart(3,'0')}",operator="someone-else")};operation("zz-target")}
        val result=r.service.get("visit");assertEquals(listOf("zz-target"),result.conflicts.map {it.archiveId});assertTrue(result.evidenceErrors.isEmpty())
    }

    @Test fun `permission revoked during inspection prevents final archive recording`()=fixture {r->
        r.db {operation("op")};val value=review(r.service.get("visit"))
        val denied=SecurityUser(id="verifier",name="核验人员",username="verifier",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=emptyList(),enabled=true)
        `when`(r.directory.requireActive("verifier")).thenReturn(user,denied)
        assertThrows(BadRequestException::class.java){r.service.review("visit",value)}
        r.db {assertTrue(CollectionPermanentArchiveDao.all().none {it.phase.startsWith(WarehouseEntryTimelineService.PHASE)})}
    }

    @Test fun `a newly signed diary version invalidates the displayed review baseline`()=fixture {r->
        r.db {operation("op")};val old=review(r.service.get("visit"))
        r.db {
            val source=CollectionPermanentArchiveDao["diary"]
            original("diary-new",WarehouseDiaryService.BIZ,"log","RECORDED_2",JsonUtils.mapper.readTree(source.snapshot))
        }
        assertThrows(BadRequestException::class.java){r.service.review("visit",old)}
        assertTrue(r.service.get("visit").reviews.isEmpty())
    }

    @Test fun `legacy short phase remains idempotent without changing the existing archive`()=fixture {r->
        r.db {operation("op")};val value=review(r.service.get("visit"));val id=r.service.review("visit",value)
        r.db {CollectionPermanentArchiveDao[id].phase=WarehouseEntryTimelineService.PHASE+value.requestKey}
        val before=r.db {CollectionPermanentArchiveDao[id].snapshot}
        assertEquals(id,r.service.review("visit",value));assertEquals(1,r.service.get("visit").reviews.size)
        r.db {assertEquals(before,CollectionPermanentArchiveDao[id].snapshot)}
    }

}
