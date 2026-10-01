/*
 * L40 IngestionMetricsIntegrationTest: sobe a aplicação real contra o Redpanda e o DynamoDB Local, com o caso de uso
 *     real, para provar os quatro desfechos no contador. Cada teste compara com o valor anterior, porque o contador
 *     é compartilhado pelo contexto.
 * L131-L134 listenerTimers: restringe a amostra ao tópico e ao listener de transações deste contexto, para
 *     tráfego do hello não satisfazer a verificação de latência.
 * L138-L140 failedListenerTimerCount: a falha precisa ter uma série de erro própria, sem aproveitar amostras
 *     bem-sucedidas de outro teste.
 *
 * Spec: Eventos contados por resultado; Latência de processamento por registro; Consumer lag exposto
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.port.output.BalanceProvider
import io.micrometer.core.instrument.MeterRegistry
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PROCESSED_COUNTER = "balance.transactions.processed"
private const val LISTENER_TIMER = "spring.kafka.listener"
private const val OBSERVATION_ERROR_TAG = "error"
private const val NEWER_TIMESTAMP_MICROS = EVENT_TIMESTAMP_MICROS + 1_000
private const val OLDER_TIMESTAMP_MICROS = EVENT_TIMESTAMP_MICROS - 1_000

@SpringBootTest
@AutoConfigureMetrics
@AutoConfigureTracing
class IngestionMetricsIntegrationTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var balanceProvider: BalanceProvider

    private fun count(result: String): Double = meterRegistry.counter(PROCESSED_COUNTER, "result", result).count()

    private fun awaitIncrease(
        result: String,
        before: Double,
    ) = await().atMost(AWAIT_TIMEOUT).until { count(result) > before }

    private fun newAccount() = UUID.randomUUID().toString()

    @Test
    fun `should count applied when a new event is ingested`() {
        val before = count("applied")

        kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = UUID.randomUUID().toString(), accountId = newAccount()))

        awaitIncrease("applied", before)
        assertEquals(before + 1, count("applied"))
    }

    @Test
    fun `should count duplicate when the same event is ingested twice`() {
        val payload = transactionEventJson(transactionId = UUID.randomUUID().toString(), accountId = newAccount())
        kafkaTemplate.publish(topics.main, payload)
        val before = count("duplicate")

        kafkaTemplate.publish(topics.main, payload)

        awaitIncrease("duplicate", before)
        assertEquals(before + 1, count("duplicate"))
    }

    @Test
    fun `should count stale ignored when an older event arrives after a newer one`() {
        val account = newAccount()
        kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = UUID.randomUUID().toString(), accountId = account, timestampMicros = NEWER_TIMESTAMP_MICROS))
        await().atMost(AWAIT_TIMEOUT).until { balanceProvider.findByAccountId(AccountId(UUID.fromString(account))) != null }
        val before = count("stale_ignored")

        kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = UUID.randomUUID().toString(), accountId = account, timestampMicros = OLDER_TIMESTAMP_MICROS))

        awaitIncrease("stale_ignored", before)
        assertEquals(before + 1, count("stale_ignored"))
    }

    @Test
    fun `should count dlq when a malformed event is dead lettered`() {
        val before = count("dlq")

        kafkaTemplate.publish(topics.main, "not json ${UUID.randomUUID()}")

        awaitIncrease("dlq", before)
        assertEquals(before + 1, count("dlq"))
    }

    @Test
    fun `should record the processing time of the listener when a record is processed`() {
        val timerCountBefore = listenerTimerCount()

        kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = UUID.randomUUID().toString(), accountId = newAccount()))

        await().atMost(AWAIT_TIMEOUT).until { listenerTimerCount() > timerCountBefore }
    }

    @Test
    fun `should record an error processing time when the listener rejects a malformed record`() {
        val timerCountBefore = failedListenerTimerCount()

        kafkaTemplate.publish(topics.main, "not json ${UUID.randomUUID()}")

        await().atMost(AWAIT_TIMEOUT).until { failedListenerTimerCount() > timerCountBefore }
    }

    @Test
    fun `should expose the max consumer lag of the transactions listener when the listener is consuming`() {
        kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = UUID.randomUUID().toString(), accountId = newAccount()))

        await().atMost(AWAIT_TIMEOUT).until { lagMeters().isNotEmpty() }
        assertTrue(lagMeters().any { meter -> meter.id.getTag("client.id").orEmpty().contains(topics.groupId) })
    }

    private fun listenerTimers() = meterRegistry.find(LISTENER_TIMER)
        .tag("messaging.source.name", topics.main)
        .timers()
        .filter { it.id.getTag("spring.kafka.listener.id").orEmpty().startsWith(INGESTION_LISTENER_ID) }

    private fun listenerTimerCount(): Long = listenerTimers().sumOf { it.count() }

    private fun failedListenerTimerCount(): Long = listenerTimers()
        .filter { it.id.getTag(OBSERVATION_ERROR_TAG).orEmpty() !in setOf("", "none") }
        .sumOf { it.count() }

    private fun lagMeters() = meterRegistry.find("kafka.consumer.fetch.manager.records.lag.max").meters()

    companion object {
        private val topics = createIngestionTopics(partitions = 1)

        @JvmStatic
        @DynamicPropertySource
        fun ingestionProperties(registry: DynamicPropertyRegistry) {
            registry.add("ingestion.topic-name") { topics.main }
            registry.add("ingestion.dlt-topic-name") { topics.dlt }
            registry.add("ingestion.consumer-group-id") { topics.groupId }
        }
    }
}
