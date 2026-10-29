package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.CollectionSearchCollectionRefVo
import io.hizhengfu.cloud.collection.domain.CollectionSearchGroupVo
import io.hizhengfu.cloud.collection.domain.CollectionSearchItemVo
import io.hizhengfu.cloud.collection.domain.CollectionUnifiedSearchQueryVo
import io.hizhengfu.cloud.collection.domain.CollectionUnifiedSearchVo
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 藏品系统统一检索。
 *
 * 检索只读取当前用户可见收藏单位；接口权限由 Controller 的 COLLECTION_SELECT 再做第一层控制。
 * “全部”模式为每个业务分组返回摘要，指定 group 时对该分组做服务端分页。
 */
@Component
@Transactional(readOnly = true)
class CollectionUnifiedSearchService(
    private val scope: CollectionScopeResolver,
    private val annualReports: AnnualReportEvidenceService,
) {
    fun search(query: CollectionUnifiedSearchQueryVo): CollectionUnifiedSearchVo {
        val keyword = CollectionUnifiedSearchPolicy.normalizeKeyword(query.keyword)
        val terms = CollectionUnifiedSearchPolicy.terms(keyword)
        val selectedGroup = query.group?.trim()?.uppercase()?.takeIf(String::isNotBlank)
        if (selectedGroup != null && selectedGroup !in GROUP_ORDER) {
            throw BadRequestException("不支持的检索分组：$selectedGroup")
        }
        val pageNum = query.pageNum.coerceAtLeast(1)
        val pageSize = query.pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val summaryMode = selectedGroup == null
        val requestedPage = if (summaryMode) 1 else pageNum
        val requestedSize = if (summaryMode) SUMMARY_SIZE else pageSize
        val fetchLimit = requestedPage * requestedSize
        val access = CollectionSearchReadAccess.require(scope)
        val context = SearchContext(
            keyword = keyword,
            recordType = query.type?.trim()?.uppercase()?.takeIf(String::isNotBlank),
            terms = terms,
            readableOrgIds = access.orgIds,
            annualOrgId = runCatching { access.currentOrgId(scope) }.getOrNull(),
            acquisitionConfidential = access.acquisitionConfidential,
            matchedCollections = findMatchedCollections(terms, access.orgIds),
            fetchLimit = fetchLimit,
        )

        val rawGroups = GROUP_ORDER
            .filter { selectedGroup == null || it == selectedGroup }
            .map { groupKey -> buildGroup(groupKey, context, requestedPage, requestedSize) }
        val collectionRefs = loadCollectionRefs(rawGroups.flatMap { it.items }.flatMap { it.collectionIds }.toSet(), access.orgIds)
        val groups = rawGroups.map { raw ->
            CollectionSearchGroupVo(
                key = raw.definition.key,
                label = raw.definition.label,
                description = raw.definition.description,
                total = raw.total,
                pageNum = requestedPage,
                pageSize = requestedSize,
                items = raw.items.map { it.toVo(collectionRefs) },
            )
        }
        return CollectionUnifiedSearchVo(
            keyword = keyword,
            total = groups.sumOf { it.total },
            groups = groups,
        )
    }

    private fun buildGroup(
        groupKey: String,
        context: SearchContext,
        pageNum: Int,
        pageSize: Int,
    ): RawGroup {
        val definition = GROUPS.getValue(groupKey)
        val sourceResults = when (groupKey) {
            GROUP_ARCHIVE -> archiveSources(context)
            GROUP_ACQUISITION -> acquisitionSources(context)
            GROUP_CIRCULATION -> circulationSources(context)
            GROUP_STORAGE -> storageSources(context)
            GROUP_FOUNDATION -> foundationSources(context)
            else -> emptyList()
        }
        val matchingSources = sourceResults.filter { context.recordType == null || it.items.firstOrNull()?.type == context.recordType }
        val merged = matchingSources
            .flatMap { it.items }
            .distinctBy { "${it.type}:${it.id}" }
            .sortedWith(
                compareByDescending<RawHit> { it.occurredAt ?: LocalDateTime.MIN }
                    .thenByDescending { it.type }.thenByDescending { it.id },
            )
        val from = ((pageNum - 1) * pageSize).coerceAtMost(merged.size)
        val to = (from + pageSize).coerceAtMost(merged.size)
        return RawGroup(
            definition = definition,
            total = matchingSources.sumOf { it.total },
            items = merged.subList(from, to),
        )
    }

    // ── 藏品档案：总账、编目、影像、附件、登记留痕 ──

    private fun archiveSources(context: SearchContext): List<SourceResult> = listOf(
        searchCollections(context),
        searchCatalogs(context),
        searchMediaFolders(context),
        searchMedia(context),
        searchAttachments(context),
        searchCollectionSets(context),
        searchLedgerLogs(context),
    )

    private fun searchCollections(context: SearchContext): SourceResult {
        val q = collectionMatchQuery(context.terms, context.readableOrgIds)
        q.orderBy(Collections.createTime to SortOrder.DESC, Collections.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val id = row[Collections.id].value
            val title = row[Collections.name]
            val fields = matchedFields(
                context.terms,
                "名称" to title,
                "原名" to row[Collections.originalName],
                "总登记号" to row[Collections.totalRegNo],
                "分类号" to row[Collections.classNo],
                "原藏号" to row[Collections.auxNo],
                "年代" to row[Collections.era],
                "质地" to row[Collections.texture],
                "地域" to row[Collections.region],
                "铭文款识" to row[Collections.inscription],
                "特征描述" to row[Collections.feature],
                "备注" to row[Collections.remark],
            )
            RawHit(
                id = id,
                type = "COLLECTION",
                typeLabel = "藏品档案",
                title = title,
                subtitle = "总登记号 ${row[Collections.totalRegNo]}",
                summary = summaryOf(row[Collections.era], row[Collections.texture], row[Collections.feature], row[Collections.remark]),
                matchedFields = fields,
                collectionIds = listOf(id),
                route = "/collection/ledger",
                occurredAt = row[Collections.createTime],
                score = relevance(context.keyword, title, row[Collections.totalRegNo], fields, related = false),
            )
        }
    }

    private fun searchCatalogs(context: SearchContext): SourceResult {
        val relatedIds = context.matchedCollections.keys
        val q = CollectionCatalogs.selectAll()
        q.applyOrgScope(CollectionCatalogs.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern -> CollectionCatalogs.auditOpinion like pattern },
                idIn(CollectionCatalogs.collectionId, relatedIds),
            )
        }
        q.orderBy(CollectionCatalogs.updateTime to SortOrder.DESC, CollectionCatalogs.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val collectionId = row[CollectionCatalogs.collectionId]
            val related = collectionId in relatedIds
            val fields = if (related) listOf("关联藏品") else matchedFields(
                context.terms,
                "审核意见" to row[CollectionCatalogs.auditOpinion],
            )
            RawHit(
                id = row[CollectionCatalogs.id].value,
                type = "CATALOG",
                typeLabel = "编目资料",
                title = "编目版本 ${row[CollectionCatalogs.version]}",
                subtitle = "状态 ${row[CollectionCatalogs.catalogStatus].name}",
                summary = row[CollectionCatalogs.auditOpinion],
                matchedFields = fields,
                collectionIds = listOf(collectionId),
                route = "/collection/catalog-entry",
                occurredAt = row[CollectionCatalogs.updateTime],
                score = relevance(context.keyword, row[CollectionCatalogs.auditOpinion], null, fields, related),
            )
        }
    }

    private fun searchMedia(context: SearchContext): SourceResult {
        val relatedIds = context.matchedCollections.keys
        val q = CollectionMedias.selectAll()
        q.applyOrgScope(CollectionMedias.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (CollectionMedias.title like pattern) or
                        (CollectionMedias.remark like pattern) or
                        (CollectionMedias.fileId like pattern)
                },
                idIn(CollectionMedias.collectionId, relatedIds),
            )
        }
        q.orderBy(CollectionMedias.createTime to SortOrder.DESC, CollectionMedias.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val collectionId = row[CollectionMedias.collectionId]
            val related = collectionId in relatedIds
            val title = row[CollectionMedias.title] ?: "${row[CollectionMedias.mediaType].name}影像"
            val fields = matchedOrRelated(context.terms, related,
                "影像标题" to row[CollectionMedias.title],
                "影像备注" to row[CollectionMedias.remark],
                "文件编号" to row[CollectionMedias.fileId],
            )
            RawHit(
                id = row[CollectionMedias.id].value,
                type = "MEDIA",
                fileId = row[CollectionMedias.fileId],
                typeLabel = "影像档案",
                title = title,
                subtitle = "影像类型 ${row[CollectionMedias.mediaType].name}",
                summary = row[CollectionMedias.remark],
                matchedFields = fields,
                collectionIds = listOf(collectionId),
                route = "/collection/gallery",
                occurredAt = row[CollectionMedias.createTime],
                score = relevance(context.keyword, title, row[CollectionMedias.fileId], fields, related),
            )
        }
    }

    private fun searchMediaFolders(context: SearchContext): SourceResult {
        val relatedIds = context.matchedCollections.keys
        val q = CollectionMediaFolders.selectAll()
        q.applyOrgScope(CollectionMediaFolders.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern -> CollectionMediaFolders.name like pattern },
                idIn(CollectionMediaFolders.collectionId, relatedIds),
            )
        }
        q.orderBy(CollectionMediaFolders.createTime to SortOrder.DESC, CollectionMediaFolders.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val collectionId = row[CollectionMediaFolders.collectionId]
            val related = collectionId in relatedIds
            val title = row[CollectionMediaFolders.name]
            val fields = matchedOrRelated(context.terms, related, "目录名称" to title)
            RawHit(
                id = row[CollectionMediaFolders.id].value,
                type = "MEDIA_FOLDER",
                typeLabel = "影像目录",
                title = title,
                matchedFields = fields,
                collectionIds = listOf(collectionId),
                route = "/collection/gallery",
                occurredAt = row[CollectionMediaFolders.createTime],
                score = relevance(context.keyword, title, null, fields, related),
            )
        }
    }

    private fun searchAttachments(context: SearchContext): SourceResult {
        val relatedIds = context.matchedCollections.keys
        val q = CollectionAttachments.selectAll()
        q.applyOrgScope(CollectionAttachments.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (CollectionAttachments.name like pattern) or
                        (CollectionAttachments.remark like pattern) or
                        (CollectionAttachments.fileId like pattern)
                },
                idIn(CollectionAttachments.collectionId, relatedIds),
            )
        }
        q.orderBy(CollectionAttachments.createTime to SortOrder.DESC, CollectionAttachments.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val collectionId = row[CollectionAttachments.collectionId]
            val related = collectionId in relatedIds
            val title = row[CollectionAttachments.name] ?: "藏品附件"
            val fields = matchedOrRelated(context.terms, related,
                "附件名称" to row[CollectionAttachments.name],
                "附件备注" to row[CollectionAttachments.remark],
                "文件编号" to row[CollectionAttachments.fileId],
            )
            RawHit(
                id = row[CollectionAttachments.id].value,
                type = "ATTACHMENT",
                fileId = row[CollectionAttachments.fileId],
                typeLabel = "档案附件",
                title = title,
                subtitle = "附件类型 ${row[CollectionAttachments.attachmentType].name}",
                summary = row[CollectionAttachments.remark],
                matchedFields = fields,
                collectionIds = listOf(collectionId),
                route = "/collection/ledger",
                occurredAt = row[CollectionAttachments.createTime],
                score = relevance(context.keyword, title, row[CollectionAttachments.fileId], fields, related),
            )
        }
    }

    private fun searchCollectionSets(context: SearchContext): SourceResult {
        val relatedIds = context.matchedCollections.keys
        val q = CollectionSets.selectAll()
        q.applyOrgScope(CollectionSets.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (CollectionSets.partNo like pattern) or (CollectionSets.partName like pattern)
                },
                idIn(CollectionSets.parentCollectionId, relatedIds) or
                    idIn(CollectionSets.childCollectionId, relatedIds),
            )
        }
        q.orderBy(CollectionSets.createTime to SortOrder.DESC, CollectionSets.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val collectionIds = listOfNotNull(row[CollectionSets.parentCollectionId], row[CollectionSets.childCollectionId]).distinct()
            val related = collectionIds.any { it in relatedIds }
            val title = row[CollectionSets.partName] ?: row[CollectionSets.partNo]?.let { "分件 $it" } ?: "藏品套件关系"
            val fields = matchedOrRelated(context.terms, related,
                "分件号" to row[CollectionSets.partNo],
                "分件名称" to row[CollectionSets.partName],
            )
            RawHit(
                id = row[CollectionSets.id].value,
                type = "COLLECTION_SET",
                typeLabel = "套件关系",
                title = title,
                subtitle = "关系 ${row[CollectionSets.relation].name}",
                matchedFields = fields,
                collectionIds = collectionIds,
                route = "/collection/ledger",
                occurredAt = row[CollectionSets.createTime],
                score = relevance(context.keyword, title, row[CollectionSets.partNo], fields, related),
            )
        }
    }

    private fun searchLedgerLogs(context: SearchContext): SourceResult {
        val relatedIds = context.matchedCollections.keys
        val q = CollectionLedgerLogs.selectAll()
        q.applyOrgScope(CollectionLedgerLogs.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (CollectionLedgerLogs.totalRegNo like pattern) or
                        (CollectionLedgerLogs.field like pattern) or
                        (CollectionLedgerLogs.before like pattern) or
                        (CollectionLedgerLogs.after like pattern) or
                        (CollectionLedgerLogs.reason like pattern)
                },
                idIn(CollectionLedgerLogs.collectionId, relatedIds),
            )
        }
        q.orderBy(CollectionLedgerLogs.createTime to SortOrder.DESC, CollectionLedgerLogs.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val collectionId = row[CollectionLedgerLogs.collectionId]
            val related = collectionId in relatedIds
            val title = "${row[CollectionLedgerLogs.action].name} · ${row[CollectionLedgerLogs.totalRegNo] ?: "藏品记录"}"
            val fields = matchedOrRelated(context.terms, related,
                "总登记号" to row[CollectionLedgerLogs.totalRegNo],
                "变更字段" to row[CollectionLedgerLogs.field],
                "原值" to row[CollectionLedgerLogs.before],
                "新值" to row[CollectionLedgerLogs.after],
                "变更原因" to row[CollectionLedgerLogs.reason],
            )
            RawHit(
                id = row[CollectionLedgerLogs.id].value,
                type = "LEDGER_LOG",
                typeLabel = "登记留痕",
                title = title,
                subtitle = row[CollectionLedgerLogs.field],
                summary = row[CollectionLedgerLogs.reason],
                matchedFields = fields,
                collectionIds = listOf(collectionId),
                route = "/collection/ledger-log",
                occurredAt = row[CollectionLedgerLogs.createTime],
                score = relevance(context.keyword, title, row[CollectionLedgerLogs.totalRegNo], fields, related),
            )
        }
    }

    // ── 征集登记：征集计划、征集申请、入馆凭证、鉴定定级 ──

    private fun acquisitionSources(context: SearchContext): List<SourceResult> = listOf(
        searchAcquisitionPlans(context),
        searchAcquisitions(context),
        searchEntryVouchers(context),
        searchAppraisals(context),
        searchAppraisalMembers(context),
    )

    private fun searchAcquisitionPlans(context: SearchContext): SourceResult {
        val q = AcquisitionPlans.selectAll()
        q.applyOrgScope(AcquisitionPlans.orgId, context.readableOrgIds)
        q.andWhere {
            allTerms(context.terms) { pattern ->
                (AcquisitionPlans.title like pattern) or (AcquisitionPlans.content like pattern)
            }
        }
        q.orderBy(AcquisitionPlans.createTime to SortOrder.DESC, AcquisitionPlans.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val title = row[AcquisitionPlans.title]
            val fields = matchedFields(context.terms,
                "计划名称" to title,
                "计划说明" to row[AcquisitionPlans.content],
            )
            RawHit(
                id = row[AcquisitionPlans.id].value,
                type = "ACQUISITION_PLAN",
                typeLabel = "征集计划",
                title = title,
                subtitle = "${row[AcquisitionPlans.year]} 年 ${row[AcquisitionPlans.planType].name}",
                summary = row[AcquisitionPlans.content],
                matchedFields = fields,
                route = "/collection/acquisition-plan",
                occurredAt = row[AcquisitionPlans.createTime],
                score = relevance(context.keyword, title, null, fields, related = false),
            )
        }
    }

    private fun searchAcquisitions(context: SearchContext): SourceResult {
        val relatedCollections = context.matchedCollections.values
        val relatedMatch = relatedCollections.groupBy({ it.orgId }, { it.id }).map { (orgId, ids) ->
            (Acquisitions.orgId eq orgId) and (Acquisitions.collectionId inList ids)
        }.reduceOrNull { left, right -> left or right } ?: Op.FALSE
        val q = Acquisitions.selectAll()
        q.applyOrgScope(Acquisitions.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (Acquisitions.name like pattern) or
                        (Acquisitions.era like pattern) or
                        (Acquisitions.texture like pattern) or
                        (Acquisitions.holder like pattern) or
                        (if (context.acquisitionConfidential) Acquisitions.reason like pattern else Op.FALSE) or
                        (Acquisitions.completeStatus like pattern)
                },
                relatedMatch,
            )
        }
        q.orderBy(Acquisitions.createTime to SortOrder.DESC, Acquisitions.id to SortOrder.DESC)
        val total = q.count()
        val rows = q.limit(context.fetchLimit).toList()
        val linkedIds = rows.mapNotNull { it[Acquisitions.collectionId] }.toSet()
        val currentOrgById = if (linkedIds.isEmpty()) emptyMap() else Collections.selectAll()
            .where { Collections.id inList linkedIds }
            .associate { it[Collections.id].value to it[Collections.orgId] }
        val items = rows.map { row ->
            val collectionId = row[Acquisitions.collectionId]
            val orgId = row[Acquisitions.orgId]
            val related = collectionId != null && context.matchedCollections[collectionId]?.orgId == orgId
            val currentLinked = collectionId?.let { currentOrgById[it] == orgId } == true
            val title = row[Acquisitions.name]
            val reason = row[Acquisitions.reason].takeIf { context.acquisitionConfidential }
            val fields = matchedOrRelated(context.terms, related,
                "拟征集名称" to title,
                "年代" to row[Acquisitions.era],
                "质地" to row[Acquisitions.texture],
                "持有人" to row[Acquisitions.holder],
                "征集理由" to reason,
                "完残状况" to row[Acquisitions.completeStatus],
            )
            RawHit(
                id = row[Acquisitions.id].value,
                type = "ACQUISITION",
                typeLabel = "征集申请",
                title = title,
                subtitle = row[Acquisitions.holder]?.let { "来源人 $it" },
                summary = summaryOf(row[Acquisitions.era], row[Acquisitions.texture], reason),
                matchedFields = fields,
                collectionIds = if (currentLinked) listOfNotNull(collectionId) else emptyList(),
                route = "/collection/acquisition",
                occurredAt = row[Acquisitions.createTime],
                score = relevance(context.keyword, title, row[Acquisitions.holder], fields, related),
            )
        }
        return SourceResult(total, items)
    }

    private fun searchEntryVouchers(context: SearchContext): SourceResult {
        val q = EntryVouchers.selectAll()
        q.applyOrgScope(EntryVouchers.orgId, context.readableOrgIds)
        q.andWhere {
            allTerms(context.terms) { pattern ->
                (EntryVouchers.voucherNo like pattern) or
                    (EntryVouchers.basis like pattern) or
                    (EntryVouchers.remark like pattern)
            }
        }
        q.orderBy(EntryVouchers.createTime to SortOrder.DESC, EntryVouchers.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val title = "入馆凭证 ${row[EntryVouchers.voucherNo]}"
            val fields = matchedFields(context.terms,
                "凭证号" to row[EntryVouchers.voucherNo],
                "入藏依据" to row[EntryVouchers.basis],
                "备注" to row[EntryVouchers.remark],
            )
            RawHit(
                id = row[EntryVouchers.id].value,
                type = "ENTRY_VOUCHER",
                typeLabel = "入馆凭证",
                title = title,
                subtitle = "状态 ${row[EntryVouchers.voucherStatus].name}",
                summary = summaryOf(row[EntryVouchers.basis], row[EntryVouchers.remark]),
                matchedFields = fields,
                route = "/collection/entry-voucher",
                occurredAt = row[EntryVouchers.createTime],
                score = relevance(context.keyword, title, row[EntryVouchers.voucherNo], fields, related = false),
            )
        }
    }

    private fun searchAppraisals(context: SearchContext): SourceResult {
        val relatedIds = context.matchedCollections.keys
        val q = Appraisals.selectAll()
        q.applyOrgScope(Appraisals.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (Appraisals.resultName like pattern) or
                        (Appraisals.resultEra like pattern) or
                        (Appraisals.resultTexture like pattern) or
                        (Appraisals.committee like pattern) or
                        (Appraisals.conclusion like pattern) or
                        (Appraisals.rejectReason like pattern)
                },
                idIn(Appraisals.collectionId, relatedIds),
            )
        }
        q.orderBy(Appraisals.createTime to SortOrder.DESC, Appraisals.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val collectionId = row[Appraisals.collectionId]
            val related = collectionId in relatedIds
            val title = row[Appraisals.resultName] ?: "鉴定定级记录"
            val fields = matchedOrRelated(context.terms, related,
                "鉴定名称" to row[Appraisals.resultName],
                "鉴定年代" to row[Appraisals.resultEra],
                "鉴定质地" to row[Appraisals.resultTexture],
                "鉴定委员" to row[Appraisals.committee],
                "鉴定结论" to row[Appraisals.conclusion],
                "驳回原因" to row[Appraisals.rejectReason],
            )
            RawHit(
                id = row[Appraisals.id].value,
                type = "APPRAISAL",
                typeLabel = "鉴定定级",
                title = title,
                subtitle = row[Appraisals.resultEra],
                summary = summaryOf(row[Appraisals.conclusion], row[Appraisals.rejectReason]),
                matchedFields = fields,
                collectionIds = listOf(collectionId),
                route = "/collection/appraisal",
                occurredAt = row[Appraisals.createTime],
                score = relevance(context.keyword, title, row[Appraisals.resultEra], fields, related),
            )
        }
    }

    private fun searchAppraisalMembers(context: SearchContext): SourceResult {
        val relatedAppraisals = if (context.matchedCollections.isEmpty()) {
            emptyMap()
        } else {
            val appraisalQuery = Appraisals.selectAll().where {
                Appraisals.collectionId inList context.matchedCollections.keys
            }
            appraisalQuery.applyOrgScope(Appraisals.orgId, context.readableOrgIds)
            appraisalQuery.associate { row -> row[Appraisals.id].value to row[Appraisals.collectionId] }
        }
        val q = AppraisalMembers.selectAll()
        q.applyOrgScope(AppraisalMembers.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (AppraisalMembers.memberName like pattern) or
                        (AppraisalMembers.organization like pattern) or
                        (AppraisalMembers.professionalTitle like pattern) or
                        (AppraisalMembers.specialty like pattern) or
                        (AppraisalMembers.opinion like pattern)
                },
                idIn(AppraisalMembers.appraisalId, relatedAppraisals.keys),
            )
        }
        q.orderBy(AppraisalMembers.createTime to SortOrder.DESC, AppraisalMembers.id to SortOrder.DESC)
        val total = q.count()
        val rows = q.limit(context.fetchLimit).toList()
        val directAppraisalIds = rows.map { it[AppraisalMembers.appraisalId] }.toSet() - relatedAppraisals.keys
        val appraisalCollections = if (directAppraisalIds.isEmpty()) {
            relatedAppraisals
        } else {
            val appraisalQuery = Appraisals.selectAll().where { idIn(Appraisals.id, directAppraisalIds) }
            appraisalQuery.applyOrgScope(Appraisals.orgId, context.readableOrgIds)
            relatedAppraisals + appraisalQuery.associate { row ->
                row[Appraisals.id].value to row[Appraisals.collectionId]
            }
        }
        return SourceResult(
            total = total,
            items = rows.map { row ->
                val appraisalId = row[AppraisalMembers.appraisalId]
                val related = appraisalId in relatedAppraisals
                val title = row[AppraisalMembers.memberName]
                val fields = matchedOrRelated(context.terms, related,
                    "成员姓名" to title,
                    "成员单位" to row[AppraisalMembers.organization],
                    "专业职称" to row[AppraisalMembers.professionalTitle],
                    "专业领域" to row[AppraisalMembers.specialty],
                    "鉴定意见" to row[AppraisalMembers.opinion],
                )
                RawHit(
                    id = row[AppraisalMembers.id].value,
                    type = "APPRAISAL_MEMBER",
                    targetId = appraisalId,
                    typeLabel = "鉴定成员",
                    title = title,
                    subtitle = summaryOf(row[AppraisalMembers.organization], row[AppraisalMembers.professionalTitle]),
                    summary = summaryOf(row[AppraisalMembers.specialty], row[AppraisalMembers.opinion]),
                    matchedFields = fields,
                    collectionIds = listOfNotNull(appraisalCollections[appraisalId]),
                    route = "/collection/appraisal",
                    occurredAt = row[AppraisalMembers.createTime],
                    score = relevance(context.keyword, title, row[AppraisalMembers.organization], fields, related),
                )
            },
        )
    }

    // ── 流转利用：提用、借展、归还、流水、展览、复制、调拨、退出馆藏 ──

    private fun circulationSources(context: SearchContext): List<SourceResult> = listOf(
        searchRequisitions(context),
        searchLoans(context),
        searchReturns(context),
        searchMovements(context),
        searchExhibitions(context),
        searchReproductions(context),
        searchTransfers(context),
        searchDeregistrations(context),
    )

    private fun searchRequisitions(context: SearchContext): SourceResult {
        val relatedBizIds = businessIdsForMatchedCollections(context, CollectionConst.BIZ_REQUISITION)
        val q = Requisitions.selectAll()
        q.applyOrgScope(Requisitions.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (Requisitions.documentNo like pattern) or
                        (Requisitions.basis like pattern) or
                        (Requisitions.department like pattern) or
                        (Requisitions.reviewOpinion like pattern) or
                        (Requisitions.conditionNote like pattern)
                },
                idIn(Requisitions.id, relatedBizIds),
            )
        }
        q.orderBy(Requisitions.createTime to SortOrder.DESC, Requisitions.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val id = row[Requisitions.id].value
            val related = id in relatedBizIds
            val title = row[Requisitions.documentNo]?.let { "提用单 $it" } ?: "藏品提用记录"
            val fields = matchedOrRelated(context.terms, related,
                "提用单号" to row[Requisitions.documentNo],
                "提用依据" to row[Requisitions.basis],
                "提用部门" to row[Requisitions.department],
                "审核意见" to row[Requisitions.reviewOpinion],
                "出库状况" to row[Requisitions.conditionNote],
            )
            RawHit(
                id = id,
                type = "REQUISITION",
                typeLabel = "提用出库",
                title = title,
                subtitle = row[Requisitions.department],
                summary = summaryOf(row[Requisitions.basis], row[Requisitions.reviewOpinion], row[Requisitions.conditionNote]),
                matchedFields = fields,
                collectionIds = splitIds(row[Requisitions.collectionIds]),
                route = "/collection/requisition",
                occurredAt = row[Requisitions.createTime],
                score = relevance(context.keyword, title, row[Requisitions.documentNo], fields, related),
            )
        }
    }

    private fun searchLoans(context: SearchContext): SourceResult {
        val relatedBizIds = businessIdsForMatchedCollections(context, CollectionConst.BIZ_LOAN)
        val q = Loans.selectAll()
        q.applyOrgScope(Loans.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (Loans.title like pattern) or
                        (Loans.borrower like pattern) or
                        (Loans.protocolNo like pattern) or
                        (Loans.insurance like pattern) or
                        (Loans.approvalDocNo like pattern) or
                        (Loans.conditionNote like pattern)
                },
                idIn(Loans.id, relatedBizIds),
            )
        }
        q.orderBy(Loans.createTime to SortOrder.DESC, Loans.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val id = row[Loans.id].value
            val related = id in relatedBizIds
            val title = row[Loans.title] ?: row[Loans.protocolNo]?.let { "借展协议 $it" } ?: "借展记录"
            val fields = matchedOrRelated(context.terms, related,
                "展览事由" to row[Loans.title],
                "借入单位" to row[Loans.borrower],
                "协议号" to row[Loans.protocolNo],
                "保险" to row[Loans.insurance],
                "批文号" to row[Loans.approvalDocNo],
                "出库状况" to row[Loans.conditionNote],
            )
            RawHit(
                id = id,
                type = "LOAN",
                typeLabel = "借展管理",
                title = title,
                subtitle = row[Loans.borrower],
                summary = summaryOf(row[Loans.protocolNo], row[Loans.approvalDocNo], row[Loans.conditionNote]),
                matchedFields = fields,
                collectionIds = splitIds(row[Loans.collectionIds]),
                route = "/collection/loan",
                occurredAt = row[Loans.createTime],
                score = relevance(context.keyword, title, row[Loans.protocolNo], fields, related),
            )
        }
    }

    private fun searchReturns(context: SearchContext): SourceResult {
        val relatedBizIds = businessIdsForMatchedCollections(context, CollectionConst.BIZ_RETURN)
        val q = CollectionReturns.selectAll()
        q.applyOrgScope(CollectionReturns.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (CollectionReturns.sourceDocumentNo like pattern) or
                        (CollectionReturns.basis like pattern) or
                        (CollectionReturns.department like pattern) or
                        (CollectionReturns.reviewOpinion like pattern) or
                        (CollectionReturns.damageNote like pattern)
                },
                idIn(CollectionReturns.id, relatedBizIds),
            )
        }
        q.orderBy(CollectionReturns.createTime to SortOrder.DESC, CollectionReturns.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val id = row[CollectionReturns.id].value
            val related = id in relatedBizIds
            val title = row[CollectionReturns.sourceDocumentNo]?.let { "归还单 $it" } ?: "藏品归还记录"
            val fields = matchedOrRelated(context.terms, related,
                "来源单号" to row[CollectionReturns.sourceDocumentNo],
                "归还依据" to row[CollectionReturns.basis],
                "使用部门" to row[CollectionReturns.department],
                "验收意见" to row[CollectionReturns.reviewOpinion],
                "损伤记录" to row[CollectionReturns.damageNote],
            )
            RawHit(
                id = id,
                type = "RETURN",
                typeLabel = "藏品归库",
                title = title,
                subtitle = row[CollectionReturns.department],
                summary = summaryOf(row[CollectionReturns.basis], row[CollectionReturns.reviewOpinion], row[CollectionReturns.damageNote]),
                matchedFields = fields,
                collectionIds = splitIds(row[CollectionReturns.collectionIds]),
                route = "/collection/return",
                occurredAt = row[CollectionReturns.createTime],
                score = relevance(context.keyword, title, row[CollectionReturns.sourceDocumentNo], fields, related),
            )
        }
    }

    private fun searchMovements(context: SearchContext): SourceResult {
        val relatedIds = context.matchedCollections.keys
        val q = StorageMovements.selectAll()
        q.applyOrgScope(StorageMovements.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (StorageMovements.documentNo like pattern) or
                        (StorageMovements.basis like pattern) or
                        (StorageMovements.department like pattern) or
                        (StorageMovements.operator like pattern) or
                        (StorageMovements.reviewOpinion like pattern)
                },
                idIn(StorageMovements.collectionId, relatedIds),
            )
        }
        q.orderBy(StorageMovements.operatedAt to SortOrder.DESC, StorageMovements.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val collectionId = row[StorageMovements.collectionId]
            val related = collectionId in relatedIds
            val title = row[StorageMovements.documentNo]?.let { "出入库单 $it" }
                ?: "${row[StorageMovements.type].name}库流水"
            val fields = matchedOrRelated(context.terms, related,
                "单号" to row[StorageMovements.documentNo],
                "依据" to row[StorageMovements.basis],
                "部门" to row[StorageMovements.department],
                "经办人" to row[StorageMovements.operator],
                "审核意见" to row[StorageMovements.reviewOpinion],
            )
            RawHit(
                id = row[StorageMovements.id].value,
                type = "MOVEMENT",
                typeLabel = "出入流水",
                title = title,
                subtitle = row[StorageMovements.reason]?.name,
                summary = summaryOf(row[StorageMovements.basis], row[StorageMovements.department], row[StorageMovements.reviewOpinion]),
                matchedFields = fields,
                collectionIds = listOf(collectionId),
                route = "/collection/movement",
                occurredAt = row[StorageMovements.operatedAt],
                score = relevance(context.keyword, title, row[StorageMovements.documentNo], fields, related),
            )
        }
    }

    private fun searchExhibitions(context: SearchContext): SourceResult {
        val relatedBizIds = businessIdsForMatchedCollections(context, CollectionConst.BIZ_EXHIBITION)
        val q = Exhibitions.selectAll()
        q.applyOrgScope(Exhibitions.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (Exhibitions.title like pattern) or
                        (Exhibitions.exhibitionNo like pattern) or
                        (Exhibitions.venue like pattern) or
                        (Exhibitions.organizer like pattern) or
                        (Exhibitions.remark like pattern)
                },
                idIn(Exhibitions.id, relatedBizIds),
            )
        }
        q.orderBy(Exhibitions.createTime to SortOrder.DESC, Exhibitions.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val id = row[Exhibitions.id].value
            val related = id in relatedBizIds
            val title = row[Exhibitions.title]
            val fields = matchedOrRelated(context.terms, related,
                "展览名称" to title,
                "展览编号" to row[Exhibitions.exhibitionNo],
                "展览地点" to row[Exhibitions.venue],
                "主办单位" to row[Exhibitions.organizer],
                "备注" to row[Exhibitions.remark],
            )
            RawHit(
                id = id,
                type = "EXHIBITION",
                typeLabel = "藏品展览",
                title = title,
                subtitle = row[Exhibitions.venue],
                summary = summaryOf(row[Exhibitions.organizer], row[Exhibitions.remark]),
                matchedFields = fields,
                collectionIds = splitIds(row[Exhibitions.collectionIds]),
                route = "/collection/exhibition",
                occurredAt = row[Exhibitions.createTime],
                score = relevance(context.keyword, title, row[Exhibitions.exhibitionNo], fields, related),
            )
        }
    }

    private fun searchReproductions(context: SearchContext): SourceResult {
        val relatedIds = context.matchedCollections.keys
        val q = Reproductions.selectAll()
        q.applyOrgScope(Reproductions.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern -> Reproductions.purpose like pattern },
                idIn(Reproductions.collectionId, relatedIds),
            )
        }
        q.orderBy(Reproductions.createTime to SortOrder.DESC, Reproductions.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val collectionId = row[Reproductions.collectionId]
            val related = collectionId in relatedIds
            val title = "${row[Reproductions.repType]?.name ?: "藏品"}复制记录"
            val fields = matchedOrRelated(context.terms, related, "用途" to row[Reproductions.purpose])
            RawHit(
                id = row[Reproductions.id].value,
                type = "REPRODUCTION",
                typeLabel = "复制拓印",
                title = title,
                subtitle = row[Reproductions.purpose],
                matchedFields = fields,
                collectionIds = listOf(collectionId),
                route = "/collection/reproduction",
                occurredAt = row[Reproductions.createTime],
                score = relevance(context.keyword, title, row[Reproductions.purpose], fields, related),
            )
        }
    }

    private fun searchTransfers(context: SearchContext): SourceResult {
        val relatedIds = context.matchedCollections.keys
        val q = CollectionTransfers.selectAll()
        q.applyOrgScope(CollectionTransfers.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (CollectionTransfers.reason like pattern) or (CollectionTransfers.approvalDocNo like pattern)
                },
                idIn(CollectionTransfers.collectionId, relatedIds),
            )
        }
        q.orderBy(CollectionTransfers.createTime to SortOrder.DESC, CollectionTransfers.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val collectionId = row[CollectionTransfers.collectionId]
            val related = collectionId in relatedIds
            val title = row[CollectionTransfers.approvalDocNo]?.let { "跨馆调拨 $it" } ?: "跨馆调拨记录"
            val fields = matchedOrRelated(context.terms, related,
                "调拨事由" to row[CollectionTransfers.reason],
                "批文号" to row[CollectionTransfers.approvalDocNo],
            )
            RawHit(
                id = row[CollectionTransfers.id].value,
                type = "TRANSFER",
                typeLabel = "跨馆调拨",
                title = title,
                summary = row[CollectionTransfers.reason],
                matchedFields = fields,
                collectionIds = listOf(collectionId),
                route = "/collection/transfer",
                occurredAt = row[CollectionTransfers.createTime],
                score = relevance(context.keyword, title, row[CollectionTransfers.approvalDocNo], fields, related),
            )
        }
    }

    private fun searchDeregistrations(context: SearchContext): SourceResult {
        val relatedIds = context.matchedCollections.keys
        val q = Deregistrations.selectAll()
        q.applyOrgScope(Deregistrations.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (Deregistrations.detail like pattern) or
                        (Deregistrations.approvalDocNo like pattern) or
                        (Deregistrations.expertOpinion like pattern) or
                        (Deregistrations.approvedBy like pattern) or
                        (Deregistrations.destination like pattern)
                },
                idIn(Deregistrations.collectionId, relatedIds),
            )
        }
        q.orderBy(Deregistrations.createTime to SortOrder.DESC, Deregistrations.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val collectionId = row[Deregistrations.collectionId]
            val related = collectionId in relatedIds
            val title = row[Deregistrations.approvalDocNo]?.let { "退出馆藏审批 $it" } ?: "藏品退出馆藏记录"
            val fields = matchedOrRelated(context.terms, related,
                "退出馆藏说明" to row[Deregistrations.detail],
                "批文号" to row[Deregistrations.approvalDocNo],
                "专家意见" to row[Deregistrations.expertOpinion],
                "审批人" to row[Deregistrations.approvedBy],
                "退出馆藏后去向" to row[Deregistrations.destination],
            )
            RawHit(
                id = row[Deregistrations.id].value,
                type = "DEREGISTRATION",
                typeLabel = "藏品退出馆藏",
                title = title,
                subtitle = row[Deregistrations.destination],
                summary = summaryOf(row[Deregistrations.detail], row[Deregistrations.expertOpinion]),
                matchedFields = fields,
                collectionIds = listOf(collectionId),
                route = "/collection/deregistration",
                occurredAt = row[Deregistrations.createTime],
                score = relevance(context.keyword, title, row[Deregistrations.approvalDocNo], fields, related),
            )
        }
    }

    // ── 库房管理：盘点记录/批次、库位、库房日志、入库申请、年度归档 ──

    private fun storageSources(context: SearchContext): List<SourceResult> = listOf(
        searchInventories(context),
        searchInventoryBatches(context),
        searchWarehouses(context),
        searchWarehouseLogs(context),
        searchWarehouseEntries(context),
        searchAnnualArchives(context),
        searchAnnualReports(context),
    )

    private fun searchInventories(context: SearchContext): SourceResult {
        val relatedIds = context.matchedCollections.keys
        val q = Inventories.selectAll()
        q.applyOrgScope(Inventories.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (Inventories.batchName like pattern) or (Inventories.note like pattern)
                },
                idIn(Inventories.collectionId, relatedIds),
            )
        }
        q.orderBy(Inventories.createTime to SortOrder.DESC, Inventories.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val collectionId = row[Inventories.collectionId]
            val related = collectionId != null && collectionId in relatedIds
            val title = row[Inventories.batchName] ?: "藏品盘点记录"
            val fields = matchedOrRelated(context.terms, related,
                "盘点批次" to row[Inventories.batchName],
                "盘点说明" to row[Inventories.note],
            )
            RawHit(
                id = row[Inventories.id].value,
                type = "INVENTORY",
                typeLabel = "藏品盘点",
                title = title,
                subtitle = row[Inventories.result]?.name,
                summary = row[Inventories.note],
                matchedFields = fields,
                collectionIds = listOfNotNull(collectionId),
                route = "/collection/inventory",
                occurredAt = row[Inventories.createTime],
                score = relevance(context.keyword, title, row[Inventories.note], fields, related),
            )
        }
    }

    private fun searchInventoryBatches(context: SearchContext): SourceResult {
        val q = InventoryBatches.selectAll()
        q.applyOrgScope(InventoryBatches.orgId, context.readableOrgIds)
        q.andWhere {
            allTerms(context.terms) { pattern ->
                (InventoryBatches.batchName like pattern) or
                    (InventoryBatches.scopeDesc like pattern) or
                    (InventoryBatches.remark like pattern)
            }
        }
        q.orderBy(InventoryBatches.createTime to SortOrder.DESC, InventoryBatches.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val title = row[InventoryBatches.batchName]
            val fields = matchedFields(context.terms,
                "批次名称" to title,
                "盘点范围" to row[InventoryBatches.scopeDesc],
                "备注" to row[InventoryBatches.remark],
            )
            RawHit(
                id = row[InventoryBatches.id].value,
                type = "INVENTORY_BATCH",
                typeLabel = "盘点批次",
                title = title,
                summary = summaryOf(row[InventoryBatches.scopeDesc], row[InventoryBatches.remark]),
                matchedFields = fields,
                collectionIds = splitIds(row[InventoryBatches.collectionIds]),
                route = "/collection/inventory",
                occurredAt = row[InventoryBatches.createTime],
                score = relevance(context.keyword, title, row[InventoryBatches.scopeDesc], fields, related = false),
            )
        }
    }

    private fun searchWarehouses(context: SearchContext): SourceResult {
        val relatedWarehouseIds = context.matchedCollections.values.mapNotNull { it.warehouseId }.toSet()
        val q = Warehouses.selectAll()
        q.applyOrgScope(Warehouses.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern ->
                    (Warehouses.name like pattern) or
                        (Warehouses.code like pattern) or
                        (Warehouses.temperature like pattern) or
                        (Warehouses.custodian like pattern)
                },
                idIn(Warehouses.id, relatedWarehouseIds),
            )
        }
        q.orderBy(Warehouses.createTime to SortOrder.DESC, Warehouses.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val id = row[Warehouses.id].value
            val related = id in relatedWarehouseIds
            val title = row[Warehouses.name]
            val fields = matchedOrRelated(context.terms, related,
                "库位名称" to title,
                "库位编码" to row[Warehouses.code],
                "温湿度要求" to row[Warehouses.temperature],
                "保管责任人" to row[Warehouses.custodian],
            )
            RawHit(
                id = id,
                type = "WAREHOUSE",
                typeLabel = "库房库位",
                title = title,
                subtitle = row[Warehouses.code],
                summary = summaryOf(row[Warehouses.temperature], row[Warehouses.custodian]),
                matchedFields = fields,
                route = "/collection/warehouse",
                occurredAt = row[Warehouses.createTime],
                score = relevance(context.keyword, title, row[Warehouses.code], fields, related),
            )
        }
    }

    private fun searchWarehouseLogs(context: SearchContext): SourceResult {
        val relatedWarehouseIds = context.matchedCollections.values.mapNotNull { it.warehouseId }.toSet()
        val q = WarehouseLogs.selectAll()
        q.applyOrgScope(WarehouseLogs.orgId, context.readableOrgIds)
        q.andWhere {
            directOrRelated(
                allTerms(context.terms) { pattern -> WarehouseLogs.content like pattern },
                idIn(WarehouseLogs.warehouseId, relatedWarehouseIds),
            )
        }
        q.orderBy(WarehouseLogs.createTime to SortOrder.DESC, WarehouseLogs.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val warehouseId = row[WarehouseLogs.warehouseId]
            val related = warehouseId != null && warehouseId in relatedWarehouseIds
            val fields = matchedOrRelated(context.terms, related, "日志内容" to row[WarehouseLogs.content])
            RawHit(
                id = row[WarehouseLogs.id].value,
                type = "WAREHOUSE_LOG",
                typeLabel = "库房日志",
                title = "${row[WarehouseLogs.logDate]} 库房日志",
                summary = row[WarehouseLogs.content],
                matchedFields = fields,
                route = "/collection/warehouse",
                occurredAt = row[WarehouseLogs.createTime],
                score = relevance(context.keyword, row[WarehouseLogs.content], null, fields, related),
            )
        }
    }

    private fun searchWarehouseEntries(context: SearchContext): SourceResult {
        val q = WarehouseEntryApplications.selectAll()
        q.applyOrgScope(WarehouseEntryApplications.orgId, context.readableOrgIds)
        q.andWhere {
            allTerms(context.terms) { pattern ->
                (WarehouseEntryApplications.applicationNo like pattern) or
                    (WarehouseEntryApplications.externalEntrants like pattern) or
                    (WarehouseEntryApplications.purpose like pattern) or
                    (WarehouseEntryApplications.accessRemark like pattern)
            }
        }
        q.orderBy(WarehouseEntryApplications.createTime to SortOrder.DESC, WarehouseEntryApplications.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val title = "入库申请 ${row[WarehouseEntryApplications.applicationNo]}"
            val fields = matchedFields(context.terms,
                "申请编号" to row[WarehouseEntryApplications.applicationNo],
                "外部人员" to row[WarehouseEntryApplications.externalEntrants],
                "入库事由" to row[WarehouseEntryApplications.purpose],
                "核验备注" to row[WarehouseEntryApplications.accessRemark],
            )
            RawHit(
                id = row[WarehouseEntryApplications.id].value,
                type = "WAREHOUSE_ENTRY",
                typeLabel = "入库申请",
                title = title,
                subtitle = row[WarehouseEntryApplications.externalEntrants],
                summary = summaryOf(row[WarehouseEntryApplications.purpose], row[WarehouseEntryApplications.accessRemark]),
                matchedFields = fields,
                route = "/collection/warehouse-entry",
                occurredAt = row[WarehouseEntryApplications.createTime],
                score = relevance(context.keyword, title, row[WarehouseEntryApplications.applicationNo], fields, related = false),
            )
        }
    }

    private fun searchAnnualArchives(context: SearchContext): SourceResult {
        val currentOrg = currentAnnualSearchOrg(context) ?: return SourceResult(0, emptyList())
        val q = AnnualReportArchives.selectAll()
        q.andWhere { AnnualReportArchives.orgId eq currentOrg }
        val annualYear = CollectionUnifiedSearchPolicy.annualReportYear(context.terms)
        when {
            annualYear == 0 -> Unit
            annualYear != null -> q.andWhere { AnnualReportArchives.year eq annualYear }
            else -> q.andWhere {
                allTerms(context.terms) { pattern ->
                    (AnnualReportArchives.statistician like pattern) or
                        (AnnualReportArchives.keeperDirector like pattern) or
                        (AnnualReportArchives.museumLeader like pattern) or
                        (AnnualReportArchives.reportedBy like pattern)
                }
            }
        }
        q.orderBy(AnnualReportArchives.createTime to SortOrder.DESC, AnnualReportArchives.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val title = "${row[AnnualReportArchives.year]} 年度统计归档"
            val fields = matchedFields(context.terms,
                "统计人" to row[AnnualReportArchives.statistician],
                "保管部主任" to row[AnnualReportArchives.keeperDirector],
                "馆负责人" to row[AnnualReportArchives.museumLeader],
                "归档人" to row[AnnualReportArchives.reportedBy],
            )
            RawHit(
                id = row[AnnualReportArchives.id].value,
                type = "ANNUAL_ARCHIVE",
                typeLabel = "年度统计",
                title = title,
                summary = summaryOf(row[AnnualReportArchives.statistician], row[AnnualReportArchives.keeperDirector], row[AnnualReportArchives.museumLeader]),
                matchedFields = fields,
                route = "/collection/annual-report",
                targetYear = row[AnnualReportArchives.year],
                occurredAt = row[AnnualReportArchives.createTime],
                score = relevance(context.keyword, title, null, fields, related = false),
            )
        }
    }

    /** 正式签署、报送和备案版本均按当前馆年度页可打开的范围检索。 */
    private fun searchAnnualReports(context: SearchContext): SourceResult {
        val currentOrg = currentAnnualSearchOrg(context) ?: return SourceResult(0, emptyList())
        val year = CollectionUnifiedSearchPolicy.annualReportYear(context.terms)
        val q = AnnualReportEvidenceRecords.select(
            AnnualReportEvidenceRecords.id, AnnualReportEvidenceRecords.year, AnnualReportEvidenceRecords.version,
            AnnualReportEvidenceRecords.submission, AnnualReportEvidenceRecords.filing,
            AnnualReportEvidenceRecords.createTime,
        ).where { AnnualReportEvidenceRecords.orgId eq currentOrg }
        if (year != null && year > 0) q.andWhere { AnnualReportEvidenceRecords.year eq year }
        q.orderBy(AnnualReportEvidenceRecords.createTime to SortOrder.DESC, AnnualReportEvidenceRecords.id to SortOrder.DESC)
        val matching = q.mapNotNull { row ->
            val reportYear = row[AnnualReportEvidenceRecords.year]
            val version = row[AnnualReportEvidenceRecords.version]
            val submission = row[AnnualReportEvidenceRecords.submission]
            val filing = row[AnnualReportEvidenceRecords.filing]
            val title = "$reportYear 年度统计报表 第 $version 版"
            val fields = listOf(
                "统计年度" to reportYear.toString(),
                "年度报表" to "$reportYear 年报",
                "版本" to version.toString(),
                "签署状态" to "已签署",
                "报送状态" to (if (submission != null) "已报送" else null),
                "报送编号" to submission?.reportNo,
                "报送机关" to submission?.authorityName,
                "备案状态" to (if (filing != null) "已备案" else null),
                "备案编号" to filing?.filingNo,
                "备案机关" to filing?.authorityName,
            )
            if (context.terms.any { term -> fields.none { (_, value) -> value?.lowercase()?.contains(term) == true } })
                return@mapNotNull null
            val matched = matchedFields(context.terms, *fields.toTypedArray())
            RawHit(
                id = row[AnnualReportEvidenceRecords.id].value,
                type = "ANNUAL_REPORT",
                typeLabel = "年度报表",
                title = title,
                subtitle = when { filing != null -> "已备案"; submission != null -> "已报送"; else -> "已签署" },
                summary = summaryOf(submission?.reportNo, filing?.filingNo),
                matchedFields = matched,
                route = "/collection/annual-report",
                targetYear = reportYear,
                occurredAt = row[AnnualReportEvidenceRecords.createTime],
                score = relevance(context.keyword, title, reportYear.toString(), matched, related = false),
            )
        }
        matching.mapNotNull { it.targetYear }.toSet().forEach { reportYear ->
            val verifiedIds = annualReports.verifiedRecordIds(reportYear)
            if (matching.any { it.targetYear == reportYear && it.id !in verifiedIds })
                throw BadRequestException("年度报表检索版本与冻结原件不一致，请核对原档案")
        }
        return SourceResult(
            total = matching.size.toLong(),
            items = matching.sortedWith(compareByDescending<RawHit> { it.occurredAt ?: LocalDateTime.MIN }.thenByDescending { it.type }.thenByDescending { it.id }).take(context.fetchLimit),
        )
    }

    private fun currentAnnualSearchOrg(context: SearchContext): String? =
        context.annualOrgId

    // ── 基础资料：收藏单位、分类、打印模板 ──

    private fun foundationSources(context: SearchContext): List<SourceResult> = listOf(
        searchOrganizations(context),
        searchCategories(context),
        searchPrintTemplates(context),
    )

    private fun searchOrganizations(context: SearchContext): SourceResult {
        val q = CollectionOrgs.selectAll()
        context.readableOrgIds?.let { ids -> q.andWhere { CollectionOrgs.id inList ids } }
        q.andWhere {
            allTerms(context.terms) { pattern ->
                (CollectionOrgs.name like pattern) or
                    (CollectionOrgs.shortName like pattern) or
                    (CollectionOrgs.code like pattern) or
                    (CollectionOrgs.legalPerson like pattern) or
                    (CollectionOrgs.address like pattern) or
                    (CollectionOrgs.contact like pattern) or
                    (CollectionOrgs.phone like pattern)
            }
        }
        q.orderBy(CollectionOrgs.createTime to SortOrder.DESC, CollectionOrgs.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val title = row[CollectionOrgs.name]
            val fields = matchedFields(context.terms,
                "单位名称" to title,
                "单位简称" to row[CollectionOrgs.shortName],
                "单位代码" to row[CollectionOrgs.code],
                "法定代表人" to row[CollectionOrgs.legalPerson],
                "地址" to row[CollectionOrgs.address],
                "联系人" to row[CollectionOrgs.contact],
                "联系电话" to row[CollectionOrgs.phone],
            )
            RawHit(
                id = row[CollectionOrgs.id].value,
                type = "ORGANIZATION",
                typeLabel = "收藏单位",
                title = title,
                subtitle = row[CollectionOrgs.code],
                summary = summaryOf(row[CollectionOrgs.address], row[CollectionOrgs.contact], row[CollectionOrgs.phone]),
                matchedFields = fields,
                route = "/collection/org",
                occurredAt = row[CollectionOrgs.createTime],
                score = relevance(context.keyword, title, row[CollectionOrgs.code], fields, related = false),
            )
        }
    }

    private fun searchCategories(context: SearchContext): SourceResult {
        val q = CollectionCategories.selectAll()
        context.readableOrgIds?.let { ids ->
            q.andWhere { (CollectionCategories.orgId eq null) or (CollectionCategories.orgId inList ids) }
        }
        q.andWhere {
            allTerms(context.terms) { pattern ->
                (CollectionCategories.name like pattern) or (CollectionCategories.code like pattern)
            }
        }
        q.orderBy(CollectionCategories.createTime to SortOrder.DESC, CollectionCategories.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val title = row[CollectionCategories.name]
            val fields = matchedFields(context.terms,
                "分类名称" to title,
                "分类代码" to row[CollectionCategories.code],
            )
            RawHit(
                id = row[CollectionCategories.id].value,
                type = "CATEGORY",
                typeLabel = "文物分类",
                title = title,
                subtitle = row[CollectionCategories.code],
                matchedFields = fields,
                route = "/collection/category",
                occurredAt = row[CollectionCategories.createTime],
                score = relevance(context.keyword, title, row[CollectionCategories.code], fields, related = false),
            )
        }
    }

    private fun searchPrintTemplates(context: SearchContext): SourceResult {
        val q = CollectionPrintTemplates.selectAll()
        q.applyOrgScope(CollectionPrintTemplates.orgId, context.readableOrgIds)
        q.andWhere {
            allTerms(context.terms) { pattern ->
                (CollectionPrintTemplates.name like pattern) or
                    (CollectionPrintTemplates.title like pattern) or
                    (CollectionPrintTemplates.documentLabel like pattern) or
                    (CollectionPrintTemplates.collectionTitle like pattern) or
                    (CollectionPrintTemplates.signatures like pattern) or
                    (CollectionPrintTemplates.remark like pattern)
            }
        }
        q.orderBy(CollectionPrintTemplates.createTime to SortOrder.DESC, CollectionPrintTemplates.id to SortOrder.DESC)
        return querySource(q, context.fetchLimit) { row ->
            val title = row[CollectionPrintTemplates.name]
            val fields = matchedFields(context.terms,
                "模板名称" to title,
                "表单标题" to row[CollectionPrintTemplates.title],
                "单号字段" to row[CollectionPrintTemplates.documentLabel],
                "清单标题" to row[CollectionPrintTemplates.collectionTitle],
                "签字栏" to row[CollectionPrintTemplates.signatures],
                "备注" to row[CollectionPrintTemplates.remark],
            )
            RawHit(
                id = row[CollectionPrintTemplates.id].value,
                type = "PRINT_TEMPLATE",
                typeLabel = "打印模板",
                title = title,
                subtitle = row[CollectionPrintTemplates.title],
                summary = summaryOf(row[CollectionPrintTemplates.documentLabel], row[CollectionPrintTemplates.collectionTitle], row[CollectionPrintTemplates.signatures]),
                matchedFields = fields,
                route = "/collection/print-template",
                occurredAt = row[CollectionPrintTemplates.createTime],
                score = relevance(context.keyword, title, row[CollectionPrintTemplates.title], fields, related = false),
            )
        }
    }

    // ── 查询与结果装配 ──

    private fun findMatchedCollections(terms: List<String>, orgIds: Set<String>): Map<String, MatchedCollection> {
        val q = collectionMatchQuery(terms, orgIds)
        return q.associate { row ->
            val id = row[Collections.id].value
            id to MatchedCollection(
                id = id,
                orgId = row[Collections.orgId],
                warehouseId = row[Collections.warehouseId],
            )
        }
    }

    private fun collectionMatchQuery(terms: List<String>, orgIds: Set<String>?): Query {
        val q = Collections.selectAll()
        q.applyOrgScope(Collections.orgId, orgIds)
        q.andWhere {
            allTerms(terms) { pattern ->
                (Collections.name like pattern) or
                    (Collections.originalName like pattern) or
                    (Collections.totalRegNo like pattern) or
                    (Collections.classNo like pattern) or
                    (Collections.auxNo like pattern) or
                    (Collections.era like pattern) or
                    (Collections.texture like pattern) or
                    (Collections.region like pattern) or
                    (Collections.country like pattern) or
                    (Collections.culture like pattern) or
                    (Collections.sourceDesc like pattern) or
                    (Collections.sourceNo like pattern) or
                    (Collections.inscription like pattern) or
                    (Collections.feature like pattern) or
                    (Collections.completeStatus like pattern) or
                    (Collections.author like pattern) or
                    (Collections.edition like pattern) or
                    (Collections.volumeNo like pattern) or
                    (Collections.rfidTag like pattern) or
                    (Collections.entryVoucherNo like pattern) or
                    (Collections.deregVoucherNo like pattern) or
                    (Collections.remark like pattern)
            }
        }
        return q
    }

    private fun businessIdsForMatchedCollections(context: SearchContext, bizType: String): Set<String> {
        val collectionIds = context.matchedCollections.keys
        if (collectionIds.isEmpty()) return emptySet()
        val q = CollectionBusinessItems.selectAll().where {
            (CollectionBusinessItems.bizType eq bizType) and
                (CollectionBusinessItems.collectionId inList collectionIds)
        }
        q.applyOrgScope(CollectionBusinessItems.orgId, context.readableOrgIds)
        return q.map { it[CollectionBusinessItems.bizId] }.toSet()
    }

    private fun loadCollectionRefs(ids: Set<String>, orgIds: Set<String>): Map<String, CollectionSearchCollectionRefVo> {
        if (ids.isEmpty()) return emptyMap()
        val q = Collections.selectAll().where { Collections.id inList ids }
        q.applyOrgScope(Collections.orgId, orgIds)
        return q.associate { row ->
            val id = row[Collections.id].value
            id to CollectionSearchCollectionRefVo(
                id = id,
                totalRegNo = row[Collections.totalRegNo],
                name = row[Collections.name],
                coverImage = row[Collections.coverImage],
            )
        }
    }

    private fun querySource(query: Query, limit: Int, mapper: (ResultRow) -> RawHit): SourceResult {
        val total = query.count()
        val items = query.limit(limit).map(mapper)
        return SourceResult(total = total, items = items)
    }

    private fun Query.applyOrgScope(column: Column<String>, orgIds: Set<String>?) {
        orgIds?.let { ids -> andWhere { column inList ids } }
    }

    private fun allTerms(terms: List<String>, matcher: (String) -> Op<Boolean>): Op<Boolean> =
        terms.map { matcher("%$it%") }.reduce { left, right -> left and right }

    private fun directOrRelated(direct: Op<Boolean>, related: Op<Boolean>): Op<Boolean> = direct or related

    private fun idIn(column: Column<String>, ids: Set<String>): Op<Boolean> =
        if (ids.isEmpty()) Op.FALSE else column inList ids

    @JvmName("nullableStringIdIn")
    private fun idIn(column: Column<String?>, ids: Set<String>): Op<Boolean> =
        if (ids.isEmpty()) Op.FALSE else column inList ids

    @JvmName("entityIdIn")
    private fun idIn(column: Column<org.jetbrains.exposed.v1.core.dao.id.EntityID<String>>, ids: Set<String>): Op<Boolean> =
        if (ids.isEmpty()) Op.FALSE else column inList ids

    private fun matchedOrRelated(
        terms: List<String>,
        related: Boolean,
        vararg fields: Pair<String, Any?>,
    ): List<String> = if (related) listOf("关联藏品") else matchedFields(terms, *fields)

    private fun matchedFields(terms: List<String>, vararg fields: Pair<String, Any?>): List<String> =
        fields.filter { (_, value) ->
            val normalized = value?.toString()?.lowercase().orEmpty()
            normalized.isNotBlank() && terms.any(normalized::contains)
        }.map { it.first }.distinct()

    private fun summaryOf(vararg values: Any?): String? {
        val text = values.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(" · ")
        if (text.isBlank()) return null
        return if (text.length <= SUMMARY_TEXT_LIMIT) text else text.take(SUMMARY_TEXT_LIMIT).trimEnd() + "…"
    }

    private fun relevance(
        keyword: String,
        title: String?,
        identity: String?,
        matchedFields: List<String>,
        related: Boolean,
    ): Int {
        val normalizedKeyword = keyword.lowercase()
        val normalizedTitle = title?.lowercase().orEmpty()
        val normalizedIdentity = identity?.lowercase().orEmpty()
        return when {
            normalizedTitle == normalizedKeyword || normalizedIdentity == normalizedKeyword -> 100
            normalizedTitle.startsWith(normalizedKeyword) || normalizedIdentity.startsWith(normalizedKeyword) -> 80
            normalizedTitle.contains(normalizedKeyword) || normalizedIdentity.contains(normalizedKeyword) -> 60
            !related && matchedFields.isNotEmpty() -> 40
            related -> 20
            else -> 0
        }
    }

    private fun splitIds(value: String?): List<String> = value.orEmpty()
        .split(',', '，', ';', '；')
        .map { it.trim() }
        .filter(String::isNotBlank)
        .distinct()

    private data class SearchContext(
        val keyword: String,
        val recordType: String?,
        val terms: List<String>,
        val readableOrgIds: Set<String>?,
        val annualOrgId: String?,
        val acquisitionConfidential: Boolean,
        val matchedCollections: Map<String, MatchedCollection>,
        val fetchLimit: Int,
    )

    private data class MatchedCollection(
        val id: String,
        val orgId: String,
        val warehouseId: String?,
    )

    private data class SourceResult(
        val total: Long,
        val items: List<RawHit>,
    )

    private data class RawGroup(
        val definition: GroupDefinition,
        val total: Long,
        val items: List<RawHit>,
    )

    private data class RawHit(
        val id: String,
        val type: String,
        val typeLabel: String,
        val title: String,
        val fileId: String? = null,
        val subtitle: String? = null,
        val summary: String? = null,
        val matchedFields: List<String> = emptyList(),
        val collectionIds: List<String> = emptyList(),
        val route: String,
        val targetId: String? = null,
        val targetYear: Int? = null,
        val occurredAt: LocalDateTime? = null,
        val score: Int = 0,
    ) {
        fun toVo(collectionRefs: Map<String, CollectionSearchCollectionRefVo>): CollectionSearchItemVo {
            val refs = collectionIds.mapNotNull(collectionRefs::get).distinctBy { it.id }
            return CollectionSearchItemVo(
                id = id,
                type = type,
                typeLabel = typeLabel,
                title = title,
                fileId = fileId,
                subtitle = subtitle,
                summary = summary,
                matchedFields = matchedFields,
                relatedCollections = refs.take(MAX_RELATED_COLLECTIONS),
                relatedCollectionCount = refs.size,
                route = route,
                targetId = targetId,
                targetYear = targetYear,
                occurredAt = occurredAt,
            )
        }
    }

    private data class GroupDefinition(
        val key: String,
        val label: String,
        val description: String,
    )

    private companion object {
        const val GROUP_ARCHIVE = "ARCHIVE"
        const val GROUP_ACQUISITION = "ACQUISITION"
        const val GROUP_CIRCULATION = "CIRCULATION"
        const val GROUP_STORAGE = "STORAGE"
        const val GROUP_FOUNDATION = "FOUNDATION"
        const val SUMMARY_SIZE = 6
        const val MAX_PAGE_SIZE = 50
        const val MAX_RELATED_COLLECTIONS = 4
        const val SUMMARY_TEXT_LIMIT = 280

        val GROUP_ORDER = listOf(
            GROUP_ARCHIVE,
            GROUP_ACQUISITION,
            GROUP_CIRCULATION,
            GROUP_STORAGE,
            GROUP_FOUNDATION,
        )
        val GROUPS = listOf(
            GroupDefinition(GROUP_ARCHIVE, "藏品档案", "总账、编目、影像、附件、套件与登记留痕"),
            GroupDefinition(GROUP_ACQUISITION, "征集登记", "征集计划、征集申请、入馆凭证与鉴定定级"),
            GroupDefinition(GROUP_CIRCULATION, "流转利用", "提用、借展、归还、展览、复制、调拨与退出馆藏"),
            GroupDefinition(GROUP_STORAGE, "库房管理", "出入库、盘点、库位、库房日志与入库申请"),
            GroupDefinition(GROUP_FOUNDATION, "基础资料", "收藏单位、文物分类与打印模板"),
        ).associateBy { it.key }
    }
}

