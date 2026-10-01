/*
 * L61 TIED_WRITES_EVERY: grupos de gravações dividem o mesmo timestamp, para o desempate pelo id da transação também
 *     ser exercitado sob concorrência.
 * L63-L64 LOW_TEXT_ID e HIGH_TEXT_ID: UUIDs cuja ordem como long com sinal (`UUID.compareTo`) diverge da ordem
 *     textual, que é a que o DynamoDB aplica.
 * L66 DynamoDbBalanceIntegrationTest: exercita o adapter de saldo contra uma instância real do DynamoDB Local, com a
 *     tabela `AccountBalances` criada pelo seed (rode com `make integration-test`). Mocks não mostram que a condição
 *     casa com a ordem do domínio nem que ela vale sob gravações concorrentes; a matriz de pares (gravado, recebido)
 *     confere o DynamoDB contra `SnapshotVersion`, caso a caso.
 * L86-L93 `should end with the greatest version when snapshots of the same account are written concurrently`: as
 *     escritas são disparadas ao mesmo tempo por coroutines em `Dispatchers.IO`, liberadas por um portão
 *     (`CompletableDeferred`), para a corrida ser real: um `runBlocking` sozinho usaria uma thread e serializaria as
 *     chamadas bloqueantes do SDK.
 * L190-L195 expectedResultOf: a matriz de pares confere o DynamoDB contra o domínio nos três resultados: mais novo é
 *     `Applied`, igual é `DuplicateIgnored` e o resto é `StaleIgnored`; é aqui que se prova que a recusa devolve o
 *     item (add-observability design D6).
 *
 * Spec: Gravação condicional do snapshot; Leitura do snapshot por conta
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import br.com.itau.challenge.balance.domain.model.OwnerId
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.domain.model.SnapshotVersion
import br.com.itau.challenge.balance.domain.model.TransactionId
import br.com.itau.challenge.hello.adapter.output.dynamodb.DynamoDbConfig
import br.com.itau.challenge.hello.adapter.output.dynamodb.DynamoDbProperties
import br.com.itau.challenge.hello.adapter.output.dynamodb.readProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import java.math.BigDecimal
import java.net.URI
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val ACCOUNT_ID_KEY = "accountId"
private const val ACCOUNT_ID_VALUE_PLACEHOLDER = ":accountId"
private const val STORED_MICROS = 1751641364589998L
private const val CONCURRENT_WRITES = 32
private const val TIED_WRITES_EVERY = 4

private val LOW_TEXT_ID: UUID = UUID.fromString("10000000-0000-4000-8000-000000000000")
private val HIGH_TEXT_ID: UUID = UUID.fromString("80000000-0000-4000-8000-000000000000")

class DynamoDbBalanceIntegrationTest {

    private val tableName = System.getenv("BALANCE_TABLE_NAME") ?: "AccountBalances"

    private val dynamoDbClient: DynamoDbClient = DynamoDbConfig().dynamoDbClient(localProperties())

    private val writer = DynamoDbBalanceWriter(dynamoDbClient, tableName)

    private val provider = DynamoDbBalanceProvider(dynamoDbClient, tableName)

    private val accountId = AccountId(UUID.randomUUID())

    private val ownerId = OwnerId(UUID.randomUUID())

    @AfterEach
    fun deleteAccountSnapshotAndCloseClient() {
        dynamoDbClient.use { it.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(accountKey()).build()) }
    }

    @Test
    fun `should apply the snapshot and read it back when the account has none`() {
        val first = snapshotAt(STORED_MICROS, UUID.randomUUID())

        val result = writer.saveIfNewer(first)

        assertEquals(SnapshotSaveResult.Applied, result)
        assertEquals(first, provider.findByAccountId(accountId))
    }

    @Test
    fun `should read back every field with the currency scale when DynamoDB normalizes the stored number`() {
        val stored = snapshotAt(STORED_MICROS, UUID.randomUUID(), amount = BigDecimal("183.10"))
        writer.saveIfNewer(stored)

        val found = provider.findByAccountId(accountId)

        assertEquals(stored, found)
        assertEquals(BigDecimal("183.10"), found?.balance?.amount)
    }

    @Test
    fun `should return null when the account has no snapshot`() {
        val found = provider.findByAccountId(accountId)

        assertNull(found)
    }

    @Test
    fun `should replace the snapshot when a newer event arrives`() {
        writer.saveIfNewer(snapshotAt(STORED_MICROS, UUID.randomUUID()))
        val newer = snapshotAt(STORED_MICROS + 1, UUID.randomUUID(), amount = BigDecimal("200.00"))

        val result = writer.saveIfNewer(newer)

        assertEquals(SnapshotSaveResult.Applied, result)
        assertEquals(newer, provider.findByAccountId(accountId))
    }

    @Test
    fun `should keep the stored snapshot when an older event arrives out of order`() {
        val stored = snapshotAt(STORED_MICROS, UUID.randomUUID())
        writer.saveIfNewer(stored)

        val result = writer.saveIfNewer(snapshotAt(STORED_MICROS - 1, UUID.randomUUID(), amount = BigDecimal("1.00")))

        assertEquals(SnapshotSaveResult.StaleIgnored, result)
        assertEquals(stored, provider.findByAccountId(accountId))
    }

    @Test
    fun `should keep the item identical when a duplicated message is saved`() {
        val stored = snapshotAt(STORED_MICROS, UUID.randomUUID())
        writer.saveIfNewer(stored)

        val result = writer.saveIfNewer(stored)

        assertEquals(SnapshotSaveResult.DuplicateIgnored, result)
        assertEquals(stored, provider.findByAccountId(accountId))
    }

    @Test
    fun `should apply the snapshot when the timestamp ties and the transaction id is greater`() {
        writer.saveIfNewer(snapshotAt(STORED_MICROS, LOW_TEXT_ID))

        val result = writer.saveIfNewer(snapshotAt(STORED_MICROS, HIGH_TEXT_ID))

        assertEquals(SnapshotSaveResult.Applied, result)
    }

    @Test
    fun `should ignore the snapshot when the timestamp ties and the transaction id is lower`() {
        writer.saveIfNewer(snapshotAt(STORED_MICROS, HIGH_TEXT_ID))

        val result = writer.saveIfNewer(snapshotAt(STORED_MICROS, LOW_TEXT_ID))

        assertEquals(SnapshotSaveResult.StaleIgnored, result)
    }

    @Test
    fun `should keep a single item per account when several snapshots are saved`() {
        (1L..3L).forEach { offset -> writer.saveIfNewer(snapshotAt(STORED_MICROS + offset, UUID.randomUUID())) }

        val itemsOfAccount = dynamoDbClient.query(accountItemsQuery()).count()

        assertEquals(1, itemsOfAccount)
    }

    @Test
    fun `should key the table by the account id partition key alone when the seed creates it`() {
        val keySchema = dynamoDbClient.describeTable(DescribeTableRequest.builder().tableName(tableName).build()).table().keySchema()

        assertEquals(listOf(KeySchemaElement.builder().attributeName(ACCOUNT_ID_KEY).keyType(KeyType.HASH).build()), keySchema)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("versionPairs")
    fun `should classify the result exactly as the domain orders the incoming version against the stored one`(pair: VersionPair) {
        writer.saveIfNewer(snapshotWith(pair.stored))

        val result = writer.saveIfNewer(snapshotWith(pair.incoming))

        assertEquals(expectedResultOf(pair), result)
    }

    private fun expectedResultOf(pair: VersionPair): SnapshotSaveResult =
        when {
            pair.incoming > pair.stored -> SnapshotSaveResult.Applied
            pair.incoming == pair.stored -> SnapshotSaveResult.DuplicateIgnored
            else -> SnapshotSaveResult.StaleIgnored
        }

    @Test
    fun `should end with the greatest version when snapshots of the same account are written concurrently`() {
        val snapshots = (1..CONCURRENT_WRITES).map { index -> snapshotAt(STORED_MICROS + index / TIED_WRITES_EVERY, UUID.randomUUID()) }

        runBlocking(Dispatchers.IO) {
            val startSignal = CompletableDeferred<Unit>()
            val writes =
                snapshots.shuffled().map { snapshot ->
                    async {
                        startSignal.await()
                        writer.saveIfNewer(snapshot)
                    }
                }
            startSignal.complete(Unit)
            writes.awaitAll()
        }

        assertEquals(snapshots.maxBy { it.version }, provider.findByAccountId(accountId))
    }

    private fun snapshotAt(
        epochMicros: Long,
        transactionId: UUID,
        amount: BigDecimal = BigDecimal("183.12"),
    ): BalanceSnapshot = snapshotWith(SnapshotVersion(EventTimestamp(epochMicros), TransactionId(transactionId)), amount)

    private fun snapshotWith(
        version: SnapshotVersion,
        amount: BigDecimal = BigDecimal("183.12"),
    ): BalanceSnapshot = BalanceSnapshot(accountId, ownerId, Money.of(amount, "BRL"), version)

    private fun accountKey(): Map<String, AttributeValue> =
        mapOf(ACCOUNT_ID_KEY to AttributeValue.builder().s(accountId.value.toString()).build())

    private fun accountItemsQuery(): QueryRequest =
        QueryRequest
            .builder()
            .tableName(tableName)
            .keyConditionExpression("$ACCOUNT_ID_KEY = $ACCOUNT_ID_VALUE_PLACEHOLDER")
            .expressionAttributeValues(mapOf(ACCOUNT_ID_VALUE_PLACEHOLDER to accountKey().getValue(ACCOUNT_ID_KEY)))
            .consistentRead(true)
            .build()

    private fun localProperties(): DynamoDbProperties =
        DynamoDbProperties(
            endpoint = URI.create(System.getenv("DYNAMODB_ENDPOINT") ?: "http://localhost:8000"),
            region = System.getenv("DYNAMODB_REGION") ?: "us-east-1",
            timeouts =
                DynamoDbProperties.Timeouts(
                    connection = Duration.ofSeconds(1),
                    socket = Duration.ofSeconds(2),
                    apiCallAttempt = Duration.ofSeconds(2),
                    apiCall = Duration.ofSeconds(5),
                ),
            retry = DynamoDbProperties.Retry(maxAttempts = 3),
            read = readProfile(),
        )

    data class VersionPair(
        val description: String,
        val stored: SnapshotVersion,
        val incoming: SnapshotVersion,
    ) {
        override fun toString(): String = description
    }

    companion object {
        private val STORED_VERSION = versionOf(STORED_MICROS, LOW_TEXT_ID)

        @JvmStatic
        fun versionPairs(): List<VersionPair> =
            listOf(
                VersionPair("older timestamp", STORED_VERSION, versionOf(STORED_MICROS - 1, HIGH_TEXT_ID)),
                VersionPair("newer timestamp", STORED_VERSION, versionOf(STORED_MICROS + 1, LOW_TEXT_ID)),
                VersionPair("duplicate", STORED_VERSION, STORED_VERSION),
                VersionPair("tie with greater id", STORED_VERSION, versionOf(STORED_MICROS, HIGH_TEXT_ID)),
                VersionPair("tie with lower id", versionOf(STORED_MICROS, HIGH_TEXT_ID), STORED_VERSION),
            )

        private fun versionOf(
            epochMicros: Long,
            transactionId: UUID,
        ): SnapshotVersion = SnapshotVersion(EventTimestamp(epochMicros), TransactionId(transactionId))
    }
}
