package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.utils.IdGenerate
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

internal object CollectionMigrationPolicy {
    fun verification(value: String?) = when(value?.trim()) {
        "CONFIRMED", "已核实" -> "CONFIRMED"
        "UNVERIFIED", "待核实" -> "UNVERIFIED"
        else -> null
    }
    fun nature(value: String?) = CollectionNaturePolicy.parse(value)
    fun status(value: String?) = when(value?.trim()) {
        "IN_STORE", "在库" -> EnumCollectionStatus.IN_STORE
        "IN_USE", "提用中" -> EnumCollectionStatus.IN_USE
        "ON_LOAN", "借出", "借出未归" -> EnumCollectionStatus.ON_LOAN
        "RESTORING", "修复中" -> EnumCollectionStatus.RESTORING
        "MISSING", "盘亏" -> EnumCollectionStatus.MISSING
        "PRE_OUT", "待出库" -> EnumCollectionStatus.PRE_OUT
        "RETURN_HOLD", "归还待核查" -> EnumCollectionStatus.RETURN_HOLD
        else -> null
    }
    fun date(value: String?): LocalDateTime? = value?.trim()?.takeIf(String::isNotBlank)?.let {
        runCatching { LocalDateTime.parse(it.replace(' ', 'T').let { text -> if(text.length==10) text+"T00:00:00" else text }) }.getOrNull()
    }
    fun historyVerified(value: String?) = value?.trim() in setOf("YES", "是")
    fun errors(request: CollectionMigrationRequestVo, orgCode: String, now: LocalDateTime, acceptedOrgCodes: Set<String> = setOf(orgCode)): List<CollectionImportErrorVo> {
        val errors=mutableListOf<CollectionImportErrorVo>()
        fun error(row: Int, field: String, message: String) { errors+=CollectionImportErrorVo(row,field,message) }
        if(request.sourceSystem.isBlank() || request.sourceSystem.length>100) error(0,"sourceSystem","来源系统必填且不超过100字")
        if(request.reason.isBlank() || request.reason.length>500) error(0,"reason","迁移核验说明必填且不超过500字")
        if(!request.belongsToCurrentMuseum) error(0,"belongsToCurrentMuseum","请确认迁移的是本馆旧系统数据；外馆调入须走调拨接收")
        if(request.rows.isEmpty() || request.rows.size>10000) error(0,"rows","每批须有1至10000条记录")
        val seen=mutableSetOf<String>()
        request.rows.forEachIndexed { index,row ->
            val no=row.item.rowNo.takeIf { it>0 } ?: index+2
            if(row.item.totalRegNo.isNullOrBlank()) error(no,"totalRegNo","迁移必须保留原总登记号，不能自动生成")
            if(row.sourceRecordId.isNullOrBlank() || row.sourceRecordId.length>100) error(no,"sourceRecordId","原系统记录编号必填且不超过100字")
            else if(!seen.add(row.sourceRecordId.trim())) error(no,"sourceRecordId","本批原系统记录编号重复")
            if(row.originalOrgCode?.trim() !in acceptedOrgCodes) error(no,"originalOrgCode","原登记馆代码与当前馆不一致；请核实归属，外馆调入不能按旧系统迁移")
            val verified=verification(row.verification)
            if(verified==null) error(no,"verification","请选择已核实或待核实")
            if(nature(row.nature)==null) error(no,"nature","藏品性质须填写文物藏品、其他藏品或科学化石")
            if(status(row.currentStatus)==null) error(no,"currentStatus","须填写未退出馆藏的当前状态；已退出资料请使用历史账册核对入口")
            if(nature(row.nature)==EnumCollectionNature.OTHER && !row.item.level.isNullOrBlank() && row.item.level.trim() !in setOf("UNRATED","未定级")) error(no,"level","其他藏品不能迁移为文物等级")
            val at=date(row.originalRegisteredAt)
            if(!row.originalRegisteredAt.isNullOrBlank() && at==null) error(no,"originalRegisteredAt","原登记日期格式须为YYYY-MM-DD或YYYY-MM-DD HH:mm:ss")
            if(at!=null && (at>now || at.year<1)) error(no,"originalRegisteredAt","原登记日期不能晚于当前时间")
            if(row.historyVerified?.trim().orEmpty() !in setOf("", "YES", "NO", "是", "否")) error(no,"historyVerified","历史增减核对须填写是或否")

        }
        return errors
    }
}

