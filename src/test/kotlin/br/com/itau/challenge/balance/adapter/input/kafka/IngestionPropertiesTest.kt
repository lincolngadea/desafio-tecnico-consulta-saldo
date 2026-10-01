/*
 * L25 IngestionPropertiesTest: lê o `application.yaml` de verdade, para os padrões documentados e os nomes das
 *     variáveis de ambiente serem os que o código usa.
 * L61-L72 should block a record: o pior caso de bloqueio de um registro precisa caber no `max.poll.interval.ms`
 *     padrão do Kafka (5 min), ou o broker tira o consumer do grupo.
 *
 * Spec: Parâmetros do consumer configuráveis por variável de ambiente
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DYNAMODB_API_CALL_TIMEOUT_KEY = "dynamodb.timeouts.api-call"
private const val FIRST_ATTEMPT = 1
private val KAFKA_DEFAULT_MAX_POLL_INTERVAL: Duration = Duration.ofMinutes(5)

class IngestionPropertiesTest {

    @EnableConfigurationProperties(IngestionProperties::class)
    class PropertiesConfiguration

    private val contextRunner =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration::class.java)

    @Test
    fun `should use the documented defaults when no environment variable is set`() {
        contextRunner.run { context ->
            val properties = context.getBean(IngestionProperties::class.java)

            assertEquals("transacoes-financeiras-processadas", properties.topicName)
            assertEquals("transacoes-financeiras-processadas.DLT", properties.dltTopicName)
            assertEquals("balance-transaction-ingestion", properties.consumerGroupId)
            assertEquals(1, properties.concurrency)
            assertEquals(3, properties.maxRetries)
            assertEquals(Duration.ofMillis(200), properties.backoff.initial)
            assertEquals(2.0, properties.backoff.multiplier)
            assertEquals(Duration.ofSeconds(2), properties.backoff.max)
            assertEquals(Duration.ofMillis(100), properties.backoff.jitter)
            assertEquals(Duration.ofSeconds(30), properties.pauseDuration)
        }
    }

    @Test
    fun `should use the retry limit from the environment when INGESTION_MAX_RETRIES is set`() {
        contextRunner.withSystemProperties("INGESTION_MAX_RETRIES=5").run { context ->
            assertEquals(5, context.getBean(IngestionProperties::class.java).maxRetries)
        }
    }

    @Test
    fun `should block a record for less than the max poll interval when the defaults are in use`() {
        contextRunner.run { context ->
            val properties = context.getBean(IngestionProperties::class.java)
            val apiCallTimeout = Binder.get(context.environment).bind(DYNAMODB_API_CALL_TIMEOUT_KEY, Duration::class.java).get()
            val attempts = FIRST_ATTEMPT + properties.maxRetries
            val longestBackoff = properties.backoff.max + properties.backoff.jitter

            val worstCaseBlockedTime = apiCallTimeout.multipliedBy(attempts.toLong()) + longestBackoff.multipliedBy(properties.maxRetries.toLong())

            assertTrue(worstCaseBlockedTime < KAFKA_DEFAULT_MAX_POLL_INTERVAL)
        }
    }
}
