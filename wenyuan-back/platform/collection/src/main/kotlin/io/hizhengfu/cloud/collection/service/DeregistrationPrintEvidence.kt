package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import tools.jackson.databind.JsonNode

internal data class DeregistrationPrintOriginal(val collection:CollectionVo,val orgName:String)
internal data class DeregistrationPrintSelection(val collection:CollectionVo,val orgName:String?,val basis:String)

/** 新方案冻结打印字段；旧方案仅使用已存在基线，不能从当前主档补作历史身份。 */
internal object DeregistrationPrintEvidence {
    fun capture(e:DeregistrationDao):Map<String,Any> = mapOf("printOriginalVersion" to 1,"printOriginal" to DeregistrationPrintOriginal(CollectionDao[e.collectionId].toModel(),CollectionOrgDao[e.orgId].name))
    fun fields(e:DeregistrationDao,root:JsonNode):Map<String,Any> {
        if(!root.has("printOriginalVersion") && !root.has("printOriginal"))return emptyMap()
        val original=read(e,root)
        return mapOf("printOriginalVersion" to 1,"printOriginal" to original)
    }
    private fun read(e:DeregistrationDao,root:JsonNode):DeregistrationPrintOriginal {
        if(root.get("printOriginalVersion")?.asInt()!=1 || root.get("printOriginal")?.isObject!=true)fail()
        val original=JsonUtils.fromJson(root.get("printOriginal").toString(),DeregistrationPrintOriginal::class.java) ?: fail()
        if(original.collection.id!=e.collectionId || original.collection.orgId!=e.orgId || original.orgName.isBlank() ||
            e.baseline==null || !CollectionCatalogMapper.matchesSnapshot(e.baseline, original.collection))fail()
        return original
    }
    fun select(e:DeregistrationDao,root:JsonNode):DeregistrationPrintSelection {
        if(root.has("printOriginalVersion") || root.has("printOriginal")) {
            val original=read(e,root)
            return DeregistrationPrintSelection(original.collection.copy(coverImage=null),original.orgName,"方案固定的藏品与单位字段；影像和附件请查阅归档原件")
        }
        val v=e.baseline ?: fail()
        val collection=CollectionVo(id=e.collectionId,orgId=e.orgId,name=v.formalName ?: v.totalName,
            classNo=v.classificationNo,
            auxNo=v.originalNo,
            originalName=v.originalName,
            categoryId=v.categoryId,
            era=v.era,
            region=v.region,
            country=v.country,
            culture=v.culture,
            textureCategory1=v.textureCategory1,
            textureCategory2=v.textureCategory2,
            texture=v.texture,
            color=v.color,
            nature=v.nature,
            level=v.level,
            quantity=v.quantity,
            unit=v.unit,
            dimensions=v.dimensions,
            weightRange=v.weightRange,
            weight=v.weight,
            weightUnit=v.weightUnit,
            volume=v.volume,
            volumeUnit=v.volumeUnit,
            completeness=v.completeness,
            completeStatus=v.completeStatus,
            preserveState=v.preserveState,
            source=v.source,
            sourceNo=v.sourceNo,
            sourceDesc=v.sourceDesc,
            acquireDate=v.acquireDate,
            acquireDateRange=v.acquireDateRange,
            acquireYear=v.acquireYear,
            feature=v.feature,
            inscription=v.inscription,
            author=v.author,
            edition=v.edition,
            volumeNo=v.volumeNo,
            valuation=v.valuation,
            warehouseId=v.warehouseId,
            remark=v.remark,
        )
        return DeregistrationPrintSelection(collection,null,"旧方案仅留存编目基线；总登记号及单位历史名称未留存，须核对原账；影像请查阅归档原件")
    }
    private fun fail():Nothing=throw BadRequestException("退出方案的历史打印字段缺失或与原基线不一致，请核对原账")
}
