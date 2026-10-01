/*
 * L35-L36 READ_RETRY_BASE_DELAY e READ_RETRY_MAX_DELAY: o backoff padrão do SDK (100 ms e 1 s para throttling)
 *     consumiria o orçamento da leitura; o retry rápido fica em 50 a 100 ms e não é configurável (YAGNI).
 * L40 DynamoDbConfig: cria os clientes DynamoDB da aplicação a partir das propriedades, com timeouts e tentativas
 *     explícitos: um por perfil de acesso, para a leitura ter orçamento curto sem encurtar a escrita
 *     (add-balance-query-api design D6).
 * L44-L55 dynamoDbClient: cliente da escrita e do `hello`; é `@Primary` para a injeção por tipo desses contextos
 *     continuar igual.
 * L58-L71 readDynamoDbClient: cliente da leitura: o SDK só deixa sobrescrever os timeouts por requisição, e não o
 *     número de tentativas nem o backoff, então o "no máximo 1 retry" exige um cliente próprio.
 * L73-L87 clientBuilder: o que é comum aos dois perfis (endpoint, região, credenciais e HTTP) fica num só lugar. As
 *     credenciais vêm da cadeia padrão do SDK (variáveis de ambiente), e não do código, para a configuração só
 *     variar por ambiente (12-Factor, add-observability design D9).
 * L89-L93 overrideConfiguration: os dois limites de tempo (`apiCallAttempt` e `apiCall`) são comuns aos perfis; só a
 *     estratégia de retry difere.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.hello.adapter.output.dynamodb

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.http.apache5.Apache5HttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.retries.api.BackoffStrategy
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder
import java.time.Duration

private val READ_RETRY_BASE_DELAY: Duration = Duration.ofMillis(50)
private val READ_RETRY_MAX_DELAY: Duration = Duration.ofMillis(100)

@Configuration
@EnableConfigurationProperties(DynamoDbProperties::class)
class DynamoDbConfig {

    @Bean
    @Primary
    fun dynamoDbClient(properties: DynamoDbProperties): DynamoDbClient =
        clientBuilder(properties, properties.timeouts)
            .overrideConfiguration(
                overrideConfiguration(properties.timeouts)
                    .retryStrategy(
                        AwsRetryStrategy
                            .standardRetryStrategy()
                            .toBuilder()
                            .maxAttempts(properties.retry.maxAttempts)
                            .build(),
                    ).build(),
            ).build()

    @Bean
    fun readDynamoDbClient(properties: DynamoDbProperties): DynamoDbClient =
        clientBuilder(properties, properties.read.timeouts)
            .overrideConfiguration(
                overrideConfiguration(properties.read.timeouts)
                    .retryStrategy(
                        AwsRetryStrategy
                            .standardRetryStrategy()
                            .toBuilder()
                            .maxAttempts(properties.read.retry.maxAttempts)
                            .backoffStrategy(BackoffStrategy.exponentialDelay(READ_RETRY_BASE_DELAY, READ_RETRY_MAX_DELAY))
                            .throttlingBackoffStrategy(BackoffStrategy.exponentialDelay(READ_RETRY_BASE_DELAY, READ_RETRY_MAX_DELAY))
                            .build(),
                    ).build(),
            ).build()

    private fun clientBuilder(
        properties: DynamoDbProperties,
        timeouts: DynamoDbProperties.Timeouts,
    ): DynamoDbClientBuilder =
        DynamoDbClient
            .builder()
            .endpointOverride(properties.endpoint)
            .region(Region.of(properties.region))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .httpClientBuilder(
                Apache5HttpClient
                    .builder()
                    .connectionTimeout(timeouts.connection)
                    .socketTimeout(timeouts.socket),
            )

    private fun overrideConfiguration(timeouts: DynamoDbProperties.Timeouts): ClientOverrideConfiguration.Builder =
        ClientOverrideConfiguration
            .builder()
            .apiCallAttemptTimeout(timeouts.apiCallAttempt)
            .apiCallTimeout(timeouts.apiCall)
}
