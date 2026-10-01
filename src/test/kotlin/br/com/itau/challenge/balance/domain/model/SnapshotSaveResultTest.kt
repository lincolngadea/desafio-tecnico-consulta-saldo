/*
 * L24 SnapshotSaveResultTest: fixa a regra de recusa no domínio: só a mesma versão é duplicata, e o empate de
 *     timestamp com id de transação menor é evento antigo.
 *
 * Spec: Gravação condicional do snapshot
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
package br.com.itau.challenge.balance.domain.model

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertSame

private const val STORED_MICROS = 1751641364589998L

private val LOW_ID = TransactionId(UUID.fromString("10000000-0000-4000-8000-000000000000"))
private val HIGH_ID = TransactionId(UUID.fromString("20000000-0000-4000-8000-000000000000"))

private fun versionOf(
    micros: Long,
    transactionId: TransactionId,
) = SnapshotVersion(EventTimestamp(micros), transactionId)

class SnapshotSaveResultTest {

    private val stored = versionOf(STORED_MICROS, HIGH_ID)

    @Test
    fun `should be a duplicate when the refused version is the stored one`() {
        assertSame(SnapshotSaveResult.DuplicateIgnored, SnapshotSaveResult.refusedBecauseOf(stored, stored))
    }

    @Test
    fun `should be stale when the refused version has an older timestamp`() {
        val older = versionOf(STORED_MICROS - 1, HIGH_ID)

        assertSame(SnapshotSaveResult.StaleIgnored, SnapshotSaveResult.refusedBecauseOf(older, stored))
    }

    @Test
    fun `should be stale when the timestamps tie and the refused transaction id is lower`() {
        val tieWithLowerId = versionOf(STORED_MICROS, LOW_ID)

        assertSame(SnapshotSaveResult.StaleIgnored, SnapshotSaveResult.refusedBecauseOf(tieWithLowerId, stored))
    }
}
