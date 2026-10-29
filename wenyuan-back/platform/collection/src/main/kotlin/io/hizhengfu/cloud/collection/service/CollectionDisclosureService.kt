package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.LocalDate

@Component
@Transactional
class CollectionDisclosureService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
    private val sourceProviders: List<CollectionDisclosureSourceProvider> = emptyList(),
) {
    companion object { const val BIZ = "COLLECTION_PUBLIC_DISCLOSURE" }

    private val readAuthorization = CollectionFileReadAuthorization(scope, CollectionActorDirectory(scope))

    fun detail(orgId: String, subject: EnumDisclosureSubject, id: String): Map<String, Any?> {
        access(orgId)
        val source = source(orgId, subject, id)
        val rows = reviews(orgId, subject, id)
        if (source == null && rows.isEmpty()) fail("本馆公开对象及历史核查记录不存在")
        val latest = rows.lastOrNull()
        val eventsByReview = rows.associate { row -> row.id.value to events(row.id.value).also { values ->
            if (values.any { it.orgId != orgId }) fail("实际公开或撤下记录所属馆与核查版本不一致，请核对原件")
        } }
        val events = eventsByReview.values.flatten()
        val active = events.filter { e -> e.content.action == EnumDisclosureEvent.PUBLISHED && events.none { it.content.revokesPublicationId == e.id.value } }
        return mapOf("orgId" to orgId, "subject" to subject, "subjectId" to id, "name" to source?.name,
            "sourceRevision" to source?.revision, "sourceReady" to (source?.ready == true), "expertOpinionIds" to source?.expertIds, "statutorySubject" to (subject in statutorySubjects),
            "records" to rows.map { row -> mapOf("id" to row.id.value, "version" to row.version, "content" to row.content,
                "matchesCurrentSource" to matches(row, source), "feedEnabledAt" to row.feedEnabledAt, "feedStoppedAt" to row.feedStoppedAt,
                "feedAvailable" to (row == latest && canFeed(row, source)), "archives" to archives.readForDisplay(BIZ, row.id.value, orgId).archives,
                "events" to eventsByReview.getValue(row.id.value).map { e -> eventView(e, orgId) }) },
            "activePublications" to active.map { e -> eventView(e, orgId) + mapOf("needsUpdate" to (latest == null || e.reviewId != latest.id.value || !matches(latest, source) || latest.content.decision != EnumDisclosureDecision.PUBLIC)) },
            "statutoryNotices" to when (subject) {
                EnumDisclosureSubject.EXIT_CONSULTATION, EnumDisclosureSubject.EXIT_RESULT -> DeregistrationNoticeDao.find { (DeregistrationNotices.deregistrationId eq id) and (DeregistrationNotices.orgId eq orgId) }.map { it.content.copy(id=it.id.value) }.filter { it.kind == if(subject==EnumDisclosureSubject.EXIT_CONSULTATION) EnumExitNoticeKind.CONSULTATION else EnumExitNoticeKind.RESULT }
                EnumDisclosureSubject.TRANSFER_RESULT -> CollectionTransferNoticeDao.find { (CollectionTransferNotices.transferId eq id) and (CollectionTransferNotices.orgId eq orgId) }.map { it.content.copy(id=it.id.value) }
                else -> emptyList<DeregistrationNoticeVo>()
            },
            "recommendedChannels" to when (subject) {
                EnumDisclosureSubject.EXIT_CONSULTATION -> listOf(EnumDisclosureChannel.AUTHORITY_WEBSITE, EnumDisclosureChannel.MUSEUM_SITE, EnumDisclosureChannel.OFFICIAL_WEBSITE)
                EnumDisclosureSubject.ACQUISITION -> listOf(EnumDisclosureChannel.OFFICIAL_WEBSITE, EnumDisclosureChannel.ANNUAL_REPORT, EnumDisclosureChannel.MEDIA)
                else -> listOf(EnumDisclosureChannel.MUSEUM_SITE, EnumDisclosureChannel.OFFICIAL_WEBSITE)
            })
    }

    fun requireArchivedOriginal(orgId:String,subject:EnumDisclosureSubject,id:String,recordId:String,archiveId:String,fileId:String) {
        if (listOf(orgId,id,recordId,archiveId,fileId).any { it.isBlank() || it.length>100 }) fail("公开核查归档原件标识不正确")
        @Suppress("UNCHECKED_CAST")
        val rows=detail(orgId,subject,id)["records"] as List<Map<String,Any?>>
        val record=rows.singleOrNull { it["id"]==recordId } ?: fail("该公开核查版本不属于当前对象")
        @Suppress("UNCHECKED_CAST")
        val originalArchives=(record["archives"] as List<Map<String,Any?>>).toMutableList()
        @Suppress("UNCHECKED_CAST")
        val events=record["events"] as List<Map<String,Any?>>
        events.forEach { event ->
            @Suppress("UNCHECKED_CAST")
            originalArchives.addAll(event["archives"] as List<Map<String,Any?>>)
        }
        val archive=originalArchives.singleOrNull { it["id"]==archiveId }
            ?: fail("该永久档案不属于所选公开核查版本")
        @Suppress("UNCHECKED_CAST")
        val files=archive["files"] as? List<Map<String,Any?>> ?: fail("公开核查归档文件清单无效")
        if (files.none { it["fileId"]==fileId }) fail("该文件不是所选公开永久档案的原件")
    }

    fun review(orgId: String, subject: EnumDisclosureSubject, id: String, value: CollectionDisclosureReviewVo): String {
        lockForWriting(orgId)
        val source = source(orgId, subject, id, lock = true) ?: fail("本馆公开对象不存在或已转出，请从原公开版本登记后续更新处置")
        if (subject == EnumDisclosureSubject.EXIT_CONSULTATION) {
            val request = DeregistrationDao[id]
            if (request.flowInstanceId != null || request.executedAt != null || CollectionAdministrativeRecordDao.find { (CollectionAdministrativeRecords.bizType eq CollectionConst.BIZ_DEREGISTER) and (CollectionAdministrativeRecords.bizId eq id) }.any()) fail("已进入行政或馆内办理，公示依据已固定；不能另换公示稿绕过原办理依据")
        }
        val previous = reviews(orgId, subject, id).lastOrNull()
        if (previous?.content == value && (value.decision != EnumDisclosureDecision.PUBLIC || source.ready)) {
            requireReviewed(previous)
            return previous.id.value
        }
        if (value.sourceRevision != source.revision) fail("业务资料已变化，请重新核查本次公开内容，不能使用旧页面提交")
        if (value.reviewedAt.isAfter(LocalDateTime.now()) || source.effectiveAt?.isAfter(value.reviewedAt) == true) fail("须按当前对应资料填写实际核查时间，不能预填未来事实")
        users.requireMuseumMember(value.reviewerUserId, orgId)
        if (value.signedReviewFileIds.isBlank() || value.restrictions.keys != EnumDisclosureRestriction.entries.toSet() || value.restrictions.values.any { it.explanation.isBlank() }) fail("须逐项核实国家秘密、商业秘密、工作秘密和个人隐私，并留存实际核查签署原件")
        if (previous != null && (value.reviewedAt.isBefore(previous.content.reviewedAt) || value.replacementReason.isNullOrBlank() || value.replacementFileIds.isNullOrBlank())) fail("公开内容或不公开依据订正须追加新版本、原因及本次核查原件")
        if (value.decision == EnumDisclosureDecision.PUBLIC) {
            if (!source.ready) fail("须先核实正式登记或征集实际履行资料，不能把未完成事项作为公开事实")
            if (value.restrictions.values.any { it.handling == EnumDisclosureHandling.WITHHELD }) fail("存在不得公开的内容时，请提供已脱敏的公开版本或记录整体不公开依据")
            val content = value.content ?: fail("请填写实际核查的公开内容")
            if (value.publicContentFileIds.isNullOrBlank()) fail("请留存与公开文字和图片一致的公开稿原件")
            validatePublic(content, subject)
            if (subject == EnumDisclosureSubject.EXIT_CONSULTATION) {
                if (content.expertOpinions.map { it.sourceOpinionId }.toSet() != source.expertIds.toSet() || content.expertOpinions.size != source.expertIds.size || content.expertOpinions.any { !it.agree || it.publicExpertLabel.isBlank() || it.opinion.isBlank() }) fail("公示稿须逐一对应全部馆外专家的独立意见和同意结论，不得遗漏、合并或替换为汇总意见")
            } else if (content.expertOpinions.isNotEmpty()) fail("独立专家公示意见仅用于退出前法定公示，不得混入其他类型公开稿")
        } else {
            if (value.content != null || !value.publicContentFileIds.isNullOrBlank() || value.withholdingReason.isNullOrBlank() || value.restrictions.values.none { it.handling == EnumDisclosureHandling.WITHHELD }) fail("整体不公开须记录适用保密类别和实际依据，不能附带待公开正文")
        }
        val row = CollectionDisclosureReviewDao.new { this.orgId = orgId; this.subject = subject; subjectId = id; version = (previous?.version ?: 0) + 1; content = value }
        archives.seal(orgId, BIZ, row.id.value, "REVIEWED", snapshot(row), reviewFiles(value))
        return row.id.value
    }

    fun enable(orgId: String, reviewId: String): String {
        lockForWriting(orgId)
        val row = row(orgId, reviewId)
        val source = source(orgId, row.subject, row.subjectId, lock = true)
        if (reviews(orgId, row.subject, row.subjectId).lastOrNull()?.id != row.id || !matches(row, source) || row.content.decision != EnumDisclosureDecision.PUBLIC) fail("只能提供本馆当前资料对应的最新已核查公开稿")
        requireReviewed(row)
        if (row.feedStoppedAt != null) fail("本版已停止供稿，请核查新的公开版本后再启用")
        if (row.feedEnabledAt != null) {
            if (!phaseIntact(reviewId, "FEED_ENABLED", mapOf("reviewId" to reviewId, "enabledAt" to row.feedEnabledAt), orgId)) fail("缺少该版实际启用供稿的留档记录，请核实")
            return reviewId
        }
        row.feedEnabledAt = LocalDateTime.now()
        if (row.subject == EnumDisclosureSubject.RESOURCE) CollectionDao[row.subjectId].apply { published = true; publishedAt = row.feedEnabledAt }
        archives.seal(orgId, BIZ, reviewId, "FEED_ENABLED", mapOf("reviewId" to reviewId, "enabledAt" to row.feedEnabledAt), null)
        return reviewId
    }
    fun stop(orgId: String, reviewId: String, value: DisclosureFeedStopVo): String {
        lockForWriting(orgId)
        val row = row(orgId, reviewId)
        if (value.reason.isBlank()) fail("请说明停止向 CMS 供稿的原因")
        if (row.feedStoppedAt != null) {
            if (!phaseIntact(reviewId, "FEED_STOPPED", mapOf("stoppedAt" to row.feedStoppedAt, "reason" to value.reason), orgId))
                fail("本版停止供稿原件缺失或与原记录不一致，不能以重复登记补造")
            return reviewId
        }
        row.feedStoppedAt = LocalDateTime.now()
        archives.seal(orgId, BIZ, reviewId, "FEED_STOPPED", mapOf("stoppedAt" to row.feedStoppedAt, "reason" to value.reason), null)
        return reviewId
    }
    fun enableLatestResource(id: String): String {
        val c = CollectionDao.findById(id) ?: fail("藏品不存在")
        access(c.orgId, true)
        val latest = reviews(c.orgId, EnumDisclosureSubject.RESOURCE, id).lastOrNull() ?: fail("请先核查脱敏公开稿；旧发布标志不能代替公开内容核查")
        enable(c.orgId, latest.id.value)
        return id
    }

    fun event(orgId: String, reviewId: String, value: CollectionDisclosureEventVo): String {
        lockForWriting(orgId)
        val row = row(orgId, reviewId)
        requireReviewed(row)
        if (row.subject in statutorySubjects) fail("法定公示和公告须从对应退出或交换调拨单登记，不能绕过三渠道及期限校验")
        events(reviewId).firstOrNull { it.content == value }?.let { event ->
            val original = CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq event.id.value) and
                    (CollectionPermanentArchives.phase eq value.action.name)
            }.singleOrNull() ?: fail("实际公开或撤下的原始凭据缺失，不能以重复登记补造")
            if (event.orgId != orgId) fail("实际公开或撤下记录不属于本馆")
            archives.verifyExisting(original, orgId, BIZ, event.id.value, value.action.name,
                mapOf("reviewId" to reviewId, "content" to value),
                listOfNotNull(value.evidenceFileIds, value.publicContentFileIds).joinToString(","))
            return event.id.value
        }
        if (value.occurredAt.isAfter(LocalDateTime.now()) || value.occurredAt.isBefore(row.content.reviewedAt) || value.location.isBlank() || value.evidenceFileIds.isBlank()) fail("请依据实际公开或撤下原件填写渠道位置、发生时间和凭据")
        users.requireMuseumMember(value.recorderUserId, orgId)
        if (value.channel == EnumDisclosureChannel.OFFICIAL_WEBSITE) requireHttpUrl(value.location)
        if (value.action == EnumDisclosureEvent.PUBLISHED) {
            if (row.content.decision != EnumDisclosureDecision.PUBLIC || value.revokesPublicationId != null || files(value.publicContentFileIds) != files(row.content.publicContentFileIds)) fail("实际公开须对应本版已核查公开稿原件，不能用其他内容或不公开依据替代")
        } else {
            val prior = value.revokesPublicationId?.let { CollectionDisclosureEventDao.findById(it) } ?: fail("撤下记录须对应一条实际公开记录")
            if (prior.orgId != orgId || prior.reviewId != reviewId || prior.content.action != EnumDisclosureEvent.PUBLISHED || prior.content.channel != value.channel || prior.content.location != value.location || prior.content.occurredAt.isAfter(value.occurredAt) || value.reason.isNullOrBlank()) fail("请核实同一版本、渠道和位置的实际撤下时间、原因及凭据")
            if (events(reviewId).any { it.content.revokesPublicationId == prior.id.value }) fail("该次实际公开已有撤下原件，不能覆盖")
            if (!value.publicContentFileIds.isNullOrBlank()) fail("撤下凭据不应作为新的公开稿")
        }
        val event = CollectionDisclosureEventDao.new { this.orgId = orgId; this.reviewId = reviewId; content = value }
        archives.seal(orgId, BIZ, event.id.value, value.action.name, mapOf("reviewId" to reviewId, "content" to value), listOfNotNull(value.evidenceFileIds, value.publicContentFileIds).joinToString(","))
        return event.id.value
    }

    /** CMS must replace by (orgId, subject, id), including removal instructions. No private review data. */
    fun feed(subject: EnumDisclosureSubject? = null): List<CollectionPublicFeedVo> {
        val visible = CollectionSearchReadAccess.require(scope, additionalPermissions = setOf(CollectionConst.PUBLISH), allowHistoricalOrgs = true).orgIds
        val query = CollectionDisclosureReviews.selectAll()
        query.andWhere { CollectionDisclosureReviews.orgId inList visible }
        subject?.let { query.andWhere { CollectionDisclosureReviews.subject eq it } }
        val rows = CollectionDisclosureReviewDao.wrapRows(query).toList()
        val output = rows.groupBy { Triple(it.orgId, it.subject, it.subjectId) }.map { (key, versions) ->
            val row = versions.maxBy { it.version }
            val available = canFeed(row, source(key.first, key.second, key.third))
            CollectionPublicFeedVo(key.third, key.first, key.second, available, row.id.value, row.version, row.content.content.takeIf { available })
        }.toMutableList()
        // Legacy flags must issue a removal instruction until a real reviewed version exists.
        if (subject == null || subject == EnumDisclosureSubject.RESOURCE) {
            val legacy = Collections.selectAll().where { Collections.published eq true }
            legacy.andWhere { Collections.orgId inList visible }
            CollectionDao.wrapRows(legacy).forEach { c ->
                if (output.none { it.orgId == c.orgId && it.subject == EnumDisclosureSubject.RESOURCE && it.id == c.id.value }) output += CollectionPublicFeedVo(c.id.value, c.orgId, EnumDisclosureSubject.RESOURCE, false)
            }
        }
        return output.sortedWith(compareBy({ it.orgId }, { it.subject.name }, { it.id }))
    }

    /** 只授权已明确启用的最新公开副本；签署、核查依据和实发凭据仍按内部原件查阅。 */
    fun isPublicArchiveFile(reference: CollectionArchiveFileReference): Boolean {
        if (reference.businessType != BIZ) return false
        val archive = CollectionPermanentArchiveDao.findById(reference.archiveId) ?: return false
        if (archive.orgId != reference.orgId || archive.bizType != BIZ || archive.bizId != reference.businessId || archive.phase != reference.phase) return false
        val origin = when (reference.phase) {
            "REVIEWED" -> CollectionDisclosureReviewDao.findById(reference.businessId)?.takeIf { row ->
                row.orgId == reference.orgId && reviewedIntact(row) && publicOnly(archive, reference.fileId,
                    row.content.publicContentFileIds, listOfNotNull(row.content.signedReviewFileIds, row.content.replacementFileIds).joinToString(","))
            }
            EnumDisclosureEvent.PUBLISHED.name -> {
                val event = CollectionDisclosureEventDao.findById(reference.businessId) ?: return false
                if (event.orgId != reference.orgId || event.content.action != EnumDisclosureEvent.PUBLISHED ||
                    !archiveIntact(archive, event.orgId, BIZ, event.id.value, event.content.action.name,
                        mapOf("reviewId" to event.reviewId, "content" to event.content),
                        listOfNotNull(event.content.evidenceFileIds, event.content.publicContentFileIds).joinToString(","))) return false
                if (!publicOnly(archive, reference.fileId, event.content.publicContentFileIds, event.content.evidenceFileIds)) return false
                CollectionDisclosureReviewDao.findById(event.reviewId)?.takeIf { it.orgId == reference.orgId }
            }
            else -> null
        } ?: return false
        return isCurrentPublicFile(origin, reference.fileId)
    }

    /** 法定公示案卷仅可开放已核查的公开稿，实发凭据和其他退出/调拨原件不随之开放。 */
    fun isPublicNoticeArchiveFile(reference: CollectionArchiveFileReference): Boolean {
        if (!reference.phase.startsWith("NOTICE_")) return false
        val noticeId = reference.phase.removePrefix("NOTICE_")
        val notice = when (reference.businessType) {
            CollectionConst.BIZ_DEREGISTER -> DeregistrationNoticeDao.findById(noticeId)?.takeIf { it.orgId == reference.orgId && it.deregistrationId == reference.businessId }?.content
            CollectionConst.BIZ_TRANSFER -> CollectionTransferNoticeDao.findById(noticeId)?.takeIf { it.orgId == reference.orgId && it.transferId == reference.businessId }?.content
            else -> null
        } ?: return false
        val archive = CollectionPermanentArchiveDao.findById(reference.archiveId) ?: return false
        if (archive.orgId != reference.orgId || archive.bizType != reference.businessType || archive.bizId != reference.businessId ||
            !archiveIntact(archive, reference.orgId, reference.businessType, reference.businessId, reference.phase, notice,
                "${notice.publicContentFileIds},${notice.publicationEvidenceFileIds}")) return false
        if (!publicOnly(archive, reference.fileId, notice.publicContentFileIds, notice.publicationEvidenceFileIds)) return false
        val review = notice.disclosureReviewId?.let { CollectionDisclosureReviewDao.findById(it) } ?: return false
        val subject = if (reference.businessType == CollectionConst.BIZ_TRANSFER) EnumDisclosureSubject.TRANSFER_RESULT
            else if (notice.kind == EnumExitNoticeKind.CONSULTATION) EnumDisclosureSubject.EXIT_CONSULTATION else EnumDisclosureSubject.EXIT_RESULT
        return review.orgId == reference.orgId && review.subjectId == reference.businessId && review.subject == subject && isCurrentPublicFile(review, reference.fileId)
    }

    private fun isCurrentPublicFile(origin: CollectionDisclosureReviewDao, fileId: String): Boolean {
        if (!reviewedIntact(origin)) return false
        val latest = reviews(origin.orgId, origin.subject, origin.subjectId).lastOrNull() ?: return false
        val org = CollectionOrgDao.findById(origin.orgId) ?: return false
        if (org.status != io.hizhengfu.cloud.common.domain.EnumContentStatus.NORMAL || org.orgStatus != EnumOrgStatus.ENABLED ||
            !canFeed(latest, source(latest.orgId, latest.subject, latest.subjectId))) return false
        val reviewed = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq BIZ) and
            (CollectionPermanentArchives.bizId eq latest.id.value) and (CollectionPermanentArchives.phase eq "REVIEWED") }.singleOrNull() ?: return false
        return publicOnly(reviewed, fileId, latest.content.publicContentFileIds,
            listOfNotNull(latest.content.signedReviewFileIds, latest.content.replacementFileIds).joinToString(","))
    }

    private fun publicOnly(archive: CollectionPermanentArchiveDao, fileId: String, publicFiles: String?, privateFiles: String?): Boolean {
        val refs = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.toList()
        fun ids(raw: String?): Set<String> = BusinessAttachmentBindingPlanner.plan(archive.bizId, "公开原件", raw).map { item ->
            refs.firstOrNull { it.fileId == item.fileId }?.fileId ?: refs.filter { ref ->
                val metadata = JsonUtils.mapper.readTree(ref.metadata)
                io.hizhengfu.cloud.common.file.FileReadPath.key(metadata.path("path").asString(), metadata.path("storageType").asString()) ==
                    io.hizhengfu.cloud.common.file.FileReadPath.key(item.fileId, metadata.path("storageType").asString())
            }.singleOrNull()?.fileId ?: fail("公开原件用途与归档文件不一致，请核对原件")
        }.toSet()
        return fileId in ids(publicFiles) && fileId !in ids(privateFiles)
    }

    private fun validatePublic(content: CollectionPublicContentVo, subject: EnumDisclosureSubject) {
        if (content.title.isBlank() || content.body.isBlank() || content.feedbackInstructions.isBlank() || content.facts.values.any(String::isBlank)) fail("公开标题、正文、公众监督反馈方式及所选字段内容须明确填写")
        if (subject == EnumDisclosureSubject.ACQUISITION && (content.facts[EnumPublicFact.MANAGEMENT].isNullOrBlank() || content.facts[EnumPublicFact.USE].isNullOrBlank())) fail("征集信息须明确公开管理和使用情况，尚未使用也应按实际情况说明")
        if (subject in statutorySubjects && content.facts[EnumPublicFact.NAME].isNullOrBlank()) fail("法定公示或公告须明确藏品基本情况")
        if (subject == EnumDisclosureSubject.EXIT_CONSULTATION && content.facts[EnumPublicFact.DISPOSITION_PLAN].isNullOrBlank()) fail("退出前公示须包含实际退出处置方案")
        if (subject in setOf(EnumDisclosureSubject.EXIT_RESULT, EnumDisclosureSubject.TRANSFER_RESULT) && (content.facts[EnumPublicFact.ACCOUNT_DISPOSITION].isNullOrBlank() || content.facts[EnumPublicFact.OBJECT_DISPOSITION].isNullOrBlank())) fail("退出结果公告须分别说明账目及实物处置情况")
        val values = listOf(content.title, content.body, content.feedbackInstructions) + content.facts.values + content.expertOpinions.flatMap { listOf(it.publicExpertLabel, it.opinion) }
        if (values.any { Regex("<[/!]?[A-Za-z][^>]*>").containsMatchIn(it) }) fail("公开内容请使用纯文本，不能包含可执行或嵌入 HTML")
        content.imageUrls.forEach(::requireHttpUrl)
    }
    private fun requireHttpUrl(value: String) {
        val uri = runCatching { URI(value) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.rawUserInfo != null) fail("请填写不含账号凭据的公开 HTTP 或 HTTPS 地址")
    }
    private fun source(orgId: String, subject: EnumDisclosureSubject, id: String, lock: Boolean = false): CollectionDisclosureSource? {
        if (subject == EnumDisclosureSubject.RESOURCE) {
            val query = CollectionDao.find { (Collections.id eq id) and (Collections.orgId eq orgId) }
            val c = (if (lock) query.forUpdate() else query).singleOrNull() ?: return null
            val version = CollectionCatalogVersionDao.find { (CollectionCatalogVersions.orgId eq orgId) and (CollectionCatalogVersions.collectionId eq id) and (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED) }.maxByOrNull { it.version }
            val ready = c.registered && c.catalogStatus == EnumCatalogStatus.APPROVED && c.lifeStatus != EnumCollectionStatus.DEREGISTERED && version != null
            return CollectionDisclosureSource(hash(listOf(orgId, id, version?.toModel(), c.registeredAt, c.historicalConfirmedAt, c.lifeStatus == EnumCollectionStatus.DEREGISTERED)), c.name, ready, version?.auditTime)
        }
        if (subject in statutorySubjects) return statutorySource(orgId, subject, id, lock)
        if (subject != EnumDisclosureSubject.ACQUISITION) {
            val providers = sourceProviders.filter { it.subject == subject }
            if (providers.size > 1) fail("公开对象来源重复注册，请核对模块配置")
            return providers.singleOrNull()?.source(orgId, id, lock)
        }
        val query = AcquisitionDao.find { (Acquisitions.id eq id) and (Acquisitions.orgId eq orgId) }
        val a = (if (lock) query.forUpdate() else query).singleOrNull() ?: return null
        val case = AcquisitionComplianceCaseDao.find { AcquisitionComplianceCases.acquisitionId eq id }.singleOrNull()
        if (case != null && case.orgId != orgId) return null
        val completion = case?.completion
        return CollectionDisclosureSource(hash(listOf(orgId, id, case?.subject, completion)), a.name, completion != null && case.vetoOn == null, completion?.actualAcquiredOn?.atStartOfDay())
    }
    private val statutorySubjects = setOf(EnumDisclosureSubject.EXIT_CONSULTATION, EnumDisclosureSubject.EXIT_RESULT, EnumDisclosureSubject.TRANSFER_RESULT)

    private fun statutorySource(orgId: String, subject: EnumDisclosureSubject, id: String, lock: Boolean): CollectionDisclosureSource? {
        if (subject == EnumDisclosureSubject.TRANSFER_RESULT) {
            val q = CollectionTransferDao.find { (CollectionTransfers.id eq id) and (CollectionTransfers.orgId eq orgId) }
            val row = (if (lock) q.forUpdate() else q).singleOrNull() ?: return null
            val seal = businessExecution(orgId, CollectionConst.BIZ_TRANSFER, id)
            return CollectionDisclosureSource(hash(listOf(orgId, id, row.baseline, row.dossier, row.execution, row.executedAt, seal?.digest)), row.baseline?.formalName ?: row.baseline?.totalName ?: "原馆转移藏品", row.executedAt != null && row.execution != null && seal != null, row.execution?.handedOn?.atStartOfDay())
        }
        val q = DeregistrationDao.find { (Deregistrations.id eq id) and (Deregistrations.orgId eq orgId) }
        val row = (if (lock) q.forUpdate() else q).singleOrNull() ?: return null
        val name = row.baseline?.formalName ?: row.baseline?.totalName ?: "退出藏品"
        // 来源异常时仍返回不可用状态，供 CMS 撤下旧稿；封存过程中的暂未完成阶段也不供稿。
        val intact = try {
            DeregistrationReadEvidence.verify(row, archives)
            true
        } catch (_: BadRequestException) { false }
          catch (_: tools.jackson.core.JacksonException) { false }
        if (!intact) return null
        if (subject == EnumDisclosureSubject.EXIT_RESULT) {
            val seal = businessExecution(orgId, CollectionConst.BIZ_DEREGISTER, id)
            return CollectionDisclosureSource(hash(listOf(orgId, id, row.baseline, row.dossier, row.reason, row.executedAt, seal?.digest)), name, row.executedAt != null && seal != null, row.executedAt)
        }
        val experts = DeregistrationExpertOpinionDao.find { DeregistrationExpertOpinions.deregistrationId eq id }.sortedBy { it.id.value }
        if (experts.any { it.orgId != orgId }) return null
        val ready = row.baseline != null && row.dossier?.dispositionPlanFileIds?.isNotBlank() == true && row.reason in DeregistrationCompliancePolicy.evaluatedReasons && row.vetoUntil == null && experts.size >= 5 && experts.all { it.content.agree } && experts.map { it.expertKey }.distinct().size == experts.size
        return CollectionDisclosureSource(hash(listOf(orgId, id, row.baseline, row.dossier, row.reason, experts.map { mapOf("id" to it.id.value, "content" to it.content) })), name, ready, experts.maxOfOrNull { it.content.assessedAt }?.atStartOfDay(), experts.map { it.id.value })
    }
    private fun businessExecution(orgId: String, biz: String, id: String) = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.orgId eq orgId) and (CollectionPermanentArchives.bizType eq biz) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq "EXECUTED") }.singleOrNull()?.takeIf { it.digest == hashJson(it.snapshot) }

    /** Same org -> source row lock order as review(), without granting the caller private review files. */
    fun lockStatutorySubject(subject: EnumDisclosureSubject, id: String): String {
        val orgId = if (subject == EnumDisclosureSubject.TRANSFER_RESULT) CollectionTransfers.selectAll().where { CollectionTransfers.id eq id }.singleOrNull()?.get(CollectionTransfers.orgId)
            else Deregistrations.selectAll().where { Deregistrations.id eq id }.singleOrNull()?.get(Deregistrations.orgId)
        if (orgId == null || subject !in statutorySubjects) fail("法定公示公告对象不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), orgId, "法定公示公告")
        lockOrg(orgId); source(orgId, subject, id, lock = true) ?: fail("法定公示公告对象不存在")
        return orgId
    }
    fun statutoryReviewOptions(orgId: String, subject: EnumDisclosureSubject, id: String): List<Map<String, Any?>> {
        val latest = reviews(orgId, subject, id).lastOrNull() ?: return emptyList()
        if (latest.content.decision != EnumDisclosureDecision.PUBLIC || !matches(latest, source(orgId, subject, id)) || !reviewedIntact(latest)) return emptyList()
        return listOf(mapOf("value" to latest.id.value, "label" to "第 ${latest.version} 版 · ${latest.content.content?.title}", "publicContentFileIds" to latest.content.publicContentFileIds))
    }
    fun latestStatutoryReviewId(orgId: String, subject: EnumDisclosureSubject, id: String): String = statutoryReviewOptions(orgId, subject, id).singleOrNull()?.get("value") as? String ?: fail("缺少本次法定公示或公告对应的有效公开稿核查原件")
    fun requireStatutoryNotice(orgId: String, subject: EnumDisclosureSubject, id: String, value: DeregistrationNoticeVo) {
        val expected = latestStatutoryReviewId(orgId, subject, id)
        if (value.disclosureReviewId != expected) fail("请关联当前已核查的同一版法定公示或公告稿，旧记录不能代替本版")
        val row = CollectionDisclosureReviewDao[expected]
        if (files(value.publicContentFileIds) != files(row.content.publicContentFileIds)) fail("实发公示或公告须对应本版核查的公开稿原件")
        if (value.startedOn.isBefore(row.content.reviewedAt.toLocalDate())) fail("实际公示或公告不能早于公开稿核查日期")
    }
    fun currentResultNoticeComplete(orgId: String, subject: EnumDisclosureSubject, id: String, notices: List<DeregistrationNoticeVo>): Boolean {
        val latest = statutoryReviewOptions(orgId, subject, id).singleOrNull()?.get("value") ?: return false
        return notices.filter { it.kind == EnumExitNoticeKind.RESULT && it.disclosureReviewId == latest }.map { it.channel }.toSet().containsAll(setOf(EnumExitNoticeChannel.MUSEUM_ONSITE, EnumExitNoticeChannel.MUSEUM_WEBSITE))
    }
    private fun matches(row: CollectionDisclosureReviewDao, source: CollectionDisclosureSource?) = source != null && source.revision == row.content.sourceRevision && source.ready
    private fun museumEnabled(orgId: String) = CollectionOrgDao.findById(orgId)?.let {
        it.status == io.hizhengfu.cloud.common.domain.EnumContentStatus.NORMAL && it.orgStatus == EnumOrgStatus.ENABLED
    } == true
    private fun canFeed(row: CollectionDisclosureReviewDao, source: CollectionDisclosureSource?) = museumEnabled(row.orgId) && matches(row, source) && row.content.decision == EnumDisclosureDecision.PUBLIC && row.feedEnabledAt != null && row.feedStoppedAt == null && reviewedIntact(row) && phaseIntact(row.id.value, "FEED_ENABLED", mapOf("reviewId" to row.id.value, "enabledAt" to row.feedEnabledAt), row.orgId)
    private fun snapshot(row: CollectionDisclosureReviewDao) = mapOf("subject" to row.subject, "subjectId" to row.subjectId, "version" to row.version, "content" to row.content)
    private fun reviewedIntact(row: CollectionDisclosureReviewDao): Boolean {
        val archive = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq BIZ) and
            (CollectionPermanentArchives.bizId eq row.id.value) and (CollectionPermanentArchives.phase eq "REVIEWED") }.singleOrNull() ?: return false
        return archiveIntact(archive, row.orgId, BIZ, row.id.value, "REVIEWED", snapshot(row), reviewFiles(row.content))
    }
    private fun archiveIntact(archive: CollectionPermanentArchiveDao, orgId: String, bizType: String, id: String,
        phase: String, value: Any, fileIds: String?): Boolean = try {
        archives.verifyExisting(archive, orgId, bizType, id, phase, value, fileIds)
        true
    } catch (_: BadRequestException) { false }
    private fun phaseIntact(id: String, phase: String, value: Any, orgId: String): Boolean = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase) }.singleOrNull()?.let { it.orgId == orgId && it.digest == hash(value) && it.digest == hashJson(it.snapshot) } == true
    private fun requireReviewed(row: CollectionDisclosureReviewDao) { if (!reviewedIntact(row)) fail("公开核查原件缺失或已变化，不能以状态代替签署原件") }
    private fun hash(value: Any) = hashJson(JsonUtils.toJson(value)!!)
    private fun hashJson(json: String) = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun files(value: String?) = value.orEmpty().split(',').map(String::trim).filter(String::isNotBlank).toSet()
    private fun reviewFiles(v: CollectionDisclosureReviewVo) = listOfNotNull(v.signedReviewFileIds, v.publicContentFileIds, v.replacementFileIds).joinToString(",")
    private fun reviews(orgId: String, subject: EnumDisclosureSubject, id: String) = CollectionDisclosureReviewDao.find { (CollectionDisclosureReviews.orgId eq orgId) and (CollectionDisclosureReviews.subject eq subject) and (CollectionDisclosureReviews.subjectId eq id) }.sortedBy { it.version }
    private fun events(id: String) = CollectionDisclosureEventDao.find { CollectionDisclosureEvents.reviewId eq id }.toList()
    private fun eventView(row: CollectionDisclosureEventDao, orgId: String) = mapOf("id" to row.id.value, "reviewId" to row.reviewId, "content" to row.content, "archives" to archives.readForDisplay(BIZ, row.id.value, orgId).archives)
    private fun row(orgId: String, id: String) = CollectionDisclosureReviewDao.find { (CollectionDisclosureReviews.id eq id) and (CollectionDisclosureReviews.orgId eq orgId) }.forUpdate().singleOrNull() ?: fail("本馆公开稿版本不存在")
    private fun lockForWriting(orgId: String) {
        access(orgId, true)
        lockOrg(orgId)
        access(orgId, true) // 等待馆锁期间权限可能变化，重复办理同样需要复核。
    }
    private fun lockOrg(orgId: String) { CollectionOrgDao.find { CollectionOrgs.id eq orgId }.forUpdate().singleOrNull() ?: fail("收藏单位不存在") }
    private fun access(orgId: String, write: Boolean = false) {
        if (!write) {
            readAuthorization.requireBusinessReadable(orgId, setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.PUBLISH), CollectionConst.CROSS_ORG, "公开核查和实发原件")
            return
        }
        CollectionCurrentOperationAccess.require(scope, orgId, true, setOf(CollectionConst.PUBLISH), "公开核查和实发原件")
    }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
