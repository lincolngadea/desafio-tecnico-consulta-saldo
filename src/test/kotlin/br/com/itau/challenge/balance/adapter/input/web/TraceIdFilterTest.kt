/*
 * L25 TraceIdFilterTest: usa o filtro de produção sem Spring; o `traceId` visto no MDC dentro da cadeia é comparado
 *     ao do atributo, e o MDC é conferido depois, inclusive com a cadeia falhando.
 *
 * Spec: Todo erro é problem+json com traceId
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.balance.adapter.input.web

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val INCOMING_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
private const val VALID_TRACEPARENT = "00-$INCOMING_TRACE_ID-00f067aa0ba902b7-01"
private const val TRACE_ID_LENGTH = 32
private const val PARENT_ID_LENGTH = 16

class TraceIdFilterTest {

    private val filter = TraceIdFilter()

    private fun traceIdSeenByChainFor(traceparent: String?): Pair<String?, String?> {
        val request = MockHttpServletRequest()
        traceparent?.let { request.addHeader("traceparent", it) }
        var fromMdc: String? = null
        val chain = FilterChain { _, _ -> fromMdc = MDC.get(TRACE_ID_MDC_KEY) }

        filter.doFilter(request, MockHttpServletResponse(), chain)

        return fromMdc to request.getAttribute(TRACE_ID_ATTRIBUTE) as String?
    }

    @Test
    fun `should generate a 32 digit hexadecimal trace id when the request has no traceparent`() {
        val (fromMdc, fromAttribute) = traceIdSeenByChainFor(null)

        assertEquals(fromMdc, fromAttribute)
        assertTrue(Regex("[0-9a-f]{$TRACE_ID_LENGTH}").matches(fromAttribute.orEmpty()))
    }

    @Test
    fun `should generate a different trace id for each request`() {
        val first = traceIdSeenByChainFor(null).second
        val second = traceIdSeenByChainFor(null).second

        assertNotEquals(first, second)
    }

    @Test
    fun `should reuse the trace id of a valid traceparent`() {
        val (fromMdc, fromAttribute) = traceIdSeenByChainFor(VALID_TRACEPARENT)

        assertEquals(INCOMING_TRACE_ID, fromAttribute)
        assertEquals(INCOMING_TRACE_ID, fromMdc)
    }

    @Test
    fun `should generate a trace id when the traceparent is malformed`() {
        val (_, fromAttribute) = traceIdSeenByChainFor("not-a-traceparent")

        assertTrue(Regex("[0-9a-f]{$TRACE_ID_LENGTH}").matches(fromAttribute.orEmpty()))
        assertNotEquals(INCOMING_TRACE_ID, fromAttribute)
    }

    @Test
    fun `should generate a trace id when the traceparent has an all zero trace id`() {
        val (_, fromAttribute) = traceIdSeenByChainFor("00-${"0".repeat(TRACE_ID_LENGTH)}-00f067aa0ba902b7-01")

        assertNotEquals("0".repeat(TRACE_ID_LENGTH), fromAttribute)
    }

    @Test
    fun `should generate a trace id when the traceparent has an all zero parent id`() {
        val (_, fromAttribute) = traceIdSeenByChainFor("00-$INCOMING_TRACE_ID-${"0".repeat(PARENT_ID_LENGTH)}-01")

        assertNotEquals(INCOMING_TRACE_ID, fromAttribute)
    }

    @Test
    fun `should clear the trace id from the mdc when the request ends`() {
        traceIdSeenByChainFor(VALID_TRACEPARENT)

        assertNull(MDC.get(TRACE_ID_MDC_KEY))
    }

    @Test
    fun `should clear the trace id from the mdc when the chain fails`() {
        val chain = FilterChain { _, _ -> error("boom") }

        runCatching { filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain) }

        assertNull(MDC.get(TRACE_ID_MDC_KEY))
    }
}
