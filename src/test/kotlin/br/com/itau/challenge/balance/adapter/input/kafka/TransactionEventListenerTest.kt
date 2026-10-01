/*
 * L40 TransactionEventListenerTest: o `Acknowledgment` falso registra a ordem dos passos para provar que o offset só
 *     é confirmado depois da gravação. Os testes de `DECLINED` e de valor diferente do saldo usam o caso de uso
 *     real, porque só se provam a partir do JSON.
 * L159-L166 capturedLogsOf: anexa um appender ao logger do listener para provar que duplicado e antigo não geram
 *     linha de nível INFO ou acima, o que um teste de saída do console não distingue de outros logs.
 *
 * Spec: Offset confirmado manualmente só depois da persistência; Transação processada vira snapshot de saldo; O
 *     saldo do evento é mantido como recebido; Eventos contados por resultado; Resultado esperado não polui o log
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.application.ProcessTransactionService
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.input.ProcessTransactionUseCase
import br.com.itau.challenge.balance.port.output.BalanceRepository
import br.com.itau.challenge.balance.port.output.TransientStorageException
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
import org.springframework.kafka.support.Acknowledgment
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val SAVED = "saved"
private const val ACKNOWLEDGED = "acknowledged"

class TransactionEventListenerTest {

    private val steps = mutableListOf<String>()
    private val acknowledgment = Acknowledgment { steps.add(ACKNOWLEDGED) }
    private val mapper = TransactionEventMapper(JsonMapper.builder().build())
    private val registry = SimpleMeterRegistry()
    private val metrics = TransactionOutcomeMetrics(registry)

    private fun listenerSaving(outcome: () -> SnapshotSaveResult) =
        TransactionEventListener(
            ProcessTransactionUseCase {
                steps.add(SAVED)
                outcome()
            },
            mapper,
            metrics,
        )

    @Test
    fun `should acknowledge the offset only after the snapshot is saved when the result is applied`() {
        listenerSaving { SnapshotSaveResult.Applied }.consume(transactionEventJson(), acknowledgment)

        assertEquals(listOf(SAVED, ACKNOWLEDGED), steps)
    }

    @Test
    fun `should acknowledge the offset without error when the snapshot is stale`() {
        listenerSaving { SnapshotSaveResult.StaleIgnored }.consume(transactionEventJson(), acknowledgment)

        assertEquals(listOf(SAVED, ACKNOWLEDGED), steps)
    }

    @Test
    fun `should acknowledge the offset without error when the snapshot is a duplicate`() {
        listenerSaving { SnapshotSaveResult.DuplicateIgnored }.consume(transactionEventJson(), acknowledgment)

        assertEquals(listOf(SAVED, ACKNOWLEDGED), steps)
    }

    @ParameterizedTest
    @MethodSource("resultsAndTheirMetric")
    fun `should count the result of the event when the snapshot is processed`(
        result: SnapshotSaveResult,
        metricResult: String,
    ) {
        listenerSaving { result }.consume(transactionEventJson(), acknowledgment)

        assertEquals(1.0, registry.counter("balance.transactions.processed", "result", metricResult).count())
    }

    @Test
    fun `should not count any result when the storage fails transiently`() {
        val listener = listenerSaving { throw TransientStorageException("throttled", RuntimeException()) }

        assertFailsWith<TransientStorageException> { listener.consume(transactionEventJson(), acknowledgment) }

        assertEquals(0.0, registry.find("balance.transactions.processed").counters().sumOf { it.count() })
    }

    @ParameterizedTest
    @MethodSource("expectedResults")
    fun `should not write an info log when the result is expected`(result: SnapshotSaveResult) {
        val logs = capturedLogsOf(TransactionEventListener::class.java)

        listenerSaving { result }.consume(transactionEventJson(), acknowledgment)

        assertTrue(logs.list.none { it.level.isGreaterOrEqual(Level.INFO) }, logs.list.toString())
    }

    @Test
    fun `should not acknowledge the offset when the storage fails transiently`() {
        val listener = listenerSaving { throw TransientStorageException("throttled", RuntimeException()) }

        assertFailsWith<TransientStorageException> { listener.consume(transactionEventJson(), acknowledgment) }

        assertEquals(listOf(SAVED), steps)
    }

    @Test
    fun `should not acknowledge the offset when the event is malformed`() {
        val listener = listenerSaving { SnapshotSaveResult.Applied }

        assertFailsWith<MalformedTransactionEventException> { listener.consume("not json", acknowledgment) }

        assertEquals(emptyList(), steps)
    }

    @Test
    fun `should save the snapshot of a declined transaction when the event is consumed`() {
        val savedSnapshots = mutableListOf<BalanceSnapshot>()
        val listener = listenerWithRealUseCase(savedSnapshots)

        listener.consume(transactionEventJson(status = "DECLINED"), acknowledgment)

        assertEquals(1, savedSnapshots.size)
    }

    @Test
    fun `should save the balance of the event when the transaction amount differs from it`() {
        val savedSnapshots = mutableListOf<BalanceSnapshot>()
        val listener = listenerWithRealUseCase(savedSnapshots)

        listener.consume(transactionEventJson(balanceJson = """{"amount": 50.00, "currency": "BRL"}"""), acknowledgment)

        assertEquals(BigDecimal("50.00"), savedSnapshots.single().balance.amount)
    }

    private fun listenerWithRealUseCase(savedSnapshots: MutableList<BalanceSnapshot>) =
        TransactionEventListener(
            ProcessTransactionService(
                BalanceRepository { snapshot ->
                    savedSnapshots.add(snapshot)
                    SnapshotSaveResult.Applied
                },
            ),
            mapper,
            metrics,
        )

    private fun capturedLogsOf(type: Class<*>): ListAppender<ILoggingEvent> =
        ListAppender<ILoggingEvent>().also { appender ->
            appender.start()
            (LoggerFactory.getLogger(type) as Logger).apply {
                level = Level.ALL
                addAppender(appender)
            }
        }

    companion object {
        @JvmStatic
        fun resultsAndTheirMetric() =
            listOf(
                Arguments.of(SnapshotSaveResult.Applied, "applied"),
                Arguments.of(SnapshotSaveResult.StaleIgnored, "stale_ignored"),
                Arguments.of(SnapshotSaveResult.DuplicateIgnored, "duplicate"),
            )

        @JvmStatic
        fun expectedResults() = listOf(SnapshotSaveResult.StaleIgnored, SnapshotSaveResult.DuplicateIgnored)
    }
}
