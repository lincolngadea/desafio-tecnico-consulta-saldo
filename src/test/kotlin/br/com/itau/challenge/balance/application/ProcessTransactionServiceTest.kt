/*
 * L53 ProcessTransactionServiceTest: o fake do `BalanceRepository` implementa o mesmo port, e os testes de falha
 *     confirmam que a exceção chega intacta ao chamador.
 *
 * Spec: Transação processada vira snapshot de saldo
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.application

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import br.com.itau.challenge.balance.domain.model.OwnerId
import br.com.itau.challenge.balance.domain.model.ProcessedTransaction
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.domain.model.SnapshotVersion
import br.com.itau.challenge.balance.domain.model.TransactionId
import br.com.itau.challenge.balance.port.output.BalanceRepository
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private val ACCOUNT_ID = AccountId(UUID.fromString("5b19c8b6-0cc4-4c72-a989-0c2ee15fa975"))
private val OWNER_ID = OwnerId(UUID.fromString("315e3cfe-f4af-4cd2-b298-a449e614349a"))
private val TRANSACTION_ID = TransactionId(UUID.fromString("8e8ae808-b154-48b5-9f3e-553935cc4543"))
private val EVENT_TIMESTAMP = EventTimestamp(1751641364589998L)
private val EVENT_BALANCE = Money.of(BigDecimal("50.00"), "BRL")

private val TRANSACTION =
    ProcessedTransaction(
        transactionId = TRANSACTION_ID,
        timestamp = EVENT_TIMESTAMP,
        accountId = ACCOUNT_ID,
        ownerId = OWNER_ID,
        balance = EVENT_BALANCE,
    )

private val EXPECTED_SNAPSHOT =
    BalanceSnapshot(
        accountId = ACCOUNT_ID,
        ownerId = OWNER_ID,
        balance = EVENT_BALANCE,
        version = SnapshotVersion(EVENT_TIMESTAMP, TRANSACTION_ID),
    )

class ProcessTransactionServiceTest {

    private val savedSnapshots = mutableListOf<BalanceSnapshot>()

    private fun serviceReturning(result: SnapshotSaveResult) =
        ProcessTransactionService(
            BalanceRepository { snapshot ->
                savedSnapshots.add(snapshot)
                result
            },
        )

    @Test
    fun `should save the snapshot of the event when a transaction is processed`() {
        serviceReturning(SnapshotSaveResult.Applied).processTransaction(TRANSACTION)

        assertEquals(listOf(EXPECTED_SNAPSHOT), savedSnapshots)
    }

    @Test
    fun `should return applied when the repository applies the snapshot`() {
        val result = serviceReturning(SnapshotSaveResult.Applied).processTransaction(TRANSACTION)

        assertSame(SnapshotSaveResult.Applied, result)
    }

    @Test
    fun `should return stale ignored without error when the repository ignores an old snapshot`() {
        val result = serviceReturning(SnapshotSaveResult.StaleIgnored).processTransaction(TRANSACTION)

        assertSame(SnapshotSaveResult.StaleIgnored, result)
    }

    @Test
    fun `should propagate the transient storage failure when the repository cannot save`() {
        val failure = TransientStorageException("throttled", RuntimeException())
        val service = ProcessTransactionService { throw failure }

        assertSame(failure, assertFailsWith<TransientStorageException> { service.processTransaction(TRANSACTION) })
    }

    @Test
    fun `should propagate the permanent storage failure when the repository cannot save`() {
        val failure = PermanentStorageException("access denied", RuntimeException())
        val service = ProcessTransactionService { throw failure }

        assertSame(failure, assertFailsWith<PermanentStorageException> { service.processTransaction(TRANSACTION) })
    }

    @Test
    fun `should propagate the unavailable failure when the storage circuit is open`() {
        val failure = StorageUnavailableException("circuit open", RuntimeException())
        val service = ProcessTransactionService { throw failure }

        assertSame(failure, assertFailsWith<StorageUnavailableException> { service.processTransaction(TRANSACTION) })
    }
}
