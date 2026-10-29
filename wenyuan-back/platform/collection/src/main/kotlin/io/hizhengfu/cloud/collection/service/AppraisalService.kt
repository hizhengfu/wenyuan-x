package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionUserResolver
import io.hizhengfu.cloud.collection.dao.AppraisalDao
import io.hizhengfu.cloud.collection.dao.AppraisalMemberDao
import io.hizhengfu.cloud.collection.dao.AppraisalMembers
import io.hizhengfu.cloud.collection.dao.AppraisalMemberVo
import io.hizhengfu.cloud.collection.dao.AppraisalVo
import io.hizhengfu.cloud.collection.dao.Appraisals
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.domain.CollectionOutboundPolicy
import io.hizhengfu.cloud.collection.domain.EnumAppraisalMemberRole
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.flow.service.FlowService
import io.hizhengfu.cloud.flow.service.FlowUserOperation
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

internal data class AppraisalRecordValues(
    val resultName: String?,
    val resultEra: String?,
    val resultTexture: String?,
    val resultLevel: EnumRelicLevel?,
    val committee: String?,
    val conclusion: String?,
    val attachmentIds: String?,
) {
    companion object {
        fun fromApply(vo: AppraisalVo) = AppraisalRecordValues(
            resultName = vo.resultName,
            resultEra = vo.resultEra,
            resultTexture = vo.resultTexture,
            resultLevel = vo.resultLevel,
            committee = vo.committee,
            conclusion = vo.conclusion,
            attachmentIds = vo.attachmentIds,
        )
    }
}

internal object AppraisalMemberApprovalPolicy {
    fun assignees(members: List<AppraisalMemberVo>): List<String> =
        members.mapNotNull { it.userId?.trim()?.takeIf(String::isNotBlank) }.distinct()

    fun requireSubmittable(members: List<AppraisalMemberVo>) {
        if (members.isEmpty()) throw BadRequestException("请添加鉴定组成员")
        if (assignees(members).isEmpty()) {
            throw BadRequestException("至少一名鉴定组成员必须关联系统用户，才能接收审批任务")
        }
    }
}

data class AppraisalCollectionInfo(
    val totalRegNo: String?,
    val name: String?,
)

object AppraisalCollectionInfoEnricher {
    fun enrich(items: List<AppraisalVo>, collectionInfoById: Map<String, AppraisalCollectionInfo>): List<AppraisalVo> =
        items.map { item ->
            val info = item.collectionId?.let { collectionInfoById[it] }
            item.copy(
                collectionTotalRegNo = info?.totalRegNo,
                collectionName = info?.name,
            )
        }
}

