package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.port.output.BalanceProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest

@Component
class DynamoDbBalanceProvider(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${dynamodb.balance-table-name}") private val tableName: String,
) : BalanceProvider {

    override fun findByAccountId(accountId: AccountId): BalanceSnapshot? {
        val response =
            translatingSdkFailures("read the balance snapshot of account ${accountId.value}") {
                dynamoDbClient.getItem(consistentGetRequest(accountId))
            }
        return if (response.hasItem()) response.item().toBalanceSnapshot() else null
    }

    // Strongly consistent so a read right after an Applied write never returns the previous snapshot (design D4).
    private fun consistentGetRequest(accountId: AccountId): GetItemRequest =
        GetItemRequest
            .builder()
            .tableName(tableName)
            .key(accountKey(accountId))
            .consistentRead(true)
            .build()
}
