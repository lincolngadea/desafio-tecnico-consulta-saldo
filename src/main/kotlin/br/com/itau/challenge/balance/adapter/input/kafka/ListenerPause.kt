/*
 * L11 ListenerPause: é o mínimo que o `IngestionRecoverer` precisa para pedir a pausa e saber por quanto tempo, e
 *     permite testar a regra de quando pausar sem um container Kafka.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import java.time.Duration

fun interface ListenerPause {
    fun pause(): Duration
}
