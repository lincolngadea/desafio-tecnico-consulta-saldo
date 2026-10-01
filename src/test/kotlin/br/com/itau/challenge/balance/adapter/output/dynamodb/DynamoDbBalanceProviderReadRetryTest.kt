/*
 * L41 DynamoDbBalanceProviderReadRetryTest: usa o cliente de leitura de produção contra um servidor HTTP do JDK que
 *     conta as requisições, porque só assim se prova "no máximo 2 tentativas" e "falha permanente não se repete";
 *     mocks do cliente não exercitam a política de retry do SDK.
 *
 * Spec: Leitura repete no máximo uma vez
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import br.com.itau.challenge.hello.adapter.output.dynamodb.DynamoDbConfig
import br.com.itau.challenge.hello.adapter.output.dynamodb.DynamoDbProperties
import br.com.itau.challenge.hello.adapter.output.dynamodb.readProfile
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val ANY_FREE_PORT = 0
private const val NO_BACKLOG = 0
private const val SERVER_ERROR = 500
private const val OK = 200
private const val AMAZON_JSON = "application/x-amz-json-1.0"

private val ITEM_JSON =
    """{"Item":{"accountId":{"S":"$ACCOUNT_ID"},"ownerId":{"S":"$OWNER_ID"},"balanceAmount":{"N":"183.12"},""" +
        """"balanceCurrency":{"S":"BRL"},"lastEventTimestamp":{"N":"$EVENT_MICROS"},"lastTransactionId":{"S":"$TRANSACTION_ID"}}}"""

private const val INTERNAL_ERROR_JSON = """{"__type":"com.amazon.coral.service#InternalServerError","message":"boom"}"""
private const val VALIDATION_ERROR_JSON = """{"__type":"com.amazon.coral.validate#ValidationException","message":"bad request"}"""

class DynamoDbBalanceProviderReadRetryTest {

    private val requestCount = AtomicInteger()
    private var respond: (Int, HttpExchange) -> Unit = { _, exchange -> reply(exchange, OK, ITEM_JSON) }

    private val server =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), ANY_FREE_PORT), NO_BACKLOG).apply {
            createContext("/") { exchange -> respond(requestCount.incrementAndGet(), exchange) }
            start()
        }

    private val client =
        DynamoDbConfig().readDynamoDbClient(
            DynamoDbProperties(
                endpoint = URI.create("http://localhost:${server.address.port}"),
                region = "us-east-1",
                timeouts = DynamoDbProperties.Timeouts(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(3)),
                retry = DynamoDbProperties.Retry(maxAttempts = 3),
                read = readProfile(),
            ),
        )

    private val provider = DynamoDbBalanceProvider(client, TABLE_NAME)

    @AfterEach
    fun stopServerAndClient() {
        client.close()
        server.stop(0)
    }

    @Test
    fun `should return the snapshot when the second attempt succeeds after a server error`() {
        respond = { attempt, exchange -> if (attempt == 1) reply(exchange, SERVER_ERROR, INTERNAL_ERROR_JSON) else reply(exchange, OK, ITEM_JSON) }

        val snapshot = provider.findByAccountId(SNAPSHOT.accountId)

        assertEquals(SNAPSHOT, snapshot)
        assertEquals(2, requestCount.get())
    }

    @Test
    fun `should fail transiently after exactly two requests when both attempts fail`() {
        respond = { _, exchange -> reply(exchange, SERVER_ERROR, INTERNAL_ERROR_JSON) }

        assertFailsWith<TransientStorageException> { provider.findByAccountId(SNAPSHOT.accountId) }

        assertEquals(2, requestCount.get())
    }

    @Test
    fun `should not retry when the failure is permanent`() {
        respond = { _, exchange -> reply(exchange, BAD_REQUEST, VALIDATION_ERROR_JSON) }

        assertFailsWith<PermanentStorageException> { provider.findByAccountId(SNAPSHOT.accountId) }

        assertEquals(1, requestCount.get())
    }

    private fun reply(
        exchange: HttpExchange,
        status: Int,
        body: String,
    ) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", AMAZON_JSON)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
