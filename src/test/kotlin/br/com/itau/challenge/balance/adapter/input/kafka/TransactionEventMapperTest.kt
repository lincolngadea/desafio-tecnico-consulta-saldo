/*
 * L26 TransactionEventMapperTest: confere o mapeamento com o `JsonMapper` real, e a lista de eventos malformados
 *     cobre parse, UUID, moeda, escala do valor, timestamp e campo obrigatório ausente.
 *
 * Spec: Evento do tópico é lido conforme o contrato do enunciado; Erro permanente vai para a DLT com o motivo
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import br.com.itau.challenge.balance.domain.model.OwnerId
import br.com.itau.challenge.balance.domain.model.ProcessedTransaction
import br.com.itau.challenge.balance.domain.model.TransactionId
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class TransactionEventMapperTest {

    private val mapper = TransactionEventMapper(JsonMapper.builder().build())

    @Test
    fun `should map the contract fields to the processed transaction when the event is valid`() {
        val transaction = mapper.toTransaction(transactionEventJson())

        assertEquals(
            ProcessedTransaction(
                transactionId = TransactionId(UUID.fromString(EVENT_TRANSACTION_ID)),
                timestamp = EventTimestamp(EVENT_TIMESTAMP_MICROS),
                accountId = AccountId(UUID.fromString(EVENT_ACCOUNT_ID)),
                ownerId = OwnerId(UUID.fromString(EVENT_OWNER_ID)),
                balance = Money.of(BigDecimal("183.12"), "BRL"),
            ),
            transaction,
        )
    }

    @Test
    fun `should keep the exact balance amount when the event has two decimal places`() {
        val transaction = mapper.toTransaction(transactionEventJson(balanceJson = """{"amount": 1234.50, "currency": "BRL"}"""))

        assertEquals(BigDecimal("1234.50"), transaction.balance.amount)
    }

    @Test
    fun `should map the event when the transaction status is not a known one`() {
        val transaction = mapper.toTransaction(transactionEventJson(status = "SOMETHING_NEW"))

        assertEquals(AccountId(UUID.fromString(EVENT_ACCOUNT_ID)), transaction.accountId)
    }

    @ParameterizedTest
    @MethodSource("malformedEvents")
    fun `should fail with a malformed event exception that keeps the cause when the event is invalid`(payload: String) {
        val failure = assertFailsWith<MalformedTransactionEventException> { mapper.toTransaction(payload) }

        assertNotNull(failure.cause)
    }

    companion object {
        @JvmStatic
        fun malformedEvents(): List<String> =
            listOf(
                "this is not json",
                "{",
                transactionEventJson(accountId = "not-a-uuid"),
                transactionEventJson(balanceJson = """{"amount": 10.00, "currency": "XXX-NOT-ISO"}"""),
                transactionEventJson(balanceJson = """{"amount": 10.005, "currency": "BRL"}"""),
                transactionEventJson(timestampMicros = 0),
                transactionEventJson(balanceJson = "null"),
                """{"transaction": {"id": "$EVENT_TRANSACTION_ID", "timestamp": $EVENT_TIMESTAMP_MICROS}}""",
            )
    }
}
