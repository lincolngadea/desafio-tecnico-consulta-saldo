/*
 * L20 SnapshotVersionTest: fixa a regra única que decide qual snapshot é o mais recente, inclusive o desempate em
 *     que a ordem textual e `UUID.compareTo` divergem.
 * L43 signedNegativeId: `UUID.compareTo` compara longs com sinal, então este id é "menor" como UUID, mas maior como
 *     texto.
 *
 * Spec: Ordem entre versões de snapshot
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
package br.com.itau.challenge.balance.domain.model

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EARLIER_MICROS = 1751641364589998L
private const val LATER_MICROS = 1751641364589999L

class SnapshotVersionTest {

    private val lowTransactionId = TransactionId(UUID.fromString("10000000-0000-0000-0000-000000000000"))
    private val highTransactionId = TransactionId(UUID.fromString("20000000-0000-0000-0000-000000000000"))

    @Test
    fun `should be newer when the timestamp is greater regardless of transaction id`() {
        val earlier = SnapshotVersion(EventTimestamp(EARLIER_MICROS), highTransactionId)
        val later = SnapshotVersion(EventTimestamp(LATER_MICROS), lowTransactionId)

        assertTrue(later > earlier)
    }

    @Test
    fun `should be newer when timestamps tie and the transaction id is lexicographically greater`() {
        val lower = SnapshotVersion(EventTimestamp(EARLIER_MICROS), lowTransactionId)
        val higher = SnapshotVersion(EventTimestamp(EARLIER_MICROS), highTransactionId)

        assertTrue(higher > lower)
    }

    @Test
    fun `should break a timestamp tie by text order even when UUID compareTo disagrees`() {
        val signedNegativeId = TransactionId(UUID.fromString("80000000-0000-0000-0000-000000000000"))
        val lower = SnapshotVersion(EventTimestamp(EARLIER_MICROS), lowTransactionId)
        val higher = SnapshotVersion(EventTimestamp(EARLIER_MICROS), signedNegativeId)

        assertTrue(higher > lower)
    }

    @Test
    fun `should not be newer than itself when timestamp and transaction id are equal`() {
        val version = SnapshotVersion(EventTimestamp(EARLIER_MICROS), lowTransactionId)
        val duplicate = SnapshotVersion(EventTimestamp(EARLIER_MICROS), lowTransactionId)

        assertEquals(0, version.compareTo(duplicate))
    }
}
