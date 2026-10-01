/*
 * L20 INGESTION_LISTENER_ID: id fixo, para a configuração do error handler achar o container e pausá-lo.
 * L23 TransactionEventListener: ponto de entrada do tópico de transações: lê o evento, chama o caso de uso e só
 *     então confirma o offset (at-least-once, add-transaction-ingestion design D2).
 * L36-L44 consume: o offset é confirmado depois de `processTransaction` retornar. O desfecho é contado na métrica, e
 *     duplicado ou mais antigo, que são resultado esperado neste fluxo, saem em `DEBUG` com o `accountId` mascarado,
 *     para não inundar o log em alto volume.
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.input.ProcessTransactionUseCase
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

const val INGESTION_LISTENER_ID = "balance-transaction-ingestion"

@Component
class TransactionEventListener(
    private val processTransactionUseCase: ProcessTransactionUseCase,
    private val transactionEventMapper: TransactionEventMapper,
    private val outcomeMetrics: TransactionOutcomeMetrics,
) {
    private val logger = LoggerFactory.getLogger(TransactionEventListener::class.java)

    @KafkaListener(
        id = INGESTION_LISTENER_ID,
        topics = ["\${ingestion.topic-name}"],
        groupId = "\${ingestion.consumer-group-id}",
        containerFactory = TRANSACTION_INGESTION_CONTAINER_FACTORY,
    )
    fun consume(payload: String, acknowledgment: Acknowledgment) {
        val transaction = transactionEventMapper.toTransaction(payload)
        val result = processTransactionUseCase.processTransaction(transaction)
        outcomeMetrics.record(result)
        if (result != SnapshotSaveResult.Applied) {
            logger.debug("Ignored event {} of {}: {}", transaction.transactionId.value, transaction.accountId, result)
        }
        acknowledgment.acknowledge()
    }
}
