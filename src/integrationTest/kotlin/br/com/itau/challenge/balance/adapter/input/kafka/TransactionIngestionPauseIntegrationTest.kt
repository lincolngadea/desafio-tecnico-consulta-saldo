/*
 * L35 TransactionIngestionPauseIntegrationTest: prova no broker que a pausa não perde nem confirma o registro: o
 *     offset confirmado não passa do registro pausado e a reentrega acontece na retomada.
 *
 * Spec: Dependência indisponível pausa as partições em vez de descartar
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import org.apache.kafka.common.TopicPartition
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MAX_RETRIES = 2
private const val PAUSE_DURATION = "2s"
private const val OUTAGE_EVENTS = 10

@SpringBootTest
@Import(ScriptedUseCaseConfiguration::class)
class TransactionIngestionPauseIntegrationTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var useCase: ScriptedProcessTransactionUseCase

    @Autowired
    private lateinit var registry: KafkaListenerEndpointRegistry

    private val container get() = checkNotNull(registry.getListenerContainer(INGESTION_LISTENER_ID))

    @BeforeEach
    fun resetUseCase() = useCase.reset()

    @AfterEach
    fun restoreStorage() {
        useCase.outcome = { SnapshotSaveResult.Applied }
        await().atMost(AWAIT_TIMEOUT).until { !container.isContainerPaused }
    }

    private fun newTransactionId() = UUID.randomUUID().toString()

    private fun awaitPaused() = await().atMost(AWAIT_TIMEOUT).until { container.isContainerPaused }

    private fun committedPast(offset: Long) = (committedOffset(topics.groupId, TopicPartition(topics.main, 0)) ?: 0L) > offset

    private fun failsOnTheFirstAttemptOf(transactionId: String, failure: () -> Throwable) {
        useCase.outcome = { transaction ->
            if (transaction.transactionId.value.toString() == transactionId && useCase.timesReceived(transactionId) == 1) throw failure()
            SnapshotSaveResult.Applied
        }
    }

    @Test
    fun `should pause without dead lettering or acknowledging when the circuit is open`() {
        val transactionId = newTransactionId()
        val payload = transactionEventJson(transactionId = transactionId)
        useCase.outcome = { throw StorageUnavailableException("circuit open", RuntimeException()) }

        val offset = kafkaTemplate.publish(topics.main, payload)

        awaitPaused()
        assertTrue(deadLetteredPayloads(topics).none { it == payload })
        assertTrue(!committedPast(offset))
    }

    @Test
    fun `should pause after the retries are exhausted when the storage keeps failing transiently`() {
        val transactionId = newTransactionId()
        useCase.outcome = { throw TransientStorageException("throttled", RuntimeException()) }

        kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = transactionId))

        awaitPaused()
        assertTrue(useCase.timesReceived(transactionId) >= 1 + MAX_RETRIES)
        assertTrue(deadLetteredPayloads(topics).none { it.contains(transactionId) })
    }

    @Test
    fun `should deliver the same record again when the pause ends`() {
        val transactionId = newTransactionId()
        failsOnTheFirstAttemptOf(transactionId) { StorageUnavailableException("circuit open", RuntimeException()) }

        val offset = kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = transactionId))

        await().atMost(AWAIT_TIMEOUT).until { committedPast(offset) }
        assertEquals(2, useCase.timesReceived(transactionId))
    }

    @Test
    fun `should consume the next records normally when the probe record succeeds`() {
        val probeId = newTransactionId()
        val nextId = newTransactionId()
        failsOnTheFirstAttemptOf(probeId) { StorageUnavailableException("circuit open", RuntimeException()) }
        kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = probeId))
        await().atMost(AWAIT_TIMEOUT).until { useCase.timesReceived(probeId) == 2 }

        val offset = kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = nextId))

        await().atMost(AWAIT_TIMEOUT).until { committedPast(offset) }
        assertEquals(1, useCase.timesReceived(nextId))
    }

    @Test
    fun `should pause again when the probe record fails`() {
        val transactionId = newTransactionId()
        useCase.outcome = { throw StorageUnavailableException("circuit open", RuntimeException()) }
        val offset = kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = transactionId))

        await().atMost(AWAIT_TIMEOUT).until { useCase.timesReceived(transactionId) >= 2 }

        awaitPaused()
        assertTrue(!committedPast(offset))
    }

    @Test
    fun `should save every event when the storage is unavailable during the consumption and then recovers`() {
        val transactionIds = List(OUTAGE_EVENTS) { newTransactionId() }
        useCase.outcome = { throw StorageUnavailableException("circuit open", RuntimeException()) }

        val lastOffset = transactionIds.map { kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = it)) }.last()
        awaitPaused()
        useCase.outcome = { SnapshotSaveResult.Applied }

        await().atMost(AWAIT_TIMEOUT).until { committedPast(lastOffset) }
        assertTrue(transactionIds.all { useCase.timesReceived(it) >= 1 })
        assertTrue(deadLetteredPayloads(topics).none { payload -> transactionIds.any { payload.contains(it) } })
    }

    companion object {
        private val topics = createIngestionTopics(partitions = 1)

        @JvmStatic
        @DynamicPropertySource
        fun ingestionProperties(registry: DynamicPropertyRegistry) {
            registry.add("ingestion.topic-name") { topics.main }
            registry.add("ingestion.dlt-topic-name") { topics.dlt }
            registry.add("ingestion.consumer-group-id") { topics.groupId }
            registry.add("ingestion.max-retries") { MAX_RETRIES }
            registry.add("ingestion.backoff.initial") { "50ms" }
            registry.add("ingestion.backoff.max") { "200ms" }
            registry.add("ingestion.backoff.jitter") { "10ms" }
            registry.add("ingestion.pause-duration") { PAUSE_DURATION }
        }
    }
}
