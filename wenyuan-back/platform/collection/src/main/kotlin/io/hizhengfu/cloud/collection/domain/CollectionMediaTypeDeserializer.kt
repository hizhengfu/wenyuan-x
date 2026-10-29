package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

/** 未传类型仍可采用 OTHER；显式非法值不能被全局宽松枚举策略吞掉。 */
class CollectionMediaTypeDeserializer : ValueDeserializer<EnumCollectionMediaType>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EnumCollectionMediaType {
        if (p.currentToken() != JsonToken.VALUE_STRING) throw BadRequestException("影像类型必须为有效的类型名称")
        return EnumCollectionMediaType.entries.firstOrNull { it.name == p.string }
            ?: throw BadRequestException("影像类型无效，请选择已有影像类型")
    }
}
