/*
 * L26 DynamoDbConfigTest: um timeout ou uma quantidade de tentativas que não chegam ao cliente real voltam a valores
 *     padrão do SDK sem que ninguém perceba, então o teste confere os clientes criados pela fábrica de produção, e
 *     não só as propriedades; inclui o cliente de leitura e que o da escrita não mudou.
 *
 * Spec: Timeouts explícitos do cliente DynamoDB; Cliente de leitura com timeouts curtos e configuráveis; Leitura
 *     repete no máximo uma vez; Configuração só por variável de ambiente
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.infrastructure.dynamodb

import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import java.net.URI
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val API_CALL_ATTEMPT_TIMEOUT: Duration = Duration.ofMillis(1500)
private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(4)
private const val MAX_ATTEMPTS = 2
private const val ACCESS_KEY_PROPERTY = "aws.accessKeyId"
private const val SECRET_KEY_PROPERTY = "aws.secretAccessKey"

class DynamoDbConfigTest {

    private val properties =
        DynamoDbProperties(
            endpoint = URI.create("http://localhost:8000"),
            region = "us-east-1",
            timeouts =
                DynamoDbProperties.Timeouts(
                    connection = Duration.ofMillis(500),
                    socket = Duration.ofSeconds(1),
                    apiCallAttempt = API_CALL_ATTEMPT_TIMEOUT,
                    apiCall = API_CALL_TIMEOUT,
                ),
            retry = DynamoDbProperties.Retry(maxAttempts = MAX_ATTEMPTS),
            read = readProfile(),
        )

    @Test
    fun `should configure the api call attempt timeout when the client is created from the properties`() {
        val client = DynamoDbConfig().dynamoDbClient(properties)

        val overrides = client.serviceClientConfiguration().overrideConfiguration()

        assertEquals(API_CALL_ATTEMPT_TIMEOUT, overrides.apiCallAttemptTimeout().orElseThrow())
    }

    @Test
    fun `should configure the total api call timeout when the client is created from the properties`() {
        val client = DynamoDbConfig().dynamoDbClient(properties)

        val overrides = client.serviceClientConfiguration().overrideConfiguration()

        assertEquals(API_CALL_TIMEOUT, overrides.apiCallTimeout().orElseThrow())
    }

    @Test
    fun `should configure the maximum attempts when the client is created from the properties`() {
        val client = DynamoDbConfig().dynamoDbClient(properties)

        val overrides = client.serviceClientConfiguration().overrideConfiguration()

        assertEquals(MAX_ATTEMPTS, overrides.retryStrategy().orElseThrow().maxAttempts())
    }

    @Test
    fun `should configure the read client with its own attempt timeout`() {
        val client = DynamoDbConfig().readDynamoDbClient(properties)

        val overrides = client.serviceClientConfiguration().overrideConfiguration()

        assertEquals(READ_API_CALL_ATTEMPT_TIMEOUT, overrides.apiCallAttemptTimeout().orElseThrow())
    }

    @Test
    fun `should configure the read client with its own total timeout`() {
        val client = DynamoDbConfig().readDynamoDbClient(properties)

        val overrides = client.serviceClientConfiguration().overrideConfiguration()

        assertEquals(READ_API_CALL_TIMEOUT, overrides.apiCallTimeout().orElseThrow())
    }

    @Test
    fun `should configure the read client with at most one retry`() {
        val client = DynamoDbConfig().readDynamoDbClient(properties)

        val overrides = client.serviceClientConfiguration().overrideConfiguration()

        assertEquals(READ_MAX_ATTEMPTS, overrides.retryStrategy().orElseThrow().maxAttempts())
    }

    @Test
    fun `should keep the write client configuration when the read client exists`() {
        val client = DynamoDbConfig().dynamoDbClient(properties)

        val overrides = client.serviceClientConfiguration().overrideConfiguration()

        assertEquals(API_CALL_TIMEOUT, overrides.apiCallTimeout().orElseThrow())
    }

    @Test
    fun `should resolve the credentials through the default provider chain when the client is created`() {
        val credentialsProvider = DynamoDbConfig().dynamoDbClient(properties).serviceClientConfiguration().credentialsProvider()

        assertIs<DefaultCredentialsProvider>(credentialsProvider)
    }

    @Test
    fun `should use the credentials of the environment when the read client is created`() {
        val previous = System.getProperty(ACCESS_KEY_PROPERTY) to System.getProperty(SECRET_KEY_PROPERTY)
        System.setProperty(ACCESS_KEY_PROPERTY, "access-from-environment")
        System.setProperty(SECRET_KEY_PROPERTY, "secret-from-environment")
        try {
            val credentialsProvider = DynamoDbConfig().readDynamoDbClient(properties).serviceClientConfiguration().credentialsProvider()

            assertEquals("access-from-environment", assertIs<AwsCredentialsProvider>(credentialsProvider).resolveCredentials().accessKeyId())
        } finally {
            restore(ACCESS_KEY_PROPERTY, previous.first)
            restore(SECRET_KEY_PROPERTY, previous.second)
        }
    }

    private fun restore(
        name: String,
        value: String?,
    ) {
        if (value == null) System.clearProperty(name) else System.setProperty(name, value)
    }
}
