/*
 * L36 TRANSACTION_INGESTION_CONTAINER_FACTORY: nome do container exclusivo do listener de transações, referenciado
 *     pelo `@KafkaListener`.
 * L38 SHUTDOWN_TIMEOUT: fica acima do pior caso de bloqueio de um registro, para o encerramento terminar o registro
 *     em andamento (add-transaction-ingestion design D9).
 * L39 PAUSE_SCHEDULER_THREAD_NAME_PREFIX: prefixo que identifica nos logs a thread que retoma o consumer após a
 *     pausa.
 * L43 TransactionIngestionConfig: reúne a infraestrutura do consumer de transações, separada do consumer do kit
 *     (add-transaction-ingestion design D8).
 * L46-L47 ingestionResumeScheduler: agenda a retomada da pausa; é encerrado junto com o contexto, o que cancela a
 *     retomada pendente.
 * L50-L53 ingestionPauseService: acha o container pelo id no registry e usa o agendador para retomá-lo, em vez de
 *     código próprio de pausa (Art. 10).
 * L56-L68 transactionIngestionContainerFactory: container próprio deste listener: o commit manual global quebraria o
 *     consumer do kit, que não confirma offset. `enable.auto.commit=false` vai nas propriedades do container, e não
 *     numa cópia do consumer factory, para não descartar o que o Boot registra nele (add-transaction-ingestion design
 *     D8).
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.input.kafka

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.ListenerContainerPauseService
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Duration
import java.util.Properties

const val TRANSACTION_INGESTION_CONTAINER_FACTORY = "transactionIngestionContainerFactory"

private val SHUTDOWN_TIMEOUT: Duration = Duration.ofSeconds(30)
private const val PAUSE_SCHEDULER_THREAD_NAME_PREFIX = "ingestion-resume-"

@Configuration
@EnableConfigurationProperties(IngestionProperties::class)
class TransactionIngestionConfig {

    @Bean
    fun ingestionResumeScheduler(): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply { setThreadNamePrefix(PAUSE_SCHEDULER_THREAD_NAME_PREFIX) }

    @Bean
    fun ingestionPauseService(
        registry: KafkaListenerEndpointRegistry,
        ingestionResumeScheduler: ThreadPoolTaskScheduler,
    ): ListenerContainerPauseService = ListenerContainerPauseService(registry, ingestionResumeScheduler)

    @Bean(TRANSACTION_INGESTION_CONTAINER_FACTORY)
    fun transactionIngestionContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        errorHandlerFactory: IngestionErrorHandlerFactory,
        properties: IngestionProperties,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            setConcurrency(properties.concurrency)
            setCommonErrorHandler(errorHandlerFactory.newErrorHandler())
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
            containerProperties.setShutdownTimeout(SHUTDOWN_TIMEOUT.toMillis())
            containerProperties.kafkaConsumerProperties = Properties().apply { setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false") }
        }
}
