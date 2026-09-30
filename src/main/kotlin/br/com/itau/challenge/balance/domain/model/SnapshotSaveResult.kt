/*
 * L9 SnapshotSaveResult: evento antigo ou duplicado é um resultado esperado, e não um erro, porque este fluxo
 *     recebe mensagens repetidas e fora de ordem com frequência.
 *
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
package br.com.itau.challenge.balance.domain.model

sealed interface SnapshotSaveResult {
    data object Applied : SnapshotSaveResult

    data object StaleIgnored : SnapshotSaveResult
}
