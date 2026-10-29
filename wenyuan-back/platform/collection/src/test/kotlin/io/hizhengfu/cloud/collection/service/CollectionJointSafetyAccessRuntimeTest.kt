package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.context.annotation.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

class CollectionJointSafetyAccessRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate) {
        val service=c.getBean(CollectionJointSafetyService::class.java)
        val archives=c.getBean(CollectionPermanentArchiveService::class.java)
        fun <T> db(body:()->T):T=tx.execute {body()}
        @Suppress("UNCHECKED_CAST") fun rows(keyword:String="",size:Int=0)=page(keyword,size).list as List<Map<String,Any?>>
        fun page(keyword:String="",size:Int=0)=service.page(MockHttpServletRequest().apply {setParameter("keyword",keyword);setParameter("pageSize",size.toString())})
        fun value(revision:String="s"):CollectionJointSafetyPlanVo {
            val at=LocalDateTime.now().minusSeconds(1)
            return CollectionJointSafetyPlanVo(sourceRevision=revision,preparedAt=at,inspectionIntervalDays=1,firstDueAt=at.plusHours(2),keeperUserId="keeper",useUserId="reader",securityUserId="security",directorUserId="director",scopeAndMethod="实际范围",environmentalAndSecurityChecks="现场措施",measuresAndReporting="报告安排",planFileIds="proof")
        }
        fun save(org:String?="museum")=service.savePlan("req",value(service.detail("req")["sourceRevision"] as String),org)
        fun writes(org:String?=null):List<()->Any?> {
            val at=LocalDateTime.now();val sign=CollectionCareSignatureVo("本人意见","proof")
            return listOf({service.savePlan("req",value(),org)}, {service.signPlan("req","missing","KEEPER",sign,org)},
                {service.recordRound("req",CollectionJointSafetyRoundVo("missing",at,at,setOf("missing"),"实际","实际","实际","proof"),org)},
                {service.signRound("req","missing","KEEPER",sign,org)},
                {service.report("req","missing",CollectionJointSafetyReportVo(at,"reader","director","实际","实际","proof"),org)},
                {service.acknowledge("req","missing",sign,org)})
        }
    }
    private fun login(permission:String="ADMIN") {
        val u=SecurityUser(id="reader",name="旧登录",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf(SimpleGrantedAuthority(permission)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(u,null,u.authorities)
    }
    private fun fixture(mode:String="single",body:(Runtime)->Unit) {
        login()
        val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("joint-${System.nanoTime()}.db")}"})
        AnnotationConfigApplicationContext().use {c ->
            c.beanFactory.registerSingleton("transactionManager",manager);c.beanFactory.registerSingleton("scope",CollectionScopeResolver(mode,"museum"))
            c.beanFactory.registerSingleton("users",mock(CollectionResponsibleUserService::class.java))
            val binder=mock(BusinessAttachmentBinder::class.java).also {`when`(it.resolveSingle(anyString())).thenAnswer {call->call.getArgument<String>(0)}}
            c.beanFactory.registerSingleton("archives",spy(CollectionPermanentArchiveService(binder,c.beanFactory.getBean("scope",CollectionScopeResolver::class.java),
                readAccess=unprotectedArchiveReadAccessProvider())))
            c.register(Transactions::class.java,CollectionJointSafetyService::class.java);c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager))
            try {
                r.db {
                    originalReaderSchema();originalReader("reader",CollectionConst.CARE)
                    SchemaUtils.create(CollectionOrgs,Collections,Requisitions,CollectionJointSafetyPlans,CollectionJointSafetyRounds,CollectionPermanentArchives,CollectionPermanentArchiveFiles,io.hizhengfu.cloud.sys.file.dao.Files,CollectionCareChecks,CollectionReturns)
                    io.hizhengfu.cloud.sys.file.dao.FileDao.new("proof"){name="签署原件.pdf";path="proof";hash="proof";size=100;ext="pdf"}
                    for(org in listOf("museum","other")) {
                        CollectionOrgDao.new(org){name=org;code=org;systemGroupId=org}
                        val cid=if(org=="museum")"piece" else "foreign"
                        CollectionDao.new(cid){orgId=org;name=if(org=="museum")"本馆藏品" else "秘密外馆名称";totalRegNo=cid}
                        RequisitionDao.new(if(org=="museum")"req" else "foreign-req") {orgId=org;collectionIds=cid;documentNo=org;purpose=EnumMoveReason.EXHIBIT;dossier=RequisitionDossierVo("dept","department","keeper","director","reader","实际","实际","实际","实际","proof",LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(30),transportRequired=false,transportDecisionReason="本馆原位提用")}
                    }
                }
                body(r)
            } finally {SecurityContextHolder.clearContext()}
        }
    }
    @ParameterizedTest @ValueSource(strings=["revoked","disabled","role","group","moved","museum"])
    fun `all six writes and detail reject stale authority and page never retains old museum`(kind:String)=fixture {r->
        r.db {when(kind){"revoked"->RolePermissions.deleteAll();"disabled"->UserDao["reader"].enabled=false;"role"->RoleDao.find {Roles.code eq "FILE_reader"}.single().enabled=false;"group"->GroupDao["museum"].enabled=false;"moved"->UserDao["reader"].groupId="other";"museum"->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED}}
        (r.writes()+listOf({r.service.detail("req")})).forEach {call->assertThrows(BadRequestException::class.java){call()}}
        if(kind in setOf("revoked","disabled","role"))assertThrows(BadRequestException::class.java){r.page()} else assertEquals(0,r.page().total)
        verifyNoInteractions(r.archives)
    }
    @Test fun `anonymous requests cannot query or write`()=fixture {r->
        SecurityContextHolder.clearContext();(r.writes()+listOf({r.service.detail("req")},{r.page()})).forEach {call->assertThrows(BadRequestException::class.java){call()}}
    }
    @Test fun `same museum authorized user can read and save confirmed plan`()=fixture {r->
        assertEquals(true,r.service.detail("req")["canOperate"]);assertEquals("museum",r.service.detail("req")["orgId"])
        assertEquals(1,r.page().total);val id=r.save();r.db {assertEquals("museum",CollectionJointSafetyPlanDao[id].orgId)}
    }
    @Test fun `actual reassignment uses current groups and page count and search agree`()=fixture("multi") {r->
        r.db {UserDao["reader"].groupId="other"};assertEquals(1,r.page().total);assertEquals("foreign-req",r.rows().single()["requisitionId"])
        assertEquals(0,r.page("本馆藏品").total);assertEquals(1,r.rows("秘密外馆名称",1).size)
        assertThrows(BadRequestException::class.java){r.service.detail("req")};assertEquals(true,r.service.detail("foreign-req")["canOperate"])
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `explicit cross read never grants foreign writing`(mode:String)=fixture(mode) {r->
        r.db {actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))}
        assertEquals(if(mode=="single")1 else 2,r.page().total.toInt())
        if(mode=="single")assertThrows(BadRequestException::class.java){r.service.detail("foreign-req")} else assertEquals(false,r.service.detail("foreign-req")["canOperate"])
        assertThrows(BadRequestException::class.java){r.service.savePlan("foreign-req",r.value(),"other")}
    }
    @Test fun `disabled museum historical access is read only`()=fixture("multi") {r->
        r.db {actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG));CollectionOrgDao["other"].orgStatus=EnumOrgStatus.DISABLED}
        assertEquals(2,r.page().total);assertEquals(false,r.service.detail("foreign-req")["canOperate"])
        assertThrows(BadRequestException::class.java){r.service.savePlan("foreign-req",r.value(),"other")}
    }
    @Test fun `new permission does not claim old HTTP authority is refreshed`()=fixture {r->login(CollectionConst.SELECT);assertEquals(false,r.service.detail("req")["canOperate"]);login(CollectionConst.CARE);assertEquals(true,r.service.detail("req")["canOperate"])}
    @Test fun `dual museum operator must retain the museum confirmed before same request changed org`()=fixture("multi") {r->
        r.db {UserGroups.insert {it[userId]="reader";it[groupId]="other"};RequisitionDao["req"].orgId="other"}
        r.writes("museum").forEach {call->assertTrue(assertThrows(BadRequestException::class.java){call()}.message!!.contains("收藏单位已变化"))};verifyNoInteractions(r.archives)
    }
    @Test fun `blank confirmation is not a bypass`()=fixture {r->assertThrows(BadRequestException::class.java){r.save("")};verifyNoInteractions(r.archives)}
    @ParameterizedTest @ValueSource(strings=["permission","org"])
    fun `sealing changed identity rolls back plan and mutation`(kind:String)=fixture("multi") {r->
        r.db {UserGroups.insert {it[userId]="reader";it[groupId]="other"}}
        doAnswer {if(kind=="permission")RolePermissions.deleteAll() else RequisitionDao["req"].orgId="other";"archive"}.`when`(r.archives).seal(anyString(),anyString(),anyString(),anyString(),anyMap<String,Any>(),nullable(String::class.java))
        assertThrows(BadRequestException::class.java){r.save()};r.db {assertEquals(0,CollectionJointSafetyPlanDao.all().count());assertEquals("museum",RequisitionDao["req"].orgId);assertTrue(RolePermissions.selectAll().count()>0)}
    }
    @Test fun `foreign current piece names cannot leak through historic request or error fallback`()=fixture {r->
        r.db {RequisitionDao["req"].collectionIds="foreign";RequisitionDao["req"].dossier=null}
        assertNull(r.rows().single()["name"]);assertEquals(0,r.page("秘密外馆名称").total)
        r.db {RequisitionDao["req"].outExecuted=true;RequisitionDao["req"].dossier=RequisitionDao["foreign-req"].dossier}
        val row=r.rows().single();assertEquals("ORIGINAL_ERROR",row["status"]);assertNull(row["name"])
    }
    @ParameterizedTest @ValueSource(strings=["plan","round"])
    fun `wrong museum records reject before malformed private JSON is parsed`(kind:String)=fixture {r->
        r.db {
            if(kind=="plan")CollectionJointSafetyPlanDao.new("bad"){orgId="other";requisitionId="req";version=1;sourceSnapshot="private";content=r.value()}
            else CollectionJointSafetyRoundDao.new("bad"){orgId="other";requisitionId="req";planId="bad-plan";checksSnapshot="private";content=CollectionJointSafetyRoundVo("bad-plan",LocalDateTime.now(),LocalDateTime.now(),emptySet(),"private","private","private","private")}
            val tx=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current();tx.entityCache.flush();val table=if(kind=="plan")"col_joint_safety_plan" else "col_joint_safety_round"
            tx.exec("UPDATE $table SET content = 'invalid-private-json' WHERE id = 'bad'")
            tx.exec("SELECT content FROM $table WHERE id = 'bad'"){row->assertTrue(row.next());assertEquals("invalid-private-json",row.getString(1))}
        }
        assertTrue(assertThrows(BadRequestException::class.java){r.service.detail("req")}.message!!.contains("子记录"))
        assertEquals("ORIGINAL_ERROR",r.rows().single()["status"])
    }
}
