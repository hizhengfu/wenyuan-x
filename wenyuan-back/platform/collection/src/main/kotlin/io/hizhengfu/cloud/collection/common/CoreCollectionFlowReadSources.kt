package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import io.hizhengfu.cloud.collection.dao.*
import org.jetbrains.exposed.v1.core.*
import org.springframework.stereotype.Component

@Component
class CoreCollectionFlowReadSources : CollectionFlowReadSources {
    override fun sources() = listOf(
        source(CollectionConst.BIZ_ACQUISITION, Acquisitions, Acquisitions.orgId, "COLLECTION_ACQUIRE"),
        source(CollectionConst.BIZ_ACQUISITION_PLAN, AcquisitionPlans, AcquisitionPlans.orgId, "COLLECTION_ACQUIRE"),
        source(CollectionConst.BIZ_EXHIBITION, Exhibitions, Exhibitions.orgId, "COLLECTION_STORAGE"),
        source(CollectionConst.BIZ_INVENTORY, Inventories, Inventories.orgId, "COLLECTION_STORAGE"),
        source(CollectionConst.BIZ_REPRODUCTION, Reproductions, Reproductions.orgId, "COLLECTION_STORAGE"),
        source(CollectionConst.BIZ_CHANGE, CollectionChanges, CollectionChanges.orgId, "COLLECTION_LEDGER_CORRECT"),
        source(CollectionConst.BIZ_DEREGISTER, Deregistrations, Deregistrations.orgId, "COLLECTION_DEREGISTER"),
        source(CollectionConst.BIZ_LOAN, Loans, Loans.orgId, "COLLECTION_STORAGE"),
        source(CollectionConst.BIZ_REQUISITION, Requisitions, Requisitions.orgId, "COLLECTION_STORAGE"),
        source(CollectionConst.BIZ_RETURN, CollectionReturns, CollectionReturns.orgId, "COLLECTION_STORAGE"),
        source(CollectionConst.BIZ_APPRAISAL, Appraisals, Appraisals.orgId, "COLLECTION_APPRAISE"),
        source(CollectionConst.BIZ_SAMPLING, CollectionSamplings, CollectionSamplings.orgId, CollectionConst.SAMPLING, allowSelect = false),
        source(CollectionConst.BIZ_WAREHOUSE_ENTRY, WarehouseEntryApplications, WarehouseEntryApplications.orgId, CollectionConst.WAREHOUSE_ENTRY_APPLY)
            .let { it.copy(permissions = it.permissions + CollectionConst.WAREHOUSE_ENTRY_VERIFY) },
        source(CollectionConst.BIZ_TRANSFER, CollectionTransfers, CollectionTransfers.orgId, CollectionConst.CROSS_ORG, CollectionTransfers.toOrgId)
            .let { it.copy(submitPermissions = setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.STORAGE)) },
    )

    private fun source(business: String, table: UIDTable, org: Column<String>, permission: String, otherOrg: Column<String?>? = null, allowSelect: Boolean = true) =
        CollectionFlowReadSource(business, table, setOf("ADMIN", CollectionConst.ADMIN, permission) + if (allowSelect) setOf(CollectionConst.SELECT) else emptySet(),
            submitPermissions = setOf("ADMIN", CollectionConst.ADMIN, permission), submitOrgCondition = { org inList it },
            taskIdentityColumns = listOfNotNull(org, otherOrg)) { ids ->
            (org inList ids) or (otherOrg?.let { it inList ids } ?: Op.FALSE)
        }
}
