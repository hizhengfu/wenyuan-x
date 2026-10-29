package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumSource
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.nio.file.Path

class AcquisitionPlanReadRuntimeTest {
    @TempDir lateinit var dir: Path
    private fun fixture(mode: String="multi", body: (AcquisitionPlanService)->Unit) {
        val db=Database.connect("jdbc:sqlite:${dir.resolve("plan.db")}","org.sqlite.JDBC")
        val stale=SecurityUser(id="reader",name="旧登录",username="reader",password="",groupId="museum",groupIds=setOf("museum"),
            postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf("ADMIN",CollectionConst.SELECT,CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(stale,null,stale.authorities)
        try { transaction(db) {
            originalReaderSchema();SchemaUtils.create(CollectionOrgs,AcquisitionPlans,Acquisitions)
            for(org in listOf("museum","other")) {
                CollectionOrgDao.new(org) {name=org;code=org;systemGroupId=org}
                AcquisitionPlanDao.new("plan-$org") {orgId=org;title="年度计划-$org";year=2026}
                AcquisitionDao.new("acq-$org") {orgId=org;planId="plan-$org";name="申请-$org";source=EnumSource.PURCHASE}
            }
            originalReader("reader",CollectionConst.SELECT)
            body(AcquisitionPlanService(mock(FlowInstanceService::class.java),CollectionScopeResolver(mode,"museum"),mock(BusinessAttachmentBinder::class.java)))
        } } finally {SecurityContextHolder.clearContext()}
    }
    @Suppress("UNCHECKED_CAST")
    private fun rows(service:AcquisitionPlanService,request:MockHttpServletRequest=MockHttpServletRequest()) = service.findPage(request).list as List<AcquisitionPlanVo>

    @Test fun `current membership controls plan lists detail and keyword counts despite stale administrator login`()=fixture { service ->
        assertEquals(listOf("plan-museum"),rows(service).map {it.id})
        assertThrows(BadRequestException::class.java){service.get("plan-other")}
        assertEquals(0L,service.findPage(MockHttpServletRequest().apply {addParameter("keyword","other")}).total)
        UserDao["reader"].groupId="other"
        assertEquals(listOf("plan-other"),rows(service).map {it.id})
        assertThrows(BadRequestException::class.java){service.get("plan-museum")}
        assertEquals("plan-other",service.get("plan-other")!!.id)
    }

    @Test fun `revoked permission and disabled account cannot reuse prior plan access`()=fixture { service ->
        assertEquals(1L,service.findPage(MockHttpServletRequest()).total)
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.findPage(MockHttpServletRequest())}
        assertThrows(BadRequestException::class.java){service.get("plan-museum")}
        actorRole("reader","ACQUIRER",setOf(CollectionConst.ACQUIRE))
        assertEquals("plan-museum",service.get("plan-museum")!!.id)
        UserDao["reader"].enabled=false
        assertThrows(BadRequestException::class.java){service.findPage(MockHttpServletRequest())}
        assertThrows(BadRequestException::class.java){service.get("plan-museum")}
    }

    @Test fun `single museum configuration bounds cross museum readers and does not mask a departure`()=fixture("single") {service ->
        actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))
        assertEquals(listOf("plan-museum"),rows(service).map {it.id})
        assertThrows(BadRequestException::class.java){service.get("plan-other")}
        RolePermissions.deleteWhere {RolePermissions.permissionId eq CollectionConst.CROSS_ORG}
        UserDao["reader"].groupId="other"
        assertEquals(0L,service.findPage(MockHttpServletRequest()).total)
        assertThrows(BadRequestException::class.java){service.get("plan-museum")}
    }

    @Test fun `foreign erroneous plan references never inflate plan progress even for cross museum readers`()=fixture {service ->
        AcquisitionDao["acq-other"].apply {planId="plan-museum";collectionId="foreign-registered"}
        assertEquals(1L,service.get("plan-museum")!!.acquisitionCount)
        assertEquals(0L,service.get("plan-museum")!!.registeredCount)
        AcquisitionDao["acq-museum"].collectionId="local-registered"
        actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))
        val all=rows(service).associateBy {it.id}
        assertEquals(2,all.size)
        assertEquals(1L,all.getValue("plan-museum").acquisitionCount)
        assertEquals(1L,all.getValue("plan-museum").registeredCount)
        assertEquals(0L,all.getValue("plan-other").acquisitionCount)
        assertEquals("plan-museum",AcquisitionDao["acq-other"].planId)
    }

    @Test fun `unpaginated plan lists preserve same museum totals and progress`()=fixture {service ->
        AcquisitionPlanDao.new("second") {orgId="museum";title="第二计划";year=2026}
        val request=MockHttpServletRequest().apply {addParameter("pageSize","0")}
        assertEquals(2L,service.findPage(request).total)
        val all=rows(service,request).associateBy {it.id}
        assertEquals(setOf("plan-museum","second"),all.keys)
        assertEquals(1L,all.getValue("plan-museum").acquisitionCount)
        assertEquals(0L,all.getValue("second").acquisitionCount)
    }

    @Test fun `draft flow inspection checks current rights before exposing form data`()=fixture {service ->
        assertEquals("plan-museum",service.getFlowInfo("plan-museum")!!["businessKey"])
        assertThrows(BadRequestException::class.java){service.getFlowInfo("plan-other")}
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.getFlowInfo("plan-museum")}
        actorRole("reader","READER",setOf(CollectionConst.SELECT))
        UserDao["reader"].groupId="other"
        assertThrows(BadRequestException::class.java){service.getFlowInfo("plan-museum")}
        assertEquals("plan-other",service.getFlowInfo("plan-other")!!["businessKey"])
    }
}
