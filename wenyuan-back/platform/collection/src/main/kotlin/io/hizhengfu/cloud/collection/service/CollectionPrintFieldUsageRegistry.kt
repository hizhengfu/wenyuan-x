package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionPrintFieldUsageVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintTemplateUsageVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType

object CollectionPrintFieldUsageRegistry {
    private fun common(label: String, placeholder: String, example: String, usage: String) =
        CollectionPrintFieldUsageVo("表头字段", label, placeholder, example, usage)

    private fun field(label: String, example: String, usage: String) =
        CollectionPrintFieldUsageVo("业务字段", label, "{{field.$label}}", example, usage)

    private fun item(label: String, key: String, example: String, usage: String) =
        CollectionPrintFieldUsageVo("藏品明细行", label, "{{item.$key}}", example, usage)

    private val commonFields = listOf(
        common("表单标题", "{{title}}", "馆藏文物入库单", "显示当前打印表单标题"),
        common("单号字段名", "{{documentLabel}}", "出入库单号", "显示单号标签"),
        common("单号", "{{documentNo}}", "RK-2026-001", "显示业务单号或总登记号"),
        common("收藏单位", "{{orgName}}", "庐山博物馆", "显示当前业务所属收藏单位"),
        common("生成时间", "{{generatedAt}}", "2026-06-28 09:02:18", "显示打印生成时间"),
    )

    private val itemFields = listOf(
        item("序号", "index", "1", "放在藏品明细表格行中，按行自动递增"),
        item("总登记号", "totalRegNo", "LS-0001", "藏品总登记号"),
        item("名称", "name", "陶鼎", "藏品名称"),
        item("分类号", "classNo", "A-01", "藏品分类号"),
        item("辅助编号", "auxNo", "旧藏-01", "辅助编号或原藏号"),
        item("文物分类", "categoryName", "陶器", "藏品文物分类名称"),
        item("年代", "era", "汉代", "藏品年代"),
        item("质地", "texture", "陶", "藏品质地，自动按字典翻译"),
        item("级别", "level", "一级", "藏品级别，自动按字典翻译"),
        item("数量", "quantity", "1 件", "藏品数量与单位"),
        item("来源", "source", "考古发掘", "藏品来源，自动按字典翻译"),
        item("取得日期", "acquireDate", "2026-06-28", "藏品取得日期"),
        item("完残程度", "completeness", "完整", "完残程度，自动按字典翻译"),
        item("库位", "warehouseName", "一号库房", "藏品库位名称；出入库单显示该次流水的出库前或入库后库位"),
        item("影像/附件", "mediaAttachmentCount", "2 / 1", "影像数量与附件数量"),
        item("备注", "remark", "随葬器物", "藏品备注"),
    )

