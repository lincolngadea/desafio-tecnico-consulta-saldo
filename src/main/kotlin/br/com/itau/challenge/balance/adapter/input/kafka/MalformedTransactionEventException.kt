/*
 * L9 MalformedTransactionEventException: é o único tipo de erro permanente de dado, para o error handler decidir o
 *     destino (DLT) sem enumerar as exceções de parse e de domínio.
 *
 * Enunciado: O que será avaliado → Tratamento de cenários adversos
 */
package br.com.itau.challenge.balance.adapter.input.kafka

class MalformedTransactionEventException(message: String, cause: Throwable) : RuntimeException(message, cause)
