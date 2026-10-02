/*
 * L34 CodeQlWorkflowTest: lê o workflow com o YAML do Boot, como o ComposeFileTest, porque a análise só roda no
 *     GitHub; assim o check protege a configuração que destrava o extrator Kotlin.
 * L91-L96 should keep the analysis settings out of the versioned gradle properties: o `gradle.properties` é o
 *     lugar versionado dessas chaves; sem elas ali, o build local mantém o Kotlin daemon e workers paralelos.
 *
 * Spec: Análise compila o código com build explícito; Compilação compatível com o extrator Kotlin; Duração
 *     limitada da análise
 * Enunciado: n/a (fix-codeql-build design D6)
 */
package br.com.itau.challenge.codescanning

import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val CODEQL_WORKFLOW = ".github/workflows/codeql.yml"
private const val GRADLE_PROPERTIES = "gradle.properties"
private const val CODEQL_INIT_ACTION = "github/codeql-action/init"
private const val CODEQL_AUTOBUILD_ACTION = "github/codeql-action/autobuild"
private const val KOTLIN_EXECUTION_STRATEGY = "kotlin.compiler.execution.strategy"
private const val GRADLE_WORKERS_MAX = "org.gradle.workers.max"
private const val KOTLIN_IN_PROCESS = "-P$KOTLIN_EXECUTION_STRATEGY=in-process"
private const val SINGLE_WORKER = "--max-workers=1"
private const val EXTRACTOR_SIZED_GRADLE_HEAP = "\"-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g\""
private const val MAX_TIMEOUT_MINUTES = 30

@Suppress("UNCHECKED_CAST")
class CodeQlWorkflowTest {

    private val workflow = Yaml().load<Map<String, Any>>(File(CODEQL_WORKFLOW).readText())
    private val analyzeJob = (workflow.getValue("jobs") as Map<String, Map<String, Any>>).getValue("analyze")
    private val steps = analyzeJob.getValue("steps") as List<Map<String, Any>>

    private fun stepsUsing(action: String): List<Map<String, Any>> =
        steps.filter { (it["uses"] as String?).orEmpty().startsWith(action) }

    private fun buildCommand(): String = steps.mapNotNull { it["run"] as String? }.joinToString("\n")

    private fun buildArguments(): List<String> = buildCommand().split(Regex("""\s+"""))

    private fun versionedGradlePropertiesOrEmpty(): String =
        File(GRADLE_PROPERTIES).takeIf { it.exists() }?.readText().orEmpty()

    @Test
    fun `should declare a manual build for java kotlin and no autobuild step when the codeql workflow is read`() {
        val initInputs = stepsUsing(CODEQL_INIT_ACTION).single().getValue("with") as Map<String, Any>

        assertEquals("java-kotlin", initInputs["languages"])
        assertEquals("manual", initInputs["build-mode"])
        assertEquals(emptyList(), stepsUsing(CODEQL_AUTOBUILD_ACTION))
    }

    @Test
    fun `should compile production unit test and integration test sources when the build step runs`() {
        val arguments = buildArguments()

        assertContains(arguments, "testClasses")
        assertContains(arguments, "integrationTestClasses")
    }

    @Test
    fun `should compile kotlin in process with a single worker when the build step runs`() {
        val arguments = buildArguments()

        assertContains(arguments, KOTLIN_IN_PROCESS)
        assertContains(arguments, SINGLE_WORKER)
    }

    @Test
    fun `should size the gradle heap for the extractor when the build step runs`() {
        val command = buildCommand()

        assertContains(command, EXTRACTOR_SIZED_GRADLE_HEAP)
    }

    @Test
    fun `should limit the analysis job duration when the codeql workflow is read`() {
        val timeoutMinutes = analyzeJob["timeout-minutes"] as Int?

        assertNotNull(timeoutMinutes)
        assertTrue(timeoutMinutes <= MAX_TIMEOUT_MINUTES, "timeout-minutes: $timeoutMinutes")
    }

    @Test
    fun `should keep the analysis settings out of the versioned gradle properties when the repository is inspected`() {
        val gradleProperties = versionedGradlePropertiesOrEmpty()

        assertFalse(gradleProperties.contains(KOTLIN_EXECUTION_STRATEGY), gradleProperties)
        assertFalse(gradleProperties.contains(GRADLE_WORKERS_MAX), gradleProperties)
    }
}
