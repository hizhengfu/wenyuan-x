package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.file.dao.FileContents
import io.hizhengfu.cloud.sys.file.domain.FILE_CACHE
import io.hizhengfu.cloud.sys.file.service.FileRetentionGuard
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.cache.annotation.CacheEvict

/** 不依赖审批状态识别业务原件；材料替换前后保留读取身份，原业务事实仍按原流程办理。 */
@Component
@Transactional
class CollectionBusinessOriginalFiles(
    private val sources: ObjectProvider<CollectionOriginalFileSource>,
    private val archives: CollectionPermanentArchiveService,
    private val localStorage: LocalFileObjectStorage? = null,
    private val readAccess: ObjectProvider<FileReadAccess>? = null,
) {
    companion object {
        const val BIZ = "COLLECTION_ORIGINAL_FILE_IDENTITY"
        const val PHASE = "ORIGINAL_ACCESS_"
        internal val INHERITABLE_BUSINESSES = setOf(
            CollectionConst.BIZ_CHANGE, CollectionConst.BIZ_LOAN, CollectionConst.BIZ_TRANSFER,
            CollectionConst.BIZ_ACQUISITION_PLAN, CollectionConst.BIZ_INVENTORY, InventoryComplianceService.BIZ,
            CollectionConst.BIZ_EXHIBITION, CollectionConst.BIZ_REPRODUCTION,
            CollectionConst.BIZ_REQUISITION, CollectionConst.BIZ_RETURN, CollectionConst.BIZ_DEREGISTER,
            CollectionConst.BIZ_WAREHOUSE_ENTRY, WarehouseDiaryService.BIZ,
            AccessionReceiptService.BIZ, CollectionConst.BIZ_APPRAISAL, CollectionConst.BIZ_ACQUISITION,
            WarehouseStaffingService.BIZ, AnnualReportEvidenceService.BIZ, CollectionBusinessOriginalFileSource.WAREHOUSE,
        )
    }
    private fun current(biz: String? = null, id: String? = null) = sources.orderedStream().toList().flatMap { it.references(biz, id) }.distinct()
    private fun file(reference: String): FileDao? = FileDao.findById(reference) ?: FileDao.find { Files.path eq reference }.singleOrNull()
    private fun keys(path: String, storage: String?): Set<String> = try { FileReadPath.keys(path, storage, localStorage) }
        catch (_: BadRequestException) { setOf(FileReadPath.key(path, storage)) }
        catch (_: java.io.IOException) { setOf(FileReadPath.key(path, storage)) }

    @Transactional(noRollbackFor = [BadRequestException::class])
    fun references(path: String, storage: String?): List<CollectionOriginalFileReference> {
        val references = current()
        val files = CollectionOriginalFileLocations.find(references.map { it.file })
        val locations = mutableMapOf<Pair<String, String?>, Set<String>>()
        fun readKeys(p: String, s: String?) = locations.getOrPut(p to s) { keys(p, s) }
        val request = readKeys(path, storage)
        return references.filter { ref ->
            val f = files[ref.file]
            readKeys(f?.path ?: ref.file, f?.storageType).any(request::contains)
        }
    }
    /** 分批目录核验只复用这次读取的来源数据，授权仍逐来源进行；不跨请求缓存。 */
    internal fun referencesForLocations(locations:List<FileMetadataLocation>):Map<FileMetadataLocation,List<CollectionOriginalFileReference>> {
        if(locations.isEmpty())return emptyMap()
        val requested=locations.distinct().associateWith {keys(it.path,it.storageType)}
        val matched=requested.keys.associateWith {mutableListOf<CollectionOriginalFileReference>()}
        val files=object:LinkedHashMap<String,FileDao?>(512,0.75f,true) {
            override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,FileDao?>?)=size>512
        }
        val resolved=object:LinkedHashMap<Pair<String,String?>,Set<String>>(256,0.75f,true) {
            override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Pair<String,String?>,Set<String>>?)=size>256
        }
        current().forEach {ref->
            val f=if(files.containsKey(ref.file))files[ref.file] else file(ref.file).also {files[ref.file]=it}
            val location=(f?.path ?: ref.file) to f?.storageType?.name
            val found=resolved.getOrPut(location) {keys(location.first,location.second)}
            requested.forEach {(candidate,values)->if(found.any(values::contains))matched.getValue(candidate).add(ref)}
        }
        return matched.mapValues {(_,references)->references.distinct()}
    }
    fun remember(businessType: String, businessId: String?) {
        remember(businessType, businessId, existingSource = false)
    }

    /** 只继承责任重建写前已核实的原提用依据；新安全等材料仍走普通上传归属校验。 */
    internal fun rememberRequisitionReplacement(businessId: String, originals: CollectionPermanentArchiveService.RequisitionReplacementOriginals) {
        archives.verifyRequisitionReplacementOriginals(originals, businessId)
        remember(CollectionConst.BIZ_REQUISITION, businessId, existingSource = false, replacementOriginals = originals)
        archives.verifyRequisitionReplacementOriginals(originals, businessId)
    }

    /** 沿用已核实主档来源；普通编辑须在写请求材料前调用，调拨执行须先完成归档与受限关联的资格核验。 */
    internal fun rememberExistingMain(collectionId: String) {
        remember(CollectionMainOriginalFileSource.BIZ, collectionId, existingSource = true)
    }

    /** 已通过业务编辑资格检查后、写入新材料之前调用。 */
    internal fun rememberExistingBusiness(businessType: String, businessId: String) {
        if (businessType !in INHERITABLE_BUSINESSES) throw BadRequestException("该业务尚未配置既有原件继承规则")
        remember(businessType, businessId, existingSource = true)
    }

    /** 只将同一申请已留存的原件关联回该申请；本次新材料仍由上传引用校验处理。 */
    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    internal fun bindRetainedBusinessReference(businessType: String, businessId: String, orgId: String, fileId: String, aliasName: String): Boolean {
        if (businessType !in INHERITABLE_BUSINESSES) throw BadRequestException("该业务尚未配置既有原件继承规则")
        val original = file(fileId) ?: throw BadRequestException("业务原件缺失或路径不唯一")
        val retained=CollectionRetainedOriginalFiles.ids(businessType,businessId,setOf(orgId)) +
            if(businessType==CollectionConst.BIZ_ACQUISITION) CollectionRetainedOriginalFiles.stageIds(businessType,businessId,setOf(orgId)) else emptySet()
        if (original.id.value !in retained) return false
        bindReadableReference(original, businessId, aliasName)
        return true
    }

    /** 仅继承对应申请已留存的材料到其逐件出入库记录，不接受任意附件关联目标。 */
    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    internal fun bindMovementReference(businessType: String, businessId: String, movementId: String, fileId: String, aliasName: String): Boolean {
        val movement = StorageMovementDao.findById(movementId) ?: throw BadRequestException("逐件出入库记录不存在")
        if (movement.refBizId != businessId) throw BadRequestException("逐件出入库记录不属于该申请")
        val matches = when (businessType) {
            CollectionConst.BIZ_LOAN -> LoanDao.findById(businessId)?.let { source ->
                val signed = source.handover?.content
                source.orgId == movement.orgId && source.flowStatus == EnumFlowStatus.PASS &&
                    movement.type == EnumMoveType.OUT && movement.collectionId in splitCollectionIds(source.collectionIds) &&
                    signed != null && movement.operatedAt == signed.handedAt && signed.items.count { it.collectionId == movement.collectionId } == 1
            } == true
            CollectionConst.BIZ_REQUISITION -> RequisitionDao.findById(businessId)?.let { source ->
                val signed = source.handover?.content
                source.orgId == movement.orgId && source.flowStatus == EnumFlowStatus.PASS &&
                    movement.type == EnumMoveType.OUT && movement.collectionId in splitCollectionIds(source.collectionIds) &&
                    signed != null && movement.operatedAt == signed.handedAt && signed.items.count { it.collectionId == movement.collectionId } == 1
            } == true
            CollectionConst.BIZ_RETURN -> CollectionReturnDao.findById(businessId)?.let { source ->
                val signed = source.inspection
                source.orgId == movement.orgId && source.flowStatus == EnumFlowStatus.PASS &&
                    movement.type == EnumMoveType.IN && movement.collectionId in splitCollectionIds(source.collectionIds) &&
                    signed != null && movement.operatedAt == signed.inspectedAt && signed.items.count { it.collectionId == movement.collectionId } == 1
            } == true
            else -> false
        }
        if (!matches || CollectionDao.findById(movement.collectionId)?.orgId != movement.orgId)
            throw BadRequestException("逐件出入库记录与已确认申请、实物或签署时间不一致")
        val original = file(fileId) ?: throw BadRequestException("业务原件缺失或路径不唯一")
        val listed = BusinessAttachmentBindingPlanner.plan(movementId, aliasName, movement.attachmentIds)
            .any { file(it.fileId)?.id?.value == original.id.value }
        if (!listed) throw BadRequestException("该材料不在逐件出入库记录的原件清单中")
        val retained = CollectionRetainedOriginalFiles.ids(businessType, businessId, setOf(movement.orgId)) +
            CollectionRetainedOriginalFiles.stageIds(businessType, businessId, setOf(movement.orgId))
        if (original.id.value !in retained) return false
        bindReadableReference(original, movementId, aliasName)
        return true
    }

    /** 执行档案已核验新旧文件资格后，只将其中的签署和处置凭据关联到本件原总账。 */
    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    internal fun bindExecutedExitReference(businessId: String, collectionId: String, fileId: String, aliasName: String) {
        val source = DeregistrationDao.findById(businessId) ?: throw BadRequestException("退出申请不存在")
        val collection = CollectionDao.findById(collectionId) ?: throw BadRequestException("退出原总账不存在")
        if (source.collectionId != collectionId || source.orgId != collection.orgId || source.executedAt == null ||
            source.flowStatus != EnumFlowStatus.PASS || collection.lifeStatus != io.hizhengfu.cloud.collection.domain.EnumCollectionStatus.DEREGISTERED)
            throw BadRequestException("退出执行事实与原总账不一致")
        val archive = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_DEREGISTER) and (CollectionPermanentArchives.bizId eq businessId) and
                (CollectionPermanentArchives.orgId eq source.orgId) and (CollectionPermanentArchives.phase eq "EXECUTED")
        }.singleOrNull() ?: throw BadRequestException("退出执行档案缺失或不唯一")
        val retained = CollectionRetainedOriginalFiles.stageIds(CollectionConst.BIZ_DEREGISTER,businessId,setOf(source.orgId),"EXECUTED")
        val snapshot = try { JsonUtils.mapper.readTree(archive.snapshot) }
            catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("退出执行档案无法解析") }
        val request = snapshot.path("request")
        if (request.path("id").asString() != businessId || request.path("collectionId").asString() != collectionId || request.path("orgId").asString() != source.orgId)
            throw BadRequestException("退出执行档案与原总账归属不一致")
        if (request.path("executedAt") != JsonUtils.mapper.readTree(JsonUtils.toJson(source.toModel())!!).path("executedAt"))
            throw BadRequestException("退出执行时间与归档事实不一致")
        val execution = snapshot.path("execution")
        val original = file(fileId) ?: throw BadRequestException("退出执行凭据缺失或路径不唯一")
        val listed = BusinessAttachmentBindingPlanner.plan(collectionId,aliasName,
            execution.path("signedChangeFileIds").asString()+","+execution.path("dispositionReceiptFileIds").asString())
            .any { file(it.fileId)?.id?.value == original.id.value }
        if (!listed || original.id.value !in retained) throw BadRequestException("该原件不属于本次退出签署和处置凭据")
        bindReadableReference(original,collectionId,aliasName)
    }

    /** 双方执行档案已写齐并核验引用资格后，仅关联本次签署凭据到本件、申请及双方逐件记录。 */
    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    internal fun bindExecutedTransferReference(businessId: String, contentId: String, fileId: String, aliasName: String) {
        val source = CollectionTransferDao.findById(businessId) ?: throw BadRequestException("交换调拨申请不存在")
        val signed = source.execution ?: throw BadRequestException("缺少实际调拨签署记录")
        val collection = CollectionDao.findById(source.collectionId) ?: throw BadRequestException("调拨藏品不存在")
        val targetOrg = source.toOrgId ?: throw BadRequestException("调拨接收馆不存在")
        if (source.executedAt == null || source.flowStatus != EnumFlowStatus.PASS || source.orgId != source.fromOrgId ||
            source.orgId == targetOrg || collection.orgId != targetOrg || collection.warehouseId != source.dossier?.targetWarehouseId)
            throw BadRequestException("调拨执行事实与接收藏品不一致")
        val original = file(fileId) ?: throw BadRequestException("调拨签署原件缺失或路径不唯一")
        val signedFiles = listOfNotNull(signed.signedHandoverFileIds,signed.conditionImageFileIds,signed.signedLedgerChangeFileIds,
            signed.receivingLedgerReceiptFileIds,signed.packingCheckFileIds).joinToString(",")
        fun listed(value: String?) = BusinessAttachmentBindingPlanner.plan(contentId,aliasName,value).any { file(it.fileId)?.id?.value == original.id.value }
        if (!listed(signedFiles)) throw BadRequestException("该材料不属于本次调拨签署原件")
        if (contentId != businessId && contentId != source.collectionId) {
            val movement = StorageMovementDao.findById(contentId) ?: throw BadRequestException("调拨逐件记录不存在")
            val expectedOrg = when(movement.type) { EnumMoveType.OUT -> source.orgId; EnumMoveType.IN -> targetOrg }
            if (movement.refBizId != businessId || movement.collectionId != source.collectionId || movement.orgId != expectedOrg ||
                movement.operatedAt != signed.handedOn.atStartOfDay() || movement.fromWarehouseId != source.baseline?.warehouseId ||
                movement.toWarehouseId != source.dossier?.targetWarehouseId || !listed(movement.attachmentIds))
                throw BadRequestException("调拨逐件记录与双方签署事实不一致")
        }
        val current = JsonUtils.mapper.readTree(JsonUtils.toJson(source.toModel())!!)
        listOf(source.orgId to "EXECUTED",targetOrg to "RECEIVED").forEach { (org,phase) ->
            val archive = CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_TRANSFER) and (CollectionPermanentArchives.bizId eq businessId) and
                    (CollectionPermanentArchives.orgId eq org) and (CollectionPermanentArchives.phase eq phase)
            }.singleOrNull() ?: throw BadRequestException("调拨双方执行档案缺失或不唯一")
            if (original.id.value !in CollectionRetainedOriginalFiles.stageIds(CollectionConst.BIZ_TRANSFER,businessId,setOf(org),phase))
                throw BadRequestException("调拨签署原件未在双方执行档案留存")
            val snapshot = try { JsonUtils.mapper.readTree(archive.snapshot) }
                catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("调拨执行档案无法解析") }
            val matches = if (phase == "EXECUTED") listOf("id","orgId","fromOrgId","toOrgId","collectionId","dossier","baseline","executedAt","execution")
                .all { snapshot.path(it) == current.path(it) }
            else snapshot.path("transferId").asString() == businessId && snapshot.path("execution") == current.path("execution") &&
                snapshot.path("collection").path("id").asString() == source.collectionId && snapshot.path("collection").path("orgId").asString() == targetOrg
            if (!matches) throw BadRequestException("调拨双方档案与实际执行记录不一致")
        }
        bindReadableReference(original,contentId,aliasName)
    }

    /** 写入请求字段之前，只延续该当前记录已经引用的原件；新文件返回false走上传校验。 */
    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    internal fun bindExistingMainReference(collectionId: String, recordId: String?, fileId: String?, contentId: String, aliasName: String?): Boolean {
        if (recordId == null || fileId.isNullOrBlank()) return false
        val collection = CollectionDao.findById(collectionId) ?: throw BadRequestException("原件所属藏品不存在")
        val currentRecord = recordId == collectionId ||
            CollectionMediaDao.findById(recordId)?.let { it.collectionId == collectionId && it.orgId == collection.orgId } == true ||
            CollectionAttachmentDao.findById(recordId)?.let { it.collectionId == collectionId && it.orgId == collection.orgId } == true ||
            CollectionCatalogDao.findById(recordId)?.let { it.collectionId == collectionId && it.orgId == collection.orgId } == true
        if (!currentRecord) return false
        if (contentId != collectionId && contentId != recordId) throw BadRequestException("旧原件只能关联到其所属藏品或当前记录")
        val original = file(fileId) ?: throw BadRequestException("业务原件缺失或路径不唯一")
        val existing = CollectionMainOriginalFileSource().references(CollectionMainOriginalFileSource.BIZ, collectionId).any { ref ->
            ref.businessType == CollectionMainOriginalFileSource.BIZ && ref.recordId == recordId &&
                file(ref.file)?.id?.value == original.id.value
        }
        if (!existing) return false
        bindReadableReference(original, contentId, aliasName)
        return true
    }

    /** 仅沿用本次已核收清册照片；不把征集保密照片复制为主档封面。 */
    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    internal fun bindAccessionCover(acquisitionId: String, collectionId: String, fileId: String) {
        val source = AcquisitionDao.findById(acquisitionId) ?: throw BadRequestException("入藏来源不存在")
        val target = CollectionDao.findById(collectionId) ?: throw BadRequestException("待登记主档不存在")
        val voucher = EntryVoucherDao.find {
            (EntryVouchers.orgId eq target.orgId) and (EntryVouchers.voucherNo eq target.entryVoucherNo.orEmpty())
        }.singleOrNull() ?: throw BadRequestException("待登记主档缺少对应入馆凭证")
        if (source.orgId != target.orgId || source.flowStatus != EnumFlowStatus.PASS || source.collectionId != null ||
            source.id.value !in splitCollectionIds(voucher.acquisitionIds) ||
            voucher.voucherStatus != io.hizhengfu.cloud.collection.domain.EnumVoucherStatus.DRAFT ||
            target.source != source.source || target.registered ||
            target.lifeStatus != io.hizhengfu.cloud.collection.domain.EnumCollectionStatus.PENDING)
            throw BadRequestException("征集原影像只能沿用到其本次核收入藏的待登记主档")
        val original = file(fileId) ?: throw BadRequestException("核收照片不存在")
        val receipt = AccessionReceiptDao.find { AccessionReceipts.acquisitionId eq acquisitionId }.maxByOrNull { it.version }
            ?: throw BadRequestException("征集原影像沿用缺少实际核收原件")
        if (receipt.orgId != source.orgId || receipt.voucherId != voucher.id.value ||
            receipt.content.accepted != true || receipt.collectionId != null || receipt.content.warehouseId != target.warehouseId)
            throw BadRequestException("征集原影像与本次实际核收不一致")
        val receiptCover = receipt.content.pieces.firstOrNull()?.photoFileIds?.let(::splitCollectionIds)?.firstOrNull()?.let(::file)
        if (receiptCover?.id?.value != original.id.value || target.coverImage != original.id.value)
            throw BadRequestException("入藏封面须为本次实际核收清册的照片")
        bindReadableReference(original, collectionId, "藏品封面影像")
    }

    private fun bindReadableReference(original: FileDao, contentId: String, aliasName: String?) {
        val access = readAccess?.getIfAvailable() ?: throw BadRequestException("原件读取授权服务未配置")
        access.requireReadable(original.path, original.storageType.name)
        val matches = (FileContents.fileId eq original.id.value) and (FileContents.contentId eq contentId)
        if (FileContents.selectAll().where { matches }.empty()) {
            FileContents.insert {
                it[FileContents.fileId] = original.id.value
                it[FileContents.contentId] = contentId
                it[FileContents.aliasName] = aliasName
            }
        } else if (!aliasName.isNullOrBlank()) {
            FileContents.update({ matches }) { it[FileContents.aliasName] = aliasName }
        }
    }

    private fun remember(businessType: String, businessId: String?, existingSource: Boolean,
                         replacementOriginals: CollectionPermanentArchiveService.RequisitionReplacementOriginals? = null) {
        if (businessId.isNullOrBlank()) return
        current(businessType, businessId).forEach { ref ->
            val original = file(ref.file) ?: throw BadRequestException("业务原件缺失或路径不唯一，请核对后办理")
            val readKeys = archives.fileReadKeys(original)
            val phase = PHASE + CollectionCareEvidence.digest("${ref.orgId}|${ref.businessType}|${original.id.value}|${original.path}|${original.storageType}|${readKeys.sorted()}").take(48)
            val snapshot = mapOf("businessType" to ref.businessType, "fileId" to original.id.value, "path" to original.path, "storageType" to original.storageType.name, "readKeys" to readKeys.sorted(),
                "purpose" to "仅保留业务原件读取身份，不代表审批完成或实际签署")
            if (replacementOriginals != null && original.id.value in replacementOriginals.basisFileIds)
                archives.sealRequisitionReplacementOriginal(replacementOriginals, ref, phase, snapshot)
            else if (existingSource && businessType == CollectionMainOriginalFileSource.BIZ) archives.sealExistingMainOriginal(ref, phase, snapshot)
            else if (existingSource) archives.sealExistingBusinessOriginal(ref, phase, snapshot)
            else archives.seal(ref.orgId, BIZ, ref.businessId, phase, snapshot, original.id.value)
        }
    }
}