@Component
@Transactional
class CollectionMigrationService(
    private val imports: CollectionImportService,
    private val collections: CollectionService,
    private val attachments: BusinessAttachmentBinder,
) {
    fun template() = imports.template().filterNot { it.field == "nature" }.map { if(it.field=="totalRegNo") it.copy(required=true) else it }+listOf(
        CollectionImportTemplateColumnVo("sourceRecordId","原系统记录编号",true),
        CollectionImportTemplateColumnVo("originalOrgCode","原登记馆代码",true),
        CollectionImportTemplateColumnVo("nature","藏品性质",true,sample="文物藏品"),
        CollectionImportTemplateColumnVo("currentStatus","当前藏品状态",true,sample="在库"),
        CollectionImportTemplateColumnVo("originalRegisteredAt","原登记日期",sample="2009-06-01"),
        CollectionImportTemplateColumnVo("originalRegisteredBy","原登记人"),
        CollectionImportTemplateColumnVo("registrationEvidenceName","登记依据文件名（先上传）"),
        CollectionImportTemplateColumnVo("verification","迁移核验结果",true,sample="待核实"),
        CollectionImportTemplateColumnVo("historyVerified","历史增减已核对",sample="否"),
    )
    private fun evidence(row: CollectionMigrationRowVo): String? {
        val named=row.registrationEvidenceName?.trim()?.takeIf(String::isNotBlank)?.let(attachments::resolveByName)
        val referenced=row.registrationEvidence?.trim()?.takeIf(String::isNotBlank)?.let(attachments::resolveSingle)
        if(named!=null && referenced!=null && named!=referenced) throw BadRequestException("登记依据文件名与文件ID不一致")
        return (named ?: referenced)?.also(attachments::requireReadableFileId)
    }
    private fun prepared(request: CollectionMigrationRequestVo, expectedOrgId: String?=null): Pair<CollectionMigrationValidateVo,CollectionImportValidateResultVo> {
        val base=imports.validate(request.rows.map { it.item.copy(nature = it.nature) }, expectedOrgId=expectedOrgId)
        val org=base.confirmation?.orgId ?: expectedOrgId
        val code=org?.let { CollectionOrgDao.findById(it)?.code }.orEmpty()
        val errors=base.errors+CollectionMigrationPolicy.errors(request,code,LocalDateTime.now(), org?.let(CollectionOrgCodeRegistry::acceptedCodes) ?: emptySet())
        val fileErrors=mutableListOf<CollectionImportErrorVo>()
        if(org!=null) {
            val previous=CollectionLedgerLogDao.find { (CollectionLedgerLogs.orgId eq org) and (CollectionLedgerLogs.field eq "legacyMigration") }.mapNotNull {
                runCatching { JsonUtils.fromJson(it.after,CollectionMigrationSnapshotVo::class.java) }.getOrNull()
            }.filter { it.sourceSystem==request.sourceSystem.trim() }.map { it.sourceRecordId }.toSet()
            request.rows.forEachIndexed { index,row -> if(row.sourceRecordId?.trim() in previous)
                fileErrors+=CollectionImportErrorVo(row.item.rowNo.takeIf { it>0 } ?: index+2,"sourceRecordId","原系统记录已迁移，不能换号重复导入") }
        }
        val evidenceIds=mutableListOf<String?>()
        request.rows.forEachIndexed { index,row ->
            try { evidenceIds+=evidence(row) }
            catch(e: BadRequestException) { fileErrors+=CollectionImportErrorVo(row.item.rowNo.takeIf { it>0 } ?: index+2,"registrationEvidence",e.message ?: "登记依据文件不可用") }
        }
        val all=errors+fileErrors
        val confirmed=0 // 表中核验结果仅作为来源声明，不能代替系统内独立复核
        return CollectionMigrationValidateVo(all.isEmpty(),all,request.rows.size,0,confirmed,request.rows.size-confirmed,
            if(all.isEmpty()) CollectionImportConfirmation.create(org!!,listOf("registered-migration/v1",request,base.confirmation,evidenceIds)) else null) to base
    }
    fun validate(request: CollectionMigrationRequestVo) = prepared(request).first

    @CacheEvict(cacheNames=[CollectionConst.CACHE],allEntries=true)
    fun commit(request: CollectionMigrationRequestVo, expectedOrgId: String, expectedValidationHash: String): CollectionImportCommitResultVo {
        // 与普通导入共用馆级锁，整个批次原子提交，重复确认不得再次创建或覆盖。
        CollectionOrgs.select(CollectionOrgs.id).where { CollectionOrgs.id eq expectedOrgId }.forUpdate().singleOrNull()
            ?: throw BadRequestException("目标收藏单位不存在")
        val (preview,base)=prepared(request,expectedOrgId)
        if(!preview.valid) return CollectionImportCommitResultVo(valid=false,errors=preview.errors)
        if(expectedValidationHash.isBlank()) throw BadRequestException("请先完成迁移预校验")
        CollectionImportConfirmation.requireSame(preview.confirmation,expectedValidationHash)
        val batch=IdGenerate.nextId(); val now=LocalDateTime.now(); val actor=SecurityUtils.getUserDetail().id
        val created=base.items.mapIndexed { index,item ->
            val row=request.rows[index]; val verified=CollectionMigrationPolicy.verification(row.verification)!!
            val id=collections.createForOrg(expectedOrgId,item.copy(nature=CollectionMigrationPolicy.nature(row.nature),lifeStatus=CollectionMigrationPolicy.status(row.currentStatus)))
            val node=CollectionDao[id]
            val evidence=evidence(row)
            attachments.bindSingle(id,"旧系统登记迁移依据",evidence)
            val snapshot=CollectionMigrationSnapshotVo(batch,request.sourceSystem.trim(),row.sourceRecordId!!.trim(),row.originalOrgCode!!.trim(),
                row.originalRegisteredAt?.trim(),row.originalRegisteredBy?.trim(),evidence,"UNVERIFIED",CollectionMigrationPolicy.historyVerified(row.historyVerified),now.toString(),actor,request.reason.trim(), declaredVerification=verified)
            CollectionLedgerLogDao.new {
                orgId=expectedOrgId;collectionId=id;totalRegNo=node.totalRegNo;action=EnumLedgerAction.AUDIT;field="legacyMigration"
                after=JsonUtils.toJson(snapshot);reason="旧系统已登账藏品迁入，待独立复核";refBizId=batch
            }
            id
        }
        return CollectionImportCommitResultVo(valid=true,createdIds=created)
    }
}

