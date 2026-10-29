package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import org.mockito.Mockito.`when`
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path

class CollectionCirculationReadRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate) {
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun permissions(vararg values:String)=db {RolePermissions.deleteAll();actorRole("reader","FILE_reader",values.toSet())}
        fun get(kind:String):Any?=when(kind) {
            "requisition" -> c.getBean(RequisitionService::class.java).get("requisition")
            "loan" -> c.getBean(LoanService::class.java).get("loan")
            else -> c.getBean(CollectionReturnService::class.java).get("return")
        }
        fun page(kind:String,keyword:String?=null,collectionId:String?=null):GenericPage<*> {
            val request=MockHttpServletRequest().apply {keyword?.let {setParameter("keyword",it)};collectionId?.let {setParameter("collectionId",it)}}
            return when(kind) {
                "requisition" -> c.getBean(RequisitionService::class.java).findPage(request)
                "loan" -> c.getBean(LoanService::class.java).findPage(request)
                else -> c.getBean(CollectionReturnService::class.java).findPage(request)
            }
        }
        fun read(kind:String,view:String):Any?=when(view) {
            "detail" -> get(kind)
            "page" -> page(kind)
            else -> when(kind) {
                "requisition" -> c.getBean(RequisitionService::class.java).getFlowInfo("requisition")
                "loan" -> c.getBean(LoanService::class.java).getFlowInfo("loan")
                else -> c.getBean(CollectionReturnService::class.java).getFlowInfo("return")
            }
        }
    }
    private fun fixture(body:(Runtime)->Unit)=fixture(CollectionScopeResolver("single","museum"),body)
    private fun fixture(scope:CollectionScopeResolver,body:(Runtime)->Unit) {
        val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("read.db")}"},DatabaseConfig {})
        val login=SecurityUser(id="reader",name="读取人员",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf("ADMIN"),authorities=listOf("ADMIN",CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(login,null,login.authorities)
        try {AnnotationConfigApplicationContext().use {c ->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("scope",scope)
            c.beanFactory.registerSingleton("localStorage",io.hizhengfu.cloud.common.file.LocalFileObjectStorage(dir.toString()))
            c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("flow",mock(FlowInstanceService::class.java))
            c.beanFactory.registerSingleton("collections",mock(CollectionService::class.java))
            c.beanFactory.registerSingleton("locks",mock(CollectionOperationLockService::class.java))
            c.beanFactory.registerSingleton("placement",mock(CollectionPlacementService::class.java))
            c.beanFactory.registerSingleton("users",mock(CollectionResponsibleUserService::class.java))
            c.beanFactory.registerSingleton("binder",mock(BusinessAttachmentBinder::class.java))
            c.beanFactory.registerSingleton("calendar",mock(CollectionCalendarService::class.java))
            c.beanFactory.registerSingleton("packets",mock(CollectionArchivePacketService::class.java))
            c.register(Transactions::class.java,io.hizhengfu.cloud.common.file.FileReadAccess::class.java,CollectionUserResolver::class.java,CollectionBusinessItemService::class.java,
                CollectionPermanentArchiveService::class.java,RequisitionStatutoryService::class.java,LoanStatutoryService::class.java,
                ReturnStatutoryService::class.java,RequisitionService::class.java,LoanService::class.java,CollectionReturnService::class.java)
            c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager))
            r.db {
                originalReaderSchema()
                SchemaUtils.create(CollectionOrgs,Collections,Requisitions,Loans,CollectionReturns,CollectionBusinessItems,
                    LoanComplianceRecords,ReturnComplianceRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
                listOf("museum","other").forEach {id->CollectionOrgDao.new(id){name=id;code=id;systemGroupId=id}}
                originalReader("reader",CollectionConst.STORAGE)
                CollectionDao.new("one"){orgId="museum";name="本馆藏品";totalRegNo="M-1";lifeStatus=EnumCollectionStatus.IN_STORE}
                CollectionDao.new("foreign"){orgId="other";name="外馆受限藏品";totalRegNo="O-1";lifeStatus=EnumCollectionStatus.IN_STORE}
                RequisitionDao.new("requisition"){orgId="museum";documentNo="RQ-1";collectionIds="one"}
                LoanDao.new("loan"){orgId="museum";title="借用核对";collectionIds="one"}
                CollectionReturnDao.new("return"){orgId="museum";sourceBizType=CollectionConst.BIZ_REQUISITION;sourceBizId="requisition";sourceDocumentNo="RQ-1";collectionIds="one"}
            }
            body(r)
        }} finally {SecurityContextHolder.clearContext()}
    }
    @ParameterizedTest @ValueSource(strings=["missing","changed"])
    fun `return detail retains facts and healthy stages when an original is unavailable`(damage:String)=fixture {r ->
        val archives=r.c.getBean(CollectionPermanentArchiveService::class.java)
        `when`(r.c.getBean(BusinessAttachmentBinder::class.java).resolveSingle("proof")).thenReturn("proof")
        val path=dir.resolve("proof.txt")
        java.nio.file.Files.writeString(path,"ORIGINAL")
        r.db {FileDao.new("proof") {name="原件.txt";this.path="proof.txt";ext="txt";size=8;hash="proof"}}
        val damaged=archives.seal("museum",CollectionConst.BIZ_RETURN,"return","RECORDED",mapOf("fact" to "return"),"proof")
        val healthy=archives.seal("museum",CollectionConst.BIZ_RETURN,"return","UNAFFECTED",mapOf("fact" to "healthy"),null)
        val before=r.db {CollectionPermanentArchiveFileDao.all().map {it.metadata}}
        if(damage=="missing")java.nio.file.Files.delete(path) else java.nio.file.Files.writeString(path,"CHANGED!")
        val detail=r.get("return") as CollectionReturnVo
        assertEquals("return",detail.id);assertEquals("one",detail.collectionIds)
        assertEquals(1,detail.evidenceErrors.size);assertEquals(2,detail.archives.size)
        assertUnavailableOriginals(detail.archives)
        assertEquals(healthy,archives.archiveForAuthorizedOrg(CollectionConst.BIZ_RETURN,"return","museum",healthy)["id"])
        assertThrows(BadRequestException::class.java) {r.c.getBean(CollectionReturnService::class.java).requireArchivedOriginal("return",damaged,"proof")}
        assertEquals(before,r.db {CollectionPermanentArchiveFileDao.all().map {it.metadata}})
        r.permissions()
        assertThrows(BadRequestException::class.java) {r.get("return")}
    }
    @ParameterizedTest @CsvSource("requisition,detail","requisition,page","requisition,flow","loan,detail","loan,page","loan,flow","return,detail","return,page","return,flow")
    fun `circulation current permission removal invalidates old administrator reads`(kind:String,view:String)=fixture {r ->
        assertNotNull(r.read(kind,view))
        r.permissions()
        assertThrows(BadRequestException::class.java){r.read(kind,view)}
    }
    private fun biz(kind:String)=when(kind) {"requisition"->CollectionConst.BIZ_REQUISITION;"loan"->CollectionConst.BIZ_LOAN;else->CollectionConst.BIZ_RETURN}
    private fun summaries(value:Any?):List<CollectionSummaryVo> = when(value) {
        is RequisitionVo -> value.collectionSummaries
        is LoanVo -> value.collectionSummaries
        is CollectionReturnVo -> value.collectionSummaries
        else -> error("unexpected view")
    }
    @ParameterizedTest @CsvSource("requisition,detail","requisition,page","requisition,flow","loan,detail","loan,page","loan,flow","return,detail","return,page","return,flow")
    fun `foreign original header never opens through circulation reads`(kind:String,view:String)=fixture {r ->
        r.db { CollectionPermanentArchiveDao.new("foreign-original") {orgId="other";bizType=biz(kind);bizId=kind;phase="SUBMITTED";snapshot="{}";digest=CollectionCareEvidence.digest(snapshot)} }
        val count=r.db {CollectionPermanentArchiveDao.count()}
        val e=assertThrows(BadRequestException::class.java){r.read(kind,view)}
        assertTrue(e.message!!.contains("所属馆"));assertEquals(count,r.db {CollectionPermanentArchiveDao.count()})
    }
    @ParameterizedTest @ValueSource(strings=["requisition","loan","return"])
    fun `current cross permission exposes owned history but not foreign current collection labels`(kind:String)=fixture(CollectionScopeResolver("multi","")) {r ->
        r.permissions(CollectionConst.STORAGE,CollectionConst.CROSS_ORG)
        assertEquals("本馆藏品",summaries(r.get(kind)).single().name)
        r.c.getBean(CollectionPermanentArchiveService::class.java).seal("museum",biz(kind),kind,"HISTORY",mapOf("name" to "本馆藏品"),null)
        val before=r.db {CollectionPermanentArchiveDao.all().map {it.snapshot to it.digest}}
        r.db {CollectionDao["one"].orgId="other";CollectionDao["one"].name="移交后名称"}
        assertTrue(summaries(r.get(kind)).isEmpty())
        assertEquals(before,r.db {CollectionPermanentArchiveDao.all().map {it.snapshot to it.digest}})
        r.permissions()
        assertThrows(BadRequestException::class.java){r.get(kind)}
    }
    @ParameterizedTest @ValueSource(strings=["requisition","loan","return"])
    fun `keyword joins enforce both index and collection owner while exact id finds retained history`(kind:String)=fixture(CollectionScopeResolver("multi","")) {r ->
        r.permissions(CollectionConst.STORAGE,CollectionConst.CROSS_ORG)
        r.db {CollectionBusinessItemDao.new {orgId="museum";bizType=biz(kind);bizId=kind;collectionId="one"}}
        assertEquals(1,(r.page(kind,"本馆藏品").list as List<*>).size)
        r.db {CollectionDao["one"].orgId="other";CollectionDao["one"].name="移交后受限名称"}
        assertTrue((r.page(kind,"移交后受限名称").list as List<*>).isEmpty())
        assertEquals(1,(r.page(kind,collectionId="one").list as List<*>).size)
        r.db {CollectionBusinessItemDao.new {orgId="other";bizType=biz(kind);bizId=kind;collectionId="foreign"}}
        assertTrue((r.page(kind,"外馆受限藏品").list as List<*>).isEmpty())
        assertTrue((r.page(kind,collectionId="foreign").list as List<*>).isEmpty())
    }
    @ParameterizedTest @CsvSource("requisition,detail","requisition,page","requisition,flow","loan,detail","loan,page","loan,flow")
    fun `foreign source cannot be displayed as a valid return`(source:String,view:String)=fixture {r ->
        r.db {
            if(source=="loan")LoanDao["loan"].orgId="other" else RequisitionDao["requisition"].orgId="other"
            CollectionReturnDao["return"].apply {sourceBizType=biz(source);sourceBizId=source}
        }
        val e=assertThrows(BadRequestException::class.java){r.read("return",view)}
        assertTrue(e.message!!.contains("原提借用业务"))
    }
    @ParameterizedTest @CsvSource("requisition,detail","requisition,page","loan,detail","loan,page")
    fun `foreign executed return cannot change original business progress`(kind:String,view:String)=fixture {r ->
        r.db {CollectionReturnDao["return"].apply {orgId="other";sourceBizType=biz(kind);sourceBizId=kind;flowStatus=EnumFlowStatus.PASS;executionState=EnumReturnExecutionState.EXECUTED}}
        assertThrows(BadRequestException::class.java){r.read(kind,view)}
    }
    @ParameterizedTest @ValueSource(strings=["loan","return"])
    fun `administrative record owner is checked before reading its content`(kind:String)=fixture {r ->
        r.db {if(kind=="loan")LoanComplianceRecordDao.new {orgId="other";loanId="loan";this.kind="ADMIN";content="not-json"}
        else ReturnComplianceRecordDao.new {orgId="other";returnId="return";this.kind="REPORT";content="not-json"}}
        val e=assertThrows(BadRequestException::class.java){r.get(kind)}
        assertTrue(e.message!!.contains("所属馆与原业务不一致"))
    }
    @Test fun `accepted ids must belong to this return even when they exist in another museum`()=fixture {r ->
        r.db {CollectionReturnDao["return"].acceptedCollectionIds="foreign"}
        assertThrows(BadRequestException::class.java){r.get("return")}
    }
    @Test fun `legitimate partial legacy return keeps progress after collection ownership changes`()=fixture {r ->
        r.db {
            CollectionDao.new("two"){orgId="museum";name="第二件";totalRegNo="M-2";lifeStatus=EnumCollectionStatus.IN_STORE}
            RequisitionDao["requisition"].collectionIds="one,two"
            CollectionReturnDao["return"].apply {flowStatus=EnumFlowStatus.PASS;executionState=EnumReturnExecutionState.EXECUTED}
            CollectionDao["one"].orgId="other"
        }
        val result=r.get("requisition") as RequisitionVo
        assertEquals(listOf("one"),result.returnedCollectionIds)
        assertEquals(listOf("two"),result.collectionSummaries.map {it.id})
        assertNotNull(r.get("return"))
    }

    @ParameterizedTest @ValueSource(strings=["requisition","loan","return"])
    fun `current group and disabled account override stale administrator session`(kind:String)=fixture {r ->
        r.db {UserDao["reader"].groupId="other"}
        assertThrows(BadRequestException::class.java){r.get(kind)}
        assertThrows(BadRequestException::class.java){r.read(kind,"flow")}
        assertTrue((r.page(kind).list as List<*>).isEmpty())
        r.permissions(CollectionConst.STORAGE,CollectionConst.CROSS_ORG)
        assertNotNull(r.get(kind))
        r.db {UserDao["reader"].enabled=false}
        for(view in listOf("detail","page","flow"))assertThrows(BadRequestException::class.java){r.read(kind,view)}
    }
    @ParameterizedTest @ValueSource(strings=["requisition","loan","return"])
    fun `single museum configuration still limits current cross readers`(kind:String)=fixture {r ->
        r.permissions(CollectionConst.STORAGE,CollectionConst.CROSS_ORG)
        r.db {RequisitionDao["requisition"].orgId="other";LoanDao["loan"].orgId="other";CollectionReturnDao["return"].orgId="other"}
        assertThrows(BadRequestException::class.java){r.get(kind)}
        assertThrows(BadRequestException::class.java){r.read(kind,"flow")}
        assertTrue((r.page(kind).list as List<*>).isEmpty())
    }

}
