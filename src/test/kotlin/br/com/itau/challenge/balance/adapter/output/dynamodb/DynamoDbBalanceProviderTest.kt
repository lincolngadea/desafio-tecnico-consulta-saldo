/*
 * L29 DynamoDbBalanceProviderTest: a leitura é o que a API expõe ao cliente, então um cliente simulado permite
 *     conferir, sem infraestrutura, que a consulta é fortemente consistente (nunca um saldo defasado) e que falha
 *     de leitura é classificada, e não engolida.
 *
 * Spec: Leitura do snapshot por conta; Classificação das falhas do DynamoDB
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DynamoDbBalanceProviderTest {

    private val client: DynamoDbClient = mock(DynamoDbClient::class.java)
    private val provider = DynamoDbBalanceProvider(client, TABLE_NAME)

    @Test
    fun `should get the item by account id with a strongly consistent read when finding a snapshot`() {
        given(client.getItem(any(GetItemRequest::class.java))).willReturn(GetItemResponse.builder().build())

        provider.findByAccountId(SNAPSHOT.accountId)

        val captor = ArgumentCaptor.forClass(GetItemRequest::class.java)
        verify(client).getItem(captor.capture())
        assertEquals(TABLE_NAME, captor.value.tableName())
        assertEquals(mapOf("accountId" to AttributeValue.builder().s(ACCOUNT_ID).build()), captor.value.key())
        assertEquals(true, captor.value.consistentRead())
    }

    @Test
    fun `should map the stored item back to the snapshot when the account has one`() {
        given(client.getItem(any(GetItemRequest::class.java))).willReturn(responseWith(storedItem()))

        val found = provider.findByAccountId(SNAPSHOT.accountId)

        assertEquals(SNAPSHOT, found)
    }

    @Test
    fun `should restore the currency scale when DynamoDB returns the amount without trailing zeros`() {
        given(client.getItem(any(GetItemRequest::class.java)))
            .willReturn(responseWith(storedItem() + ("balanceAmount" to AttributeValue.builder().n("183.1").build())))

        val found = provider.findByAccountId(SNAPSHOT.accountId)

        assertEquals(BigDecimal("183.10"), found?.balance?.amount)
    }

    @Test
    fun `should return null when the account has no snapshot`() {
        given(client.getItem(any(GetItemRequest::class.java))).willReturn(GetItemResponse.builder().build())

        val found = provider.findByAccountId(SNAPSHOT.accountId)

        assertNull(found)
    }

    @Test
    fun `should fail permanently when a required attribute is missing from the stored item`() {
        given(client.getItem(any(GetItemRequest::class.java)))
            .willReturn(responseWith(storedItem() - "ownerId"))

        assertFailsWith<PermanentStorageException> { provider.findByAccountId(SNAPSHOT.accountId) }
    }

    @Test
    fun `should fail permanently when the stored currency is invalid`() {
        given(client.getItem(any(GetItemRequest::class.java)))
            .willReturn(responseWith(storedItem() + ("balanceCurrency" to AttributeValue.builder().s("XYZ").build())))

        assertFailsWith<PermanentStorageException> { provider.findByAccountId(SNAPSHOT.accountId) }
    }

    @Test
    fun `should fail transiently when the read cannot reach DynamoDB`() {
        given(client.getItem(any(GetItemRequest::class.java))).willThrow(connectionRefused())

        assertFailsWith<TransientStorageException> { provider.findByAccountId(SNAPSHOT.accountId) }
    }

    @Test
    fun `should fail permanently when the read targets a missing table`() {
        given(client.getItem(any(GetItemRequest::class.java)))
            .willThrow(ResourceNotFoundException.builder().statusCode(BAD_REQUEST).build())

        assertFailsWith<PermanentStorageException> { provider.findByAccountId(SNAPSHOT.accountId) }
    }

    private fun storedItem(): Map<String, AttributeValue> =
        mapOf(
            "accountId" to AttributeValue.builder().s(ACCOUNT_ID).build(),
            "ownerId" to AttributeValue.builder().s(OWNER_ID).build(),
            "balanceAmount" to AttributeValue.builder().n("183.12").build(),
            "balanceCurrency" to AttributeValue.builder().s("BRL").build(),
            "lastEventTimestamp" to AttributeValue.builder().n(EVENT_MICROS.toString()).build(),
            "lastTransactionId" to AttributeValue.builder().s(TRANSACTION_ID).build(),
        )

    private fun responseWith(item: Map<String, AttributeValue>): GetItemResponse =
        GetItemResponse.builder().item(item).build()
}
