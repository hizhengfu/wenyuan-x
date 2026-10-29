package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** WW/T 0017 A.4：本体时间的性质与纪年表示方式是两个独立维度。 */
enum class EnumCollectionEraMeaning { MANUFACTURED, USED, FORMED, LIVED }
enum class EnumCollectionEraNotation { GREGORIAN, GEOLOGICAL, ARCHAEOLOGICAL, CHINESE_HISTORY, REGNAL, OTHER }

data class CatalogProvenanceVo(
    val from: LocalDate? = null, val to: LocalDate? = null, val periodDescription: String? = null,
    val institution: String? = null, val collector: String? = null, val originalNumber: String? = null,
    val evidence: String? = null, val remark: String? = null,
)
data class CatalogExcavationVo(
    val date: LocalDate? = null, val periodDescription: String? = null, val site: String? = null,
    val position: String? = null, val institution: String? = null, val evidence: String? = null,
)
data class CatalogPublicationVo(
    val publishedOn: LocalDate? = null, val periodDescription: String? = null, val publisher: String? = null,
    val title: String? = null, val author: String? = null, val chapter: String? = null,
    val pages: String? = null, val summary: String? = null, val remark: String? = null,
)
data class CatalogManufactureVo(
    val period: String? = null, val place: String? = null, val technique: String? = null,
    val institution: String? = null, val maker: String? = null, val remark: String? = null,
)
data class CatalogPartMeasurementVo(
    val partNo: String? = null, val name: String? = null, val dimensions: DimensionVo? = null,
    val dimensionUnit: String? = null, val weight: String? = null, val weightUnit: String? = null,
    val includesAccessories: Boolean? = null, val remark: String? = null,
    val dimensionUnknownReason: String? = null, val weightUnknownReason: String? = null,
)

/** 新增著录细目不推断历史事实；null 不进入历史快照序列化结果。 */
data class CollectionRegistrationDetailsVo(
    val paperLedgerBookNo: String? = null,
    val paperLedgerPage: String? = null,
    val appraisalInstitution: String? = null,
    val appraisers: String? = null,
    val appraisedOn: LocalDate? = null,
    val coverIsFrontView: Boolean? = null,
    val eraMeaning: EnumCollectionEraMeaning? = null,
    val eraNotation: EnumCollectionEraNotation? = null,
    val dimensionUnknownReason: String? = null,
    val weightUnknownReason: String? = null,
    val quantityUnknownReason: String? = null,
    val weightExcludesAccessories: Boolean? = null,
    val measurementMethod: String? = null,
    val measuredOn: LocalDate? = null,
    val measuredBy: String? = null,
    val parts: List<CatalogPartMeasurementVo> = emptyList(),
    val provenance: List<CatalogProvenanceVo> = emptyList(),
    val excavations: List<CatalogExcavationVo> = emptyList(),
    val publications: List<CatalogPublicationVo> = emptyList(),
    val manufacture: List<CatalogManufactureVo> = emptyList(),
)

