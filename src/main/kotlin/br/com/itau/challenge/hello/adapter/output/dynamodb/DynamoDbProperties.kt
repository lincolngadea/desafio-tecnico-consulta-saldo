/*
 * L20 DynamoDbProperties: agrupa as configurações dos clientes DynamoDB, para todo timeout e o limite de tentativas
 *     serem explícitos e poderem ser sobrescritos por variável de ambiente.
 * L27 Timeouts: são quatro limites porque cada camada limita a sua parte: uma falha de rede não consome todo o teto
 *     da chamada, e uma tentativa lenta não consome as demais. `apiCall` limita a chamada inteira, somando todas as
 *     tentativas.
 * L34 Retry: `maxAttempts` conta a primeira chamada: 2 significa uma tentativa original e uma repetição.
 * L36-L46 Read: perfil da leitura, com as invariantes na construção (Fail Fast): no máximo 1 retry e `maxAttempts ×
 *     api-call-attempt ≤ api-call`, para o retry nunca ser truncado em silêncio nem o pior caso passar do orçamento.
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
    val read: Read,
) {
    data class Timeouts(
        val connection: Duration,
        val socket: Duration,
        val apiCallAttempt: Duration,
        val apiCall: Duration,
    )

    data class Retry(val maxAttempts: Int)

    data class Read(val timeouts: Timeouts, val retry: Retry) {
        init {
            require(retry.maxAttempts in 1..MAX_READ_ATTEMPTS) {
                "dynamodb.read.retry.max-attempts must be between 1 and $MAX_READ_ATTEMPTS (at most 1 retry), got ${retry.maxAttempts}"
            }
            require(timeouts.apiCallAttempt.multipliedBy(retry.maxAttempts.toLong()) <= timeouts.apiCall) {
                "dynamodb.read: maxAttempts (${retry.maxAttempts}) x api-call-attempt (${timeouts.apiCallAttempt}) " +
                    "must fit in api-call (${timeouts.apiCall})"
            }
        }
    }

    private companion object {
        const val MAX_READ_ATTEMPTS = 2
    }
}
