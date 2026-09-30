/*
 * L12 OwnerId: identifica o titular da conta, devolvido ao cliente como `owner`. É um tipo próprio para nunca ser
 *     confundido com o id da conta ou o da transação, que também são UUIDs.
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.domain.model

import java.util.UUID

@JvmInline
value class OwnerId(val value: UUID)
