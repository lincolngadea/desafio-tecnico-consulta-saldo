/*
 * L23 BalanceResponseMapperTest: fixa o formato de `updated_at` (milissegundos, offset de Brasília, três dígitos
 *     fixos) e a escala do valor; o caso de 2017 prova o offset de horário de verão.
 *
 * Spec: Resposta de sucesso segue o contrato do enunciado
 * Enunciado: O que construir → Exposição (API REST) → Contrato de resposta → updated_at
 */
package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.adapter.output.dynamodb.SNAPSHOT
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import br.com.itau.challenge.balance.domain.model.SnapshotVersion
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

private const val EVENT_MICROS_WITH_FRACTION = 1751641364589998L
private const val EVENT_MICROS_ON_THE_SECOND = 1751641364000000L
private const val EVENT_MICROS_BEFORE_2019 = 1514764800123456L

class BalanceResponseMapperTest {

    private fun snapshotAt(micros: Long) = SNAPSHOT.copy(version = SnapshotVersion(EventTimestamp(micros), SNAPSHOT.version.transactionId))

    @Test
    fun `should map the identifiers and the balance when the snapshot is mapped`() {
        val response = SNAPSHOT.toResponse()

        assertEquals(SNAPSHOT.accountId.value, response.id)
        assertEquals(SNAPSHOT.ownerId.value, response.owner)
        assertEquals(BigDecimal("183.12"), response.balance.amount)
        assertEquals("BRL", response.balance.currency)
    }

    @Test
    fun `should keep the currency scale when the balance has trailing zeros`() {
        val snapshot = SNAPSHOT.copy(balance = Money.of(BigDecimal("183.1"), "BRL"))

        assertEquals("183.10", snapshot.toResponse().balance.amount.toPlainString())
    }

    @Test
    fun `should format the instant in milliseconds with the Sao Paulo offset when the event has microseconds`() {
        assertEquals("2025-07-04T12:02:44.589-03:00", snapshotAt(EVENT_MICROS_WITH_FRACTION).toResponse().updatedAt)
    }

    @Test
    fun `should keep three fraction digits when the instant falls on an exact second`() {
        assertEquals("2025-07-04T12:02:44.000-03:00", snapshotAt(EVENT_MICROS_ON_THE_SECOND).toResponse().updatedAt)
    }

    @Test
    fun `should use the daylight saving offset when the instant is before 2019`() {
        assertEquals("2017-12-31T22:00:00.123-02:00", snapshotAt(EVENT_MICROS_BEFORE_2019).toResponse().updatedAt)
    }

    @Test
    fun `should return the owner and the account as separate identifiers when they differ`() {
        val response = SNAPSHOT.toResponse()

        assertEquals(UUID.fromString("315e3cfe-f4af-4cd2-b298-a449e614349a"), response.owner)
    }
}
