/*
 * L25 HexagonalArchitectureTest: mantém todo contexto delimitado na estrutura hexagonal, para o núcleo nunca
 *     depender de framework nem de adapter; estende a regra do kit ao contexto balance.
 * L29-L32 camadas (Layer): as camadas casam em todos os contextos de uma vez, porque um contexto pode ainda não ter
 *     todas elas.
 *
 * Spec: n/a (add-balance-repository design D9)
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

private const val ROOT_PACKAGE = "br.com.itau.challenge"

private val FRAMEWORK_PACKAGES =
    listOf("org.springframework", "software.amazon", "org.apache.kafka", "tools.jackson", "com.fasterxml")

class HexagonalArchitectureTest {

    @Test
    fun `should keep hexagonal layers pointing inward`() {
        val domain = Layer("Domain", "$ROOT_PACKAGE..domain..")
        val port = Layer("Port", "$ROOT_PACKAGE..port..")
        val application = Layer("Application", "$ROOT_PACKAGE..application..")
        val adapter = Layer("Adapter", "$ROOT_PACKAGE..adapter..")

        Konsist.scopeFromPackage("$ROOT_PACKAGE..").assertArchitecture {
            domain.dependsOnNothing()
            port.doesNotDependOn(application, adapter)
            application.doesNotDependOn(adapter)
        }
    }

    @ParameterizedTest
    @MethodSource("boundedContexts")
    fun `should keep the domain free of framework dependencies`(boundedContext: String) {
        Konsist
            .scopeFromPackage("$ROOT_PACKAGE.$boundedContext.domain..")
            .files
            .assertFalse { file -> FRAMEWORK_PACKAGES.any { framework -> file.hasImport { it.name.startsWith(framework) } } }
    }

    @ParameterizedTest
    @MethodSource("boundedContexts")
    fun `should keep technology types out of ports`(boundedContext: String) {
        Konsist
            .scopeFromPackage("$ROOT_PACKAGE.$boundedContext.port..")
            .files
            .assertFalse { file -> FRAMEWORK_PACKAGES.any { framework -> file.hasImport { it.name.startsWith(framework) } } }
    }

    companion object {
        @JvmStatic
        fun boundedContexts(): List<String> = listOf("hello", "balance")
    }
}
