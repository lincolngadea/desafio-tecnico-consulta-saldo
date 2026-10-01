/*
 * L32 TransactionIngestionEndToEndIntegrationTest: usa o caso de uso e o DynamoDB reais: para o consumer, uma
 *     reentrega depois de uma queda entre a gravação e o commit é indistinguível de uma duplicata, então publicar o
 *     mesmo evento de novo prova esse cenário ponta a ponta.
 *
 * Spec: Offset confirmado manualmente só depois da persistência
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.port.output.BalanceProvider
import org.apache.kafka.common.TopicPartition
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NEWER_TIMESTAMP_MICROS = EVENT_TIMESTAMP_MICROS + 1_000
private const val OLDER_TIMESTAMP_MICROS = EVENT_TIMESTAMP_MICROS - 1_000
private const val NEWER_BALANCE = """{"amount": 500.00, "currency": "BRL"}"""
private const val OLDER_BALANCE = """{"amount": 1.00, "currency": "BRL"}"""

@SpringBootTest
class TransactionIngestionEndToEndIntegrationTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var balanceProvider: BalanceProvider

    private fun awaitCommittedPast(offset: Long) {
        await().atMost(AWAIT_TIMEOUT).until { (committedOffset(topics.groupId, TopicPartition(topics.main, 0)) ?: 0L) > offset }
    }

    private fun snapshotOf(accountId: UUID): BalanceSnapshot? = balanceProvider.findByAccountId(AccountId(accountId))

    @Test
    fun `should keep the snapshot unchanged when the same event is delivered again`() {
        val accountId = UUID.randomUUID()
        val payload = transactionEventJson(transactionId = UUID.randomUUID().toString(), accountId = accountId.toString())
        kafkaTemplate.publish(topics.main, payload)
        await().atMost(AWAIT_TIMEOUT).until { snapshotOf(accountId) != null }
        val snapshotAfterFirstDelivery = snapshotOf(accountId)

        val secondDeliveryOffset = kafkaTemplate.publish(topics.main, payload)

        awaitCommittedPast(secondDeliveryOffset)
        assertEquals(snapshotAfterFirstDelivery, snapshotOf(accountId))
        assertTrue(deadLetteredPayloads(topics).none { it == payload })
    }

    @Test
    fun `should keep the newer snapshot when an older event arrives after it`() {
        val accountId = UUID.randomUUID().toString()
        val newerTransactionId = UUID.randomUUID().toString()
        kafkaTemplate.publish(
            topics.main,
            transactionEventJson(transactionId = newerTransactionId, accountId = accountId, timestampMicros = NEWER_TIMESTAMP_MICROS, balanceJson = NEWER_BALANCE),
        )
        await().atMost(AWAIT_TIMEOUT).until { snapshotOf(UUID.fromString(accountId)) != null }

        val olderEventOffset =
            kafkaTemplate.publish(
                topics.main,
                transactionEventJson(transactionId = UUID.randomUUID().toString(), accountId = accountId, timestampMicros = OLDER_TIMESTAMP_MICROS, balanceJson = OLDER_BALANCE),
            )

        awaitCommittedPast(olderEventOffset)
        assertEquals(newerTransactionId, snapshotOf(UUID.fromString(accountId))?.version?.transactionId?.value.toString())
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
