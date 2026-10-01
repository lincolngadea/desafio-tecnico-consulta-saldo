/*
 * L18 TransactionTopicsIntegrationTest: exige os tópicos criados por `make kafka-topics-ingestion`, que usa o `make
 *     kafka-topic-create` do kit com 6 partições e já roda no `make integration-test`; sem eles o teste falha, o que
 *     mostra o ambiente fora do combinado.
 *
 * Spec: Tópicos com partições definidas e criados pelo comando do kit
 * Enunciado: Como começar → Criando o tópico Kafka
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private const val TRANSACTIONS_TOPIC = "transacoes-financeiras-processadas"
private const val TRANSACTIONS_DLT_TOPIC = "$TRANSACTIONS_TOPIC.DLT"
private const val EXPECTED_PARTITIONS = 6

class TransactionTopicsIntegrationTest {

    private fun partitionsOf(topic: String): Int =
        adminClient().use { admin -> admin.describeTopics(listOf(topic)).allTopicNames().get().getValue(topic).partitions().size }

    @Test
    fun `should have six partitions in the transactions topic when it is created by the kit command`() {
        assertEquals(EXPECTED_PARTITIONS, partitionsOf(TRANSACTIONS_TOPIC))
    }

    @Test
    fun `should have six partitions in the dead letter topic when it is created by the kit command`() {
        assertEquals(EXPECTED_PARTITIONS, partitionsOf(TRANSACTIONS_DLT_TOPIC))
    }
}
