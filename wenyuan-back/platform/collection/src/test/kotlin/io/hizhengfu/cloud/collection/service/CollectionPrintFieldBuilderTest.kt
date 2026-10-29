package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionRegistrationDetailsVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionEraMeaning
import io.hizhengfu.cloud.collection.domain.EnumCollectionEraNotation
import io.hizhengfu.cloud.collection.dao.AppraisalVo
import io.hizhengfu.cloud.collection.dao.AcquisitionVo
import io.hizhengfu.cloud.collection.dao.CollectionTransferVo
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.dao.LoanVo
import io.hizhengfu.cloud.collection.dao.DeregistrationVo
import io.hizhengfu.cloud.collection.dao.ExhibitionVo
import io.hizhengfu.cloud.collection.dao.InventoryVo
import io.hizhengfu.cloud.collection.dao.MovementVo
import io.hizhengfu.cloud.collection.dao.RequisitionVo
import io.hizhengfu.cloud.collection.dao.ReproductionVo
// RestorationVo 已迁移至 module-collection-restoration，本文件相关用例一并移除
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.domain.EnumApproveLevel
import io.hizhengfu.cloud.collection.domain.EnumDeregReason
import io.hizhengfu.cloud.collection.domain.EnumInventoryResult
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.collection.domain.EnumProcessState
import io.hizhengfu.cloud.collection.domain.EnumRepType
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo
import io.hizhengfu.cloud.collection.domain.CollectionInstitutionSuccessionVo
import io.hizhengfu.cloud.collection.domain.CollectionTransferDossierVo
import io.hizhengfu.cloud.collection.domain.CollectionTransferExecutionVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionNature
import io.hizhengfu.cloud.collection.domain.EnumCollectionTransferKind
import io.hizhengfu.cloud.collection.domain.EnumInstitutionSuccession
import io.hizhengfu.cloud.collection.domain.EnumOwnership
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CollectionPrintFieldBuilderTest {

    @Test
    fun `collection archive fields include complete ledger reference fields`() {
        val fields = CollectionPrintFieldBuilder.collectionArchiveFields(
            CollectionVo(
                originalName = "近仿宋缠枝印花牡丹碗",
                era = "中华民国（1912-1949年）",
                registrationDetails = CollectionRegistrationDetailsVo(eraMeaning=EnumCollectionEraMeaning.MANUFACTURED, eraNotation=EnumCollectionEraNotation.CHINESE_HISTORY),
                textureCategory1 = "单一质地",
                textureCategory2 = "无机质/非金属材料",
                weightRange = "0.01-1 kg",
                weight = "0.17",
                weightUnit = "kg",
                entryVoucherNo = "RJ-20260924-0001",
                deregVoucherNo = "TC-20260924-0001",
                registeredAt = LocalDateTime.of(2026, 9, 24, 9, 30),
                acquireDateRange = "1949.10.1-1965",
                acquireYear = "2009",
                author = "佚名",
                edition = "民国版",
                volumeNo = "第一卷",
            ),
        ).associate { it.label to it.value }

        assertEquals("近仿宋缠枝印花牡丹碗", fields["原名"])
        assertEquals("中华民国（1912-1949年）", fields["具体年代"])
        assertEquals("制造年代", fields["年代类型"])
        assertEquals("中国历史学年代", fields["年代表示方式"])
        assertEquals("单一质地", fields["质地类别"])
        assertEquals("无机质/非金属材料", fields["质地细类"])
        assertEquals("0.01-1 kg", fields["质量范围"])
        assertEquals("0.170 kg（附件质量口径待核实）", fields["具体质量"])
        assertEquals("kg", fields["质量单位"])
        assertEquals("RJ-20260924-0001", fields["入馆凭证号"])
        assertEquals("TC-20260924-0001", fields["退出凭证号"])
        assertEquals("2026-09-24 09:30:00", fields["登记时间"])
        assertEquals("1949.10.1-1965", fields["取得时间范围"])
        assertEquals("2009", fields["取得年度"])
        assertEquals("佚名", fields["著者"])
        assertEquals("民国版", fields["版本"])
        assertEquals("第一卷", fields["存卷"])
    }

    @Test
    fun `dictionary print fields carry backend dict types`() {
        val acquisitionFields = CollectionPrintFieldBuilder.acquisitionFields(
            AcquisitionVo(
                proposedLevel = EnumRelicLevel.FIRST,
                source = io.hizhengfu.cloud.collection.domain.EnumSource.PURCHASE,
                flowStatus = io.hizhengfu.cloud.common.domain.EnumFlowStatus.PASS,
            ),
        ).associateBy { it.label }
        val movementFields = CollectionPrintFieldBuilder.movementFields(
            MovementVo(type = EnumMoveType.OUT, reason = EnumMoveReason.RESTORE),
        ).associateBy { it.label }
        val requisitionFields = CollectionPrintFieldBuilder.requisitionFields(
            RequisitionVo(returned = true),
        ).associateBy { it.label }

        assertEquals(CollectionConst.DICT_RELIC_LEVEL, acquisitionFields["拟定级别"]?.dictType)
        assertEquals(CollectionConst.DICT_SOURCE, acquisitionFields["征集方式"]?.dictType)
        assertEquals("flow_biz_status", acquisitionFields["流程状态"]?.dictType)
        assertEquals(CollectionConst.DICT_MOVE_TYPE, movementFields["出入库类型"]?.dictType)
        assertEquals(CollectionConst.DICT_MOVE_REASON, movementFields["事由"]?.dictType)
        assertEquals(CollectionConst.DICT_RETURNED, requisitionFields["是否归还"]?.dictType)
        assertEquals("true", requisitionFields["是否归还"]?.value)
    }

    @Test
    fun `acquisition fields include evidence price registration and flow state`() {
        val fields = CollectionPrintFieldBuilder.acquisitionFields(
            AcquisitionVo(
                name = "青铜鼎",
                categoryId = "cat-bronze",
                era = "商代",
                texture = "铜",
                proposedLevel = EnumRelicLevel.FIRST,
                source = io.hizhengfu.cloud.collection.domain.EnumSource.PURCHASE,
                quantity = 1,
                unit = "件",
                holder = "张三",
                proposedPrice = 1200000,
                appraisedValue = 1500000,
                reason = "具有重要历史价值，拟征集入藏",
                images = "report-1,image-2",
                collectionId = "collection-1",
                flowStatus = io.hizhengfu.cloud.common.domain.EnumFlowStatus.PASS,
            ),
        )

        val byLabel = fields.associate { it.label to it.value }
        assertEquals("青铜鼎", byLabel["拟征集名称"])
        assertEquals("cat-bronze", byLabel["文物分类"])
        assertEquals("商代", byLabel["年代"])
        assertEquals("铜", byLabel["质地"])
        assertEquals("FIRST", byLabel["拟定级别"])
        assertEquals("PURCHASE", byLabel["征集方式"])
        assertEquals("1 件", byLabel["数量"])
        assertEquals("张三", byLabel["持有人/卖方"])
        assertEquals("12000", byLabel["拟征集价（元）"])
        assertEquals("15000", byLabel["评估价（元）"])
        assertEquals("具有重要历史价值，拟征集入藏", byLabel["征集理由"])
        assertEquals("report-1,image-2", byLabel["证据/影像"])
        assertEquals("collection-1", byLabel["登账藏品"])
        assertEquals("PASS", byLabel["流程状态"])
    }

    @Test
    fun `transfer fields include source target approval document reason and attachments`() {
        val fields = CollectionPrintFieldBuilder.transferFields(
            CollectionTransferVo(
                fromOrgId = "org-source",
                toOrgId = "org-target",
                reason = "馆际调拨用于专题展陈",
                approvalDocNo = "调拨批〔2026〕8号",
                attachmentIds = "transfer-approval-1,handover-2",
                flowStatus = io.hizhengfu.cloud.common.domain.EnumFlowStatus.PASS,
            ),
        )

        val byLabel = fields.associate { it.label to it.value }
        assertEquals("org-source", byLabel["源收藏单位"])
        assertEquals("org-target", byLabel["目标收藏单位"])
        assertEquals("馆际调拨用于专题展陈", byLabel["调拨事由"])
        assertEquals("调拨批〔2026〕8号", byLabel["批文号"])
        assertEquals("transfer-approval-1,handover-2", byLabel["附件"])
        assertEquals("PASS", byLabel["流程状态"])
    }

    @Test
    fun `old executed other collection print identifies missing transport material without irrelevant designation fields`() {
        val succession = CollectionInstitutionSuccessionVo(
            eventKind = EnumInstitutionSuccession.MERGER,
            sourceMuseumName = "源馆", sourceRegistrationNo = "SOURCE", eventDocumentNo = "MERGER-1",
            decidedOn = LocalDate.of(2026, 9, 20), effectiveOn = LocalDate.of(2026, 9, 20),
            eventFileIds = "event", nonprofitLegalBasis = "合成依据", legalBasisFileIds = "basis",
            liquidationOrMergerPlanFileIds = "plan", inventoryFileIds = "inventory",
            ownership = EnumOwnership.STATE, movable = true, propertyBasisFileIds = "property",
            publicBenefitPurpose = "公益收藏", noPrivateDistribution = true,
            thirdPartyRightsConclusion = "已处理", thirdPartyRightsFileIds = "rights",
            thirdPartyRightsResolved = true, authorizedRepresentativeUserId = "leader",
            representativeAuthorizationFileIds = "authorization",
        )
        val dossier = CollectionTransferDossierVo(
            kind = EnumCollectionTransferKind.INSTITUTION_SUCCESSION,
            sourceKeeperUserId = "source-keeper", sourceLeaderUserId = "leader",
            targetKeeperUserId = "target-keeper", targetLeaderUserId = "target-leader",
            targetWarehouseId = "warehouse", archiveHandoverFileIds = "archive",
            sourcePaperLedgerRef = "source-ledger", targetPaperLedgerRef = "target-ledger",
            succession = succession,
        )
        val execution = CollectionTransferExecutionVo(
            handedOn = LocalDate.of(2026, 9, 21), sourceCustodianUserId = "source-custodian",
            targetCustodianUserId = "target-custodian", signedHandoverFileIds = "handover",
            conditionDescription = "状况已核对", conditionImageFileIds = "image",
            authenticityVerified = true, signedLedgerChangeFileIds = "ledger-change",
            receivingLedgerReceiptFileIds = "receipt",
        )
        val old = CollectionTransferVo(
            baseline = CollectionCatalogContentVo(nature = EnumCollectionNature.OTHER),
            dossier = dossier, execution = execution, executedAt = LocalDateTime.of(2026, 9, 21, 10, 0),
        )
        val oldFields = CollectionPrintFieldBuilder.transferFields(old).associate { it.label to it.value }
        val attachments = CollectionPrintFieldBuilder.transferFields(old).associateBy { it.label }
        assertEquals(listOf("basis"), attachments["非营利处置法律原件"]?.fileReferences)
        assertEquals(listOf("inventory"), attachments["完整清点清册原件"]?.fileReferences)
        assertEquals(listOf("rights"), attachments["第三方权利处理原件"]?.fileReferences)
        assertEquals(listOf("image"), attachments["点交状况影像"]?.fileReferences)
        assertEquals("本件不属于国家所有可移动文物，省级文物指定决定字段不适用", oldFields["省级指定适用性"])
        assertTrue("省级指定文号" !in oldFields)
        assertTrue(oldFields["包装运输材料状态"]?.contains("待核实") == true)

        val complete = old.copy(
            baseline = CollectionCatalogContentVo(nature = EnumCollectionNature.RELIC),
            dossier = dossier.copy(transportStandardReference = "标准-版本", transportStandardApplicability = "适用依据",
                transportCarrierName = "承运馆", transportCarrierCapabilityFileIds = "capability", transportPlanFileIds = "plan"),
            execution = execution.copy(appliedTransportStandardReference = "标准-版本",
                packingStandardConfirmed = true, packingCheckFileIds = "packing"),
        )
        val completeFields = CollectionPrintFieldBuilder.transferFields(complete).associate { it.label to it.value }
        assertTrue("省级指定文号" in completeFields)
        assertTrue("省级指定适用性" !in completeFields)
        assertTrue("包装运输材料状态" !in completeFields)

        val agreementFields = CollectionPrintFieldBuilder.transferFields(old.copy(dossier = dossier.copy(
            exchangeAgreementFileIds = "exchange", donationAgreementFileIds = "donation", donorNoticeFileIds = "notice",
            donorWrittenConsentFileIds = "consent", provincialJurisdictionFileIds = "jurisdiction", legalBasisFileIds = "legal",
        ))).associateBy { it.label }
        for ((label, id) in listOf("交换协议原件" to "exchange", "捐赠协议原件" to "donation", "捐赠人通知原件" to "notice",
            "捐赠人书面同意原件" to "consent", "省级管辖依据" to "jurisdiction", "转移法律依据" to "legal")) {
            assertEquals(listOf(id), agreementFields[label]?.fileReferences)
            assertEquals(id, agreementFields[label]?.value)
        }
    }

    @Test fun `statutory exit retains explicitly marked ordinary attachments without guessing person ids`() {
        val fields = CollectionPrintFieldBuilder.deregistrationFields(DeregistrationVo(
            dossier = io.hizhengfu.cloud.collection.domain.DeregistrationDossierVo(keeperUserId = "2100000000000000000"),
            attachmentIds = "exit-one,exit-two,exit-one",
        )).associateBy { it.label }
        assertEquals(listOf("exit-one", "exit-two"), fields["其他退出原件"]?.fileReferences)
        assertEquals("exit-one,exit-two,exit-one", fields["其他退出原件"]?.value)
        assertTrue(fields["保管部门负责人"]!!.fileReferences.isEmpty())
        assertTrue(CollectionPrintFieldBuilder.deregistrationFields(DeregistrationVo(
            dossier = io.hizhengfu.cloud.collection.domain.DeregistrationDossierVo(),
        )).none { it.label == "其他退出原件" })
        assertTrue(CollectionPrintFieldBuilder.attachments("空可选附件" to null, "空白" to "  ").isEmpty())
    }

    @Test
    fun `appraisal fields include conclusion committee level finalized time and attachments`() {
        val fields = CollectionPrintFieldBuilder.appraisalFields(
            AppraisalVo(
                resultName = "青釉莲瓣纹瓷碗",
                resultEra = "北宋",
                resultTexture = "瓷",
                resultLevel = EnumRelicLevel.FIRST,
                committee = "张三、李四、王五",
                conclusion = "专家组一致认定为一级文物",
                rejectReason = "材料不完整",
                attachmentIds = "opinion-1,meeting-2",
                appraisedAt = LocalDateTime.of(2026, 6, 24, 14, 30),
            ),
        )

        val byLabel = fields.associate { it.label to it.value }
        assertEquals("青釉莲瓣纹瓷碗", byLabel["鉴定定名"])
        assertEquals("北宋", byLabel["断代"])
        assertEquals("瓷", byLabel["质地"])
        assertEquals("FIRST", byLabel["建议级别"])
        assertEquals("张三、李四、王五", byLabel["鉴定委员会"])
        assertEquals("专家组一致认定为一级文物", byLabel["鉴定结论"])
        assertEquals("材料不完整", byLabel["驳回原因"])
        assertEquals("opinion-1,meeting-2", byLabel["附件"])
        assertEquals("2026-06-24 14:30:00", byLabel["馆内鉴定完成时间"])
    }

    // 修复字段用例已迁移到 module-collection-restoration

    @Test
    fun `reproduction fields include type purpose images date and collection`() {
        val fields = CollectionPrintFieldBuilder.reproductionFields(
            ReproductionVo(
                collectionId = "collection-1",
                repType = EnumRepType.RUBBING,
                purpose = "碑刻拓片用于专题展陈",
                images = "rubbing-1,rubbing-2",
                repDate = LocalDate.of(2026, 6, 24),
            ),
        )

        val byLabel = fields.associate { it.label to it.value }
        assertEquals("collection-1", byLabel["藏品"])
        assertEquals("RUBBING", byLabel["复制类型"])
        assertEquals("碑刻拓片用于专题展陈", byLabel["用途"])
        assertEquals("rubbing-1,rubbing-2", byLabel["产物影像"])
        assertEquals("2026-06-24", byLabel["采集日期"])
    }

    @Test
    fun `movement fields include handover audit fields and attachments`() {
        val fields = CollectionPrintFieldBuilder.movementFields(
            MovementVo(
                type = EnumMoveType.OUT,
                reason = EnumMoveReason.RESTORE,
                basis = "修复审批单 XF-1",
                department = "保管部",
                fromWarehouseId = "wh-a",
                toWarehouseId = "wh-b",
                operator = "operator-1",
                reviewer = "reviewer-1",
                custodian = "custodian-1",
                handoverUser = "handover-1",
                reviewOpinion = "点交无误",
                attachmentIds = "move-file-1",
                operatedAt = LocalDateTime.of(2026, 6, 24, 10, 30),
            ),
        )

        val byLabel = fields.associate { it.label to it.value }
        assertEquals("OUT", byLabel["出入库类型"])
        assertEquals("RESTORE", byLabel["事由"])
        assertEquals("修复审批单 XF-1", byLabel["依据"])
        assertEquals("move-file-1", byLabel["附件"])
        assertTrue(fields.map { it.label }.containsAll(listOf("经办人", "复核人", "保管员", "交接人", "操作时间")))
    }

    @Test
    fun `requisition fields include return state and attachments`() {
        val fields = CollectionPrintFieldBuilder.requisitionFields(
            RequisitionVo(
                purpose = EnumMoveReason.RESEARCH,
                basis = "研究课题 RK-1",
                department = "研究部",
                operator = "operator-1",
                reviewer = "reviewer-1",
                custodian = "custodian-1",
                handoverUser = "handover-1",
                reviewOpinion = "按期归还",
                attachmentIds = "req-file-1,req-file-2",
                expectReturnDate = LocalDate.of(2026, 7, 1),
                returnDate = LocalDate.of(2026, 7, 2),
                returned = true,
            ),
        )

        val byLabel = fields.associate { it.label to it.value }
        assertEquals("RESEARCH", byLabel["提用事由"])
        assertEquals("研究课题 RK-1", byLabel["提用依据"])
        assertEquals("req-file-1,req-file-2", byLabel["附件"])
        assertEquals("2026-07-01", byLabel["预计归还日期"])
        assertEquals("2026-07-02", byLabel["实际归还日期"])
        assertEquals("true", byLabel["是否归还"])
    }

    @Test
    fun `inventory fields include batch result note checked time and attachments`() {
        val fields = CollectionPrintFieldBuilder.inventoryFields(
            InventoryVo(
                batchName = "2026年度库房清查",
                result = EnumInventoryResult.ABNORMAL,
                note = "囊匣标签需更换",
                attachmentIds = "inventory-photo-1,report-1",
                checkedAt = LocalDateTime.of(2026, 6, 24, 11, 0),
            ),
        )

        val byLabel = fields.associate { it.label to it.value }
        assertEquals("2026年度库房清查", byLabel["盘点批次"])
        assertEquals("ABNORMAL", byLabel["盘点结果"])
        assertEquals("囊匣标签需更换", byLabel["备注"])
        assertEquals("inventory-photo-1,report-1", byLabel["附件"])
        assertEquals("2026-06-24 11:00:00", byLabel["盘点时间"])
    }

    @Test
    fun `exhibition fields include project details dates image attachments and state`() {
        val fields = CollectionPrintFieldBuilder.exhibitionFields(
            ExhibitionVo(
                title = "瓷器精品展",
                exhibitionNo = "EX-2026-01",
                venue = "临展一厅",
                organizer = "陈列展览部",
                coverImage = "cover-1",
                attachmentIds = "plan-1,approval-2",
                startDate = LocalDate.of(2026, 7, 1),
                endDate = LocalDate.of(2026, 10, 8),
                state = EnumProcessState.DONE,
                remark = "恒温恒湿展柜，展前完成点交",
            ),
        )

        val byLabel = fields.associate { it.label to it.value }
        val dictByLabel = fields.associate { it.label to it.dictType }
        assertEquals("瓷器精品展", byLabel["展览名称"])
        assertEquals("EX-2026-01", byLabel["展览编号"])
        assertEquals("临展一厅", byLabel["地点"])
        assertEquals("陈列展览部", byLabel["主办/承办"])
        assertEquals("2026-07-01", byLabel["开展日期"])
        assertEquals("2026-10-08", byLabel["结束日期"])
        assertEquals("cover-1", byLabel["封面图"])
        assertEquals("plan-1,approval-2", byLabel["附件"])
        assertEquals("DONE", byLabel["状态"])
        assertEquals(CollectionConst.DICT_PROCESS_STATE, dictByLabel["状态"])
        assertEquals("恒温恒湿展柜，展前完成点交", byLabel["备注"])
    }

    @Test
    fun `loan handover fields include approval document insurance dates and attachments`() {
        val fields = CollectionPrintFieldBuilder.loanHandoverFields(
            LoanVo(
                title = "宋元瓷器外展",
                borrower = "市博物馆",
                protocolNo = "XY-2026-01",
                insurance = "保单 BX-2026",
                approveLevel = EnumApproveLevel.PROVINCE,
                approvalDocNo = "文物出馆批〔2026〕1号",
                operator = "operator-1",
                reviewer = "reviewer-1",
                loanDate = LocalDate.of(2026, 6, 1),
                returnDate = LocalDate.of(2026, 9, 1),
                attachmentIds = "file-a,file-b",
            ),
        )

        val byLabel = fields.associate { it.label to it.value }
        val dictByLabel = fields.associate { it.label to it.dictType }
        assertEquals("市博物馆", byLabel["借入方"])
        assertEquals("PROVINCE", byLabel["审批层级"])
        assertEquals(CollectionConst.DICT_APPROVE_LEVEL, dictByLabel["审批层级"])
        assertEquals("文物出馆批〔2026〕1号", byLabel["出馆批文号"])
        assertEquals("保单 BX-2026", byLabel["保险"])
        assertEquals("2026-06-01", byLabel["借出日期"])
        assertEquals("2026-09-01", byLabel["预计/实际返还日期"])
        assertEquals("operator-1", byLabel["经办人"])
        assertEquals("reviewer-1", byLabel["复核人"])
        assertEquals("file-a,file-b", byLabel["附件"])
        assertTrue(fields.map { it.label }.containsAll(listOf("展览/事由", "协议号", "返还状态", "经办人", "复核人")))
    }

    @Test
    fun `deregistration fields include approval level document detail and attachments`() {
        val fields = CollectionPrintFieldBuilder.deregistrationFields(
            DeregistrationVo(
                reason = EnumDeregReason.DAMAGE,
                detail = "严重残损，经专家论证不再具备收藏价值",
                approveLevel = EnumApproveLevel.PROVINCE,
                approvalDocNo = "文物注销批〔2026〕5号",
                expertOpinion = "经专家评议，已无修复价值",
                approvedBy = "分管馆长",
                destination = "移交主管部门",
                attachmentIds = "expert-1,approval-2",
            ),
        )

        val byLabel = fields.associate { it.label to it.value }
        assertEquals("DAMAGE", byLabel["退出馆藏原因"])
        assertEquals("PROVINCE", byLabel["审批层级"])
        assertEquals("文物注销批〔2026〕5号", byLabel["批文号"])
        assertEquals("严重残损，经专家论证不再具备收藏价值", byLabel["退出馆藏说明"])
        assertEquals("expert-1,approval-2", byLabel["附件"])
        assertEquals("经专家评议，已无修复价值", byLabel["专家评委会意见"])
        assertEquals("移交主管部门", byLabel["退出馆藏后去向"])
    }
}
