/*
 * L24 NoStoreFilter: o requisito é o saldo mais atual, então nenhuma resposta de `/balances` pode ser guardada por
 *     intermediário. É um filtro, e não o `WebContentInterceptor`, porque o interceptor só roda quando há handler, e
 *     o `405` e a rota inexistente nunca chegam a um (add-balance-query-api design D8). O caminho é lido
 *     decodificado e sem parâmetros de caminho, como o Spring MVC o casa, para `/%62alances` e `/balances;x=1` não
 *     escaparem. Enunciado: O que construir → Exposição (API REST)
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.adapter.input.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.UrlPathHelper

private const val BALANCES_PATH = "/balances"

@Component
class NoStoreFilter : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        pathOf(request).let { it != BALANCES_PATH && !it.startsWith("$BALANCES_PATH/") }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().headerValue)
        filterChain.doFilter(request, response)
    }

    private fun pathOf(request: HttpServletRequest): String = UrlPathHelper.defaultInstance.getPathWithinApplication(request)
}
