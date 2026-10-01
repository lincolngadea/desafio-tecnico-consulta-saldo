/*
 * L22 IngestionRecoverer: decide o destino de um registro de que o Spring desistiu: dependência fora do ar pausa o
 *     consumer e mantém o registro; todo o resto vai para a DLT. Assim evento bom nunca vai para a DLT por culpa do
 *     DynamoDB (add-transaction-ingestion design D4).
 * L29-L43 accept: a pausa é pedida antes de lançar `ListenerPausedException`, para o Spring devolver o registro sem
 *     commit; o descarte para a DLT é logado só com o tipo da causa, porque a mensagem pode conter trecho do
 *     payload, e o motivo completo já vai nos headers da DLT. O `dlq` só é contado depois de a DLT aceitar o
 *     registro, porque sem a publicação o registro não tem desfecho.
 * L45-L46 Throwable.isDependencyFailure: percorre a cadeia de causas porque o Spring embrulha a exceção do listener
 *     em `ListenerExecutionFailedException`.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.ConsumerRecordRecoverer

class IngestionRecoverer(
    private val deadLetterRecoverer: ConsumerRecordRecoverer,
    private val listenerPause: ListenerPause,
    private val outcomeMetrics: TransactionOutcomeMetrics,
) : ConsumerRecordRecoverer {
    private val logger = LoggerFactory.getLogger(IngestionRecoverer::class.java)

    override fun accept(record: ConsumerRecord<*, *>, failure: Exception) {
        if (failure.isDependencyFailure()) {
            throw ListenerPausedException(record, listenerPause.pause(), failure)
        }
        val rootCause = failure.rootCause()
        logger.warn(
            "Dead lettering record {}-{}@{} because of {}",
            record.topic(),
            record.partition(),
            record.offset(),
            rootCause.javaClass.simpleName,
        )
        deadLetterRecoverer.accept(record, failure)
        outcomeMetrics.recordDeadLettered()
    }

    private fun Throwable.isDependencyFailure(): Boolean =
        generateSequence(this) { it.cause }.any { it is TransientStorageException || it is StorageUnavailableException }

    private fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()
}
