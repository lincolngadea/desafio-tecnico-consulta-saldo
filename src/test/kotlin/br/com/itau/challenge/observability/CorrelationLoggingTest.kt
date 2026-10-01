/*
 * L50 CorrelationLoggingTest: usa o log real em JSON, lido linha a linha, com o tracing ligado, para provar a
 *     correlação ponta a ponta: o `traceId` do log é o do corpo de erro, vem do `traceparent` e não vaza para a
 *     requisição seguinte.
 * L102-L114 should not write a trace id when the log is outside any request: emite o log da própria thread do teste,
 *     depois de uma requisição, porque a linha de startup depende da ordem de execução e do cache de contexto.
 *
 * Spec: Logs em JSON com traceId; traceId propagado do HTTP; Dados pessoais e payload nunca vão para o log
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.observability

import br.com.itau.challenge.balance.adapter.input.web.ScriptedGetBalanceUseCase
import br.com.itau.challenge.balance.adapter.input.web.ScriptedGetBalanceUseCaseConfiguration
import br.com.itau.challenge.balance.adapter.output.dynamodb.OWNER_ID
import br.com.itau.challenge.balance.adapter.output.dynamodb.SNAPSHOT
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ACCOUNT_ID = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975"
private const val INCOMING_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
private const val CAUSE_MESSAGE = "storage exploded"
private const val TRACE_ID_FIELD = "traceId"
private const val OUTSIDE_MESSAGE = "outside any request"

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
@Import(ScriptedGetBalanceUseCaseConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
class CorrelationLoggingTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val useCase: ScriptedGetBalanceUseCase,
) {

    private val jsonMapper = JsonMapper.builder().build()

    @BeforeEach
    fun failTheUseCase() {
        useCase.reset()
        useCase.outcome = { throw IllegalStateException(CAUSE_MESSAGE) }
    }

    private fun failingQuery(traceparent: String? = null): String =
        mockMvc
            .get("/balances/$ACCOUNT_ID") { traceparent?.let { header("traceparent", it) } }
            .andReturn()
            .response
            .contentAsString

    private fun errorLogOf(output: CapturedOutput): JsonNode =
        output.all
            .lines()
            .filter { it.startsWith("{") }
            .map { jsonMapper.readTree(it) }
            .last { it["message"].asString().contains(CAUSE_MESSAGE) }

    private fun traceIdOfBody(body: String): String = jsonMapper.readTree(body)[TRACE_ID_FIELD].asString()

    @Test
    fun `should write a json line with timestamp level logger message and trace id when a request fails`(output: CapturedOutput) {
        failingQuery()

        val line = errorLogOf(output)

        assertNotNull(line["@timestamp"])
        assertEquals("ERROR", line["level"].asString())
        assertTrue(line["logger_name"].asString().isNotBlank())
        assertTrue(line[TRACE_ID_FIELD].asString().isNotBlank())
    }

    @Test
    fun `should keep the stack trace inside the json line when an exception is logged`(output: CapturedOutput) {
        failingQuery()

        val line = errorLogOf(output)

        assertTrue(line["stack_trace"].asString().contains("IllegalStateException"))
        assertTrue(output.all.lines().none { it.trimStart().startsWith("at ") })
    }

    @Test
    fun `should not write a trace id when the log is outside any request`(output: CapturedOutput) {
        failingQuery("00-$INCOMING_TRACE_ID-00f067aa0ba902b7-01")

        LoggerFactory.getLogger("outside-request").info(OUTSIDE_MESSAGE)

        val outsideLine =
            output.all
                .lines()
                .filter { it.startsWith("{") }
                .map { jsonMapper.readTree(it) }
                .last { it["message"].asString() == OUTSIDE_MESSAGE }
        assertNull(outsideLine[TRACE_ID_FIELD])
    }

    @Test
    fun `should reuse the trace id of a valid traceparent in the log`(output: CapturedOutput) {
        failingQuery("00-$INCOMING_TRACE_ID-00f067aa0ba902b7-01")

        assertEquals(INCOMING_TRACE_ID, errorLogOf(output)[TRACE_ID_FIELD].asString())
    }

    @Test
    fun `should write a new 32 digit trace id when the request has no traceparent`(output: CapturedOutput) {
        failingQuery()

        assertTrue(Regex("[0-9a-f]{32}").matches(errorLogOf(output)[TRACE_ID_FIELD].asString()))
    }

    @Test
    fun `should write a different trace id for each request when there is no traceparent`() {
        val first = traceIdOfBody(failingQuery())
        val second = traceIdOfBody(failingQuery())

        assertNotEquals(first, second)
    }

    @Test
    fun `should write the same trace id in the log and in the error body when a request fails`(output: CapturedOutput) {
        val body = failingQuery()

        assertEquals(traceIdOfBody(body), errorLogOf(output)[TRACE_ID_FIELD].asString())
    }

    @Test
    fun `should not carry the trace id to the next request when two requests run in sequence`(output: CapturedOutput) {
        failingQuery("00-$INCOMING_TRACE_ID-00f067aa0ba902b7-01")
        val secondBody = failingQuery()

        assertNotEquals(INCOMING_TRACE_ID, traceIdOfBody(secondBody))
        assertNotEquals(INCOMING_TRACE_ID, errorLogOf(output)[TRACE_ID_FIELD].asString())
    }

    @Test
    fun `should not log the balance the owner or the whole account id when a query ends in success not found or error`(output: CapturedOutput) {
        useCase.outcome = { SNAPSHOT }
        mockMvc.get("/balances/$ACCOUNT_ID")
        useCase.outcome = { null }
        mockMvc.get("/balances/$ACCOUNT_ID")
        useCase.outcome = { throw IllegalStateException(CAUSE_MESSAGE) }
        mockMvc.get("/balances/$ACCOUNT_ID")

        val queryLogs = output.all.lines().filter { it.startsWith("{") }.joinToString("\n")

        assertFalse(queryLogs.contains(OWNER_ID), queryLogs)
        assertFalse(queryLogs.contains(ACCOUNT_ID), queryLogs)
        assertFalse(queryLogs.contains("183.12"), queryLogs)
    }
}
