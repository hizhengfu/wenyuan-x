package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionUserResolver
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.dao.WarehouseLogDao
import io.hizhengfu.cloud.collection.dao.WarehouseLogVo
import io.hizhengfu.cloud.collection.dao.WarehouseLogs
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** 库房日志：保管员记录当日库房事件，支持按库房/日期段/关键字翻查。 */
@Component
@Transactional
class WarehouseLogService(
    private val scope: CollectionScopeResolver,
    private val userResolver: CollectionUserResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val diaryService: WarehouseDiaryService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) : CrudService<WarehouseLogDao>(WarehouseLogDao) {

    fun findPage(request: HttpServletRequest): GenericPage<WarehouseLogVo> {
        val page = GenericPage<WarehouseLogVo>().build(request)
        val q = WarehouseLogs.selectAll()
        q.andWhere { WarehouseLogs.orgId inList diaryService.readOrgIds() }
        request.getParameter("warehouseId")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { WarehouseLogs.warehouseId eq it } }
        request.getParameter("keyword")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { WarehouseLogs.content like "%$it%" } }
        request.getParameter("logDateStart")?.let { parseCollectionDateParam(it) }?.let { q.andWhere { WarehouseLogs.logDate greaterEq it } }
        request.getParameter("logDateEnd")?.let { parseCollectionDateParam(it) }?.let { q.andWhere { WarehouseLogs.logDate lessEq it } }
        q.orderBy(WarehouseLogs.logDate to SortOrder.DESC, WarehouseLogs.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<WarehouseLogDao>)
        page.list = daoPage.data?.map(::listRecord)?.let(::enrich)
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<WarehouseLogVo>
    }

    /** 单份历史原件异常不阻断其他日记；异常行只提供定位信息，详情仍按原规则严格核验。 */
    private fun listRecord(row: WarehouseLogDao): WarehouseLogVo = try {
        diaryService.requireRecordedEvidence(row)
        row.toModel()
    } catch (failure: BadRequestException) {
        WarehouseLogVo(
            id = row.id.value,
            orgId = row.orgId,
            warehouseId = row.warehouseId,
            logDate = row.logDate,
            createBy = row.createBy,
            createTime = row.createTime,
            evidenceIssue = failure.message ?: "日记原件核验失败，请核对原始记录与备份",
        )
    }

    fun save(vo: WarehouseLogVo): String {
        val content=vo.content?.takeIf(String::isNotBlank) ?: throw BadRequestException("入库工作及巡库内容必填")
        val logDate=vo.logDate ?: throw BadRequestException("实际日记日期必填")
        if(logDate.isAfter(LocalDate.now()))throw BadRequestException("不能预填未来库房日记")
        val value=vo.diary ?: throw BadRequestException("请补齐日记的人员进出、实际温湿度、藏品动态、安全及异常处置")
        val warehouseId=vo.warehouseId?.takeIf(String::isNotBlank) ?: throw BadRequestException("请选择实际保管库房")
        val historical=vo.id?.let { id -> WarehouseLogDao.findById(id) ?: throw BadRequestException("库房日记不存在") }
        val initial=WarehouseDao.findById(warehouseId) ?: throw BadRequestException("库房不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(),initial.orgId,"库房日记")
        val warehouse=CollectionLocationMutation.reference(initial.orgId,warehouseId,
            allowRetired=historical?.diary!=null && historical.warehouseId==warehouseId) ?: throw BadRequestException("库房不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(),warehouse.orgId,"库房日记")
        CollectionCurrentOperationAccess.require(scope, warehouse.orgId, true,
            setOf(io.hizhengfu.cloud.collection.common.CollectionConst.STORAGE, io.hizhengfu.cloud.collection.common.CollectionConst.WAREHOUSE_MANAGE), "库房日记")
        val actualEntries=diaryService.entries(warehouseId,logDate)
        if(vo.entries==null || vo.entries!=actualEntries)throw BadRequestException("实际人员进出记录尚未核对或已经变化，请刷新核对后再签署日记")
        if (vo.id == null) {
            // 同馆位置锁已由 reference 取得；只复用完全相同的实际记录，允许同日不同巡库记录。
            WarehouseLogDao.find { (WarehouseLogs.orgId eq warehouse.orgId) and
                (WarehouseLogs.warehouseId eq warehouseId) and (WarehouseLogs.logDate eq logDate) }
                .firstOrNull { it.diary == value && it.content == content && it.attachmentIds == vo.attachmentIds && it.entries == actualEntries }
                ?.let { diaryService.requireRecordedEvidence(it); return it.id.value }
        }
        val existing=vo.id?.let { id -> WarehouseLogDao.find { WarehouseLogs.id eq id }.forUpdate().singleOrNull() ?: throw BadRequestException("库房日记不存在") }
        if(existing!=null) {
            OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(),existing.orgId,"库房日记")
            if(existing.orgId!=warehouse.orgId || existing.warehouseId!=warehouseId || existing.logDate!=logDate)throw BadRequestException("已记录日记不能改换馆、库房或日期")
            val unchanged=existing.diary==value && existing.content==content && existing.attachmentIds==vo.attachmentIds && existing.entries==actualEntries
            if(!unchanged && value.replacementReason.isNullOrBlank())throw BadRequestException("更正日记须说明原因并保留原件")
            originalFiles?.rememberExistingBusiness(WarehouseDiaryService.BIZ,existing.id.value)
            if(unchanged) {
                diaryService.requireRecordedEvidence(existing)
                return existing.id.value
            }
            diaryService.preservePrevious(existing)
        }
        val node=existing ?: dao.new {
            orgId=warehouse.orgId;this.warehouseId=warehouseId;this.logDate=logDate
            this.content=content;this.attachmentIds=vo.attachmentIds
        }
        diaryService.record(node,value,actualEntries,content,vo.attachmentIds)
        BusinessAttachmentBindingPlanner.plan(node.id.value,"库房日记附件",node.attachmentIds).forEach { item ->
            if(originalFiles?.bindRetainedBusinessReference(WarehouseDiaryService.BIZ,node.id.value,node.orgId,item.fileId,item.aliasName)!=true)
                attachmentBinder.bindSingle(node.id.value,item.aliasName,item.fileId)
        }
        originalFiles?.remember(WarehouseDiaryService.BIZ, node.id.value)
        return node.id.value
    }

    fun remove(id: String): String {
        val node=findById(id) ?: throw BadRequestException("库房日记不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(),node.orgId,"库房日记")
        throw BadRequestException("库房日记属于实际保管记录，不能删除；请补充更正并保留原件")
    }

    private fun enrich(items: List<WarehouseLogVo>): List<WarehouseLogVo> {
        if (items.isEmpty()) return items
        val whNames = WarehouseDao.all().associate { it.id.value to (it.orgId to it.name) }
        val names = userResolver.names(items.map { it.createBy })
        items.forEach {
            it.warehouseName = it.warehouseId?.let { w -> whNames[w]?.takeIf { name -> name.first==it.orgId }?.second }
            it.createByName = it.createBy?.let { u -> names[u] }
        }
        return items
    }
}