/** 数值标准化仅供明确标注的规范输出；保存时不悄悄改写原测量值。 */
object CollectionMeasurementPolicy {
    private val number = Regex("^([+]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+))\\s*(cm|mm|m|kg|g|厘米|毫米|米|千克|克)?$", RegexOption.IGNORE_CASE)
    private fun unit(value: String): String = when (value.trim().lowercase()) {
        "厘米" -> "cm"; "毫米" -> "mm"; "米" -> "m"; "千克" -> "kg"; "克" -> "g"
        else -> value.trim().lowercase()
    }
    private fun parse(value: String?, declaredUnit: String?, units: Set<String>): Pair<BigDecimal, String>? {
        val match = value?.trim()?.let(number::matchEntire) ?: return null
        val inline = match.groupValues[2].takeIf(String::isNotBlank)?.let(::unit)
        val declared = declaredUnit?.takeIf(String::isNotBlank)?.let(::unit)
        if (inline != null && declared != null && inline != declared) return null
        val resolved = inline ?: declared ?: return null
        val amount = match.groupValues[1].toBigDecimalOrNull() ?: return null
        return (amount to resolved).takeIf { amount.signum() > 0 && resolved in units }
    }
    fun lengthCm(value: String?, declaredUnit: String?): BigDecimal? = parse(value, declaredUnit, setOf("mm", "cm", "m"))?.let { (n, u) ->
        when(u) { "m" -> n.multiply(BigDecimal(100)); "mm" -> n.divide(BigDecimal.TEN); else -> n }
    }
    fun massKg(value: String?, declaredUnit: String?): BigDecimal? = parse(value, declaredUnit, setOf("g", "kg"))?.let { (n, u) ->
        if(u == "g") n.divide(BigDecimal(1000)) else n
    }
    fun lengthText(value: String?, declaredUnit: String?): String? = lengthCm(value, declaredUnit)?.let {
        if(it > BigDecimal(100)) "${it.divide(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).toPlainString()} m"
        else "${it.setScale(1, RoundingMode.HALF_UP).toPlainString()} cm"
    }
    fun massText(value: String?, declaredUnit: String?): String? = massKg(value, declaredUnit)?.let { "${it.setScale(3, RoundingMode.HALF_UP).toPlainString()} kg" }
    fun dimensions(value: DimensionVo?): List<Pair<String, String>> = if(value == null) emptyList() else
        (listOf("通高" to value.height, "口径" to value.mouth, "底径" to value.bottom, "腹径" to value.belly,
            "长" to value.length, "宽" to value.width, "直径" to value.diameter) + value.extra.orEmpty().filterKeys { it != "description" }.toList())
            .mapNotNull { (label, amount) -> amount?.takeIf(String::isNotBlank)?.let { label to it } }

    fun issues(content: CollectionCatalogContentVo): List<String> = buildList {
        val measured = dimensions(content.dimensions)
        if(measured.isNotEmpty() && measured.any { lengthCm(it.second, content.dimensionUnit) == null }) add("尺寸须为正数并明确一致的 mm、cm 或 m 单位；原文疑义请在测量说明中记录")
        if(!content.weight.isNullOrBlank() && massKg(content.weight, content.weightUnit) == null) add("质量须为正数并明确一致的 g 或 kg 单位")
        if(content.nature != EnumCollectionNature.OTHER && !content.weight.isNullOrBlank() && content.registrationDetails?.weightExcludesAccessories != true) add("须确认称重不包含附件质量")
        val parts = content.registrationDetails?.parts.orEmpty()
        if(parts.any { it.partNo.isNullOrBlank() || it.name.isNullOrBlank() } || parts.map { it.partNo?.trim() }.distinct().size != parts.size) add("分件测量须有不重复的分件号及名称")
        parts.forEach { part ->
            if(dimensions(part.dimensions).isEmpty() && part.dimensionUnknownReason.isNullOrBlank()) add("分件 ${part.partNo} 须记录尺寸或无法测量原因")
            if(part.weight.isNullOrBlank() && part.weightUnknownReason.isNullOrBlank()) add("分件 ${part.partNo} 须记录质量或无法称重原因")
            if(dimensions(part.dimensions).any { lengthCm(it.second, part.dimensionUnit) == null }) add("分件 ${part.partNo} 的尺寸数值或单位不正确")
            if(!part.weight.isNullOrBlank() && massKg(part.weight, part.weightUnit) == null) add("分件 ${part.partNo} 的质量须为正数并明确单位")
            if(content.nature != EnumCollectionNature.OTHER && !part.weight.isNullOrBlank() && part.includesAccessories != false) add("分件 ${part.partNo} 的质量须不包含附件")
        }
        content.registrationDetails?.let { details ->
            if(details.measuredOn?.isAfter(LocalDate.now()) == true) add("测量日期不能晚于今天")
            if(details.provenance.any { it.from != null && it.to != null && it.from > it.to }) add("流传经历结束日期不能早于开始日期")
        }
    }
    fun requireValid(content: CollectionCatalogContentVo) {
        val issues = issues(content)
        if(issues.isNotEmpty()) throw BadRequestException(issues.joinToString("；"))
    }
}
