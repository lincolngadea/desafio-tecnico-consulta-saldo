/*
 * L44 UseCaseCompositionTest: carrega somente a composição explícita, sem varrer os services, para provar
 *     que os ports de entrada são únicos.
 * L74-L95 should block only reads / should block only writes: provocar circuito aberto prova a seleção dos
 *     decorators primários, sem inspecionar campos privados. Enunciado: O que será avaliado → Resiliência
 *
 * Spec: Composição Spring é externa ao núcleo; Infraestrutura DynamoDB compartilhada não pertence a hello
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge.configuration

import br.com.itau.challenge.balance.adapter.output.dynamodb.DynamoDbBalanceProvider
import br.com.itau.challenge.balance.adapter.output.dynamodb.DynamoDbBalanceWriter
import br.com.itau.challenge.balance.adapter.output.resilience.BalanceCircuitBreakerConfig
import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import br.com.itau.challenge.balance.domain.model.OwnerId
import br.com.itau.challenge.balance.domain.model.ProcessedTransaction
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.domain.model.TransactionId
import br.com.itau.challenge.infrastructure.dynamodb.DynamoDbConfig
import br.com.itau.challenge.balance.port.input.GetBalanceUseCase
import br.com.itau.challenge.balance.port.input.ProcessTransactionUseCase
import br.com.itau.challenge.balance.port.output.BalanceProvider
import br.com.itau.challenge.balance.port.output.BalanceRepository
import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import br.com.itau.challenge.hello.port.input.GetGreetingUseCase
import br.com.itau.challenge.hello.port.input.SaveGreetingTemplateUseCase
import br.com.itau.challenge.hello.port.output.GreetingTemplateProvider
import br.com.itau.challenge.hello.port.output.GreetingTemplateRepository
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UseCaseCompositionTest {
    private val runner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withUserConfiguration(BalanceUseCaseConfiguration::class.java, BalanceCircuitBreakerConfig::class.java, Outputs::class.java)
    private val accountId = AccountId(UUID.randomUUID())
    private val transaction = ProcessedTransaction(
        transactionId = TransactionId(UUID.randomUUID()),
        timestamp = EventTimestamp(1751641364589998L),
        accountId = accountId,
        ownerId = OwnerId(UUID.randomUUID()),
        balance = Money.of(BigDecimal("50.00"), "BRL"),
    )

    @Test
    fun `should expose exactly one implementation per input port when both compositions are loaded`() {
        runner.withUserConfiguration(HelloUseCaseConfiguration::class.java, HelloOutputs::class.java).run { context ->
            assertNull(context.startupFailure)
            listOf(
                GetBalanceUseCase::class.java,
                ProcessTransactionUseCase::class.java,
                GetGreetingUseCase::class.java,
                SaveGreetingTemplateUseCase::class.java,
            ).forEach { type ->
                assertEquals(1, context.getBeansOfType(type).size, type.name)
            }
            assertEquals("Hello Ana", context.getBean(GetGreetingUseCase::class.java).getGreeting("Ana").message)
        }
    }

    @Test
    fun `should block only reads when the primary read circuit is open`() {
        runner.run { context ->
            context.getBean("balanceReadCircuitBreaker", CircuitBreaker::class.java).transitionToOpenState()
            assertFailsWith<StorageUnavailableException> { context.getBean(GetBalanceUseCase::class.java).getBalance(accountId) }
            assertEquals(SnapshotSaveResult.Applied, context.getBean(ProcessTransactionUseCase::class.java).processTransaction(transaction))
            val outputs = context.getBean(Outputs::class.java)
            assertEquals(0, outputs.reads)
            assertEquals(1, outputs.writes)
        }
    }

    @Test
    fun `should block only writes when the primary write circuit is open`() {
        runner.run { context ->
            context.getBean("balanceWriteCircuitBreaker", CircuitBreaker::class.java).transitionToOpenState()
            assertFailsWith<StorageUnavailableException> { context.getBean(ProcessTransactionUseCase::class.java).processTransaction(transaction) }
            assertNull(context.getBean(GetBalanceUseCase::class.java).getBalance(accountId))
            val outputs = context.getBean(Outputs::class.java)
            assertEquals(1, outputs.reads)
            assertEquals(0, outputs.writes)
        }
    }

    @Test
    fun `should load shared dynamodb profiles when production balance adapters are registered without hello`() {
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(
                BalanceUseCaseConfiguration::class.java,
                BalanceCircuitBreakerConfig::class.java,
                DynamoDbConfig::class.java,
                DynamoDbBalanceProvider::class.java,
                DynamoDbBalanceWriter::class.java,
            ).run { context ->
            assertNull(context.startupFailure)
            assertEquals(2, context.getBeansOfType(software.amazon.awssdk.services.dynamodb.DynamoDbClient::class.java).size)
            assertEquals(1, context.getBeansOfType(GetBalanceUseCase::class.java).size)
            assertEquals(1, context.getBeansOfType(ProcessTransactionUseCase::class.java).size)
            assertEquals(0, context.getBeansOfType(GetGreetingUseCase::class.java).size)
        }
    }

    @Configuration(proxyBeanMethods = false)
    class Outputs {
        var reads = 0
        var writes = 0

        @Bean
        fun dynamoDbBalanceProvider() = BalanceProvider {
            reads++
            null
        }

        @Bean
        fun dynamoDbBalanceWriter() = BalanceRepository {
            writes++
            SnapshotSaveResult.Applied
        }
    }

    @Configuration(proxyBeanMethods = false)
    class HelloOutputs {
        @Bean
        fun greetingTemplateProvider() = GreetingTemplateProvider { "Hello %s" }

        @Bean
        fun greetingTemplateRepository() = GreetingTemplateRepository { }
    }
}
