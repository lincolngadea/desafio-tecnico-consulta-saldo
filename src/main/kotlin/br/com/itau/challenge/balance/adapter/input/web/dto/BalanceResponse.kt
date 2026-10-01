/*
 * L19 BalanceResponse: é o contrato de resposta do enunciado, com exatamente estes campos e nomes. `updated_at` usa
 *     `@JsonProperty` porque o contrato é snake_case e o Kotlin não. Enunciado: O que construir → Exposição (API
 *     REST) → Contrato de resposta
 * L34 MoneyResponse: agrupa `balance.amount` e `balance.currency`; o `amount` é `BigDecimal` para o saldo sair como
 *     número JSON exato, sem ponto flutuante. Não se chama `BalancePayload` porque o DTO do Kafka já usa esse nome
 *     para outro conceito. Enunciado: O que construir → Exposição (API REST) → Contrato de resposta → balance.amount
 *
 * Enunciado: O que construir → Exposição (API REST) → Contrato de resposta
 */
package br.com.itau.challenge.balance.adapter.input.web.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.UUID

@Schema(description = "Saldo mais atual de uma conta")
data class BalanceResponse(
    @field:Schema(description = "Identificador da conta", example = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975")
    val id: UUID,
    @field:Schema(description = "Identificador do titular", example = "315e3cfe-f4af-4cd2-b298-a449e614349a")
    val owner: UUID,
    val balance: MoneyResponse,
    @field:JsonProperty("updated_at")
    @field:Schema(
        name = "updated_at",
        description = "Data/hora da última atualização (ISO 8601)",
        example = "2025-07-05T18:04:13.433-03:00",
    )
    val updatedAt: String,
) {
    @Schema(description = "Saldo atual")
    data class MoneyResponse(
        @field:Schema(description = "Saldo atual, com a escala da moeda", example = "183.12")
        val amount: BigDecimal,
        @field:Schema(description = "Código ISO 4217", example = "BRL")
        val currency: String,
    )
}