    private val businessFields: Map<EnumCollectionPrintType, List<CollectionPrintFieldUsageVo>> = mapOf(
        EnumCollectionPrintType.COLLECTION_ARCHIVE to listOf(
            field("总登记号", "LS-0001", "藏品总登记号"),
            field("分类号", "A-01", "藏品分类号"),
            field("辅助编号", "旧藏-01", "辅助编号或原藏号"),
            field("名称", "陶鼎", "藏品名称"),
            field("文物分类", "陶器", "文物分类名称"),
            field("年代", "汉代", "藏品年代"),
            field("质地", "陶", "藏品质地"),
            field("级别", "一级", "藏品级别"),
            field("数量", "1 件", "藏品数量与单位"),
            field("来源", "考古发掘", "藏品来源"),
            field("取得日期", "2026-06-28", "取得日期"),
            field("尺寸", "通高：20cm；口径：12cm", "藏品尺寸汇总"),
            field("铭文款识", "内壁铭文三字", "铭文款识"),
            field("特征描述", "侈口，圆腹，三足", "藏品特征描述"),
            field("当前库位", "一号库房", "当前库房库位"),
        ),
        EnumCollectionPrintType.MOVEMENT to listOf(
            field("出入库类型", "入库", "入库或出库"),
            field("事由", "展览", "出入库事由"),
            field("依据", "展览批文", "出入库依据"),
            field("部门", "保管部", "经办部门"),
            field("原库位", "一号库房", "出库前库位"),
            field("目标库位", "展厅", "入库或出库目标位置"),
            field("经办人", "张三", "经办人"),
            field("复核人", "李四", "复核人"),
            field("保管员", "王五", "保管员"),
            field("交接人", "赵六", "交接人"),
            field("审核/验收意见", "同意", "审核或验收意见"),
            field("藏品明细数据来源", "登记号、名称取流水；其他属性按本馆主档或调拨冻结档案标示", "说明历史身份快照、当前主档与调拨永久档案的资料来源边界"),
            field("本单藏品数", "7", "同单号下的藏品数量"),
            field("操作时间", "2026-06-28 09:02:18", "出入库操作时间"),
        ),
        EnumCollectionPrintType.REQUISITION to listOf(
            field("提用事由", "研究", "提用事由"),
            field("提用依据", "课题申请", "提用依据"),
            field("提用部门", "研究部", "提用部门"),
            field("经办人", "张三", "经办人"),
            field("复核人", "李四", "复核人"),
            field("保管员", "王五", "保管员"),
            field("交接人", "赵六", "交接人"),
            field("预计归还日期", "2026-07-10", "预计归还日期"),
            field("实际归还日期", "2026-07-09", "实际归还日期"),
            field("是否归还", "已归还", "归还状态"),
            field("藏品明细数据来源", "已转馆藏品取本单流水和原馆留存账", "说明旧流水身份缺失及随后转馆快照的参考边界"),
        ),
        EnumCollectionPrintType.LOAN_HANDOVER to listOf(
            field("展览/事由", "青铜器特展", "借展项目或事由"),
            field("借入方", "某某博物馆", "借入单位"),
            field("协议号", "XY-2026-001", "协议编号"),
            field("审批层级", "省级", "审批层级"),
            field("出馆批文号", "PW-2026-001", "出馆批文号"),
            field("保险", "已投保", "保险信息"),
            field("借出日期", "2026-06-28", "借出日期"),
            field("预计/实际返还日期", "2026-09-28", "返还日期"),
            field("返还状态", "未归还", "是否返还"),
            field("藏品明细数据来源", "已转馆藏品取本单流水和原馆留存账", "说明旧流水身份缺失及随后转馆快照的参考边界"),
        ),
        EnumCollectionPrintType.DEREGISTRATION to listOf(
            field("退出馆藏原因", "损毁", "退出馆藏原因"),
            field("审批层级", "馆级", "审批层级"),
            field("批文号", "ZX-2026-001", "退出馆藏批文号"),
            field("退出馆藏说明", "自然损毁", "退出馆藏说明"),
            field("流程状态", "通过", "流程审批状态"),
        ),
        EnumCollectionPrintType.INVENTORY to listOf(
            field("盘点批次", "PD-2026-001", "盘点批次"),
            field("盘点结果", "账物相符", "单件盘点结果"),
            field("备注", "状态良好", "盘点备注"),
            field("盘点时间", "2026-06-28 09:02:18", "盘点时间"),
            field("本批盘点数", "120", "同批次盘点总数"),
            field("账物相符", "118", "同批次账物相符数量"),
            field("盘盈", "1", "同批次盘盈数量"),
            field("盘亏", "1", "同批次盘亏数量"),
            field("状态异常", "0", "同批次状态异常数量"),
            field("藏品明细数据来源", "已转馆藏品取原馆留存账", "说明随后转馆快照不等于盘点当时属性"),
        ),
        EnumCollectionPrintType.APPRAISAL to listOf(
            field("鉴定定名", "青铜鼎", "鉴定后的名称"),
            field("断代", "商代", "鉴定断代"),
            field("质地", "铜", "鉴定质地"),
            field("定级", "一级", "鉴定级别"),
            field("鉴定委员会", "馆藏鉴定委员会", "鉴定组织"),
            field("鉴定结论", "建议定为一级文物", "鉴定结论"),
            field("定案时间", "2026-06-28 09:02:18", "定案时间"),
        ),
        EnumCollectionPrintType.TRANSFER to listOf(
            field("源收藏单位", "主馆", "调出收藏单位"),
            field("目标收藏单位", "分馆", "调入收藏单位"),
            field("调拨事由", "馆际调拨", "调拨事由"),
            field("批文号", "DB-2026-001", "调拨批文号"),
            field("流程状态", "通过", "流程审批状态"),
        ),
        EnumCollectionPrintType.RESTORATION to listOf(
            field("藏品", "LS-0001", "修复藏品"),
            field("修复机构", "文保中心", "修复机构"),
            field("经办人", "张三", "经办人"),
            field("复核人", "李四", "复核人"),
            field("修复方案", "清洁加固", "修复方案"),
            field("修复过程", "表面清理，局部加固", "修复过程"),
            field("开始日期", "2026-06-01", "修复开始日期"),
            field("竣工日期", "2026-06-28", "修复竣工日期"),
            field("修复状态", "已完成", "修复状态"),
        ),
        EnumCollectionPrintType.ACQUISITION to listOf(
            field("拟征集名称", "测试征集", "拟征集藏品名称"),
            field("文物分类", "陶器", "文物分类"),
            field("年代", "民国", "年代"),
            field("质地", "瓷", "质地"),
            field("拟定级别", "一级", "拟定级别"),
            field("征集方式", "收购", "征集方式"),
            field("数量", "1 件", "数量与单位"),
            field("持有人/卖方", "某某", "持有人或卖方"),
            field("拟征集价（元）", "50", "拟征集价格，单位为元"),
            field("评估价（元）", "50", "评估价格，单位为元"),
            field("征集理由", "补充馆藏序列", "征集理由"),
            field("登账藏品", "陶鼎", "征集通过后关联的登账藏品"),
            field("流程状态", "通过", "流程审批状态"),
        ),
        EnumCollectionPrintType.REPRODUCTION to listOf(
            field("藏品", "LS-0001", "复制拓印藏品"),
            field("复制类型", "影像采集", "复制拓印类型"),
            field("用途", "展陈宣传", "用途"),
            field("采集日期", "2026-06-28", "采集日期"),
        ),
        EnumCollectionPrintType.EXHIBITION to listOf(
            field("展览名称", "青铜器特展", "展览名称"),
            field("展览编号", "ZL-2026-001", "展览编号"),
            field("地点", "第一展厅", "展览地点"),
            field("主办/承办", "庐山博物馆", "主办或承办单位"),
            field("开展日期", "2026-06-28", "开展日期"),
            field("结束日期", "2026-08-28", "结束日期"),
            field("状态", "进行中", "展览状态"),
            field("备注", "重点展览", "备注"),
        ),
    )

    fun usage(type: EnumCollectionPrintType): CollectionPrintTemplateUsageVo =
        CollectionPrintTemplateUsageVo(type, commonFields + businessFields[type].orEmpty() + itemFields)
}
