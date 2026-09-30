/*
 * L9 BalanceSnapshot: o saldo mais recente conhecido de uma conta, calculado pelo autorizador para o evento
 *     identificado por `version`. O serviço mantém esse saldo como recebido e nunca o recalcula.
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.domain.model

data class BalanceSnapshot(
    val accountId: AccountId,
    val ownerId: OwnerId,
    val balance: Money,
    val version: SnapshotVersion,
)
