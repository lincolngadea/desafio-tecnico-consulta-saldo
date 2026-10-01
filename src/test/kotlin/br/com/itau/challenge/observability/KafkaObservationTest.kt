/*
 * L26 KafkaObservationTest: a observação ligada sem o `ObservationRegistry` da aplicação é um no-op silencioso,
 *     então o teste confere os dois, e que o consumer do `hello` continua sem observação.
 *
 * Spec: traceId propagado do Kafka
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.observability

import br.com.itau.challenge.balance.adapter.input.kafka.TRANSACTION_INGESTION_CONTAINER_FACTORY
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMetrics
@AutoConfigureTracing
class KafkaObservationTest(
    @Autowired @Qualifier(TRANSACTION_INGESTION_CONTAINER_FACTORY)
    private val transactionContainerFactory: ConcurrentKafkaListenerContainerFactory<String, String>,
    @Autowired @Qualifier("kafkaListenerContainerFactory")
    private val helloContainerFactory: ConcurrentKafkaListenerContainerFactory<String, String>,
    @Autowired private val observationRegistry: ObservationRegistry,
) {

    @Test
    fun `should observe the records of the transaction listener`() {
        assertTrue(transactionContainerFactory.containerProperties.isObservationEnabled)
    }

    @Test
    fun `should report the transaction listener to the application observation registry`() {
        assertSame(observationRegistry, transactionContainerFactory.containerProperties.observationRegistry)
    }

    @Test
    fun `should leave the hello listener without observation`() {
        assertFalse(helloContainerFactory.containerProperties.isObservationEnabled)
    }
}
