/*
 * L26 IngestionErrorHandlerFactory: monta o error handler do container sem expô-lo como bean, porque o Spring Boot
 *     aplicaria um bean de error handler também ao container do consumer do kit (add-transaction-ingestion design
 *     D8).
 * L33-L41 newErrorHandler: só `TransientStorageException` é tentada de novo (`defaultFalse`); `setCommitRecovered`
 *     porque em commit manual o handler confirma o offset depois que a DLT aceita o registro
 *     (add-transaction-ingestion design D4).
 * L43-L47 pauseListener: pausa pelo id do listener, e o `ListenerContainerPauseService` agenda a retomada; o aviso
 *     informa por quanto tempo, para a pausa ser observável.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.port.output.TransientStorageException
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.ListenerContainerPauseService
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class IngestionErrorHandlerFactory(
    private val kafkaOperations: KafkaOperations<String, String>,
    private val pauseService: ListenerContainerPauseService,
    private val properties: IngestionProperties,
) {
    private val logger = LoggerFactory.getLogger(IngestionErrorHandlerFactory::class.java)

    fun newErrorHandler(): DefaultErrorHandler {
        val deadLetterRecoverer =
            DeadLetterPublishingRecoverer(kafkaOperations) { record, _ -> TopicPartition(properties.dltTopicName, record.partition()) }
        return DefaultErrorHandler(IngestionRecoverer(deadLetterRecoverer, ListenerPause(::pauseListener)), ingestionBackOff(properties)).apply {
            defaultFalse()
            addRetryableExceptions(TransientStorageException::class.java)
            setCommitRecovered(true)
        }
    }

    private fun pauseListener(): Duration {
        logger.warn("Pausing the transaction listener for {} because the balance storage is unavailable", properties.pauseDuration)
        pauseService.pause(INGESTION_LISTENER_ID, properties.pauseDuration)
        return properties.pauseDuration
    }
}
