/*
 * L33 SafeTextRepresentationTest: prova que interpolar um objeto de domínio em uma mensagem não vaza o titular nem a
 *     conta inteira, porque o `toString` é a única forma de um dado pessoal chegar a um log sem ninguém o pedir.
 *
 * Spec: Tipos de domínio têm representação textual segura
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.balance.domain.model

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ACCOUNT_UUID = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975"
private const val OWNER_UUID = "315e3cfe-f4af-4cd2-b298-a449e614349a"
private const val ACCOUNT_FIRST_GROUP = "5b19c8b6"

private val ACCOUNT_ID = AccountId(UUID.fromString(ACCOUNT_UUID))
private val OWNER_ID = OwnerId(UUID.fromString(OWNER_UUID))

private val PROCESSED_TRANSACTION =
    ProcessedTransaction(
        transactionId = TransactionId(UUID.fromString("8e8ae808-b154-48b5-9f3e-553935cc4543")),
        timestamp = EventTimestamp(1751641364589998L),
        accountId = ACCOUNT_ID,
        ownerId = OWNER_ID,
        balance = Money.of(BigDecimal("183.12"), "BRL"),
    )

class SafeTextRepresentationTest {

    @Test
    fun `should not reveal any digit of the owner when the owner id is converted to text`() {
        val text = OWNER_ID.toString()

        assertFalse(OWNER_UUID.split("-").any { group -> text.contains(group) }, text)
    }

    @Test
    fun `should show only the first group when the account id is converted to text`() {
        val text = ACCOUNT_ID.toString()

        assertTrue(text.contains(ACCOUNT_FIRST_GROUP), text)
        assertFalse(text.contains(ACCOUNT_UUID), text)
        assertFalse(ACCOUNT_UUID.split("-").drop(1).any { group -> text.contains(group) }, text)
    }

    @Test
    fun `should not leak owner or account when a processed transaction is converted to text`() {
        val text = PROCESSED_TRANSACTION.toString()

        assertFalse(text.contains(OWNER_UUID), text)
        assertFalse(text.contains(ACCOUNT_UUID), text)
    }

    @Test
    fun `should not leak owner or account when a snapshot is converted to text`() {
        val text = PROCESSED_TRANSACTION.toSnapshot().toString()

        assertFalse(text.contains(OWNER_UUID), text)
        assertFalse(text.contains(ACCOUNT_UUID), text)
    }

    @Test
    fun `should keep the real values available when the ids are read`() {
        val snapshot = PROCESSED_TRANSACTION.toSnapshot()

        assertEquals(UUID.fromString(ACCOUNT_UUID), snapshot.accountId.value)
        assertEquals(UUID.fromString(OWNER_UUID), snapshot.ownerId.value)
    }
}
