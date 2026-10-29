package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.*

/** 所有内置业务单据与编目卡共享的著录附表；来源须由调用方先确定为现账或发生时快照。 */
object CollectionRegistrationPrintFields {
    private fun field(label: String, value: Any?, dict: String? = null) = CollectionPrintFieldVo(label, value?.toString()?.takeIf(String::isNotBlank) ?: "未记录", dict)
    private fun joined(vararg values: Any?): String? = values.filterNotNull().map { it.toString() }.filter(String::isNotBlank).joinToString("；").ifBlank { null }
    private fun quantity(value: Int?, unit: String?): String = if(value == null || unit.isNullOrBlank()) "待核实" else "$value $unit"
    fun dimensions(content: CollectionCatalogContentVo): String {
        val measured = CollectionMeasurementPolicy.dimensions(content.dimensions).joinToString("；") { (label, value) ->
        "$label：${CollectionMeasurementPolicy.lengthText(value, content.dimensionUnit) ?: joined(value, content.dimensionUnit, "数值或单位待核实")}" }
        .ifBlank { content.registrationDetails?.dimensionUnknownReason?.let { "未测量：$it" } ?: "待核实" }
        val description = content.dimensions?.extra?.get("description")?.takeIf(String::isNotBlank)?.let { "补充说明：$it" }
        return joined(measured, description)!!
    }
    fun mass(content: CollectionCatalogContentVo): String = CollectionMeasurementPolicy.massText(content.weight, content.weightUnit)
        ?.let { "$it（${if(content.registrationDetails?.weightExcludesAccessories == true) "不含附件" else "附件质量口径待核实"}）" }
        ?: if(content.weight.isNullOrBlank()) content.registrationDetails?.weightUnknownReason?.let { "未称重：$it" } ?: "待核实"
        else joined(content.weight, content.weightUnit, "数值或单位待核实")!!

    fun fields(content: CollectionCatalogContentVo): List<CollectionPrintFieldVo> = buildList {
        add(field("原藏号",content.originalNo));add(field("分类号",content.classificationNo))
        add(field("正式名称",content.formalName));add(field("原名",content.originalName))
        add(field("具体年代",content.era))
        add(field("质地",content.texture,"col_texture"));add(field("级别",content.level?.name,"col_relic_level"))
        add(field("登记件套数",quantity(content.quantity,content.unit)));add(field("实际数量",quantity(content.actualQuantity,content.actualQuantityUnit)))
        add(field("尺寸",dimensions(content)));add(field("质量",mass(content)))
        add(field("完残程度",content.completeness?.name,"col_completeness"));add(field("完残状况",content.completeStatus))
        add(field("保存状态",content.preserveState?.name,"col_preserve"));add(field("来源方式",content.source?.name,"col_source"))
        add(field("来源说明",content.sourceDesc));add(field("取得日期",content.acquireDate ?: content.acquireDateRange))
        add(field("实收入馆日期",content.entryDate));add(field("核收完成时间",content.receiptCompletedAt))
        add(field("基本特征",content.feature));add(field("铭文款识",content.inscription))
        add(field("鉴定意见",content.appraisalOpinion))
        content.registrationDetails?.let { d ->
            add(field("纸质总账册号",d.paperLedgerBookNo));add(field("纸质总账页码",d.paperLedgerPage))
            add(field("年代类型",d.eraMeaning?.let { mapOf(EnumCollectionEraMeaning.MANUFACTURED to "制造年代",EnumCollectionEraMeaning.USED to "使用年代",EnumCollectionEraMeaning.FORMED to "形成年代",EnumCollectionEraMeaning.LIVED to "生存年代")[it] }))
            add(field("年代表示方式",d.eraNotation?.let { mapOf(EnumCollectionEraNotation.GREGORIAN to "公历纪年",EnumCollectionEraNotation.GEOLOGICAL to "地质年代",EnumCollectionEraNotation.ARCHAEOLOGICAL to "考古学年代",EnumCollectionEraNotation.CHINESE_HISTORY to "中国历史学年代",EnumCollectionEraNotation.REGNAL to "帝王纪年",EnumCollectionEraNotation.OTHER to "其他")[it] }))
            add(field("测量记录",joined(d.measuredOn,d.measuredBy,d.measurementMethod)))
            add(field("鉴定机构",d.appraisalInstitution));add(field("鉴定人员",d.appraisers));add(field("鉴定日期",d.appraisedOn))
            d.parts.forEach { p ->
                val c=CollectionCatalogContentVo(dimensions=p.dimensions,dimensionUnit=p.dimensionUnit,weight=p.weight,weightUnit=p.weightUnit,
                    registrationDetails=CollectionRegistrationDetailsVo(weightExcludesAccessories=p.includesAccessories?.not(),dimensionUnknownReason=p.dimensionUnknownReason,weightUnknownReason=p.weightUnknownReason))
                add(field("分件 ${p.partNo.orEmpty()} ${p.name.orEmpty()}",joined(dimensions(c),mass(c),p.remark)))
            }
            d.provenance.forEachIndexed { i,p -> add(field("流传经历 ${i+1}",joined(p.from,p.to,p.periodDescription,p.institution,p.collector,p.originalNumber,p.evidence,p.remark))) }
            d.excavations.forEachIndexed { i,p -> add(field("考古信息 ${i+1}",joined(p.date,p.periodDescription,p.site,p.position,p.institution,p.evidence))) }
            d.manufacture.forEachIndexed { i,p -> add(field("生产制造 ${i+1}",joined(p.period,p.place,p.technique,p.institution,p.maker,p.remark))) }
            d.publications.forEachIndexed { i,p -> add(field("著录文献 ${i+1}",joined(p.publishedOn,p.periodDescription,p.publisher,p.title,p.author,p.chapter,p.pages,p.summary,p.remark))) }
        }
        if(!content.bibliography.isNullOrBlank()) add(field("原著录文献",content.bibliography))
    }
    fun collectionFields(collection: CollectionVo): List<CollectionPrintFieldVo> = fields(CollectionCatalogMapper.fromCollection(collection)) + listOf(
        field("总登记号",collection.totalRegNo),field("登记日期",collection.registeredAt?.toLocalDate() ?: if (collection.historicalConfirmedAt != null) "原账未载／待考" else null),
        field("入馆凭证号",collection.entryVoucherNo),field("退出馆藏凭证号",collection.deregVoucherNo ?: if(collection.lifeStatus!=EnumCollectionStatus.DEREGISTERED) "不适用" else "待核实"),field("保管位置",collection.warehouseName ?: collection.warehouseId),field("备注",collection.remark))
    fun supplement(print: CollectionPrintVo): CollectionPrintVo {
        val standard=print.collections.flatMapIndexed { i,c ->
            collectionFields(c).map { it.copy(label="藏品 ${i+1}（${c.totalRegNo ?: "待登记"}）${it.label}") }
        }
        val labels=standard.map {it.label}.toSet()
        return print.copy(fields=print.fields.filterNot {it.label in labels}+standard)
    }
}
