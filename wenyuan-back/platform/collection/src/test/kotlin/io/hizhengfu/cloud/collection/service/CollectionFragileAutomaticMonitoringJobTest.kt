package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class CollectionFragileAutomaticMonitoringJobTest {
    @Test fun `one failing actual use is retried independently while other records continue`() {
        val service=mock(CollectionFragileAutomaticMonitoringService::class.java)
        `when`(service.due()).thenReturn(listOf("broken","valid"))
        `when`(service.scan("broken")).thenThrow(IllegalStateException("source unavailable"))
        CollectionFragileAutomaticMonitoringJob(service).run()
        verify(service).failed("broken");verify(service).scan("valid");verify(service,never()).failed("valid")
    }
}
