package br.com.itau.challenge.balance.domain.model

/** Outcome of saving a snapshot: stale and duplicate events are an expected result, not an error. */
sealed interface SnapshotSaveResult {
    data object Applied : SnapshotSaveResult

    /** The stored snapshot is as recent as or more recent than the one offered, so nothing changed. */
    data object StaleIgnored : SnapshotSaveResult
}
