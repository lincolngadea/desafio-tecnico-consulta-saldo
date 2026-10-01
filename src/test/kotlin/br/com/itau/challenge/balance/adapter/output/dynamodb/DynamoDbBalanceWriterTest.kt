/*
 * L49 DynamoDbBalanceWriterTest: a requisição condicional é o contrato com o DynamoDB: um cliente simulado permite
 *     conferir a `ConditionExpression` e a classificação das falhas sem infraestrutura, enquanto o comportamento
 *     real fica no teste de integração.
 * L255-L258 refusedBy: faz o cliente simulado recusar a condição devolvendo o item que bloqueou a gravação, como o
 *     DynamoDB faz com `ReturnValuesOnConditionCheckFailure`.
 *
 * Spec: Gravação condicional do snapshot; Classificação das falhas do DynamoDB
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException
import software.amazon.awssdk.core.exception.ApiCallTimeoutException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val INTERNAL_SERVER_ERROR = 500
private const val GREATER_TRANSACTION_ID = "ffffffff-b154-48b5-9f3e-553935cc4543"
private const val SERVICE_UNAVAILABLE = 503
private const val TIMEOUT_MILLIS = 3000L

class DynamoDbBalanceWriterTest {

    private val client: DynamoDbClient = mock(DynamoDbClient::class.java)
    private val writer = DynamoDbBalanceWriter(client, TABLE_NAME)

    @Test
    fun `should put one item keyed by account id into the configured table when saving a snapshot`() {
        given(client.putItem(any(PutItemRequest::class.java))).willReturn(PutItemResponse.builder().build())

        writer.saveIfNewer(SNAPSHOT)

        val request = capturedPutRequest()
        assertEquals(TABLE_NAME, request.tableName())
        assertEquals(ACCOUNT_ID, request.item().getValue("accountId").s())
        assertEquals(OWNER_ID, request.item().getValue("ownerId").s())
        assertEquals("183.12", request.item().getValue("balanceAmount").n())
        assertEquals("BRL", request.item().getValue("balanceCurrency").s())
        assertEquals(EVENT_MICROS.toString(), request.item().getValue("lastEventTimestamp").n())
        assertEquals(TRANSACTION_ID, request.item().getValue("lastTransactionId").s())
    }

    @Test
    fun `should condition the put on the stored version being older when saving a snapshot`() {
        given(client.putItem(any(PutItemRequest::class.java))).willReturn(PutItemResponse.builder().build())

        writer.saveIfNewer(SNAPSHOT)

        val request = capturedPutRequest()
        assertEquals(
            "attribute_not_exists(accountId) OR lastEventTimestamp < :timestamp " +
                "OR (lastEventTimestamp = :timestamp AND lastTransactionId < :transactionId)",
            request.conditionExpression(),
        )
        assertEquals(EVENT_MICROS.toString(), request.expressionAttributeValues().getValue(":timestamp").n())
        assertEquals(TRANSACTION_ID, request.expressionAttributeValues().getValue(":transactionId").s())
    }

    @Test
    fun `should report applied when the conditional put succeeds`() {
        given(client.putItem(any(PutItemRequest::class.java))).willReturn(PutItemResponse.builder().build())

        val result = writer.saveIfNewer(SNAPSHOT)

        assertEquals(SnapshotSaveResult.Applied, result)
    }

    @Test
    fun `should ask DynamoDB for the blocking item when the condition fails`() {
        given(client.putItem(any(PutItemRequest::class.java))).willReturn(PutItemResponse.builder().build())

        writer.saveIfNewer(SNAPSHOT)

        assertEquals(ReturnValuesOnConditionCheckFailure.ALL_OLD, capturedPutRequest().returnValuesOnConditionCheckFailure())
    }

    @Test
    fun `should report duplicate ignored when the blocking item has the same version`() {
        refusedBy(SNAPSHOT.toItem())

        assertEquals(SnapshotSaveResult.DuplicateIgnored, writer.saveIfNewer(SNAPSHOT))
    }

    @Test
    fun `should report stale ignored when the blocking item has a newer timestamp`() {
        refusedBy(storedItemWith(timestamp = EVENT_MICROS + 1, transactionId = TRANSACTION_ID))

        assertEquals(SnapshotSaveResult.StaleIgnored, writer.saveIfNewer(SNAPSHOT))
    }

    @Test
    fun `should report stale ignored when the timestamps tie and the blocking item has a greater transaction id`() {
        refusedBy(storedItemWith(timestamp = EVENT_MICROS, transactionId = GREATER_TRANSACTION_ID))

        assertEquals(SnapshotSaveResult.StaleIgnored, writer.saveIfNewer(SNAPSHOT))
    }

    @Test
    fun `should report stale ignored when DynamoDB does not return the blocking item`() {
        given(client.putItem(any(PutItemRequest::class.java)))
            .willThrow(ConditionalCheckFailedException.builder().statusCode(BAD_REQUEST).build())

        assertEquals(SnapshotSaveResult.StaleIgnored, writer.saveIfNewer(SNAPSHOT))
    }

    @Test
    fun `should fail permanently when the blocking item is malformed`() {
        refusedBy(mapOf("accountId" to stringValue(ACCOUNT_ID)))

        assertFailsWith<PermanentStorageException> { writer.saveIfNewer(SNAPSHOT) }
    }

    @Test
    fun `should not read the item again when the condition fails`() {
        refusedBy(SNAPSHOT.toItem())

        writer.saveIfNewer(SNAPSHOT)

        verify(client, never()).getItem(any(GetItemRequest::class.java))
    }

    @Test
    fun `should fail transiently when DynamoDB throttles the write`() {
        val throttling =
            ProvisionedThroughputExceededException
                .builder()
                .statusCode(BAD_REQUEST)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("ProvisionedThroughputExceededException").build())
                .build()
        given(client.putItem(any(PutItemRequest::class.java))).willThrow(throttling)

        assertFailsWith<TransientStorageException> { writer.saveIfNewer(SNAPSHOT) }
    }

    @Test
    fun `should fail transiently when DynamoDB answers with an internal server error`() {
        given(client.putItem(any(PutItemRequest::class.java)))
            .willThrow(InternalServerErrorException.builder().statusCode(INTERNAL_SERVER_ERROR).build())

        assertFailsWith<TransientStorageException> { writer.saveIfNewer(SNAPSHOT) }
    }

    @Test
    fun `should fail transiently when DynamoDB is unavailable`() {
        given(client.putItem(any(PutItemRequest::class.java)))
            .willThrow(DynamoDbException.builder().statusCode(SERVICE_UNAVAILABLE).build())

        assertFailsWith<TransientStorageException> { writer.saveIfNewer(SNAPSHOT) }
    }

    @Test
    fun `should fail transiently when the whole api call times out`() {
        given(client.putItem(any(PutItemRequest::class.java))).willThrow(ApiCallTimeoutException.create(TIMEOUT_MILLIS))

        assertFailsWith<TransientStorageException> { writer.saveIfNewer(SNAPSHOT) }
    }

    @Test
    fun `should fail transiently when an api call attempt times out`() {
        given(client.putItem(any(PutItemRequest::class.java)))
            .willThrow(ApiCallAttemptTimeoutException.create(TIMEOUT_MILLIS))

        assertFailsWith<TransientStorageException> { writer.saveIfNewer(SNAPSHOT) }
    }

    @Test
    fun `should fail transiently when the connection to DynamoDB fails`() {
        given(client.putItem(any(PutItemRequest::class.java))).willThrow(connectionRefused())

        assertFailsWith<TransientStorageException> { writer.saveIfNewer(SNAPSHOT) }
    }

    @Test
    fun `should fail permanently when the table does not exist`() {
        given(client.putItem(any(PutItemRequest::class.java)))
            .willThrow(ResourceNotFoundException.builder().statusCode(BAD_REQUEST).build())

        assertFailsWith<PermanentStorageException> { writer.saveIfNewer(SNAPSHOT) }
    }

    @Test
    fun `should fail permanently when DynamoDB rejects the request as invalid`() {
        val validation =
            DynamoDbException
                .builder()
                .statusCode(BAD_REQUEST)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("ValidationException").build())
                .build()
        given(client.putItem(any(PutItemRequest::class.java))).willThrow(validation)

        assertFailsWith<PermanentStorageException> { writer.saveIfNewer(SNAPSHOT) }
    }

    @Test
    fun `should fail permanently when the client fails without an I-O cause`() {
        given(client.putItem(any(PutItemRequest::class.java)))
            .willThrow(SdkClientException.builder().message("Unable to unmarshall response").build())

        assertFailsWith<PermanentStorageException> { writer.saveIfNewer(SNAPSHOT) }
    }

    @Test
    fun `should preserve the SDK exception as the cause when a failure is translated`() {
        val sdkFailure = connectionRefused()
        given(client.putItem(any(PutItemRequest::class.java))).willThrow(sdkFailure)

        val failure = assertFailsWith<TransientStorageException> { writer.saveIfNewer(SNAPSHOT) }

        assertSame(sdkFailure, failure.cause)
    }

    private fun capturedPutRequest(): PutItemRequest {
        val captor = ArgumentCaptor.forClass(PutItemRequest::class.java)
        verify(client).putItem(captor.capture())
        return captor.value
    }

    @Test
    fun `should mention only the first group of the account id when a failure message is built`() {
        given(client.putItem(any(PutItemRequest::class.java))).willThrow(connectionRefused())

        val failure = assertFailsWith<TransientStorageException> { writer.saveIfNewer(SNAPSHOT) }

        assertTrue(failure.message.orEmpty().contains(ACCOUNT_ID.substringBefore('-')), failure.message)
        assertFalse(failure.message.orEmpty().contains(ACCOUNT_ID), failure.message)
    }

    private fun refusedBy(blockingItem: Map<String, AttributeValue>) {
        given(client.putItem(any(PutItemRequest::class.java)))
            .willThrow(ConditionalCheckFailedException.builder().statusCode(BAD_REQUEST).item(blockingItem).build())
    }

    private fun storedItemWith(
        timestamp: Long,
        transactionId: String,
    ): Map<String, AttributeValue> =
        SNAPSHOT.toItem() +
            mapOf(
                "lastEventTimestamp" to numberValue(timestamp.toString()),
                "lastTransactionId" to stringValue(transactionId),
            )
}
