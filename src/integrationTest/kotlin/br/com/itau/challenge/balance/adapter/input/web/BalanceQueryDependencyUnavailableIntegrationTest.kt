/*
 * L33-L34 SCHEDULING_TOLERANCE e FAST_FAILURE_LIMIT: a folga cobre o agendamento da JVM além do timeout da leitura,
 *     e o limite de falha rápida prova que o circuito aberto não toca o DynamoDB.
 * L37 BalanceQueryDependencyUnavailableIntegrationTest: aponta o DynamoDB para uma porta fechada, que dá recusa de
 *     conexão, para provar o `503` com `Retry-After` dentro do orçamento da leitura e, depois da janela do circuito,
 *     a falha rápida. O tópico é exclusivo e sem eventos, para a ingestão não competir com o teste.
 *
 * Spec: Dependência indisponível responde 503 com Retry-After; O pior caso da leitura cabe no orçamento de latência;
 *     Circuit breaker de leitura com a classificação de erros compartilhada
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.adapter.input.kafka.createIngestionTopics
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.junit.jupiter.api.Assertions.assertTimeout
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.ServerSocket
import java.net.http.HttpClient
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals

private const val SERVICE_UNAVAILABLE = 503
private const val CIRCUIT_WINDOW = 4
private val READ_API_CALL_TIMEOUT: Duration = Duration.ofMillis(800)
private val SCHEDULING_TOLERANCE: Duration = Duration.ofMillis(700)
private val FAST_FAILURE_LIMIT: Duration = Duration.ofMillis(300)

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["management.server.port=0"])
class BalanceQueryDependencyUnavailableIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    @Qualifier("balanceReadCircuitBreaker")
    private lateinit var readCircuitBreaker: CircuitBreaker

    private val httpClient = HttpClient.newHttpClient()

    private fun queryUnknownAccount() = httpClient.get(port, "/balances/${UUID.randomUUID()}")

    @Test
    fun `should answer 503 with retry after within the read budget when DynamoDB is down`() {
        assertTimeout(READ_API_CALL_TIMEOUT + SCHEDULING_TOLERANCE) {
            val response = queryUnknownAccount()

            assertEquals(SERVICE_UNAVAILABLE, response.statusCode())
            assertEquals("5", response.headers().firstValue("Retry-After").orElse(""))
        }
    }

    @Test
    fun `should fail fast with 503 once the read circuit is open`() {
        repeat(CIRCUIT_WINDOW) { queryUnknownAccount() }

        assertEquals(CircuitBreaker.State.OPEN, readCircuitBreaker.state)
        assertTimeout(FAST_FAILURE_LIMIT) {
            val response = queryUnknownAccount()

            assertEquals(SERVICE_UNAVAILABLE, response.statusCode())
            assertEquals("5", response.headers().firstValue("Retry-After").orElse(""))
        }
    }

    companion object {
        private val topics = createIngestionTopics(partitions = 1)
        private val closedPort = ServerSocket(0).use { it.localPort }

        @JvmStatic
        @DynamicPropertySource
        fun unavailableDependency(registry: DynamicPropertyRegistry) {
            registry.add("dynamodb.endpoint") { "http://localhost:$closedPort" }
            registry.add("balance.circuit-breaker.sliding-window-size") { CIRCUIT_WINDOW }
            registry.add("balance.circuit-breaker.wait-duration-in-open-state") { "60s" }
            registry.add("ingestion.topic-name") { topics.main }
            registry.add("ingestion.dlt-topic-name") { topics.dlt }
            registry.add("ingestion.consumer-group-id") { topics.groupId }
        }
    }
}
