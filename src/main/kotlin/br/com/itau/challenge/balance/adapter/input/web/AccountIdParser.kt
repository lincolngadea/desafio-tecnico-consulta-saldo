/*
 * L14 CANONICAL_UUID: só a forma canônica de 36 caracteres é aceita, porque `UUID.fromString` aceita formas como
 *     `1-1-1-1-1` e o enunciado tipa o parâmetro como UUID.
 * L16 parseAccountId: texto inválido é ausência esperada e volta como `null`, e não como exceção; o controller o
 *     traduz em `400`.
 *
 * Enunciado: O que construir → Exposição (API REST) → Contrato de request → accountId
 */
package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.domain.model.AccountId
import java.util.UUID

private val CANONICAL_UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

internal fun parseAccountId(text: String): AccountId? = if (CANONICAL_UUID.matches(text)) AccountId(UUID.fromString(text)) else null
