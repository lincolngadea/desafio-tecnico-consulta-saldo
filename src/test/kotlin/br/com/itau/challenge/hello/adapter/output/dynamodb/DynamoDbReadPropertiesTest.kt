/*
 * L20 DynamoDbReadPropertiesTest: lê o `application.yaml` de verdade, e a configuração incoerente é provada pela
 *     falha de subida do contexto, que é o que o operador vê.
 *
 * Spec: Cliente de leitura com timeouts curtos e configuráveis; Leitura repete no máximo uma vez; O pior caso da
 *     leitura cabe no orçamento de latência
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.hello.adapter.output.dynamodb

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DynamoDbReadPropertiesTest {

    @EnableConfigurationProperties(DynamoDbProperties::class)
    class PropertiesConfiguration

    private val contextRunner =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration::class.java)

    @Test
    fun `should use the documented read defaults when no environment variable is set`() {
        contextRunner.run { context ->
            val read = context.getBean(DynamoDbProperties::class.java).read

            assertEquals(Duration.ofMillis(200), read.timeouts.connection)
            assertEquals(Duration.ofMillis(300), read.timeouts.socket)
            assertEquals(Duration.ofMillis(300), read.timeouts.apiCallAttempt)
            assertEquals(Duration.ofMillis(800), read.timeouts.apiCall)
            assertEquals(2, read.retry.maxAttempts)
        }
    }

    @Test
    fun `should keep the write defaults untouched when the read profile exists`() {
        contextRunner.run { context ->
            val properties = context.getBean(DynamoDbProperties::class.java)

            assertEquals(Duration.ofMillis(500), properties.timeouts.connection)
            assertEquals(Duration.ofSeconds(3), properties.timeouts.apiCall)
            assertEquals(3, properties.retry.maxAttempts)
        }
    }

    @Test
    fun `should use the read values from the environment when the read variables are set`() {
        contextRunner
            .withSystemProperties(
                "DYNAMODB_READ_CONNECTION_TIMEOUT=100ms",
                "DYNAMODB_READ_SOCKET_TIMEOUT=150ms",
                "DYNAMODB_READ_API_CALL_ATTEMPT_TIMEOUT=200ms",
                "DYNAMODB_READ_API_CALL_TIMEOUT=500ms",
                "DYNAMODB_READ_MAX_ATTEMPTS=1",
            ).run { context ->
                val read = context.getBean(DynamoDbProperties::class.java).read

                assertEquals(Duration.ofMillis(100), read.timeouts.connection)
                assertEquals(Duration.ofMillis(150), read.timeouts.socket)
                assertEquals(Duration.ofMillis(200), read.timeouts.apiCallAttempt)
                assertEquals(Duration.ofMillis(500), read.timeouts.apiCall)
                assertEquals(1, read.retry.maxAttempts)
            }
    }

    @Test
    fun `should refuse to start when the read allows more than one retry`() {
        contextRunner.withSystemProperties("DYNAMODB_READ_MAX_ATTEMPTS=3").run { context ->
            val failure = assertNotNull(context.startupFailure)

            assertContains(generateSequence<Throwable>(failure) { it.cause }.joinToString { it.message.orEmpty() }, "1 retry")
        }
    }

    @Test
    fun `should refuse to start when the attempts do not fit in the total call timeout`() {
        contextRunner
            .withSystemProperties("DYNAMODB_READ_API_CALL_ATTEMPT_TIMEOUT=500ms", "DYNAMODB_READ_API_CALL_TIMEOUT=800ms")
            .run { context ->
                val failure = assertNotNull(context.startupFailure)

                assertContains(generateSequence<Throwable>(failure) { it.cause }.joinToString { it.message.orEmpty() }, "maxAttempts")
            }
    }
}
