package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import java.time.LocalDate

class CollectionOpeningCalendarPersistenceTest {
    private fun fixture(body: (CollectionOpeningCalendarService, CollectionScopeResolver) -> Unit) {
        val db=Database.connect("jdbc:sqlite:file:opening-calendar-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
        StaticApplicationContext().use { context ->
            context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager()); context.refresh()
            SpringContextHolder().setApplicationContext(context)
            transaction(db) {
                SchemaUtils.create(CollectionOrgs,CollectionOpeningCalendarVersions,CollectionCalendarVersions)
                CollectionOrgDao.new("museum") { name="本馆"; code="museum"; parentId="0"; parentIds="0," }
                val scope=mock(CollectionScopeResolver::class.java)
                `when`(scope.readableOrgIds()).thenReturn(setOf("museum"))
                `when`(scope.writableOrgIds()).thenReturn(setOf("museum"))
                body(CollectionOpeningCalendarService(scope),scope)
            }
        }
    }
    @Test fun `save retains revisions reloads rules and rejects stale changes`() = fixture { service, _ ->
        val value=CollectionOpeningCalendarVo(2026,weeklyClosures=listOf(CollectionWeeklyClosureVo(LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),setOf(1))))
        assertNull(service.get("museum",2026))
        val first=service.save("museum",value); assertEquals(1,first.revision)
        assertEquals(first,service.get("museum",2026))
        val second=service.save("museum",first.copy(exceptions=listOf(CollectionOpeningExceptionVo(LocalDate.of(2026,6,1),true,"临时开放"))))
        assertEquals(2,second.revision); assertEquals(second,service.get("museum",2026))
        assertEquals(2,CollectionOpeningCalendarVersionDao.all().count())
        assertThrows(BadRequestException::class.java) { service.save("museum",first) }
        assertEquals(2,CollectionOpeningCalendarVersionDao.all().count())
    }
    @Test fun `read and write require separate visible org scopes`() = fixture { service, scope ->
        assertThrows(BadRequestException::class.java) { service.get("other",2026) }
        assertThrows(BadRequestException::class.java) { service.save("other",CollectionOpeningCalendarVo(2026)) }
        `when`(scope.writableOrgIds()).thenReturn(emptySet())
        assertThrows(BadRequestException::class.java) { service.save("museum",CollectionOpeningCalendarVo(2026)) }
        assertEquals(0,CollectionOpeningCalendarVersionDao.all().count())
    }
    @Test fun `opening saves never replace statutory calendar versions`() = fixture { service, _ ->
        val statutory=CollectionWorkingCalendar.national(2026)
        CollectionCalendarVersionDao.new { orgId="museum"; year=2026; revision=1; content=statutory }
        service.save("museum",CollectionOpeningCalendarVo(2026))
        assertEquals(statutory,CollectionCalendarVersionDao.all().single().content)
        assertEquals(1,CollectionCalendarVersionDao.all().count())
    }
}
