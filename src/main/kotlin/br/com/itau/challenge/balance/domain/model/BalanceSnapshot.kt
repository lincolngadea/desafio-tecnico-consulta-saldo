package br.com.itau.challenge.balance.domain.model

/** The latest known balance of an account, as calculated by the authorizer for the event identified by [version]. */
data class BalanceSnapshot(
    val accountId: AccountId,
    val ownerId: OwnerId,
    val balance: Money,
    val version: SnapshotVersion,
)
