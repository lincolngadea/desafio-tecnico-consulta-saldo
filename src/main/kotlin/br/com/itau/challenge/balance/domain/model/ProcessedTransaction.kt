/*
 * L10 ProcessedTransaction: é o evento de domínio da ingestão. Não carrega status nem valor da transação: todo
 *     evento, aprovado ou recusado, grava o saldo calculado pelo autorizador (add-transaction-ingestion design D1).
 * L17-L23 toSnapshot: a regra de que evento vira snapshot fica no domínio, e não no adapter nem no serviço.
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.domain.model

data class ProcessedTransaction(
    val transactionId: TransactionId,
    val timestamp: EventTimestamp,
    val accountId: AccountId,
    val ownerId: OwnerId,
    val balance: Money,
) {
    fun toSnapshot(): BalanceSnapshot =
        BalanceSnapshot(
            accountId = accountId,
            ownerId = ownerId,
            balance = balance,
            version = SnapshotVersion(timestamp, transactionId),
        )
}
