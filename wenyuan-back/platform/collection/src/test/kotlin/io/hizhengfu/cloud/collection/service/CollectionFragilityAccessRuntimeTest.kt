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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

class CollectionFragilityAccessRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate) {
        val service=c.getBean(CollectionFragilityService::class.java)
        val monitoring=c.getBean(CollectionFragileMonitoringService::class.java)
        val adjustment=c.getBean(CollectionFragileAdjustmentService::class.java)
        val scope=c.getBean(CollectionScopeResolver::class.java)
        val archives=c.getBean(CollectionPermanentArchiveService::class.java)
        val provider=c.getBean(CollectionFragileEnvironmentProvider::class.java)
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun damage(kind:String)=db {when(kind) {
            "revoked"->RolePermissions.deleteAll()
            "disabled"->UserDao["reader"].enabled=false
            "role"->RoleDao.find {Roles.code eq "FILE_reader"}.single().enabled=false
            "group"->GroupDao["museum"].enabled=false
            "museum"->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
            "moved"->UserDao["reader"].groupId="other"
            "configured"->ReflectionTestUtils.setField(scope,"defaultOrgId","other")
        }}
        fun history(id:String="piece",expectedOrgId:String?=null)=service.history(id,historyValue(),expectedOrgId)
        fun historyValue()=FragileUseHistoryVo(EnumFragileUseKind.DISPLAY,LocalDateTime.now().minusDays(2),LocalDateTime.now().minusDays(1),"实际历史依据","据原件补录历史展出事实","proof")
        fun entries(expectedOrgId:String?=null):Map<String,()->Any?> {
            val at=LocalDateTime.now().minusMinutes(1);val sign=CollectionCareSignatureVo("本人意见","proof")
            return linkedMapOf(
                "detail" to {service.detail("piece")}, "sources" to {service.environmentSources("piece")},
                "source" to {service.source("piece",CollectionConst.BIZ_REQUISITION,"missing")},
                "status" to {db {monitoring.automaticStatus("piece")}},
                "automatic-page" to {db {monitoring.automaticPage("piece","missing",1,20)}},
                "samples" to {db {monitoring.samples("piece","missing",1,20)}},
                "automatic-samples" to {db {monitoring.samples("piece","missing",1,20,true)}},
                "assessment" to {service.assess("piece",FragilityAssessmentVo(sourceRevision="s",assessedAt=at,fragileMaterial=false,materialAndCondition="实际",environmentRequirements="实际",storageProtection="实际",useRiskAssessment="实际",conservationUserId="reader",keeperUserId="keeper",historyVerifiedFrom=at,historyReviewFileIds="proof",assessmentFileIds="proof"),expectedOrgId)},
                "plan" to {service.savePlan("piece",FragileUsePlanVo("missing",CollectionConst.BIZ_REQUISITION,"missing","s",environmentAndProtection="实际",necessityAndAlternatives="实际",signedPlanFileIds="proof"),expectedOrgId)},
                "sign" to {service.sign("piece","missing",sign,expectedOrgId)}, "history" to {history(expectedOrgId=expectedOrgId)},
                "start" to {service.start("piece","missing",FragileUseStartVo(EnumFragileUseKind.DISPLAY,startedAt=at,operatorUserId="reader",actualLocationOrRoute="实际",actualEnvironmentAndProtection="实际",signedFileIds="proof"),expectedOrgId)},
                "end" to {service.end("piece","missing",FragileUseEndVo(at,"实际","实际","proof"),expectedOrgId)},
                "review" to {service.review("piece","missing",FragileUseReviewVo("missing","proof"),expectedOrgId)},
                "inspect" to {monitoring.inspect("piece","missing",FragileEnvironmentMonitoringVo(at,"reader",true,"reader","实际","实际",false,"实际","实际","proof"),expectedOrgId)},
                "clear" to {monitoring.clear("piece","missing",IncidentClearanceVo("missing",at,"conservation","keeper","实际","实际","proof"),expectedOrgId)},
                "adjust" to {adjustment.propose("piece","missing",FragileUseAdjustmentVo(factsRevision="s",reviewedAt=at,checkId="missing",reason="实际",actualUnstartedEvidence="实际",adjustments=emptyList(),signedFileIds="proof"),expectedOrgId)},
                "adjust-sign" to {adjustment.sign("piece","missing","KEEPER",sign,expectedOrgId)},
                "withdraw" to {adjustment.withdraw("piece","missing",FragileAdjustmentWithdrawalVo(at,"实际","proof"),expectedOrgId)},
            )
        }
    }
    private fun login(permission:String="ADMIN") {
        val u=SecurityUser(id="reader",name="旧登录",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf(SimpleGrantedAuthority(permission)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(u,null,u.authorities)
    }
    private fun fixture(mode:String="single",body:(Runtime)->Unit) {
        login()
        val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("fragility-${System.nanoTime()}.db")}"})
        AnnotationConfigApplicationContext().use {c ->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("scope",CollectionScopeResolver(mode,"museum"))
            c.beanFactory.registerSingleton("users",mock(CollectionResponsibleUserService::class.java))
            c.beanFactory.registerSingleton("archives",mock(CollectionPermanentArchiveService::class.java))
            c.beanFactory.registerSingleton("locks",CollectionOperationLockService())
            c.beanFactory.registerSingleton("provider",mock(CollectionFragileEnvironmentProvider::class.java))
            c.beanFactory.registerSingleton("incidents",mock(CollectionIncidentService::class.java))
            c.register(Transactions::class.java,CollectionFragilityService::class.java,CollectionFragileAdjustmentService::class.java,CollectionFragileMonitoringService::class.java)
            c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager))
            try {
                r.db {
                    originalReaderSchema();originalReader("reader",CollectionConst.CARE)
                    SchemaUtils.create(CollectionOrgs,Collections,CollectionFragilityAssessments,CollectionFragileUsePlans,CollectionFragileUseRecords,
                        CollectionFragileUseAdjustments,CollectionFragileMonitorings,CollectionFragileAutomaticChecks,CollectionFragileMonitorRuntimes,CollectionPermanentArchives)
                    for(org in listOf("museum","other"))CollectionOrgDao.new(org){name=org;code=org;systemGroupId=org}
                    CollectionDao.new("piece"){orgId="museum";name="本件";totalRegNo="A1"}
                    CollectionDao.new("foreign"){orgId="other";name="外馆";totalRegNo="B1"}
                }
                body(r)
            } finally {SecurityContextHolder.clearContext()}
        }
    }
    @ParameterizedTest @ValueSource(strings=["revoked","disabled","role","group","museum","moved","configured"])
    fun `all nineteen manual entries recheck current identity before business records`(damage:String)=fixture {r ->
        r.damage(damage)
        r.entries().forEach {(name,call)->
            val failure=assertThrows(BadRequestException::class.java,{call()},name)
            assertTrue(failure.message!!.let {it.contains("权限") || it.contains("无权") || it.contains("责任人员") || it.contains("所属") || it.contains("配置")},"$name: ${failure.message}")
        }
        r.db {assertEquals(0,CollectionFragileUseRecordDao.all().count())};verifyNoInteractions(r.archives,r.provider)
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `same museum current care reader has current detail and may record actual history`(mode:String)=fixture(mode) {r ->
        assertEquals(true,r.service.detail("piece")["canOperate"]);assertEquals("museum",r.service.detail("piece")["orgId"])
        assertTrue(r.service.environmentSources("piece").isEmpty());assertTrue(r.db {r.monitoring.automaticStatus("piece")}.isEmpty())
        val id=r.history();r.db {assertEquals("museum",CollectionFragileUseRecordDao[id].orgId)}
    }
    @Test fun `current cross museum permission permits read only and cannot write foreign history`()=fixture("multi") {r ->
        r.db {actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))}
        assertEquals(false,r.service.detail("foreign")["canOperate"])
        assertThrows(BadRequestException::class.java){r.history("foreign")}
    }
    @Test fun `single museum configuration rejects foreign details even with current cross permission`()=fixture {r ->
        r.db {actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))}
        assertThrows(BadRequestException::class.java){r.service.detail("foreign")}
    }
    @Test fun `current disabled museum history needs explicit current cross scope and never opens writing`()=fixture("multi") {r ->
        r.db {CollectionOrgDao["other"].orgStatus=EnumOrgStatus.DISABLED;actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))}
        assertEquals(false,r.service.detail("foreign")["canOperate"])
        assertThrows(BadRequestException::class.java){r.history("foreign")}
    }
    @Test fun `new current care permission does not claim an old session can pass the HTTP gate`()=fixture {r ->
        login(CollectionConst.SELECT)
        assertEquals(false,r.service.detail("piece")["canOperate"])
        login(CollectionConst.CARE);assertEquals(true,r.service.detail("piece")["canOperate"])
    }
    @Test fun `history insertion rolls back when sealing revokes current qualification`()=fixture {r ->
        doAnswer {RolePermissions.deleteAll();"archive"}.`when`(r.archives).seal(anyString(),anyString(),anyString(),anyString(),anyMap<String,Any>(),nullable(String::class.java))
        assertThrows(BadRequestException::class.java){r.history()}
        r.db {assertEquals(0,CollectionFragileUseRecordDao.all().count());assertTrue(RolePermissions.selectAll().count()>0)}
    }
    @Test fun `device directory cannot return after provider revokes current read permission`()=fixture {r ->
        `when`(r.provider.sources("museum")).thenAnswer {RolePermissions.deleteAll();emptyList<FragileEnvironmentDeviceOption>()}
        assertThrows(BadRequestException::class.java){r.service.environmentSources("piece")}
    }
    @Test fun `foreign malformed rows are excluded before detail and automatic status parsing`()=fixture {r ->
        r.db {
            CollectionFragileUseRecordDao.new("foreign-history"){orgId="other";collectionId="piece";history=r.historyValue()}
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("UPDATE col_fragile_use_record SET history = 'invalid-private-json' WHERE id = 'foreign-history'")
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("SELECT history FROM col_fragile_use_record WHERE id = 'foreign-history'") {row->assertTrue(row.next());assertEquals("invalid-private-json",row.getString(1))}
        }
        assertTrue((r.service.detail("piece")["records"] as List<*>).isEmpty())
        assertTrue(r.db {r.monitoring.automaticStatus("piece")}.isEmpty())
    }
    @Test fun `receiving museum creates its own assessment without reading former museum private content`()=fixture {r ->
        val at=LocalDateTime.now().minusDays(1)
        fun value(revision:String)=FragilityAssessmentVo(sourceRevision=revision,assessedAt=at,fragileMaterial=false,materialAndCondition="实际材质",environmentRequirements="实际环境",storageProtection="实际保护",useRiskAssessment="实际风险",conservationUserId="conservation",keeperUserId="keeper",historyVerifiedFrom=at.minusDays(1),historyReviewFileIds="proof",assessmentFileIds="proof")
        r.db {
            CollectionFragilityAssessmentDao.new("former") {orgId="other";collectionId="piece";version=7;catalog=CollectionCareEvidence.catalog(CollectionDao["piece"]);content=value("former")}
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("UPDATE col_fragility_assessment SET content = 'invalid-former-private-content' WHERE id = 'former'")
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("SELECT content FROM col_fragility_assessment WHERE id = 'former'") {row->assertTrue(row.next());assertEquals("invalid-former-private-content",row.getString(1))}
        }
        val detail=r.service.detail("piece");assertTrue((detail["assessments"] as List<*>).isEmpty())
        val id=r.service.assess("piece",value(detail["sourceRevision"] as String))
        r.db {assertEquals(8,CollectionFragilityAssessmentDao[id].version);assertEquals("museum",CollectionFragilityAssessmentDao[id].orgId);assertEquals(2,CollectionFragilityAssessmentDao.all().count())}
    }

    @Test fun `anonymous requests are rejected at every manual service entry`()=fixture {r ->
        SecurityContextHolder.clearContext()
        r.entries().forEach {(name,call)->assertTrue(assertThrows(BadRequestException::class.java,{call()},name).message!!.contains("登录"),name)}
        verifyNoInteractions(r.archives,r.provider)
    }
    @Test fun `actual multi museum reassignment permits the new museum without following old login membership`()=fixture("multi") {r ->
        r.damage("moved")
        assertThrows(BadRequestException::class.java){r.service.detail("piece")}
        assertEquals(true,r.service.detail("foreign")["canOperate"])
        val id=r.history("foreign");r.db {assertEquals("other",CollectionFragileUseRecordDao[id].orgId)}
    }

    @Test fun `all twelve writes reject an old museum even when the operator currently belongs to both museums`()=fixture("multi") {r ->
        val confirmed=r.service.detail("piece")["orgId"] as String
        r.db {UserGroups.insert {it[userId]="reader";it[groupId]="other"};CollectionDao["piece"].orgId="other";CollectionDao["piece"].totalRegNo="B2"}
        assertEquals(true,r.service.detail("piece")["canOperate"])
        val writes=setOf("assessment","plan","sign","history","start","end","review","inspect","clear","adjust","adjust-sign","withdraw")
        val entries=r.entries(confirmed).filterKeys(writes::contains);assertEquals(12,entries.size)
        entries.forEach {(name,call)->assertTrue(assertThrows(BadRequestException::class.java,{call()},name).message!!.contains("收藏单位已变化"),name)}
        verifyNoInteractions(r.archives,r.provider)
        val saved=r.history("piece","other");r.db {assertEquals("other",CollectionFragileUseRecordDao[saved].orgId)}
    }
    @Test fun `confirmed same museum history records normally and blank museum is not a bypass`()=fixture {r ->
        assertThrows(BadRequestException::class.java){r.history(expectedOrgId="")}
        val saved=r.history(expectedOrgId="museum");r.db {assertEquals("museum",CollectionFragileUseRecordDao[saved].orgId)}
    }

    @Test fun `museum identity changed during sealing rolls back even for a current dual museum operator`()=fixture("multi") {r ->
        r.db {UserGroups.insert {it[userId]="reader";it[groupId]="other"}}
        doAnswer {CollectionDao["piece"].orgId="other";"archive"}.`when`(r.archives).seal(anyString(),anyString(),anyString(),anyString(),anyMap<String,Any>(),nullable(String::class.java))
        assertTrue(assertThrows(BadRequestException::class.java){r.history(expectedOrgId="museum")}.message!!.contains("收藏单位已变化"))
        r.db {assertEquals("museum",CollectionDao["piece"].orgId);assertEquals(0,CollectionFragileUseRecordDao.all().count())}
    }

}
