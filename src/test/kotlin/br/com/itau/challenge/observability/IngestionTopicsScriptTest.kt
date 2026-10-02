/*
 * L18 IngestionTopicsScriptTest: garante que os nomes dos tópicos e as partições vivem num só script, usado pelo
 *     seed e pelo `Makefile`, para o conhecimento não ficar duplicado.
 *
 * Spec: Serviço app sobe com um comando
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.observability

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val TRANSACTIONS_TOPIC = "transacoes-financeiras-processadas"

class IngestionTopicsScriptTest {

    private val script = File("infra/redpanda/ingestion-topics.sh").readText()
    private val makefile = File("Makefile").readText()

    @Test
    fun `should create the transactions topic and its dead letter topic with six partitions`() {
        assertContains(script, TRANSACTIONS_TOPIC)
        assertContains(script, "$TRANSACTIONS_TOPIC.DLT")
        assertContains(script, "INGESTION_PARTITIONS:-6")
    }

    @Test
    fun `should describe the topic before creating it so that the script can run twice`() {
        assertTrue(script.indexOf("topic describe") in 0 until script.indexOf("topic create"))
    }

    @Test
    fun `should have one source for the topic names and the partitions when the makefile creates the topics`() {
        assertContains(makefile, "/redpanda-seed/ingestion-topics.sh")
        assertFalse(makefile.contains(TRANSACTIONS_TOPIC), "topic name duplicated in the Makefile")
        assertFalse(makefile.contains("PARTITIONS=6"), "partition count duplicated in the Makefile")
    }
}
