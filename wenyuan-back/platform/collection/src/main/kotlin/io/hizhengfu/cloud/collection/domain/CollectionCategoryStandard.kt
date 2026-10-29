package io.hizhengfu.cloud.collection.domain

/** WW/T 0017—2013 附录D表D.1。代码仅是表内序号的两位表示，不是另行发布的分类编码。 */
object CollectionCategoryStandard {
    const val BASIS = "WW/T 0017—2013 附录D表D.1"
    const val SELECTION_GUIDANCE = "钱币、雕塑造像、文具等应按具体用途或工艺选择类别：银币归钱币，铜鎏金佛像归雕塑、造像，石砚和玉杆毛笔归文具。"
    data class Entry(val code: String, val name: String, val sort: Int)
    val entries: List<Entry> = listOf(
        "玉石器、宝石",
        "陶器",
        "瓷器",
        "铜器",
        "金银器",
        "铁器、其他金属器",
        "漆器",
        "雕塑、造像",
        "石器、石刻、砖瓦",
        "书法、绘画",
        "文具",
        "甲骨",
        "玺印符牌",
        "钱币",
        "牙骨角器",
        "竹木雕",
        "家具",
        "珐琅器",
        "织绣",
        "古籍善本",
        "碑帖拓本",
        "武器",
        "邮品",
        "文件、宣传品",
        "档案文书",
        "名人遗物",
        "玻璃器",
        "乐器、法器",
        "皮革",
        "音像制品",
        "票据",
        "交通、运输工具",
        "度量衡器",
        "标本、化石",
        "其他",
    ).mapIndexed { index, name -> Entry((index + 1).toString().padStart(2, '0'), name, index) }
    fun matches(code: String?, name: String?): Boolean = entries.any { it.code == code && it.name == name }
}
