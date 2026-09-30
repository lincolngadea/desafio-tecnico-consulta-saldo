package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException
import software.amazon.awssdk.core.exception.ApiCallTimeoutException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException
import software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private const val INTERNAL_SERVER_ERROR = 500
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
    fun `should report stale ignored when the stored version is as recent or newer`() {
        given(client.putItem(any(PutItemRequest::class.java)))
            .willThrow(ConditionalCheckFailedException.builder().statusCode(BAD_REQUEST).build())

        val result = writer.saveIfNewer(SNAPSHOT)

        assertEquals(SnapshotSaveResult.StaleIgnored, result)
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
}
