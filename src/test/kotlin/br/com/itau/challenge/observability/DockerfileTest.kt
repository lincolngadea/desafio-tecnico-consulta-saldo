/*
 * L21 DockerfileTest: lê o `Dockerfile` do disco, porque o ambiente de desenvolvimento pode não ter Docker; é a
 *     proteção possível sem construir a imagem, e a validação real (`id -u`, `SIGTERM`) fica para o ambiente com
 *     Docker.
 *
 * Spec: Imagens com versão fixa; Imagem multi-stage mínima; Processo sem privilégio de root; JVM ajustada para
 *     contêiner; Encerramento por SIGTERM
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.observability

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val FULL_VERSION_TAG = Regex(""".+:\d+\.\d+\.\d+.*""")

class DockerfileTest {

    private val instructions: List<String> =
        File("Dockerfile").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    private val runtimeStage: List<String> =
        instructions.dropWhile { !it.startsWith("FROM ") || !it.endsWith(" AS runtime") }

    private val externalImages: List<String> =
        instructions.filter { it.startsWith("FROM ") }.map { it.removePrefix("FROM ").substringBefore(" AS ") }.filter { it.contains(":") }

    @Test
    fun `should pin every external image to a full version when the dockerfile is read`() {
        assertTrue(externalImages.isNotEmpty())
        externalImages.forEach { image ->
            assertTrue(FULL_VERSION_TAG.matches(image), "floating tag: $image")
            assertTrue(!image.endsWith(":latest"), "latest tag: $image")
        }
    }

    @Test
    fun `should build and run in separate stages when the dockerfile is read`() {
        val stages = instructions.filter { it.startsWith("FROM ") }.map { it.substringAfter(" AS ", "") }

        assertContains(stages, "builder")
        assertContains(stages, "runtime")
        assertTrue(runtimeStage.first().contains("-jre"), runtimeStage.first())
    }

    @Test
    fun `should copy only the jar into the runtime stage when the dockerfile is read`() {
        val copies = runtimeStage.filter { it.startsWith("COPY ") }

        assertEquals(1, copies.size, copies.toString())
        assertContains(copies.single(), "--from=builder")
        assertContains(copies.single(), ".jar")
    }

    @Test
    fun `should run as a numeric non root user when the dockerfile is read`() {
        val user = runtimeStage.last { it.startsWith("USER ") }.removePrefix("USER ").substringBefore(":")

        assertTrue(user.all { it.isDigit() }, "USER must be numeric: $user")
        assertNotEquals(0, user.toInt())
    }

    @Test
    fun `should set the container memory flags of the jvm when the dockerfile is read`() {
        val javaToolOptions = runtimeStage.single { it.startsWith("ENV JAVA_TOOL_OPTIONS") }

        assertContains(javaToolOptions, "-XX:MaxRAMPercentage=")
        assertContains(javaToolOptions, "-XX:+ExitOnOutOfMemoryError")
    }

    @Test
    fun `should start java directly as pid 1 in exec form when the dockerfile is read`() {
        val entrypoint = runtimeStage.single { it.startsWith("ENTRYPOINT ") }

        assertTrue(entrypoint.startsWith("ENTRYPOINT [\"java\""), entrypoint)
        assertTrue(runtimeStage.contains("STOPSIGNAL SIGTERM"))
    }
}
