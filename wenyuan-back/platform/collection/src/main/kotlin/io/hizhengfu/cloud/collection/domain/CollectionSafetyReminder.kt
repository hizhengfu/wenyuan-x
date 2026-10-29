package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumCollectionSafetyDomain { CARE, JOINT, EMERGENCY, LOAN }
data class CollectionSafetyAssignee(val userId:String,val roles:Set<String> = emptySet(),val departmentId:String?=null)
data class CollectionSafetyTask(
    val key:String,val orgId:String,val domain:EnumCollectionSafetyDomain,val kind:String,val title:String,
    val sourceId:String,val recordId:String?=null,val collectionId:String?=null,val name:String?=null,
    val dueAt:LocalDateTime?=null,val status:String="PENDING",val basis:String,
    val assignees:List<CollectionSafetyAssignee> = emptyList(),val evidenceError:String?=null,
)
data class CollectionSafetyReminderSettingsVo(
    val previousId:String?=null,val enabled:Boolean,val domains:Set<EnumCollectionSafetyDomain>,
    val leadDays:Int,val repeatDays:Int,val coordinatorUserIds:Set<String> = emptySet(),
)
data class CollectionSafetyReminderSettingsRecord(val request:CollectionSafetyReminderSettingsVo,val actorUserId:String,val recordedAt:LocalDateTime)
data class CollectionSafetyReminderItem(val stateId:String,val taskKey:String,val fingerprint:String)
/** 只表示站内信主体及收件记录已入队，不表示已阅读或已履行业务。 */
interface CollectionSafetyReminderDeliveryPort {
    fun enqueue(orgId:String,userId:String,deliveryId:String,idempotencyKey:String):String
}
