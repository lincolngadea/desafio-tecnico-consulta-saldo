/*
 * L49 TraceIdCapturingUseCase: dublê que honra o contrato do port e guarda o `traceId` do MDC no instante em que o
 *     caso de uso roda, que é o que todo log do processamento usa; assim o teste prova a propagação sem depender de
 *     um log que o fluxo normal não escreve.
 * L69 IngestionObservabilityIntegrationTest: sobe a aplicação real contra o Redpanda, com o tracing ligado, para
 *     provar a correlação do Kafka: o `traceparent` do registro vira o `traceId` do processamento, a DLT o preserva
 *     e nenhum log leva o `owner` nem trecho do payload (um `owner` sentinela num evento malformado).
 *
 * Spec: traceId propagado do Kafka; Dados pessoais e payload nunca vão para o log
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.model.ProcessedTransaction
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.input.ProcessTransactionUseCase
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val INCOMING_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
private const val TRACEPARENT = "00-$INCOMING_TRACE_ID-00f067aa0ba902b7-01"
private const val SENTINEL_OWNER = "0badc0de-0bad-4c0d-8bad-c0de0badc0de"
private const val TRACEPARENT_HEADER = "traceparent"
private const val TRACE_ID_KEY = "traceId"
private const val TRUNCATED_CHARACTERS = 12

internal class TraceIdCapturingUseCase : ProcessTransactionUseCase {
    val traceIdByTransaction = ConcurrentHashMap<String, String?>()

    override fun processTransaction(transaction: ProcessedTransaction): SnapshotSaveResult {
        traceIdByTransaction[transaction.transactionId.value.toString()] = MDC.get(TRACE_ID_KEY)
        return SnapshotSaveResult.Applied
    }
}

@TestConfiguration
internal class TraceIdCapturingConfiguration {
    @Bean
    @Primary
    fun traceIdCapturingUseCase(): TraceIdCapturingUseCase = TraceIdCapturingUseCase()
}

@SpringBootTest
@AutoConfigureTracing
@Import(TraceIdCapturingConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
class IngestionObservabilityIntegrationTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var useCase: TraceIdCapturingUseCase

    private fun publishWithTraceparent(
        payload: String,
        traceparent: String?,
    ) {
        val record = ProducerRecord<String, String>(topics.main, 0, null, payload)
        traceparent?.let { record.headers().add(TRACEPARENT_HEADER, it.toByteArray()) }
        kafkaTemplate.send(record).get()
    }

    private fun traceIdSeenFor(transactionId: String): String? {
        await().atMost(AWAIT_TIMEOUT).until { useCase.traceIdByTransaction.containsKey(transactionId) }
        return useCase.traceIdByTransaction[transactionId]
    }

    @Test
    fun `should process the record under the trace id of its traceparent header`() {
        val transactionId = UUID.randomUUID().toString()

        publishWithTraceparent(transactionEventJson(transactionId = transactionId), TRACEPARENT)

        assertEquals(INCOMING_TRACE_ID, traceIdSeenFor(transactionId))
    }

    @Test
    fun `should process the record under a new 32 digit trace id when the header is missing`() {
        val transactionId = UUID.randomUUID().toString()

        publishWithTraceparent(transactionEventJson(transactionId = transactionId), null)

        assertTrue(Regex("[0-9a-f]{32}").matches(traceIdSeenFor(transactionId).orEmpty()))
    }

    @Test
    fun `should not carry the trace id of one record to the next when they are processed in sequence`() {
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()
        publishWithTraceparent(transactionEventJson(transactionId = first), TRACEPARENT)
        publishWithTraceparent(transactionEventJson(transactionId = second), null)

        assertEquals(INCOMING_TRACE_ID, traceIdSeenFor(first))
        assertNotEquals(INCOMING_TRACE_ID, traceIdSeenFor(second))
    }

    @Test
    fun `should keep the traceparent header in the dead letter record when the event is malformed`() {
        val payload = "not json ${UUID.randomUUID()}"

        publishWithTraceparent(payload, TRACEPARENT)

        await().atMost(AWAIT_TIMEOUT).until { readAll(topics.dlt).any { it.value() == payload } }
        val deadLettered = readAll(topics.dlt).single { it.value() == payload }
        assertEquals(TRACEPARENT, String(deadLettered.headers().lastHeader(TRACEPARENT_HEADER).value()))
    }

    @Test
    fun `should not write the owner or any part of the payload to the log when a malformed event is dead lettered`(output: CapturedOutput) {
        val payload = transactionEventJson().replace(EVENT_OWNER_ID, SENTINEL_OWNER).dropLast(TRUNCATED_CHARACTERS)

        publishWithTraceparent(payload, TRACEPARENT)

        await().atMost(AWAIT_TIMEOUT).until { readAll(topics.dlt).any { it.value() == payload } }
        assertNotNull(readAll(topics.dlt).firstOrNull { it.value() == payload })
        assertFalse(output.all.contains(SENTINEL_OWNER), "the owner reached the log")
        assertFalse(output.all.contains("\"currency\""), "a part of the payload reached the log")
    }

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
