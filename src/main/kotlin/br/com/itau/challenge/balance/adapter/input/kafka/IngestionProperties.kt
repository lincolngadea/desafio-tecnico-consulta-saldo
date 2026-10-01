/*
 * L13 IngestionProperties: agrupa os parâmetros do consumer de transações (tópicos, grupo, retry, backoff e pausa),
 *     para todos poderem ser sobrescritos por variável de ambiente sem alterar código.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("ingestion")
data class IngestionProperties(
    val topicName: String,
    val dltTopicName: String,
    val consumerGroupId: String,
    val concurrency: Int,
    val maxRetries: Int,
    val backoff: Backoff,
    val pauseDuration: Duration,
) {
    data class Backoff(
        val initial: Duration,
        val multiplier: Double,
        val max: Duration,
        val jitter: Duration,
    )
}
