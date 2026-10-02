/*
 * L16 HexagonalArchitectureTest: aplica a política provada por fixtures em todo fonte de produção para
 *     impedir que um contexto novo ou incompleto escape da verificação arquitetural.
 *
 * Spec: Núcleo livre de frameworks em cada contexto; Adapters acessam casos de uso por ports;
 *     Dependências internas apontam para dentro do próprio contexto; Verificação descobre todos os bounded contexts
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge

import br.com.itau.challenge.architecture.HexagonalPolicy
import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HexagonalArchitectureTest {
    @Test
    fun `should keep every production context pure and pointing inward`() {
        val files = Konsist.scopeFromDirectory("src/main/kotlin").files
        assertEquals(emptyList(), HexagonalPolicy().violations(files))
    }
}
