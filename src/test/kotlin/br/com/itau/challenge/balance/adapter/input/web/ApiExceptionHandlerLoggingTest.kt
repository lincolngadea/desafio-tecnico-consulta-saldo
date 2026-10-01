/*
 * L30 ApiExceptionHandlerLoggingTest: captura a saída real do log para provar que a causa de um `500` aparece com o
 *     mesmo `traceId` do corpo, que é o que permite achar o erro no log a partir da resposta.
 *
 * Spec: Falha inesperada responde 500 sem vazar detalhe interno; Todo erro é problem+json com traceId
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.adapter.output.dynamodb.ACCOUNT_ID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertContains

private const val INCOMING_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
private const val CAUSE_MESSAGE = "disk exploded"

@SpringBootTest
@AutoConfigureMockMvc
@Import(ScriptedGetBalanceUseCaseConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
class ApiExceptionHandlerLoggingTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val useCase: ScriptedGetBalanceUseCase,
) {

    @Test
    fun `should log the cause with the trace id of the response when the answer is 500`(output: CapturedOutput) {
        useCase.outcome = { throw IllegalStateException(CAUSE_MESSAGE) }

        mockMvc.get("/balances/$ACCOUNT_ID") {
            header("traceparent", "00-$INCOMING_TRACE_ID-00f067aa0ba902b7-01")
        }.andExpect { jsonPath("$.traceId") { value(INCOMING_TRACE_ID) } }

        val errorLine = output.all.lines().first { it.contains(CAUSE_MESSAGE) }
        assertContains(errorLine, INCOMING_TRACE_ID)
    }
}
