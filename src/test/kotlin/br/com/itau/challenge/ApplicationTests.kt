/*
 * L21 ApplicationTests: verifica a unicidade dos ports no contexto real, pois uma composição isolada
 *     não detectaria services registrados em duplicidade pela varredura da aplicação.
 *
 * Spec: Composição Spring é externa ao núcleo
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge

import br.com.itau.challenge.balance.port.input.GetBalanceUseCase
import br.com.itau.challenge.balance.port.input.ProcessTransactionUseCase
import br.com.itau.challenge.hello.port.input.GetGreetingUseCase
import br.com.itau.challenge.hello.port.input.SaveGreetingTemplateUseCase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import kotlin.test.assertEquals

@SpringBootTest
class ApplicationTests @Autowired constructor(private val context: ApplicationContext) {
    @Test
    fun `should expose one production bean per input port when the full context starts`() {
        listOf(
            GetBalanceUseCase::class.java,
            ProcessTransactionUseCase::class.java,
            GetGreetingUseCase::class.java,
            SaveGreetingTemplateUseCase::class.java,
        ).forEach { type ->
            assertEquals(1, context.getBeansOfType(type).size, type.name)
        }
    }
}
