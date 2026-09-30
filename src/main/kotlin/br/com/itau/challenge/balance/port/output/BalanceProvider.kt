/*
 * L12 BalanceProvider: lê o snapshot de saldo mais recente de uma conta pela chave, que é tudo o que a consulta de
 *     saldo precisa. O contrato de `findByAccountId` (retorno e exceções) continua no KDoc da assinatura.
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.port.output

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot

fun interface BalanceProvider {
    /**
     * Devolve o snapshot mais recente gravado de [accountId], refletindo toda gravação concluída antes da chamada,
     * ou `null` quando a conta não tem snapshot.
     *
     * @throws TransientStorageException quando tentar de novo mais tarde pode dar certo.
     * @throws PermanentStorageException quando tentar de novo não ajuda, inclusive com snapshot gravado malformado.
     */
    fun findByAccountId(accountId: AccountId): BalanceSnapshot?
}
