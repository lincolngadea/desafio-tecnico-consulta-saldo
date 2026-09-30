/*
 * L13 BalanceRepository: grava o snapshot de saldo mais recente de cada conta, para evento duplicado, fora de ordem
 *     ou concorrente nunca sobrescrever um saldo mais novo. O contrato de `saveIfNewer` (retorno e exceções)
 *     continua no KDoc da assinatura.
 *
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
package br.com.itau.challenge.balance.port.output

import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult

fun interface BalanceRepository {
    /**
     * Grava [snapshot] somente se a conta ainda não tem snapshot ou se a versão dele é mais nova que a gravada,
     * conforme `SnapshotVersion`; a verificação e a gravação são uma única operação atômica.
     *
     * @return [SnapshotSaveResult.Applied] quando gravou, e [SnapshotSaveResult.StaleIgnored] quando o snapshot
     * gravado é tão recente quanto o oferecido, ou mais (evento fora de ordem ou duplicado).
     * @throws TransientStorageException quando tentar de novo mais tarde pode dar certo.
     * @throws PermanentStorageException quando tentar de novo não ajuda.
     */
    fun saveIfNewer(snapshot: BalanceSnapshot): SnapshotSaveResult
}
