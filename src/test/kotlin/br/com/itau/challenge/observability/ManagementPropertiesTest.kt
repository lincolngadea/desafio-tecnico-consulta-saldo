/*
 * L15 ManagementPropertiesTest: lê o `application.yaml` de verdade, para os padrões documentados e os nomes das
 *     variáveis de ambiente serem os que o código usa.
 *
 * Spec: Endpoint de métricas na porta de gerenciamento; Configuração só por variável de ambiente
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.observability

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertEquals

class ManagementPropertiesTest {

    private val contextRunner = ApplicationContextRunner().withInitializer(ConfigDataApplicationContextInitializer())

    @Test
    fun `should use port 8082 for management when no environment variable is set`() {
        contextRunner.run { context -> assertEquals("8082", context.environment.getProperty("management.server.port")) }
    }

    @Test
    fun `should use the management port from the environment when the variable is set`() {
        contextRunner.withSystemProperties("MANAGEMENT_PORT=9191").run { context ->
            assertEquals("9191", context.environment.getProperty("management.server.port"))
        }
    }

    @Test
    fun `should use the log format from the environment when the variable is set`() {
        contextRunner.withSystemProperties("LOG_FORMAT=ecs").run { context ->
            assertEquals("ecs", context.environment.getProperty("logging.structured.format.console"))
        }
    }

    @Test
    fun `should use the logstash json format for the log when no environment variable is set`() {
        contextRunner.run { context -> assertEquals("logstash", context.environment.getProperty("logging.structured.format.console")) }
    }
}
