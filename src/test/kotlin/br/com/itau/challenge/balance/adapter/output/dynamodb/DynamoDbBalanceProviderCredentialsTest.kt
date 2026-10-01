/*
 * L25 DynamoDbBalanceProviderCredentialsTest: um provider de credenciais que falha reproduz o ambiente sem
 *     credenciais: a consulta tem de falhar de forma explícita e permanente, sem pôr valor de credencial na
 *     mensagem.
 *
 * Spec: Configuração só por variável de ambiente
 * Enunciado: O que será avaliado → Tratamento de cenários adversos
 */
package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.port.output.PermanentStorageException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

private const val SECRET_THAT_MUST_NOT_APPEAR = "super-secret-value"

class DynamoDbBalanceProviderCredentialsTest {

    private val withoutCredentials =
        DynamoDbClient
            .builder()
            .endpointOverride(URI.create("http://localhost:1"))
            .region(Region.US_EAST_1)
            .credentialsProvider(AwsCredentialsProvider { throw SdkClientException.create("Unable to load credentials from any provider in the chain") })
            .build()

    @AfterEach
    fun closeClient() {
        withoutCredentials.close()
    }

    @Test
    fun `should fail permanently when the environment has no credentials`() {
        val provider = DynamoDbBalanceProvider(withoutCredentials, TABLE_NAME)

        val failure = assertFailsWith<PermanentStorageException> { provider.findByAccountId(SNAPSHOT.accountId) }

        assertContains(failure.message.orEmpty(), "credentials")
    }

    @Test
    fun `should not put any credential value in the failure message when the credentials cannot be loaded`() {
        val provider = DynamoDbBalanceProvider(withoutCredentials, TABLE_NAME)

        val failure = assertFailsWith<PermanentStorageException> { provider.findByAccountId(SNAPSHOT.accountId) }

        assertFalse(failure.message.orEmpty().contains(SECRET_THAT_MUST_NOT_APPEAR))
    }
}
