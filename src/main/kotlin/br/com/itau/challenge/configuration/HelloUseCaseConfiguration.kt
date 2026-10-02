/*
 * L19 HelloUseCaseConfiguration: mantém a composição Spring fora do núcleo e recebe ports por tipo para preservar
 *     a seleção dos decorators primários sem acoplar os casos de uso aos adapters.
 *
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge.configuration

import br.com.itau.challenge.hello.application.GreetingService
import br.com.itau.challenge.hello.application.SaveGreetingTemplateService
import br.com.itau.challenge.hello.port.input.GetGreetingUseCase
import br.com.itau.challenge.hello.port.input.SaveGreetingTemplateUseCase
import br.com.itau.challenge.hello.port.output.GreetingTemplateProvider
import br.com.itau.challenge.hello.port.output.GreetingTemplateRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class HelloUseCaseConfiguration {
    @Bean
    fun getGreetingUseCase(provider: GreetingTemplateProvider): GetGreetingUseCase = GreetingService(provider)

    @Bean
    fun saveGreetingTemplateUseCase(repository: GreetingTemplateRepository): SaveGreetingTemplateUseCase = SaveGreetingTemplateService(repository)
}
