package io.hizhengfu.cloud.collection.reminder

import io.hizhengfu.cloud.collection.domain.CollectionSafetyReminderDeliveryPort
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.message.dao.*
import io.hizhengfu.cloud.message.domain.*
import io.hizhengfu.cloud.message.service.MessageNotifyService
import io.hizhengfu.cloud.sys.system.dao.UserDao
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** 收藏模块使用统一消息服务投递站内信；不启用默认短信、邮箱或第三方推送通道。 */
@Component
@Transactional(propagation=Propagation.MANDATORY)
class CollectionSafetyInAppDelivery(private val messages:MessageNotifyService):CollectionSafetyReminderDeliveryPort {
    override fun enqueue(orgId:String,userId:String,deliveryId:String,idempotencyKey:String):String {
        val user=UserDao.find {Users.id eq userId}.forUpdate().singleOrNull()
        if(user?.enabled!=true)throw BadRequestException("安全待办收件账号不可用")
        val id=messages.send(MessageNotifyCreateVo(
            title="藏品安全待办提醒",content="您有藏品安全事项需要核对。请进入安全待办页面查看当前任务、责任分工和原始记录。提醒不代表事项已经办理。",
            summary="请查看当前安全待办与原始记录",channels=setOf(EnumMessageNotifyChannel.IN_APP),scope=MessageNotifyTargetSpec.users(userId),
            bizType="COLLECTION_SAFETY_REMINDER",bizId=deliveryId,idempotencyKey=idempotencyKey,
            clickUrl="/collection/safety-task?orgId="+URLEncoder.encode(orgId,StandardCharsets.UTF_8),
        ))
        val notify=MessageNotifyDao.findById(id)
        val users=MessageNotifyRecipientDao.find {MessageNotifyRecipients.notifyId eq id}.map {it.userId}
        if(notify?.channels!="IN_APP" || notify.bizType!="COLLECTION_SAFETY_REMINDER" || notify.bizId!=deliveryId || notify.idempotencyKey!=idempotencyKey || notify.recipientCount!=1 || users!=listOf(userId))throw BadRequestException("安全待办站内信收件记录不完整")
        return id
    }
}
