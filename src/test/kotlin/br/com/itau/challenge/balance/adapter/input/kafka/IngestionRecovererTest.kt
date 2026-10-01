/*
 * L27 IngestionRecovererTest: cobre a decisão entre DLT e pausa para cada tipo de falha, incluindo a falha não
 *     classificada, que vai para a DLT.
 *
 * Spec: Erro permanente vai para a DLT com o motivo; Dependência indisponível pausa as partições em vez de
 *     descartar; Eventos contados por resultado
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import org.apache.kafka.clients.consumer.ConsumerRecord
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.kafka.listener.ListenerExecutionFailedException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val RECORD = ConsumerRecord("topic", 0, 0L, "key", "value")
private val PAUSE_DURATION: Duration = Duration.ofSeconds(30)

class IngestionRecovererTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = TransactionOutcomeMetrics(registry)
    private val deadLettered = mutableListOf<Exception>()
    private var pauses = 0
    private val recoverer =
        IngestionRecoverer(
            { _, failure -> deadLettered.add(failure) },
            {
                pauses++
                PAUSE_DURATION
            },
            metrics,
        )

    private fun dlqCount() = registry.counter("balance.transactions.processed", "result", "dlq").count()

    private fun listenerFailure(cause: Throwable) = ListenerExecutionFailedException("listener failed", cause)

    @ParameterizedTest
    @MethodSource("permanentFailures")
    fun `should dead letter the record without pausing when the failure is permanent`(cause: Throwable) {
        val failure = listenerFailure(cause)

        recoverer.accept(RECORD, failure)

        assertEquals(listOf<Exception>(failure), deadLettered)
        assertEquals(0, pauses)
    }

    @ParameterizedTest
    @MethodSource("permanentFailures")
    fun `should count dlq once when the record is dead lettered`(cause: Throwable) {
        recoverer.accept(RECORD, listenerFailure(cause))

        assertEquals(1.0, dlqCount())
    }

    @Test
    fun `should not count dlq when the dead letter publication fails`() {
        val failingRecoverer = IngestionRecoverer({ _, _ -> error("dlt down") }, { PAUSE_DURATION }, metrics)

        assertFailsWith<IllegalStateException> { failingRecoverer.accept(RECORD, listenerFailure(MalformedTransactionEventException("bad", RuntimeException()))) }

        assertEquals(0.0, dlqCount())
    }

    @ParameterizedTest
    @MethodSource("dependencyFailures")
    fun `should not count any result when the listener is paused`(cause: Throwable) {
        assertFailsWith<ListenerPausedException> { recoverer.accept(RECORD, listenerFailure(cause)) }

        assertEquals(0.0, registry.find("balance.transactions.processed").counters().sumOf { it.count() })
    }

    @ParameterizedTest
    @MethodSource("dependencyFailures")
    fun `should pause the listener and keep the record when the dependency is failing`(cause: Throwable) {
        assertFailsWith<ListenerPausedException> { recoverer.accept(RECORD, listenerFailure(cause)) }

        assertEquals(1, pauses)
        assertEquals(emptyList(), deadLettered)
    }

    @Test
    fun `should keep the failure as the cause when the listener is paused`() {
        val failure = listenerFailure(TransientStorageException("throttled", RuntimeException()))

        val paused = assertFailsWith<ListenerPausedException> { recoverer.accept(RECORD, failure) }

        assertEquals(failure, paused.cause)
    }

    companion object {
        @JvmStatic
        fun permanentFailures(): List<Throwable> =
            listOf(
                MalformedTransactionEventException("bad json", RuntimeException()),
                PermanentStorageException("access denied", RuntimeException()),
                IllegalStateException("unclassified failure"),
            )

        @JvmStatic
        fun dependencyFailures(): List<Throwable> =
            listOf(
                TransientStorageException("throttled", RuntimeException()),
                StorageUnavailableException("circuit open", RuntimeException()),
            )
    }
}
