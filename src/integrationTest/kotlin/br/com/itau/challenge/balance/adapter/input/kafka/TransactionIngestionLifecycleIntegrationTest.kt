/*
 * L43 TransactionIngestionLifecycleIntegrationTest: cada teste sobe e fecha a sua aplicação com argumentos de linha
 *     de comando: propriedades do builder perdem para o `application.yaml`, e o contexto em cache do Spring não pode
 *     ser fechado pelo teste. O segundo membro do grupo roda fora do Spring para forçar o rebalance.
 *
 * Spec: Graceful shutdown; Rebalance sem perda; Dependência indisponível pausa as partições em vez de descartar
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.Application
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SLOW_SAVE_MILLIS = 1500L
private const val PAUSE_DURATION = "30s"
private const val REBALANCE_EVENTS = 40
private const val EVENTS_BEFORE_REBALANCE = 10
private const val PARTITIONS = 2
private val POLL_TIMEOUT: Duration = Duration.ofMillis(200)
private val STAYS_PAUSED_WINDOW: Duration = Duration.ofSeconds(3)
private val TRANSACTION_ID_PATTERN = Regex("\"id\": \"([0-9a-f-]{36})\"")

class TransactionIngestionLifecycleIntegrationTest {

    private val kafkaTemplate get() = application.getBean("kafkaTemplate") as KafkaTemplate<String, String>

    private lateinit var application: ConfigurableApplicationContext

    private val useCase get() = application.getBean(ScriptedProcessTransactionUseCase::class.java)

    private val registry get() = application.getBean(KafkaListenerEndpointRegistry::class.java)

    private val container get() = checkNotNull(registry.getListenerContainer(INGESTION_LISTENER_ID))

    @BeforeEach
    fun startApplication() {
        application =
            SpringApplicationBuilder(Application::class.java, ScriptedUseCaseConfiguration::class.java)
                .web(WebApplicationType.NONE)
                .run(
                    "--ingestion.topic-name=${topics.main}",
                    "--ingestion.dlt-topic-name=${topics.dlt}",
                    "--ingestion.consumer-group-id=${topics.groupId}",
                    "--ingestion.pause-duration=$PAUSE_DURATION",
                )
    }

    @AfterEach
    fun stopApplication() = application.close()

    private fun newTransactionId() = UUID.randomUUID().toString()

    private fun committedPast(offset: Long, partition: Int = 0) =
        (committedOffset(topics.groupId, TopicPartition(topics.main, partition)) ?: 0L) > offset

    @Test
    fun `should finish the record in progress and acknowledge it when the application is stopped`() {
        val transactionId = newTransactionId()
        val finished = AtomicBoolean(false)
        useCase.outcome = {
            Thread.sleep(SLOW_SAVE_MILLIS)
            finished.set(true)
            SnapshotSaveResult.Applied
        }
        val offset = kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = transactionId))
        await().atMost(AWAIT_TIMEOUT).until { useCase.timesReceived(transactionId) == 1 }

        application.close()

        assertTrue(finished.get())
        assertTrue(committedPast(offset))
    }

    @Test
    fun `should leave the consumer group when the application is stopped`() {
        val offset = kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = newTransactionId()))
        await().atMost(AWAIT_TIMEOUT).until { committedPast(offset) }

        application.close()

        await().atMost(AWAIT_TIMEOUT).until { groupMembers().isEmpty() }
    }

    @Test
    fun `should cancel the scheduled resume when the application is stopped during a pause`() {
        useCase.outcome = { throw StorageUnavailableException("circuit open", RuntimeException()) }
        kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = newTransactionId()))
        await().atMost(AWAIT_TIMEOUT).until { container.isContainerPaused }

        val scheduler = application.getBean(ThreadPoolTaskScheduler::class.java)

        application.close()

        assertTrue(scheduler.scheduledThreadPoolExecutor.isShutdown)
    }

    @Test
    fun `should keep the partitions paused when a rebalance happens during the pause`() {
        useCase.outcome = { throw StorageUnavailableException("circuit open", RuntimeException()) }
        kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = newTransactionId()))
        await().atMost(AWAIT_TIMEOUT).until { container.isContainerPaused }
        val receivedBeforeRebalance = useCase.received.size

        RecordingConsumer(topics).use { otherMember ->
            await().atMost(AWAIT_TIMEOUT).until { otherMember.hasPartitions() }
            val idsOfNewEvents = (0 until PARTITIONS).map { newTransactionId() }
            idsOfNewEvents.forEachIndexed { partition, id -> kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = id), partition) }

            await().during(STAYS_PAUSED_WINDOW).atMost(AWAIT_TIMEOUT).until { useCase.received.size == receivedBeforeRebalance }
        }
    }

    @Test
    fun `should process every event when a second member joins the group during the consumption`() {
        val transactionIds = List(REBALANCE_EVENTS) { newTransactionId() }
        transactionIds.take(EVENTS_BEFORE_REBALANCE).forEachIndexed { index, id ->
            kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = id), index % PARTITIONS)
        }

        RecordingConsumer(topics).use { otherMember ->
            transactionIds.drop(EVENTS_BEFORE_REBALANCE).forEachIndexed { index, id ->
                kafkaTemplate.publish(topics.main, transactionEventJson(transactionId = id), index % PARTITIONS)
            }

            await().atMost(AWAIT_TIMEOUT).until {
                val processedIds = useCase.received.map { it.transactionId.value.toString() } + otherMember.transactionIds()
                processedIds.containsAll(transactionIds)
            }
        }
        assertTrue(deadLetteredPayloads(topics).isEmpty())
    }

    private fun groupMembers() =
        adminClient().use { admin ->
            admin.describeConsumerGroups(listOf(topics.groupId)).all().get().getValue(topics.groupId).members()
        }

    private class RecordingConsumer(topics: IngestionTopics) : AutoCloseable {
        private val consumer = KafkaConsumer<String, String>(consumerProperties(topics.groupId))
        private val processedPayloads = CopyOnWriteArrayList<String>()
        private val running = AtomicBoolean(true)
        private val partitionsAssigned = AtomicBoolean(false)
        private val pollingThread =
            thread {
                consumer.subscribe(listOf(topics.main))
                while (running.get()) {
                    consumer.poll(POLL_TIMEOUT).forEach { processedPayloads.add(it.value()) }
                    partitionsAssigned.set(consumer.assignment().isNotEmpty())
                    consumer.commitSync()
                }
                consumer.close()
            }

        fun hasPartitions(): Boolean = partitionsAssigned.get()

        fun transactionIds(): List<String> = processedPayloads.mapNotNull { TRANSACTION_ID_PATTERN.find(it)?.groupValues?.get(1) }

        override fun close() {
            running.set(false)
            pollingThread.join()
        }
    }

    companion object {
        private val topics = createIngestionTopics(partitions = PARTITIONS)
    }
}
