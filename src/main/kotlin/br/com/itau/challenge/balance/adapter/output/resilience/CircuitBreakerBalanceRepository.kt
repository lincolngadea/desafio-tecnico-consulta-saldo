/*
 * L18 CircuitBreakerBalanceRepository: Decorator que protege o repositório sem o caso de uso saber que o circuit
 *     breaker existe (add-transaction-ingestion design D6).
 * L23-L28 saveIfNewer: traduz `CallNotPermittedException` para `StorageUnavailableException`, para o tipo do
 *     Resilience4j nunca atravessar o port (Art. 1).
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.resilience

import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.output.BalanceRepository
import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker

class CircuitBreakerBalanceRepository(
    private val delegate: BalanceRepository,
    private val circuitBreaker: CircuitBreaker,
) : BalanceRepository {

    override fun saveIfNewer(snapshot: BalanceSnapshot): SnapshotSaveResult =
        try {
            circuitBreaker.executeSupplier { delegate.saveIfNewer(snapshot) }
        } catch (openCircuit: CallNotPermittedException) {
            throw StorageUnavailableException("Circuit breaker '${circuitBreaker.name}' is open", openCircuit)
        }
}
