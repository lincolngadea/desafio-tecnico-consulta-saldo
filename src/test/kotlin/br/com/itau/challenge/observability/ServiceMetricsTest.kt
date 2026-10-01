/*
 * L43 ServiceMetricsTest: sobe o contexto real com métricas ligadas e conta as amostras por status e por rota; só os
 *     timers HTTP são limpos entre os testes, porque limpar o registry apagaria os contadores e os estados
 *     pré-registrados.
 *
 * Spec: Métricas HTTP por status; Baixa cardinalidade; Endpoint de métricas na porta de gerenciamento
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.observability

import br.com.itau.challenge.balance.adapter.input.web.ScriptedGetBalanceUseCase
import br.com.itau.challenge.balance.adapter.input.web.ScriptedGetBalanceUseCaseConfiguration
import br.com.itau.challenge.balance.adapter.output.dynamodb.SNAPSHOT
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val ACCOUNT_ID = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975"
private const val HTTP_REQUESTS = "http.server.requests"
private const val BALANCE_ROUTE = "/balances/{accountId}"
private const val UNKNOWN_PATHS = 10
private const val DISTINCT_ACCOUNTS = 3

@SpringBootTest(properties = ["management.server.port="])
@AutoConfigureMockMvc
@AutoConfigureMetrics
@AutoConfigureTracing
@Import(ScriptedGetBalanceUseCaseConfiguration::class)
class ServiceMetricsTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val useCase: ScriptedGetBalanceUseCase,
    @Autowired private val meterRegistry: MeterRegistry,
) {

    @BeforeEach
    fun resetUseCase() {
        useCase.reset()
        meterRegistry.find(HTTP_REQUESTS).meters().forEach { meterRegistry.remove(it) }
    }

    private fun requestCount(status: String): Long =
        meterRegistry
            .find(HTTP_REQUESTS)
            .tags("status", status, "uri", BALANCE_ROUTE, "method", "GET")
            .timer()
            ?.count() ?: 0L

    private fun routesSeen(): Set<String> = meterRegistry.find(HTTP_REQUESTS).timers().mapNotNull { it.id.getTag("uri") }.toSet()

    @Test
    fun `should record a sample with status 200 and the route pattern when a query succeeds`() {
        useCase.outcome = { SNAPSHOT }

        mockMvc.get("/balances/$ACCOUNT_ID")

        assertEquals(1L, requestCount("200"))
    }

    @Test
    fun `should record separate samples per status when queries end in 400 404 503 and 500`() {
        mockMvc.get("/balances/abc")
        useCase.outcome = { null }
        mockMvc.get("/balances/$ACCOUNT_ID")
        useCase.outcome = { throw TransientStorageException("down", RuntimeException()) }
        mockMvc.get("/balances/$ACCOUNT_ID")
        useCase.outcome = { throw PermanentStorageException("broken", RuntimeException()) }
        mockMvc.get("/balances/$ACCOUNT_ID")

        assertEquals(listOf(1L, 1L, 1L, 1L), listOf("400", "404", "503", "500").map(::requestCount))
    }

    @Test
    fun `should use a single route label when ten different unknown paths are called`() {
        repeat(UNKNOWN_PATHS) { index -> mockMvc.get("/does-not-exist-$index") }

        assertEquals(1, routesSeen().size, routesSeen().toString())
    }

    @Test
    fun `should not grow the route series when different accounts are queried`() {
        useCase.outcome = { SNAPSHOT }

        repeat(DISTINCT_ACCOUNTS) { mockMvc.get("/balances/${UUID.randomUUID()}") }

        assertEquals(setOf(BALANCE_ROUTE), routesSeen())
    }

    @Test
    fun `should serve the prometheus text with the service metrics when the endpoint is called`() {
        useCase.outcome = { SNAPSHOT }
        mockMvc.get("/balances/$ACCOUNT_ID")

        val body = mockMvc.get("/actuator/prometheus").andReturn().response.contentAsString

        assertTrue(body.contains("http_server_requests_seconds_count"), "http metric missing")
        assertTrue(body.contains("balance_transactions_processed_total"), "result counter missing")
        assertTrue(body.contains("resilience4j_circuitbreaker_state"), "circuit state missing")
        assertNotNull(Regex("""result="duplicate"""").find(body), "duplicate series missing")
    }
}
