package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst as C
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

/** WW/T 0018 的出入库关联信息，按原出库业务及每批实际归还列示，未归还不填写验收事实。 */
internal object CollectionCirculationPrintFields {
    fun build(seed: MovementVo, archives: CollectionPermanentArchiveService?): List<CollectionPrintFieldVo> {
        if(seed.reason in setOf(EnumMoveReason.TRANSFER,EnumMoveReason.EMERGENCY)) return emptyList()
        val org=seed.orgId ?: return emptyList()
        val reference=seed.refBizId ?: return emptyList()
        val received=if(seed.type==EnumMoveType.IN) CollectionReturnDao.findById(reference) else null
        if(received!=null && received.orgId!=org) throw BadRequestException("归还流水与原归还单所属馆不一致")
        val source=received?.sourceBizId ?: reference
        val req=RequisitionDao.findById(source)
        val loan=LoanDao.findById(source)
        if(req!=null && loan!=null) throw BadRequestException("出库业务标识不唯一，请核查")
        val biz=when { req!=null -> C.BIZ_REQUISITION; loan!=null -> C.BIZ_LOAN; else -> return emptyList() }
        if((req?.orgId ?: loan?.orgId)!=org || received?.let { it.sourceBizType!=biz }==true)
            throw BadRequestException("出入库流水与来源业务不一致")
        val service=archives ?: throw BadRequestException("出入库关联单原件核验服务未配置")
        val original=service.phaseForAuthorizedOrg(biz,source,org,"OUT_EXECUTED")
            ?: return listOf(CollectionPrintFieldVo("出入库关联核查","旧出库未保留完整执行原件，不能生成前后现状对照；请核对签署原件"))
        val root=JsonUtils.mapper.readTree(original["snapshot"] as String).path("request")
        if(root.path("id").asString()!=source || root.path("orgId").asString()!=org || !root.path("outExecuted").asBoolean())
            throw BadRequestException("出库归档身份或实际执行标记不一致")
        val baseline:List<RequisitionCollectionBaselineVo>
        val outItems:List<RequisitionConditionVo>
        val outTime:java.time.LocalDateTime
        val outSigned:String
        val custodians:Set<String>
        val receivers:Set<String>
        if(biz==C.BIZ_REQUISITION) {
            val value=JsonUtils.mapper.treeToValue(root,RequisitionVo::class.java)
            val handover=value.handover?.content ?: throw BadRequestException("出库永久档案缺少实际点交")
            baseline=value.baseline?.items.orEmpty();outItems=handover.items;outTime=handover.handedAt;outSigned=handover.signedHandoverFileIds
            custodians=handover.custodianUserIds;receivers=handover.receivingUserIds
        } else {
            val value=JsonUtils.mapper.treeToValue(root,LoanVo::class.java)
            val handover=value.handover?.content ?: throw BadRequestException("借用出库永久档案缺少实际点交")
            baseline=value.baseline?.items.orEmpty();outItems=handover.items;outTime=handover.handedAt;outSigned=handover.signedHandoverFileIds
            custodians=handover.custodianUserIds;receivers=handover.receivingPersonNames
        }
        val ids=splitCollectionIds(root.path("collectionIds").asString()).toSet()
        if(seed.collectionId !in ids || ids!=outItems.map {it.collectionId}.toSet() || ids!=baseline.map {it.collectionId}.toSet())
            throw BadRequestException("出库清单、逐件现状与原批准基线不一致")
        val fields=mutableListOf(CollectionPrintFieldVo("关联原出库单",root.path(if(biz==C.BIZ_LOAN) "protocolNo" else "documentNo").asString()),
            CollectionPrintFieldVo("实际出库时间",outTime.toString()),CollectionPrintFieldVo("出库保管点交人员",custodians.joinToString("、")),
            CollectionPrintFieldVo("出库接收点交人员",receivers.joinToString("、")),
            CollectionPrintFieldVo("签署说明","人员记录与签署原件分别列示；本打印件的空白签字栏不代表已经签署"))
        fields += CollectionPrintFieldBuilder.attachment("出库双方签署原件",outSigned)
        outItems.forEachIndexed { index,item ->
            val content=baseline.single {it.collectionId==item.collectionId}.content
            val prefix="出库藏品 ${index+1}（${content.formalName ?: item.collectionId}）"
            fields += CollectionPrintFieldVo("$prefix 数量","登记：${content.quantity ?: "待核实"} ${content.unit.orEmpty()}；实际：${if(content.actualQuantityUnit=="套") "待核实" else content.actualQuantity ?: "待核实"} ${content.actualQuantityUnit?.takeIf { it!="套" }.orEmpty()}")
            fields += CollectionPrintFieldVo("$prefix 出库前现状",item.condition)
            fields += CollectionPrintFieldBuilder.attachments("$prefix 出库前照片" to item.imageFileIds)
        }
        val returns=CollectionReturnDao.find { (CollectionReturns.orgId eq org) and (CollectionReturns.sourceBizType eq biz) and (CollectionReturns.sourceBizId eq source) }
            .filter { it.executionState==EnumReturnExecutionState.EXECUTED }.sortedWith(compareBy({it.executedAt},{it.id.value}))
        val physicallyReceived=mutableSetOf<String>()
        for((index,row) in returns.withIndex()) {
            val prefix="归还第${index+1}批（${row.id.value}）"
            if(row.inspection==null) { fields += CollectionPrintFieldVo("$prefix 历史核查","旧记录缺少逐件验收原件，不能推定归还数量与现状一致");continue }
            val originalReturn=ReturnResponsibilityEvidence.received(row)
            service.verifyExisting(originalReturn,org,C.BIZ_RETURN,row.id.value,"RECEIVED",JsonUtils.mapper.readTree(originalReturn.snapshot),returnFiles(row))
            val inspection=row.inspection!!
            val sourceSnapshot=row.sourceSnapshot ?: throw BadRequestException("归还未留存原出库对照")
            if(sourceSnapshot.outAt!=outTime || sourceSnapshot.outSignedFileIds!=outSigned ||
                sourceSnapshot.items.any { s -> baseline.singleOrNull { it.collectionId==s.collectionId }?.content!=s.content || outItems.singleOrNull {it.collectionId==s.collectionId}!=s.outboundCondition })
                throw BadRequestException("归还对照与原出库冻结事实不一致")
            val incoming=inspection.items.map {it.collectionId}.toSet()
            if(incoming.size!=inspection.items.size || !ids.containsAll(incoming) || incoming.any {it in physicallyReceived})
                throw BadRequestException("各批归还藏品重复或超出原出库范围")
            physicallyReceived+=incoming
            fields += CollectionPrintFieldVo("$prefix 接收时间",inspection.inspectedAt.toString())
            fields += CollectionPrintFieldVo("$prefix 交回人员",(inspection.returningUserIds+inspection.returningPersonNames).joinToString("、"))
            fields += CollectionPrintFieldVo("$prefix 接收保管人员",inspection.custodianUserIds.joinToString("、"))
            fields += CollectionPrintFieldBuilder.attachment("$prefix 双方签署原件",inspection.signedHandoverFileIds)
            inspection.items.forEach { item ->
                val before=outItems.single {it.collectionId==item.collectionId}
                fields += CollectionPrintFieldVo("$prefix 藏品 ${item.collectionId} 前后对照","出库前：${before.condition}；归还：${item.condition}；真实性：${if(item.authenticityMatches) "一致" else "待核查"}（${item.authenticityMethod}）；现状：${if(item.conditionMatches) "一致" else "有差异"}；接收时${if(item.abnormal()) "待核查保管" else "验收一致"}")
                fields += CollectionPrintFieldBuilder.attachments("$prefix 藏品 ${item.collectionId} 归还照片" to item.imageFileIds)
                if(item.abnormal()) fields += CollectionPrintFieldVo("$prefix 藏品 ${item.collectionId} 异常和防护","${item.anomalyDescription.orEmpty()}；${item.protectiveMeasures.orEmpty()}")
            }
            service.listForAuthorizedOrgs(C.BIZ_RETURN,row.id.value,setOf(org)).filter { (it["phase"] as? String)?.let { p->p.startsWith("REPORT_") || p.startsWith("RESOLUTION_") }==true }.forEach { a ->
                val phase=a["phase"] as String
                if(phase.startsWith("REPORT_")) {
                    val report=JsonUtils.fromJson(a["snapshot"] as String,ReturnReportVo::class.java)!!
                    fields += CollectionPrintFieldVo("$prefix 异常报告","${report.collectionId}；${report.authorityName}；${report.reportNo}；${report.reportedAt}；${report.description}")
                    fields += CollectionPrintFieldBuilder.attachment("$prefix 异常报告原件",report.fileIds)
                } else {
                    val resolution=JsonUtils.fromJson(a["snapshot"] as String,ReturnResolutionVo::class.java)!!
                    fields += CollectionPrintFieldVo("$prefix 异常处理","${resolution.collectionId}；${resolution.completedAt}；原因：${resolution.cause}；最终现状：${resolution.finalCondition}")
                    fields += CollectionPrintFieldBuilder.attachments("$prefix 保管方书面说明" to resolution.custodianExplanationFileIds,"$prefix 交回方书面说明" to resolution.returningExplanationFileIds,"$prefix 调查原件" to resolution.investigationFileIds,"$prefix 处理签署原件" to resolution.signedApprovalFileIds)
                }
            }
        }
        fields += CollectionPrintFieldVo("逐批归还汇总","原出库${ids.size}条；有逐件接收原件${physicallyReceived.size}条；尚未取得逐件接收原件${(ids-physicallyReceived).size}条。异常处理另按本单所列原件核实，实收不等于验收通过。")
        return fields
    }
}
