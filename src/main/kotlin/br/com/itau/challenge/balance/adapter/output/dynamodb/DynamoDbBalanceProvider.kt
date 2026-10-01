/*
 * L22 DynamoDbBalanceProvider: lê o snapshot com GetItem pela chave, nunca com Scan, para a consulta continuar
 *     barata em alto volume. Recebe o cliente de leitura, de timeouts curtos e no máximo 1 retry
 *     (add-balance-query-api design D6). Enunciado: O que construir → Exposição (API REST)
 * L35-L41 consistentGetRequest: leitura fortemente consistente: logo após um Applied, nunca devolve o snapshot
 *     anterior (add-balance-repository design D4). Enunciado: O que será avaliado → Tratamento de concorrência
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.port.output.BalanceProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest

@Component
class DynamoDbBalanceProvider(
    @Qualifier("readDynamoDbClient") private val dynamoDbClient: DynamoDbClient,
    @Value("\${dynamodb.balance-table-name}") private val tableName: String,
) : BalanceProvider {

    override fun findByAccountId(accountId: AccountId): BalanceSnapshot? {
        val response =
            translatingSdkFailures("read the balance snapshot of account ${accountId.value}") {
                dynamoDbClient.getItem(consistentGetRequest(accountId))
            }
        return if (response.hasItem()) response.item().toBalanceSnapshot() else null
    }

    private fun consistentGetRequest(accountId: AccountId): GetItemRequest =
        GetItemRequest
            .builder()
            .tableName(tableName)
            .key(accountKey(accountId))
            .consistentRead(true)
            .build()
}
