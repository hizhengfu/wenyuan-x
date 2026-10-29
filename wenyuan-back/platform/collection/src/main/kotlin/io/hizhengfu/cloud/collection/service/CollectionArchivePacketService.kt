package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.LocalDate

@Component
@Transactional
class CollectionArchivePacketService(
    private val scope: CollectionScopeResolver, private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
    private val retainedLedgers: CollectionRetainedLedgerService,
) {
    private val readAuthorization=CollectionFileReadAuthorization(scope,CollectionActorDirectory(scope))
    companion object { const val BIZ = "COLLECTION_ARCHIVE_PACKET"; const val JURISDICTION_BIZ = "COLLECTION_ARCHIVE_JURISDICTION" }
    /** 档案办理按原馆账目选择，调出后仍保留原馆的独立档案入口。 */
    fun collections(orgId: String): List<Map<String, String>> {
        access(orgId)
        return CollectionLedgerReferenceChoices.list(orgId, retainedLedgers)
    }
    fun jurisdictions(orgId: String): List<Map<String, Any?>> {
        access(orgId)
        val current = jurisdictionAt(orgId, LocalDate.now())
        return jurisdictionRows(orgId).map { row -> mapOf("id" to row.id.value, "version" to row.version, "content" to row.content, "current" to (row.id == current?.id), "archives" to archives.readForDisplay(JURISDICTION_BIZ, row.id.value, orgId).archives) }
    }
    fun jurisdiction(orgId: String, value: ArchiveJurisdictionVo): String {
        access(orgId, true); lockOrg(orgId)
        val previous = jurisdictionRows(orgId).lastOrNull()
        if (previous?.content == value) { requireJurisdiction(previous.snapshot()); return previous.id.value }
        if (value.competentAuthorityName.isBlank() || value.nationalAuthorityName.isBlank() || value.localArchiveAuthorityName.isBlank() || value.basisFileIds.isBlank()) fail("请核实本馆主管机关、属地文物档案受理部门、国务院文物行政部门及实际依据原件")
        if (value.localArchiveAuthorityName == value.nationalAuthorityName) fail("属地文物档案受理部门与国务院文物行政部门不能混填，请核实实际档案受理程序")
        if (value.kind == EnumArchiveJurisdiction.NATIONAL_COMPETENT && value.competentAuthorityName != value.nationalAuthorityName) fail("由国务院文物行政部门主管的馆，主管机关须与该国家机关一致")
        if (value.kind == EnumArchiveJurisdiction.LOCAL_COMPETENT && value.competentAuthorityName == value.nationalAuthorityName) fail("地方主管部门与国务院文物行政部门不能填为同一机关，请按实际归属核实")
        if (value.verifiedAt.isAfter(LocalDateTime.now())) fail("主管归属核实时间不能填写未来完成事实")
        users.requireMuseumMember(value.responsibleUserId, orgId); users.requireRole(value.responsibleUserId, orgId, CollectionConst.ROLE_DIRECTOR)
        if (previous != null && (value.verifiedAt.isBefore(previous.content.verifiedAt) || value.replacementReason.isNullOrBlank() || value.replacementFileIds.isNullOrBlank())) fail("行政归属或名称订正须追加核实版本、原因和本次原件")
        val row = ArchiveJurisdictionDao.new { this.orgId = orgId; version = (previous?.version ?: 0) + 1; content = value }
        archives.seal(orgId, JURISDICTION_BIZ, row.id.value, "VERIFIED", value, listOfNotNull(value.basisFileIds, value.replacementFileIds).joinToString(","))
        return row.id.value
    }
    fun detail(orgId: String, collectionId: String): Map<String, Any?> {
        access(orgId)
        val source = source(orgId, collectionId,forDisplay=true)
        val current = source.versions.maxByOrNull { it.version }
        val jurisdiction = jurisdictionAt(orgId, LocalDate.now())?.snapshot()
        return mapOf("evidenceIssue" to source.issue,"orgId" to orgId, "collectionId" to collectionId, "totalRegNo" to source.number, "name" to source.name,
            "catalogVersions" to source.versions, "currentCatalogVersionId" to current?.id, "source" to source.kind,
            "jurisdiction" to jurisdiction, "records" to packets(orgId, collectionId).map { packet ->
                if (packet.source.catalogVersion.collectionId != collectionId || packet.content.catalogVersionId != packet.source.catalogVersion.id)
                    fail("档案目录的编目快照与原藏品或版本不一致")
                packet.jurisdiction?.let { frozen ->
                    val original=ArchiveJurisdictionDao.findById(frozen.recordId) ?: fail("档案主管归属原记录缺失")
                    if(original.orgId!=orgId || original.version!=frozen.version || original.content!=frozen.content)
                        fail("档案主管归属快照与本馆原记录不一致")
                }
                val submissions = submissions(packet.id.value)
                if(submissions.any { it.orgId!=orgId || it.target!=it.content.target })fail("档案报送记录与本馆目录或报送目标不一致")
                val targets = requiredTargets(packet.source.catalogVersion.content.nature, packet.source.catalogVersion.content.level, packet.jurisdiction ?: jurisdiction)
                val filingComplete = !targets.isNullOrEmpty() && targets.all { target -> submissions.any { it.target == target && it.receipt != null } }
                mapOf("id" to packet.id.value, "version" to packet.version, "content" to packet.content, "source" to packet.source,
                    "jurisdiction" to packet.jurisdiction, "coverageIssues" to coverageIssues(packet), "matchesCurrentCatalog" to matchesCurrentCatalog(packet,current),
                    "requiredTargets" to targets, "filingCompleteForPacket" to filingComplete,
                    "matchesCurrentJurisdiction" to (packet.jurisdiction?.let { routing(it) == jurisdiction?.let(::routing) } ?: false),
                    "submissions" to submissions.map { row -> mapOf("id" to row.id.value, "content" to row.content, "receipt" to row.receipt, "archives" to archives.readForDisplay(BIZ, row.id.value, orgId).archives) },
                    "archives" to archives.readForDisplay(BIZ, packet.id.value, orgId).archives)
            })
    }
    /** 目录查阅职责只可读取本馆主管依据、档案版本或报送记录绑定的冻结原件。 */
    fun requireOriginal(orgId: String, archiveId: String, fileId: String) {
        access(orgId)
        if (!fileId.matches(Regex("[0-9]{15,20}"))) fail("档案原件文件标识不正确")
        val archive = CollectionPermanentArchiveDao.findById(archiveId)
            ?.takeIf { it.orgId == orgId && it.bizType in setOf(BIZ, JURISDICTION_BIZ) }
            ?: fail("本馆档案原件不存在")
        when (archive.bizType) {
            JURISDICTION_BIZ -> ArchiveJurisdictionDao.findById(archive.bizId)
                ?.takeIf { it.orgId == orgId } ?: fail("本馆档案主管归属原记录不存在")
            BIZ -> {
                val packet = CollectionArchivePacketDao.findById(archive.bizId)
                    ?: CollectionArchiveSubmissionDao.findById(archive.bizId)?.let { submission ->
                        if (submission.orgId != orgId) fail("档案报送记录不属于本馆")
                        CollectionArchivePacketDao.findById(submission.packetId)
                    } ?: fail("本馆档案编制或报送原记录不存在")
                if (packet.orgId != orgId) fail("档案版本不属于本馆")
                source(orgId, packet.collectionId)
            }
            else -> fail("档案原件类型不正确")
        }
        val verified = archives.listForAuthorizedOrg(archive.bizType, archive.bizId, orgId)
            .singleOrNull { it["id"] == archiveId } ?: fail("本馆冻结档案不存在")
        @Suppress("UNCHECKED_CAST")
        val files = verified["files"] as List<Map<String, Any?>>
        if (files.none { it["fileId"] == fileId }) fail("该文件不是本档案的归档原件")
    }
    fun record(orgId: String, collectionId: String, value: CollectionArchivePacketVo): String {
        access(orgId, true); lockOrg(orgId)
        val source = source(orgId, collectionId)
        val version = source.versions.find { it.id == value.catalogVersionId } ?: fail("请选择本馆当前或留存原账的已审核编目版本")
        val previous = packets(orgId, collectionId).lastOrNull()
        if (previous?.content == value) { requireCompiled(previous); return previous.id.value }
        if (value.archiveNo.isBlank() || value.inventoryFileIds.isBlank() || value.photographFileIds.isBlank() || value.signedVerificationFileIds.isBlank()) fail("实际档案编号、档案目录、识别照片及编制核验签署原件必填")
        if (value.preparedAt.isAfter(LocalDateTime.now()) || version.auditTime?.isAfter(value.preparedAt) == true) fail("本版档案须在对应编目审核之后实际编制，不能预填未来完成时间")
        if (value.format in setOf(EnumCollectionArchiveFormat.PAPER, EnumCollectionArchiveFormat.BOTH) && value.physicalLocation.isNullOrBlank()) fail("纸质档案须登记实际保存位置")
        if (value.format in setOf(EnumCollectionArchiveFormat.ELECTRONIC, EnumCollectionArchiveFormat.BOTH) && value.electronicLocation.isNullOrBlank()) fail("电子档案须登记实际档案系统或存储卷宗位置")
        users.requireMuseumMember(value.archivistUserId, orgId)
        value.sections.forEach { (section, entry) ->
            if (entry.reference.isBlank()) fail("档案目录须逐项说明原件位置、未发生或缺项情况")
            if (entry.status == EnumArchiveSectionStatus.INCLUDED && entry.fileIds.isNullOrBlank()) fail("已归入档案的目录事项须关联实际原件")
            if (entry.status == EnumArchiveSectionStatus.NOT_APPLICABLE && (section != EnumArchiveSection.GRADING || version.content.nature != EnumCollectionNature.OTHER)) fail("不适用仅用于其他藏品不适用文物定级，其他事项应如实记录原件、未发生或缺项")
        }
        if (previous != null && (value.preparedAt.isBefore(previous.content.preparedAt) || value.replacementReason.isNullOrBlank() || value.replacementFileIds.isNullOrBlank())) fail("补充档案或订正须追加新版本、实际编制时间、原因和本次原件")
        val snapshot = ArchivePacketSourceVo(source.number, version.content.formalName ?: version.content.totalName ?: "历史编目名称待核实", version, source.kind, LocalDateTime.now())
        val packet = CollectionArchivePacketDao.new { this.orgId = orgId; this.collectionId = collectionId; this.version = (previous?.version ?: 0) + 1; content = value; this.source = snapshot }
        archives.seal(orgId, BIZ, packet.id.value, "COMPILED", compiled(packet), packetFiles(value))
        return packet.id.value
    }
    fun submission(orgId: String, packetId: String, value: ArchivePacketSubmissionVo): String {
        access(orgId, true); lockOrg(orgId)
        val packet = packet(orgId, packetId)
        val existing = submissions(packetId).firstOrNull { it.target == value.target }
        if (existing != null) { if (existing.content == value) { requireCompiled(packet); requirePacketJurisdiction(packet); requireSubmission(existing); return existing.id.value }; fail("该版档案的实际报送原件不能覆盖，请另建补正档案版本") }
        requireCompiled(packet)
        val jurisdiction = packet.jurisdiction ?: jurisdictionAt(orgId, value.submittedAt.toLocalDate())?.snapshot() ?: fail("请先核实实际报送当日有效的本馆档案备案归属及依据，不能默认免除国家档案备案范围")
        requireJurisdiction(jurisdiction)
        val targets = requiredTargets(packet.source.catalogVersion.content.nature, packet.source.catalogVersion.content.level, jurisdiction) ?: fail("请先核实文物藏品性质和正式级别")
        if (value.target !in targets) fail("该版档案不属于所选备案对象；其他藏品不按文物档案备案处理")
        val authority = if (value.target == EnumArchiveFilingTarget.NATIONAL) jurisdiction.content.nationalAuthorityName else jurisdiction.content.localArchiveAuthorityName
        if (value.authorityName != authority || value.submissionNo.isBlank() || value.submissionFileIds.isBlank()) fail("实际报送机关须与本馆核实的该级文物行政部门一致，报送编号及原件必填")
        if (value.submittedAt.isBefore(packet.content.preparedAt) || value.submittedAt.isAfter(LocalDateTime.now())) fail("须在该版档案实际编制之后报送，不得填写未来时间")
        if (packet.jurisdiction == null) {
            packet.jurisdiction = jurisdiction
            archives.seal(orgId, BIZ, packetId, "JURISDICTION", jurisdiction, listOfNotNull(jurisdiction.content.basisFileIds, jurisdiction.content.replacementFileIds).joinToString(","))
        } else requirePacketJurisdiction(packet)
        val row = CollectionArchiveSubmissionDao.new { this.orgId = orgId; this.packetId = packetId; target = value.target; content = value }
        archives.seal(orgId, BIZ, row.id.value, "SUBMITTED", mapOf("packetId" to packetId, "content" to value), value.submissionFileIds)
        return row.id.value
    }
    fun receipt(orgId: String, submissionId: String, value: ArchivePacketReceiptVo): String {
        access(orgId, true); lockOrg(orgId)
        val row = CollectionArchiveSubmissionDao.find { (CollectionArchiveSubmissions.id eq submissionId) and (CollectionArchiveSubmissions.orgId eq orgId) }.forUpdate().singleOrNull() ?: fail("本馆档案报送记录不存在")
        if (row.receipt != null) { if (row.receipt == value) {
            val packet = packet(orgId, row.packetId)
            requireCompiled(packet); requirePacketJurisdiction(packet); requireSubmission(row)
            requirePhase(BIZ, row.id.value, "FILED")
            archives.seal(orgId, BIZ, row.id.value, "FILED", value, value.receiptFileIds)
            return submissionId
        }; fail("实际档案备案回执不能覆盖，请另留补正版本") }
        val packet = packet(orgId, row.packetId)
        requireCompiled(packet); requirePacketJurisdiction(packet)
        if (value.authorityName != row.content.authorityName || value.filingNo.isBlank() || value.receiptFileIds.isBlank()) fail("请核实同一报送机关的实际档案备案编号和回执原件")
        if (value.receivedAt.isBefore(row.content.submittedAt) || value.receivedAt.isAfter(LocalDateTime.now())) fail("实际备案回执须在对应报送后收到且不得填写未来时间")
        requireSubmission(row)
        row.receipt = value
        archives.seal(orgId, BIZ, row.id.value, "FILED", value, value.receiptFileIds)
        return row.id.value
    }
    /** Business gate only; administrative filing is an independently tracked obligation. No raw originals escape here. */
    fun requireEstablished(collection: CollectionDao) {
        val current = source(collection.orgId, collection.id.value).versions.maxByOrNull { it.version }
        val all = packets(collection.orgId, collection.id.value)
        if (all.isEmpty()) fail("尚无实际建档目录和核验原件，已审核编目不能代替建档：${collection.totalRegNo}")
        // Historical supplements remain append-only, but they must not replace the
        // latest packet for the currently approved catalog at a business gate.
        val packet = all.lastOrNull { it.content.catalogVersionId == current?.id }
            ?: fail("档案基本信息未对应当前正式编目，请依法动态更新档案：${collection.totalRegNo}")
        if (!matchesCurrentCatalog(packet,current)) fail("档案基本信息未对应当前正式编目，请依法动态更新档案：${collection.totalRegNo}")
        if (coverageIssues(packet).isNotEmpty()) fail("实际档案仍有目录或原件缺项，不能仅凭旧审批认定建档完成：${collection.totalRegNo}")
        requireCompiled(packet)
    }
    private fun matchesCurrentCatalog(packet: CollectionArchivePacketDao,current: CollectionCatalogVersionVo?): Boolean {
        if(current==null || packet.content.catalogVersionId!=current.id)return false
        // Archive JSON stores epoch milliseconds; PostgreSQL datetime columns retain microseconds.
        // Compare both representations at the persisted precision without changing either original.
        fun stored(value: CollectionCatalogVersionVo)=value.copy(
            submittedAt=value.submittedAt?.truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
            auditTime=value.auditTime?.truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
        )
        return stored(packet.source.catalogVersion)==stored(current)
    }
    private fun coverageIssues(packet: CollectionArchivePacketDao): List<String> {
        val nature = packet.source.catalogVersion.content.nature
        val required = setOf(EnumArchiveSection.BASIC, EnumArchiveSection.ACQUISITION, EnumArchiveSection.IDENTIFICATION, EnumArchiveSection.STORAGE) + if (CollectionNaturePolicy.usesRelicRules(nature)) setOf(EnumArchiveSection.GRADING) else emptySet()
        val issues = EnumArchiveSection.entries.filter { section ->
            val entry = packet.content.sections[section]
            entry == null || entry.status == EnumArchiveSectionStatus.MISSING || (section in required && entry.status != EnumArchiveSectionStatus.INCLUDED)
        }.map { it.name }.toMutableList()
        if (nature == null) issues += "NATURE_UNVERIFIED"
        if (CollectionNaturePolicy.usesRelicRules(nature) && packet.source.catalogVersion.content.level in setOf(null, EnumRelicLevel.UNRATED)) issues += "GRADE_UNVERIFIED"
        return issues
    }
    private data class Source(val number: String, val name: String, val kind: String, val versions: List<CollectionCatalogVersionVo>,val issue:String?=null)
    private fun source(orgId: String, id: String,forDisplay:Boolean=false): Source {
        val current = CollectionDao.findById(id)?.takeIf { it.orgId == orgId }
        val retained = if (current == null) CollectionRetainedLedgerDao.find { (CollectionRetainedLedgers.orgId eq orgId) and (CollectionRetainedLedgers.collectionId eq id) }.maxByOrNull { it.createTime } else null
        if (current == null && retained == null) fail("本馆当前账及留存原账均无该藏品")
        val displayed = retained?.takeIf {forDisplay}?.let {retainedLedgers.boundSnapshotForAuthorizedDisplay(it,orgId)}
        val retainedSnapshot = displayed?.snapshot ?: retained?.let { retainedLedgers.verifiedBoundSnapshotForAuthorizedBusiness(it, orgId) }
        val versions = if (current != null) CollectionCatalogVersionDao.find { (CollectionCatalogVersions.orgId eq orgId) and (CollectionCatalogVersions.collectionId eq id) and (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED) }.map { it.toModel() }
            else retainedSnapshot!!.catalogVersions.filter { it.status == EnumCatalogStatus.APPROVED }
        if(retainedSnapshot!=null && (retainedSnapshot.collection.id!=id || retainedSnapshot.collection.orgId!=orgId || versions.any {it.collectionId!=id}))
            fail("原馆留存账及编目快照与本馆原藏品不一致")
        return Source(current?.totalRegNo ?: retained!!.totalRegNo,
            current?.name ?: retained!!.name, if (current != null) "CURRENT_MUSEUM" else "RETAINED_SOURCE_MUSEUM", versions.sortedBy { it.version },displayed?.issue)
    }
    private fun requiredTargets(nature: EnumCollectionNature?, level: EnumRelicLevel?, jurisdiction: ArchiveJurisdictionSnapshotVo?): Set<EnumArchiveFilingTarget>? {
        if (nature == EnumCollectionNature.OTHER) return emptySet()
        if (!CollectionNaturePolicy.usesRelicRules(nature) || jurisdiction == null || level == null || level == EnumRelicLevel.UNRATED) return null
        return setOf(EnumArchiveFilingTarget.COMPETENT) + if (level == EnumRelicLevel.FIRST || jurisdiction.content.kind == EnumArchiveJurisdiction.NATIONAL_COMPETENT) setOf(EnumArchiveFilingTarget.NATIONAL) else emptySet()
    }
    private fun routing(j: ArchiveJurisdictionSnapshotVo) = listOf(j.content.kind.name, j.content.competentAuthorityName, j.content.localArchiveAuthorityName, j.content.nationalAuthorityName)
    private fun jurisdictionRows(orgId: String) = ArchiveJurisdictionDao.find { ArchiveJurisdictions.orgId eq orgId }.sortedBy { it.version }
    private fun jurisdictionAt(orgId: String, date: LocalDate) = jurisdictionRows(orgId).lastOrNull { !it.content.effectiveOn.isAfter(date) }
    private fun packets(orgId: String, id: String) = CollectionArchivePacketDao.find { (CollectionArchivePackets.orgId eq orgId) and (CollectionArchivePackets.collectionId eq id) }.sortedBy { it.version }
    private fun submissions(id: String) = CollectionArchiveSubmissionDao.find { CollectionArchiveSubmissions.packetId eq id }.toList()
    private fun packet(orgId: String, id: String) = CollectionArchivePacketDao.find { (CollectionArchivePackets.id eq id) and (CollectionArchivePackets.orgId eq orgId) }.forUpdate().singleOrNull() ?: fail("本馆档案版本不存在")
    private fun compiled(packet: CollectionArchivePacketDao) = mapOf("collectionId" to packet.collectionId, "content" to packet.content, "source" to packet.source)
    private fun packetFiles(v: CollectionArchivePacketVo) = (listOfNotNull(v.inventoryFileIds, v.photographFileIds, v.signedVerificationFileIds, v.replacementFileIds) + v.sections.values.mapNotNull { it.fileIds }).joinToString(",")
    private fun requireCompiled(packet: CollectionArchivePacketDao) { requirePhase(BIZ, packet.id.value, "COMPILED"); archives.seal(packet.orgId, BIZ, packet.id.value, "COMPILED", compiled(packet), packetFiles(packet.content)) }
    private fun requireJurisdiction(j: ArchiveJurisdictionSnapshotVo) {
        val row = ArchiveJurisdictionDao.findById(j.recordId) ?: fail("主管归属原件不存在")
        if (row.content != j.content || row.version != j.version) fail("主管归属原件已变化，请核实")
        requirePhase(JURISDICTION_BIZ, j.recordId, "VERIFIED")
        archives.seal(row.orgId, JURISDICTION_BIZ, j.recordId, "VERIFIED", j.content, listOfNotNull(j.content.basisFileIds, j.content.replacementFileIds).joinToString(","))
    }
    private fun requirePacketJurisdiction(packet: CollectionArchivePacketDao) {
        val j = packet.jurisdiction ?: fail("缺少该版档案实际报送时的主管归属依据")
        requireJurisdiction(j); requirePhase(BIZ, packet.id.value, "JURISDICTION")
        archives.seal(packet.orgId, BIZ, packet.id.value, "JURISDICTION", j, listOfNotNull(j.content.basisFileIds, j.content.replacementFileIds).joinToString(","))
    }
    private fun requireSubmission(row: CollectionArchiveSubmissionDao) {
        requirePhase(BIZ, row.id.value, "SUBMITTED")
        archives.seal(row.orgId, BIZ, row.id.value, "SUBMITTED", mapOf("packetId" to row.packetId, "content" to row.content), row.content.submissionFileIds)
    }
    private fun requirePhase(biz: String, id: String, phase: String) {
        // Internal readiness checks may inspect the reciprocal exchange partner without
        // granting its raw archive files to the caller. seal() verifies the expected org/digest.
        if (CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq biz) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase) }.empty()) fail("缺少档案已留存的原始阶段，不能用完成状态替代原件")
    }
    private fun access(orgId: String, write: Boolean = false) {
        if(!write) {
            readAuthorization.requireReadable(setOf(orgId),setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.ARCHIVE_MANAGE,CollectionConst.ARCHIVE_SELECT),CollectionConst.CROSS_ORG,"藏品档案目录及原件")
            if(!scope.isMulti() && orgId!=scope.currentOrgId())fail("该档案不属于当前配置收藏单位")
            return
        }
        CollectionCurrentOperationAccess.require(scope, orgId, true, setOf(CollectionConst.ARCHIVE_MANAGE), "藏品档案原件")
    }
    private fun lockOrg(orgId: String) {
        CollectionOrgDao.find { CollectionOrgs.id eq orgId }.forUpdate().singleOrNull() ?: fail("收藏单位不存在")
        access(orgId, true) // 等待期间撤权、停用或调馆后，不得继续办理档案原件。
    }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
