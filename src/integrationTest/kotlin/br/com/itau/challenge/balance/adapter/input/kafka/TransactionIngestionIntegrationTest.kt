/*
 * L34 TransactionIngestionIntegrationTest: sobe a aplicação real contra o Redpanda, com o caso de uso roteirizado, e
 *     confere o commit do offset e a DLT pelo broker, e não por mocks.
 *
 * Spec: Offset confirmado manualmente só depois da persistência; Erro permanente vai para a DLT com o motivo; Erro
 *     transitório é tentado de novo com backoff exponencial e jitter
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import org.apache.kafka.common.TopicPartition
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MAX_RETRIES = 2
private const val ORIGIN_PARTITION = 0

@SpringBootTest
@Import(ScriptedUseCaseConfiguration::class)
class TransactionIngestionIntegrationTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var useCase: ScriptedProcessTransactionUseCase

    @BeforeEach
    fun resetUseCase() = useCase.reset()

    private fun newTransactionId() = UUID.randomUUID().toString()

    private fun awaitCommittedPast(offset: Long) {
        await().atMost(AWAIT_TIMEOUT).until { (committedOffset(topics.groupId, TopicPartition(topics.main, 0)) ?: 0L) > offset }
    }

    private fun awaitDeadLettered(payload: String) =
        await().atMost(AWAIT_TIMEOUT).until { deadLetteredPayloads(topics).contains(payload) }

    @Test
    fun `should acknowledge the offset after saving when a valid event is consumed`() {
        val transactionId = newTransactionId()

        val offset = kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = transactionId))

        awaitCommittedPast(offset)
        assertEquals(1, useCase.timesReceived(transactionId))
    }

    @Test
    fun `should dead letter a malformed json and acknowledge it without calling the use case`() {
        val payload = "this is not json ${UUID.randomUUID()}"

        val offset = kafkaTemplate.publish(topics.main, payload)

        awaitDeadLettered(payload)
        awaitCommittedPast(offset)
        assertTrue(useCase.received.isEmpty())
    }

    @Test
    fun `should dead letter an event with an invalid account id`() {
        val payload = transactionEventJson(transactionId = newTransactionId(), accountId = "not-a-uuid")

        val offset = kafkaTemplate.publish(topics.main, payload)

        awaitDeadLettered(payload)
        awaitCommittedPast(offset)
    }

    @Test
    fun `should dead letter an event without the balance`() {
        val payload = transactionEventJson(transactionId = newTransactionId(), balanceJson = "null")

        val offset = kafkaTemplate.publish(topics.main, payload)

        awaitDeadLettered(payload)
        awaitCommittedPast(offset)
    }

    @Test
    fun `should dead letter without retrying when the storage fails permanently`() {
        val transactionId = newTransactionId()
        val payload = transactionEventJson(transactionId = transactionId)
        useCase.outcome = { throw PermanentStorageException("access denied", RuntimeException()) }

        val offset = kafkaTemplate.publish(topics.main, payload)

        awaitDeadLettered(payload)
        awaitCommittedPast(offset)
        assertEquals(1, useCase.timesReceived(transactionId))
    }

    @Test
    fun `should keep the payload and the origin when a record is dead lettered`() {
        val payload = "preserved payload ${UUID.randomUUID()}"

        val offset = kafkaTemplate.publish(topics.main, payload)

        awaitDeadLettered(payload)
        val deadLetter = readAll(topics.dlt).single { it.value() == payload }
        assertEquals(MalformedTransactionEventException::class.java.name, deadLetter.header(DLT_EXCEPTION_CAUSE_FQCN_HEADER))
        assertTrue(deadLetter.header(DLT_EXCEPTION_MESSAGE_HEADER).isNotBlank())
        assertEquals(topics.main, deadLetter.header(DLT_ORIGINAL_TOPIC_HEADER))
        assertEquals(ORIGIN_PARTITION, ByteBuffer.wrap(deadLetter.headers().lastHeader(DLT_ORIGINAL_PARTITION_HEADER).value()).int)
        assertEquals(offset, ByteBuffer.wrap(deadLetter.headers().lastHeader(DLT_ORIGINAL_OFFSET_HEADER).value()).long)
    }

    @Test
    fun `should process the next event when the previous one is invalid`() {
        val validTransactionId = newTransactionId()

        kafkaTemplate.publish(topics.main, "invalid ${UUID.randomUUID()}")
        val offset = kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = validTransactionId))

        awaitCommittedPast(offset)
        assertEquals(1, useCase.timesReceived(validTransactionId))
    }

    @Test
    fun `should save and acknowledge when the second attempt succeeds after a transient failure`() {
        val transactionId = newTransactionId()
        useCase.outcome = { transaction ->
            if (useCase.timesReceived(transaction.transactionId.value.toString()) == 1) throw TransientStorageException("throttled", RuntimeException())
            SnapshotSaveResult.Applied
        }

        val offset = kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = transactionId))

        awaitCommittedPast(offset)
        assertEquals(2, useCase.timesReceived(transactionId))
        assertTrue(deadLetteredPayloads(topics).none { it.contains(transactionId) })
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
        }
    }
}
