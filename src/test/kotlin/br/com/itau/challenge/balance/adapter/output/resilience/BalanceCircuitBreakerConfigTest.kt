/*
 * L35 BalanceCircuitBreakerConfigTest: um parâmetro que não chega ao circuito real volta ao padrão da biblioteca sem
 *     ninguém perceber, então o teste confere o circuito criado pela fábrica de produção, como no
 *     `DynamoDbConfigTest`; também prova que os dois circuitos são independentes e aplicam a mesma classificação.
 * L147-L151 stateGauge: lê o estado do circuito pela métrica publicada, e não pelo objeto do circuito, para provar o
 *     que o Prometheus vai mostrar.
 * L161-L167 classificação compartilhada: parametrizado sobre os dois beans, para a regra de falha dos dois circuitos
 *     não divergir.
 *
 * Spec: Parâmetros do circuito configuráveis por variável de ambiente; Circuit breaker de leitura com a
 *     classificação de erros compartilhada; Estado do circuit breaker exposto
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.resilience

import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
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

    private val configuration = BalanceCircuitBreakerConfig().balanceWriteCircuitBreaker(properties, CircuitBreakerRegistry.ofDefaults()).circuitBreakerConfig

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
        val registry = CircuitBreakerRegistry.ofDefaults()
        val write = config.balanceWriteCircuitBreaker(properties, registry)
        val read = config.balanceReadCircuitBreaker(properties, registry)

        write.transitionToOpenState()

        assertEquals(CircuitBreaker.State.OPEN, write.state)
        assertEquals(CircuitBreaker.State.CLOSED, read.state)
    }

    @Test
    fun `should keep the write circuit closed when the read circuit opens`() {
        val config = BalanceCircuitBreakerConfig()
        val registry = CircuitBreakerRegistry.ofDefaults()
        val write = config.balanceWriteCircuitBreaker(properties, registry)
        val read = config.balanceReadCircuitBreaker(properties, registry)

        read.transitionToOpenState()

        assertEquals(CircuitBreaker.State.OPEN, read.state)
        assertEquals(CircuitBreaker.State.CLOSED, write.state)
    }

    @Test
    fun `should create independent circuits for the write and the read`() {
        val config = BalanceCircuitBreakerConfig()

        val registry = CircuitBreakerRegistry.ofDefaults()

        assertNotSame(config.balanceWriteCircuitBreaker(properties, registry), config.balanceReadCircuitBreaker(properties, registry))
    }

    @Test
    fun `should mark both circuits as closed in the state metric when they are created`() {
        val meterRegistry = metricsOfBothCircuits()

        assertEquals(1.0, stateGauge(meterRegistry, "balance-storage", "closed"))
        assertEquals(1.0, stateGauge(meterRegistry, "balance-storage-read", "closed"))
    }

    @Test
    fun `should mark only the read circuit as open in the state metric when the read circuit opens`() {
        val registry = CircuitBreakerRegistry.ofDefaults()
        val config = BalanceCircuitBreakerConfig()
        config.balanceWriteCircuitBreaker(properties, registry)
        val read = config.balanceReadCircuitBreaker(properties, registry)
        val meterRegistry = SimpleMeterRegistry().also { config.balanceCircuitBreakerMetrics(registry).bindTo(it) }

        read.transitionToOpenState()

        assertEquals(1.0, stateGauge(meterRegistry, "balance-storage-read", "open"))
        assertEquals(1.0, stateGauge(meterRegistry, "balance-storage", "closed"))
    }

    @Test
    fun `should count successful and failed calls of the read circuit when the provider answers and fails`() {
        val registry = CircuitBreakerRegistry.ofDefaults()
        val config = BalanceCircuitBreakerConfig()
        val read = config.balanceReadCircuitBreaker(properties, registry)
        val meterRegistry = SimpleMeterRegistry().also { config.balanceCircuitBreakerMetrics(registry).bindTo(it) }

        read.executeSupplier { "ok" }
        runCatching { read.executeSupplier { throw TransientStorageException("down", RuntimeException()) } }

        assertEquals(1L, callsTimer(meterRegistry, "balance-storage-read", "successful").count())
        assertEquals(1L, callsTimer(meterRegistry, "balance-storage-read", "failed").count())
    }

    private fun metricsOfBothCircuits(): SimpleMeterRegistry {
        val registry = CircuitBreakerRegistry.ofDefaults()
        val config = BalanceCircuitBreakerConfig()
        config.balanceWriteCircuitBreaker(properties, registry)
        config.balanceReadCircuitBreaker(properties, registry)
        return SimpleMeterRegistry().also { config.balanceCircuitBreakerMetrics(registry).bindTo(it) }
    }

    private fun stateGauge(
        meterRegistry: SimpleMeterRegistry,
        circuit: String,
        state: String,
    ): Double = meterRegistry.get("resilience4j.circuitbreaker.state").tag("name", circuit).tag("state", state).gauge().value()

    private fun callsTimer(
        meterRegistry: SimpleMeterRegistry,
        circuit: String,
        kind: String,
    ) = meterRegistry.get("resilience4j.circuitbreaker.calls").tag("name", circuit).tag("kind", kind).timer()

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
            val registry = CircuitBreakerRegistry.ofDefaults()
            return listOf(config.balanceWriteCircuitBreaker(properties, registry), config.balanceReadCircuitBreaker(properties, registry))
        }
    }
}
