/*
 * L14 TransactionEventMessage: lê só os campos de que o snapshot precisa. `ignoreUnknown` evita que um campo novo do
 *     autorizador quebre a ingestão, e `amount` é `BigDecimal` para o saldo não perder precisão.
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.adapter.input.kafka.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class TransactionEventMessage(val transaction: TransactionPayload, val account: AccountPayload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TransactionPayload(val id: UUID, val timestamp: Long)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AccountPayload(val id: UUID, val owner: UUID, val balance: BalancePayload)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BalancePayload(val amount: BigDecimal, val currency: String)
}
