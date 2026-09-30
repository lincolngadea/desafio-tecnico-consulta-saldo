/*
 * L9 InvalidEventTimestampException: lançada quando um timestamp não consegue ordenar eventos, para dado inválido
 *     ser rejeitado em vez de corromper a ordem dos snapshots.
 *
 * Enunciado: O que será avaliado → Tratamento de cenários adversos
 */
package br.com.itau.challenge.balance.domain.exception

class InvalidEventTimestampException(epochMicros: Long) :
    IllegalArgumentException("Event timestamp must be a positive number of microseconds since the epoch, but was $epochMicros")
