package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.InventoryItemCheckVo
import io.hizhengfu.cloud.collection.domain.InventoryCompletionVo
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanSessions
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionState
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionType
import io.hizhengfu.cloud.collection.domain.CollectionCareCheckVo
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime

data class MobileInventoryCheckRequest(val operationId: String, val baselineRevision: String, val value: InventoryItemCheckVo,
    val expectedCheckRevision: String = "NONE")
data class MobileCareCheckRequest(val operationId: String, val sourceRevision: String, val value: CollectionCareCheckVo)
data class MobileInventoryCompletionRequest(val baselineRevision:String,val value:InventoryCompletionVo,
    val checksRevision:String,val issueActionsRevision:String)

@Component
@Transactional
class CollectionMobileMutationService(
    private val scope: CollectionScopeResolver,
    private val inventory: InventoryComplianceService,
    private val care: CollectionCareService,
) {
    fun inventoryCheck(id: String, request: MobileInventoryCheckRequest): String {
        val batch = InventoryBatchDao.findById(id) ?: throw BadRequestException("盘库任务不存在")
        return mutate(batch.orgId, request.operationId, "INVENTORY:$id", request,
            setOf(CollectionConst.STORAGE)) {
            batch.refresh(flush = false)
            val baseline = batch.baseline ?: throw BadRequestException("盘库任务尚未冻结清单")
            if (sha256(JsonUtils.toJson(baseline)!!) != request.baselineRevision)
                throw BadRequestException("盘库清单版本已变化，请重新下载任务并核对现场草稿")
            val old = batch.checks?.items.orEmpty().singleOrNull { it.collectionId == request.value.collectionId }
            val revision = old?.let { sha256(JsonUtils.toJson(it)!!) } ?: "NONE"
            if (revision != request.expectedCheckRevision)
                throw BadRequestException("该藏品的核验记录已被更新，请核对本机草稿后重新提交")
            inventory.check(id, request.value)
        }
    }

    fun careCheck(id: String, request: MobileCareCheckRequest): String {
        val collection = CollectionDao.findById(id) ?: throw BadRequestException("藏品不存在")
        return mutate(collection.orgId, request.operationId, "CARE:$id", request,
            setOf(CollectionConst.CARE)) { care.inspect(id, request.value, request.sourceRevision) }
    }

    /** 与新一轮领取共用批次行锁，避免“核对已停止”后又有另一手持机领取的竞态。 */
    fun inventoryComplete(id:String,request:MobileInventoryCompletionRequest):String {
        val batch=InventoryBatchDao.findById(id) ?: throw BadRequestException("盘库任务不存在")
        CollectionCurrentOperationAccess.require(scope,batch.orgId,true,setOf(CollectionConst.STORAGE),"移动盘库签署")
        CollectionOrgs.selectAll().where{CollectionOrgs.id eq batch.orgId}.forUpdate().single()
        InventoryBatches.selectAll().where{InventoryBatches.id eq id}.forUpdate().single()
        batch.refresh(flush=false)
        val baseline=batch.baseline ?: throw BadRequestException("盘库任务尚未冻结清单")
        if(sha256(JsonUtils.toJson(baseline)!!)!=request.baselineRevision)
            throw BadRequestException("盘库范围已变化，请重新下载核对后签署")
        if((batch.checks?.let{sha256(JsonUtils.toJson(it)!!)} ?: "NONE")!=request.checksRevision ||
            (batch.issueActions?.let{sha256(JsonUtils.toJson(it)!!)} ?: "NONE")!=request.issueActionsRevision)
            throw BadRequestException("逐件核验或差异处置已更新，请重新核对签署内容")
        if(RfidScanSessions.selectAll().where {
            (RfidScanSessions.orgId eq batch.orgId) and (RfidScanSessions.businessId eq id) and
                (RfidScanSessions.sessionType eq EnumRfidScanSessionType.INVENTORY) and
                (RfidScanSessions.directInput eq true) and (RfidScanSessions.sessionState inList setOf(
                    EnumRfidScanSessionState.CREATED,EnumRfidScanSessionState.STARTING,EnumRfidScanSessionState.READING,
                    EnumRfidScanSessionState.STOPPING,EnumRfidScanSessionState.PREVIEW_READY,EnumRfidScanSessionState.FINALIZING))
        }.limit(1).any())throw BadRequestException("本任务仍有手持机扫描未结束并封存，请协调结束和同步后再签署")
        return inventory.complete(id,request.value)
    }

    private fun mutate(orgId: String, operationId: String, target: String, payload: Any,
        permissions: Set<String>, execute: () -> String): String {
        if (operationId.isBlank() || operationId.length > 100) throw BadRequestException("移动操作编号无效")
        CollectionCurrentOperationAccess.require(scope, orgId, true, permissions, "移动现场记录")
        // 与清点业务一致先锁馆行；相同操作在多实例重传时也只有一次业务写入。
        CollectionOrgs.selectAll().where { CollectionOrgs.id eq orgId }.forUpdate().single()
        val userId = SecurityUtils.getUserDetail().id
        val hash = sha256(JsonUtils.toJson(payload)!!)
        MobileOperationReceipts.selectAll().where {
            (MobileOperationReceipts.orgId eq orgId) and (MobileOperationReceipts.userId eq userId) and
                (MobileOperationReceipts.operationId eq operationId)
        }.singleOrNull()?.let {
            if (it[MobileOperationReceipts.target] != target || it[MobileOperationReceipts.payloadHash] != hash)
                throw BadRequestException("移动操作编号已用于其他内容，请保留原始草稿并核对")
            return it[MobileOperationReceipts.resultId]
        }
        val result = execute()
        MobileOperationReceipts.insert {
            it[id] = IdGenerate.nextId(); it[MobileOperationReceipts.orgId] = orgId
            it[MobileOperationReceipts.userId] = userId; it[MobileOperationReceipts.operationId] = operationId
            it[MobileOperationReceipts.target] = target; it[payloadHash] = hash; it[resultId] = result
            it[createdAt] = LocalDateTime.now()
        }
        return result
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
