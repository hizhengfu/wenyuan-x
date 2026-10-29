package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst as C
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import org.jetbrains.exposed.v1.core.inList
import org.springframework.stereotype.Component

@Component
class CollectionBusinessOriginalFileSource : CollectionOriginalFileSource {
    companion object {
        const val MOVEMENT = "COLLECTION_STORAGE_MOVEMENT"
        const val WAREHOUSE = "COLLECTION_WAREHOUSE_FILES"
        const val TRACKING_IMPORT = "COLLECTION_TRACKING_IMPORT"
    }
    override fun references(businessType: String?, businessId: String?): List<CollectionOriginalFileReference> {
        val references = super.references(businessType, businessId)
        val movements = references.filter { it.businessType == MOVEMENT }.map { it.businessId }.distinct()
            .chunked(200).flatMap { batch -> StorageMovementDao.find { StorageMovements.id inList batch }.toList() }
            .associateBy { it.id.value }
        val appraisals = references.filter { it.businessType == C.BIZ_APPRAISAL }.map { it.businessId }.distinct()
            .chunked(200).flatMap { batch -> AppraisalDao.find { Appraisals.id inList batch }.toList() }
            .associateBy { it.id.value }
        val transfers = movements.values.filter { it.reason == EnumMoveReason.TRANSFER }.mapNotNull { it.refBizId }.distinct()
            .chunked(200).flatMap { batch -> CollectionTransferDao.find { CollectionTransfers.id inList batch }.toList() }
            .associateBy { it.id.value }
        val relocations = movements.values.filter { it.reason == EnumMoveReason.EMERGENCY }.mapNotNull { it.refBizId }.distinct()
            .chunked(200).flatMap { batch -> IncidentRelocationDao.find { IncidentRelocations.id inList batch }.toList() }
            .associateBy { it.id.value }
        return references.map { ref ->
            if (ref.businessType == C.BIZ_APPRAISAL) {
                val parent = appraisals[ref.businessId]
                return@map if (parent?.orgId == ref.orgId) ref else ref.copy(businessType = "COLLECTION_UNRESOLVED_APPRAISAL")
            }
            if (ref.businessType != MOVEMENT) return@map ref
            val movement = movements[ref.businessId]
                ?: return@map ref.copy(businessType = "COLLECTION_UNRESOLVED_MOVEMENT")
            val source = movement.refBizId
            when (movement.reason) {
                EnumMoveReason.TRANSFER -> {
                    val transfer = transfers[source]
                    if (transfer == null || ref.orgId !in setOfNotNull(transfer.orgId, transfer.toOrgId) || transfer.collectionId != movement.collectionId)
                        ref.copy(businessType = "COLLECTION_UNRESOLVED_MOVEMENT")
                    else ref.copy(businessType = C.BIZ_TRANSFER, businessId = transfer.id.value)
                }
                EnumMoveReason.EMERGENCY -> {
                    val relocation = relocations[source]
                    if (relocation == null || relocation.orgId != ref.orgId || relocation.collectionId != movement.collectionId)
                        ref.copy(businessType = "COLLECTION_UNRESOLVED_MOVEMENT")
                    else ref.copy(businessType = C.BIZ_INCIDENT_RELOCATION, businessId = relocation.id.value)
                }
                else -> ref
            }
        }.distinct()
    }
    override val businessLabels = listOf(
        CollectionOriginalBusinessLabel(AcquisitionPlans, AcquisitionPlans.orgId, C.BIZ_ACQUISITION_PLAN, listOf(AcquisitionPlans.title)),
        CollectionOriginalBusinessLabel(Loans, Loans.orgId, C.BIZ_LOAN, listOf(Loans.protocolNo, Loans.title)),
        CollectionOriginalBusinessLabel(Requisitions, Requisitions.orgId, C.BIZ_REQUISITION, listOf(Requisitions.documentNo)),
        CollectionOriginalBusinessLabel(CollectionReturns, CollectionReturns.orgId, C.BIZ_RETURN, listOf(CollectionReturns.sourceDocumentNo)),
        CollectionOriginalBusinessLabel(Deregistrations, Deregistrations.orgId, C.BIZ_DEREGISTER, listOf(Deregistrations.approvalDocNo), Deregistrations.collectionId),
        CollectionOriginalBusinessLabel(CollectionTransfers, CollectionTransfers.orgId, C.BIZ_TRANSFER, listOf(CollectionTransfers.approvalDocNo), CollectionTransfers.collectionId),
        CollectionOriginalBusinessLabel(CollectionChanges, CollectionChanges.orgId, C.BIZ_CHANGE, collectionId=CollectionChanges.collectionId),
        CollectionOriginalBusinessLabel(Inventories, Inventories.orgId, C.BIZ_INVENTORY, listOf(Inventories.batchName), Inventories.collectionId),
        CollectionOriginalBusinessLabel(InventoryBatches, InventoryBatches.orgId, InventoryComplianceService.BIZ, listOf(InventoryBatches.batchName)),
        CollectionOriginalBusinessLabel(Appraisals, Appraisals.orgId, C.BIZ_APPRAISAL, collectionId=Appraisals.collectionId),
        CollectionOriginalBusinessLabel(Reproductions, Reproductions.orgId, C.BIZ_REPRODUCTION, collectionId=Reproductions.collectionId),
        CollectionOriginalBusinessLabel(EntryVouchers, EntryVouchers.orgId, AccessionReceiptService.BIZ, listOf(EntryVouchers.voucherNo)),
        CollectionOriginalBusinessLabel(Exhibitions, Exhibitions.orgId, C.BIZ_EXHIBITION, listOf(Exhibitions.exhibitionNo, Exhibitions.title)),
        CollectionOriginalBusinessLabel(WarehouseEntryApplications, WarehouseEntryApplications.orgId, C.BIZ_WAREHOUSE_ENTRY, listOf(WarehouseEntryApplications.applicationNo)),
        CollectionOriginalBusinessLabel(StorageMovements, StorageMovements.orgId, MOVEMENT, listOf(StorageMovements.documentNo, StorageMovements.totalRegNoAtMovement, StorageMovements.collectionNameAtMovement)),
        CollectionOriginalBusinessLabel(WarehouseLogs, WarehouseLogs.orgId, WarehouseDiaryService.BIZ, listOf(WarehouseLogs.logDate)),
        CollectionOriginalBusinessLabel(Warehouses, Warehouses.orgId, WAREHOUSE, listOf(Warehouses.name)),
        CollectionOriginalBusinessLabel(Warehouses, Warehouses.orgId, WarehouseStaffingService.BIZ, listOf(Warehouses.name)),
        CollectionOriginalBusinessLabel(AnnualReportArchives, AnnualReportArchives.orgId, AnnualReportEvidenceService.BIZ, listOf(AnnualReportArchives.year)),
        CollectionOriginalBusinessLabel(CollectionWordImports, CollectionWordImports.orgId, TRACKING_IMPORT, listOf(CollectionWordImports.sourceFileName), CollectionWordImports.collectionId),
        CollectionOriginalBusinessLabel(IncidentRelocations, IncidentRelocations.orgId, C.BIZ_INCIDENT_RELOCATION, collectionId=IncidentRelocations.collectionId),
    )
    override val tables = listOf(
        CollectionOriginalFileTable(AcquisitionPlans, AcquisitionPlans.orgId, C.BIZ_ACQUISITION_PLAN, listOf(AcquisitionPlans.attachmentIds)),
        CollectionOriginalFileTable(Loans, Loans.orgId, C.BIZ_LOAN, listOf(Loans.attachmentIds, Loans.conditionPhotoIds, Loans.signedVoucherAttachmentIds), listOf(Loans.dossier, Loans.handover)),
        CollectionOriginalFileTable(Requisitions, Requisitions.orgId, C.BIZ_REQUISITION, listOf(Requisitions.attachmentIds, Requisitions.conditionPhotoIds, Requisitions.signedVoucherAttachmentIds), listOf(Requisitions.dossier, Requisitions.handover, Requisitions.sourceRetirement)),
        CollectionOriginalFileTable(CollectionReturns, CollectionReturns.orgId, C.BIZ_RETURN, listOf(CollectionReturns.attachmentIds, CollectionReturns.damagePhotoIds, CollectionReturns.signedVoucherAttachmentIds), listOf(CollectionReturns.inspection)),
        CollectionOriginalFileTable(Deregistrations, Deregistrations.orgId, C.BIZ_DEREGISTER, listOf(Deregistrations.attachmentIds), listOf(Deregistrations.dossier)),
        CollectionOriginalFileTable(CollectionTransfers, CollectionTransfers.orgId, C.BIZ_TRANSFER, listOf(CollectionTransfers.attachmentIds), listOf(CollectionTransfers.dossier, CollectionTransfers.execution)),
        CollectionOriginalFileTable(CollectionChanges, CollectionChanges.orgId, C.BIZ_CHANGE, listOf(CollectionChanges.evidenceFileIds, CollectionChanges.signedChangeFileIds), listOf(CollectionChanges.before, CollectionChanges.content)),
        CollectionOriginalFileTable(Inventories, Inventories.orgId, C.BIZ_INVENTORY, listOf(Inventories.attachmentIds)),
        CollectionOriginalFileTable(InventoryBatches, InventoryBatches.orgId, InventoryComplianceService.BIZ, listOf(InventoryBatches.reportAttachmentIds), listOf(InventoryBatches.plan, InventoryBatches.checks)),
        CollectionOriginalFileTable(Appraisals, Appraisals.orgId, C.BIZ_APPRAISAL, listOf(Appraisals.attachmentIds)),
        CollectionOriginalFileTable(AppraisalMembers, AppraisalMembers.orgId, C.BIZ_APPRAISAL, listOf(AppraisalMembers.signatureFileId), owner=AppraisalMembers.appraisalId),
        CollectionOriginalFileTable(Reproductions, Reproductions.orgId, C.BIZ_REPRODUCTION, listOf(Reproductions.images)),
        CollectionOriginalFileTable(EntryVouchers, EntryVouchers.orgId, AccessionReceiptService.BIZ, listOf(EntryVouchers.attachmentIds)),
        CollectionOriginalFileTable(Exhibitions, Exhibitions.orgId, C.BIZ_EXHIBITION, listOf(Exhibitions.coverImage, Exhibitions.attachmentIds)),
        CollectionOriginalFileTable(WarehouseEntryApplications, WarehouseEntryApplications.orgId, C.BIZ_WAREHOUSE_ENTRY, listOf(WarehouseEntryApplications.attachmentIds), listOf(WarehouseEntryApplications.dossier, WarehouseEntryApplications.entryRecord, WarehouseEntryApplications.exitRecord)),
        CollectionOriginalFileTable(StorageMovements, StorageMovements.orgId, MOVEMENT, listOf(StorageMovements.attachmentIds)),
        CollectionOriginalFileTable(WarehouseLogs, WarehouseLogs.orgId, WarehouseDiaryService.BIZ, listOf(WarehouseLogs.attachmentIds), listOf(WarehouseLogs.diary)),
        CollectionOriginalFileTable(Warehouses, Warehouses.orgId, WAREHOUSE, listOf(Warehouses.thumbnail)),
        CollectionOriginalFileTable(Warehouses, Warehouses.orgId, WarehouseStaffingService.BIZ, json=listOf(Warehouses.staffing), bindings=false),
        CollectionOriginalFileTable(AnnualReportArchives, AnnualReportArchives.orgId, AnnualReportEvidenceService.BIZ, listOf(AnnualReportArchives.reportAttachmentIds)),
        CollectionOriginalFileTable(CollectionWordImports, CollectionWordImports.orgId, TRACKING_IMPORT, listOf(CollectionWordImports.sourceFileId)),
    )
}
