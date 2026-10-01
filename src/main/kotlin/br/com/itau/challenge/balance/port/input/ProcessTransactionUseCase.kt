/*
 * L13 ProcessTransactionUseCase: o consumer depende deste port, e não do serviço, para a ingestão ser testada com um
 *     fake e a origem dos eventos poder mudar sem tocar o caso de uso; o contrato de retorno e de exceções fica no
 *     KDoc da assinatura.
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.port.input

import br.com.itau.challenge.balance.domain.model.ProcessedTransaction
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult

fun interface ProcessTransactionUseCase {
    /**
     * Grava o saldo trazido por [transaction] como snapshot da conta, sem recalculá-lo.
     *
     * @return o resultado da gravação: [SnapshotSaveResult.Applied] ou [SnapshotSaveResult.StaleIgnored].
     * @throws br.com.itau.challenge.balance.port.output.TransientStorageException quando tentar de novo mais tarde
     * pode dar certo.
     * @throws br.com.itau.challenge.balance.port.output.StorageUnavailableException quando o armazenamento está
     * indisponível no momento.
     * @throws br.com.itau.challenge.balance.port.output.PermanentStorageException quando tentar de novo não ajuda.
     */
    fun processTransaction(transaction: ProcessedTransaction): SnapshotSaveResult
}
