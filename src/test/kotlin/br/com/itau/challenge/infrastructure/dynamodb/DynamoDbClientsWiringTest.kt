/*
 * L20 DynamoDbClientsWiringTest: sobe o contexto real para provar que a injeção de `DynamoDbClient` por tipo, sem
 *     qualificador (writer e `hello`), continua recebendo o cliente da escrita, que é o `@Primary`, e que o cliente
 *     de leitura é outra instância.
 *
 * Spec: Cliente de leitura com timeouts curtos e configuráveis
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.infrastructure.dynamodb

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@SpringBootTest
class DynamoDbClientsWiringTest(
    @Autowired private val injectedByType: DynamoDbClient,
    @Autowired @Qualifier("dynamoDbClient") private val writeClient: DynamoDbClient,
    @Autowired @Qualifier("readDynamoDbClient") private val readClient: DynamoDbClient,
) {

    @Test
    fun `should inject the write client when the client is requested by type without qualifier`() {
        assertSame(writeClient, injectedByType)
    }

    @Test
    fun `should keep the read client distinct from the write client`() {
        assertNotSame(writeClient, readClient)
    }
}
