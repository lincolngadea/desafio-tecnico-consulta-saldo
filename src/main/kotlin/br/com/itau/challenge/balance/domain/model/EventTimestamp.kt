package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.InvalidEventTimestampException

/** Instant of a transaction event as issued by the authorizer, in microseconds since the epoch. */
@JvmInline
value class EventTimestamp(val epochMicros: Long) : Comparable<EventTimestamp> {

    init {
        if (epochMicros <= 0) throw InvalidEventTimestampException(epochMicros)
    }

    override fun compareTo(other: EventTimestamp): Int = epochMicros.compareTo(other.epochMicros)
}
