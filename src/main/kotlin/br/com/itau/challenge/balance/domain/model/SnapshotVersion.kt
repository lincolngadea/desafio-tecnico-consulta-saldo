package br.com.itau.challenge.balance.domain.model

/**
 * The authoritative definition of which balance snapshot is the most recent: the greater event timestamp wins,
 * and a timestamp tie is broken by the canonical lowercase text of the transaction id, compared lexicographically.
 * Text order (not `UUID.compareTo`, which compares signed longs) keeps the rule identical to a string comparison
 * in any storage. Equal timestamp and transaction id means the same event: neither version is newer.
 */
data class SnapshotVersion(val timestamp: EventTimestamp, val transactionId: TransactionId) : Comparable<SnapshotVersion> {

    override fun compareTo(other: SnapshotVersion): Int = ORDER.compare(this, other)

    private companion object {
        val ORDER: Comparator<SnapshotVersion> =
            compareBy<SnapshotVersion> { it.timestamp }.thenBy { it.transactionId.value.toString() }
    }
}
