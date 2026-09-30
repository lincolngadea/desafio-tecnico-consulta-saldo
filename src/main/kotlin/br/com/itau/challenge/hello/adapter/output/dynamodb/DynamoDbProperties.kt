/*
 * L18 DynamoDbProperties: agrupa as configurações do cliente DynamoDB, para todo timeout e o limite de tentativas
 *     serem explícitos e poderem ser sobrescritos por variável de ambiente.
 * L24 Timeouts: são quatro limites porque cada camada limita a sua parte: uma falha de rede não consome todo o teto
 *     da chamada, e uma tentativa lenta não consome as demais. `apiCall` limita a chamada inteira, somando todas as
 *     tentativas.
 * L31 Retry: `maxAttempts` conta a primeira chamada: 2 significa uma tentativa original e uma repetição.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.hello.adapter.output.dynamodb

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("dynamodb")
data class DynamoDbProperties(
    val endpoint: URI,
    val region: String,
    val timeouts: Timeouts,
    val retry: Retry,
) {
    data class Timeouts(
        val connection: Duration,
        val socket: Duration,
        val apiCallAttempt: Duration,
        val apiCall: Duration,
    )

    data class Retry(val maxAttempts: Int)
}
