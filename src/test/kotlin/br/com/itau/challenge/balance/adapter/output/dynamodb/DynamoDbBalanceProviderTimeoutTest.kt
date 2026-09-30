/*
 * L29 SCHEDULING_TOLERANCE: folga para o encerramento do cliente e a variação de agendamento, além do limite
 *     configurado.
 * L33 DynamoDbBalanceProviderTimeoutTest: prova, com a fábrica de cliente de produção, que um endpoint que aceita
 *     conexões mas nunca responde não bloqueia quem chamou além do timeout total da chamada.
 * L35 silentEndpoint: o sistema operacional conclui o handshake TCP pela fila de espera, então o socket aceita mas
 *     nunca responde.
 *
 * Spec: Timeouts explícitos do cliente DynamoDB
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.port.output.TransientStorageException
import br.com.itau.challenge.hello.adapter.output.dynamodb.DynamoDbConfig
import br.com.itau.challenge.hello.adapter.output.dynamodb.DynamoDbProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeout
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.time.Duration
import java.util.UUID
import kotlin.test.assertFailsWith

private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(1)

private val SCHEDULING_TOLERANCE: Duration = Duration.ofMillis(500)

private const val ANY_FREE_PORT = 0

class DynamoDbBalanceProviderTimeoutTest {

    private val silentEndpoint = ServerSocket(ANY_FREE_PORT)

    private val dynamoDbClient =
        DynamoDbConfig().dynamoDbClient(propertiesPointingAt(URI.create("http://localhost:${silentEndpoint.localPort}")))

    private val provider = DynamoDbBalanceProvider(dynamoDbClient, TABLE_NAME)

    @AfterEach
    fun closeClientAndEndpoint() {
        dynamoDbClient.close()
        silentEndpoint.close()
    }

    @Test
    fun `should fail transiently within the api call timeout when DynamoDB never answers`() {
        val accountId = AccountId(UUID.randomUUID())

        assertTimeout(API_CALL_TIMEOUT + SCHEDULING_TOLERANCE) {
            assertFailsWith<TransientStorageException> { provider.findByAccountId(accountId) }
        }
    }

    private fun propertiesPointingAt(endpoint: URI): DynamoDbProperties =
        DynamoDbProperties(
            endpoint = endpoint,
            region = "us-east-1",
            timeouts =
                DynamoDbProperties.Timeouts(
                    connection = Duration.ofMillis(200),
                    socket = Duration.ofMillis(300),
                    apiCallAttempt = Duration.ofMillis(300),
                    apiCall = API_CALL_TIMEOUT,
                ),
            retry = DynamoDbProperties.Retry(maxAttempts = 3),
        )
}
