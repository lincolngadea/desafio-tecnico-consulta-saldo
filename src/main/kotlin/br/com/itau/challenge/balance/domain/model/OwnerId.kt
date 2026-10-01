/*
 * L14 OwnerId: identifica o titular da conta, devolvido ao cliente como `owner`. É um tipo próprio para nunca ser
 *     confundido com o id da conta ou o da transação, que também são UUIDs.
 * L15 toString: não revela o titular, dado pessoal que nunca pode ir para log, nem por interpolação de um objeto
 *     composto (add-observability design D4).
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.domain.model

import java.util.UUID

@JvmInline
value class OwnerId(val value: UUID) {
    override fun toString(): String = "OwnerId(****)"
}
