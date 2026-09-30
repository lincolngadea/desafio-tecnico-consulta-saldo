/*
 * L23-L26 ids e timestamp: dados de exemplo compartilhados pelos testes do adapter de saldo. São os do payload de
 *     exemplo do enunciado, então os testes usam os valores do próprio contrato.
 * L29-L35 SNAPSHOT: o saldo de exemplo (`183.12 BRL`) é o do mesmo payload do enunciado.
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import br.com.itau.challenge.balance.domain.model.OwnerId
import br.com.itau.challenge.balance.domain.model.SnapshotVersion
import br.com.itau.challenge.balance.domain.model.TransactionId
import software.amazon.awssdk.core.exception.SdkClientException
import java.math.BigDecimal
import java.net.ConnectException
import java.util.UUID

internal const val TABLE_NAME = "AccountBalances"
internal const val ACCOUNT_ID = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975"
internal const val OWNER_ID = "315e3cfe-f4af-4cd2-b298-a449e614349a"
internal const val TRANSACTION_ID = "8e8ae808-b154-48b5-9f3e-553935cc4543"
internal const val EVENT_MICROS = 1751641364589998L
internal const val BAD_REQUEST = 400

internal val SNAPSHOT =
    BalanceSnapshot(
        accountId = AccountId(UUID.fromString(ACCOUNT_ID)),
        ownerId = OwnerId(UUID.fromString(OWNER_ID)),
        balance = Money.of(BigDecimal("183.12"), "BRL"),
        version = SnapshotVersion(EventTimestamp(EVENT_MICROS), TransactionId(UUID.fromString(TRANSACTION_ID))),
    )

internal fun connectionRefused(): SdkClientException =
    SdkClientException
        .builder()
        .message("Unable to execute HTTP request")
        .cause(ConnectException("Connection refused"))
        .build()
