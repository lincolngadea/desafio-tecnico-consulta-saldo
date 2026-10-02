/*
 * L14 SaveGreetingTemplateService: o caso de uso continua construível com fakes, sem conhecer o container responsável
 *     pela composição da aplicação (enforce-hexagonal-architecture design D1).
 *
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge.hello.application

import br.com.itau.challenge.hello.domain.exception.InvalidGreetingTemplateException
import br.com.itau.challenge.hello.domain.model.GreetingTemplate
import br.com.itau.challenge.hello.port.input.SaveGreetingTemplateUseCase
import br.com.itau.challenge.hello.port.output.GreetingTemplateRepository

class SaveGreetingTemplateService(
    private val greetingTemplateRepository: GreetingTemplateRepository,
) : SaveGreetingTemplateUseCase {

    override fun saveGreetingTemplate(greetingTemplate: GreetingTemplate) {
        if (greetingTemplate.id.isBlank()) {
            throw InvalidGreetingTemplateException("Greeting template id must not be blank")
        }
        if (greetingTemplate.template.isBlank()) {
            throw InvalidGreetingTemplateException("Greeting template must not be blank")
        }

        greetingTemplateRepository.save(greetingTemplate)
    }
}
