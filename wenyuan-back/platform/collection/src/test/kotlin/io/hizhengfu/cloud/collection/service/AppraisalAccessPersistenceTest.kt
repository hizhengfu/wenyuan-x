package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.util.ReflectionTestUtils

class AppraisalAccessPersistenceTest {
    private class Fixture(val service:AppraisalService,val scope:CollectionScopeResolver,val change:CollectionChangeService,val flow:FlowInstanceService) {
        fun page(keyword:String?=null)=service.findPage(MockHttpServletRequest().apply {setParameter("pageSize","0");keyword?.let {setParameter("keyword",it)}})
        fun grant(vararg values:String) {RolePermissions.deleteAll();actorRole("reader","CURRENT_APPRAISAL",values.toSet())}
        fun damage(kind:String) {when(kind) {
            "revoked"->grant()
            "disabled"->UserDao["reader"].enabled=false
            "moved"->{UserDao["reader"].groupId="other";actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))}
            "configured"->ReflectionTestUtils.setField(scope,"defaultOrgId","other")
            "museum_disabled"->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
            else->error(kind)
        }}
        fun state()=AppraisalDao.all().map {it.toModel()}.sortedBy {it.id} to AppraisalMemberDao.all().map {it.toModel()}.sortedBy {it.id}
    }
    private fun fixture(mode:String="single",body:(Fixture)->Unit) {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        val user=SecurityUser(id="reader",name="旧登录",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf("ADMIN",CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        try {transaction(Database.connect("jdbc:sqlite:file:appraisal-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")) {
            originalReaderSchema();SchemaUtils.create(CollectionOrgs,Collections,Appraisals,AppraisalMembers)
            for(org in listOf("museum","other")) {
                CollectionOrgDao.new(org){name=org;code=org;systemGroupId=org}
                CollectionDao.new("piece-$org"){orgId=org;name="藏品-$org";totalRegNo="REG-$org";lifeStatus=EnumCollectionStatus.IN_STORE}
                AppraisalDao.new("case-$org"){orgId=org;collectionId="piece-$org";resultName="鉴选-$org";conclusion="原结论";flowStatus=EnumFlowStatus.DRAFT}
                AppraisalMemberDao.new("member-$org"){orgId=org;appraisalId="case-$org";userId="reader";memberName="原专家-$org";opinion="原意见-$org"}
            }
            originalReader("reader",CollectionConst.APPRAISE);actorRole("reader","CORRECT",setOf(CollectionConst.LEDGER_CORRECT))
            val scope=CollectionScopeResolver(mode,"museum");val change=mock(CollectionChangeService::class.java);val flow=mock(FlowInstanceService::class.java)
            `when`(change.proposal(anyString(), eq(false))).thenReturn(CollectionChangeVo(collectionId="piece-museum",orgId="museum",content=CollectionCatalogMapper.fromCollection(CollectionDao["piece-museum"].toModel())))
            `when`(change.save(any(CollectionChangeVo::class.java) ?: CollectionChangeVo())).thenReturn("change-draft")
            val binder=mock(BusinessAttachmentBinder::class.java)
            val resolver=mock(CollectionUserResolver::class.java);`when`(resolver.names(anyList())).thenReturn(mapOf("reader" to "当前专家"))
            val service=AppraisalService(flow,scope,resolver,change,binder,mock(CollectionOperationLockService::class.java),mock(AccessionReceiptService::class.java))
            body(Fixture(service,scope,change,flow))
        }} finally {SecurityContextHolder.clearContext();context.close()}
    }
    @ParameterizedTest @CsvSource("revoked,get","revoked,flow","revoked,page","disabled,get","disabled,flow","disabled,page",
        "configured,get","configured,flow","configured,page","museum_disabled,get","museum_disabled,flow","museum_disabled,page")
    fun `appraisal reads honor current authority and configured museum`(damage:String,entry:String)=fixture {r ->
        r.service.get("case-museum");r.service.getFlowInfo("case-museum");r.page()
        val before=r.state();r.damage(damage)
        if(entry=="page" && damage in setOf("configured","museum_disabled")) {
            val rows=r.page().list as List<*>;assertFalse(rows.filterIsInstance<AppraisalVo>().any {it.id=="case-museum"})
        } else assertThrows(BadRequestException::class.java){when(entry){"get"->r.service.get("case-museum");"flow"->r.service.getFlowInfo("case-museum");else->r.page()}}
        assertEquals(before,r.state())
    }
    @ParameterizedTest @ValueSource(strings=["get","flow","page"])
    fun `appraisal reads replace stale administrator membership`(entry:String)=fixture("multi") {r ->
        UserDao["reader"].groupId="other"
        if(entry=="page")assertEquals(listOf("case-other"),(r.page().list as List<*>).filterIsInstance<AppraisalVo>().map {it.id})
        else assertThrows(BadRequestException::class.java){if(entry=="get")r.service.get("case-museum") else r.service.getFlowInfo("case-museum")}
    }
    @ParameterizedTest @CsvSource(
        "revoked,new",
        "revoked,update",
        "revoked,draft",
        "revoked,submit",
        "revoked,cancel",
        "revoked,void",
        "revoked,change",
        "disabled,new",
        "disabled,update",
        "disabled,draft",
        "disabled,submit",
        "disabled,cancel",
        "disabled,void",
        "disabled,change",
        "moved,new",
        "moved,update",
        "moved,draft",
        "moved,submit",
        "moved,cancel",
        "moved,void",
        "moved,change",
        "configured,new",
        "configured,update",
        "configured,draft",
        "configured,submit",
        "configured,cancel",
        "configured,void",
        "configured,change",
        "museum_disabled,new",
        "museum_disabled,update",
        "museum_disabled,draft",
        "museum_disabled,submit",
        "museum_disabled,cancel",
        "museum_disabled,void",
        "museum_disabled,change"
    )
    fun `appraisal mutations reject stale duties without changing records`(damage:String,entry:String)=fixture {r ->
        val row=AppraisalDao["case-museum"]
        if(entry=="cancel"){row.flowStatus=EnumFlowStatus.AUDIT;row.flowInstanceId="flow-museum"}
        if(entry in setOf("void","change"))row.flowStatus=EnumFlowStatus.PASS
        val before=r.state();r.damage(damage)
        clearInvocations(r.flow,r.change)
        assertThrows(BadRequestException::class.java){when(entry) {
            "new"->r.service.apply(AppraisalVo(collectionId="piece-museum",resultName="新鉴选",committee="专家"))
            "update"->r.service.save(row.toModel().copy(resultName="改写"))
            "draft"->r.service.saveDraft("case-museum")
            "submit"->r.service.submit("case-museum")
            "cancel"->r.service.cancel("case-museum")
            "void"->r.service.void("case-museum")
            else->r.service.createChange("case-museum")
        }}
        assertEquals(before,r.state());verifyNoInteractions(r.flow,r.change)
    }
    @Test fun `appraisal names and keyword joins never use a foreign collection`()=fixture {r ->
        AppraisalDao["case-museum"].collectionId="piece-other"
        assertTrue(r.service.get("case-museum").collectionName == null)
        assertEquals(0L,r.page("REG-other").total)
        r.grant(CollectionConst.APPRAISE,CollectionConst.CROSS_ORG)
        assertTrue(r.service.get("case-museum").collectionTotalRegNo == null)
        assertEquals(0L,r.page("REG-other").total)
    }
    @ParameterizedTest @ValueSource(strings=["get","flow","page"])
    fun `appraisal mixed museum expert rows cannot be returned as local signatures`(entry:String)=fixture {r ->
        AppraisalMemberDao["member-museum"].orgId="other"
        assertThrows(BadRequestException::class.java){when(entry){"get"->r.service.get("case-museum");"flow"->r.service.getFlowInfo("case-museum");else->r.page()}}
    }
    @Test fun `appraisal ledger correction duty can propose change without appraisal registration duty`()=fixture {r ->
        r.grant(CollectionConst.LEDGER_CORRECT);AppraisalDao["case-museum"].flowStatus=EnumFlowStatus.PASS
        assertEquals("change-draft",r.service.createChange("case-museum"))
        assertThrows(BadRequestException::class.java){r.service.get("case-museum")}
        assertThrows(BadRequestException::class.java){r.service.save(AppraisalVo(collectionId="piece-museum",committee="专家"))}
    }
    @ParameterizedTest @ValueSource(strings=["PASS","AUDIT","REJECT","CANCEL"])
    fun `appraisal existing stages cannot be reset through generic draft entry`(status:String)=fixture {r ->
        val row=AppraisalDao["case-museum"];row.flowStatus=EnumFlowStatus.valueOf(status)
        if(status=="PASS")row.appraisedAt=java.time.LocalDateTime.of(2026,9,1,10,0)
        val before=r.state()
        assertThrows(BadRequestException::class.java){r.service.saveDraft(row.id.value)}
        assertEquals(before,r.state())
    }
    @ParameterizedTest @ValueSource(strings=["save","draft","submit"])
    fun `appraisal completed fact cannot be overwritten despite an inconsistent draft status`(entry:String)=fixture {r ->
        val row=AppraisalDao["case-museum"];row.appraisedAt=java.time.LocalDateTime.of(2026,9,1,10,0)
        val before=r.state()
        assertThrows(BadRequestException::class.java){when(entry){"save"->r.service.save(row.toModel().copy(conclusion="覆盖原结论"));"draft"->r.service.saveDraft(row.id.value);else->r.service.submit(row.id.value)}}
        assertEquals(before,r.state());verifyNoInteractions(r.flow)
    }
    @ParameterizedTest @ValueSource(strings=["COLLECTION_APPRAISE","ADMIN","COLLECTION_ADMIN"])
    fun `appraisal current authorized creators retain normal draft editing`(permission:String)=fixture {r ->
        r.grant(permission)
        val id=r.service.apply(AppraisalVo(collectionId="piece-museum",resultName="新的鉴选",committee="独立专家",conclusion="待会签意见"))
        r.service.saveDraft(id)
        assertEquals(id,r.service.save(r.service.get(id).copy(conclusion="补充待会签意见")))
        assertEquals("补充待会签意见",r.service.get(id).conclusion)
        assertEquals("独立专家",r.service.get(id).members.single().memberName)
    }
    @Test fun `appraisal current viewer reads without gaining registration rights`()=fixture {r ->
        r.grant(CollectionConst.SELECT)
        assertEquals(1L,r.page().total);r.service.get("case-museum");r.service.getFlowInfo("case-museum")
        assertThrows(BadRequestException::class.java){r.service.apply(AppraisalVo(collectionId="piece-museum",committee="专家"))}
    }
    @Test fun `completed appraisal print retains subject and member opinions and rejects altered results`()=fixture { r ->
        SchemaUtils.create(CollectionPermanentArchives,CollectionPermanentArchiveFiles,io.hizhengfu.cloud.sys.file.dao.Files)
        val binder=mock(BusinessAttachmentBinder::class.java)
        val archives=CollectionPermanentArchiveService(binder,r.scope,readAccess=unprotectedArchiveReadAccessProvider())
        val row=AppraisalDao["case-museum"];row.flowStatus=EnumFlowStatus.PASS;row.appraisedAt=java.time.LocalDateTime.of(2026,1,1,10,0)
        CollectionAppraisalPrintFacts.capture(row,archives)
        CollectionDao["piece-museum"].name="后来更名"
        val source=CollectionAppraisalPrintFacts.model(row)
        val frozen=CollectionAppraisalPrintFacts.read(source,archives)!!
        assertEquals("藏品-museum",frozen.collection.name);assertEquals("原意见-museum",frozen.appraisal.members.single().opinion)
        val fields=CollectionPrintFieldBuilder.appraisalFields(source)
        assertTrue(fields.any {it.label=="建议级别"});assertTrue(fields.any {it.label=="成员 1 意见" && it.value=="原意见-museum"})
        AppraisalMemberDao["member-museum"].opinion="被覆盖的意见"
        assertThrows(BadRequestException::class.java) {CollectionAppraisalPrintFacts.read(CollectionAppraisalPrintFacts.model(row),archives)}
    }
    @Test fun `appraisal completed callback preserves results without requiring a logged in operator`()=fixture {r ->
        val row=AppraisalDao["case-museum"];val original=SecurityContextHolder.getContext().authentication
        try {
            SecurityContextHolder.clearContext()
            r.service.onFlowCompleted(row,io.hizhengfu.cloud.flow.domain.FlowCompletedEvent("flow",CollectionConst.FLOW_APPRAISAL,"鉴选",row.id.value,CollectionConst.BIZ_APPRAISAL,emptyMap(),"reader","原经办人"))
            assertEquals(EnumFlowStatus.PASS,row.flowStatus);assertTrue(row.appraisedAt != null);assertEquals("原结论",row.conclusion)
        } finally {SecurityContextHolder.getContext().authentication=original}
    }
    @Test fun `appraisal legitimate multi museum history and current transfer remain readable`()=fixture("multi") {r ->
        r.grant(CollectionConst.APPRAISE,CollectionConst.CROSS_ORG)
        assertEquals(2L,r.page().total);r.service.get("case-other");r.service.getFlowInfo("case-other")
        CollectionOrgDao["other"].orgStatus=EnumOrgStatus.DISABLED;r.service.get("case-other")
        CollectionOrgDao["other"].orgStatus=EnumOrgStatus.ENABLED
        r.grant(CollectionConst.APPRAISE);UserDao["reader"].groupId="other"
        assertEquals("case-other",r.service.get("case-other").id)
        assertEquals("case-other",r.service.save(AppraisalDao["case-other"].toModel().copy(resultName="新馆鉴选")))
    }
}
