package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNull
import org.mockito.Mockito.*
import org.springframework.security.core.context.SecurityContextHolder

class CollectionSafetyReminderJobTest {
    @Test fun `one museum refresh or delivery failure does not block other museums and batches`() {
        SecurityContextHolder.clearContext()
        val service=mock(CollectionSafetyReminderService::class.java)
        `when`(service.enabledOrgs()).thenReturn(listOf("first","second"))
        `when`(service.refresh("first")).thenThrow(IllegalStateException("broken source"))
        `when`(service.dueDeliveries()).thenReturn(listOf("failed-batch","good-batch"))
        `when`(service.deliver("failed-batch")).thenThrow(IllegalStateException("unavailable"))
        CollectionSafetyReminderJob(service).run()
        verify(service).refresh("second");verify(service).failed("failed-batch");verify(service).deliver("good-batch")
        verify(service,never()).failed("good-batch");assertNull(SecurityContextHolder.getContext().authentication)
    }
}
