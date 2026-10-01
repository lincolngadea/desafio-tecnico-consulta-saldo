/*
 * L29 ManagementPortTest: sobe o servidor de verdade, com a API e o gerenciamento em portas aleatórias distintas,
 *     porque o MockMvc não serve a porta de gerenciamento separada.
 *
 * Spec: Endpoint de métricas na porta de gerenciamento
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.observability

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.env.Environment
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val OK = 200
private const val NOT_FOUND = 404

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["management.server.port=0"])
@AutoConfigureMetrics
class ManagementPortTest {

    @LocalServerPort
    private var apiPort: Int = 0

    @Autowired
    private lateinit var environment: Environment

    private val httpClient = HttpClient.newHttpClient()

    private val managementPort: Int
        get() = environment.getRequiredProperty("local.management.port", Int::class.java)

    private fun get(
        port: Int,
        path: String,
    ): HttpResponse<String> =
        httpClient.send(HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(), HttpResponse.BodyHandlers.ofString())

    @Test
    fun `should serve the prometheus text on the management port`() {
        val response = get(managementPort, "/actuator/prometheus")

        assertEquals(OK, response.statusCode())
        assertTrue(response.body().contains("jvm_memory_used_bytes"))
    }

    @Test
    fun `should not expose the actuator on the api port`() {
        assertEquals(NOT_FOUND, get(apiPort, "/actuator/prometheus").statusCode())
        assertEquals(NOT_FOUND, get(apiPort, "/actuator/health").statusCode())
    }

    @Test
    fun `should use a port different from the api port for management`() {
        assertNotEquals(apiPort, managementPort)
    }

    @Test
    fun `should expose only health and prometheus on the management port`() {
        assertEquals(NOT_FOUND, get(managementPort, "/actuator/env").statusCode())
        assertEquals(NOT_FOUND, get(managementPort, "/actuator/beans").statusCode())
        assertEquals(NOT_FOUND, get(managementPort, "/actuator/metrics").statusCode())
    }
}
