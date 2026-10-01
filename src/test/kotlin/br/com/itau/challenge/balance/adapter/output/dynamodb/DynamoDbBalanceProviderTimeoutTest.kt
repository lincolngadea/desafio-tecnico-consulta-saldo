/*
 * L29 SCHEDULING_TOLERANCE: folga para o encerramento do cliente e a variação de agendamento, além do limite
 *     configurado.
 * L33 DynamoDbBalanceProviderTimeoutTest: prova, com a fábrica do cliente de leitura de produção, que um endpoint
 *     que aceita conexões mas nunca responde não bloqueia quem chamou além do timeout total da leitura.
 * L35 silentEndpoint: o sistema operacional conclui o handshake TCP pela fila de espera, então o socket aceita mas
 *     nunca responde.
 *
 * Spec: Cliente de leitura com timeouts curtos e configuráveis
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.port.output.TransientStorageException
import br.com.itau.challenge.hello.adapter.output.dynamodb.DynamoDbConfig
import br.com.itau.challenge.hello.adapter.output.dynamodb.DynamoDbProperties
import br.com.itau.challenge.hello.adapter.output.dynamodb.READ_API_CALL_TIMEOUT
import br.com.itau.challenge.hello.adapter.output.dynamodb.readProfile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeout
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.time.Duration
import java.util.UUID
import kotlin.test.assertFailsWith

private val SCHEDULING_TOLERANCE: Duration = Duration.ofMillis(500)

private const val ANY_FREE_PORT = 0

class DynamoDbBalanceProviderTimeoutTest {

    private val silentEndpoint = ServerSocket(ANY_FREE_PORT)

    private val dynamoDbClient =
        DynamoDbConfig().readDynamoDbClient(propertiesPointingAt(URI.create("http://localhost:${silentEndpoint.localPort}")))

    private val provider = DynamoDbBalanceProvider(dynamoDbClient, TABLE_NAME)

    @AfterEach
    fun closeClientAndEndpoint() {
        dynamoDbClient.close()
        silentEndpoint.close()
    }

    @Test
    fun `should fail transiently within the read api call timeout when DynamoDB never answers`() {
        val accountId = AccountId(UUID.randomUUID())

        assertTimeout(READ_API_CALL_TIMEOUT + SCHEDULING_TOLERANCE) {
            assertFailsWith<TransientStorageException> { provider.findByAccountId(accountId) }
        }
    }

    private fun propertiesPointingAt(endpoint: URI): DynamoDbProperties =
        DynamoDbProperties(
            endpoint = endpoint,
            region = "us-east-1",
            timeouts = writeTimeouts(),
            retry = DynamoDbProperties.Retry(maxAttempts = 3),
            read = readProfile(),
        )

    private fun writeTimeouts() =
        DynamoDbProperties.Timeouts(
            connection = Duration.ofMillis(500),
            socket = Duration.ofSeconds(1),
            apiCallAttempt = Duration.ofSeconds(1),
            apiCall = Duration.ofSeconds(3),
        )
}
