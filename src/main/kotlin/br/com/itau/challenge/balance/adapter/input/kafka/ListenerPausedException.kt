/*
 * L15 ListenerPausedException: é um `KafkaBackoffException` porque o Spring o trata como recuo: loga em DEBUG e
 *     devolve o registro por seek, sem commit. Uma exceção comum viraria `ERROR` com stack trace a cada pausa
 *     (add-transaction-ingestion design R1).
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.listener.KafkaBackoffException
import java.time.Duration

class ListenerPausedException(record: ConsumerRecord<*, *>, pausedFor: Duration, override val cause: Throwable) :
    KafkaBackoffException(
        "Listener paused for $pausedFor because the balance storage is unavailable; the record stays uncommitted",
        TopicPartition(record.topic(), record.partition()),
        INGESTION_LISTENER_ID,
        System.currentTimeMillis() + pausedFor.toMillis(),
    )
