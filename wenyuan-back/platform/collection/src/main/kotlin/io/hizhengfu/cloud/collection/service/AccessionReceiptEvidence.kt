package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.AccessionReceiptVo
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 在调用方完成业务授权后核对原件；读取不新建阶段、不重建摘要或文件内容基线。 */
@Component
@Transactional(readOnly = true)
class AccessionReceiptEvidence(
    private val archives: CollectionPermanentArchiveService,
    private val mainFileReadPolicy: CollectionMainFileReadPolicy,
) {
    data class Verified(
        val archives: List<Map<String, Any?>>,
        val legacyFileContentUnverified: Boolean,
        val actualAcquiredOn: java.time.LocalDate,
        val registeredCollection: CollectionVo?,
    )

    fun requireRead(row: AccessionReceiptDao, voucher: EntryVoucherDao,verifyContent:Boolean=true): Verified {
        if (row.voucherId != voucher.id.value || row.orgId != voucher.orgId || row.version < 1 ||
            row.content.acquisitionId != row.acquisitionId)
            fail("核收内容与原凭证条目标识不一致")
        val source = try { JsonUtils.mapper.readTree(row.sourceSnapshot) }
            catch (_: tools.jackson.core.JacksonException) { fail("核收取得依据无法解析，请核对原件") }
        if (source?.path("id")?.asString() != row.acquisitionId || source.path("orgId").asString() != row.orgId)
            fail("核收取得依据与原条目或所属馆不一致")
        val acquired = try { JsonUtils.mapper.treeToValue(source.path("actualAcquiredOn"), java.time.LocalDate::class.java) }
            catch (_: tools.jackson.core.JacksonException) { fail("核收取得日期原件缺失或格式不一致") }
            ?: fail("核收取得日期原件缺失或格式不一致")
        val originals = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq AccessionReceiptService.BIZ) and (CollectionPermanentArchives.bizId eq row.id.value)
        }.toList()
        if (originals.any { it.orgId != row.orgId || it.phase !in setOf("RECORDED", "LINKED", "REGISTERED") })
            fail("核收永久原件所属馆或阶段不一致")
        val expected = linkedMapOf<String, Any>("RECORDED" to mapOf(
            "voucherNo" to voucher.voucherNo, "source" to row.sourceSnapshot, "content" to row.content))
        val collection = row.collectionId?.takeIf(String::isNotBlank)?.let {
            CollectionDao.findById(it) ?: fail("核收关联的登记记录缺失")
        }
        val registered = collection?.let { originalRegistration(it, row.orgId, voucher.voucherNo) }
        if (registered != null) {
            expected["LINKED"] = mapOf("collectionId" to registered.id, "totalRegNo" to registered.totalRegNo, "content" to row.content)
            if (registered.registered) {
                val history = AccessionReceiptDao.find { AccessionReceipts.acquisitionId eq row.acquisitionId }.sortedBy { it.version }
                val latest = history.lastOrNull() ?: fail("核收订正版本链缺失")
                if (latest.collectionId != row.collectionId || latest.voucherId != row.voucherId ||
                    history.map { it.version } != (1..latest.version).toList())
                    fail("正式登记的核收订正版本链不一致")
                val registrationOriginal = CollectionPermanentArchiveDao.find {
                    (CollectionPermanentArchives.bizType eq AccessionReceiptService.BIZ) and
                        (CollectionPermanentArchives.bizId eq latest.id.value) and
                        (CollectionPermanentArchives.phase eq "REGISTERED")
                }.singleOrNull() ?: fail("最新核收版本的正式登记原件缺失")
                if (registrationOriginal.digest != CollectionCareEvidence.digest(registrationOriginal.snapshot))
                    fail("核收正式登记原件摘要不一致")
                val registrationSnapshot = try { JsonUtils.mapper.readTree(registrationOriginal.snapshot) }
                    catch (_: tools.jackson.core.JacksonException) { fail("核收正式登记原件无法解析") }
                val supersededNode = registrationSnapshot?.get("supersededReceiptIds")
                val supersededIds: List<String>? = if (supersededNode == null) null else {
                    if (!supersededNode.isArray || supersededNode.any { !it.isString }) fail("核收正式登记版本标记无效")
                    supersededNode.filter { it.isString }.map { it.asString() }.also { ids ->
                        if (ids != history.dropLast(1).filter { it.collectionId == latest.collectionId }.map { it.id.value })
                            fail("核收正式登记版本标记与原件链不一致")
                    }
                }
                if (supersededIds == null || row.id.value !in supersededIds) {
                    val registeredSnapshot = mutableMapOf<String, Any?>(
                        "collectionId" to registered.id, "totalRegNo" to registered.totalRegNo,
                        "registeredAt" to registered.registeredAt, "historicalConfirmedAt" to registered.historicalConfirmedAt)
                    if (supersededIds != null) registeredSnapshot["supersededReceiptIds"] = supersededIds
                    expected["REGISTERED"] = registeredSnapshot
                }
            }
        }
        if (originals.map { it.phase }.toSet() != expected.keys || originals.size != expected.size)
            fail("核收阶段永久原件缺失、重复或与登记状态不一致")
        var legacy = false
        val result = originals.sortedBy { it.createTime }.map { original ->
            if (!archives.verifyExisting(original, row.orgId, AccessionReceiptService.BIZ, row.id.value,
                    original.phase, expected.getValue(original.phase), files(row.content),verifyContent=verifyContent)) legacy = true
            mapOf("id" to original.id.value, "phase" to original.phase, "snapshot" to original.snapshot,
                "digest" to original.digest, "archivedAt" to original.createTime,
                "files" to CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq original.id.value }.map {
                    mapOf("fileId" to it.fileId, "metadata" to it.metadata)
                })
        }
        return Verified(if(verifyContent) result else archives.readForDisplay(AccessionReceiptService.BIZ,row.id.value,row.orgId).archives, legacy, acquired, registered)
    }

    /** 调拨后以原馆已冻结且已执行的移交账为准，不拿接收馆的现时主档重写入馆事实。 */
    private fun originalRegistration(collection: CollectionDao, orgId: String, voucherNo: String): CollectionVo {
        if (collection.orgId == orgId && collection.entryVoucherNo == voucherNo) return collection.toModel()
        val candidates = CollectionRetainedLedgerDao.find {
            (CollectionRetainedLedgers.orgId eq orgId) and (CollectionRetainedLedgers.collectionId eq collection.id.value)
        }.filter { it.snapshot.collection.entryVoucherNo == voucherNo }
        val retained = candidates.singleOrNull() ?: fail("缺少唯一对应核收原凭证的原馆留存总账")
        if (retained.snapshot.identity != CollectionRetainedLedgerIdentity(
                orgId, collection.id.value, retained.transferId, retained.targetOrgId))
            fail("原馆留存总账缺少冻结的调拨关联")
        mainFileReadPolicy.verifiedEdge(retained)
        return retained.snapshot.collection
    }

    private fun files(value: AccessionReceiptVo) = (value.pieces.map { it.photoFileIds } +
        listOfNotNull(value.signedReceiptFileIds, value.lateHandlingFileIds, value.correctionFileIds)).joinToString(",")
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
