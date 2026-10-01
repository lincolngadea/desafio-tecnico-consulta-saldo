/*
 * L43-L44 BALANCE_STORAGE_WRITE_CIRCUIT_BREAKER e BALANCE_STORAGE_READ_CIRCUIT_BREAKER: nomes dos circuitos, que
 *     aparecem na mensagem de `StorageUnavailableException` e identificam cada circuito em métricas futuras.
 * L50 BalanceCircuitBreakerConfig: isola em uma configuração a criação dos circuit breakers e a decoração do
 *     repositório e do provider de saldo.
 * L53 balanceCircuitBreakerRegistry: os circuitos nascem de um registry porque o `resilience4j-micrometer` publica
 *     as métricas a partir dele; sem registry, o estado dos circuitos não chegaria ao Prometheus (add-observability
 *     design D5). Enunciado: O que será avaliado → Production readiness
 * L56-L59 balanceWriteCircuitBreaker: circuito da escrita, que pausa a ingestão quando abre; é instância própria
 *     para a API não pausar o consumer (add-balance-query-api design D7). O nome diz a que lado o circuito serve,
 *     porque há dois beans do mesmo tipo.
 * L62-L65 balanceReadCircuitBreaker: circuito da leitura, independente do da escrita: janelas, sondas e perfis de
 *     timeout diferentes não devem se misturar.
 * L68 balanceCircuitBreakerMetrics: publica o estado, as chamadas e a taxa de falha dos dois circuitos,
 *     identificados pelo nome, pela solução pronta do Resilience4j, e não por gauges escritos à mão. Enunciado: O
 *     que será avaliado → Production readiness
 * L73-L76 resilientBalanceRepository: `@Primary` faz o caso de uso receber o repositório protegido; o writer entra
 *     por qualificador de nome para este pacote não depender da classe concreta do adapter DynamoDB.
 * L80-L83 resilientBalanceProvider: `@Primary` faz o caso de consulta receber o provider protegido, com o mesmo
 *     critério de qualificador do repositório.
 * L85-L95 storageCircuitBreakerConfig: definição única do que conta como falha, compartilhada pelos dois circuitos
 *     (DRY): só `TransientStorageException` conta; `PermanentStorageException` é ignorada porque nada diz sobre a
 *     saúde da dependência. `minimumNumberOfCalls` igual à janela evita abrir o circuito com poucas chamadas.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.resilience

import br.com.itau.challenge.balance.port.output.BalanceProvider
import br.com.itau.challenge.balance.port.output.BalanceRepository
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

private const val BALANCE_STORAGE_WRITE_CIRCUIT_BREAKER = "balance-storage"
private const val BALANCE_STORAGE_READ_CIRCUIT_BREAKER = "balance-storage-read"
private const val WRITE_CIRCUIT_BREAKER_BEAN = "balanceWriteCircuitBreaker"
private const val READ_CIRCUIT_BREAKER_BEAN = "balanceReadCircuitBreaker"

@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties::class)
class BalanceCircuitBreakerConfig {

    @Bean
    fun balanceCircuitBreakerRegistry(): CircuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults()

    @Bean
    fun balanceWriteCircuitBreaker(
        properties: CircuitBreakerProperties,
        registry: CircuitBreakerRegistry,
    ): CircuitBreaker = registry.circuitBreaker(BALANCE_STORAGE_WRITE_CIRCUIT_BREAKER, storageCircuitBreakerConfig(properties))

    @Bean
    fun balanceReadCircuitBreaker(
        properties: CircuitBreakerProperties,
        registry: CircuitBreakerRegistry,
    ): CircuitBreaker = registry.circuitBreaker(BALANCE_STORAGE_READ_CIRCUIT_BREAKER, storageCircuitBreakerConfig(properties))

    @Bean
    fun balanceCircuitBreakerMetrics(registry: CircuitBreakerRegistry): TaggedCircuitBreakerMetrics =
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry)

    @Bean
    @Primary
    fun resilientBalanceRepository(
        @Qualifier("dynamoDbBalanceWriter") delegate: BalanceRepository,
        @Qualifier(WRITE_CIRCUIT_BREAKER_BEAN) circuitBreaker: CircuitBreaker,
    ): BalanceRepository = CircuitBreakerBalanceRepository(delegate, circuitBreaker)

    @Bean
    @Primary
    fun resilientBalanceProvider(
        @Qualifier("dynamoDbBalanceProvider") delegate: BalanceProvider,
        @Qualifier(READ_CIRCUIT_BREAKER_BEAN) circuitBreaker: CircuitBreaker,
    ): BalanceProvider = CircuitBreakerBalanceProvider(delegate, circuitBreaker)

    private fun storageCircuitBreakerConfig(properties: CircuitBreakerProperties): CircuitBreakerConfig =
        CircuitBreakerConfig
            .custom()
            .failureRateThreshold(properties.failureRateThreshold)
            .slidingWindowSize(properties.slidingWindowSize)
            .minimumNumberOfCalls(properties.slidingWindowSize)
            .waitDurationInOpenState(properties.waitDurationInOpenState)
            .permittedNumberOfCallsInHalfOpenState(properties.halfOpenCalls)
            .recordExceptions(TransientStorageException::class.java)
            .ignoreExceptions(PermanentStorageException::class.java)
            .build()
}
