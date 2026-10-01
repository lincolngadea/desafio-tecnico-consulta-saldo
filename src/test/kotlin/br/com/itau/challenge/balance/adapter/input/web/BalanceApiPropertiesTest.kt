/*
 * L17 BalanceApiPropertiesTest: lê o `application.yaml` de verdade, para o padrão documentado e o nome da variável
 *     de ambiente serem os que o código usa.
 *
 * Spec: Dependência indisponível responde 503 com Retry-After
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.web

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration
import kotlin.test.assertEquals

class BalanceApiPropertiesTest {

    @EnableConfigurationProperties(BalanceApiProperties::class)
    class PropertiesConfiguration

    private val contextRunner =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration::class.java)

    @Test
    fun `should use five seconds as retry after when no environment variable is set`() {
        contextRunner.run { context ->
            assertEquals(Duration.ofSeconds(5), context.getBean(BalanceApiProperties::class.java).retryAfter)
        }
    }

    @Test
    fun `should use the retry after from the environment when the variable is set`() {
        contextRunner.withSystemProperties("BALANCE_API_RETRY_AFTER=12s").run { context ->
            assertEquals(Duration.ofSeconds(12), context.getBean(BalanceApiProperties::class.java).retryAfter)
        }
    }
}
