package io.hizhengfu.cloud.collection.domain

data class EntryVoucherExistingCollectionVo(
    val collectionId: String,
    val stateHash: String,
)

/** 按征集条目明确关联已有待登记记录；未选择的条目仍正常新建。 */
data class EntryVoucherExecuteVo(
    val existingCollections: Map<String, EntryVoucherExistingCollectionVo> = emptyMap(),
)

data class EntryVoucherRegistrationTargetVo(
    val collectionId: String,
    val totalRegNo: String,
    val name: String,
    val stateHash: String,
)
