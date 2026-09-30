/*
 * L13 EventTimestamp: instante de um evento de transação como emitido pelo autorizador, em microssegundos desde a
 *     epoch.
 * L15-L17 init: valores não positivos são rejeitados na construção, porque não conseguem ordenar eventos.
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.InvalidEventTimestampException

@JvmInline
value class EventTimestamp(val epochMicros: Long) : Comparable<EventTimestamp> {

    init {
        if (epochMicros <= 0) throw InvalidEventTimestampException(epochMicros)
    }

    override fun compareTo(other: EventTimestamp): Int = epochMicros.compareTo(other.epochMicros)
}
