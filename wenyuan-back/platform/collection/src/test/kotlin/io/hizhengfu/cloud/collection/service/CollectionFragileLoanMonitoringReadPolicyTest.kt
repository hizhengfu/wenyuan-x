package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst as C
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping

class CollectionFragileLoanMonitoringReadPolicyTest {
    private val authorization = mock(CollectionFileReadAuthorization::class.java)
    private val source = mock(CollectionFragilityOriginalService::class.java)
    private val beanFactory = StaticListableBeanFactory().apply { addBean("fragilityOriginals", source) }
    private val policy = CollectionBusinessArchiveReadPolicy(authorization, mock(CollectionDisclosureService::class.java),
        fragilityOriginals = beanFactory.getBeanProvider(CollectionFragilityOriginalService::class.java))
    private val reference = CollectionArchiveFileReference("sealed", "museum", C.BIZ_LOAN, "loan", "MONITORING_monitor", "proof", "{}")
    private val pattern = "/api/v1/collection/fragility/{collectionId}/original/loan-monitoring/{orgId}/{adjustmentId}/{monitoringId}/{fileId}"

    @AfterEach fun cleanup() = RequestContextHolder.resetRequestAttributes()

    private fun route(pattern: String = this.pattern, variables: Map<String, String> = mapOf("collectionId" to "piece", "orgId" to "museum",
        "adjustmentId" to "change", "monitoringId" to "monitor", "fileId" to "proof")): MockHttpServletRequest {
        val request = MockHttpServletRequest("GET", "/api/v1/collection/fragility/piece/original/loan-monitoring/museum/change/monitor/proof")
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern)
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, variables)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        return request
    }
    private fun denyOrdinaryLoan() {
        doThrow(BadRequestException("缺少借用案卷职责")).`when`(authorization)
            .requireBusinessReadable("museum", setOf("ADMIN", C.ADMIN, C.STORAGE, C.SELECT), C.CROSS_ORG, "藏品业务原件", emptySet())
    }

    @Test fun `dedicated source repeats frozen linkage authorization without requiring unrelated loan duty`() {
        denyOrdinaryLoan(); route()
        `when`(source.requireLoanMonitoringSource("piece", "museum", "change", "monitor", "proof")).thenReturn("sealed")
        assertDoesNotThrow { policy.requireReadable(reference) }
        verify(source).requireLoanMonitoringSource("piece", "museum", "change", "monitor", "proof")
        verifyNoInteractions(authorization)
    }
    @Test fun `generic and ordinary loan routes retain their original duty boundary`() {
        denyOrdinaryLoan()
        for (route in listOf("/api/v1/file/original", "/api/v1/collection/loan/{id}/original/{archiveId}/{fileId}")) {
            route(route)
            assertThrows(BadRequestException::class.java) { policy.requireReadable(reference) }
        }
        RequestContextHolder.resetRequestAttributes()
        assertThrows(BadRequestException::class.java) { policy.requireReadable(reference) }
        verifyNoInteractions(source)
    }
    @Test fun `source denial propagates before bytes can be authorized`() {
        route(); `when`(source.requireLoanMonitoringSource("piece", "museum", "change", "monitor", "proof"))
            .thenThrow(BadRequestException("冻结事实或当前资格已变化"))
        assertThrows(BadRequestException::class.java) { policy.requireReadable(reference) }
        verifyNoInteractions(authorization)
    }
    @Test fun `selected archive must equal the currently resolved frozen source`() {
        route(); `when`(source.requireLoanMonitoringSource("piece", "museum", "change", "monitor", "proof")).thenReturn("other-sealed")
        assertThrows(BadRequestException::class.java) { policy.requireReadable(reference) }
    }
    @Test fun `wrong museum file or monitoring phase cannot use source delegation`() {
        route()
        for (ref in listOf(reference.copy(orgId = "other"), reference.copy(fileId = "other-proof"), reference.copy(phase = "MONITORING_other"), reference.copy(phase = "OUT_EXECUTED"))) {
            assertThrows(BadRequestException::class.java) { policy.requireReadable(ref) }
        }
        verifyNoInteractions(source)
    }
    @Test fun `missing trusted mapping variables are rejected and a client URI alone grants nothing`() {
        denyOrdinaryLoan(); val request = route(variables = emptyMap())
        assertThrows(BadRequestException::class.java) { policy.requireReadable(reference) }
        request.removeAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
        assertThrows(BadRequestException::class.java) { policy.requireReadable(reference) }
        verifyNoInteractions(source)
    }
    @Test fun `HEAD uses the same frozen source authorization while a wrong HTTP method grants nothing`() {
        denyOrdinaryLoan(); val request = route()
        `when`(source.requireLoanMonitoringSource("piece", "museum", "change", "monitor", "proof")).thenReturn("sealed")
        request.method = "HEAD"
        assertDoesNotThrow { policy.requireReadable(reference) }
        verify(source).requireLoanMonitoringSource("piece", "museum", "change", "monitor", "proof")
        clearInvocations(source)
        request.method = "POST"
        assertThrows(BadRequestException::class.java) { policy.requireReadable(reference) }
        verifyNoInteractions(source)
    }
}
