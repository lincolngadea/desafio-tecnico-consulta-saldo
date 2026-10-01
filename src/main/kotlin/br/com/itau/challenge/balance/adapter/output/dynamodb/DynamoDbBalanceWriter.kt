/*
 * L30-L34 STORED_VERSION_IS_OLDER: tradução para o armazenamento da ordem de SnapshotVersion (timestamp e, no
 *     empate, o id da transação como texto); mantenha as duas em sincronia. O DynamoDB avalia a condição dentro da
 *     própria gravação, então o compare-and-set é atômico e dispensa leitura prévia.
 * L37 DynamoDbBalanceWriter: grava o snapshot com um único PutItem condicional, para consumidores concorrentes nunca
 *     trocarem um saldo mais novo por um mais antigo.
 * L47-L53 putIfNewer: condição que falha é o resultado esperado para evento antigo ou duplicado (mensagens fora de
 *     ordem e repetidas), então vira resultado tipado, e não erro.
 * L55-L60 toResult: a recusa da condição devolve o item que a causou (`ReturnValuesOnConditionCheckFailure`), então
 *     distinguir duplicata de evento antigo não custa uma leitura nem quebra a atomicidade; sem o item, o desfecho
 *     conservador é `StaleIgnored`, sem erro. Enunciado: O que será avaliado → Tratamento de concorrência
 *
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.output.BalanceRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure

private const val TIMESTAMP_PLACEHOLDER = ":timestamp"
private const val TRANSACTION_ID_PLACEHOLDER = ":transactionId"

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
        translatingSdkFailures("save the balance snapshot of account ${snapshot.accountId}") {
            putIfNewer(snapshot)
        }

    private fun putIfNewer(snapshot: BalanceSnapshot): SnapshotSaveResult =
        try {
            dynamoDbClient.putItem(putIfNewerRequest(snapshot))
            SnapshotSaveResult.Applied
        } catch (refusal: ConditionalCheckFailedException) {
            refusal.toResult(snapshot)
        }

    private fun ConditionalCheckFailedException.toResult(snapshot: BalanceSnapshot): SnapshotSaveResult =
        if (hasItem()) {
            SnapshotSaveResult.refusedBecauseOf(snapshot.version, item().toBalanceSnapshot().version)
        } else {
            SnapshotSaveResult.StaleIgnored
        }

    private fun putIfNewerRequest(snapshot: BalanceSnapshot): PutItemRequest =
        PutItemRequest
            .builder()
            .tableName(tableName)
            .item(snapshot.toItem())
            .conditionExpression(STORED_VERSION_IS_OLDER)
            .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
            .expressionAttributeValues(
                mapOf(
                    TIMESTAMP_PLACEHOLDER to numberValue(snapshot.version.timestamp.epochMicros.toString()),
                    TRANSACTION_ID_PLACEHOLDER to stringValue(snapshot.version.transactionId.value.toString()),
                ),
            ).build()
}
