/*
 * L14 AccountId: identifica a conta cujo saldo mais recente é gravado. É um tipo próprio para nunca ser confundido
 *     com o id do titular ou o da transação, que também são UUIDs.
 * L15 toString: mostra só o primeiro grupo do UUID para a conta ir mascarada para o log
 *     (add-observability design D4).
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.domain.model

import java.util.UUID

@JvmInline
value class AccountId(val value: UUID) {
    override fun toString(): String = "AccountId(${value.toString().substringBefore('-')})"
}
