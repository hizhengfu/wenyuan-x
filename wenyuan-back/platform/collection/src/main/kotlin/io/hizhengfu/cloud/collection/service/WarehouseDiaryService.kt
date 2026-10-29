package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.isNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Component
@Transactional
class WarehouseDiaryService(
    private val scope: CollectionScopeResolver,
    private val staff: WarehouseStaffingService,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
) {
    companion object { const val BIZ = "COLLECTION_WAREHOUSE_DIARY" }
    internal fun readOrgIds() = CollectionSearchReadAccess.require(scope,
        additionalPermissions=setOf(CollectionConst.STORAGE,CollectionConst.WAREHOUSE_MANAGE),allowHistoricalOrgs=true).orgIds
    fun entries(warehouseId: String, day: LocalDate): WarehouseDiaryEntriesVo {
        val warehouse=WarehouseDao.findById(warehouseId) ?: fail("库房不存在")
        OrgScopedReadPolicy.requireVisible(readOrgIds(),warehouse.orgId,"库房进出记录")
        return WarehouseDiaryEntriesVo(WarehouseEntryApplicationDao.find {
            (WarehouseEntryApplications.orgId eq warehouse.orgId) and
            (WarehouseEntryApplications.actualEntryAt less day.plusDays(1).atStartOfDay()) and
            (WarehouseEntryApplications.actualExitAt.isNull() or (WarehouseEntryApplications.actualExitAt greaterEq day.atStartOfDay()))
        }.filter { warehouseId in splitCollectionIds(it.warehouseIds) }
            .sortedWith(compareBy<WarehouseEntryApplicationDao> { it.actualEntryAt }.thenBy { it.id.value }).map {
            // 接口和JSON原件按毫秒传递；历史数据库的微秒余数不应制造虚假的进出变化。
            WarehouseDiaryVisitVo(it.id.value,it.applicationNo,it.purpose,
                it.entryRecord?.let { record -> record.copy(occurredAt=record.occurredAt.truncatedTo(ChronoUnit.MILLIS)) },
                it.exitRecord?.let { record -> record.copy(occurredAt=record.occurredAt.truncatedTo(ChronoUnit.MILLIS)) },
                it.actualEntryAt!!.truncatedTo(ChronoUnit.MILLIS),it.plannedEnd.truncatedTo(ChronoUnit.MILLIS),
                splitCollectionIds(it.entrantIds).toSet(),it.externalEntrants,it.actualExitAt?.truncatedTo(ChronoUnit.MILLIS))
        })
    }
    fun get(id: String): WarehouseLogVo {
        val e=WarehouseLogDao.findById(id) ?: fail("库房日记不存在")
        OrgScopedReadPolicy.requireVisible(readOrgIds(),e.orgId,"库房日记")
        requireRecordedEvidence(e, verifyContent=false)
        val inspected=archives.readForDisplay(BIZ,id,e.orgId)
        val current=e.warehouseId?.let {
            val warehouse=WarehouseDao.findById(it) ?: fail("日记关联库房不存在，请核对原记录")
            if(warehouse.orgId!=e.orgId)fail("日记关联库房所属馆不一致，请核对原记录")
            entries(it,e.logDate)
        }
        return e.toModel().copy(archives=inspected.archives,currentEntries=current,entriesChanged=e.diary!=null && e.entries!=current,
            evidenceIssue=inspected.issues.takeIf { it.isNotEmpty() }?.joinToString("\n"))
    }
    fun requireArchivedOriginal(id:String,archiveId:String,fileId:String) {
        if (listOf(id,archiveId,fileId).any { it.isBlank() || it.length>100 }) fail("库房日记归档原件标识不正确")
        val e=WarehouseLogDao.findById(id) ?: fail("库房日记不存在")
        OrgScopedReadPolicy.requireVisible(readOrgIds(),e.orgId,"库房日记")
        requireRecordedEvidence(e)
        val archive=archives.archiveForAuthorizedOrg(BIZ,id,e.orgId,archiveId)
        @Suppress("UNCHECKED_CAST")
        val files=archive["files"] as? List<Map<String,Any?>> ?: fail("库房日记归档文件清单无效")
        if (files.none { it["fileId"]==fileId }) fail("该文件不是所选库房日记永久档案的原件")
    }
    fun record(e: WarehouseLogDao, value: WarehouseDiaryVo, reviewedEntries: WarehouseDiaryEntriesVo,
        workContent: String = e.content, attachmentIds: String? = e.attachmentIds) {
        val warehouseId=e.warehouseId ?: fail("每日库房日记须对应实际库房，不能使用整库通用记录替代")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(),e.orgId,"库房日记")
        CollectionLocationMutation.reference(e.orgId,warehouseId,allowRetired=e.diary!=null) ?: fail("库房不存在")
        val previous=requireRecordedEvidence(e)
        // 当前任职只证明新的人员/报告资格；已核实原件中的未改动事实不随离任失效。
        val roster by lazy { staff.baselineForWrite(e.orgId,listOf(warehouseId)).items.single().staffing }
        if(e.diary!=null && e.diary!=value && value.replacementReason.isNullOrBlank())fail("更正日记须说明原因，原始日记和进出快照永久保留")
        if(e.diary?.hasAnomaly==true && value.hasAnomaly!=true)fail("已经发现的异常不能改成未发生，请记录后续处理结果")
        if(value.inspectedAt.toLocalDate()!=e.logDate || value.inspectedAt.isAfter(LocalDateTime.now()))fail("请填写当日实际巡库时间，不得虚填未来时间")
        if(value.inspectorUserIds.isEmpty())fail("请明确实际巡库保管人员")
        value.inspectorUserIds.forEach { id ->
            if(previous?.inspectedAt!=value.inspectedAt || id !in previous.inspectorUserIds)
                staff.requireKeeper(id,e.orgId,listOf(warehouseId))
        }
        if(value.entryRecordsComplete!=true)fail("须核对并完整记录当日出入库人员、时间与入库工作")
        if(value.collectionDynamics.isBlank() || value.securitySituation.isBlank())fail("须记录库内藏品动态和安全情况")
        if(value.readings.isEmpty())fail("须填写实际库房温湿度等环境数据")
        value.readings.forEach { reading ->
            if(reading.measuredAt.toLocalDate()!=e.logDate || reading.measuredAt.isAfter(LocalDateTime.now()))fail("环境数据须来自日志当日实际测量")
            if(reading.temperature==null || reading.humidity==null || !reading.temperature.isFinite() || reading.temperature< -273.15 || !reading.humidity.isFinite() || reading.humidity !in 0.0..100.0)fail("温度或相对湿度数值无效")
            if(reading.sourceDescription.isBlank() || reading.evidenceFileIds.isBlank())fail("须说明实际测量来源并留存记录原件")
        }
        if(value.hasAnomaly==null)fail("请明确说明是否发现异常")
        val visits=entries(warehouseId,e.logDate)
        if(visits!=reviewedEntries)fail("实际人员进出记录已经变化，请刷新核对后再签署日记")
        val sourceExceptions=visits.items.filter { it.entry==null || it.exit?.continuousAccompaniment==false || (it.exitedAt==null && it.plannedEnd.isBefore(LocalDateTime.now())) }
        if(sourceExceptions.isNotEmpty() && value.hasAnomaly!=true)fail("进出记录存在未核实旧原件、陪同中断或超时未离库，须如实记录异常及处理")
        if(value.hasAnomaly==true) {
            if(value.anomalyDescription.isNullOrBlank() || value.handlingMeasures.isNullOrBlank() || value.significanceReason.isNullOrBlank() || value.major==null || value.resolved==null)fail("异常须记录具体情况、已采取措施、重大情况判断及处理进度")
            if(value.keeperUserId.isNullOrBlank() || value.keeperReportedAt==null || value.keeperReportFileIds.isNullOrBlank())fail("异常须实际报告保管部门负责人，并登记报告原件")
            val retainedKeeperReport=previous?.hasAnomaly==true && previous.keeperUserId==value.keeperUserId &&
                previous.keeperReportedAt==value.keeperReportedAt && previous.keeperReportFileIds==value.keeperReportFileIds
            if(!retainedKeeperReport) {
                if(value.keeperUserId!=roster.keeperUserId)fail("新增或更正报告须核对当前保管部门负责人")
                users.requireRole(value.keeperUserId,e.orgId,CollectionConst.ROLE_STORAGE_HEAD)
            }
            val knownAt=listOf(value.inspectedAt)+sourceExceptions.mapNotNull { it.exit?.takeIf { exit -> exit.continuousAccompaniment==false }?.occurredAt }
            if(value.keeperReportedAt.isBefore(knownAt.max()) || value.keeperReportedAt.isAfter(LocalDateTime.now()))fail("请核实发现异常后实际报告保管负责人的时间")
            if(value.major==true) {
                if(value.leaderUserId.isNullOrBlank() || value.leaderReportedAt==null || value.leaderReportFileIds.isNullOrBlank())fail("重大异常须实际报告主要负责人并留存原件")
                val retainedLeaderReport=previous?.hasAnomaly==true && previous.major==true && previous.leaderUserId==value.leaderUserId &&
                    previous.leaderReportedAt==value.leaderReportedAt && previous.leaderReportFileIds==value.leaderReportFileIds
                if(!retainedLeaderReport) {
                    if(value.leaderUserId!=roster.leaderUserId)fail("新增或更正重大报告须核对当前主要负责人")
                    users.requireRole(value.leaderUserId,e.orgId,CollectionConst.ROLE_DIRECTOR)
                }
                if(value.leaderReportedAt.isBefore(value.keeperReportedAt) || value.leaderReportedAt.isAfter(LocalDateTime.now()))fail("请核实重大异常实际逐级报告时间")
            }
            if(value.resolved==false && (value.followUpAt==null || !value.followUpAt.isAfter(value.inspectedAt)))fail("尚未处理完毕须明确后续核查时间")
            if(value.resolved==true && visits.items.any { it.exitedAt==null && it.plannedEnd.isBefore(LocalDateTime.now()) })fail("人员超时仍未登记离库，不能将该日记异常标为已处理完毕")
        }
        if(value.signedDiaryFileIds.isBlank())fail("请上传实际巡库人员签署的库房日记")
        archives.seal(e.orgId,BIZ,e.id.value,"RECORDED_${IdGenerate.nextId()}",
            mapOf("warehouseId" to warehouseId,"logDate" to e.logDate,"workContent" to workContent,"diary" to value,"entries" to visits),files(value,attachmentIds))
        e.content=workContent;e.attachmentIds=attachmentIds;e.diary=value;e.entries=visits
    }
    /** 沿用历史人员事实须以本件最新签署原件为依据，不能由可变业务行自行证明。 */
    @Transactional(readOnly = true, noRollbackFor = [BadRequestException::class])
    internal fun requireRecordedEvidence(e: WarehouseLogDao, verifyContent: Boolean = true): WarehouseDiaryVo? {
        val recorded=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq e.id.value)
        }.filter { it.phase.startsWith("RECORDED_") }.maxWithOrNull(compareBy({ it.createTime },{ it.id.value }))
        val previous=e.diary
        if(previous==null) {
            if(recorded!=null)fail("已签署日记仍有永久原件，当前业务内容不能清除")
            return null
        }
        if(recorded==null)fail("缺少已签署日记原件，请先核实历史记录")
        if(recorded.orgId!=e.orgId || recorded.digest!=CollectionCareEvidence.digest(recorded.snapshot))fail("已签署日记原件摘要或所属馆不一致")
        val actual=try { JsonUtils.mapper.readTree(recorded.snapshot) }
            catch (_: tools.jackson.core.JacksonException) { fail("已签署日记原件无法解析") }
        val expected=JsonUtils.mapper.valueToTree<tools.jackson.databind.JsonNode>(mapOf(
            "warehouseId" to e.warehouseId,"logDate" to e.logDate,"workContent" to e.content,"diary" to previous,"entries" to e.entries))
        if(actual!=expected)fail("当前日记与最新签署原件不一致，请核实原始记录")
        // 已签署版本按原档案只读核验，包括引用和已记录的本地字节或远端版本。
        if(verifyContent) archives.verifyExisting(recorded,e.orgId,BIZ,e.id.value,recorded.phase,actual,files(previous,e.attachmentIds))
        else archives.requireExistingMetadata(recorded,e.orgId,BIZ,e.id.value,recorded.phase,actual,files(previous,e.attachmentIds))
        return previous
    }
    private fun files(value: WarehouseDiaryVo, attachmentIds: String?) =
        (value.readings.map { it.evidenceFileIds }+listOfNotNull(value.signedDiaryFileIds,value.keeperReportFileIds,value.leaderReportFileIds,attachmentIds)).joinToString(",")
    fun preservePrevious(e: WarehouseLogDao) {
        if(e.diary==null)archives.seal(e.orgId,BIZ,e.id.value,"LEGACY",e.toModel(),e.attachmentIds)
    }
    fun gaps(from: LocalDate, to: LocalDate): List<Map<String,Any?>> {
        if(to.isBefore(from) || to.isAfter(LocalDate.now()) || ChronoUnit.DAYS.between(from,to)>366)fail("日记核对范围须为一年以内的实际日期")
        val visible=readOrgIds()
        val warehouses=WarehouseDao.find { (Warehouses.orgId inList visible) and (Warehouses.status eq EnumContentStatus.NORMAL) }.filter { it.sceneType==EnumLocationScene.STORAGE && it.type in setOf(EnumWarehouseType.WAREHOUSE,EnumWarehouseType.AREA) }
        return warehouses.map { w ->
            val recorded=WarehouseLogDao.find {
                (WarehouseLogs.orgId eq w.orgId) and (WarehouseLogs.warehouseId eq w.id.value) and
                    (WarehouseLogs.logDate greaterEq from) and (WarehouseLogs.logDate lessEq to)
            }.mapNotNull { log ->
                requireRecordedEvidence(log)
                log.logDate.takeIf { log.diary!=null }
            }.toSet()
            // 任职调整不是库房启用日期，不能以新任职日期隐藏以前缺少的日记。
            val dates=generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.filter { day -> day !in recorded }.toList()
            mapOf("warehouseId" to w.id.value,"warehouseName" to w.name,"staffingVerified" to (w.staffing!=null),"missingDates" to dates)
        }
    }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
