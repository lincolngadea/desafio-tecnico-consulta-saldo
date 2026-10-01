/*
 * L27 BALANCE_STORAGE_CIRCUIT_BREAKER: nome do circuito, que aparece na mensagem de `StorageUnavailableException` e
 *     identifica o circuito em métricas futuras.
 * L31 BalanceCircuitBreakerConfig: isola em uma configuração a criação do circuit breaker e a decoração do
 *     repositório de saldo.
 * L34-L47 balanceCircuitBreaker: só `TransientStorageException` conta como falha; `PermanentStorageException` é
 *     ignorada porque nada diz sobre a saúde da dependência. `minimumNumberOfCalls` igual à janela evita abrir o
 *     circuito com poucas chamadas.
 * L51-L54 resilientBalanceRepository: `@Primary` faz o caso de uso receber o repositório protegido; o writer entra
 *     por qualificador de nome para este pacote não depender da classe concreta do adapter DynamoDB.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.resilience

import br.com.itau.challenge.balance.port.output.BalanceRepository
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

private const val BALANCE_STORAGE_CIRCUIT_BREAKER = "balance-storage"

@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties::class)
class BalanceCircuitBreakerConfig {

    @Bean
    fun balanceCircuitBreaker(properties: CircuitBreakerProperties): CircuitBreaker =
        CircuitBreaker.of(
            BALANCE_STORAGE_CIRCUIT_BREAKER,
            CircuitBreakerConfig
                .custom()
                .failureRateThreshold(properties.failureRateThreshold)
                .slidingWindowSize(properties.slidingWindowSize)
                .minimumNumberOfCalls(properties.slidingWindowSize)
                .waitDurationInOpenState(properties.waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(properties.halfOpenCalls)
                .recordExceptions(TransientStorageException::class.java)
                .ignoreExceptions(PermanentStorageException::class.java)
                .build(),
        )

    @Bean
    @Primary
    fun resilientBalanceRepository(
        @Qualifier("dynamoDbBalanceWriter") delegate: BalanceRepository,
        balanceCircuitBreaker: CircuitBreaker,
    ): BalanceRepository = CircuitBreakerBalanceRepository(delegate, balanceCircuitBreaker)
}
