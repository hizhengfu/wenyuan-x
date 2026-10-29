package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.LoanDao
import io.hizhengfu.cloud.collection.dao.MovementVo
import io.hizhengfu.cloud.collection.dao.RequisitionDao
import io.hizhengfu.cloud.collection.dao.StorageMovementDao
import io.hizhengfu.cloud.collection.dao.StorageMovements
import io.hizhengfu.cloud.collection.domain.CollectionLifecyclePolicy
import io.hizhengfu.cloud.collection.domain.CollectionImportTemplateColumnVo
import io.hizhengfu.cloud.collection.domain.CollectionOutboundPolicy
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.CollectionDutySeparationPolicy
import io.hizhengfu.cloud.collection.domain.CollectionWordMovementPreviewVo
import io.hizhengfu.cloud.collection.domain.MovementExecutionPolicy
import io.hizhengfu.cloud.collection.domain.MovementImportCommitResultVo
import io.hizhengfu.cloud.collection.domain.MovementImportRowVo
import io.hizhengfu.cloud.collection.domain.MovementImportValidateResultVo
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 出入库流水（账物核对）。一级文物出入库强制双人复核。 */
@Component
@Transactional
class StorageMovementService(
    private val scope: CollectionScopeResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val operationLockService: CollectionOperationLockService,
    private val originalFiles: CollectionBusinessOriginalFiles? = null,
) : CrudService<StorageMovementDao>(StorageMovementDao) {

    fun findByCollection(collectionId: String): List<MovementVo> {
        val collection = CollectionDao.findById(collectionId) ?: throw BadRequestException("藏品不存在")
        OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), collection.orgId, "藏品出入库记录")
        return StorageMovementDao.find { StorageMovements.collectionId eq collectionId }
            .sortedByDescending { it.operatedAt }
            .map { it.toModel() }
            .let(::enrich)
    }

    fun findPage(request: HttpServletRequest): GenericPage<MovementVo> {
        val page = GenericPage<MovementVo>().build(request)
        val filters = MovementQueryFilters.from { request.getParameter(it) }
        val q = StorageMovements.selectAll()
        scope.readableOrgIds()?.let { ids -> q.andWhere { StorageMovements.orgId inList ids } }
        filters.keyword?.let { keyword ->
            q.andWhere {
                (StorageMovements.documentNo like "%$keyword%") or
                        (StorageMovements.collectionId like "%$keyword%") or
                        (StorageMovements.basis like "%$keyword%") or
                        (StorageMovements.department like "%$keyword%") or
                        (StorageMovements.operator like "%$keyword%") or
                        (StorageMovements.reviewer like "%$keyword%")
            }
        }
        filters.collectionId?.let { q.andWhere { StorageMovements.collectionId eq it } }
        filters.documentNo?.let { q.andWhere { StorageMovements.documentNo like "%$it%" } }
        filters.type?.let { q.andWhere { StorageMovements.type eq it } }
        filters.reason?.let { q.andWhere { StorageMovements.reason eq it } }
        filters.operator?.let { q.andWhere { StorageMovements.operator like "%$it%" } }
        filters.reviewer?.let { q.andWhere { StorageMovements.reviewer like "%$it%" } }
        filters.department?.let { q.andWhere { StorageMovements.department like "%$it%" } }
        filters.hasAttachment?.let { has ->
            q.andWhere {
                if (has) {
                    StorageMovements.attachmentIds.isNotNull() and (StorageMovements.attachmentIds neq "")
                } else {
                    StorageMovements.attachmentIds.isNull() or (StorageMovements.attachmentIds eq "")
                }
            }
        }
        filters.operatedAtStart?.let { q.andWhere { StorageMovements.operatedAt greaterEq it } }
        filters.operatedAtEnd?.let { q.andWhere { StorageMovements.operatedAt lessEq it } }
        q.orderBy(StorageMovements.operatedAt to SortOrder.DESC)

        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<StorageMovementDao>)
        page.list = daoPage.data?.map { it.toModel() }?.let(::enrich)
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<MovementVo>
    }

    /** 已退出日常导入入口；模板和预校验也不再引导用户填写无法提交的流水。 */
    fun importTemplate(): List<CollectionImportTemplateColumnVo> = MovementExecutionPolicy.requireBusinessExecution()

    @Suppress("UNUSED_PARAMETER")
    fun validateImport(rows: List<MovementImportRowVo>): MovementImportValidateResultVo =
        MovementExecutionPolicy.requireBusinessExecution()

    @Suppress("UNUSED_PARAMETER")
    fun commitImport(rows: List<MovementImportRowVo>): MovementImportCommitResultVo =
        MovementExecutionPolicy.requireBusinessExecution()

    /**
     * 写入经“动态跟踪记录汇总表”结构校验确认的历史流水。
     * 历史补录只还原事实时间和库位，不触发当前藏品状态迁移，也不伪造今日审批单。
     */
    fun createHistoricalImport(
        collectionId: String,
        sourceImportId: String,
        sequence: Int,
        item: CollectionWordMovementPreviewVo,
        sourceTotalRegNo: String? = null,
        sourceCollectionName: String? = null,
    ): String {
        val coll = CollectionDao.findById(collectionId) ?: throw BadRequestException("藏品不存在")
        val actor = CollectionCurrentOperationAccess.require(scope, coll.orgId, true,
            setOf(CollectionConst.STORAGE), "历史出入库补录")
        CollectionDutySeparationPolicy.requireStorageOperation(actor.permissions, actor.roleCodes)
        val fromLocationId = CollectionLocationMutation.reference(coll.orgId, item.fromWarehouseId)?.id?.value
        val toLocationId = CollectionLocationMutation.reference(coll.orgId, item.toWarehouseId)?.id?.value
        val documentNo = "DT-${coll.totalRegNo}-${item.operatedAt.toLocalDate()}-$sequence".take(64)
        val movement = dao.new {
            orgId = coll.orgId
            this.documentNo = documentNo
            this.collectionId = collectionId
            totalRegNoAtMovement = sourceTotalRegNo?.takeIf(String::isNotBlank)
            collectionNameAtMovement = sourceCollectionName?.takeIf(String::isNotBlank)
            type = item.type
            reason = item.reason
            basis = listOfNotNull(
                item.basis?.takeIf(String::isNotBlank),
                item.fromWarehouseName?.takeIf { fromLocationId == null }?.let { "原库位：$it" },
                item.toWarehouseName?.takeIf { toLocationId == null }?.let { "目标库位：$it" },
            ).joinToString("；").takeIf(String::isNotBlank)?.take(500)
            fromWarehouseId = fromLocationId
            toWarehouseId = toLocationId
            operator = item.operator?.take(64)
            reviewOpinion = item.remark?.take(500)
            operatedAt = item.operatedAt
            refBizId = sourceImportId
        }
        return movement.id.value
    }

    /** 兼容旧 HTTP 写入口；实际流转由各业务执行服务生成流水，不能凭客户端批准编号直接改账。 */
    @Suppress("UNUSED_PARAMETER")
    fun create(vo: MovementVo): String = MovementExecutionPolicy.requireBusinessExecution()

    private fun enrich(items: List<MovementVo>): List<MovementVo> {
        val ids = items.mapNotNull { it.collectionId }.toSet()
        if (ids.isEmpty()) return items
        val collections = CollectionDao.find { Collections.id inList ids }
            .associateBy({ it.id.value }, { it.toModel() })
        return items.map { item ->
            val collection = item.collectionId?.let(collections::get)
            item.copy(
                totalRegNo = item.totalRegNo ?: collection?.totalRegNo,
                collectionName = item.collectionName ?: collection?.name,
            )
        }
    }

    private fun visibleCollectionIndex(): Triple<Set<String>, Map<String, String>, Set<String>> {
        val q = Collections.selectAll()
        scope.readableOrgIds()?.let { ids -> q.andWhere { Collections.orgId inList ids } }
        val ids = HashSet<String>()
        val byRegNo = HashMap<String, String>()
        val firstLevelIds = HashSet<String>()
        q.forEach {
            val id = it[Collections.id].value
            ids += id
            it[Collections.totalRegNo].takeIf { regNo -> regNo.isNotBlank() }?.let { regNo -> byRegNo[regNo] = id }
            if (it[Collections.level] == EnumRelicLevel.FIRST) firstLevelIds += id
        }
        return Triple(ids, byRegNo, firstLevelIds)
    }
}
