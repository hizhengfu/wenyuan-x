package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType

/** 独立业务模块提供当前授权后的打印数据；不能以客户端完整内容替代原业务读取。 */
interface CollectionPrintExtension {
    val type: EnumCollectionPrintType
    fun fetch(id: String): CollectionPrintVo
}
