/*
 * L16 ProcessTransactionService: só orquestra o caso de uso: a regra de montar o snapshot fica no domínio e a de
 *     ordem no `SnapshotVersion`, aplicada pelo repositório.
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.application

import br.com.itau.challenge.balance.domain.model.ProcessedTransaction
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.input.ProcessTransactionUseCase
import br.com.itau.challenge.balance.port.output.BalanceRepository
import org.springframework.stereotype.Service

@Service
class ProcessTransactionService(
    private val balanceRepository: BalanceRepository,
) : ProcessTransactionUseCase {

    override fun processTransaction(transaction: ProcessedTransaction): SnapshotSaveResult =
        balanceRepository.saveIfNewer(transaction.toSnapshot())
}
