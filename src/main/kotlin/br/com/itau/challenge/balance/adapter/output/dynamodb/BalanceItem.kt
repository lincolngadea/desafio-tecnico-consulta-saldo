package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import br.com.itau.challenge.balance.domain.model.OwnerId
import br.com.itau.challenge.balance.domain.model.SnapshotVersion
import br.com.itau.challenge.balance.domain.model.TransactionId
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util.UUID

internal const val ACCOUNT_ID_ATTRIBUTE = "accountId"
internal const val OWNER_ID_ATTRIBUTE = "ownerId"
internal const val BALANCE_AMOUNT_ATTRIBUTE = "balanceAmount"
internal const val BALANCE_CURRENCY_ATTRIBUTE = "balanceCurrency"
internal const val LAST_EVENT_TIMESTAMP_ATTRIBUTE = "lastEventTimestamp"
internal const val LAST_TRANSACTION_ID_ATTRIBUTE = "lastTransactionId"

internal fun BalanceSnapshot.toItem(): Map<String, AttributeValue> =
    mapOf(
        ACCOUNT_ID_ATTRIBUTE to stringValue(accountId.value.toString()),
        OWNER_ID_ATTRIBUTE to stringValue(ownerId.value.toString()),
        BALANCE_AMOUNT_ATTRIBUTE to numberValue(balance.amount.toPlainString()),
        BALANCE_CURRENCY_ATTRIBUTE to stringValue(balance.currency.currencyCode),
        LAST_EVENT_TIMESTAMP_ATTRIBUTE to numberValue(version.timestamp.epochMicros.toString()),
        LAST_TRANSACTION_ID_ATTRIBUTE to stringValue(version.transactionId.value.toString()),
    )

// Missing attributes, unparsable values and domain invariant violations all surface as IllegalArgumentException.
internal fun Map<String, AttributeValue>.toBalanceSnapshot(): BalanceSnapshot =
    try {
        BalanceSnapshot(
            accountId = AccountId(uuidOf(ACCOUNT_ID_ATTRIBUTE)),
            ownerId = OwnerId(uuidOf(OWNER_ID_ATTRIBUTE)),
            balance = Money.of(numberOf(BALANCE_AMOUNT_ATTRIBUTE).toBigDecimal(), stringOf(BALANCE_CURRENCY_ATTRIBUTE)),
            version =
                SnapshotVersion(
                    timestamp = EventTimestamp(numberOf(LAST_EVENT_TIMESTAMP_ATTRIBUTE).toLong()),
                    transactionId = TransactionId(uuidOf(LAST_TRANSACTION_ID_ATTRIBUTE)),
                ),
        )
    } catch (malformed: IllegalArgumentException) {
        throw PermanentStorageException("Stored balance item is malformed: ${malformed.message}", malformed)
    }

internal fun accountKey(accountId: AccountId): Map<String, AttributeValue> =
    mapOf(ACCOUNT_ID_ATTRIBUTE to stringValue(accountId.value.toString()))

internal fun stringValue(text: String): AttributeValue = AttributeValue.builder().s(text).build()

internal fun numberValue(number: String): AttributeValue = AttributeValue.builder().n(number).build()

private fun Map<String, AttributeValue>.stringOf(name: String): String =
    requireNotNull(this[name]?.s()) { "string attribute '$name' is missing" }

private fun Map<String, AttributeValue>.numberOf(name: String): String =
    requireNotNull(this[name]?.n()) { "number attribute '$name' is missing" }

private fun Map<String, AttributeValue>.uuidOf(name: String): UUID = UUID.fromString(stringOf(name))
