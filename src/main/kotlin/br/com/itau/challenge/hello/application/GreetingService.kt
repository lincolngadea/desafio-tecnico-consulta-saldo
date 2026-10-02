/*
 * L14 GreetingService: o caso de uso continua construível com fakes, sem conhecer o container responsável
 *     pela composição da aplicação (enforce-hexagonal-architecture design D1).
 *
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge.hello.application

import br.com.itau.challenge.hello.domain.exception.BlankRequesterNameException
import br.com.itau.challenge.hello.domain.model.Greeting
import br.com.itau.challenge.hello.port.input.GetGreetingUseCase
import br.com.itau.challenge.hello.port.output.GreetingTemplateProvider

class GreetingService(
    private val greetingTemplateProvider: GreetingTemplateProvider,
) : GetGreetingUseCase {

    override fun getGreeting(requesterName: String): Greeting {
        if (requesterName.isBlank()) {
            throw BlankRequesterNameException()
        }

        val template = greetingTemplateProvider.randomTemplate()
        return Greeting(message = template.format(requesterName.trim()))
    }
}
