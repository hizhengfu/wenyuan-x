package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.collection.dao.WarehouseVo

/** Retain the old location identity and its monitoring history while correcting the current directory. */
data class WarehouseLocationCorrectionVo(
    val replacement: WarehouseVo,
    val reason: String,
    val signedFileIds: String,
)

data class WarehouseLocationCorrectionResultVo(
    val retiredLocationId: String,
    val replacementLocationId: String,
    val archiveId: String,
)
