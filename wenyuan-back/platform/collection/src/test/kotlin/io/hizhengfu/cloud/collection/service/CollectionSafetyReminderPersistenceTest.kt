package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.jetbrains.exposed.v1.dao.entityCache
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.*

class CollectionSafetyReminderPersistenceTest {
    private val scope=CollectionScopeResolver("multi","")
    private val recipients=mock(CollectionSafetyRecipientService::class.java,org.mockito.stubbing.Answer<Any?> {c->
        if(c.method.name=="resolve") {val task=c.getArgument<CollectionSafetyTask>(0);CollectionSafetyRecipients(task.assignees.map {it.userId}.toSet(),emptySet(),emptySet(),task.assignees.isEmpty())} else RETURNS_DEFAULTS.answer(c)
    })
    private val data=mutableListOf<CollectionSafetyTask>()
    private val source=object:CollectionSafetyTaskSource {
        override val domain=EnumCollectionSafetyDomain.CARE
        override fun tasks(orgId:String,at:LocalDateTime)=data.filter {it.orgId==orgId}.map {if(it.dueAt!=null)it.copy(status=if(it.dueAt<=at)"OVERDUE" else "SCHEDULED") else it}
    }
    private var time=Instant.parse("2026-09-08T08:00:00Z")
    private val clock=object:Clock(){override fun getZone()=ZoneOffset.UTC;override fun withZone(zone:ZoneId)=this;override fun instant()=time}
    private var fail=false
    private val sent=mutableListOf<String>()
    private val port=object:CollectionSafetyReminderDeliveryPort {
        override fun enqueue(orgId:String,userId:String,deliveryId:String,idempotencyKey:String):String {
            assertNull(SecurityContextHolder.getContext().authentication,"调度不得伪造管理员身份")
            Probe.insert {it[id]=deliveryId;it[key]=idempotencyKey}
            if(fail)throw IllegalStateException("fake transport failure")
            sent+=idempotencyKey;return "message-$deliveryId"
        }
    }
    private val service=CollectionSafetyReminderService(scope,recipients,listOf(source),listOf(port),clock)
    private val context=StaticApplicationContext()
    private lateinit var db:Database
    private object Probe:Table("reminder_queue_probe") {val id=varchar("id",64);val key=varchar("key",64);override val primaryKey=PrimaryKey(id)}
    @BeforeEach fun setup(){context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)}
    @AfterEach fun cleanup(){SecurityContextHolder.clearContext();context.close()}
    private fun actor(permission:String="ADMIN",org:String="museum") {
        val u=SecurityUser("admin","配置人员","admin","",groupId=org,groupIds=setOf(org),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(permission)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(u,null,u.authorities)
    }
    private fun fixture(block:()->Unit) {
        db=Database.connect("jdbc:sqlite:file:reminder-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC",databaseConfig=DatabaseConfig {useNestedTransactions=true})
        transaction(db) {
            SchemaUtils.create(CollectionOrgs,CollectionSafetyReminderSettings,CollectionSafetyReminderStates,CollectionSafetyReminderDeliveries,CollectionSafetyReminderAttempts,Probe)
            CollectionOrgDao.new("museum"){name="本馆";code="M"};CollectionOrgDao.new("other"){name="外馆";code="O"}
            actor();service.saveSettings("museum",settings());SecurityContextHolder.clearContext();block()
        }
    }
    private fun settings(previous:String?=null,enabled:Boolean=true,lead:Int=0,repeat:Int=7)=CollectionSafetyReminderSettingsVo(previous,enabled,setOf(EnumCollectionSafetyDomain.CARE),lead,repeat,emptySet())
    private fun task(key:String="a",user:String="keeper",basis:String="basis",due:LocalDateTime?=null,status:String="PENDING",org:String="museum")=CollectionSafetyTask(key,org,EnumCollectionSafetyDomain.CARE,"CHECK","检查养护",key,dueAt=due,status=status,basis=basis,assignees=listOf(CollectionSafetyAssignee(user)))
    private fun advance(days:Long=0,minutes:Long=0){time=time.plusSeconds(days*86400+minutes*60)}
    private fun row(id:String)=CollectionSafetyReminderDeliveryDao[id]
    @Test fun `new loan source is visible but is not silently enabled in default reminders`()=fixture {
        CollectionSafetyReminderSettingsDao.all().forEach {it.delete()}
        actor()
        assertTrue((service.options()["domains"] as Set<*>).contains(EnumCollectionSafetyDomain.LOAN))
        val context=service.context("museum")
        assertEquals(false,context["enabled"])
        assertFalse((context["reminderDomains"] as Set<*>).contains(EnumCollectionSafetyDomain.LOAN))
    }
    @Test fun `configured loan reminder follows source completion before delivery`()=fixture {
        var open=true
        val loan=object:CollectionSafetyTaskSource {
            override val domain=EnumCollectionSafetyDomain.LOAN
            override fun tasks(orgId:String,at:LocalDateTime)=if(orgId=="museum" && open)listOf(
                CollectionSafetyTask("loan-duty","museum",domain,"RETURN_DUE","借出藏品归还期限","loan-1",
                    dueAt=at.minusDays(1),status="OVERDUE",basis="agreement-1",assignees=listOf(CollectionSafetyAssignee("keeper")))) else emptyList()
        }
        val withLoan=CollectionSafetyReminderService(scope,recipients,listOf(source,loan),listOf(port),clock)
        actor();val previous=withLoan.settings("museum")["currentId"] as String
        withLoan.saveSettings("museum",CollectionSafetyReminderSettingsVo(previous,true,setOf(EnumCollectionSafetyDomain.LOAN),0,7))
        SecurityContextHolder.clearContext()
        val batch=withLoan.refresh("museum").single()
        assertEquals(listOf("loan-duty"),row(batch).items.map {it.taskKey})
        open=false
        assertNull(withLoan.deliver(batch));assertEquals("CANCELLED",row(batch).state)
        assertTrue(withLoan.refresh("museum").isEmpty())
    }
    private fun reject(block:()->Unit){TransactionManager.current().entityCache.flush();assertThrows(Exception::class.java){transaction(db){block()}}}
    @Test fun `aggregate only eligible tasks and suppress duplicate refresh until repeat`()=fixture {
        data+=listOf(task(),task("b"),task("future",due=LocalDateTime.now(clock).plusDays(3)),task("working",status="IN_PROGRESS"))
        val batch=service.refresh("museum").single();assertEquals(2,row(batch).items.size);assertTrue(service.refresh("museum").isEmpty())
        service.deliver(batch);assertEquals("QUEUED",row(batch).state);assertTrue(service.refresh("museum").isEmpty());assertEquals(1L,Probe.selectAll().count())
        advance(days=6);val future=service.refresh("museum").single();assertEquals(listOf("future"),row(future).items.map {it.taskKey});service.deliver(future)
        advance(days=1);val repeat=service.refresh("museum").single();assertEquals(setOf("a","b"),row(repeat).items.map {it.taskKey}.toSet())
        service.deliver(repeat);assertEquals(3,sent.toSet().size)
    }
    @Test fun `completion before delivery cancels whole stale batch and refresh keeps current tasks`()=fixture {
        data+=listOf(task(),task("b"));val old=service.refresh("museum").single();data.removeAt(0)
        assertNull(service.deliver(old));assertEquals("CANCELLED",row(old).state);assertEquals(0L,Probe.selectAll().count())
        val fresh=service.refresh("museum").single();assertEquals(listOf("b"),row(fresh).items.map {it.taskKey});service.deliver(fresh)
        data.clear();service.refresh("museum");assertTrue(CollectionSafetyReminderStateDao.all().none {it.active})
        data+=task("b");assertEquals(1,service.refresh("museum").size)
    }
    @Test fun `one completed task does not resend other already queued tasks`()=fixture {
        data+=listOf(task(),task("b"));service.deliver(service.refresh("museum").single());data.removeAt(0)
        assertTrue(service.refresh("museum").isEmpty());data+=task("new")
        val next=service.refresh("museum").single();assertEquals(listOf("new"),row(next).items.map {it.taskKey})
    }
    @Test fun `effective replacement cancels old recipient and notifies new actor`()=fixture {
        data+=task();val old=service.refresh("museum").single();data[0]=task(user="successor")
        assertNull(service.deliver(old));assertEquals("CANCELLED",row(old).state)
        val next=service.refresh("museum").single();assertEquals("successor",row(next).userId);service.deliver(next)
        assertFalse(CollectionSafetyReminderStateDao.all().single {it.userId=="keeper"}.active)
    }
    @Test fun `basis change is rechecked without requiring refresh and disabled settings stop pending`()=fixture {
        data+=task();val old=service.refresh("museum").single();data[0]=task(basis="new-revision")
        assertNull(service.deliver(old));val fresh=service.refresh("museum").single()
        actor();val current=service.settings("museum")["currentId"] as String;service.saveSettings("museum",settings(current,enabled=false));SecurityContextHolder.clearContext()
        assertNull(service.deliver(fresh));assertEquals("CANCELLED",row(fresh).state);assertTrue(service.refresh("museum").isEmpty())
    }
    @Test fun `failure rolls back queue write and retries same idempotency key with backoff`()=fixture {
        data+=task();val id=service.refresh("museum").single();val key=row(id).idempotencyKey;fail=true
        reject {service.deliver(id)};assertEquals(0L,Probe.selectAll().count());assertEquals("PENDING",row(id).state)
        service.failed(id);assertEquals("RETRY",row(id).state);assertEquals(1,row(id).attempts);assertTrue(service.dueDeliveries().isEmpty())
        assertTrue(service.refresh("museum").isEmpty());advance(minutes=5);fail=false;assertEquals(listOf(id),service.dueDeliveries());service.deliver(id)
        assertEquals(key,Probe.selectAll().single()[Probe.key]);assertEquals(2,row(id).attempts);assertEquals(listOf("FAILED","QUEUED"),CollectionSafetyReminderAttemptDao.all().map {it.result})
    }
    @Test fun `unavailable transport remains retryable and no false successful time is stored`()=fixture {
        data+=task();val id=service.refresh("museum").single()
        val noPort=CollectionSafetyReminderService(scope,recipients,listOf(source),emptyList(),clock)
        reject {noPort.deliver(id)};noPort.failed(id);assertNull(row(id).queuedAt);assertNull(CollectionSafetyReminderStateDao.all().single().lastNotifiedAt)
    }
    @Test fun `lead days and overdue transition both use actual fixed deadline`()=fixture {
        actor();val current=service.settings("museum")["currentId"] as String;service.saveSettings("museum",settings(current,lead=2));SecurityContextHolder.clearContext()
        data+=task(due=LocalDateTime.now(clock).plusDays(2));service.deliver(service.refresh("museum").single())
        advance(days=1);assertTrue(service.refresh("museum").isEmpty());advance(days=1);assertEquals(1,service.refresh("museum").size)
    }
    @Test fun `disabled reminder settings are read in one query and have no time wakeup`()=fixture {
        actor();val id=service.settings("museum")["currentId"] as String
        service.saveSettings("museum",settings(id,enabled=false));SecurityContextHolder.clearContext()
        TransactionManager.current().entityCache.flush()
        val sql=mutableListOf<String>()
        TransactionManager.current().addLogger(object:SqlLogger {
            override fun log(context:org.jetbrains.exposed.v1.core.statements.StatementContext,transaction:Transaction) {sql+=context.sql(transaction)}
        })
        assertTrue(service.enabledOrgs().isEmpty())
        assertEquals(1,sql.count {it.startsWith("SELECT",ignoreCase=true)})
        assertNull(service.nextWakeAt(emptyList()))
    }
    @Test fun `reminder wakeup uses persisted retry deadline and future task boundaries`()=fixture {
        data+=task(due=LocalDateTime.now(clock).plusHours(2))
        assertEquals(LocalDateTime.now(clock).plusHours(2),service.nextWakeAt(service.enabledOrgs()))
        advance(minutes=120)
        val id=service.refresh("museum").single();service.failed(id)
        assertEquals(row(id).nextAttemptAt,service.nextWakeAt(service.enabledOrgs()))
    }
    @Test fun `no recipient stays visible and page zero has actual total with museum and domain boundaries`()=fixture {
        data+=listOf(task().copy(assignees=emptyList()),task("outside",org="other"),task("scheduled",due=LocalDateTime.now(clock).plusDays(20)))
        assertTrue(service.refresh("museum").isEmpty())
        actor(CollectionConst.CARE);val page=service.page(GenericPage<Map<String,Any?>>().build(1,0))
        assertEquals(2L,page.total);val list=page.list as List<*>;assertTrue((list.first() as Map<*,*>)["coordinationMissing"] as Boolean)
        assertThrows(BadRequestException::class.java){service.page(GenericPage(),orgId="other")}
        assertThrows(BadRequestException::class.java){service.page(GenericPage(),domain=EnumCollectionSafetyDomain.EMERGENCY)}
        assertThrows(BadRequestException::class.java){service.settings("museum")}
        actor(CollectionConst.SAFETY_REMINDER);assertThrows(BadRequestException::class.java){service.page(GenericPage())}
    }
    @Test fun `configuration is versioned and stale clients cannot overwrite or grant roles`()=fixture {
        actor();val current=service.settings("museum")["currentId"] as String
        assertEquals(current,service.saveSettings("museum",settings()))
        assertThrows(BadRequestException::class.java){service.saveSettings("museum",settings(repeat=3))}
        assertThrows(BadRequestException::class.java){service.saveSettings("museum",settings(current,repeat=0))}
        assertThrows(BadRequestException::class.java){service.saveSettings("museum",settings(current).copy(coordinatorUserIds=setOf("outsider")))}
        val id=service.saveSettings("museum",settings(current,repeat=3));assertNotEquals(current,id);assertEquals(2,CollectionSafetyReminderSettingsDao.all().count())
    }
    @Test fun `museum disable cancels unsent reminders even when settings were enabled`()=fixture {
        data+=task();val id=service.refresh("museum").single();CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        assertNull(service.deliver(id));assertEquals("CANCELLED",row(id).state);assertTrue(service.enabledOrgs().isEmpty())
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.transaction.annotation.EnableTransactionManagement
    class Transactions
    @Test fun `Spring proxy commits refresh independently and rolls back failed message enqueue`() {
        val ds=org.sqlite.SQLiteDataSource().apply {url="jdbc:sqlite:file:spring-reminder-${System.nanoTime()}?mode=memory&cache=shared"}
        ds.connection.use {
            val tx=org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager(ds,DatabaseConfig {})
            val template=org.springframework.transaction.support.TransactionTemplate(tx)
            org.springframework.context.annotation.AnnotationConfigApplicationContext().use {runtime->
                runtime.beanFactory.registerSingleton("transactionManager",tx)
                runtime.beanFactory.registerSingleton("scope",scope)
                runtime.beanFactory.registerSingleton("recipients",recipients)
                runtime.beanFactory.registerSingleton("source",source)
                runtime.beanFactory.registerSingleton("port",port)
                runtime.beanFactory.registerSingleton("clock",clock)
                runtime.register(Transactions::class.java,CollectionSafetyReminderService::class.java)
                runtime.refresh()
                template.executeWithoutResult {
                    SchemaUtils.create(CollectionOrgs,CollectionSafetyReminderSettings,CollectionSafetyReminderStates,CollectionSafetyReminderDeliveries,CollectionSafetyReminderAttempts,Probe)
                    CollectionOrgDao.new("museum"){name="本馆";code="M"}
                }
                val real=runtime.getBean(CollectionSafetyReminderService::class.java)
                assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(real))
                actor();real.saveSettings("museum",settings());SecurityContextHolder.clearContext();data+=task()
                val id=real.refresh("museum").single();fail=true
                assertThrows(IllegalStateException::class.java){real.deliver(id)}
                template.executeWithoutResult {assertEquals(0L,Probe.selectAll().count());assertEquals("PENDING",row(id).state)}
                real.failed(id);advance(minutes=5);fail=false;real.deliver(id)
                template.executeWithoutResult {assertEquals(1L,Probe.selectAll().count());assertEquals("QUEUED",row(id).state);assertEquals(2,row(id).attempts)}
            }
        }
    }

}
