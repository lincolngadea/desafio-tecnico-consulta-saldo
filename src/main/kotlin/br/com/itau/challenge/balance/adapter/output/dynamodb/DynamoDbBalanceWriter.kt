package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.output.BalanceRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest

private const val TIMESTAMP_PLACEHOLDER = ":timestamp"
private const val TRANSACTION_ID_PLACEHOLDER = ":transactionId"

// Storage translation of SnapshotVersion ordering (timestamp, then transaction id as text); keep both in sync.
private const val STORED_VERSION_IS_OLDER =
    "attribute_not_exists($ACCOUNT_ID_ATTRIBUTE) " +
        "OR $LAST_EVENT_TIMESTAMP_ATTRIBUTE < $TIMESTAMP_PLACEHOLDER " +
        "OR ($LAST_EVENT_TIMESTAMP_ATTRIBUTE = $TIMESTAMP_PLACEHOLDER " +
        "AND $LAST_TRANSACTION_ID_ATTRIBUTE < $TRANSACTION_ID_PLACEHOLDER)"

@Component
class DynamoDbBalanceWriter(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${dynamodb.balance-table-name}") private val tableName: String,
) : BalanceRepository {

    override fun saveIfNewer(snapshot: BalanceSnapshot): SnapshotSaveResult =
        translatingSdkFailures("save the balance snapshot of account ${snapshot.accountId.value}") {
            putIfNewer(snapshot)
        }

    private fun putIfNewer(snapshot: BalanceSnapshot): SnapshotSaveResult =
        try {
            dynamoDbClient.putItem(putIfNewerRequest(snapshot))
            SnapshotSaveResult.Applied
        } catch (_: ConditionalCheckFailedException) {
            SnapshotSaveResult.StaleIgnored
        }

    private fun putIfNewerRequest(snapshot: BalanceSnapshot): PutItemRequest =
        PutItemRequest
            .builder()
            .tableName(tableName)
            .item(snapshot.toItem())
            .conditionExpression(STORED_VERSION_IS_OLDER)
            .expressionAttributeValues(
                mapOf(
                    TIMESTAMP_PLACEHOLDER to numberValue(snapshot.version.timestamp.epochMicros.toString()),
                    TRANSACTION_ID_PLACEHOLDER to stringValue(snapshot.version.transactionId.value.toString()),
                ),
            ).build()
}
