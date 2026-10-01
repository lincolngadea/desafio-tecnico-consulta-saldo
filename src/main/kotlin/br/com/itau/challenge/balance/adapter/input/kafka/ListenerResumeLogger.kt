/*
 * L15 ListenerResumeLogger: o Spring retoma o container sem registrar nada; o evento de retomada permite logá-la, e o
 *     filtro por tópico evita registrar a retomada do consumer do kit.
 *
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.kafka.event.ConsumerResumedEvent
import org.springframework.stereotype.Component

@Component
class ListenerResumeLogger(
    private val properties: IngestionProperties,
) {
    private val logger = LoggerFactory.getLogger(ListenerResumeLogger::class.java)

    @EventListener
    fun logResume(event: ConsumerResumedEvent) {
        if (event.partitions.any { it.topic() == properties.topicName }) {
            logger.info("Transaction listener resumed on partitions {}", event.partitions)
        }
    }
}
