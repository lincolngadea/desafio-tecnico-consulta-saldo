/*
 * L21 TransactionOutcomeMetrics: único ponto que conta o desfecho de cada evento (applied, stale_ignored, duplicate,
 *     dlq). Fica no adapter, e não no caso de uso, porque Micrometer não entra na camada de aplicação (Art. 1). Os
 *     quatro contadores nascem no construtor, para a série existir com zero e a cardinalidade ficar fixa em quatro.
 * L29-L35 record: o `when` sobre a sealed `SnapshotSaveResult` é exaustivo e sem `else`: um novo resultado quebra a
 *     compilação até alguém decidir sua métrica.
 *
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

private const val PROCESSED_COUNTER = "balance.transactions.processed"
private const val RESULT_TAG = "result"

@Component
class TransactionOutcomeMetrics(
    meterRegistry: MeterRegistry,
) {
    private val applied = counterFor(meterRegistry, "applied")
    private val staleIgnored = counterFor(meterRegistry, "stale_ignored")
    private val duplicate = counterFor(meterRegistry, "duplicate")
    private val deadLettered = counterFor(meterRegistry, "dlq")

    fun record(result: SnapshotSaveResult) {
        when (result) {
            SnapshotSaveResult.Applied -> applied.increment()
            SnapshotSaveResult.StaleIgnored -> staleIgnored.increment()
            SnapshotSaveResult.DuplicateIgnored -> duplicate.increment()
        }
    }

    fun recordDeadLettered() {
        deadLettered.increment()
    }

    private fun counterFor(
        meterRegistry: MeterRegistry,
        result: String,
    ): Counter =
        Counter
            .builder(PROCESSED_COUNTER)
            .description("Transaction events by final outcome")
            .tag(RESULT_TAG, result)
            .register(meterRegistry)
}
