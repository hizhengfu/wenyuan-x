package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.service.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import java.time.LocalDateTime
import java.nio.file.Path

internal fun <E : FlowUIDEntity> assertBusinessReentryDenied(service: FlowService<E>, entity: E, node: String) {
    val before=listOf(entity.flowStatus,entity.flowInstanceId,entity.updateTime,entity.updateBy)
    val instance=entity.flowInstanceId!!
    assertThrows(BadRequestException::class.java) { service.onFlowJumped(entity,FlowJumpedEvent(instance,service.definitionKey(),"原审批",entity.id.value,
        service.businessType(),listOf(node),node,"重新确认","核对","manager","管理员","owner","发起人员",emptyMap())) }
    assertThrows(BadRequestException::class.java) { service.onFlowReactivated(entity,FlowReactivatedEvent(instance,service.definitionKey(),"原审批",entity.id.value,
        service.businessType(),"COMPLETED",node,"重新确认","核对","manager","管理员","owner","发起人员",emptyMap())) }
    assertEquals(before,listOf(entity.flowStatus,entity.flowInstanceId,entity.updateTime,entity.updateBy))
}

/** 实际业务表及实体回调前置；不代替引擎入口、资源占用和真实原件的联合验收。 */
class CollectionBusinessReentryPersistenceTest {
    @TempDir lateinit var dir: Path
    private fun <E : FlowUIDEntity> reenter(service: FlowService<E>, entity: E, node: String, action: String) {
        if (action == "JUMP") service.onFlowJumped(entity, FlowJumpedEvent("original", service.definitionKey(), "原审批", entity.id.value,
            service.businessType(), listOf(node), node, "重新确认", "核对后办理", "manager", "管理人员", "owner", "发起人员", emptyMap()))
        else service.onFlowReactivated(entity, FlowReactivatedEvent("original", service.definitionKey(), "原审批", entity.id.value,
            service.businessType(), "CANCELED", node, "重新确认", "核对后办理", "manager", "管理人员", "owner", "发起人员", emptyMap()))
    }

    @ParameterizedTest @ValueSource(strings = ["appraised", "plan-linked", "exhibition-ongoing", "exhibition-started", "exhibition-returned", "exhibition-requisition", "valid-plan", "valid-exhibition"])
    fun `jump and reactivation preserve completed facts and referenced approvals while unstarted originals remain reviewable`(kind: String) {
        StaticApplicationContext().use { context ->
            context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager()); context.refresh(); SpringContextHolder().setApplicationContext(context)
            val database = Database.connect("jdbc:sqlite:${dir.resolve("business.db")}", driver="org.sqlite.JDBC")
            val tables=listOf(Appraisals, AcquisitionPlans, Acquisitions, Exhibitions, CollectionPlacements, Requisitions, Warehouses)
            fun snapshot()=transaction(database) { tables.map { t -> t.selectAll().orderBy(t.id).map { r -> t.columns.map { r[it] } } } }
            lateinit var operation: (String) -> Unit
            lateinit var record: () -> FlowUIDEntity
            transaction(database) {
                SchemaUtils.create(Appraisals, AcquisitionPlans, Acquisitions, Exhibitions, CollectionPlacements, Requisitions, Warehouses)
                val scope=CollectionScopeResolver("multi", "")
                val flow=mock(FlowInstanceService::class.java); val binder=mock(BusinessAttachmentBinder::class.java)
                val locks=mock(CollectionOperationLockService::class.java)
                when {
                    kind == "appraised" -> {
                        val e=AppraisalDao.new("record") { orgId="museum"; collectionId="object"; conclusion="原鉴选结论"; appraisedAt=LocalDateTime.of(2026,9,1,10,0); flowStatus=EnumFlowStatus.PASS; flowInstanceId="original" }
                        val service=AppraisalService(flow,scope,mock(CollectionUserResolver::class.java),mock(CollectionChangeService::class.java),binder,locks,mock(AccessionReceiptService::class.java))
                        operation={ reenter(service,AppraisalDao["record"],"committee",it) }; record={ AppraisalDao["record"] }
                    }
                    kind.endsWith("plan") || kind == "plan-linked" -> {
                        val e=AcquisitionPlanDao.new("record") { orgId="museum"; title="原计划"; year=2026; flowStatus=EnumFlowStatus.PASS; flowInstanceId="original" }
                        if(kind=="plan-linked") AcquisitionDao.new("child") { orgId="museum"; planId=e.id.value; name="按原批准计划编制的征集" }
                        val service=AcquisitionPlanService(flow,scope,binder)
                        operation={ reenter(service,AcquisitionPlanDao["record"],"committee",it) }; record={ AcquisitionPlanDao["record"] }
                    }
                    else -> {
                        WarehouseDao.new("case") { orgId="museum"; name="原展柜"; sceneType=EnumLocationScene.EXHIBITION; type=EnumWarehouseType.SHOWCASE }
                        val e=ExhibitionDao.new("record") { orgId="museum"; title="原展览"; collectionIds="object"; flowStatus=EnumFlowStatus.PASS; flowInstanceId="original"; if(kind=="exhibition-ongoing")state=EnumProcessState.ONGOING }
                        CollectionPlacementDao.new("placement") { orgId="museum"; collectionId="object"; locationId="case"; sourceBizType=CollectionConst.BIZ_EXHIBITION; sourceBizId=e.id.value; approved=true
                            if(kind=="exhibition-started")validFrom=LocalDateTime.of(2026,9,1,10,0)
                            if(kind=="exhibition-returned")validTo=LocalDateTime.of(2026,9,2,10,0)
                        }
                        if(kind=="exhibition-requisition")RequisitionDao.new("child") { orgId="museum"; sourceBizType=CollectionConst.BIZ_EXHIBITION; sourceBizId=e.id.value; flowInstanceId="original-outbound" }
                        val service=ExhibitionService(flow,scope,binder,mock(RequisitionService::class.java),mock(CollectionBusinessItemService::class.java),locks,CollectionPlacementService())
                        operation={ reenter(service,ExhibitionDao["record"],"approve",it) }; record={ ExhibitionDao["record"] }
                    }
                }
            }
            val before=snapshot()
            for(action in listOf("JUMP","REACTIVATE")) {
                if(kind.startsWith("valid")) {
                    transaction(database) {
                        operation(action); assertEquals(EnumFlowStatus.AUDIT,record().flowStatus)
                        assertEquals("original",record().flowInstanceId)
                        if(kind=="valid-exhibition")assertFalse(CollectionPlacementDao["placement"].approved)
                    }
                } else {
                    assertThrows(BadRequestException::class.java) { transaction(database) { operation(action) } }
                    assertEquals(before,snapshot())
                }
            }
        }
    }
}
