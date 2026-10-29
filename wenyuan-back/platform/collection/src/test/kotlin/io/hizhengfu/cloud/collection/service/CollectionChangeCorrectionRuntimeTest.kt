package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.exception.ForbiddenException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.engine.*
import io.hizhengfu.cloud.flow.service.*
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime

/** 实际业务、原件档案表、账号、事务、引擎及总账/编目；文件绑定协作者不访问真实存储。 */
class CollectionChangeCorrectionRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate) {
        val service = c.getBean(CollectionChangeService::class.java)
        lateinit var paperBookId: String
        fun execution() = CollectionChangeExecutionVo("signed", null, LocalDate.now(), paperBookId, 1)
        val tasks = c.getBean(FlowTaskService::class.java)
        val instances = c.getBean(FlowInstanceService::class.java)
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun login(id: String) {
            val user=db { c.getBean(CollectionActorDirectory::class.java).requireActive(id) }
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,"",user.authorities)
        }
        fun act(id: String, person: String, action: String="APPROVE") {
            val task=db { FlowTaskDao.find {(FlowTasks.instanceId eq CollectionChangeDao[id].flowInstanceId!!) and (FlowTasks.status eq "PENDING")}.single().id.value }
            tasks.complete(CompleteTaskRequest(task,action,comment="本人核对材料"),person,person)
        }
        fun draft(): String {
            val p=service.proposal("object")
            return service.save(p.copy(reason="按原件核对定名",content=p.content!!.copy(formalName="原拟定名"),keeperUserId="keeper",leaderUserId="leader",evidenceFileIds="evidence"))
        }
        fun setDraft(): String {
            db {
                CollectionDao.new("part") {orgId="a";name="原分件藏品";totalRegNo="002";registeredAt=LocalDateTime.of(2020,1,2,10,0);catalogStatus=EnumCatalogStatus.APPROVED}
                CollectionSetDao.new("relation") {orgId="a";parentCollectionId="object";childCollectionId="part";partNo="01";partName="原套件关系";relation=EnumSetRelation.PART}
            }
            val p=service.proposal("object",true)
            return service.save(p.copy(reason="原件核对套件关系",setAmendment=p.setAmendment!!.copy(after=p.setAmendment.after.map {it.copy(partName="订正套件关系")}),
                keeperUserId="keeper",leaderUserId="leader",evidenceFileIds="evidence"))
        }
        fun original(): String {
            val id=draft()
            service.submit(id);act(id,"keeper");act(id,"leader","REJECT")
            return id
        }
        fun request(id:String,key:String="request")=CollectionChangeCorrectionRequest(key,db { CollectionChangeDao[id].flowInstanceId!! },"补充新发现的定名依据","correction-file")
        fun snapshot()=db { listOf(CollectionSets,CollectionLedgerLogs,CollectionCatalogVersions,CollectionChanges,FlowInstances,FlowTasks,FlowInstanceLogs,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Collections,CollectionOperationLocks).map {t->
            t.selectAll().orderBy(t.id).map {r->t.columns.map {r[it]}}
        } }
    }
    private fun fixture(body:(Runtime)->Unit) {
        val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("correction.db")}"},DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("scope",CollectionScopeResolver("multi",""))
            c.beanFactory.registerSingleton("binder",mock(BusinessAttachmentBinder::class.java).also { b->
                `when`(b.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0).trim().takeIf(String::isNotBlank)}
            })
            c.beanFactory.registerSingleton("grading",mock(CollectionGradingService::class.java))
            c.beanFactory.registerSingleton("receipt",mock(AccessionReceiptService::class.java))
            c.beanFactory.registerSingleton("loader",FlowGraphLoader(16,5))
            c.beanFactory.registerSingleton("registry",FlowBusinessTypeRegistry())
            c.beanFactory.registerSingleton("resolver",FlowAssigneeResolver())
            c.register(Transactions::class.java,CollectionChangeService::class.java,CollectionCatalogService::class.java,CollectionOperationLockService::class.java,
                CollectionPermanentArchiveService::class.java, io.hizhengfu.cloud.common.file.FileReadAccess::class.java,CollectionRetainedLedgerService::class.java,CollectionActorDirectory::class.java,CollectionResponsibleUserService::class.java,PaperLedgerService::class.java,
                CoreCollectionFlowReadSources::class.java,CollectionFlowStartPolicy::class.java,CollectionNamedFlowActorPolicy::class.java,CollectionFlowFormPolicy::class.java,
                CollectionFlowDefinitionConstraint::class.java,FlowDefinitionService::class.java,FlowEngine::class.java,FlowInstanceService::class.java,FlowTaskService::class.java,FlowEventDispatcher::class.java)
            c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager))
            r.db {
                actorSchema()
                SchemaUtils.create(CollectionSets,CollectionOrgs,Collections,CollectionCatalogs,CollectionCatalogVersions,CollectionChanges,CollectionChangeApprovals,CollectionOperationLocks,CollectionRetainedLedgers,PaperLedgerBooks,PaperLedgerCustodies,
                    CollectionLedgerLogs,CollectionLedgerStatEvents,CollectionMedias,CollectionAttachments,CollectionCategories,Warehouses,
                    CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FileContents,FileUsers,FlowDefinitions,FlowNodeDefs,FlowEdgeDefs,FlowInstances,FlowTasks,FlowInstanceLogs,FlowCCs)
                listOf("a","b").forEach {id->CollectionOrgDao.new(id){code=id;name=id;systemGroupId="g-$id"};GroupDao.new("g-$id"){name=id;type='1';parentId="0";parentIds="0,"}}
                listOf("registrar","keeper","leader","ledgerKeeper","other").forEach {id->UserDao.new(id){name=id;loginName=id;password="unused";enabled=true;groupId=if(id=="other")"g-b" else "g-a"}}
                actorRole("registrar","REGISTRAR",setOf(CollectionConst.LEDGER_CORRECT,"FLOW_MANAGE"))
                actorRole("other","OTHER",setOf(CollectionConst.LEDGER_CORRECT))
                actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD);actorRole("leader",CollectionConst.ROLE_DIRECTOR)
                actorRole("ledgerKeeper",CollectionConst.ROLE_LEDGER_CUSTODIAN)
                listOf("evidence","correction-file","signed").forEach {id->FileDao.new(id){name="$id.pdf";path="/files/$id.pdf";ext="pdf";hash=id;size=10}}
                WarehouseDao.new("store") { orgId="a"; name="保管库房"; sceneType=EnumLocationScene.STORAGE }
                CollectionCategoryDao.new("ceramic") { name="瓷器"; code="03"; parentId="0" }
                val collection=CollectionDao.new("object") {orgId="a";totalRegNo="001";classNo="C-1";
                    actualQuantity=1;actualQuantityUnit="件";entryDate=LocalDate.of(2020,1,1);
                    preserveState=EnumPreserve.A;completeStatus="完整";feature="青花纹饰";coverImage="evidence";warehouseId="store";
                    registrationDetails=CollectionRegistrationDetailsVo(dimensionUnknownReason="装具内暂不能测量",weightUnknownReason="装具内暂不能称重",coverIsFrontView=true,eraMeaning=EnumCollectionEraMeaning.MANUFACTURED,eraNotation=EnumCollectionEraNotation.CHINESE_HISTORY);name="原定名";era="清";texture="瓷";textureCategory1="单一质地";textureCategory2="无机质/非金属材料";categoryId="ceramic";
                    level=EnumRelicLevel.GENERAL;source=EnumSource.OLD;acquireDate=LocalDate.of(2020,1,1);completeness=EnumCompleteness.COMPLETE;
                    lifeStatus=EnumCollectionStatus.IN_STORE;catalogStatus=EnumCatalogStatus.APPROVED;registeredAt=LocalDateTime.of(2020,1,2,10,0)}
                c.getBean(CollectionCatalogService::class.java).ensureCatalogFor(collection)
            }
            r.login("registrar")
            r.paperBookId = c.getBean(PaperLedgerService::class.java).register("a", PaperLedgerBookVo(
                "总账第一册", "总登记账", LocalDate.of(2020, 1, 1), 20, "测试藏品范围", "evidence",
                PaperLedgerCustodyVo(LocalDateTime.now().minusDays(1), "ledgerKeeper", "纸账专用室",
                    EnumPaperLedgerCondition.GOOD, "保存完好", "signed")))
            try {
                val json=javaClass.classLoader.getResourceAsStream("flow/collection_change.json")!!.use {JsonUtils.mapper.readTree(it).path("model").toString()}
                val definitions=c.getBean(FlowDefinitionService::class.java)
                val d=definitions.create(FlowDefinitionVo(key=CollectionConst.FLOW_CHANGE,name="原件补正核验",modelData=json),"author")
                definitions.publish(d.id.value,"publisher")
                body(r)
            } finally {SecurityContextHolder.clearContext()}
        }
    }

    @ParameterizedTest @CsvSource("revoked,submit","revoked,cancel","revoked,void","revoked,execute","revoked,correction",
        "moved,submit","moved,cancel","moved,void","moved,execute","moved,correction",
        "storage,submit","storage,cancel","storage,void","storage,execute","storage,correction")
    fun `current registrar changes reject real workflow mutations with complete transaction rollback`(damage:String,entry:String)=fixture {r->
        val id=if(entry=="correction")r.original() else r.draft()
        if(entry in setOf("cancel","void","execute")) {
            r.service.submit(id)
            if(entry!="cancel"){r.act(id,"keeper");r.act(id,"leader")}
        }
        val request=if(entry=="correction")r.request(id) else null
        r.db {when(damage) {
            "revoked"->RoleUsers.deleteWhere {userId eq "registrar"}
            "moved"->{UserDao["registrar"].groupId="g-b";actorRole("registrar","CROSS",setOf(CollectionConst.CROSS_ORG))}
            "storage"->actorRole("registrar",CollectionConst.ROLE_KEEPER)
        }}
        val before=r.snapshot()
        assertThrows(BadRequestException::class.java){when(entry) {
            "submit"->r.service.submit(id)
            "cancel"->r.service.cancel(id)
            "void"->r.service.void(id)
            "execute"->r.service.execute(id,r.execution())
            else->r.service.createCorrection(id,request!!)
        }}
        assertEquals(before,r.snapshot())
    }

    @Test fun `correction preserves original evidence and approval and requires its own complete signed execution`()=fixture {r->
        val old=r.original();val oldValue=r.service.get(old);val oldHistory=r.db {FlowInstanceLogs.selectAll().map {it[FlowInstanceLogs.id].value}}
        val request=r.request(old);val next=r.service.createCorrection(old,request)
        assertEquals(next,r.service.createCorrection(old,request))
        assertEquals(oldValue,r.service.get(old));assertEquals(oldHistory,r.db {FlowInstanceLogs.selectAll().map {it[FlowInstanceLogs.id].value}})
        val draft=r.service.get(next)
        assertEquals(EnumFlowStatus.DRAFT,draft.flowStatus);assertNull(draft.flowInstanceId);assertNull(draft.appliedAt);assertNull(draft.signedChangeFileIds)
        assertEquals(old,draft.correction!!.previousChangeId);assertEquals("registrar",draft.correction.recordedBy)
        assertEquals(1,(r.service.corrections(old)["following"] as List<*>).size)
        assertEquals(old,(r.service.corrections(next)["previous"] as Map<*,*>)["id"])
        val guard=CollectionArchiveFileRetentionGuard()
        assertThrows(BadRequestException::class.java){r.db {guard.requireDeletable("correction-file")}}
        r.service.save(draft.copy(content=draft.content!!.copy(formalName="补正后定名"),reason="核对新原件后的拟变更"))
        r.service.submit(next)
        assertEquals("原定名",r.db {CollectionDao["object"].name})
        val flow=r.db {FlowInstanceDao[CollectionChangeDao[next].flowInstanceId!!].formData!!}
        assertTrue("correction" in flow)
        r.act(next,"keeper");r.act(next,"leader")
        assertEquals(EnumFlowStatus.PASS,r.service.get(next).flowStatus);assertEquals("原定名",r.db {CollectionDao["object"].name})
        r.service.execute(next,r.execution())
        assertEquals("补正后定名",r.db {CollectionDao["object"].name});assertEquals("001",r.db {CollectionDao["object"].totalRegNo})
        assertEquals(r.paperBookId,r.db {CollectionChangeDao[next].paperLedgerBookId})
        assertEquals(1,r.db {CollectionChangeDao[next].paperLedgerPageNo})
        assertEquals(oldValue.copy(collectionName="补正后定名"),r.service.get(old));assertNotNull(r.service.get(next).appliedAt)
    }

    @Test fun `signed change refuses unregistered or out of range paper pages without partial writes`()=fixture {r->
        val id=r.draft();r.service.submit(id);r.act(id,"keeper");r.act(id,"leader")
        val before=r.snapshot()
        assertThrows(BadRequestException::class.java){r.service.execute(id,r.execution().copy(paperLedgerBookId=null,paperLedgerRef="任意纸账引用"))}
        assertThrows(BadRequestException::class.java){r.service.execute(id,r.execution().copy(paperLedgerPageNo=21))}
        assertThrows(BadRequestException::class.java){r.service.execute(id,r.execution().copy(paperLedgerRef="伪造页码"))}
        assertEquals(before,r.snapshot())
        r.service.execute(id,r.execution())
        assertEquals("总账第一册 第1页",r.db {CollectionChangeDao[id].paperLedgerRef})
    }

    @ParameterizedTest @ValueSource(strings=["reason","files","stale-instance","original-drift","paper-location","missing-archive","digest","pending","running-child","foreign","permission"])
    fun `invalid correction sources or authority roll back all writes`(kind:String)=fixture {r->
        val old=r.original();var request=r.request(old)
        when(kind){
            "reason"->request=request.copy(reason=" ");"files"->request=request.copy(evidenceFileIds=" ");"stale-instance"->request=request.copy(expectedInstanceId="another")
            "original-drift"->r.db {CollectionChangeDao[old].reason="与原件不同"}
            "paper-location"->r.db {CollectionChangeDao[old].paperLedgerBookId=r.paperBookId}
            "missing-archive"->r.db {CollectionPermanentArchives.deleteAll()}
            "digest"->r.db {CollectionPermanentArchiveDao.all().first {it.bizType==CollectionConst.BIZ_CHANGE && it.bizId==old && it.phase=="SUBMITTED"}.digest="wrong"}
            "pending"->r.db {FlowTaskDao.new {instanceId=request.expectedInstanceId;definitionId=FlowInstanceDao[instanceId].definitionId;nodeKey="ghost";nodeName="未结束任务";status="PENDING";type="HANDLE"}}
            "running-child"->r.db {FlowInstanceDao.new {definitionId=FlowInstanceDao[request.expectedInstanceId].definitionId;definitionKey="child";definitionName="child";definitionVersion=1;title="child";initiator="registrar";initiatorName="registrar";parentInstanceId=request.expectedInstanceId;status="RUNNING"}}
            "foreign"->r.login("other")
            "permission"->r.db {RoleUsers.deleteWhere {userId eq "registrar"}}
        }
        val before=r.snapshot();val failure=assertThrows(RuntimeException::class.java){r.service.createCorrection(old,request)}
        assertTrue(failure is BadRequestException || failure is ForbiddenException, failure.toString());assertEquals(before,r.snapshot())
    }

    @ParameterizedTest @ValueSource(strings=["CANCEL","VOID"])
    fun `formally canceled or voided submissions keep their original evidence when corrected`(state:String)=fixture { r->
        val old=r.original()
        r.instances.reactivate(r.request(old).expectedInstanceId,ReactivateFlowRequest("keeper"),"registrar","管理员")
        r.service.cancel(old)
        if(state=="VOID")r.service.void(old)
        val next=r.service.createCorrection(old,r.request(old))
        assertEquals(EnumFlowStatus.valueOf(state),r.service.get(next).correction!!.previousStatus)
        r.service.submit(next);r.act(next,"keeper");r.act(next,"leader")
        assertEquals(EnumFlowStatus.PASS,r.service.get(next).flowStatus)
        assertEquals(EnumFlowStatus.valueOf(state),r.service.get(old).flowStatus)
    }

    @Test fun `a successor prevents old reactivation and parallel drafts while an abandoned draft stays in history`()=fixture {r->
        val old=r.original();val request=r.request(old);val next=r.service.createCorrection(old,request)
        val before=r.snapshot()
        assertThrows(BadRequestException::class.java){r.service.createCorrection(old,request.copy(requestKey="second"))};assertEquals(before,r.snapshot())
        assertThrows(BadRequestException::class.java){r.instances.reactivate(request.expectedInstanceId,ReactivateFlowRequest("keeper"),"registrar","管理员")};assertEquals(before,r.snapshot())
        assertThrows(BadRequestException::class.java){r.service.void(old)};assertEquals(before,r.snapshot())
        r.service.void(next)
        val replacement=r.service.createCorrection(old,request.copy(requestKey="replacement",reason="补正草稿作废后重新核对"))
        assertNotEquals(next,replacement);assertEquals(EnumFlowStatus.VOID,r.service.get(next).flowStatus)
        assertEquals(2,(r.service.corrections(old)["following"] as List<*>).size)
    }

    @ParameterizedTest @ValueSource(strings=["REJECT","CANCEL"])
    fun `a later correction blocks reopening and branching from a closed sibling`(state:String)=fixture {r->
        val old=r.original();val first=r.service.createCorrection(old,r.request(old,"first"))
        r.service.submit(first)
        if(state=="REJECT"){r.act(first,"keeper");r.act(first,"leader","REJECT")}else r.service.cancel(first)
        val second=r.service.createCorrection(old,r.request(old,"second"));val before=r.snapshot()
        assertThrows(BadRequestException::class.java){r.instances.reactivate(r.request(first).expectedInstanceId,ReactivateFlowRequest("keeper"),"registrar","管理员")}
        assertEquals(before,r.snapshot())
        assertThrows(BadRequestException::class.java){r.service.createCorrection(first,r.request(first,"branch"))};assertEquals(before,r.snapshot())
        r.service.submit(second);r.act(second,"keeper");r.act(second,"leader")
        assertEquals(EnumFlowStatus.PASS,r.service.get(second).flowStatus);assertEquals(EnumFlowStatus.valueOf(state),r.service.get(first).flowStatus)
    }

    @Test fun `an active descendant also prevents a new parallel branch from the original`()=fixture {r->
        val old=r.original();val first=r.service.createCorrection(old,r.request(old,"first"));r.service.submit(first);r.service.cancel(first)
        val second=r.service.createCorrection(first,r.request(first,"second"));val before=r.snapshot()
        assertThrows(BadRequestException::class.java){r.service.createCorrection(old,r.request(old,"parallel"))};assertEquals(before,r.snapshot())
        r.service.submit(second);r.act(second,"keeper");r.act(second,"leader");assertEquals(EnumFlowStatus.PASS,r.service.get(second).flowStatus)
    }

    @ParameterizedTest @ValueSource(strings=["origin","archive","source-reopened"])
    fun `submission rechecks formal correction origin before creating any new workflow`(kind:String)=fixture {r->
        val old=r.original();val next=r.service.createCorrection(old,r.request(old))
        r.db {when(kind){
            "origin"->CollectionChangeDao[next].correction=CollectionChangeDao[next].correction!!.copy(reason="替换来由")
            "archive"->CollectionPermanentArchiveDao.find {CollectionPermanentArchives.bizId eq next}.single().snapshot="changed"
            else->FlowInstanceDao[CollectionChangeDao[old].flowInstanceId!!].status="RUNNING"
        }}
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.service.submit(next)};assertEquals(before,r.snapshot())
    }
    @Test fun `real flow retains set relation until statutory approval and signed execution`()=fixture {r->
        val id=r.setDraft();r.service.submit(id)
        assertEquals(setOf("object","part"),r.db {CollectionOperationLockDao.all().map{it.collectionId}.toSet()})
        r.act(id,"keeper");assertEquals("原套件关系",r.db {CollectionSetDao["relation"].partName})
        r.act(id,"leader");assertEquals("原套件关系",r.db {CollectionSetDao["relation"].partName})
        r.service.execute(id,r.execution())
        assertEquals("订正套件关系",r.db {CollectionSetDao["relation"].partName})
        assertEquals(0L,r.db {CollectionOperationLockDao.all().count()})
        assertEquals(setOf("SUBMITTED","SET_APPLIED","APPLIED"),r.db {CollectionPermanentArchiveDao.all().filter{it.bizType==CollectionConst.BIZ_CHANGE}.map{it.phase}.filterNot{it.startsWith("APPROVED_")}.toSet()})
    }

    @Test fun `late signed binding failure rolls back set rows ledger and archive as one transaction`()=fixture {r->
        val id=r.setDraft();r.service.submit(id);r.act(id,"keeper");r.act(id,"leader")
        val binder=r.c.getBean(BusinessAttachmentBinder::class.java)
        doThrow(BadRequestException("签署文件绑定故障")).`when`(binder).bind(id,"已签署藏品信息变更表","signed")
        val before=r.snapshot()
        assertThrows(BadRequestException::class.java){r.service.execute(id,r.execution())}
        assertEquals(before,r.snapshot());assertEquals("原套件关系",r.db {CollectionSetDao["relation"].partName})
        doNothing().`when`(binder).bind(id,"已签署藏品信息变更表","signed")
        r.service.execute(id,r.execution())
        assertEquals("订正套件关系",r.db {CollectionSetDao["relation"].partName})
    }

    @ParameterizedTest @ValueSource(strings=["child_lock","changed_relation","changed_identity"])
    fun `set submission rejects conflicting member or changed baseline with full rollback`(kind:String)=fixture {r->
        val id=r.setDraft()
        r.db {when(kind) {
            "child_lock"->r.c.getBean(CollectionOperationLockService::class.java).acquire("a","OTHER","busy",listOf("part"))
            "changed_relation"->CollectionSetDao["relation"].partName="另有订正"
            else->CollectionDao["part"].name="身份变化"
        }}
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.service.submit(id)};assertEquals(before,r.snapshot())
    }

    @Test fun `canceled set amendment releases all members and keeps submitted original`()=fixture {r->
        val id=r.setDraft();r.service.submit(id);val submitted=r.db {CollectionPermanentArchiveDao.all().single{it.phase=="SUBMITTED"}.snapshot}
        r.service.cancel(id)
        assertEquals(0L,r.db {CollectionOperationLockDao.all().count()})
        assertEquals("原套件关系",r.db {CollectionSetDao["relation"].partName})
        assertEquals(submitted,r.db {CollectionPermanentArchiveDao.all().single{it.phase=="SUBMITTED"}.snapshot})
    }

    @Test fun `rejected set amendment correction retains original and requires separate approval`()=fixture {r->
        val id=r.setDraft();r.service.submit(id);r.act(id,"keeper");r.act(id,"leader","REJECT")
        val before=r.db {CollectionChangeDao[id].setAmendment}
        val next=r.service.createCorrection(id,r.request(id));assertEquals(before,r.db {CollectionChangeDao[next].setAmendment})
        assertEquals("原套件关系",r.db {CollectionSetDao["relation"].partName})
        r.service.submit(next);r.act(next,"keeper");r.act(next,"leader")
        r.service.execute(next,r.execution())
        assertEquals(before,r.db {CollectionChangeDao[id].setAmendment})
        assertEquals("订正套件关系",r.db {CollectionSetDao["relation"].partName})
    }

    @ParameterizedTest @ValueSource(strings=["remove","replace"])
    fun `actual signed execution can remove or replace a formal part preserving original identity`(operation:String)=fixture {r->
        val id=r.setDraft();val draft=r.service.get(id)
        if(operation=="replace")r.db {CollectionDao.new("replacement"){orgId="a";name="更正独立分件";totalRegNo="003";registeredAt=LocalDateTime.of(2020,1,2,10,0)}}
        val after=if(operation=="remove")emptyList() else listOf(CollectionSetRelationSnapshot(childCollectionId="replacement",partNo="02",partName="更正独立分件",relation=EnumSetRelation.PART))
        r.service.save(draft.copy(setAmendment=draft.setAmendment!!.copy(after=after)))
        r.service.submit(id);r.act(id,"keeper");r.act(id,"leader")
        r.service.execute(id,r.execution())
        assertNull(r.db {CollectionSetDao.findById("relation")})
        assertEquals(if(operation=="remove")0L else 1L,r.db {CollectionSetDao.all().count()})
        val original=r.db {CollectionPermanentArchiveDao.all().single{it.phase=="SET_APPLIED"}.snapshot}
        assertTrue(original.contains("原分件藏品"));assertTrue(original.contains("relation"))
        if(operation=="replace")assertTrue(original.contains(r.db{CollectionSetDao.all().single().id.value}))
    }

}
