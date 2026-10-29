package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionPrintTemplateVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.ACQUISITION
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.APPRAISAL
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.COLLECTION_ARCHIVE
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.DEREGISTRATION
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.ENTRY_VOUCHER
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.MOUNTING
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.EXHIBITION
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.INVENTORY
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.LOAN_HANDOVER
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.MOVEMENT
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.REPRODUCTION
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.REQUISITION
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.RESTORATION
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.TRANSFER
import io.hizhengfu.cloud.common.exception.BadRequestException

/** Central registry for formal collection print forms and their Word template metadata. */
object CollectionPrintTemplateRegistry {
    private const val DEFAULT_TEMPLATE_PATH = "templates/collection/print/collection-business-form.docx"

    private val templates = listOf(
        template(COLLECTION_ARCHIVE, 0, "藏品登记表", "藏品登记表", "总登记号", "藏品信息", listOf("登记人", "复核人", "保管员", "负责人", "日期")),
        template(MOVEMENT, 1, "出入库单", "藏品出入库凭证", "出入库单号", "出入库藏品清单", listOf("经办人", "复核人", "保管员", "交接人", "日期")),
        template(REQUISITION, 2, "提用单", "馆藏藏品提用单", "提用单号", "提用藏品清单", listOf("提用部门负责人", "保管部门负责人", "法定代表人或主要负责人", "点交保管人员", "接收提用人员", "点交日期")),
        template(LOAN_HANDOVER, 3, "借用点交单", "馆藏藏品借用出馆交接单", "协议/批文号", "借用藏品清单", listOf("保管部门负责人", "法定代表人或主要负责人", "出借方点交人员一", "出借方点交人员二及其他人员", "借用方点交人员一", "借用方点交人员二及其他人员", "实际点交日期")),
        template(DEREGISTRATION, 4, "退出执行确认及处置记录", "藏品退出执行确认及处置记录", "行政批复或备案文号", "退出藏品清单", listOf("保管部门负责人", "法定代表人或主要负责人", "经办人签字或盖章", "纸质账册号及页码", "日期")),
        template(INVENTORY, 5, "盘点表", "馆藏文物盘点清查记录表", "盘点批次/单号", "盘点藏品清单", listOf("盘点人", "复核人", "保管员", "负责人", "日期")),
        template(APPRAISAL, 6, "馆内鉴定意见表", "馆藏文物馆内鉴定意见表", "鉴定记录号", "鉴定藏品信息", listOf("鉴定委员", "复核人", "保管员", "负责人", "日期")),
        template(TRANSFER, 7, "跨馆转移交接表", "博物馆藏品跨馆转移交接表", "行政文书号", "逐件跨馆转移清单", listOf("源馆保管负责人", "源馆主要负责人或依法授权人员", "接收馆保管负责人", "接收馆主要负责人", "双方实物点交人员", "总账经办人", "双方账册和页码", "实际交接日期")),
        template(RESTORATION, 8, "修复记录表", "馆藏文物保护修复记录表", "修复记录号", "修复藏品信息", listOf("修复人员", "复核人", "保管员", "负责人", "日期")),
        template(ACQUISITION, 9, "征集入藏表", "馆藏文物征集入藏审批登记表", "征集申请号", "入藏藏品信息", listOf("申请人", "评估人", "审核人", "批准人", "日期")),
        template(REPRODUCTION, 10, "复制拓印记录表", "馆藏文物复制拓印影像采集记录表", "复制拓印记录号", "复制拓印藏品信息", listOf("采集人", "复核人", "保管员", "负责人", "日期")),
        template(EXHIBITION, 11, "展览记录表", "馆藏文物展览记录表", "展览编号", "参展藏品清单", listOf("策展人", "保管员", "复核人", "负责人", "日期")),
        template(MOUNTING, 12, "装裱凭证", "书画装裱交接凭证", "装裱凭证号", "装裱藏品清单", listOf("送裱人", "装裱方", "复核人", "保管员", "日期")),
        template(ENTRY_VOUCHER, 13, "入馆凭证", "馆藏文物入馆凭证", "入馆凭证号", "入藏藏品清单", listOf("移交人", "接收人", "保管员", "负责人", "日期")),
    )

    fun list(): List<CollectionPrintTemplateVo> = templates.sortedBy { it.sort }

    fun get(type: EnumCollectionPrintType): CollectionPrintTemplateVo =
        templates.firstOrNull { it.type == type } ?: throw BadRequestException("打印表单模板未配置：$type")

    fun resolve(print: CollectionPrintVo): CollectionPrintTemplateVo {
        val base = get(print.type)
        val title = print.title.takeIf { it.isNotBlank() } ?: base.title
        return base.copy(
            title = title,
            documentLabel = if (print.type == TRANSFER && !print.businessId.isNullOrBlank() &&
                print.documentNo == print.businessId) "案卷编号" else base.documentLabel,
            collectionTitle = if (print.type == MOVEMENT) movementCollectionTitle(title, base.collectionTitle) else base.collectionTitle,
        )
    }

    private fun template(
        type: EnumCollectionPrintType,
        sort: Int,
        name: String,
        title: String,
        documentLabel: String,
        collectionTitle: String,
        signatures: List<String>,
    ): CollectionPrintTemplateVo = CollectionPrintTemplateVo(
        code = type.name,
        type = type,
        name = name,
        title = title,
        documentLabel = documentLabel,
        collectionTitle = collectionTitle,
        templatePath = DEFAULT_TEMPLATE_PATH,
        signatures = signatures,
        sort = sort,
    )

    private fun movementCollectionTitle(title: String, fallback: String): String = when {
        title.contains("入库") -> "入库藏品清单"
        title.contains("出库") -> "出库藏品清单"
        else -> fallback
    }
}
