package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.LoanVo
import io.hizhengfu.cloud.collection.domain.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 新规借用凭证使用实际方案和逐件原件，不把旧的整单摘要当作点交记录。 */
internal object LoanCompliancePrintFields {
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun field(label: String, value: Any?) = CollectionPrintFieldVo(
        label = label,
        value = when (value) {
            null -> null
            is Boolean -> if (value) "是" else "否"
            is LocalDateTime -> value.format(timeFormat)
            is Collection<*> -> value.joinToString("、")
            else -> value.toString()
        },
    )

    fun build(loan: LoanVo): List<CollectionPrintFieldVo> = buildList {
        val d = requireNotNull(loan.dossier)
        fun add(label: String, value: Any?) { add(field(label, value)) }
        fun file(label: String, value: String?) { add(CollectionPrintFieldBuilder.attachment(label, value)) }
        fun itemName(id: String): String {
            val name = loan.baseline?.items?.find { it.collectionId == id }?.content?.formalName
            return if (name.isNullOrBlank()) id else "$name（$id）"
        }
        add("展览/事由", loan.title)
        add("借入方", loan.borrower)
        add("借用主体类型", when (d.borrowerKind) {
            EnumLoanBorrowerKind.STATE_MUSEUM -> "国有博物馆"
            EnumLoanBorrowerKind.NON_STATE_MUSEUM -> "非国有博物馆"
            EnumLoanBorrowerKind.OTHER_ORGANIZATION -> "其他单位"
            EnumLoanBorrowerKind.OVERSEAS_ORGANIZATION -> "境外机构"
        })
        add("单位登记编号", d.borrowerRegistrationNo)
        file("单位性质及登记依据", d.borrowerIdentityFileIds)
        add("协议号", loan.protocolNo)
        add("保险", loan.insurance)
        add("具体使用目的", d.purposeDescription)
        add("协议约定出借时间", d.agreedOutAt)
        add("协议约定归还时间", d.agreedReturnAt)
        loan.exhibitionEnds.forEachIndexed {index,row ->
            val v=(row["content"] as LoanExhibitionEndRecordVo).request;val label="展览结束${index+1}"
            add("$label：实际日期",v.actualEndedOn);file("$label：事实依据", v.evidenceFileIds);add("$label：原记录",v.previousId);add("$label：更正原因",v.correctionReason)
        }
        loan.overseasFilings.forEachIndexed {index,row ->
            val record=row["content"] as LoanOverseasFilingRecordVo;val v=record.request;val label="出境报送${index+1}"
            add("$label：事项",if(v.kind==EnumOverseasFilingKind.AGREEMENT)"正式协议备案" else "展览结项报送")
            add("$label：原报送记录",v.previousId);add("$label：补充更正原因",v.correctionReason)
            add("$label：接收机关",v.authorityName);add("$label：报送编号",v.submissionNo);add("$label：实际报送日期",v.submittedOn)
            add("$label：报送期限",record.dueOn);add("$label：曾逾期报送",record.late);file("$label：接收凭据", v.submissionReceiptFileIds)
            add("$label：关联结束记录",v.endingRecordId);add("$label：实际展览结束日期",v.actualExhibitionEndedOn);file("$label：结束依据", v.endingEvidenceFileIds)
            file("$label：结项备案表", v.conclusionFormFileIds);file("$label：结项报告", v.conclusionReportFileIds);file("$label：展览音像资料", v.audiovisualFileIds)
        }
        loan.overseasPorts.forEachIndexed {index,row ->
            val v=(row["content"] as LoanPortClearanceRecordVo).request;val label="口岸手续${index+1}"
            add("$label：类型",if(v.stage==EnumLoanPortStage.EXIT)"出境放行" else "复进境结项")
            add("$label：原批准记录",v.originalApprovalId);add("$label：原出境记录",v.exitRecordId)
            add("$label：审核机构",v.reviewAgency);add("$label：实际审核时间",v.reviewedAt)
            add("$label：审核证书号",v.certificateNo);file("$label：审核证书原件", v.certificateFileIds)
            v.identifiers.forEach {(id,mark)->add("$label：藏品$id 登记标识",mark)}
            add("$label：口岸",v.portName);file("$label：指定口岸依据", v.designatedPortFileIds)
            add("$label：海关手续时间",v.customsAt);add("$label：海关单证号",v.customsDocumentNo);file("$label：海关原件", v.customsFileIds)
        }
        loan.overseasExtensions.forEachIndexed { index,row ->
            val extension=row["content"] as LoanOverseasExtensionRecordVo
            val v=extension.request;val label="出境延期${index+1}"
            add("$label：原展期截止",extension.previousExhibitionEndsOn)
            add("$label：原约定归还时间",extension.previousReturnAt)
            add("$label：原行政批准记录",v.originalApprovalId)
            add("$label：特殊需要",v.specialNeed)
            add("$label：原审批机关",v.authorityName)
            add("$label：延期文号",v.documentNo)
            add("$label：实际申请日期",v.submittedOn)
            add("$label：实际批准日期",v.approvedOn)
            add("$label：批准晚于原展期",extension.approvalLate)
            add("$label：批准后展期截止",v.exhibitionEndsOn)
            add("$label：补充协议归还时间",v.agreedReturnAt)
            add("$label：补充协议签署日期",v.agreementSignedOn)
            file("$label：申请原件", v.submissionFileIds)
            file("$label：批准原件", v.approvalFileIds)
            file("$label：协议原件", v.agreementFileIds)
        }
        add("实际集体研究同意时间", d.collectiveDecisionAt)
        add("实际参会人员", d.collectiveParticipantNames)
        file("集体研究同意原件", d.collectiveDecisionFileIds)
        add("实际协议签署时间", d.agreementSignedAt)
        file("双方签署借用协议", d.signedAgreementFileIds)
        file("双方确认的完整清单", d.agreedCollectionListFileIds)
        add("安全责任约定", d.safetyResponsibilities)
        add("运输包装要求", d.transportRequirements)
        add("适用包装运输标准及版本", d.transportStandardReference)
        add("标准适用范围及核对依据", d.transportStandardApplicability)
        file("包装运输方案与标准核对原件", d.transportPlanFileIds)
        add("借用保管要求", d.storageRequirements)
        add("实际使用及存放地点", d.useSite)
        add("承担运输的馆或机构", d.carrierName)
        file("运输资信和能力依据", d.carrierCapabilityFileIds)
        add("专职运输监督人员及单位", "${d.transportSupervisorName} / ${d.transportSupervisorOrganization}")
        file("专职运输监督指派原件", d.transportAssignmentFileIds)
        add("保管部门负责人", d.keeperUserId)
        add("法定代表人或主要负责人", d.leaderUserId)
        add("本馆在借监管保管员", d.supervisionUserId)
        add("首次双方检查日期", d.firstInspectionOn)
        add("馆方确定的检查间隔天数", d.inspectionIntervalDays)
        file("技术指导及监督检查计划", d.inspectionPlanFileIds)
        add("实际出库已登记", loan.outExecuted)
        add("实际出库时间", loan.outExecutedTime)
        add("全部归还已登记", loan.returned)
        add("经办人", loan.operator)
        add("一级文物复核人", loan.reviewer)
        file("其他依据和附件", loan.attachmentIds)
        d.assessments.forEach { a ->
            val name = itemName(a.collectionId)
            add("$name：法定管理类别", when (a.objectKind) {
                EnumLoanObjectKind.RELIC -> "文物藏品"
                EnumLoanObjectKind.SCIENTIFIC_FOSSIL -> "科学价值古脊椎动物或古人类化石"
                EnumLoanObjectKind.OTHER -> "其他藏品"
            })
            add("$name：出借评估现状", a.condition)
            add("$name：保存状况不佳", a.poorCondition)
            add("$name：运输包装易损", a.transportDamageRisk)
            add("$name：借用必要性", a.necessity)
            add("$name：风险评估", a.riskAssessment)
            file("$name：评估签署原件", a.assessmentFileIds)
        }
        loan.administrativeEvidence.forEachIndexed { index, record ->
            val a = record["content"] as? LoanAdministrativeVo ?: return@forEachIndexed
            val title = "行政原件${index + 1}"
            add("$title：机关及文号", "${a.authorityName} / ${a.documentNo}")
            add("$title：机关层级", when (a.level) {
                EnumAuthorityLevel.COMPETENT -> "主管文物行政部门"
                EnumAuthorityLevel.PROVINCE -> "省级文物行政部门"
                EnumAuthorityLevel.NATIONAL -> "国务院文物行政部门"
                EnumAuthorityLevel.STATE_COUNCIL -> "国务院"
            })
            add("$title：文书性质", if (a.action == EnumAdministrativeAction.APPROVAL) "批准" else "备案")
            add("$title：实际报送日期", a.submittedOn)
            add("$title：实际批准或备案确认日期", a.issuedOn)
            add("$title：涉及藏品", a.collectionIds.map(::itemName))
            file("$title：报送接收凭据", a.submissionFileIds)
            file("$title：实际文书文件", a.fileIds)
        }
        loan.handover?.let { record ->
            val h = record.content
            add("保管负责人审查指派时间", h.reviewedAt)
            add("审查并指派人员的负责人", h.reviewerUserId)
            file("审查及指派原件", h.reviewAndAssignmentFileIds)
            add("实际点交时间", h.handedAt)
            add("实际点交保管人员", h.custodianUserIds)
            add("借用方实际点交人员", h.receivingPersonNames)
            file("借用方人员身份授权依据", h.receivingAuthorizationFileIds)
            add("已核实双方人员不同", h.distinctPartiesConfirmed)
            add("安全防护措施已落实", h.safetyMeasuresConfirmed)
            add("场所具备安全条件", h.siteSafetyConfirmed)
            file("安全条件核实原件", h.safetyCheckFileIds)
            add("实际采用包装运输标准及版本", h.appliedTransportStandardReference)
            add("实际包装已按方案标准核对", h.packingStandardConfirmed)
            file("逐件包装运输核验原件", h.packingCheckFileIds)
            file("双方签署逐件点交单", h.signedHandoverFileIds)
            add("点交原件补充或更正原因", h.replacementReason)
            add("点交登记人员及时间", "${record.recordedBy} / ${record.recordedAt.format(timeFormat)}")
            h.items.forEach { item ->
                val name = itemName(item.collectionId)
                add("$name：点交保存现状", item.condition)
                add("$name：文字需影像补充", item.textNeedsImages)
                file("$name：点交影像", item.imageFileIds)
            }
        }
        if (d.purpose == EnumLoanPurpose.EXHIBITION) add("展览实物是否出境", d.exhibitionOverseas)
        d.overseasExhibition?.let { overseas ->
            add("出境草签协议日期", overseas.draftAgreementSignedOn)
            file("出境草签协议草案原件", overseas.draftAgreementFileIds)
            add("出境展览开始日期", overseas.exhibitionStartsOn)
            add("初次批准展览结束日期", overseas.exhibitionEndsOn)
            add("所有参展馆文物展品总数", overseas.totalRelicExhibits)
            add("全展一级文物件套数", overseas.firstLevelExhibits)
            file("所有参展馆完整展品目录", overseas.wholeExhibitionListFileIds)
            file("禁止出境目录核查依据", overseas.prohibitedCatalogCheckFileIds)
            overseas.objects.forEach { item ->
                val name = itemName(item.collectionId)
                add("$name：一级文物孤品或易损品", item.uniqueOrFragileFirstLevel)
                add("$name：列入禁止出境范围", item.prohibitedFromExit)
                file("$name：曾在国内正式展出依据", item.domesticExhibitionEvidenceFileIds)
            }
        }
        file("境外机构借用适用程序依据", d.overseasProcedureFileIds)
        file("其他用途适用法律依据", d.otherLegalBasisFileIds)
    }
}
