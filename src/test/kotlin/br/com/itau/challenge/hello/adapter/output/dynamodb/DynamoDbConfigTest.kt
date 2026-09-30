package br.com.itau.challenge.hello.adapter.output.dynamodb

import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Duration
import kotlin.test.assertEquals

private val API_CALL_ATTEMPT_TIMEOUT: Duration = Duration.ofMillis(1500)
private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(4)
private const val MAX_ATTEMPTS = 2

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
}
