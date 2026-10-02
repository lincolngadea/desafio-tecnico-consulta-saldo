/*
 * L22 HexagonalPolicyTest: injeta fontes mínimas no mesmo guard da produção; uma fonte inválida deve falhar
 *     sem precisar compilar um terceiro contexto de negócio.
 *
 * Spec: Núcleo livre de frameworks em cada contexto; Adapters acessam casos de uso por ports;
 *     Dependências internas apontam para dentro do próprio contexto; Verificação descobre todos os bounded contexts
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HexagonalPolicyTest {
    @TempDir
    lateinit var directory: Path
    private val policy = HexagonalPolicy()

    @Test
    fun `should accept a pure core when only domain and port types are referenced`() {
        assertEquals(emptyList(), violations("balance.application", "import br.com.itau.challenge.balance.port.input.GetBalanceUseCase\nimport java.math.BigDecimal\nclass Service"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["hello", "balance"])
    fun `should reject spring when application uses a service annotation`(context: String) {
        assertRejected("$context.application", "import org.springframework.stereotype.Service\n@Service class Service", "org.springframework")
    }

    @ParameterizedTest
    @CsvSource(
        "domain,io.micrometer.core.instrument.MeterRegistry",
        "port,io.github.resilience4j.circuitbreaker.CircuitBreaker",
        "application,jakarta.inject.Inject",
        "port,javax.inject.Inject",
    )
    fun `should reject other frameworks when the core references them`(layer: String, dependency: String) {
        assertRejected("balance.$layer", "import $dependency\nclass Subject", dependency)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "import org.springframework.stereotype.Service as Managed\n@Managed class Subject",
        "@org.springframework.stereotype.Service class Subject",
        "class Subject(val registry: io.micrometer.core.instrument.MeterRegistry)",
        "fun registry() = io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults(\"test\")",
    ])
    fun `should reject aliases and qualified references when no ordinary import is present`(source: String) {
        assertTrue(violations("balance.application", source).isNotEmpty())
    }

    @Test
    fun `should ignore comments and string literals when they contain framework names`() {
        assertEquals(emptyList(), violations("balance.application", "// org.springframework.stereotype.Service\n/* io.micrometer.core.instrument.MeterRegistry */\nval message = \"io.github.resilience4j.circuitbreaker.CircuitBreaker\""))
    }

    @ParameterizedTest
    @ValueSource(strings = ["br.com.itau.challenge.balance.application.GetBalanceService", "br.com.itau.challenge.configuration.BalanceUseCaseConfiguration"])
    fun `should reject application and composition when an adapter references them`(dependency: String) {
        assertRejected("balance.adapter.input.web", "import $dependency\nclass Controller", dependency)
    }

    @Test
    fun `should accept adapters when they use ports domain models and frameworks`() {
        assertEquals(emptyList(), violations("balance.adapter.input.web", "import br.com.itau.challenge.balance.port.input.GetBalanceUseCase\nimport br.com.itau.challenge.balance.domain.model.AccountId\nimport org.springframework.web.bind.annotation.RestController\nclass Controller"))
    }

    @ParameterizedTest
    @CsvSource(
        "domain,br.com.itau.challenge.balance.port.output.BalanceProvider",
        "port,br.com.itau.challenge.balance.application.GetBalanceService",
        "application,br.com.itau.challenge.balance.adapter.output.dynamodb.DynamoDbBalanceProvider",
        "domain,br.com.itau.challenge.hello.domain.model.Greeting",
        "application,br.com.itau.challenge.configuration.BalanceUseCaseConfiguration",
    )
    fun `should reject outward and cross context dependencies when the core imports them`(layer: String, dependency: String) {
        assertRejected("balance.$layer", "import $dependency\nclass Subject", dependency)
    }

    @Test
    fun `should discover hello and balance when their core layers exist`() {
        directory.resolve("Hello.kt").writeText("package br.com.itau.challenge.hello.domain\nclass Greeting")
        directory.resolve("Balance.kt").writeText("package br.com.itau.challenge.balance.port\ninterface Provider")
        assertEquals(setOf("hello", "balance"), policy.contexts(files()))
    }

    @Test
    fun `should reject a third partial context without adding it to a list`() {
        assertRejected("payments.port", "import org.springframework.stereotype.Service\ninterface Payment", "org.springframework")
        assertEquals(setOf("payments"), policy.contexts(files()))
    }

    @Test
    fun `should exclude technical packages when discovering contexts`() {
        directory.resolve("Configuration.kt").writeText("package br.com.itau.challenge.configuration\nclass Configuration")
        directory.resolve("Infrastructure.kt").writeText("package br.com.itau.challenge.infrastructure.dynamodb\nclass Client")
        directory.resolve("Balance.kt").writeText("package br.com.itau.challenge.balance.domain\nclass Account")
        assertEquals(setOf("balance"), policy.contexts(files()))
    }

    @Test
    fun `should accept real jdk javax packages when imports include a wildcard`() {
        assertEquals(emptyList(), violations("balance.domain", "import javax.crypto.*\nclass Subject(val cipher: Cipher)"))
    }

    @Test
    fun `should reject qualified adapter dependencies when imports are absent`() {
        assertRejected("balance.adapter.input.web", "class Subject(val service: br.com.itau.challenge.balance.application.GetBalanceService)", "br.com.itau.challenge.balance.application.GetBalanceService")
    }

    @Test
    fun `should reject framework wildcard imports when the core uses them`() {
        assertRejected("balance.port", "import io.micrometer.core.instrument.*\ninterface Subject", "io.micrometer.core.instrument")
    }

    @Test
    fun `should reject a qualified framework function when no type name or import is present`() {
        assertRejected("balance.application", "fun composition() = org.springframework.context.support.beans { }", "org.springframework.context.support.beans")
    }

    @Test
    fun `should ignore ordinary receiver chains when they do not name a dependency package`() {
        assertEquals(emptyList(), violations("balance.application", "fun amount(invoice: Invoice) = invoice.details.amount"))
    }

    @Test
    fun `should reject a qualified composition function when an adapter calls it`() {
        assertRejected("balance.adapter.input.web", "fun service() = br.com.itau.challenge.configuration.createService()", "br.com.itau.challenge.configuration.createService")
    }

    @Test
    fun `should accept own core qualified types when imports are absent`() {
        assertEquals(emptyList(), violations("balance.application", "class Subject(val account: br.com.itau.challenge.balance.domain.model.AccountId)"))
    }

    @Test
    fun `should report missing contexts when the expected scope is empty`() {
        directory.resolve("Subject.kt").writeText("package unrelated\nclass Subject")
        assertTrue(policy.violations(files()).any { it.contains("No bounded contexts") })
    }

    private fun files() = Konsist.scopeFromExternalDirectory(directory.toString()).files

    private fun violations(layer: String, source: String): List<String> {
        directory.resolve("Subject.kt").writeText("package br.com.itau.challenge.$layer\n$source")
        return policy.violations(files())
    }

    private fun assertRejected(layer: String, source: String, dependency: String) {
        assertTrue(violations(layer, source).any { it.contains(dependency) }, "Dependency must be rejected: $dependency")
    }
}
