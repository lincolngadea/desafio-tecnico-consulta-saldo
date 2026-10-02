/*
 * L19 BalanceUseCaseConfiguration: mantém a composição Spring fora do núcleo e recebe ports por tipo para preservar
 *     a seleção dos decorators primários sem acoplar os casos de uso aos adapters.
 *
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge.configuration

import br.com.itau.challenge.balance.application.GetBalanceService
import br.com.itau.challenge.balance.application.ProcessTransactionService
import br.com.itau.challenge.balance.port.input.GetBalanceUseCase
import br.com.itau.challenge.balance.port.input.ProcessTransactionUseCase
import br.com.itau.challenge.balance.port.output.BalanceProvider
import br.com.itau.challenge.balance.port.output.BalanceRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class BalanceUseCaseConfiguration {
    @Bean
    fun getBalanceUseCase(provider: BalanceProvider): GetBalanceUseCase = GetBalanceService(provider)

    @Bean
    fun processTransactionUseCase(repository: BalanceRepository): ProcessTransactionUseCase = ProcessTransactionService(repository)
}
