package br.com.itau.challenge.hello.adapter.output.dynamodb

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("dynamodb")
data class DynamoDbProperties(
    val endpoint: URI,
    val region: String,
    val timeouts: Timeouts,
    val retry: Retry,
) {
    data class Timeouts(
        val connection: Duration,
        val socket: Duration,
        val apiCallAttempt: Duration,
        /** Upper bound for the whole call, including every retry attempt. */
        val apiCall: Duration,
    )

    data class Retry(val maxAttempts: Int)
}
