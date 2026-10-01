/*
 * L48 BalanceControllerTest: teste de contrato do adapter HTTP: o caso de uso é um fake roteirizável, então cada
 *     exceção do núcleo e cada ausência são provadas como o status, os cabeçalhos e o corpo que o cliente vê. O
 *     texto cru do corpo é conferido onde o formato do número importa, e o caminho codificado e com parâmetros de
 *     caminho prova que o `no-store` não tem desvio de URL.
 *
 * Spec: Resposta de sucesso segue o contrato do enunciado; accountId inválido responde 400; Conta inexistente
 *     responde 404; Dependência indisponível responde 503 com Retry-After; Falha inesperada responde 500 sem vazar
 *     detalhe interno; Todo erro é problem+json com traceId; Saldo nunca é servido de cache (inclui o erro produzido
 *     antes do controller)
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.adapter.output.dynamodb.ACCOUNT_ID
import br.com.itau.challenge.balance.adapter.output.dynamodb.SNAPSHOT
import br.com.itau.challenge.balance.domain.model.Money
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val PROBLEM_JSON: MediaType = MediaType.APPLICATION_PROBLEM_JSON
private const val UNKNOWN_ACCOUNT_ID = "00000000-0000-4000-8000-000000000001"
private const val EXPECTED_RETRY_AFTER = "5"
private const val TRACE_ID_LENGTH = 32

@SpringBootTest
@AutoConfigureMockMvc
@Import(ScriptedGetBalanceUseCaseConfiguration::class)
class BalanceControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val useCase: ScriptedGetBalanceUseCase,
) {

    @BeforeEach
    fun resetUseCase() {
        useCase.reset()
    }

    @Test
    fun `should answer 200 with the five contract fields when the account has a snapshot`() {
        useCase.outcome = { SNAPSHOT }

        mockMvc.get("/balances/$ACCOUNT_ID").andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.id") { value(ACCOUNT_ID) }
            jsonPath("$.owner") { value("315e3cfe-f4af-4cd2-b298-a449e614349a") }
            jsonPath("$.balance.amount") { value(183.12) }
            jsonPath("$.balance.currency") { value("BRL") }
            jsonPath("$.updated_at") { value("2025-07-04T12:02:44.589-03:00") }
            jsonPath("$.length()") { value(4) }
            jsonPath("$.balance.length()") { value(2) }
        }
    }

    @Test
    fun `should write the amount as a json number with the currency scale`() {
        useCase.outcome = { SNAPSHOT.copy(balance = Money.of(BigDecimal("183.1"), "BRL")) }

        val body = mockMvc.get("/balances/$ACCOUNT_ID").andReturn().response.contentAsString

        assertTrue(body.contains("\"amount\":183.10"), body)
    }

    @Test
    fun `should write a negative balance as is`() {
        useCase.outcome = { SNAPSHOT.copy(balance = Money.of(BigDecimal("-25.00"), "BRL")) }

        val body = mockMvc.get("/balances/$ACCOUNT_ID").andReturn().response.contentAsString

        assertTrue(body.contains("\"amount\":-25.00"), body)
    }

    @Test
    fun `should answer the same account when the identifier is in uppercase`() {
        useCase.outcome = { SNAPSHOT }

        mockMvc.get("/balances/${ACCOUNT_ID.uppercase()}").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(ACCOUNT_ID) }
        }
    }

    @Test
    fun `should answer 400 without calling the use case when the account id is not a uuid`() {
        mockMvc.get("/balances/abc").andExpect {
            status { isBadRequest() }
            content { contentType(PROBLEM_JSON) }
        }

        assertTrue(useCase.requestedAccounts.isEmpty())
    }

    @Test
    fun `should answer 400 without calling the use case when the account id is a non canonical uuid`() {
        mockMvc.get("/balances/1-1-1-1-1").andExpect { status { isBadRequest() } }

        assertTrue(useCase.requestedAccounts.isEmpty())
    }

    @Test
    fun `should answer 400 when the account id has no hyphens`() {
        mockMvc.get("/balances/${ACCOUNT_ID.replace("-", "")}").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `should answer 404 with a trace id when the account has no snapshot`() {
        useCase.outcome = { null }

        mockMvc.get("/balances/$UNKNOWN_ACCOUNT_ID").andExpect {
            status { isNotFound() }
            content { contentType(PROBLEM_JSON) }
            jsonPath("$.traceId") { isNotEmpty() }
        }
    }

    @Test
    fun `should answer 503 with retry after when the storage fails transiently`() {
        useCase.outcome = { throw TransientStorageException("throttled", RuntimeException()) }

        mockMvc.get("/balances/$ACCOUNT_ID").andExpect {
            status { isServiceUnavailable() }
            content { contentType(PROBLEM_JSON) }
            header { string(HttpHeaders.RETRY_AFTER, EXPECTED_RETRY_AFTER) }
        }
    }

    @Test
    fun `should answer 503 with retry after when the circuit is open`() {
        useCase.outcome = { throw StorageUnavailableException("circuit open", RuntimeException()) }

        mockMvc.get("/balances/$ACCOUNT_ID").andExpect {
            status { isServiceUnavailable() }
            header { string(HttpHeaders.RETRY_AFTER, EXPECTED_RETRY_AFTER) }
        }
    }

    @Test
    fun `should answer 500 without the original message when the storage fails permanently`() {
        useCase.outcome = { throw PermanentStorageException("table AccountBalances is gone", RuntimeException()) }

        mockMvc.get("/balances/$ACCOUNT_ID").andExpect {
            status { isInternalServerError() }
            content { contentType(PROBLEM_JSON) }
            content { string(not(containsString("AccountBalances"))) }
        }
    }

    @Test
    fun `should answer 500 with a trace id when an unexpected exception is thrown`() {
        useCase.outcome = { throw IllegalStateException("boom") }

        mockMvc.get("/balances/$ACCOUNT_ID").andExpect {
            status { isInternalServerError() }
            content { contentType(PROBLEM_JSON) }
            jsonPath("$.traceId") { isNotEmpty() }
            content { string(not(containsString("boom"))) }
        }
    }

    @Test
    fun `should describe the problem with status title detail instance and trace id when an error is produced`() {
        mockMvc.get("/balances/abc").andExpect {
            jsonPath("$.status") { value(400) }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.detail") { isNotEmpty() }
            jsonPath("$.instance") { value("/balances/abc") }
            jsonPath("$.traceId") { isNotEmpty() }
        }
    }

    @Test
    fun `should reuse the trace id of the traceparent header in the error body`() {
        mockMvc.get("/balances/abc") {
            header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
        }.andExpect {
            jsonPath("$.traceId") { value("4bf92f3577b34da6a3ce929d0e0e4736") }
        }
    }

    @Test
    fun `should generate a 32 digit hexadecimal trace id when there is no traceparent`() {
        val body = mockMvc.get("/balances/abc").andReturn().response.contentAsString

        assertTrue(Regex("\"traceId\":\"[0-9a-f]{32}\"").containsMatchIn(body), body)
    }

    @Test
    fun `should generate a different trace id for each request when there is no traceparent`() {
        val traceIdOf = { mockMvc.get("/balances/abc").andReturn().response.contentAsString.substringAfter("\"traceId\":\"").take(TRACE_ID_LENGTH) }

        assertNotEquals(traceIdOf(), traceIdOf())
    }

    @Test
    fun `should answer 405 with allow and trace id when the method is not supported`() {
        mockMvc.post("/balances/$ACCOUNT_ID").andExpect {
            status { isMethodNotAllowed() }
            content { contentType(PROBLEM_JSON) }
            header { exists(HttpHeaders.ALLOW) }
            jsonPath("$.traceId") { isNotEmpty() }
        }
    }

    @Test
    fun `should answer 404 as a problem when the account id is missing from the path`() {
        mockMvc.get("/balances").andExpect {
            status { isNotFound() }
            content { contentType(PROBLEM_JSON) }
            jsonPath("$.traceId") { isNotEmpty() }
        }
    }

    @Test
    fun `should answer the hello errors as problem json with a trace id`() {
        mockMvc.get("/hello").andExpect {
            status { isBadRequest() }
            content { contentType(PROBLEM_JSON) }
            jsonPath("$.traceId") { isNotEmpty() }
        }
    }

    @Test
    fun `should forbid storing the response when the account has a snapshot`() {
        useCase.outcome = { SNAPSHOT }

        mockMvc.get("/balances/$ACCOUNT_ID").andExpect { header { string(HttpHeaders.CACHE_CONTROL, "no-store") } }
    }

    @Test
    fun `should forbid storing the response when the account has no snapshot`() {
        useCase.outcome = { null }

        mockMvc.get("/balances/$UNKNOWN_ACCOUNT_ID").andExpect { header { string(HttpHeaders.CACHE_CONTROL, "no-store") } }
    }

    @Test
    fun `should forbid storing the response when the storage is unavailable`() {
        useCase.outcome = { throw TransientStorageException("down", RuntimeException()) }

        mockMvc.get("/balances/$ACCOUNT_ID").andExpect { header { string(HttpHeaders.CACHE_CONTROL, "no-store") } }
    }

    @Test
    fun `should forbid storing the response when the route does not exist under balances`() {
        mockMvc.get("/balances").andExpect { header { string(HttpHeaders.CACHE_CONTROL, "no-store") } }
    }

    @Test
    fun `should forbid storing the response when the path is percent encoded`() {
        useCase.outcome = { SNAPSHOT }

        mockMvc.get(URI.create("/%62alances/$ACCOUNT_ID")).andExpect { header { string(HttpHeaders.CACHE_CONTROL, "no-store") } }
    }

    @Test
    fun `should forbid storing the response when the path has matrix parameters`() {
        useCase.outcome = { SNAPSHOT }

        mockMvc.get("/balances;x=1/$ACCOUNT_ID").andExpect { header { string(HttpHeaders.CACHE_CONTROL, "no-store") } }
    }

    @Test
    fun `should forbid storing the response when the method is not supported`() {
        mockMvc.post("/balances/$ACCOUNT_ID").andExpect { header { string(HttpHeaders.CACHE_CONTROL, "no-store") } }
    }

    @Test
    fun `should reflect the latest snapshot and ask the use case every time when the snapshot changes between queries`() {
        useCase.outcome = { SNAPSHOT }
        mockMvc.get("/balances/$ACCOUNT_ID").andExpect { jsonPath("$.balance.amount") { value(183.12) } }
        useCase.outcome = { SNAPSHOT.copy(balance = Money.of(BigDecimal("10.00"), "BRL")) }

        mockMvc.get("/balances/$ACCOUNT_ID").andExpect { jsonPath("$.balance.amount") { value(10.0) } }

        assertEquals(2, useCase.requestedAccounts.size)
    }

    @Test
    fun `should not nest the extra members under a properties field in the error body`() {
        mockMvc.get("/balances/abc").andExpect {
            jsonPath("$.properties") { doesNotExist() }
        }
    }
}
