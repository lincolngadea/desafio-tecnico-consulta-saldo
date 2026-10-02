/*
 * L23 ComposeFileTest: lê o `docker-compose.yml` e o `application.yaml` com o YAML do Boot, para comparar o
 *     `stop_grace_period` com o tempo de encerramento gracioso configurado, que estão em arquivos diferentes.
 *
 * Spec: Imagens com versão fixa; JVM ajustada para contêiner; Encerramento por SIGTERM; Serviço app sobe com um
 *     comando
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.observability

import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val FULL_VERSION_TAG = Regex(""".+:v?\d+\.\d+\.\d+.*""")
private const val SERVICE_COMPLETED = "service_completed_successfully"

@Suppress("UNCHECKED_CAST")
class ComposeFileTest {

    private val compose = Yaml().load<Map<String, Any>>(File("docker-compose.yml").readText())
    private val services = compose.getValue("services") as Map<String, Map<String, Any>>
    private val app = services.getValue("app")
    private val application = Yaml().load<Map<String, Any>>(File("src/main/resources/application.yaml").readText())

    private fun seconds(duration: String): Long = duration.removeSuffix("s").toLong()

    @Test
    fun `should pin every image to a full version when the compose file is read`() {
        val images = services.values.mapNotNull { it["image"] as String? }

        assertTrue(images.isNotEmpty())
        images.forEach { assertTrue(FULL_VERSION_TAG.matches(it), "floating tag: $it") }
    }

    @Test
    fun `should limit the memory of the app so that the jvm flags have effect`() {
        assertNotNull(app["mem_limit"])
    }

    @Test
    fun `should wait longer to stop the app than the graceful shutdown takes`() {
        val lifecycle = (application.getValue("spring") as Map<String, Any>).getValue("lifecycle") as Map<String, Any>

        assertTrue(seconds(app.getValue("stop_grace_period") as String) > seconds(lifecycle.getValue("timeout-per-shutdown-phase") as String))
    }

    @Test
    fun `should start the app only after both seeds complete successfully`() {
        val dependsOn = app.getValue("depends_on") as Map<String, Map<String, String>>

        assertEquals(SERVICE_COMPLETED, dependsOn.getValue("dynamodb-seed").getValue("condition"))
        assertEquals(SERVICE_COMPLETED, dependsOn.getValue("redpanda-seed").getValue("condition"))
    }

    @Test
    fun `should check the readiness probe on the management port in the app healthcheck`() {
        val test = ((app.getValue("healthcheck") as Map<String, Any>).getValue("test") as List<String>).joinToString(" ")

        assertContains(test, "8082")
        assertContains(test, "/actuator/health/readiness")
    }

    @Test
    fun `should publish the api and the management ports of the app`() {
        val ports = (app.getValue("ports") as List<Any>).map { it.toString() }

        assertContains(ports, "8080:8080")
        assertContains(ports, "8082:8082")
    }

    @Test
    fun `should give the app the credentials and the management port by environment`() {
        val environment = app.getValue("environment") as Map<String, Any>

        assertContains(environment.keys, "AWS_ACCESS_KEY_ID")
        assertContains(environment.keys, "AWS_SECRET_ACCESS_KEY")
        assertContains(environment.keys, "MANAGEMENT_PORT")
    }

    @Test
    fun `should create the ingestion topics from the redpanda seed`() {
        val entrypoint = (services.getValue("redpanda-seed").getValue("entrypoint") as List<String>).joinToString(" ")

        assertContains(entrypoint, "/redpanda-seed/ingestion-topics.sh")
    }
}
