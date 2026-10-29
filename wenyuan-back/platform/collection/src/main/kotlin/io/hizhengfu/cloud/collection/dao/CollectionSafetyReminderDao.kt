package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.json.json

object CollectionSafetyReminderSettings : OrgAuditUIDTable("col_safety_reminder_settings") {
    val version=integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val record=json("record",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionSafetyReminderSettingsRecord::class.java)!!})
    init {uniqueIndex(shortIndexName("org_version",unique=true),orgId,version)}
}
class CollectionSafetyReminderSettingsDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionSafetyReminderSettings) {
    companion object:OrgAuditUIDEntityClass<CollectionSafetyReminderSettingsDao>(CollectionSafetyReminderSettings)
    var version by CollectionSafetyReminderSettings.version
    var record by CollectionSafetyReminderSettings.record
}
object CollectionSafetyReminderStates : OrgAuditUIDTable("col_safety_reminder_state") {
    val userId=varchar("user_id",64)
    val taskKey=varchar("task_key",64)
    val fingerprint=varchar("fingerprint",64)
    val active=bool("active").default(true)
    val lastFingerprint=varchar("last_fingerprint",64).nullable()
    val lastNotifiedAt=datetime("last_notified_at").nullable()
    val pendingDeliveryId=varchar("pending_delivery_id",64).nullable()
    init {uniqueIndex(shortIndexName("target",unique=true),orgId,userId,taskKey)}
}
class CollectionSafetyReminderStateDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionSafetyReminderStates) {
    companion object:OrgAuditUIDEntityClass<CollectionSafetyReminderStateDao>(CollectionSafetyReminderStates)
    var userId by CollectionSafetyReminderStates.userId
    var taskKey by CollectionSafetyReminderStates.taskKey
    var fingerprint by CollectionSafetyReminderStates.fingerprint
    var active by CollectionSafetyReminderStates.active
    var lastFingerprint by CollectionSafetyReminderStates.lastFingerprint
    var lastNotifiedAt by CollectionSafetyReminderStates.lastNotifiedAt
    var pendingDeliveryId by CollectionSafetyReminderStates.pendingDeliveryId
}
object CollectionSafetyReminderDeliveries : OrgAuditUIDTable("col_safety_reminder_delivery") {
    val userId=varchar("user_id",64)
    val idempotencyKey=varchar("idempotency_key",64).uniqueIndex(shortIndexName("idempotency",unique=true))
    val settingsId=varchar("settings_id",64)
    val items=json("items",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,Array<CollectionSafetyReminderItem>::class.java)!!.toList()})
    val state=varchar("state",24).default("PENDING")
    val requestedAt=datetime("requested_at")
    val nextAttemptAt=datetime("next_attempt_at")
    val attempts=integer("attempts", checkConstraintName = shortCheckConstraintName("attempts")).default(0)
    val lastError=varchar("last_error",100).nullable()
    val messageId=varchar("message_id",64).nullable()
    val queuedAt=datetime("queued_at").nullable()
    init {index(shortIndexName("due"),false,state,nextAttemptAt)}
}
class CollectionSafetyReminderDeliveryDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionSafetyReminderDeliveries) {
    companion object:OrgAuditUIDEntityClass<CollectionSafetyReminderDeliveryDao>(CollectionSafetyReminderDeliveries)
    var userId by CollectionSafetyReminderDeliveries.userId
    var idempotencyKey by CollectionSafetyReminderDeliveries.idempotencyKey
    var settingsId by CollectionSafetyReminderDeliveries.settingsId
    var items by CollectionSafetyReminderDeliveries.items
    var state by CollectionSafetyReminderDeliveries.state
    var requestedAt by CollectionSafetyReminderDeliveries.requestedAt
    var nextAttemptAt by CollectionSafetyReminderDeliveries.nextAttemptAt
    var attempts by CollectionSafetyReminderDeliveries.attempts
    var lastError by CollectionSafetyReminderDeliveries.lastError
    var messageId by CollectionSafetyReminderDeliveries.messageId
    var queuedAt by CollectionSafetyReminderDeliveries.queuedAt
}
object CollectionSafetyReminderAttempts : OrgAuditUIDTable("col_safety_reminder_attempt") {
    val deliveryId=varchar("delivery_id",64).index(shortIndexName("delivery_id"))
    val attemptedAt=datetime("attempted_at")
    val result=varchar("result",24)
    val messageId=varchar("message_id",64).nullable()
}
class CollectionSafetyReminderAttemptDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionSafetyReminderAttempts) {
    companion object:OrgAuditUIDEntityClass<CollectionSafetyReminderAttemptDao>(CollectionSafetyReminderAttempts)
    var deliveryId by CollectionSafetyReminderAttempts.deliveryId
    var attemptedAt by CollectionSafetyReminderAttempts.attemptedAt
    var result by CollectionSafetyReminderAttempts.result
    var messageId by CollectionSafetyReminderAttempts.messageId
}
