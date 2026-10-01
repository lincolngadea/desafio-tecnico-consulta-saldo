/*
 * L13 CircuitBreakerProperties: agrupa os parâmetros do circuit breaker do repositório de saldo, para serem ajustados
 *     por variável de ambiente sem alterar código.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.resilience

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("balance.circuit-breaker")
data class CircuitBreakerProperties(
    val failureRateThreshold: Float,
    val slidingWindowSize: Int,
    val waitDurationInOpenState: Duration,
    val halfOpenCalls: Int,
)
