package br.com.itau.challenge.balance.domain.exception

class InvalidEventTimestampException(epochMicros: Long) :
    IllegalArgumentException("Event timestamp must be a positive number of microseconds since the epoch, but was $epochMicros")
