/*
 * L21 ReadinessOnShutdownTest: o `REFUSING_TRAFFIC` é publicado pelo contexto de servidor web ao fechar, então o
 *     teste sobe um contexto servlet de verdade; num contexto sem web o evento não existe. As portas aleatórias
 *     são argumentos de maior precedência que o application.yaml, para não disputar a porta 8082 com outro teste.
 *
 * Spec: Readiness recusa tráfego no encerramento
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.observability

import org.junit.jupiter.api.Test
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.LivenessState
import org.springframework.boot.availability.ReadinessState
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.WebApplicationType
import org.springframework.context.ApplicationListener
import br.com.itau.challenge.Application
import kotlin.test.assertEquals

class ReadinessOnShutdownTest {

    private val readinessChanges = mutableListOf<ReadinessState>()
    private val livenessChanges = mutableListOf<LivenessState>()

    @Test
    fun `should refuse traffic and keep liveness correct when the web context starts closing`() {
        val context =
            SpringApplicationBuilder(Application::class.java)
                .web(WebApplicationType.SERVLET)
                .listeners(
                    ApplicationListener<AvailabilityChangeEvent<*>> { event ->
                        when (val state = event.state) {
                            is ReadinessState -> readinessChanges.add(state)
                            is LivenessState -> livenessChanges.add(state)
                        }
                    },
                ).run("--server.port=0", "--management.server.port=0")

        context.close()

        assertEquals(ReadinessState.REFUSING_TRAFFIC, readinessChanges.last())
        assertEquals(LivenessState.CORRECT, livenessChanges.last())
    }
}
