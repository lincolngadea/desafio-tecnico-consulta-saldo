/*
 * L16 GetBalanceService: só orquestra o caso de uso: o adapter de entrada não pode depender do port de saída, e a
 *     regra "conta sem saldo é ausência e falha de armazenamento sobe" fica numa fronteira testável sem HTTP.
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.application

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.port.input.GetBalanceUseCase
import br.com.itau.challenge.balance.port.output.BalanceProvider
import org.springframework.stereotype.Service

@Service
class GetBalanceService(
    private val balanceProvider: BalanceProvider,
) : GetBalanceUseCase {

    override fun getBalance(accountId: AccountId): BalanceSnapshot? = balanceProvider.findByAccountId(accountId)
}
