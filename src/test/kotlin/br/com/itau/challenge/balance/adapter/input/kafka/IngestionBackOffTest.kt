/*
 * L23 IngestionBackOffTest: o crescimento exponencial é testado com jitter zero, porque o Spring aplica o jitter em
 *     torno do intervalo corrente; o jitter é testado pelo primeiro intervalo.
 *
 * Spec: Erro transitório é tentado de novo com backoff exponencial e jitter
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import org.junit.jupiter.api.Test
import org.springframework.util.backoff.BackOffExecution
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MAX_RETRIES = 4
private const val MULTIPLIER = 2.0
private const val SAMPLES = 50
private val INITIAL_INTERVAL: Duration = Duration.ofMillis(200)
private val MAX_INTERVAL: Duration = Duration.ofSeconds(1)
private val JITTER: Duration = Duration.ofMillis(100)

class IngestionBackOffTest {

    private fun propertiesWith(jitter: Duration) =
        IngestionProperties(
            topicName = "topic",
            dltTopicName = "topic.DLT",
            consumerGroupId = "group",
            concurrency = 1,
            maxRetries = MAX_RETRIES,
            backoff = IngestionProperties.Backoff(INITIAL_INTERVAL, MULTIPLIER, MAX_INTERVAL, jitter),
            pauseDuration = Duration.ofSeconds(30),
        )

    private fun intervalsOfOneExecution(jitter: Duration): List<Long> {
        val execution = ingestionBackOff(propertiesWith(jitter)).start()
        return generateSequence { execution.nextBackOff().takeIf { it != BackOffExecution.STOP } }.toList()
    }

    @Test
    fun `should stop after the configured number of retries when the record keeps failing`() {
        assertEquals(MAX_RETRIES, intervalsOfOneExecution(JITTER).size)
    }

    @Test
    fun `should grow each interval exponentially up to the maximum when the record keeps failing`() {
        val intervals = intervalsOfOneExecution(Duration.ZERO)

        assertEquals(listOf(200L, 400L, 800L, 1000L), intervals)
    }

    @Test
    fun `should keep the first interval within the jitter around the initial interval when the jitter is configured`() {
        val firstIntervals = List(SAMPLES) { intervalsOfOneExecution(JITTER).first() }

        val allowed = (INITIAL_INTERVAL - JITTER).toMillis()..(INITIAL_INTERVAL + JITTER).toMillis()
        assertTrue(firstIntervals.all { it in allowed }, "first intervals $firstIntervals should be within $allowed")
    }

    @Test
    fun `should vary the first interval between executions when the jitter is configured`() {
        val firstIntervals = List(SAMPLES) { intervalsOfOneExecution(JITTER).first() }.toSet()

        assertTrue(firstIntervals.size > 1, "jitter should produce different first intervals, but got $firstIntervals")
    }
}
