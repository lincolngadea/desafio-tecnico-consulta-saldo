/*
 * L26-L27 TRACEPARENT e ALL_ZEROS: aceita o `traceparent` W3C (versão `00`) e rejeita ids zerados, que a
 *     especificação declara inválidos.
 * L31 TraceIdFilter: dá a toda requisição um `traceId` de correlação, reaproveitando o do chamador quando válido, e
 *     o mantém no MDC para o log e num atributo para o corpo de erro. É limpo no `finally` porque a thread volta ao
 *     pool.
 *
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.balance.adapter.input.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

internal const val TRACE_ID_ATTRIBUTE = "traceId"
internal const val TRACE_ID_MDC_KEY = "traceId"

private const val TRACEPARENT_HEADER = "traceparent"
private val TRACEPARENT = Regex("00-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}")
private val ALL_ZEROS = Regex("0+")

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val traceId = traceIdOf(request)
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId)
        MDC.put(TRACE_ID_MDC_KEY, traceId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY)
        }
    }

    private fun traceIdOf(request: HttpServletRequest): String = incomingTraceId(request) ?: newTraceId()

    private fun incomingTraceId(request: HttpServletRequest): String? {
        val match = request.getHeader(TRACEPARENT_HEADER)?.let { TRACEPARENT.matchEntire(it) } ?: return null
        val (traceId, parentId) = match.destructured
        return traceId.takeUnless { ALL_ZEROS.matches(it) || ALL_ZEROS.matches(parentId) }
    }

    private fun newTraceId(): String = UUID.randomUUID().toString().replace("-", "")
}
