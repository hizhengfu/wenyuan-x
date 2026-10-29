package io.hizhengfu.cloud.collection.reminder

import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.message.dao.*
import io.hizhengfu.cloud.message.domain.*
import io.hizhengfu.cloud.message.service.*
import io.hizhengfu.cloud.sys.system.dao.*
import io.hizhengfu.cloud.sys.file.service.FileService
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.core.context.SecurityContextHolder

class CollectionSafetyInAppDeliveryTest {
    private lateinit var db:Database
    private val config=mock(MessageNotifyConfigReader::class.java).also {`when`(it.defaultChannels()).thenReturn(setOf(EnumMessageNotifyChannel.SMS,EnumMessageNotifyChannel.EMAIL))}
    private val dispatcher=mock(MessageChannelDispatcher::class.java)
    private fun adapter(resolver:MessageRecipientResolver=MessageRecipientResolver())=CollectionSafetyInAppDelivery(MessageNotifyService(resolver,MessageIdempotencyResolver(),config,dispatcher,MessageHtmlSanitizer(),mock(FileService::class.java),ApplicationEventPublisher {}))
    private fun fixture(block:()->Unit) {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context);SecurityContextHolder.clearContext()
        try {
            db=Database.connect("jdbc:sqlite:file:inapp-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC",databaseConfig=DatabaseConfig {useNestedTransactions=true})
            transaction(db) {SchemaUtils.create(Users,MessageNotifies,MessageNotifyRecipients);UserDao.new("keeper"){name="保管人员";loginName="keeper";password="unused";enabled=true};block()}
        }finally{context.close();SecurityContextHolder.clearContext()}
    }
    @Test fun `real notify and one unread recipient are idempotently queued only in app`()=fixture {
        val a=adapter();val id=a.enqueue("museum","keeper","batch","stable-key");assertEquals(id,a.enqueue("museum","keeper","batch","stable-key"))
        val n=MessageNotifyDao[id];assertEquals("IN_APP",n.channels);assertEquals(1,n.recipientCount);assertEquals("/collection/safety-task?orgId=museum",n.clickUrl)
        assertNull(n.attachments);assertNull(n.emailBody);assertNull(n.smsTemplateCode)
        assertEquals("藏品安全待办提醒",n.title);assertFalse(n.content.orEmpty().contains("batch"))
        assertEquals(1,MessageNotifyDao.all().toList().size);val r=MessageNotifyRecipientDao.all().single();assertEquals("keeper",r.userId);assertEquals(EnumMessageInAppReadStatus.UNREAD,r.readStatus)
        verify(config,never()).defaultChannels();verify(dispatcher,times(1)).dispatchAsync(id)
    }
    @Test fun `recipient filtering rolls back message and cannot be recorded as successful`()=fixture {
        val empty=mock(MessageRecipientResolver::class.java,org.mockito.stubbing.Answer<Any?> {c->if(c.method.name=="resolve")emptyList<UserDao>() else RETURNS_DEFAULTS.answer(c)})
        val a=adapter(empty);TransactionManager.current().entityCache.flush()
        assertThrows(BadRequestException::class.java){transaction(db){a.enqueue("museum","keeper","batch","stable-key")}}
        assertEquals(0,MessageNotifyDao.all().toList().size);assertEquals(0,MessageNotifyRecipientDao.all().toList().size)
        UserDao["keeper"].enabled=false
        assertThrows(BadRequestException::class.java){adapter().enqueue("museum","keeper","new","new-key")}
    }
    @Test fun `existing wrong business id or recipient is rejected and URL encodes museum`()=fixture {
        val a=adapter();a.enqueue("museum /?","keeper","batch","stable-key")
        assertEquals("/collection/safety-task?orgId=museum+%2F%3F",MessageNotifyDao.all().single().clickUrl)
        assertThrows(BadRequestException::class.java){a.enqueue("museum","keeper","another-batch","stable-key")}
    }
}
