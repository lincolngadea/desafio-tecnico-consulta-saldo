/*
 * L13 BalanceApiProperties: o valor do `Retry-After` do `503` é uma dica ao cliente e precisa poder mudar por
 *     variável de ambiente sem alterar código.
 *
 * Enunciado: O que será avaliado → Tratamento de cenários adversos
 */
package br.com.itau.challenge.balance.adapter.input.web

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("balance.api")
data class BalanceApiProperties(
    val retryAfter: Duration,
)
