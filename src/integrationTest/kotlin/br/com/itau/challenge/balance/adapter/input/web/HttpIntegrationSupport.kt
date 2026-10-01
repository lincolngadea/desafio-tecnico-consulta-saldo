/*
 * L15-L19 HttpClient.get: cliente HTTP do JDK, para os testes de integração chamarem o servidor real sem acrescentar
 *     dependência de teste.
 *
 * Spec: n/a (add-balance-query-api design D10)
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.adapter.input.web

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

internal fun HttpClient.get(
    port: Int,
    path: String,
): HttpResponse<String> =
    send(HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(), HttpResponse.BodyHandlers.ofString())
