package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.dao.Warehouses
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.inList

/** 结构维护使用实际parentId关系；parentIds仅作为派生路径，不能决定哪些下级需要保护。 */
internal object WarehouseTreeStructure {
    fun ancestors(parentId: String, orgId: String, scene: EnumLocationScene, forbidden: Set<String> = emptySet()): List<WarehouseDao> {
        val chain=mutableListOf<WarehouseDao>()
        val seen=mutableSetOf<String>()
        var next=parentId
        while(next!="0") {
            if(next in forbidden || !seen.add(next)) throw BadRequestException("位置父级关系存在循环或指向自身下级")
            val parent=WarehouseDao.findById(next) ?: throw BadRequestException("位置父级不存在，请核对位置结构")
            parent.refresh(flush = true)
            if(parent.status!=EnumContentStatus.NORMAL) throw BadRequestException("位置父级已退役，请重新选择有效位置")
            if(parent.orgId!=orgId) throw BadRequestException("位置父级链不属于同一收藏单位")
            if(parent.sceneType!=scene) throw BadRequestException("位置父级链必须属于同一位置场景")
            chain+=parent;next=parent.parentId
        }
        val ordered=chain.asReversed()
        val expected=mutableListOf("0")
        ordered.forEach { parent ->
            if(parent.parentIds.split(',').filter(String::isNotBlank)!=expected)
                throw BadRequestException("位置父级路径与实际关系不一致，请先核对父级结构")
            expected+=parent.id.value
        }
        return ordered
    }

    fun subtree(root: WarehouseDao): List<WarehouseDao> {
        val nodes=mutableListOf(root)
        val seen=mutableSetOf(root.id.value)
        var level=listOf(root.id.value)
        while(level.isNotEmpty()) {
            val children=level.chunked(200).flatMap { batch -> WarehouseDao.find { Warehouses.parentId inList batch }.toList() }
            children.forEach { child ->
                child.refresh(flush = true)
                if(!seen.add(child.id.value)) throw BadRequestException("位置下级关系存在循环，不能调整结构")
                if(child.orgId!=root.orgId || child.sceneType!=root.sceneType)
                    throw BadRequestException("位置分支存在其他收藏单位或其他场景的下级，请先核对结构")
            }
            nodes+=children;level=children.map { it.id.value }
        }
        return nodes
    }

    fun parentPath(ancestors: List<WarehouseDao>): String = checkedPath((listOf("0")+ancestors.map { it.id.value }).joinToString(",",postfix=","))

    /** 顺序来自按父子关系遍历的分支，先校验所有路径再允许写入。 */
    fun paths(nodes: List<WarehouseDao>, ancestors: List<WarehouseDao>): Map<String,String> {
        val paths=linkedMapOf(nodes.first().id.value to parentPath(ancestors))
        nodes.drop(1).forEach { child ->
            val parent=paths[child.parentId] ?: throw BadRequestException("位置分支父级不完整")
            paths[child.id.value]=checkedPath(parent+child.parentId+",")
        }
        return paths
    }
    private fun checkedPath(path: String): String {
        if(path.length>2000) throw BadRequestException("位置层级过深，超出库位路径长度限制")
        return path
    }
}