internal object CollectionUnifiedSearchPolicy {
    private const val MAX_KEYWORD_LENGTH = 100

    /** 可识别的年度先在 SQL 中缩小范围；0 表示年报语义词不限年度，null 交由当前馆凭据元数据匹配。 */
    fun annualReportYear(terms: List<String>): Int? {
        val yearTerm = Regex("^((?:19|20)\\d{2})(?:年|年度|年度统计|年度报表|年报)?$")
        val years = terms.mapNotNull { yearTerm.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.distinct()
        if (years.size > 1) return null
        val labels = listOf("年度统计", "年度报表", "年报", "报送", "备案")
        if (terms.any { term -> yearTerm.matches(term).not() && labels.none { it.contains(term) } }) return null
        return years.singleOrNull() ?: 0
    }

    fun normalizeKeyword(value: String?): String {
        val clean = value?.trim()?.replace(Regex("\\s+"), " ")
            ?.replace("%", "")
            ?.replace("_", "")
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.takeIf(String::isNotBlank)
            ?: throw BadRequestException("请输入搜索关键字")
        if (clean.length > MAX_KEYWORD_LENGTH) throw BadRequestException("搜索关键字不能超过 $MAX_KEYWORD_LENGTH 个字符")
        return clean
    }

    fun terms(keyword: String): List<String> = keyword
        .lowercase()
        .split(' ')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
}
