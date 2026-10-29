package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class AnnualReportEvidenceService(
    private val scope: CollectionScopeResolver, private val statistics: CollectionLedgerStatisticsService,
    private val users: CollectionResponsibleUserService, private val archives: CollectionPermanentArchiveService,
) {
    companion object { const val BIZ="COLLECTION_ANNUAL_REPORT" }
    fun detail(year: Int): Map<String,Any?> {
        val org=readOrg()
        val old=AnnualReportArchiveDao.find { (AnnualReportArchives.orgId eq org) and (AnnualReportArchives.year eq year) }.singleOrNull()
        return mapOf("orgId" to org,"year" to year,"legacyArchive" to old?.toModel(),"records" to records(org,year).map { row ->
            mapOf("id" to row.id.value,"version" to row.version,"signed" to row.signed,"calculated" to row.calculated,"submission" to row.submission,"filing" to row.filing,"archives" to verifiedArchives(row,year,org,verifyContent=false))
        })
    }
    /** 检索与详情使用相同的逐版冻结原件校验，不让篡改的阶段或编号进入搜索结果。 */
    fun verifiedRecordIds(year: Int): Set<String> {
        val org=readOrg()
        return records(org,year).map { row ->
            verifiedArchives(row,year,org,verifyContent=false)
            row.id.value
        }.toSet()
    }
    /** 只允许读取当前馆年度报表指定版本、指定阶段档案中的冻结文件。 */
    fun requireArchivedOriginal(year:Int,id:String,archiveId:String,fileId:String) {
        val org=readOrg()
        if (!id.matches(Regex("[0-9]{15,20}")) || !archiveId.matches(Regex("[0-9]{15,20}")) || fileId.isBlank() || fileId.length>100)
            fail("年度报表归档原件标识不正确")
        val row=AnnualReportEvidenceDao.findById(id) ?: fail("年度报表原件记录不存在")
        if (row.orgId!=org || row.year!=year) fail("年度报表原件不属于当前收藏单位及年度")
        val archive=archives.listForAuthorizedOrg(BIZ,id,org).singleOrNull { it["id"]==archiveId }
            ?: fail("该永久档案不属于本版年度报表")
        @Suppress("UNCHECKED_CAST")
        val files=archive["files"] as? List<Map<String,Any?>> ?: fail("年度报表归档文件清单无效")
        if (files.none { it["fileId"]==fileId }) fail("该文件不是所选年度报表永久档案的原件")
    }
    fun signed(value: AnnualReportSignedVo, expectedOrgId: String): String {
        val org=requireConfirmedOrg(expectedOrgId)
        CollectionOrgDao.find { CollectionOrgs.id eq org }.forUpdate().singleOrNull() ?: fail("收藏单位不存在")
        requireConfirmedOrg(org) // 等待馆锁后复核原窗口馆别，重复签署也不能绕过。
        val previous=records(org,value.year).lastOrNull()
        if(previous!=null)verifiedArchives(previous,previous.year,org)
        if(previous?.signed==value)return previous.id.value
        val calculated=statistics.preview(value.year,org)
        if(!calculated.completeYear)fail("本年度尚未结束，请先使用阶段预览；不能预先登记完整年度签署报表")
        if(value.signedAt.isBefore(LocalDateTime.of(value.year+1,1,1,0,0)) || value.signedAt.isAfter(LocalDateTime.now()))fail("完整年度报表须在年度结束后实际签署，不能填写未来时间")
        if(value.signedReportFileIds.isBlank())fail("请保留完整的真实签署报表原件")
        listOf(value.statisticianUserId,value.keeperUserId,value.leaderUserId).forEach { users.requireMuseumMember(it,org) }
        if(setOf(value.statisticianUserId,value.keeperUserId,value.leaderUserId).size!=3)fail("请分别记录实际制表人、保管负责人和主要负责人")
        users.requireRole(value.keeperUserId,org,CollectionConst.ROLE_STORAGE_HEAD);users.requireRole(value.leaderUserId,org,CollectionConst.ROLE_DIRECTOR)
        validateFigures(value.figures)
        if(needsReconciliation(value.figures,calculated) && (value.reconciliationNote.isNullOrBlank() || value.reconciliationFileIds.isNullOrBlank()))fail("原报表与系统历史计算有差异或缺口，须附纸质总账、逐件明细及核对说明原件；不能用当前状态填补历史")
        if(previous!=null && (value.replacementReason.isNullOrBlank() || value.replacementFileIds.isNullOrBlank() || value.signedAt.isBefore(previous.signed.signedAt)))fail("补报或订正须另建版本，说明原因并保留本次签署订正原件及真实时间")
        val row=AnnualReportEvidenceDao.new { orgId=org;year=value.year;version=(previous?.version ?: 0)+1;signed=value;this.calculated=calculated }
        archives.seal(org,BIZ,row.id.value,"SIGNED",mapOf("signed" to value,"calculated" to calculated),signedFiles(value))
        return row.id.value
    }
    fun submission(id: String,value: AnnualReportSubmissionVo): String {
        val row=record(id)
        verifiedArchives(row,row.year,row.orgId)
        if(row.submission!=null){if(row.submission==value)return id;fail("实际报送原件不能覆盖，请另建补报或订正版")}
        if(listOf(value.authorityName,value.reportNo,value.submissionFileIds).any(String::isBlank))fail("请填写主管文物行政部门、实际报送编号及报送凭据")
        if(value.submittedAt.isBefore(row.signed.signedAt) || value.submittedAt.isAfter(LocalDateTime.now()))fail("报送须在实际签署之后，不得填写未来时间")
        row.submission=value;archives.seal(row.orgId,BIZ,id,"SUBMITTED",value,value.submissionFileIds)
        return id
    }
    fun filing(id: String,value: AnnualReportFilingVo): String {
        val row=record(id)
        verifiedArchives(row,row.year,row.orgId)
        if(row.filing!=null){if(row.filing==value)return id;fail("备案回执不能覆盖，请另建订正记录")}
        val sent=row.submission ?: fail("请先登记真实报送凭据")
        if(value.authorityName!=sent.authorityName || value.filingNo.isBlank() || value.receiptFileIds.isBlank())fail("请核实同一主管文物行政部门的备案编号及实际回执")
        if(value.filedAt.isBefore(sent.submittedAt) || value.filedAt.isAfter(LocalDateTime.now()))fail("备案回执时间须在报送之后且不得填写未来时间")
        row.filing=value;archives.seal(row.orgId,BIZ,id,"FILED",value,value.receiptFileIds)
        return id
    }
    /** 展示状态和继续办理均只能依据与业务字段一致的各阶段冻结原件。 */
    private fun verifiedArchives(row: AnnualReportEvidenceDao, year: Int, org: String,verifyContent:Boolean=true): List<Map<String,Any?>> {
        if(row.year!=year || row.orgId!=org || row.signed.year!=year || row.calculated.year!=year || row.calculated.orgId!=org)
            fail("年度报送与留档计算的年度或所属馆不一致，请核对原件")
        if(row.filing!=null && row.submission==null)fail("备案回执缺少对应的实际报送凭据")
        val expected=linkedMapOf<String,Pair<Any,String>>(
            "SIGNED" to (mapOf("signed" to row.signed,"calculated" to row.calculated) to signedFiles(row.signed)),
        )
        row.submission?.let { expected["SUBMITTED"]=it to it.submissionFileIds }
        row.filing?.let { expected["FILED"]=it to it.receiptFileIds }
        val listed=if(verifyContent) archives.listForAuthorizedOrg(BIZ,row.id.value,row.orgId) else archives.readForDisplay(BIZ,row.id.value,row.orgId).archives
        if(listed.size!=expected.size || listed.map { it["phase"] }.toSet()!=expected.keys)
            fail("年度报表业务阶段与永久原件不一致，请核对原档案")
        listed.forEach { item ->
            val phase=item["phase"] as? String ?: fail("年度报表原件阶段无效")
            val archive=CollectionPermanentArchiveDao.findById(item["id"] as? String ?: fail("年度报表原件标识无效"))
                ?: fail("年度报表原件不存在")
            val (snapshot,files)=expected.getValue(phase)
            archives.verifyExisting(archive,row.orgId,BIZ,row.id.value,phase,snapshot,files,verifyContent=verifyContent)
        }
        return listed
    }
    private fun record(id: String): AnnualReportEvidenceDao {
        val row=AnnualReportEvidenceDao.find { AnnualReportEvidenceRecords.id eq id }.forUpdate().singleOrNull() ?: fail("年度报表原件记录不存在")
        requireWrite(row.orgId);return row
    }
    private fun requireWrite(orgId: String) = CollectionCurrentOperationAccess.require(
        scope, orgId, true, setOf(CollectionConst.STAT), "年度报送原件")
    /** 确认原窗口的馆别，并以当前人员关系/后台设置核对，不能使用旧登录主机构替换。 */
    private fun requireConfirmedOrg(expectedOrgId: String): String {
        if(expectedOrgId.isBlank())fail("请重新读取年度报表，确认本次签署的收藏单位")
        val current=requireWrite(expectedOrgId)
        if(scope.currentOrgId(current)!=expectedOrgId)fail("当前收藏单位已变化，请重新读取年度报表后签署")
        return expectedOrgId
    }
    private fun readOrg()=CollectionSearchReadAccess.require(scope, additionalPermissions=setOf(CollectionConst.STAT), allowHistoricalOrgs=true).currentOrgId(scope)
    private fun records(org: String,year: Int)=AnnualReportEvidenceDao.find { (AnnualReportEvidenceRecords.orgId eq org) and (AnnualReportEvidenceRecords.year eq year) }.sortedBy { it.version }
    private fun signedFiles(value: AnnualReportSignedVo)=listOfNotNull(value.signedReportFileIds,value.reconciliationFileIds,value.replacementFileIds).joinToString(",")
    private fun validateFigures(v: AnnualDeclaredFiguresVo) {
        if(listOf(v.openingTotal,v.newTotal,v.transferInTotal,v.transferOutTotal,v.deregTotal,v.closingTotal).any { it<0 } || v.openingTotal.toLong()+v.newTotal+v.transferInTotal-v.transferOutTotal-v.deregTotal!=v.closingTotal.toLong())fail("正式登记条目数须非负且期初加新增、调入减退出、调出等于期末")
        if(v.changesDescription.isBlank())fail("请按原报表说明本年藏品增减和信息变更情况")
        if(v.quantities.map { it.unit.trim() }.distinct().size!=v.quantities.size)fail("各计量单位只能列一次，件和套须分别统计")
        v.quantities.forEach { q ->
            val balance=q.opening.toBigInteger()+q.added.toBigInteger()+q.transferIn.toBigInteger()-q.transferOut.toBigInteger()-q.deregistered.toBigInteger()+q.adjustment.toBigInteger()
            if(q.unit.isBlank() || q.unit!=q.unit.trim() || q.unit.length>100 || listOf(q.opening,q.added,q.transferIn,q.transferOut,q.deregistered,q.closing).any { it<0 } || balance!=q.closing.toBigInteger())fail("请核实各单位的实际数量和增减平衡，不能将件与套直接相加")
        }
        // Each formal entry contains at least one physical unit. This is a lower-bound
        // validation only; the report still displays each unit separately.
        val counts=listOf(v.openingTotal,v.newTotal,v.transferInTotal,v.transferOutTotal,v.deregTotal,v.closingTotal)
        val quantities=v.quantities.map { listOf(it.opening,it.added,it.transferIn,it.transferOut,it.deregistered,it.closing) }
        if(counts.withIndex().any { (index,count)->quantities.sumOf { it[index].toBigInteger() }<count.toBigInteger() })fail("正式登记条目须有对应实际数量，请核实各计量单位的期初、增减和期末数量")
    }
    private fun needsReconciliation(v: AnnualDeclaredFiguresVo,c: AnnualLedgerPreviewVo)=c.issues.isNotEmpty() ||
        listOf(v.openingTotal,v.newTotal,v.transferInTotal,v.transferOutTotal,v.deregTotal,v.closingTotal)!=listOf(c.openingTotal,c.newTotal,c.transferInTotal,c.transferOutTotal,c.deregTotal,c.closingTotal) || v.quantities.map { it.balance() }.sortedBy { it.unit }!=c.quantityBalances.sortedBy { it.unit }
    private fun fail(message: String): Nothing=throw BadRequestException(message)
}
