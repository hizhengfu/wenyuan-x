package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileObjectStorage
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.springframework.context.annotation.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path

class CollectionCareDigitalAccessRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true)
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity class Transactions
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate) {
        val main=c.getBean(ReproductionService::class.java)
        val care=c.getBean(CollectionCareService::class.java)
        val physical=c.getBean(ReproductionComplianceService::class.java)
        val digital=c.getBean(DigitalAcquisitionService::class.java)
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun permissions(vararg values:String)=db {RolePermissions.deleteAll();actorRole("reader","FILE_reader",values.toSet())}
        fun read(view:String):Any?=when(view) {
            "care"->care.detail("piece")
            "care-history"->care.history("piece","museum")
            "physical"->physical.detail("physical")
            "units"->physical.units("physical")
            "physical-form"->db {physical.formData(ReproductionDao["physical"])}
            "digital"->digital.detail("digital")
            else->db {digital.formData(ReproductionDao["digital"])}
        }
    }
    private fun fixture(mode:String="single",body:(Runtime)->Unit) {
        val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("access.db")}"},DatabaseConfig {})
        val login=SecurityUser(id="reader",name="读取人员",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf("ADMIN"),roleCodes=setOf(CollectionConst.ROLE_INFORMATION),authorities=listOf("ADMIN",CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(login,null,login.authorities)
        try {AnnotationConfigApplicationContext().use {c ->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("scope",CollectionScopeResolver(mode,"museum"))
            c.beanFactory.registerSingleton("users",mock(CollectionResponsibleUserService::class.java))
            c.beanFactory.registerSingleton("archives",mock(CollectionPermanentArchiveService::class.java))
            c.beanFactory.registerSingleton("originals",mock(CollectionOriginalAccessService::class.java))
            c.beanFactory.registerSingleton("locks",mock(CollectionOperationLockService::class.java))
            c.beanFactory.registerSingleton("incidents",mock(CollectionIncidentService::class.java))
            c.beanFactory.registerSingleton("storage",mock(FileObjectStorage::class.java))
            c.beanFactory.registerSingleton("flow",mock(io.hizhengfu.cloud.flow.service.FlowInstanceService::class.java))
            c.beanFactory.registerSingleton("binder",mock(BusinessAttachmentBinder::class.java))
            c.beanFactory.registerSingleton("cacheManager",org.springframework.cache.concurrent.ConcurrentMapCacheManager())
            // No qualification module: even the empty units response must check current authority.
            c.register(Transactions::class.java,ReproductionService::class.java,CollectionCareService::class.java,ReproductionComplianceService::class.java,DigitalAcquisitionService::class.java,io.hizhengfu.cloud.collection.api.DigitalAcquisitionController::class.java)
            c.refresh();io.hizhengfu.cloud.common.utils.SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager))
            r.db {
                originalReaderSchema()
                SchemaUtils.create(CollectionOrgs,Collections,Reproductions,CollectionCarePlans,CollectionCareChecks,CollectionCareWorks,ReproductionPlans,DigitalAcquisitionPlans)
                listOf("museum","other").forEach {id->CollectionOrgDao.new(id){name=id;code=id;systemGroupId=id}}
                originalReader("reader",CollectionConst.CARE)
                actorRole("reader","FILE_reader",setOf(CollectionConst.STORAGE))
                CollectionDao.new("piece"){orgId="museum";name="本馆藏品";totalRegNo="M-1";lifeStatus=EnumCollectionStatus.IN_STORE;nature=EnumCollectionNature.OTHER}
                ReproductionDao.new("physical"){orgId="museum";collectionId="piece";repType=EnumRepType.COPY;purpose="研究";repDate=java.time.LocalDate.of(2026,9,1)}
                ReproductionDao.new("digital"){orgId="museum";collectionId="piece";repType=EnumRepType.SCAN_3D;purpose="数字保护";repDate=java.time.LocalDate.of(2026,9,1)}
            }
            body(r)
        }} finally {SecurityContextHolder.clearContext()}
    }
    @ParameterizedTest @ValueSource(strings=["care","care-history","physical","physical-form","units","digital","digital-form"])
    fun `revoked current permissions cannot reuse old administrator login`(view:String)=fixture {r ->
        assertDoesNotThrow {r.read(view)}
        r.permissions()
        assertThrows(BadRequestException::class.java){r.read(view)}
    }
    @ParameterizedTest @ValueSource(strings=["care","care-history","physical","physical-form","units","digital","digital-form"])
    fun `disabled current account cannot read business material`(view:String)=fixture {r ->
        r.db {UserDao["reader"].enabled=false}
        assertThrows(BadRequestException::class.java){r.read(view)}
    }
    @ParameterizedTest @ValueSource(strings=["care","care-history","physical","physical-form","units","digital","digital-form"])
    fun `current museum membership is required even in single mode`(view:String)=fixture {r ->
        r.db {UserDao["reader"].groupId="other"}
        assertThrows(BadRequestException::class.java){r.read(view)}
        r.permissions(CollectionConst.CARE,CollectionConst.STORAGE,CollectionConst.CROSS_ORG)
        assertDoesNotThrow {r.read(view)}
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `configured single museum remains bounded while explicit multi cross grant reads`(mode:String)=fixture(mode) {r ->
        r.permissions(CollectionConst.CARE,CollectionConst.STORAGE,CollectionConst.CROSS_ORG)
        r.db {CollectionDao["piece"].orgId="other";ReproductionDao["physical"].orgId="other";ReproductionDao["digital"].orgId="other"}
        listOf("care","physical","physical-form","units","digital","digital-form").forEach {view->
            if(mode=="single")assertThrows(BadRequestException::class.java){r.read(view)} else assertDoesNotThrow {r.read(view)}
        }
    }
    @Test fun `physical view permission does not authorize care or digital operations`()=fixture {r ->
        r.permissions(CollectionConst.SELECT)
        assertDoesNotThrow {r.read("physical")};assertDoesNotThrow {r.read("units")}
        assertThrows(BadRequestException::class.java){r.read("care")}
        assertThrows(BadRequestException::class.java){r.read("digital")}
    }
    @ParameterizedTest @ValueSource(strings=[CollectionConst.ROLE_INFORMATION,CollectionConst.ROLE_STORAGE_HEAD])
    fun `current digital job role works and role revocation takes effect`(code:String)=fixture {r ->
        r.permissions()
        r.db {actorRole("reader",code)}
        assertDoesNotThrow {r.read("digital")}
        r.db {RoleUsers.deleteAll()}
        assertThrows(BadRequestException::class.java){r.read("digital")}
    }
    @ParameterizedTest @ValueSource(strings=[CollectionConst.ROLE_INFORMATION,CollectionConst.ROLE_STORAGE_HEAD])
    fun `secured controller uses current digital role rather than old login permission`(code:String)=fixture {r ->
        r.permissions();r.db {actorRole("reader",code)}
        val login=SecurityUser(id="reader",name="当前岗位人员",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.SELECT)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(login,null,login.authorities)
        val controller=r.c.getBean(io.hizhengfu.cloud.collection.api.DigitalAcquisitionController::class.java)
        assertDoesNotThrow {controller.detail("digital")}
        r.db {RoleUsers.deleteAll()}
        assertThrows(BadRequestException::class.java){controller.detail("digital")}
    }
    @Test fun `secured digital controller still refuses unauthenticated access`()=fixture {r ->
        SecurityContextHolder.clearContext()
        val controller=r.c.getBean(io.hizhengfu.cloud.collection.api.DigitalAcquisitionController::class.java)
        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException::class.java){controller.detail("digital")}
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `digital detail reports current write scope independently of cross viewing`(mode:String)=fixture(mode) {r ->
        assertEquals(true,r.digital.detail("digital")["canOperate"])
        r.db {UserDao["reader"].groupId="other"}
        r.permissions(CollectionConst.STORAGE,CollectionConst.CROSS_ORG)
        assertEquals(false,r.digital.detail("digital")["canOperate"])
        r.db {UserDao["reader"].groupId="museum"}
        assertEquals(true,r.digital.detail("digital")["canOperate"])
        r.db {CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED}
        assertEquals(false,r.digital.detail("digital")["canOperate"])
    }
    @ParameterizedTest @ValueSource(strings=["revoked","disabled","legacy-flow"])
    fun `main reproduction reads cannot reuse obsolete login authority`(kind:String)=fixture {r ->
        assertEquals(2L,r.main.findPage(org.springframework.mock.web.MockHttpServletRequest()).total)
        if(kind=="disabled")r.db {UserDao["reader"].enabled=false} else r.permissions()
        if(kind=="legacy-flow") {
            r.db {ReproductionDao["physical"].repType=null}
            assertThrows(BadRequestException::class.java){r.main.getFlowInfo("physical")}
        } else assertThrows(BadRequestException::class.java){r.main.findPage(org.springframework.mock.web.MockHttpServletRequest())}
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `main reproduction page obeys current scope and pairs collection lookup by museum`(mode:String)=fixture(mode) {r ->
        r.permissions(CollectionConst.SELECT)
        r.db {UserDao["reader"].groupId="other"}
        assertEquals(0L,r.main.findPage(org.springframework.mock.web.MockHttpServletRequest()).total)
        r.permissions(CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        r.db {CollectionDao["piece"].orgId="other";CollectionDao["piece"].name="接收馆私有名称"}
        val page=r.main.findPage(org.springframework.mock.web.MockHttpServletRequest())
        @Suppress("UNCHECKED_CAST") val rows=page.list as List<ReproductionVo>
        assertEquals(2L,page.total);assertTrue(rows.all {it.collectionName==null && it.collectionTotalRegNo==null})
        val search=org.springframework.mock.web.MockHttpServletRequest().apply {addParameter("keyword","接收馆私有名称")}
        assertEquals(0L,r.main.findPage(search).total)
        r.db {ReproductionDao["physical"].orgId="other";ReproductionDao["digital"].orgId="other"}
        assertEquals(if(mode=="single")0L else 2L,r.main.findPage(org.springframework.mock.web.MockHttpServletRequest()).total)
    }
    @ParameterizedTest @ValueSource(strings=["create","save"])
    fun `main reproduction direct draft writes need current operation duty`(kind:String)=fixture {r ->
        val value=r.db {ReproductionDao["physical"].toModel().copy(purpose="本次改写")}
        r.permissions(CollectionConst.SELECT)
        assertThrows(BadRequestException::class.java){r.main.save(if(kind=="create")value.copy(id=null)else value)}
        r.db {assertEquals(2L,ReproductionDao.count());assertEquals("研究",ReproductionDao["physical"].purpose)}
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `physical operation flag requires current duty actual museum and existing session gate`(mode:String)=fixture(mode) {r ->
        r.permissions(CollectionConst.SELECT)
        assertEquals(false,r.physical.detail("physical")["canOperate"])
        r.permissions(CollectionConst.STORAGE)
        assertEquals(true,r.physical.detail("physical")["canOperate"])
        r.db {UserDao["reader"].groupId="other"}
        r.permissions(CollectionConst.STORAGE,CollectionConst.CROSS_ORG)
        assertEquals(false,r.physical.detail("physical")["canOperate"])
        r.db {UserDao["reader"].groupId="museum"}
        val login=SecurityUser(id="reader",name="权限已变化",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.SELECT)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(login,null,login.authorities)
        assertEquals(false,r.physical.detail("physical")["canOperate"])
    }
    @Test fun `multi museum editor cannot move an existing reproduction record to another museum`()=fixture("multi") {r ->
        r.permissions(CollectionConst.STORAGE)
        r.db {
            UserGroups.insert {it[UserGroups.userId]="reader";it[UserGroups.groupId]="other"}
            CollectionDao.new("foreign"){orgId="other";name="另一馆藏品";totalRegNo="O-1";lifeStatus=EnumCollectionStatus.IN_STORE}
        }
        val original=r.db {ReproductionDao["physical"].toModel()}
        assertThrows(BadRequestException::class.java){r.main.save(original.copy(collectionId="foreign"))}
        r.db {assertEquals(original,ReproductionDao["physical"].toModel())}
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `care history uses explicit business museum within configured and current scope`(mode:String)=fixture(mode) {r ->
        assertThrows(BadRequestException::class.java){r.care.history("piece","other")}
        r.permissions(CollectionConst.CARE,CollectionConst.CROSS_ORG)
        if(mode=="single")assertThrows(BadRequestException::class.java){r.care.history("piece","other")}
        else assertEquals(false,r.care.history("piece","other")["canOperate"])
        r.permissions(CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        assertThrows(BadRequestException::class.java){r.care.history("piece","museum")}
    }

}
