/*
 * L35 TransactionEventMapperTest: confere o mapeamento com o `JsonMapper` real, incluindo parse, UUID, moeda,
 *     escala, timestamp e campos ausentes. Os casos de privacidade incluem escala inválida e titular usado como
 *     moeda ou UUID inválido, para cobrir causas que ecoam entrada.
 * L119-L124 assertNoSensitiveData: imprime o stack trace inteiro, que é tudo o que um log pode conter da falha, e
 *     confere que nem o titular nem o saldo do evento estão nele.
 *
 * Spec: Evento do tópico é lido conforme o contrato do enunciado; Erro permanente vai para a DLT com o motivo; Dados
 *     pessoais e payload nunca vão para o log
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
import java.io.PrintWriter
import java.io.StringWriter
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

private const val TRUNCATED_CHARACTERS = 12

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

    @Test
    fun `should not carry any part of the payload when the json is invalid`() {
        val truncatedJson = transactionEventJson().dropLast(TRUNCATED_CHARACTERS)

        val failure = assertFailsWith<MalformedTransactionEventException> { mapper.toTransaction(truncatedJson) }

        assertNoSensitiveData(failure)
    }

    @Test
    fun `should not carry any part of the payload when a field is of the wrong type`() {
        val wrongType = transactionEventJson().replace("\"id\": \"$EVENT_ACCOUNT_ID\"", "\"id\": 42")

        val failure = assertFailsWith<MalformedTransactionEventException> { mapper.toTransaction(wrongType) }

        assertNoSensitiveData(failure)
    }

    @Test
    fun `should not carry the owner or the balance when the domain rejects a value`() {
        val invalidCurrency = transactionEventJson(balanceJson = """{"amount": 183.12, "currency": "XYZ"}""")

        val failure = assertFailsWith<MalformedTransactionEventException> { mapper.toTransaction(invalidCurrency) }

        assertNoSensitiveData(failure)
    }

    private fun assertNoSensitiveData(failure: Throwable) {
        val everythingThatCanBeLogged = StringWriter().also { failure.printStackTrace(PrintWriter(it)) }.toString()

        assertFalse(everythingThatCanBeLogged.contains(EVENT_OWNER_ID), everythingThatCanBeLogged)
        assertFalse(everythingThatCanBeLogged.contains(EVENT_BALANCE_AMOUNT), everythingThatCanBeLogged)
    }

    @Test
    fun `should not carry the balance when the domain rejects its decimal scale`() {
        val invalidScale = transactionEventJson(balanceJson = """{"amount": 183.125, "currency": "BRL"}""")

        val failure = assertFailsWith<MalformedTransactionEventException> { mapper.toTransaction(invalidScale) }

        assertNoSensitiveData(failure)
    }

    @Test
    fun `should not carry the owner when an invalid currency contains it`() {
        val invalidCurrency = transactionEventJson(balanceJson = """{"amount": 183.12, "currency": "$EVENT_OWNER_ID"}""")

        val failure = assertFailsWith<MalformedTransactionEventException> { mapper.toTransaction(invalidCurrency) }

        assertNoSensitiveData(failure)
    }

    @Test
    fun `should not carry the owner when an invalid uuid contains it`() {
        val invalidIdentifier = transactionEventJson(accountId = "invalid-$EVENT_OWNER_ID")

        val failure = assertFailsWith<MalformedTransactionEventException> { mapper.toTransaction(invalidIdentifier) }

        assertNoSensitiveData(failure)
    }
}
