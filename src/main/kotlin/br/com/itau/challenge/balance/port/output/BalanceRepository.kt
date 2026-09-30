package br.com.itau.challenge.balance.port.output

import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult

fun interface BalanceRepository {
    /**
     * Stores [snapshot] only if its account has no snapshot yet or if its version is newer than the stored one,
     * as defined by `SnapshotVersion`; the check and the write are a single atomic operation.
     *
     * @return [SnapshotSaveResult.Applied] when stored, [SnapshotSaveResult.StaleIgnored] when the stored snapshot
     * is as recent or more recent (out-of-order or duplicate event).
     * @throws TransientStorageException when retrying later may succeed.
     * @throws PermanentStorageException when retrying will not help.
     */
    fun saveIfNewer(snapshot: BalanceSnapshot): SnapshotSaveResult
}
