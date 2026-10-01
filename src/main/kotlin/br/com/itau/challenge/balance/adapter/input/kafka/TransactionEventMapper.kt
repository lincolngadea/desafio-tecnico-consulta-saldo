/*
 * L23 TransactionEventMapper: traduz o JSON do enunciado para o evento de domínio no adapter, para o domínio
 *     continuar sem Jackson (Art. 1).
 * L27-L34 toTransaction: qualquer falha de parse ou de invariante do domínio vira
 *     `MalformedTransactionEventException`, um erro permanente que vai para a DLT em vez de travar o consumo.
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.adapter.input.kafka.dto.TransactionEventMessage
import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import br.com.itau.challenge.balance.domain.model.OwnerId
import br.com.itau.challenge.balance.domain.model.ProcessedTransaction
import br.com.itau.challenge.balance.domain.model.TransactionId
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

@Component
class TransactionEventMapper(
    private val objectMapper: ObjectMapper,
) {

    fun toTransaction(payload: String): ProcessedTransaction =
        try {
            objectMapper.readValue(payload, TransactionEventMessage::class.java).toTransaction()
        } catch (invalidJson: JacksonException) {
            throw MalformedTransactionEventException("Transaction event is not a valid JSON for the contract", invalidJson)
        } catch (invalidValue: IllegalArgumentException) {
            throw MalformedTransactionEventException("Transaction event carries a value the domain rejects", invalidValue)
        }

    private fun TransactionEventMessage.toTransaction(): ProcessedTransaction =
        ProcessedTransaction(
            transactionId = TransactionId(transaction.id),
            timestamp = EventTimestamp(transaction.timestamp),
            accountId = AccountId(account.id),
            ownerId = OwnerId(account.owner),
            balance = Money.of(account.balance.amount, account.balance.currency),
        )
}
