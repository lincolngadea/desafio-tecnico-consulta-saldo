/*
 * L12-L19 ingestionBackOff: usa o `ExponentialBackOff` do Spring com `maxAttempts`, porque o limite exato de retries
 *     é requisito: o `ExponentialBackOffWithMaxRetries` limita pelo tempo somado e, com jitter, deixava passar um
 *     retry a mais (add-transaction-ingestion design D5).
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import org.springframework.util.backoff.ExponentialBackOff

fun ingestionBackOff(properties: IngestionProperties): ExponentialBackOff =
    ExponentialBackOff().apply {
        initialInterval = properties.backoff.initial.toMillis()
        multiplier = properties.backoff.multiplier
        maxInterval = properties.backoff.max.toMillis()
        jitter = properties.backoff.jitter.toMillis()
        maxAttempts = properties.maxRetries.toLong()
    }