/** 鉴定（定名/断代/定级）。流程通过后保留鉴定结论，由总账经办人发起正式信息变更。 */
@Component
@Transactional
class AppraisalService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val userResolver: CollectionUserResolver,
    private val changeService: CollectionChangeService,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val operationLockService: CollectionOperationLockService,
    private val accessionService: AccessionReceiptService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
    private val archives: CollectionPermanentArchiveService? = null,
) : FlowService<AppraisalDao>(AppraisalDao, flowInstanceService, CollectionConst.CACHE) {

    override fun businessType() = CollectionConst.BIZ_APPRAISAL
    override fun definitionKey() = CollectionConst.FLOW_APPRAISAL
    override fun businessTypeName() = "鉴定定级"
    override fun authorizeUserOperation(entity: AppraisalDao, operation: FlowUserOperation) {
        requireAccess(entity.orgId, operation != FlowUserOperation.FLOW_INFO)
        if (operation == FlowUserOperation.SAVE_DRAFT) requireDraftEditable(entity, "鉴定记录")
        if (operation in setOf(FlowUserOperation.SAVE_DRAFT, FlowUserOperation.SUBMIT) && entity.appraisedAt != null)
            throw BadRequestException("已经形成的鉴选结果须保留，后续鉴选请另立记录")
    }

    private fun requireAccess(orgId: String, write: Boolean, permissions: Set<String> =
        if (write) setOf(CollectionConst.APPRAISE) else setOf(CollectionConst.APPRAISE, CollectionConst.SELECT)) =
        CollectionCurrentOperationAccess.require(scope, orgId, write, permissions, "馆内鉴定")
    override fun buildTitle(entity: AppraisalDao) = "鉴定定级：${entity.resultName ?: entity.collectionId}"
    override fun buildFormData(entity: AppraisalDao) = mapOf(
        "collectionId" to entity.collectionId,
        "resultName" to entity.resultName,
        "resultEra" to entity.resultEra,
        "resultTexture" to entity.resultTexture,
        "resultLevel" to entity.resultLevel?.name,
        "committee" to entity.committee,
        "members" to members(entity.id.value).map {
            mapOf(
                "userId" to it.userId,
                "memberName" to it.memberName,
                "memberRole" to it.memberRole?.name,
                "organization" to it.organization,
                "professionalTitle" to it.professionalTitle,
                "specialty" to it.specialty,
                "opinion" to it.opinion,
                "signatureFileId" to it.signatureFileId,
            )
        },
        "conclusion" to entity.conclusion,
        "attachmentIds" to entity.attachmentIds,
    )

    override fun buildSelfSelectAssignees(entity: AppraisalDao): Map<String, List<String>> = mapOf(
        "committee" to AppraisalMemberApprovalPolicy.assignees(members(entity.id.value)),
    )

    override fun beforeSubmit(entity: AppraisalDao) {
        AppraisalMemberApprovalPolicy.requireSubmittable(members(entity.id.value))
    }

    override fun beforeFlowReentry(entity: AppraisalDao, targetNodeKey: String) {
        if (entity.appraisedAt != null) throw BadRequestException("已经形成的鉴选结果须保留，后续鉴选请另立记录")
        if (targetNodeKey != "committee") throw BadRequestException("鉴选重新审批须由全部鉴选成员重新办理")
        beforeSubmit(entity)
    }

    protected override fun acquireApprovalResources(entity: AppraisalDao) =
        operationLockService.acquire(entity.orgId, businessType(), entity.id.value, listOf(entity.collectionId))

    protected override fun releaseApprovalResources(entity: AppraisalDao) =
        operationLockService.release(businessType(), entity.id.value)

    fun findPage(request: HttpServletRequest): GenericPage<AppraisalVo> {
        val access = CollectionSearchReadAccess.require(scope, additionalPermissions = setOf(CollectionConst.APPRAISE), allowHistoricalOrgs = true)
        val page = GenericPage<AppraisalVo>().build(request)
        val q = Appraisals.selectAll()
        q.andWhere { Appraisals.orgId inList access.orgIds }
        request.getParameter("keyword")?.trim()?.takeIf { it.isNotBlank() }?.let { kw ->
            val matched = collectionIdsMatchingKeyword(kw, access.orgIds)
            var collectionMatch: org.jetbrains.exposed.v1.core.Op<Boolean> = org.jetbrains.exposed.v1.core.Op.FALSE
            matched.forEach { (org, ids) ->
                collectionMatch = collectionMatch or ((Appraisals.orgId eq org) and (Appraisals.collectionId inList ids))
            }
            q.andWhere {
                (Appraisals.collectionId like "%$kw%") or
                    collectionMatch or
                    (Appraisals.resultName like "%$kw%") or
                    (Appraisals.resultEra like "%$kw%") or
                    (Appraisals.resultTexture like "%$kw%") or
                    (Appraisals.committee like "%$kw%") or
                    (Appraisals.conclusion like "%$kw%") or
                    (Appraisals.rejectReason like "%$kw%") or
                    (Appraisals.attachmentIds like "%$kw%")
            }
        }
        request.getParameter("collectionId")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { Appraisals.collectionId eq it } }
        request.getParameter("resultName")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { Appraisals.resultName like "%$it%" } }
        request.getParameter("resultLevel")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumRelicLevel.valueOf(value) }.getOrNull()?.let { q.andWhere { Appraisals.resultLevel eq it } }
        }
        request.getParameter("flowStatus")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumFlowStatus.valueOf(value) }.getOrNull()?.let { q.andWhere { Appraisals.flowStatus eq it } }
        }
        request.getParameter("appraisedAtStart")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            parseCollectionDateParam(value)?.atStartOfDay()?.let { q.andWhere { Appraisals.appraisedAt greaterEq it } }
        }
        request.getParameter("appraisedAtEnd")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            parseCollectionDateParam(value)?.plusDays(1)?.atStartOfDay()?.minusNanos(1)
                ?.let { q.andWhere { Appraisals.appraisedAt lessEq it } }
        }
        request.getParameter("hasAttachment")?.trim()?.takeIf { it.isNotBlank() }?.toBooleanStrictOrNull()?.let { has ->
            if (has) q.andWhere { Appraisals.attachmentIds.isNotNull() and Appraisals.attachmentIds.neq("") }
            else q.andWhere { Appraisals.attachmentIds.isNull() or Appraisals.attachmentIds.eq("") }
        }
        q.orderBy(Appraisals.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<AppraisalDao>)
        page.list = daoPage.data?.map { it.toModel() }?.let { enrichMembers(enrichCollectionInfo(it)) }
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<AppraisalVo>
    }

    fun get(id: String): AppraisalVo {
        val entity = findById(id) ?: throw BadRequestException("鉴定记录不存在")
        requireAccess(entity.orgId, false)
        return enrichMembers(enrichCollectionInfo(listOf(entity.toModel()))).single()
    }

    fun save(vo: AppraisalVo): String {
        val collectionId = vo.collectionId?.takeIf { it.isNotBlank() } ?: throw BadRequestException("藏品必填")
        val coll = CollectionDao.findById(collectionId) ?: throw BadRequestException("藏品不存在")
        requireAccess(coll.orgId, true)
        if (coll.lifeStatus == EnumCollectionStatus.PENDING && !coll.inventoryHold) {
            accessionService.requireReceived(coll)
        } else CollectionOutboundPolicy.requireInStore(coll.lifeStatus, "鉴定", coll.inventoryHold)
        val values = AppraisalRecordValues.fromApply(vo)
        val requestedMembers = normalizedMembers(vo)
        val d = saveDraft(vo.id, "鉴定记录不存在", createEntity = {
            dao.new {
                orgId = coll.orgId
                this.collectionId = collectionId
            }
        }) {
            requireAccess(orgId, true)
            if (orgId != coll.orgId) throw BadRequestException("已有鉴定记录不能改换收藏单位")
            if (!vo.id.isNullOrBlank()) originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_APPRAISAL,id.value)
            this.collectionId = collectionId
            resultName = values.resultName
            resultEra = values.resultEra
            resultTexture = values.resultTexture
            resultLevel = values.resultLevel
            committee = requestedMembers.joinToString("、") { it.memberName.orEmpty() }.takeIf(String::isNotBlank)
            conclusion = values.conclusion
            rejectReason = null
            attachmentIds = values.attachmentIds
        }
        replaceMembers(d, requestedMembers)
        BusinessAttachmentBindingPlanner.plan(d.id.value,"鉴定材料附件",d.attachmentIds).forEach { item ->
            if(originalFiles?.bindRetainedBusinessReference(CollectionConst.BIZ_APPRAISAL,d.id.value,d.orgId,item.fileId,item.aliasName)!=true)
                attachmentBinder.bindSingle(d.id.value,item.aliasName,item.fileId)
        }
        originalFiles?.remember(CollectionConst.BIZ_APPRAISAL, d.id.value)
        return d.id.value
    }

    fun apply(vo: AppraisalVo): String = save(vo)

    override fun onFlowCompleted(entity: AppraisalDao, event: FlowCompletedEvent) {
        super.onFlowCompleted(entity, event)
        entity.appraisedAt = LocalDateTime.now()
        archives?.let { CollectionAppraisalPrintFacts.capture(entity, it) }
    }

    /** 鉴定意见不代替行政定级或信息变更批准。此入口仅创建待补充的变更草稿。 */
    fun createChange(id: String): String {
        val row = AppraisalDao.findById(id) ?: throw BadRequestException("鉴定记录不存在")
        requireAccess(row.orgId, true, setOf(CollectionConst.LEDGER_CORRECT))
        val collection = CollectionDao.findById(row.collectionId) ?: throw BadRequestException("鉴定关联藏品不存在")
        if (collection.orgId != row.orgId) throw BadRequestException("鉴定关联藏品已不属于原馆，请核对原记录")
        val entity = row.toModel()
        if (entity.flowStatus != EnumFlowStatus.PASS) throw BadRequestException("鉴定审批通过后才能发起信息变更")
        val initial = changeService.proposal(entity.collectionId!!)
        val content = initial.content!!
        return changeService.save(initial.copy(
            reason = "依据鉴定记录 $id：${entity.conclusion.orEmpty()}",
            evidenceFileIds = entity.attachmentIds,
            content = content.copy(
                formalName = entity.resultName?.takeIf(String::isNotBlank) ?: content.formalName,
                era = entity.resultEra?.takeIf(String::isNotBlank) ?: content.era,
                texture = entity.resultTexture?.takeIf(String::isNotBlank) ?: content.texture,
                level = entity.resultLevel ?: content.level,
                appraisalOpinion = "鉴定记录 $id：${entity.conclusion.orEmpty()}",
            ),
        ))
    }

    private fun enrichCollectionInfo(items: List<AppraisalVo>): List<AppraisalVo> {
        val ids = items.mapNotNull { it.collectionId }.toSet()
        if (ids.isEmpty()) return items
        val infoById = Collections
            .selectAll()
            .where { Collections.id inList ids }
            .associate {
                it[Collections.id].value to (it[Collections.orgId] to AppraisalCollectionInfo(
                    totalRegNo = it[Collections.totalRegNo],
                    name = it[Collections.name],
                ))
            }
        return items.map { item ->
            val info = infoById[item.collectionId]?.takeIf { it.first == item.orgId }?.second
            item.copy(collectionName = info?.name, collectionTotalRegNo = info?.totalRegNo)
        }
    }

    private fun enrichMembers(items: List<AppraisalVo>): List<AppraisalVo> {
        val ids = items.mapNotNull { it.id }.toSet()
        if (ids.isEmpty()) return items
        val orgById = items.associate { it.id to it.orgId }
        val grouped = AppraisalMemberDao.find { AppraisalMembers.appraisalId inList ids }
            .onEach { if (it.orgId != orgById[it.appraisalId]) throw BadRequestException("鉴定成员与原鉴定记录所属馆不一致") }
            .sortedWith(compareBy<AppraisalMemberDao> { it.sort }.thenBy { it.memberName })
            .groupBy { it.appraisalId }
            .mapValues { (_, rows) -> rows.map { it.toModel() } }
        return items.map { item ->
            val structured = item.id?.let(grouped::get).orEmpty()
            item.copy(members = structured.ifEmpty { legacyMembers(item.committee) })
        }
    }

    private fun members(appraisalId: String): List<AppraisalMemberVo> {
        val root = AppraisalDao.findById(appraisalId) ?: throw BadRequestException("鉴定记录不存在")
        val structured = AppraisalMemberDao.find {
            AppraisalMembers.appraisalId eq appraisalId
        }.onEach { if (it.orgId != root.orgId) throw BadRequestException("鉴定成员与原鉴定记录所属馆不一致") }.sortedWith(compareBy<AppraisalMemberDao> { it.sort }.thenBy { it.memberName }).map { it.toModel() }
        if (structured.isNotEmpty()) return structured
        return legacyMembers(AppraisalDao.findById(appraisalId)?.committee)
    }

    private fun normalizedMembers(vo: AppraisalVo): List<AppraisalMemberVo> {
        val source = when {
            vo.members.isNotEmpty() -> vo.members
            !vo.committee.isNullOrBlank() -> legacyMembers(vo.committee)
            !vo.id.isNullOrBlank() -> members(vo.id)
            else -> emptyList()
        }
        val userIds = source.mapNotNull { it.userId?.trim()?.takeIf(String::isNotBlank) }
        val userNames = collectionUserNames(userIds)
        val seen = mutableSetOf<String>()
        val normalized = source.mapIndexed { index, member ->
            val userId = member.userId?.trim()?.takeIf(String::isNotBlank)
            if (userId != null && userId !in userNames) {
                throw BadRequestException("第 ${index + 1} 名鉴定组成员关联的系统用户不存在")
            }
            val name = userId?.let(userNames::get)
                ?: member.memberName?.trim()?.takeIf(String::isNotBlank)
                ?: throw BadRequestException("第 ${index + 1} 名鉴定组成员姓名必填")
            val key = userId?.let { "user:$it" }
                ?: "external:${name.lowercase()}:${member.organization?.trim().orEmpty().lowercase()}"
            if (!seen.add(key)) throw BadRequestException("鉴定组成员「$name」重复")
            member.copy(
                userId = userId,
                memberName = name,
                memberRole = member.memberRole ?: EnumAppraisalMemberRole.MEMBER,
                organization = member.organization?.trim()?.takeIf(String::isNotBlank),
                professionalTitle = member.professionalTitle?.trim()?.takeIf(String::isNotBlank),
                specialty = member.specialty?.trim()?.takeIf(String::isNotBlank),
                signatureFileId = attachmentBinder.resolveSingle(member.signatureFileId),
                sort = member.sort ?: index,
            )
        }.toMutableList()
        if (normalized.count { it.memberRole == EnumAppraisalMemberRole.LEADER } > 1) {
            throw BadRequestException("鉴定组只能设置一名组长")
        }
        if (normalized.isNotEmpty() && normalized.none { it.memberRole == EnumAppraisalMemberRole.LEADER }) {
            normalized[0] = normalized[0].copy(memberRole = EnumAppraisalMemberRole.LEADER)
        }
        return normalized
    }

    private fun replaceMembers(appraisal: AppraisalDao, members: List<AppraisalMemberVo>) {
        AppraisalMemberDao.find { AppraisalMembers.appraisalId eq appraisal.id.value }.forEach { it.delete() }
        members.forEachIndexed { index, member ->
            val saved = AppraisalMemberDao.new {
                orgId = appraisal.orgId
                appraisalId = appraisal.id.value
                userId = member.userId
                memberName = member.memberName!!
                memberRole = member.memberRole ?: EnumAppraisalMemberRole.MEMBER
                organization = member.organization
                professionalTitle = member.professionalTitle
                specialty = member.specialty
                opinion = member.opinion
                signatureFileId = member.signatureFileId
                sort = member.sort ?: index
            }
            saved.signatureFileId?.let { file ->
                val alias="鉴定成员签名-${saved.memberName}"
                if(originalFiles?.bindRetainedBusinessReference(CollectionConst.BIZ_APPRAISAL,appraisal.id.value,appraisal.orgId,file,alias)!=true)
                    attachmentBinder.bindSingle(appraisal.id.value,alias,file)
            }
        }
    }

    private fun collectionUserNames(userIds: List<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        return userResolver.names(userIds)
    }

    private fun legacyMembers(committee: String?): List<AppraisalMemberVo> = committee.orEmpty()
        .split(',', '，', ';', '；', '、', '\n')
        .map(String::trim)
        .filter(String::isNotBlank)
        .mapIndexed { index, name ->
            AppraisalMemberVo(
                memberName = name,
                memberRole = if (index == 0) EnumAppraisalMemberRole.LEADER else EnumAppraisalMemberRole.MEMBER,
                sort = index,
            )
        }

    private fun collectionIdsMatchingKeyword(keyword: String, orgIds: Set<String>): Map<String, List<String>> {
        val q = Collections.selectAll()
        q.andWhere { Collections.orgId inList orgIds }
        q.andWhere {
            (Collections.totalRegNo like "%$keyword%") or
                (Collections.name like "%$keyword%")
        }
        return q.groupBy({ it[Collections.orgId] }, { it[Collections.id].value })
    }
}
