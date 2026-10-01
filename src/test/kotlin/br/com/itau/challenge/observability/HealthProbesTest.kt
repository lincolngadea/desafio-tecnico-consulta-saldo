/*
 * L32 HealthProbesTest: o contexto sobe sem DynamoDB nem Kafka, então o `liveness` e o `readiness` UP já provam que
 *     não dependem deles; o grupo `readiness` é conferido pelo bean real, e não por uma propriedade que poderia nem
 *     existir.
 *
 * Spec: Liveness separado do readiness; Readiness não depende do DynamoDB; Detalhes de saúde não são expostos
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.observability

import br.com.itau.challenge.balance.adapter.input.web.ScriptedGetBalanceUseCase
import br.com.itau.challenge.balance.adapter.input.web.ScriptedGetBalanceUseCaseConfiguration
import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val ACCOUNT_ID = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975"

@SpringBootTest(properties = ["management.server.port="])
@AutoConfigureMockMvc
@Import(ScriptedGetBalanceUseCaseConfiguration::class)
class HealthProbesTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val useCase: ScriptedGetBalanceUseCase,
    @Autowired private val healthGroups: HealthEndpointGroups,
) {

    @BeforeEach
    fun resetUseCase() {
        useCase.reset()
    }

    @Test
    fun `should answer 200 with status UP on the liveness probe when the application is alive`() {
        mockMvc.get("/actuator/health/liveness").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
        }
    }

    @Test
    fun `should answer 200 with status UP on the readiness probe when the application is ready`() {
        mockMvc.get("/actuator/health/readiness").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
        }
    }

    @Test
    fun `should keep the liveness UP when neither DynamoDB nor Kafka is running in this context`() {
        mockMvc.get("/actuator/health/liveness").andExpect { status { isOk() } }
    }

    @Test
    fun `should keep the readiness UP and answer the query with 503 when DynamoDB is down`() {
        useCase.outcome = { throw StorageUnavailableException("circuit open", RuntimeException()) }

        mockMvc.get("/actuator/health/readiness").andExpect { status { isOk() } }
        mockMvc.get("/balances/$ACCOUNT_ID").andExpect {
            status { isServiceUnavailable() }
            header { exists("Retry-After") }
        }
    }

    @Test
    fun `should include only the availability state in the readiness group`() {
        val readiness = healthGroups.get("readiness")

        assertNotNull(readiness)
        assertTrue(readiness.isMember("readinessState"))
        assertFalse(readiness.isMember("dynamoDb"))
        assertFalse(readiness.isMember("kafka"))
        assertFalse(readiness.isMember("diskSpace"))
    }

    @Test
    fun `should include only the liveness state in the liveness group`() {
        val liveness = healthGroups.get("liveness")

        assertNotNull(liveness)
        assertTrue(liveness.isMember("livenessState"))
        assertFalse(liveness.isMember("dynamoDb"))
        assertFalse(liveness.isMember("kafka"))
    }

    @Test
    fun `should show only the status without components or details on every health endpoint`() {
        listOf("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").forEach { path ->
            val body = mockMvc.get(path).andReturn().response.contentAsString

            assertFalse(body.contains("components"), "$path: $body")
            assertFalse(body.contains("details"), "$path: $body")
        }
    }
}
