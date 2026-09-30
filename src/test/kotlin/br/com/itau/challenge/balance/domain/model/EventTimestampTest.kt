package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.InvalidEventTimestampException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val EVENT_MICROS = 1751641364589998L

class EventTimestampTest {

    @Test
    fun `should reject the timestamp when it is zero`() {
        assertFailsWith<InvalidEventTimestampException> { EventTimestamp(0) }
    }

    @Test
    fun `should reject the timestamp when it is negative`() {
        assertFailsWith<InvalidEventTimestampException> { EventTimestamp(-1) }
    }

    @Test
    fun `should keep microsecond precision when the timestamp is positive`() {
        val timestamp = EventTimestamp(EVENT_MICROS)

        assertEquals(EVENT_MICROS, timestamp.epochMicros)
    }
}
