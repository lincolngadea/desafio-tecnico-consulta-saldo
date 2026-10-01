/*
 * L20 BalanceCircuitBreakerConfigTest: um parâmetro que não chega ao circuito real volta ao padrão da biblioteca sem
 *     ninguém perceber, então o teste confere o circuito criado pela fábrica de produção, como no
 *     `DynamoDbConfigTest`.
 *
 * Spec: Parâmetros do circuito configuráveis por variável de ambiente
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.resilience

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

private const val FAILURE_RATE_THRESHOLD = 80f
private const val SLIDING_WINDOW_SIZE = 20
private const val HALF_OPEN_CALLS = 2
private val WAIT_DURATION_IN_OPEN_STATE: Duration = Duration.ofSeconds(5)

class BalanceCircuitBreakerConfigTest {

    private val properties =
        CircuitBreakerProperties(
            failureRateThreshold = FAILURE_RATE_THRESHOLD,
            slidingWindowSize = SLIDING_WINDOW_SIZE,
            waitDurationInOpenState = WAIT_DURATION_IN_OPEN_STATE,
            halfOpenCalls = HALF_OPEN_CALLS,
        )

    private val configuration = BalanceCircuitBreakerConfig().balanceCircuitBreaker(properties).circuitBreakerConfig

    @Test
    fun `should use the failure rate threshold from the properties when the circuit breaker is created`() {
        assertEquals(FAILURE_RATE_THRESHOLD, configuration.failureRateThreshold)
    }

    @Test
    fun `should use the sliding window size as window and minimum calls when the circuit breaker is created`() {
        assertEquals(SLIDING_WINDOW_SIZE, configuration.slidingWindowSize)
        assertEquals(SLIDING_WINDOW_SIZE, configuration.minimumNumberOfCalls)
    }

    @Test
    fun `should use the wait duration from the properties when the circuit breaker is created`() {
        assertEquals(WAIT_DURATION_IN_OPEN_STATE, configuration.waitIntervalFunctionInOpenState.apply(1).let { Duration.ofMillis(it) })
    }

    @Test
    fun `should use the half open calls from the properties when the circuit breaker is created`() {
        assertEquals(HALF_OPEN_CALLS, configuration.permittedNumberOfCallsInHalfOpenState)
    }
}