/** 文件身份单独归档，不成为原业务的批准、验收或签署阶段。 */
@Component
class CollectionOriginalIdentityReadPolicy(private val policies: ObjectProvider<CollectionArchiveReadPolicy>) : CollectionArchiveReadPolicy {
    override val businessTypes = setOf(CollectionBusinessOriginalFiles.BIZ)
    override fun requireReadable(reference: CollectionArchiveFileReference) {
        val archive = CollectionPermanentArchiveDao.findById(reference.archiveId)
            ?: throw BadRequestException("原件读取身份记录缺失，请核对原始业务")
        if (archive.bizType != CollectionBusinessOriginalFiles.BIZ || archive.orgId != reference.orgId || archive.bizId != reference.businessId ||
            archive.digest != CollectionCareEvidence.digest(archive.snapshot)) throw BadRequestException("原件读取身份记录不一致，请核对原始业务")
        val snapshot = try { JsonUtils.mapper.readTree(archive.snapshot) }
            catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("原件读取身份记录无法解析") }
        val businessType = snapshot.path("businessType").asString()
        if (snapshot.path("fileId").asString() != reference.fileId || businessType == CollectionBusinessOriginalFiles.BIZ)
            throw BadRequestException("原件读取身份与文件或来源不一致")
        val matches = policies.orderedStream().filter { businessType in it.businessTypes }.toList()
        if (matches.size != 1) throw BadRequestException("原件来源的读取规则尚未唯一配置，请核对业务模块")
        matches.single().requireReadable(reference.copy(businessType = businessType, phase = "RETAINED_ORIGINAL"))
    }
}

