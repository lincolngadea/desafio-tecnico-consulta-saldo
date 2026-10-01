/*
 * L17 CircuitBreakerPropertiesTest: lê o `application.yaml` de verdade, para os padrões documentados e os nomes das
 *     variáveis de ambiente serem os que o código usa.
 *
 * Spec: Parâmetros do circuito configuráveis por variável de ambiente
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.resilience

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration
import kotlin.test.assertEquals

class CircuitBreakerPropertiesTest {

    @EnableConfigurationProperties(CircuitBreakerProperties::class)
    class PropertiesConfiguration

    private val contextRunner =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration::class.java)

    @Test
    fun `should use the documented defaults when no environment variable is set`() {
        contextRunner.run { context ->
            val properties = context.getBean(CircuitBreakerProperties::class.java)

            assertEquals(50f, properties.failureRateThreshold)
            assertEquals(10, properties.slidingWindowSize)
            assertEquals(Duration.ofSeconds(30), properties.waitDurationInOpenState)
            assertEquals(3, properties.halfOpenCalls)
        }
    }

    @Test
    fun `should use the values from the environment when the circuit breaker variables are set`() {
        contextRunner
            .withSystemProperties(
                "BALANCE_CB_FAILURE_RATE_THRESHOLD=80",
                "BALANCE_CB_SLIDING_WINDOW_SIZE=20",
                "BALANCE_CB_WAIT_DURATION_OPEN=5s",
                "BALANCE_CB_HALF_OPEN_CALLS=2",
            ).run { context ->
                val properties = context.getBean(CircuitBreakerProperties::class.java)

                assertEquals(80f, properties.failureRateThreshold)
                assertEquals(20, properties.slidingWindowSize)
                assertEquals(Duration.ofSeconds(5), properties.waitDurationInOpenState)
                assertEquals(2, properties.halfOpenCalls)
            }
    }
}
