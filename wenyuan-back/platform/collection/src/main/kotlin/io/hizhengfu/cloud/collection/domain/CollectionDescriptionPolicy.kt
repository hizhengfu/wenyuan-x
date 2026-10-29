package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException

/** WW/T 0017 A.4、A.5。细类保留完整路径，避免“有机质”同时被当作根类和叶类。 */
object CollectionDescriptionPolicy {
    val eraMeanings = linkedMapOf(
        EnumCollectionEraMeaning.MANUFACTURED to "制造年代", EnumCollectionEraMeaning.USED to "使用年代",
        EnumCollectionEraMeaning.FORMED to "形成年代", EnumCollectionEraMeaning.LIVED to "生存年代",
    )
    val eraNotations = linkedMapOf(
        EnumCollectionEraNotation.GREGORIAN to "公历纪年", EnumCollectionEraNotation.GEOLOGICAL to "地质年代",
        EnumCollectionEraNotation.ARCHAEOLOGICAL to "考古学年代", EnumCollectionEraNotation.CHINESE_HISTORY to "中国历史学年代",
        EnumCollectionEraNotation.REGNAL to "帝王纪年", EnumCollectionEraNotation.OTHER to "其他纪年",
    )
    val materialCategories = linkedMapOf(
        "单一质地" to listOf("无机质/非金属材料", "无机质/金属材料", "有机质/植物质材料", "有机质/动物质材料", "有机质/其他有机质材料"),
        "复合或组合质地" to listOf("无机复合或组合质地", "有机复合或组合质地", "有机无机复合或组合质地"),
    )
    // 常用具体材质仅作录入建议；复合材质须写明主要材质，不限制为此清单。
    val materials = listOf("瓷", "陶", "铜", "金", "银", "铁", "锡", "铅", "铝", "玉", "宝石", "石", "砖瓦", "玻璃", "珐琅",
        "木", "竹", "漆", "纸张", "棉", "麻", "丝", "毛", "皮革", "骨", "角", "牙", "贝壳", "树脂", "橡胶", "塑料", "其他")

    fun materialIssues(category: String?, detail: String?): List<String> = buildList {
        if (!category.isNullOrBlank() && category !in materialCategories) add("质地类别须选择单一质地或复合或组合质地")
        if (!detail.isNullOrBlank() && detail !in materialCategories[category].orEmpty()) add("质地细类与质地类别不一致，请按规范层级选择")
    }

    /** 草稿允许尚未填写；已填写的组合必须有效，完整性在编目提交时检查。 */
    fun issues(content: CollectionCatalogContentVo): List<String> = buildList {
        addAll(natureIssues(content))
        addAll(materialIssues(content.textureCategory1, content.textureCategory2))
        if (content.actualQuantity != null && content.actualQuantity <= 0) add("实际数量须为正整数")
        if (content.actualQuantityUnit?.trim() == "套") add("实际数量单位应为件、只、册等，不得使用套")
        if (content.actualQuantity == null && !content.actualQuantityUnit.isNullOrBlank()) add("填写实际数量单位时须同时填写实际数量")
        if (content.actualQuantity != null && content.actualQuantityUnit.isNullOrBlank()) add("请填写实际数量单位")
    }

    fun natureIssues(content: CollectionCatalogContentVo): List<String> =
        if (content.nature == EnumCollectionNature.OTHER && content.level !in setOf(null, EnumRelicLevel.UNRATED))
            listOf("其他藏品不适用文物等级，请核实藏品性质与级别") else emptyList()

    fun requireValid(content: CollectionCatalogContentVo) {
        val issues = issues(content)
        if (issues.isNotEmpty()) throw BadRequestException(issues.joinToString("；"))
    }
}