@Component
@Transactional(noRollbackFor = [BadRequestException::class])
class CollectionBusinessOriginalFileReadGuard(
    private val originals: CollectionBusinessOriginalFiles,
    private val policies: ObjectProvider<CollectionArchiveReadPolicy>,
    private val authorization: CollectionFileReadAuthorization? = null,
) : FileReadMetadataBatchAuthorizationGuard, FileReadDeploymentScopeGuard {
    override fun isProtected(path: String, storageType: String?) = originals.references(path, storageType).isNotEmpty()
    override fun requireDeploymentScope(path: String, storageType: String?) {
        val orgIds = originals.references(path, storageType).map { it.orgId }.toSet()
        if (orgIds.isNotEmpty()) (authorization ?: throw BadRequestException("业务原件部署馆别读取边界尚未配置"))
            .requireDeploymentScope(orgIds, "业务原件")
    }
    override fun checkMetadataReadable(path:String,storageType:String?)=checkReadable(path,storageType)
    override fun checkMetadataReadableMany(locations:List<FileMetadataLocation>):Map<FileMetadataLocation,Result<Boolean>> {
        val available=policies.orderedStream().toList()
        return originals.referencesForLocations(locations).mapValues {(_,references)->
            try {
                references.distinctBy {Triple(it.orgId,it.businessType,it.businessId)}.forEach {ref->
                    val policy=available.filter {ref.businessType in it.businessTypes}
                    if(policy.size!=1)throw BadRequestException("业务原件的读取规则尚未唯一配置，请核对业务模块")
                    policy.single().requireReadable(CollectionArchiveFileReference("",ref.orgId,ref.businessType,ref.businessId,"CURRENT_FILE",ref.file,"{}"))
                }
                Result.success(references.isNotEmpty())
            } catch(e:BadRequestException) {Result.failure(e)}
        }
    }
    override fun requireReadable(path: String, storageType: String?) { checkReadable(path, storageType) }
    override fun checkReadable(path: String, storageType: String?): Boolean {
        val available = policies.orderedStream().toList()
        val references = originals.references(path, storageType).distinctBy { Triple(it.orgId, it.businessType, it.businessId) }
        references.forEach { ref ->
            val policy = available.filter { ref.businessType in it.businessTypes }
            if (policy.size != 1) throw BadRequestException("业务原件的读取规则尚未唯一配置，请核对业务模块")
            policy.single().requireReadable(CollectionArchiveFileReference("", ref.orgId, ref.businessType, ref.businessId, "CURRENT_FILE", ref.file, "{}"))
        }
        return references.isNotEmpty()
    }
}

/** 仍被草稿或历史业务实际引用的文件不能通过公共文件删除/解绑入口脱离原件保护。 */
@Component
class CollectionBusinessOriginalFileRetentionGuard(private val originals: ObjectProvider<CollectionBusinessOriginalFiles>) : FileRetentionGuard {
    private fun references(fileId: String): List<CollectionOriginalFileReference> {
        val file = FileDao.findById(fileId) ?: return emptyList()
        return originals.getObject().references(file.path, file.storageType.name)
    }
    override fun requireDeletable(fileId: String) {
        if (references(fileId).isNotEmpty()) throw BadRequestException("该文件仍属于业务原始材料，不能删除；请从原业务办理更正并保留原件")
    }
    override fun requireUnlinkable(fileId: String, contentId: String) {
        if (references(fileId).any { contentId == it.businessId || contentId == it.recordId })
            throw BadRequestException("该附件关联属于业务原始材料，不能从公共文件入口移除")
    }
}
