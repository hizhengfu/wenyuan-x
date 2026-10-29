package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class DonationRecognitionService(private val scope: CollectionScopeResolver, private val archives: CollectionPermanentArchiveService) {
    private val prefix="DONOR_RECOGNITION_"
    private fun access(id:String,write:Boolean):AcquisitionDao {
        val entity=AcquisitionDao.findById(id) ?: throw BadRequestException("征集申请不存在")
        CollectionCurrentOperationAccess.require(scope,entity.orgId,write,setOf(CollectionConst.ACQUIRE),"捐赠鼓励记录")
        if(entity.source!=EnumSource.DONATE)throw BadRequestException("仅捐赠取得的藏品可登记捐赠鼓励")
        return entity
    }
    private fun rows(entity:AcquisitionDao)=CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_ACQUISITION) and (CollectionPermanentArchives.bizId eq entity.id.value)
    }.filter { it.phase.startsWith(prefix) }.sortedBy { it.createTime }.onEach {
        if(it.orgId!=entity.orgId || it.digest!=CollectionCareEvidence.digest(it.snapshot))throw BadRequestException("鼓励档案归属或摘要异常，请核查原件")
    }
    private fun content(row:CollectionPermanentArchiveDao)=JsonUtils.fromJson(row.snapshot,DonationRecognitionVo::class.java) ?: throw BadRequestException("鼓励档案内容无法解析")
    fun list(id:String):List<Map<String,Any?>> {
        val entity=access(id,false)
        val org=entity.orgId
        val result=rows(entity).map { row ->
            val original=archives.phaseForDisplay(CollectionConst.BIZ_ACQUISITION,id,org,row.phase)
                ?: throw BadRequestException("捐赠鼓励永久原件显示信息缺失")
            if(original["id"]!=row.id.value)throw BadRequestException("捐赠鼓励原件与记录不一致")
            mapOf("id" to row.id.value,"recordedAt" to row.createTime,"recordedBy" to row.createBy,"content" to content(row),"files" to original["files"],"evidenceIssue" to original["evidenceIssue"])
        }
        if(access(id,false).orgId!=org)throw BadRequestException("捐赠鼓励所属收藏单位已变化，请重新读取")
        return result
    }
    fun record(id:String,vo:DonationRecognitionVo):String {
        access(id,true)
        val entity=AcquisitionDao.find { Acquisitions.id eq id }.forUpdate().singleOrNull() ?: throw BadRequestException("征集申请不存在")
        access(id,true)
        val completion=AcquisitionComplianceCaseDao.find { AcquisitionComplianceCases.acquisitionId eq id }.singleOrNull()
            ?.takeIf { it.orgId==entity.orgId && it.vetoOn==null }?.completion ?: throw BadRequestException("请先完成捐赠实际取得记录")
        DonationRecognitionPolicy.requireValid(vo,completion.actualAcquiredOn)
        val files=listOfNotNull(vo.evidenceFileIds,vo.approvalFileIds,vo.receiptFileIds).joinToString(",")
        val existing=rows(entity)
        val phase=prefix+vo.requestId
        existing.singleOrNull { it.phase==phase }?.let {
            archives.verifyExisting(it,entity.orgId,CollectionConst.BIZ_ACQUISITION,id,phase,vo,files)
            return it.id.value
        }
        vo.correctsArchiveId?.let { old ->
            if(existing.none { it.id.value==old })throw BadRequestException("被更正记录不属于本捐赠申请")
            if(existing.any { content(it).correctsArchiveId==old })throw BadRequestException("该记录已有更正，请基于最新更正继续办理")
        }
        return archives.seal(entity.orgId,CollectionConst.BIZ_ACQUISITION,id,phase,vo,files)
    }
}
