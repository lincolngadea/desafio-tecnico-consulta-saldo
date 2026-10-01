/*
 * L39 GetBalanceServiceTest: o fake do `BalanceProvider` implementa o mesmo port, e os testes de falha confirmam que
 *     a exceção chega intacta ao chamador. O `SNAPSHOT` é local porque a camada `application` não pode importar o
 *     adapter, nem nos testes.
 *
 * Spec: Consulta devolve o snapshot mais recente da conta; Conta sem snapshot é ausência esperada; Falhas do
 *     armazenamento não são engolidas
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.application

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import br.com.itau.challenge.balance.domain.model.OwnerId
import br.com.itau.challenge.balance.domain.model.SnapshotVersion
import br.com.itau.challenge.balance.domain.model.TransactionId
import br.com.itau.challenge.balance.port.output.BalanceProvider
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

private val SNAPSHOT =
    BalanceSnapshot(
        accountId = AccountId(UUID.fromString("5b19c8b6-0cc4-4c72-a989-0c2ee15fa975")),
        ownerId = OwnerId(UUID.fromString("315e3cfe-f4af-4cd2-b298-a449e614349a")),
        balance = Money.of(BigDecimal("183.12"), "BRL"),
        version = SnapshotVersion(EventTimestamp(1751641364589998L), TransactionId(UUID.fromString("8e8ae808-b154-48b5-9f3e-553935cc4543"))),
    )

class GetBalanceServiceTest {

    private val requestedAccounts = mutableListOf<AccountId>()

    private fun serviceReturning(outcome: () -> BalanceSnapshot?) =
        GetBalanceService(
            BalanceProvider { accountId ->
                requestedAccounts.add(accountId)
                outcome()
            },
        )

    @Test
    fun `should return the snapshot of the provider when the account has one`() {
        val snapshot = serviceReturning { SNAPSHOT }.getBalance(SNAPSHOT.accountId)

        assertSame(SNAPSHOT, snapshot)
    }

    @Test
    fun `should ask the provider once with the requested account id`() {
        val accountId = AccountId(UUID.randomUUID())

        serviceReturning { SNAPSHOT }.getBalance(accountId)

        assertEquals(listOf(accountId), requestedAccounts)
    }

    @Test
    fun `should return null without error when the account has no snapshot`() {
        val snapshot = serviceReturning { null }.getBalance(SNAPSHOT.accountId)

        assertNull(snapshot)
    }

    @Test
    fun `should propagate the transient failure when the provider fails transiently`() {
        val failure = TransientStorageException("throttled", RuntimeException())

        val thrown = assertFailsWith<TransientStorageException> { serviceReturning { throw failure }.getBalance(SNAPSHOT.accountId) }

        assertSame(failure, thrown)
    }

    @Test
    fun `should propagate the unavailable failure when the storage is unavailable`() {
        val failure = StorageUnavailableException("circuit open", RuntimeException())

        val thrown = assertFailsWith<StorageUnavailableException> { serviceReturning { throw failure }.getBalance(SNAPSHOT.accountId) }

        assertSame(failure, thrown)
    }

    @Test
    fun `should propagate the permanent failure when the provider fails permanently`() {
        val failure = PermanentStorageException("malformed", RuntimeException())

        val thrown = assertFailsWith<PermanentStorageException> { serviceReturning { throw failure }.getBalance(SNAPSHOT.accountId) }

        assertSame(failure, thrown)
    }
}