/** 已迁移正式登记不能重新登账；首次本系统编目仅补齐编目专属内容。 */
internal object CollectionMigrationCatalogPolicy {
    fun initial(collection: CollectionDao): Boolean {
        if(!collection.registered || collection.published || collection.catalogStatus==EnumCatalogStatus.APPROVED) return false
        if(CollectionCatalogVersionDao.find { (CollectionCatalogVersions.collectionId eq collection.id.value) and (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED) }.any()) return false
        return confirmed(collection)
    }
    fun confirmed(collection: CollectionDao): Boolean {
        if (!collection.registered) return false
        return CollectionLedgerLogDao.find { (CollectionLedgerLogs.orgId eq collection.orgId) and (CollectionLedgerLogs.collectionId eq collection.id.value) and (CollectionLedgerLogs.field eq "legacyMigration") }.any {
            val snapshot=runCatching { JsonUtils.fromJson(it.after,CollectionMigrationSnapshotVo::class.java) }.getOrNull()
            snapshot?.verification=="CONFIRMED" && CollectionMigrationPolicy.date(snapshot.originalRegisteredAt)==collection.registeredAt
        }
    }
    fun requireUnchanged(collection: CollectionDao, content: CollectionCatalogContentVo) {
        val merged=CollectionCatalogMapper.mergeCollection(content,collection.toModel())
        if((merged!=content && merged.copy(actualQuantity=content.actualQuantity,actualQuantityUnit=content.actualQuantityUnit,dimensionUnit=content.dimensionUnit)!=content) ||
            (merged!=content && !CollectionCatalogMapper.matchesSnapshot(CollectionCatalogMapper.fromCollection(collection.toModel()).copy(actualQuantity=content.actualQuantity,actualQuantityUnit=content.actualQuantityUnit,dimensionUnit=content.dimensionUnit),collection.toModel())) ||
            content.quantity!=collection.quantity || content.unit!=collection.unit)
            throw BadRequestException("迁入藏品已经正式登账，首次编目只能补齐编目专属内容；请按迁入原账核对，正式信息订正须在编目核验后办理信息变更")
    }
}
