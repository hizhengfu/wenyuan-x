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
import org.junit.jupiter.api.Test
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
import java.time.LocalDateTime

class CollectionChangeAccessPersistenceTest {
    private class Fixture(val service:CollectionChangeService,val scope:CollectionScopeResolver,val flow:FlowInstanceService,
        val archives:CollectionPermanentArchiveService) {
        fun grant(vararg permissions:String) { RolePermissions.deleteAll();actorRole("reader","CURRENT_CHANGE",permissions.toSet()) }
        fun administrator() {
            Roles.insert {it[Roles.id]=EnumRole.ADMIN.value;it[code]="ACTUAL_ADMIN";it[name]="实际管理员"}
            RoleUsers.insert {it[userId]="reader";it[roleId]=EnumRole.ADMIN.value}
        }
        fun damage(kind:String) { when(kind) {
            "revoked"->grant(CollectionConst.SELECT)
            "disabled"->UserDao["reader"].enabled=false
            "moved"->{UserDao["reader"].groupId="other";actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))}
            "configured"->ReflectionTestUtils.setField(scope,"defaultOrgId","other")
            "museum_disabled"->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
            "storage"->actorRole("reader","STORAGE",setOf(CollectionConst.STORAGE))
            "keeper"->actorRole("reader",CollectionConst.ROLE_KEEPER)
            else->error(kind)
        } }
        fun page()=service.findPage(MockHttpServletRequest().apply {setParameter("pageSize","0")})
        fun state()=CollectionChangeDao.all().map {it.toModel()}.sortedBy {it.id} to CollectionDao.all().map {it.toModel()}.sortedBy {it.id}
        fun save(id:String?="case-museum",org:String="museum")=service.save(CollectionChangeDao["case-$org"].toModel().copy(id=id,reason="新事由"))
    }
    private fun fixture(mode:String="single",body:(Fixture)->Unit) {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        val user=SecurityUser(id="reader",name="旧登录",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,
            roles=setOf(EnumRole.ADMIN.value),authorities=listOf("ADMIN",CollectionConst.LEDGER_CORRECT,CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        try {transaction(Database.connect("jdbc:sqlite:file:change-access-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")) {
            originalReaderSchema();SchemaUtils.create(CollectionOrgs,Collections,CollectionChanges,CollectionCatalogVersions,CollectionRetainedLedgers,CollectionCategories)
            CollectionCategoryDao.new("ceramic") { name="瓷器"; code="03"; parentId="0" }
            for(org in listOf("museum","other")) {
                CollectionOrgDao.new(org){name=org;code=org;systemGroupId=org}
                val piece=CollectionDao.new("piece-$org"){orgId=org;name="藏品-$org";categoryId="ceramic";totalRegNo="REG-$org";lifeStatus=EnumCollectionStatus.IN_STORE;catalogStatus=EnumCatalogStatus.APPROVED;registeredAt=LocalDateTime.of(2020,1,2,10,0)}
                val source=CollectionCatalogMapper.fromCollection(piece.toModel())
                CollectionChangeDao.new("case-$org"){orgId=org;collectionId=piece.id.value;reason="原事由";before=source;baseline=source;content=source.copy(formalName="拟订正-$org");flowStatus=EnumFlowStatus.DRAFT;createBy="reader"}
            }
            originalReader("reader",CollectionConst.LEDGER_CORRECT)
            val scope=CollectionScopeResolver(mode,"museum");val flow=mock(FlowInstanceService::class.java)
            val binder=mock(BusinessAttachmentBinder::class.java)
            val archives=mock(CollectionPermanentArchiveService::class.java)
            val service=CollectionChangeService(flow,scope,mock(CollectionOperationLockService::class.java),binder,mock(CollectionCatalogService::class.java),
                mock(CollectionResponsibleUserService::class.java),archives,mock(CollectionRetainedLedgerService::class.java),CollectionActorDirectory(scope),mock(PaperLedgerService::class.java))
            body(Fixture(service,scope,flow,archives))
        }} finally {SecurityContextHolder.clearContext();context.close()}
    }

    @ParameterizedTest @CsvSource("disabled,get","disabled,flow","disabled,page","configured,get","configured,flow","configured,page","museum_disabled,get","museum_disabled,flow","museum_disabled,page")
    fun `reads use current account and configured museum`(damage:String,entry:String)=fixture {r->
        r.service.get("case-museum");val before=r.state();r.damage(damage)
        if(entry=="page" && damage!="disabled") assertFalse((r.page().list as List<*>).filterIsInstance<CollectionChangeVo>().any {it.id=="case-museum"})
        else assertThrows(BadRequestException::class.java){when(entry){"get"->r.service.get("case-museum");"flow"->r.service.getFlowInfo("case-museum");else->r.page()}}
        assertEquals(before,r.state())
    }
    @ParameterizedTest @ValueSource(strings=["get","flow","page","proposal","corrections"])
    fun `revoked read duties cannot use old administrator login`(entry:String)=fixture {r->
        r.grant();val before=r.state()
        assertThrows(BadRequestException::class.java){when(entry){"get"->r.service.get("case-museum");"flow"->r.service.getFlowInfo("case-museum");"proposal"->r.service.proposal("piece-museum");"corrections"->r.service.corrections("case-museum");else->r.page()}}
        assertEquals(before,r.state())
    }
    @ParameterizedTest @ValueSource(strings=["get","flow","page"])
    fun `current membership replaces old administrator read scope`(entry:String)=fixture("multi") {r->
        UserDao["reader"].groupId="other"
        if(entry=="page")assertEquals(listOf("case-other"),(r.page().list as List<*>).filterIsInstance<CollectionChangeVo>().map {it.id})
        else assertThrows(BadRequestException::class.java){if(entry=="get")r.service.get("case-museum") else r.service.getFlowInfo("case-museum")}
    }
    @ParameterizedTest @CsvSource(
        "revoked,new","revoked,update","revoked,draft","revoked,execute",
        "disabled,new","disabled,update","disabled,draft","disabled,execute",
        "moved,new","moved,update","moved,draft","moved,execute",
        "configured,new","configured,update","configured,draft","configured,execute",
        "museum_disabled,new","museum_disabled,update","museum_disabled,draft","museum_disabled,execute",
        "storage,new","storage,update","storage,draft","storage,execute",
        "keeper,new","keeper,update","keeper,draft","keeper,execute")
    fun `writes and repeated execution require current duties before changing records`(damage:String,entry:String)=fixture {r->
        if(entry=="execute")CollectionChangeDao["case-museum"].apply {flowStatus=EnumFlowStatus.PASS;appliedAt=LocalDateTime.of(2026,9,1,10,0)}
        val before=r.state();r.damage(damage)
        assertThrows(BadRequestException::class.java){when(entry){"new"->r.save(null);"update"->r.save();"draft"->r.service.saveDraft("case-museum");else->r.service.execute("case-museum",CollectionChangeExecutionVo())}}
        assertEquals(before,r.state());verifyNoInteractions(r.flow)
    }
    @ParameterizedTest @ValueSource(strings=["AUDIT","PASS","REJECT","CANCEL","VOID"])
    fun `generic draft entry cannot reset prior stages without an instance reference`(status:String)=fixture {r->
        CollectionChangeDao["case-museum"].flowStatus=EnumFlowStatus.valueOf(status);val before=r.state()
        assertThrows(BadRequestException::class.java){r.service.saveDraft("case-museum")};assertEquals(before,r.state())
    }
    @ParameterizedTest @ValueSource(strings=["draft","update"])
    fun `applied facts cannot be overwritten even if mutable status became draft`(entry:String)=fixture {r->
        CollectionChangeDao["case-museum"].appliedAt=LocalDateTime.of(2026,9,1,10,0);val before=r.state()
        assertThrows(BadRequestException::class.java){if(entry=="draft")r.service.saveDraft("case-museum") else r.save()};assertEquals(before,r.state())
    }
    @Test fun `foreign collection identity is omitted from historic local change`()=fixture {r->
        CollectionChangeDao["case-museum"].collectionId="piece-other"
        assertNull(r.service.get("case-museum").collectionName);assertNull(r.service.get("case-museum").totalRegNo)
    }
    @Test fun `mixed museum draft cannot be rewritten through collection membership`()=fixture("multi") {r->
        r.administrator();assertEquals("case-other",r.save("case-other","other"))
        CollectionChangeDao["case-museum"].orgId="other";val before=r.state()
        assertThrows(BadRequestException::class.java){r.save()};assertEquals(before,r.state())
    }
    @Test fun `legitimate local draft and current read only access remain usable`()=fixture {r->
        val id=r.save(null);r.service.saveDraft(id);assertEquals("新事由",r.service.get(id).reason)
        r.grant(CollectionConst.SELECT);assertEquals("新事由",r.service.get(id).reason);r.service.getFlowInfo(id);assertEquals(2L,r.page().total)
        assertThrows(BadRequestException::class.java){r.save(id)}
    }
    @Test fun `change original requires current museum access and exact case file membership`()=fixture {r->
        val own="2101898409802321920";val other="2101898409802321921";val historic="2101898409802321922"
        CollectionChangeDao["case-museum"].evidenceFileIds=own
        CollectionChangeDao["case-other"].evidenceFileIds=other
        `when`(r.archives.listForAuthorizedOrg(CollectionConst.BIZ_CHANGE,"case-museum","museum"))
            .thenReturn(listOf(mapOf("files" to listOf(mapOf("fileId" to historic)))))
        r.grant(CollectionConst.SELECT)
        r.service.requireOriginal("case-museum",own)
        r.service.requireOriginal("case-museum",historic)
        assertThrows(BadRequestException::class.java){r.service.requireOriginal("case-museum",other)}
        assertThrows(BadRequestException::class.java){r.service.requireOriginal("case-other",other)}
        assertThrows(BadRequestException::class.java){r.service.requireOriginal("case-museum","../file")}
        r.grant()
        assertThrows(BadRequestException::class.java){r.service.requireOriginal("case-museum",own)}
    }
    @Test fun `change archived original requires the selected frozen archive and current museum read duty`()=fixture {r->
        val archiveId="2101898409802321923";val fileId="2101898409802321922"
        `when`(r.archives.listForAuthorizedOrg(CollectionConst.BIZ_CHANGE,"case-museum","museum"))
            .thenReturn(listOf(mapOf("id" to archiveId,"files" to listOf(mapOf("fileId" to fileId)))))
        r.grant(CollectionConst.SELECT)
        assertDoesNotThrow { r.service.requireArchivedOriginal("case-museum",archiveId,fileId) }
        assertThrows(BadRequestException::class.java){r.service.requireArchivedOriginal("case-museum","2101898409802321924",fileId)}
        assertThrows(BadRequestException::class.java){r.service.requireArchivedOriginal("case-museum",archiveId,"2101898409802321925")}
        assertThrows(BadRequestException::class.java){r.service.requireArchivedOriginal("case-other",archiveId,fileId)}
        assertThrows(BadRequestException::class.java){r.service.requireArchivedOriginal("case-museum",archiveId,"../file")}
        r.grant()
        assertThrows(BadRequestException::class.java){r.service.requireArchivedOriginal("case-museum",archiveId,fileId)}
    }
    @Test fun `new current museum appointment can save despite old login membership`()=fixture("multi") {r->
        UserDao["reader"].groupId="other";assertEquals("case-other",r.save("case-other","other"));assertEquals("新事由",r.service.get("case-other").reason)
    }
    @Test fun `cross museum read authority does not expand write authority`()=fixture("multi") {r->
        actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG));assertEquals("case-other",r.service.get("case-other").id)
        assertThrows(BadRequestException::class.java){r.save("case-other","other")}
    }
    @Test fun `clearing old storage duties permits current registrar work`()=fixture {r->
        val stale=SecurityUser(id="reader",name="旧库房职责",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,
            roles=emptySet(),authorities=listOf(CollectionConst.LEDGER_CORRECT,CollectionConst.STORAGE).map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(stale,null,stale.authorities)
        assertEquals("case-museum",r.save())
    }
    @ParameterizedTest @ValueSource(strings=["configured","museum_disabled","storage","revoked"])
    fun `actual administrator cannot bypass configuration or ledger duty restrictions`(damage:String)=fixture {r->
        r.administrator();r.damage(damage);val before=r.state()
        assertThrows(BadRequestException::class.java){r.save()};assertEquals(before,r.state())
    }
    @Test fun `actual administrator still needs explicit ledger duty and can use ledger custodian role`()=fixture {r->
        r.administrator();r.grant();assertThrows(BadRequestException::class.java){r.save()}
        actorRole("reader",CollectionConst.ROLE_LEDGER_CUSTODIAN);assertEquals("case-museum",r.save())
    }
    @Test fun `current single museum reassignment allows new museum records only`()=fixture {r->
        UserDao["reader"].groupId="other";ReflectionTestUtils.setField(r.scope,"defaultOrgId","other")
        assertEquals("case-other",r.save("case-other","other"));assertEquals(listOf("case-other"),(r.page().list as List<*>).filterIsInstance<CollectionChangeVo>().map {it.id})
        assertThrows(BadRequestException::class.java){r.service.get("case-museum")}
    }
    @Test fun `historical cross museum read remains available for inactive museum without write rights`()=fixture("multi") {r->
        r.grant(CollectionConst.SELECT,CollectionConst.CROSS_ORG);CollectionOrgDao["other"].orgStatus=EnumOrgStatus.DISABLED
        assertEquals("case-other",r.service.get("case-other").id);assertEquals(2L,r.page().total)
        assertThrows(BadRequestException::class.java){r.save("case-other","other")}
    }
    @Test fun `proposal ignores a foreign museum catalog version sharing collection identity`()=fixture {r->
        CollectionCatalogVersionDao.new("foreign-version"){orgId="other";catalogId="foreign";collectionId="piece-museum";version=9;catalogStatus=EnumCatalogStatus.APPROVED;content=CollectionCatalogContentVo(formalName="外馆机密定名")}
        assertEquals("藏品-museum",r.service.proposal("piece-museum").content!!.formalName)
        val id=r.save(null);assertEquals("藏品-museum",r.service.get(id).before!!.formalName)
    }
}
