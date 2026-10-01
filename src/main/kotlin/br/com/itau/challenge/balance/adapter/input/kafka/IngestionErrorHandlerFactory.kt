/*
 * L18 IngestionErrorHandlerFactory: monta o error handler do container sem expô-lo como bean, porque o Spring Boot
 *     aplicaria um bean de error handler também ao container do consumer do kit (add-transaction-ingestion design
 *     D8).
 * L23-L28 newErrorHandler: só `TransientStorageException` é tentada de novo (`defaultFalse`); `setCommitRecovered`
 *     porque em commit manual o handler confirma o offset depois que a DLT aceita o registro
 *     (add-transaction-ingestion design D4).
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.port.output.TransientStorageException
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.stereotype.Component

@Component
class IngestionErrorHandlerFactory(
    private val ingestionRecoverer: IngestionRecoverer,
    private val properties: IngestionProperties,
) {

    fun newErrorHandler(): DefaultErrorHandler =
        DefaultErrorHandler(ingestionRecoverer, ingestionBackOff(properties)).apply {
            defaultFalse()
            addRetryableExceptions(TransientStorageException::class.java)
            setCommitRecovered(true)
        }
}
