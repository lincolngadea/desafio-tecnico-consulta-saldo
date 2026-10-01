/*
 * L43 BalanceQueryEndToEndIntegrationTest: percorre o caminho inteiro com infraestrutura real: evento no Redpanda,
 *     ingestão, gravação no DynamoDB Local e consulta por HTTP em porta aleatória. Cada teste usa um `accountId`
 *     novo e espera pelo efeito, e o descarte do evento antigo é provado pelo offset confirmado, e não por um tempo
 *     fixo.
 *
 * Spec: Resposta de sucesso segue o contrato do enunciado; Conta inexistente responde 404; accountId inválido
 *     responde 400; Consultas seguidas refletem o snapshot mais recente
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.adapter.input.kafka.AWAIT_TIMEOUT
import br.com.itau.challenge.balance.adapter.input.kafka.EVENT_TIMESTAMP_MICROS
import br.com.itau.challenge.balance.adapter.input.kafka.committedOffset
import br.com.itau.challenge.balance.adapter.input.kafka.createIngestionTopics
import br.com.itau.challenge.balance.adapter.input.kafka.publish
import br.com.itau.challenge.balance.adapter.input.kafka.transactionEventJson
import org.apache.kafka.common.TopicPartition
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NEWER_TIMESTAMP_MICROS = EVENT_TIMESTAMP_MICROS + 1_000
private const val OLDER_TIMESTAMP_MICROS = EVENT_TIMESTAMP_MICROS - 1_000
private const val NEWER_BALANCE = """{"amount": 500.00, "currency": "BRL"}"""
private const val OLDER_BALANCE = """{"amount": 1.00, "currency": "BRL"}"""
private const val OK = 200
private const val BAD_REQUEST = 400
private const val NOT_FOUND = 404

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["management.server.port=0"])
class BalanceQueryEndToEndIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    private val httpClient = HttpClient.newHttpClient()
    private val jsonMapper = JsonMapper.builder().build()

    private fun awaitBalanceBody(accountId: String): String {
        await().atMost(AWAIT_TIMEOUT).until { httpClient.get(port, "/balances/$accountId").statusCode() == OK }
        return httpClient.get(port, "/balances/$accountId").body()
    }

    private fun awaitBalance(accountId: String): tools.jackson.databind.JsonNode = jsonMapper.readTree(awaitBalanceBody(accountId))

    private fun amountTextOf(body: String): String = Regex(""""amount":(-?[0-9.]+)""").find(body)?.groupValues?.get(1).orEmpty()

    private fun awaitCommittedPast(offset: Long) {
        await().atMost(AWAIT_TIMEOUT).until { (committedOffset(topics.groupId, TopicPartition(topics.main, 0)) ?: 0L) > offset }
    }

    private fun publishEvent(
        accountId: String,
        timestampMicros: Long = EVENT_TIMESTAMP_MICROS,
        status: String = "APPROVED",
        balanceJson: String = NEWER_BALANCE,
    ): Long =
        kafkaTemplate.publish(
            topics.main,
            transactionEventJson(UUID.randomUUID().toString(), status, timestampMicros, accountId, balanceJson),
        )

    @Test
    fun `should answer 200 with the event values when an event was ingested`() {
        val accountId = UUID.randomUUID().toString()
        publishEvent(accountId)

        val rawBody = awaitBalanceBody(accountId)
        val body = jsonMapper.readTree(rawBody)

        assertEquals(accountId, body["id"].asString())
        assertEquals("315e3cfe-f4af-4cd2-b298-a449e614349a", body["owner"].asString())
        assertEquals("500.00", amountTextOf(rawBody))
        assertEquals("BRL", body["balance"]["currency"].asString())
        assertEquals("2025-07-04T12:02:44.589-03:00", body["updated_at"].asString())
    }

    @Test
    fun `should answer 404 when the account has no snapshot`() {
        val response = httpClient.get(port, "/balances/${UUID.randomUUID()}")

        assertEquals(NOT_FOUND, response.statusCode())
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"))
    }

    @Test
    fun `should answer 400 when the account id is not a uuid`() {
        assertEquals(BAD_REQUEST, httpClient.get(port, "/balances/abc").statusCode())
    }

    @Test
    fun `should keep answering the newer balance when an older event arrives after it`() {
        val accountId = UUID.randomUUID().toString()
        publishEvent(accountId, NEWER_TIMESTAMP_MICROS, balanceJson = NEWER_BALANCE)
        awaitBalance(accountId)

        val olderEventOffset = publishEvent(accountId, OLDER_TIMESTAMP_MICROS, balanceJson = OLDER_BALANCE)
        awaitCommittedPast(olderEventOffset)

        assertEquals("500.00", amountTextOf(awaitBalanceBody(accountId)))
    }

    @Test
    fun `should advance only the update instant when a declined event is newer`() {
        val accountId = UUID.randomUUID().toString()
        publishEvent(accountId, EVENT_TIMESTAMP_MICROS, balanceJson = NEWER_BALANCE)
        val before = awaitBalance(accountId)["updated_at"].asString()

        publishEvent(accountId, NEWER_TIMESTAMP_MICROS + 5_000_000, status = "DECLINED", balanceJson = NEWER_BALANCE)
        await().atMost(AWAIT_TIMEOUT).until { awaitBalance(accountId)["updated_at"].asString() != before }

        assertEquals("500.00", amountTextOf(awaitBalanceBody(accountId)))
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
