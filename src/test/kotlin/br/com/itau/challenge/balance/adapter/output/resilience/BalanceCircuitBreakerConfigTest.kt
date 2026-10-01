/*
 * L31 BalanceCircuitBreakerConfigTest: um parâmetro que não chega ao circuito real volta ao padrão da biblioteca sem
 *     ninguém perceber, então o teste confere o circuito criado pela fábrica de produção, como no
 *     `DynamoDbConfigTest`; também prova que os dois circuitos são independentes e aplicam a mesma classificação.
 * L97-L103 classificação compartilhada: parametrizado sobre os dois beans, para a regra de falha dos dois circuitos
 *     não divergir.
 *
 * Spec: Parâmetros do circuito configuráveis por variável de ambiente; Circuit breaker de leitura com a
 *     classificação de erros compartilhada
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.resilience

import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

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

    private val configuration = BalanceCircuitBreakerConfig().balanceWriteCircuitBreaker(properties).circuitBreakerConfig

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

    @Test
    fun `should keep the read circuit closed when the write circuit opens`() {
        val config = BalanceCircuitBreakerConfig()
        val write = config.balanceWriteCircuitBreaker(properties)
        val read = config.balanceReadCircuitBreaker(properties)

        write.transitionToOpenState()

        assertEquals(CircuitBreaker.State.OPEN, write.state)
        assertEquals(CircuitBreaker.State.CLOSED, read.state)
    }

    @Test
    fun `should keep the write circuit closed when the read circuit opens`() {
        val config = BalanceCircuitBreakerConfig()
        val write = config.balanceWriteCircuitBreaker(properties)
        val read = config.balanceReadCircuitBreaker(properties)

        read.transitionToOpenState()

        assertEquals(CircuitBreaker.State.OPEN, read.state)
        assertEquals(CircuitBreaker.State.CLOSED, write.state)
    }

    @Test
    fun `should create independent circuits for the write and the read`() {
        val config = BalanceCircuitBreakerConfig()

        assertNotSame(config.balanceWriteCircuitBreaker(properties), config.balanceReadCircuitBreaker(properties))
    }

    @ParameterizedTest
    @MethodSource("circuitBreakers")
    fun `should count transient failures and ignore permanent ones when the circuit is created`(circuitBreaker: CircuitBreaker) {
        val configuration = circuitBreaker.circuitBreakerConfig

        assertTrue(configuration.recordExceptionPredicate.test(TransientStorageException("throttled", RuntimeException())))
        assertFalse(configuration.recordExceptionPredicate.test(PermanentStorageException("denied", RuntimeException())))
        assertTrue(configuration.ignoreExceptionPredicate.test(PermanentStorageException("denied", RuntimeException())))
    }

    companion object {
        @JvmStatic
        fun circuitBreakers(): List<CircuitBreaker> {
            val config = BalanceCircuitBreakerConfig()
            val properties = CircuitBreakerProperties(FAILURE_RATE_THRESHOLD, SLIDING_WINDOW_SIZE, WAIT_DURATION_IN_OPEN_STATE, HALF_OPEN_CALLS)
            return listOf(config.balanceWriteCircuitBreaker(properties), config.balanceReadCircuitBreaker(properties))
        }
    }
}
