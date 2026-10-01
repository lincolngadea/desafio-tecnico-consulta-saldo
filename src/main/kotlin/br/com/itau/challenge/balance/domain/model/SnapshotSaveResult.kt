/*
 * L14 SnapshotSaveResult: evento antigo ou duplicado é um resultado esperado, e não um erro, porque este fluxo
 *     recebe mensagens repetidas e fora de ordem com frequência.
 * L19 DuplicateIgnored: o mesmo evento de novo (reentrega, retentativa ou queda entre gravar e confirmar) é
 *     diferente de um evento fora de ordem: a causa e o tratamento operacional diferem, e a métrica os separa
 *     (add-observability design D6).
 * L22-L25 refusedBecauseOf: a regra de "mesma versão é duplicata, versão armazenada mais nova é antigo" fica no
 *     domínio, e o armazenamento só entrega a versão que recusou a gravação.
 *
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
package br.com.itau.challenge.balance.domain.model

sealed interface SnapshotSaveResult {
    data object Applied : SnapshotSaveResult

    data object StaleIgnored : SnapshotSaveResult

    data object DuplicateIgnored : SnapshotSaveResult

    companion object {
        fun refusedBecauseOf(
            offered: SnapshotVersion,
            stored: SnapshotVersion,
        ): SnapshotSaveResult = if (offered == stored) DuplicateIgnored else StaleIgnored
    }
}
