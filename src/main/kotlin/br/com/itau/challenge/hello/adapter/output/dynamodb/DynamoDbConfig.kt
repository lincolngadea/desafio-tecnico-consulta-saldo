package br.com.itau.challenge.hello.adapter.output.dynamodb

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.http.apache5.Apache5HttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

@Configuration
@EnableConfigurationProperties(DynamoDbProperties::class)
class DynamoDbConfig {

    @Bean
    fun dynamoDbClient(properties: DynamoDbProperties): DynamoDbClient =
        DynamoDbClient
            .builder()
            .endpointOverride(properties.endpoint)
            .region(Region.of(properties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")),
            ).httpClientBuilder(
                Apache5HttpClient
                    .builder()
                    .connectionTimeout(properties.timeouts.connection)
                    .socketTimeout(properties.timeouts.socket),
            ).overrideConfiguration(
                ClientOverrideConfiguration
                    .builder()
                    .apiCallAttemptTimeout(properties.timeouts.apiCallAttempt)
                    .apiCallTimeout(properties.timeouts.apiCall)
                    .retryStrategy(
                        AwsRetryStrategy
                            .standardRetryStrategy()
                            .toBuilder()
                            .maxAttempts(properties.retry.maxAttempts)
                            .build(),
                    ).build(),
            ).build()
}
