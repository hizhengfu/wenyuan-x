package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/** 由信息变更流程调用；不提供跳过批准、签署的独立 HTTP 生效入口。 */
internal object CollectionSetAmendments {
    fun coordinate(orgId: String) {
        CollectionOrgDao.find { CollectionOrgs.id eq orgId }.forUpdate().singleOrNull()
            ?: throw BadRequestException("收藏单位不存在")
    }

    fun relations(parent: CollectionDao): List<CollectionSetRelationSnapshot> =
        CollectionSets.selectAll().where { CollectionSets.parentCollectionId eq parent.id.value }.map { row ->
            if (row[CollectionSets.orgId] != parent.orgId) throw BadRequestException("套件关系原馆不一致，请保留原记录核对")
            row[CollectionSets.childCollectionId]?.let { requireMember(it, parent.orgId) }
            CollectionSetRelationSnapshot(row[CollectionSets.id].value, row[CollectionSets.childCollectionId], row[CollectionSets.partNo] ?: "", row[CollectionSets.partName] ?: "",
                row[CollectionSets.relation], row[CollectionSets.quantity], row[CollectionSets.unit], row[CollectionSets.sort])
        }.sortedBy { it.id }

    fun proposal(parent: CollectionDao): CollectionSetAmendment {
        val rows = relations(parent)
        return CollectionSetAmendment(rows, rows, identities(parent, rows, rows))
    }

    fun prepare(parent: CollectionDao, requested: CollectionSetAmendment): CollectionSetAmendment {
        coordinate(parent.orgId)
        val before = relations(parent)
        if (requested.before.sortedBy { it.id } != before) throw BadRequestException("套件关系已变化，请重新读取后拟订申请")
        val after = requested.after.map { row -> row.copy(childCollectionId = row.childCollectionId?.trim()?.takeIf(String::isNotBlank),
            partNo = row.partNo.trim(), partName = row.partName.trim(), unit = row.unit.trim()) }
            .sortedWith(compareBy<CollectionSetRelationSnapshot> { it.id ?: "" }.thenBy { it.partNo })
        val result = CollectionSetAmendment(before, after, identities(parent, before, after))
        validate(parent, result)
        return result
    }

    fun affectedIds(parentId: String, amendment: CollectionSetAmendment): List<String> =
        (listOf(parentId) + (amendment.before + amendment.after).mapNotNull { it.childCollectionId }).distinct().sorted()

    fun validate(parent: CollectionDao, amendment: CollectionSetAmendment) {
        coordinate(parent.orgId)
        if (relations(parent) != amendment.before || identities(parent, amendment.before, amendment.after) != amendment.identities)
            throw BadRequestException("套件关系或关联藏品身份已变化，请保留原申请并重新核对")
        val rows = amendment.after
        val existing = amendment.before.mapNotNull { it.id }.toSet()
        if (rows.mapNotNull { it.id }.let { it.size != it.distinct().size || it.any { id -> id !in existing } })
            throw BadRequestException("套件订正不能使用其他关系标识")
        if (rows.any { it.partNo.isBlank() || it.partNo.length > 32 || it.partName.isBlank() || it.partName.length > 200 ||
                it.quantity <= 0 || it.unit.isBlank() || it.unit.length > 16 || it.relation == EnumSetRelation.MAIN })
            throw BadRequestException("请完整填写合法分件号、名称、关系、数量和计量单位")
        if (rows.map { it.partNo }.distinct().size != rows.size) throw BadRequestException("分件号不能重复")
        val children = rows.mapNotNull { it.childCollectionId }
        if (children.distinct().size != children.size || parent.id.value in children) throw BadRequestException("独立分件不能重复或指向主件自身")
        children.forEach { child ->
            requireMember(child, parent.orgId)
            if (CollectionSetDao.find { CollectionSets.childCollectionId eq child }.any { it.parentCollectionId != parent.id.value || it.orgId != parent.orgId })
                throw BadRequestException("该藏品已属于其他套件，请先按原套件批准的订正办理解除")
            val visited = mutableSetOf<String>()
            fun visit(id: String) {
                if (id == parent.id.value) throw BadRequestException("套件关系不能形成循环引用")
                if (!visited.add(id)) return
                relations(requireMember(id, parent.orgId)).mapNotNull { it.childCollectionId }.forEach(::visit)
            }
            visit(child)
        }
    }

    fun requireUnlocked(parent: CollectionDao, amendment: CollectionSetAmendment, locks: CollectionOperationLockService,
                        bizType: String? = null, bizId: String? = null) {
        affectedIds(parent.id.value, amendment).forEach { locks.requireUnlocked(it, bizType, bizId) }
    }

    /** 申请获批且纸账签署完成后，在调用方事务内同步关系；实际新关系 ID 归入生效档案。 */
    fun apply(parent: CollectionDao, amendment: CollectionSetAmendment): List<CollectionSetRelationSnapshot> {
        validate(parent, amendment)
        val retained = amendment.after.mapNotNull { it.id }.toSet()
        CollectionSetDao.find { CollectionSets.parentCollectionId eq parent.id.value }.filter { it.id.value !in retained }.forEach { it.delete() }
        amendment.after.forEach { value ->
            val row = value.id?.let { CollectionSetDao[it] } ?: CollectionSetDao.new { orgId = parent.orgId }
            row.parentCollectionId = parent.id.value; row.childCollectionId = value.childCollectionId
            row.partNo = value.partNo; row.partName = value.partName; row.relation = value.relation
            row.quantity = value.quantity; row.unit = value.unit; row.sort = value.sort
        }
        return relations(parent)
    }

    private fun identities(parent: CollectionDao, before: List<CollectionSetRelationSnapshot>, after: List<CollectionSetRelationSnapshot>) =
        (listOf(parent.id.value) + (before + after).mapNotNull { it.childCollectionId }).distinct().sorted().map { id ->
            val item = memberRow(id, parent.orgId)
            CollectionSetIdentitySnapshot(id, item[Collections.orgId], item[Collections.totalRegNo], item[Collections.name], item[Collections.registeredAt]?.truncatedTo(java.time.temporal.ChronoUnit.MILLIS), item[Collections.historicalConfirmedAt]?.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
        }

    private fun memberRow(id: String, orgId: String) =
        (Collections.selectAll().where { Collections.id eq id }.singleOrNull()
            ?: throw BadRequestException("套件关联藏品缺失，请保留原记录核对")).also {
            if (it[Collections.orgId] != orgId) throw BadRequestException("套件关联藏品须属于原收藏单位")
        }

    private fun requireMember(id: String, orgId: String): CollectionDao {
        memberRow(id, orgId)
        return CollectionDao[id]
    }
}
