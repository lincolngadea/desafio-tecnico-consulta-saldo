package br.com.itau.challenge.balance.port.output

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot

fun interface BalanceProvider {
    /**
     * Returns the latest stored snapshot of [accountId], reflecting every save that completed before the call,
     * or `null` when the account has no snapshot.
     *
     * @throws TransientStorageException when retrying later may succeed.
     * @throws PermanentStorageException when retrying will not help, including a stored snapshot that is malformed.
     */
    fun findByAccountId(accountId: AccountId): BalanceSnapshot?
}
