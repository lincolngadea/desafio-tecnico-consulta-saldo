/*
 * L13 GetBalanceUseCase: o controller depende deste port, e não do serviço, para a consulta ser testada com um fake
 *     e a origem do pedido poder mudar sem tocar o caso de uso. A conta sem snapshot é `null`, e não exceção, porque
 *     é um resultado esperado; o contrato de retorno e de exceções fica no KDoc da assinatura.
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.port.input

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot

fun interface GetBalanceUseCase {
    /**
     * Devolve o snapshot de saldo mais recente de [accountId], ou `null` quando a conta não tem snapshot.
     *
     * @throws br.com.itau.challenge.balance.port.output.TransientStorageException quando tentar de novo mais tarde
     * pode dar certo.
     * @throws br.com.itau.challenge.balance.port.output.StorageUnavailableException quando o armazenamento está
     * indisponível no momento.
     * @throws br.com.itau.challenge.balance.port.output.PermanentStorageException quando tentar de novo não ajuda.
     */
    fun getBalance(accountId: AccountId): BalanceSnapshot?
}
