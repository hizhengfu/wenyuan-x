package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 保管部门实际发出的安全提前归库要求，不改写已批准的提用方案。 */
data class RequisitionRecallVo(
    val sourceRevision:String,
    val previousId:String?=null,
    val collectionIds:Set<String>,
    val issuedAt:LocalDateTime,
    val returnBy:LocalDateTime,
    val reason:String,
    val safetyFindings:String,
    val protectiveMeasures:String,
    val notification:String,
    val fileIds:String,
)
data class RequisitionRecallRecord(
    val id:String,val orgId:String,val requisitionId:String,val request:RequisitionRecallVo,
    val sourceSnapshot:String,val custodyId:String?,val outboundArchiveId:String,val outboundDigest:String,
    val keeperUserId:String,val useUserId:String,val registeredAt:LocalDateTime,
)
