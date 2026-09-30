/*
 * L12 AccountId: identifica a conta cujo saldo mais recente é gravado. É um tipo próprio para nunca ser confundido
 *     com o id do titular ou o da transação, que também são UUIDs.
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.domain.model

import java.util.UUID

@JvmInline
value class AccountId(val value: UUID)