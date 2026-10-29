package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException

/** 第61条的法定管理类别贯穿主档和全部业务，不从名称、分类目录或自然资源保护等级推定。 */
object CollectionNaturePolicy {
    fun parse(value: String?): EnumCollectionNature? = when (value?.trim()) {
        "RELIC", "文物藏品" -> EnumCollectionNature.RELIC
        "OTHER", "其他藏品" -> EnumCollectionNature.OTHER
        "SCIENTIFIC_FOSSIL", "科学化石" -> EnumCollectionNature.SCIENTIFIC_FOSSIL
        else -> null
    }
    fun usesRelicRules(nature: EnumCollectionNature?): Boolean = nature in setOf(
        EnumCollectionNature.RELIC, EnumCollectionNature.SCIENTIFIC_FOSSIL,
    )

    fun usesRelicRules(storedName: String?): Boolean = usesRelicRules(
        storedName?.takeIf(String::isNotBlank)?.let { name ->
            EnumCollectionNature.entries.singleOrNull { it.name == name }
                ?: throw BadRequestException("原档案藏品法定性质无法识别，请核对原件")
        },
    )

    fun loanKind(nature: EnumCollectionNature?): EnumLoanObjectKind? = when (nature) {
        EnumCollectionNature.RELIC -> EnumLoanObjectKind.RELIC
        EnumCollectionNature.SCIENTIFIC_FOSSIL -> EnumLoanObjectKind.SCIENTIFIC_FOSSIL
        EnumCollectionNature.OTHER -> EnumLoanObjectKind.OTHER
        null -> null
    }

    fun requireLoanKind(nature: EnumCollectionNature?, kind: EnumLoanObjectKind) {
        if (loanKind(nature) != kind) throw BadRequestException(
            "逐件法定管理类别须与正式登记一致；第61条化石须先通过正式编目或信息变更核实，不能仅在本单改选类别",
        )
    }

    fun label(nature: EnumCollectionNature?): String = when (nature) {
        EnumCollectionNature.RELIC -> "文物藏品"
        EnumCollectionNature.SCIENTIFIC_FOSSIL -> "科学价值古脊椎动物或古人类化石（按文物规定管理）"
        EnumCollectionNature.OTHER -> "其他藏品"
        null -> "待核实"
    }
}
