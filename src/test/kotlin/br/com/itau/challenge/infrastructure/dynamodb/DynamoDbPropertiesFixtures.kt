/*
 * L14-L16 READ_API_CALL_ATTEMPT_TIMEOUT, READ_API_CALL_TIMEOUT e READ_MAX_ATTEMPTS: os valores padrão do perfil de
 *     leitura, em um só lugar para os testes de cliente, de timeout e de retry usarem os mesmos.
 * L18-L32 readProfile: monta o perfil de leitura válido, com os parâmetros que cada teste varia; é público porque os
 *     testes de integração são outro módulo.
 *
 * Spec: n/a (add-balance-query-api design D6)
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.infrastructure.dynamodb

import java.time.Duration

val READ_API_CALL_ATTEMPT_TIMEOUT: Duration = Duration.ofMillis(300)
val READ_API_CALL_TIMEOUT: Duration = Duration.ofMillis(800)
const val READ_MAX_ATTEMPTS = 2

fun readProfile(
    apiCallAttempt: Duration = READ_API_CALL_ATTEMPT_TIMEOUT,
    apiCall: Duration = READ_API_CALL_TIMEOUT,
    maxAttempts: Int = READ_MAX_ATTEMPTS,
): DynamoDbProperties.Read =
    DynamoDbProperties.Read(
        timeouts =
            DynamoDbProperties.Timeouts(
                connection = Duration.ofMillis(200),
                socket = Duration.ofMillis(300),
                apiCallAttempt = apiCallAttempt,
                apiCall = apiCall,
            ),
        retry = DynamoDbProperties.Retry(maxAttempts = maxAttempts),
    )
