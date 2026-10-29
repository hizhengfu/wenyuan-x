package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.util.ReflectionTestUtils

class CollectionSetAccessPersistenceTest {
    private class Fixture(val service:CollectionSetService,val scope:CollectionScopeResolver) {
        fun grant(vararg permissions:String) {RolePermissions.deleteAll();actorRole("reader","CURRENT_SET",permissions.toSet())}
        fun damage(kind:String) {when(kind) {
            "revoked"->grant(CollectionConst.SELECT)
            "disabled"->UserDao["reader"].enabled=false
            "moved"->{UserDao["reader"].groupId="other";actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))}
            "configured"->ReflectionTestUtils.setField(scope,"defaultOrgId","other")
            "museum_disabled"->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
            "storage"->actorRole("reader","STORAGE",setOf(CollectionConst.STORAGE))
            "keeper"->actorRole("reader",CollectionConst.ROLE_KEEPER)
            else->error(kind)
        }}
        fun snapshot():List<List<List<Any?>>> {
            // 完成夹具准备/账号变更的审计回调后，按实际落库字段比较本次操作前后状态。
            TransactionManager.current().commit();TransactionManager.current().entityCache.clear()
            return listOf(Collections,CollectionSets,CollectionRegistrationNumbers,CollectionCatalogs,CollectionLedgerLogs).map {t->t.selectAll().orderBy(t.id).map {r->t.columns.map {r[it]}}}
        }
        fun save(id:String?="relation-museum",org:String="museum")=service.save("parent-$org",CollectionSetVo(id=id,partNo=if(id==null)"NEW" else "01",partName="本次分件",relation=EnumSetRelation.PART,quantity=2,unit="件"))
        fun entry()=CollectionSetEntryVo(main=CollectionVo(name="新套件",categoryId="category",level=EnumRelicLevel.GENERAL,source=EnumSource.OLD,acquireDate=java.time.LocalDate.of(2020,1,1),quantity=1,unit="套"),parts=listOf(CollectionSetEntryPartVo(partNo="01",partName="新分件一"),CollectionSetEntryPartVo(partNo="02",partName="新分件二")))
        fun administrator() {Roles.insert {it[Roles.id]=EnumRole.ADMIN.value;it[code]="ACTUAL_ADMIN";it[name]="实际管理员"};RoleUsers.insert {it[userId]="reader";it[roleId]=EnumRole.ADMIN.value}}
    }
    private fun fixture(mode:String="single",afterCommit:((Fixture,org.springframework.transaction.PlatformTransactionManager)->Unit)?=null,body:(Fixture)->Unit) {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        val old=SecurityUser(id="reader",name="旧登录",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf("ADMIN",CollectionConst.LEDGER_CREATE,CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(old,null,old.authorities)
        val path=java.nio.file.Files.createTempFile("collection-set-access-", ".db")
        val database=Database.connect("jdbc:sqlite:$path",driver="org.sqlite.JDBC")
        try {val fixture=transaction(database) {
            originalReaderSchema();SchemaUtils.create(CollectionOperationLocks,CollectionOrgs,Collections,CollectionSets,CollectionCatalogs,CollectionCatalogVersions,CollectionRegistrationNumbers, CollectionRetainedLedgers,CollectionLedgerLogs)
            for(org in listOf("museum","other")) {
                CollectionOrgDao.new(org){name=org;code=org;systemGroupId=org}
                for(kind in listOf("parent","child"))CollectionDao.new("$kind-$org"){orgId=org;totalRegNo="$kind-$org";name="$kind-$org";lifeStatus=EnumCollectionStatus.PENDING;catalogStatus=EnumCatalogStatus.DRAFT}
                CollectionSetDao.new("relation-$org"){orgId=org;parentCollectionId="parent-$org";childCollectionId="child-$org";partNo="01";partName="原分件-$org";relation=EnumSetRelation.PART;quantity=1;unit="件"}
            }
            originalReader("reader",CollectionConst.LEDGER_CREATE);actorRole("reader","SELECT",setOf(CollectionConst.SELECT))
            val scope=CollectionScopeResolver(mode,"museum");val binder=mock(BusinessAttachmentBinder::class.java);val locks=CollectionOperationLockService()
            val catalog=CollectionCatalogService(scope,locks,mock(CollectionGradingService::class.java),mock(AccessionReceiptService::class.java),binder)
            val collections=CollectionService(scope,binder,CollectionRegistrationNumberService(),locks,catalog,mock(CollectionChangeService::class.java),mock(CollectionRetainedLedgerService::class.java),mock(CollectionDisclosureService::class.java))
            Fixture(CollectionSetService(scope,collections),scope)
        }
            transaction(database) {body(fixture)}
            afterCommit?.invoke(fixture,org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager(org.sqlite.SQLiteDataSource().apply {url="jdbc:sqlite:$path"}))
        } finally {SecurityContextHolder.clearContext();context.close();java.nio.file.Files.deleteIfExists(path)}
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.transaction.annotation.EnableTransactionManagement(proxyTargetClass=true)
    class SetTransactions

    @ParameterizedTest @CsvSource("0,existing", "1,existing", "1,main", "1,previous")
    fun `set number conflict rolls back all earlier entries and allows retry`(index:Int,conflict:String) {
        fixture(afterCommit={r,manager ->
            val tx=org.springframework.transaction.support.TransactionTemplate(manager)
            org.springframework.context.annotation.AnnotationConfigApplicationContext().use {context ->
                context.beanFactory.registerSingleton("transactionManager",manager)
                context.register(SetTransactions::class.java)
                context.registerBean(CollectionSetService::class.java,java.util.function.Supplier {r.service})
                context.refresh()
                val actual=context.getBean(CollectionSetService::class.java)
                fun state()=tx.execute {
                    listOf(Collections,CollectionSets,CollectionRegistrationNumbers,CollectionCatalogs,CollectionLedgerLogs).map {table ->
                        table.selectAll().map {row->table.columns.map {row[it]}.toString()}.sorted()
                    }
                }
                val input=r.entry().let {entry->entry.copy(main=entry.main!!.copy(totalRegNo="900001"),parts=entry.parts.mapIndexed {i,part->part.copy(collection=CollectionVo(totalRegNo="90000${i+2}"))})}
                val number=when(conflict){"main"->"900001";"previous"->"900002";else->"900099"}
                val invalid=input.copy(parts=input.parts.mapIndexed {i,part->if(i==index)part.copy(collection=part.collection!!.copy(totalRegNo=number))else part})
                val before=state()
                val failure=assertThrows(BadRequestException::class.java){actual.createSetEntry(invalid)}
                assertEquals(if(conflict=="existing")"总登记号「$number」已存在于其他藏品主档，请先核查总账" else "总登记号「$number」已被占用，不能重复使用",failure.message)
                assertEquals(before,state(),"主档、分件关系、占号、编目及留痕必须整体回滚")
                val result=actual.createSetEntry(input)
                tx.execute {
                    val ids=listOf(result.mainCollectionId)+result.childCollectionIds
                    assertEquals(setOf("900001","900002","900003"),ids.map {CollectionDao[it].totalRegNo}.toSet())
                    assertEquals(3L,CollectionRegistrationNumberDao.count())
                    assertEquals(3L,CollectionCatalogDao.count());assertEquals(3L,CollectionLedgerLogDao.count())
                    assertEquals(result.childCollectionIds.toSet(),actual.list(result.mainCollectionId).mapNotNull {it.childCollectionId}.toSet())
                    assertTrue(ids.all {CollectionDao[it].registeredAt==null})
                }
            }
        }) {CollectionDao["parent-museum"].totalRegNo="900099"}
    }

    @ParameterizedTest @ValueSource(strings=["disabled","configured","museum_disabled"])
    fun `set lists use current actor and configured museum`(kind:String)=fixture {r->
        r.service.list("parent-museum");r.damage(kind);val before=r.snapshot()
        assertThrows(BadRequestException::class.java){r.service.list("parent-museum")};assertEquals(before,r.snapshot())
    }
    @Test fun `revoked read duty cannot use stale administrator snapshot`()=fixture {r->
        r.grant();assertThrows(BadRequestException::class.java){r.service.list("parent-museum")}
    }
    @Test fun `read membership comes from current directory`()=fixture("multi") {r->
        UserDao["reader"].groupId="other";assertThrows(BadRequestException::class.java){r.service.list("parent-museum")};assertEquals("relation-other",r.service.list("parent-other").single().id)
    }
    @ParameterizedTest @CsvSource("revoked,new","revoked,update","revoked,delete","revoked,entry",
        "disabled,new","disabled,update","disabled,delete","disabled,entry",
        "moved,new","moved,update","moved,delete","moved,entry",
        "configured,new","configured,update","configured,delete","configured,entry",
        "museum_disabled,new","museum_disabled,update","museum_disabled,delete","museum_disabled,entry",
        "storage,new","storage,update","storage,delete","storage,entry",
        "keeper,new","keeper,update","keeper,delete","keeper,entry")
    fun `relationship mutations and bulk entry reject current duty or museum changes`(damage:String,entry:String)=fixture {r->
        r.damage(damage);val before=r.snapshot()
        assertThrows(BadRequestException::class.java){when(entry){"new"->r.save(null);"update"->r.save();"delete"->r.service.delete("parent-museum","relation-museum");else->r.service.createSetEntry(r.entry())}}
        assertEquals(before,r.snapshot())
    }
    @ParameterizedTest @ValueSource(strings=["list","save","delete"])
    fun `foreign relationship cannot be exposed moved or deleted through local parent`(entry:String)=fixture {r->
        CollectionSetDao["relation-museum"].orgId="other";val before=r.snapshot()
        assertThrows(BadRequestException::class.java){when(entry){"list"->r.service.list("parent-museum");"save"->r.save();else->r.service.delete("parent-museum","relation-museum")}}
        assertEquals(before,r.snapshot())
    }
    @ParameterizedTest @ValueSource(strings=["foreign","missing"])
    fun `local relation cannot expose an invalid child identity`(kind:String)=fixture {r->
        CollectionSetDao["relation-museum"].childCollectionId=if(kind=="foreign")"child-other" else "missing";val before=r.snapshot()
        assertThrows(BadRequestException::class.java){r.service.list("parent-museum")};assertEquals(before,r.snapshot())
    }
    @Test fun `current local registrar can create update delete and reserve independent set numbers`()=fixture {r->
        r.save();assertEquals(2,r.service.list("parent-museum").single().quantity)
        val local=r.save(null);r.service.delete("parent-museum",local)
        val result=r.service.createSetEntry(r.entry());val ids=listOf(result.mainCollectionId)+result.childCollectionIds
        assertEquals(3,ids.size);assertEquals(3,ids.map {CollectionDao[it].totalRegNo}.toSet().size)
        assertTrue(ids.all {CollectionDao[it].orgId=="museum" && CollectionDao[it].lifeStatus==EnumCollectionStatus.PENDING && CollectionDao[it].registeredAt==null})
        assertEquals(3L,CollectionRegistrationNumberDao.count());assertEquals(3L,CollectionCatalogDao.count());assertEquals(3L,CollectionLedgerLogDao.count())
        assertEquals(result.childCollectionIds.toSet(),r.service.list(result.mainCollectionId).mapNotNull {it.childCollectionId}.toSet())
    }
    @Test fun `bulk entry follows current primary museum rather than old login`()=fixture("multi") {r->
        UserDao["reader"].groupId="other";val result=r.service.createSetEntry(r.entry())
        assertTrue((listOf(result.mainCollectionId)+result.childCollectionIds).all {CollectionDao[it].orgId=="other"})
    }
    @Test fun `historical cross museum reads never authorize relation writes`()=fixture("multi") {r->
        r.grant(CollectionConst.SELECT,CollectionConst.CROSS_ORG);CollectionOrgDao["other"].orgStatus=EnumOrgStatus.DISABLED
        assertEquals("relation-other",r.service.list("parent-other").single().id);assertThrows(BadRequestException::class.java){r.save("relation-other","other")}
    }
    @ParameterizedTest @ValueSource(strings=["configured","museum_disabled","storage","revoked"])
    fun `actual administrator cannot bypass ledger duties or configured museum`(damage:String)=fixture {r->
        r.administrator();r.damage(damage);val before=r.snapshot();assertThrows(BadRequestException::class.java){r.save()};assertEquals(before,r.snapshot())
    }
    @Test fun `valid current child links still reject cycle and duplicate part numbers`()=fixture {r->
        assertThrows(BadRequestException::class.java){r.service.save("child-museum",CollectionSetVo(childCollectionId="parent-museum",partNo="C",partName="环",relation=EnumSetRelation.PART))}
        assertThrows(BadRequestException::class.java){r.service.save("parent-museum",CollectionSetVo(partNo="01",partName="重复",relation=EnumSetRelation.PART))}
        assertEquals("relation-museum",r.service.list("parent-museum").single().id)
    }
    @Test fun `foreign nested relation cannot influence local cycle traversal`()=fixture {r->
        CollectionSetDao.new("foreign-nested"){orgId="other";parentCollectionId="child-museum";childCollectionId="child-other";partNo="X";partName="外馆资料";relation=EnumSetRelation.PART}
        val before=r.snapshot()
        assertThrows(BadRequestException::class.java){r.service.save("parent-museum",CollectionSetDao["relation-museum"].toModel())};assertEquals(before,r.snapshot())
    }
    @Test fun `actual administrator may work with explicit ledger custodian role`()=fixture {r->
        r.administrator();r.grant();assertThrows(BadRequestException::class.java){r.save()}
        actorRole("reader",CollectionConst.ROLE_LEDGER_CUSTODIAN);assertEquals("relation-museum",r.save())
    }
    @Test fun `removing old storage duties allows the currently qualified registrar`()=fixture {r->
        val old=SecurityUser(id="reader",name="旧库房职责",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),
            authorities=listOf(CollectionConst.LEDGER_CREATE,CollectionConst.STORAGE).map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(old,null,old.authorities)
        val result=r.service.createSetEntry(r.entry());assertEquals(2,result.childCollectionIds.size)
    }
}
