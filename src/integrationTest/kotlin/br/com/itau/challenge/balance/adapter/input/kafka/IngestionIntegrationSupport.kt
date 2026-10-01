/*
 * L51-L58 createIngestionTopics: cria tópicos únicos por classe de teste, porque a criação automática está desligada
 *     e o estado de uma classe não deve vazar para outra.
 * L72-L83 readAll: lê o tópico inteiro com um consumer próprio, sem grupo compartilhado, para conferir a DLT sem
 *     interferir no consumo da aplicação.
 * L90-L91 KafkaTemplate.publish: publica sem chave, como o produtor do kit, e devolve o offset para o teste esperar o
 *     commit passar dele.
 * L97 ScriptedProcessTransactionUseCase: dublê que honra o contrato do port e deixa o teste roteirizar a falha por
 *     registro, sem depender de matchers do Mockito com tipos não anuláveis do Kotlin.
 * L117 ScriptedUseCaseConfiguration: substitui o caso de uso real pelo dublê com `@Primary`, só nos testes que
 *     importam esta configuração.
 *
 * Spec: n/a (add-transaction-ingestion design D2)
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.model.ProcessedTransaction
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.input.ProcessTransactionUseCase
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

internal const val BOOTSTRAP_SERVERS = "localhost:19092"
internal const val DLT_EXCEPTION_CAUSE_FQCN_HEADER = "kafka_dlt-exception-cause-fqcn"
internal const val DLT_EXCEPTION_MESSAGE_HEADER = "kafka_dlt-exception-message"
internal const val DLT_ORIGINAL_TOPIC_HEADER = "kafka_dlt-original-topic"
internal const val DLT_ORIGINAL_PARTITION_HEADER = "kafka_dlt-original-partition"
internal const val DLT_ORIGINAL_OFFSET_HEADER = "kafka_dlt-original-offset"
internal val AWAIT_TIMEOUT: Duration = Duration.ofSeconds(20)

private const val TOPIC_SUFFIX_LENGTH = 8
private const val REPLICATION_FACTOR: Short = 1
private val POLL_TIMEOUT: Duration = Duration.ofMillis(200)

internal data class IngestionTopics(val main: String, val dlt: String, val groupId: String)

internal fun createIngestionTopics(partitions: Int): IngestionTopics {
    val suffix = UUID.randomUUID().toString().take(TOPIC_SUFFIX_LENGTH)
    val topics = IngestionTopics("it-transactions-$suffix", "it-transactions-$suffix.DLT", "it-group-$suffix")
    adminClient().use { admin ->
        admin.createTopics(listOf(NewTopic(topics.main, partitions, REPLICATION_FACTOR), NewTopic(topics.dlt, partitions, REPLICATION_FACTOR))).all().get()
    }
    return topics
}

internal fun adminClient(): AdminClient = AdminClient.create(mapOf("bootstrap.servers" to BOOTSTRAP_SERVERS))

internal fun consumerProperties(groupId: String): Map<String, Any> =
    mapOf(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to BOOTSTRAP_SERVERS,
        ConsumerConfig.GROUP_ID_CONFIG to groupId,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
    )

internal fun readAll(topic: String): List<ConsumerRecord<String, String>> =
    KafkaConsumer<String, String>(consumerProperties("it-reader-${UUID.randomUUID()}")).use { consumer ->
        val partitions = consumer.partitionsFor(topic).map { TopicPartition(topic, it.partition()) }
        consumer.assign(partitions)
        consumer.seekToBeginning(partitions)
        val endOffsets = consumer.endOffsets(partitions)
        val records = mutableListOf<ConsumerRecord<String, String>>()
        while (partitions.any { consumer.position(it) < endOffsets.getValue(it) }) {
            consumer.poll(POLL_TIMEOUT).forEach { records.add(it) }
        }
        records
    }

internal fun committedOffset(groupId: String, partition: TopicPartition): Long? =
    adminClient().use { admin ->
        admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get()[partition]?.offset()
    }

internal fun KafkaTemplate<String, String>.publish(topic: String, payload: String, partition: Int = 0): Long =
    send(ProducerRecord<String, String>(topic, partition, null, payload)).get().recordMetadata.offset()

internal fun deadLetteredPayloads(topics: IngestionTopics): List<String> = readAll(topics.dlt).map { it.value() }

internal fun ConsumerRecord<String, String>.header(name: String): String = String(headers().lastHeader(name).value())

internal class ScriptedProcessTransactionUseCase : ProcessTransactionUseCase {
    val received = CopyOnWriteArrayList<ProcessedTransaction>()

    @Volatile
    var outcome: (ProcessedTransaction) -> SnapshotSaveResult = { SnapshotSaveResult.Applied }

    override fun processTransaction(transaction: ProcessedTransaction): SnapshotSaveResult {
        received.add(transaction)
        return outcome(transaction)
    }

    fun timesReceived(transactionId: String): Int = received.count { it.transactionId.value.toString() == transactionId }

    fun reset() {
        received.clear()
        outcome = { SnapshotSaveResult.Applied }
    }
}

@TestConfiguration
internal class ScriptedUseCaseConfiguration {
    @Bean
    @Primary
    fun scriptedProcessTransactionUseCase(): ScriptedProcessTransactionUseCase = ScriptedProcessTransactionUseCase()
}
