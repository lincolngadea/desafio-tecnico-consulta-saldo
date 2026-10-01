/*
 * L19 TransactionOutcomeMetricsTest: usa um registry em memória para provar que cada desfecho incrementa só a sua
 *     série, e que a cardinalidade fica fixa em quatro mesmo com muitos eventos.
 *
 * Spec: Eventos contados por resultado; Baixa cardinalidade
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private const val PROCESSED_COUNTER = "balance.transactions.processed"
private const val RESULT_TAG = "result"
private const val DISTINCT_EVENTS = 50

class TransactionOutcomeMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = TransactionOutcomeMetrics(registry)

    private fun count(result: String): Double = registry.counter(PROCESSED_COUNTER, RESULT_TAG, result).count()

    @Test
    fun `should count applied when an event is applied`() {
        metrics.record(SnapshotSaveResult.Applied)

        assertEquals(1.0, count("applied"))
    }

    @Test
    fun `should count stale ignored when an older event is processed`() {
        metrics.record(SnapshotSaveResult.StaleIgnored)

        assertEquals(1.0, count("stale_ignored"))
    }

    @Test
    fun `should count duplicate when the same event is processed again`() {
        metrics.record(SnapshotSaveResult.DuplicateIgnored)

        assertEquals(1.0, count("duplicate"))
    }

    @Test
    fun `should count dlq when a record is dead lettered`() {
        metrics.recordDeadLettered()

        assertEquals(1.0, count("dlq"))
    }

    @Test
    fun `should increment only the matching result when an event is recorded`() {
        metrics.record(SnapshotSaveResult.Applied)

        assertEquals(listOf(0.0, 0.0, 0.0), listOf("stale_ignored", "duplicate", "dlq").map(::count))
    }

    @Test
    fun `should keep the four result series when many events are recorded`() {
        repeat(DISTINCT_EVENTS) { metrics.record(SnapshotSaveResult.Applied) }

        val series = registry.find(PROCESSED_COUNTER).counters()

        assertEquals(setOf("applied", "stale_ignored", "duplicate", "dlq"), series.map { it.id.getTag(RESULT_TAG) }.toSet())
        assertEquals(setOf(RESULT_TAG), series.flatMap { it.id.tags }.map { it.key }.toSet())
    }
}
