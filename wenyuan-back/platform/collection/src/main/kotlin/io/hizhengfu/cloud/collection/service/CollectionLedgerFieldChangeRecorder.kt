package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionLedgerLogDao
import io.hizhengfu.cloud.collection.domain.EnumCatalogStatus
import io.hizhengfu.cloud.collection.domain.EnumLedgerAction

/** 随正式业务事务记录主档字段变动；重复办理或未改变值时不产生虚假更正。 */
internal object CollectionLedgerFieldChangeRecorder {
    fun catalogStatus(collection: CollectionDao, value: EnumCatalogStatus, reason: String, refBizId: String) =
        record(collection, "catalogStatus", collection.catalogStatus.name, value.name, reason, refBizId) { collection.catalogStatus = value }

    fun warehouse(collection: CollectionDao, value: String?, reason: String, refBizId: String) =
        record(collection, "warehouseId", collection.warehouseId, value, reason, refBizId) { collection.warehouseId = value }

    fun entryVoucher(collection: CollectionDao, value: String?, reason: String, refBizId: String) =
        record(collection, "entryVoucherNo", collection.entryVoucherNo, value, reason, refBizId) { collection.entryVoucherNo = value }

    fun deregVoucher(collection: CollectionDao, value: String?, reason: String, refBizId: String) =
        record(collection, "deregVoucherNo", collection.deregVoucherNo, value, reason, refBizId) { collection.deregVoucherNo = value }

    fun inventoryHold(collection: CollectionDao, value: Boolean, reason: String, refBizId: String) =
        record(collection, "inventoryHold", collection.inventoryHold.toString(), value.toString(), reason, refBizId) { collection.inventoryHold = value }

    private fun record(
        collection: CollectionDao,
        fieldName: String,
        beforeValue: String?,
        afterValue: String?,
        description: String,
        businessId: String,
        apply: () -> Unit,
    ) {
        if (beforeValue == afterValue) return
        apply()
        CollectionLedgerLogDao.new {
            orgId = collection.orgId
            collectionId = collection.id.value
            totalRegNo = collection.totalRegNo
            action = EnumLedgerAction.CORRECT
            field = fieldName
            before = beforeValue
            after = afterValue
            reason = description
            refBizId = businessId
        }
    }
}
