package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 存量只读核查；历史业务中的类别仅提示核实，不直接改主档、级别、审批或永久原件。 */
@Component
@Transactional
class CollectionNatureReviewService(private val scope: CollectionScopeResolver, private val directory: CollectionActorDirectory) {
    data class Item(val collectionId: String, val orgId: String, val totalRegNo: String, val name: String,
        val nature: EnumCollectionNature?, val reasons: List<String>)
    data class Result(val items: List<Item>, val total: Int, val unresolvedArchives: Int, val page: Int, val pageSize: Int)

    fun page(page: Int = 1, pageSize: Int = 30): Result {
        val actor = directory.requireActive(SecurityUtils.getUserDetail().id)
        if (actor.permissions.none { it in setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.SELECT, CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT) })
            throw BadRequestException("缺少当前藏品查询或总账管理权限")
        return inspect(scope.currentReadableOrgIds(actor, CollectionConst.CROSS_ORG), page, pageSize)
    }

    // 只执行 SELECT。部署诊断传 null 查询全库，业务入口独立按当前身份限制可见馆别。
    fun inspect(orgIds: Set<String>? = null, page: Int = 1, pageSize: Int = 30): Result {
        if (page < 1 || pageSize !in 1..200) throw BadRequestException("核查页码或每页数量无效")
        if (orgIds?.isEmpty() == true) return Result(emptyList(), 0, 0, page, pageSize)
        val scientific = linkedSetOf<String>()
        var unresolved = 0
        val a = CollectionPermanentArchives
        var offset = 0L
        while (true) {
            val batch = a.select(a.orgId, a.bizType, a.snapshot, a.digest).where {
                a.bizType inList listOf(CollectionConst.BIZ_LOAN, CollectionConst.BIZ_RETURN)
            }.orderBy(a.id).limit(200).offset(offset).toList()
            if (batch.isEmpty()) break
            batch.forEach { row ->
                try {
                    if (row[a.digest] != CollectionCareEvidence.digest(row[a.snapshot])) throw BadRequestException("原档案摘要不一致")
                    val envelope = JsonUtils.mapper.readTree(row[a.snapshot])
                    val data = if (envelope.has("proposal")) envelope.path("proposal") else envelope
                    val items = if (row[a.bizType] == CollectionConst.BIZ_LOAN) data.path("dossier").path("assessments") else data.path("inspection").path("items")
                    items.filter { it.path("objectKind").asString() == EnumLoanObjectKind.SCIENTIFIC_FOSSIL.name }
                        .mapNotNullTo(scientific) { it.path("collectionId").asString().takeIf(String::isNotBlank) }
                } catch (_: tools.jackson.core.JacksonException) {
                    if (orgIds == null || row[a.orgId] in orgIds) unresolved++
                } catch (_: BadRequestException) {
                    if (orgIds == null || row[a.orgId] in orgIds) unresolved++
                }
            }
            offset += batch.size
        }
        // 尚未形成永久快照的历史草稿同样提示，不能把草稿中的判断当作正式性质。
        offset = 0
        while (true) {
            val batch = Loans.select(Loans.dossier).orderBy(Loans.id).limit(200).offset(offset).toList()
            if (batch.isEmpty()) break
            batch.forEach { row -> row[Loans.dossier]?.assessments.orEmpty().filter { it.objectKind == EnumLoanObjectKind.SCIENTIFIC_FOSSIL }.mapTo(scientific) { it.collectionId } }
            offset += batch.size
        }
        offset = 0
        while (true) {
            val batch = CollectionReturns.select(CollectionReturns.inspection).orderBy(CollectionReturns.id).limit(200).offset(offset).toList()
            if (batch.isEmpty()) break
            batch.forEach { row -> row[CollectionReturns.inspection]?.items.orEmpty().filter { it.objectKind == EnumLoanObjectKind.SCIENTIFIC_FOSSIL }.mapTo(scientific) { it.collectionId } }
            offset += batch.size
        }
        var total = 0
        val result = mutableListOf<Item>()
        val start = (page.toLong() - 1) * pageSize
        offset = 0
        while (true) {
            val c = Collections
            val query = c.select(c.id, c.orgId, c.totalRegNo, c.name, c.nature, c.level)
                .where { c.isRegistered() or (c.catalogStatus eq EnumCatalogStatus.APPROVED) }
            if (orgIds != null) query.andWhere { c.orgId inList orgIds }
            val batch = query.orderBy(c.id).limit(200).offset(offset).toList()
            if (batch.isEmpty()) break
            batch.forEach { row ->
                val reasons = buildList {
                    if (row[c.nature] == null) add("正式藏品尚未核实法定性质")
                    if (row[c.id].value in scientific && row[c.nature] != EnumCollectionNature.SCIENTIFIC_FOSSIL)
                        add("历史借用或归还曾登记为第61条化石，须对照原件核实正式性质；历史选择不能替代正式变更")
                    if (CollectionNaturePolicy.usesRelicRules(row[c.nature]) && row[c.level] == EnumRelicLevel.UNRATED)
                        add("按文物规定管理的正式藏品尚未核实相应等级及行政依据")
                }
                if (reasons.isNotEmpty()) {
                    if (total.toLong() >= start && result.size < pageSize) result += Item(row[c.id].value, row[c.orgId], row[c.totalRegNo], row[c.name], row[c.nature], reasons)
                    total++
                }
            }
            offset += batch.size
        }
        return Result(result, total, unresolved, page, pageSize)
    }
}
