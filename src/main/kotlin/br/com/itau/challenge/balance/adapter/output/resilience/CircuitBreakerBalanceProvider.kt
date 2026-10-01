/*
 * L19 CircuitBreakerBalanceProvider: Decorator que protege a leitura sem o caso de uso saber que o circuit breaker
 *     existe (add-balance-query-api design D7).
 * L24-L29 findByAccountId: traduz `CallNotPermittedException` para `StorageUnavailableException`, para o tipo do
 *     Resilience4j nunca atravessar o port (Art. 1). O resultado `null` (conta inexistente) conta como sucesso,
 *     porque o `404` é resposta esperada.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.resilience

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.port.output.BalanceProvider
import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker

class CircuitBreakerBalanceProvider(
    private val delegate: BalanceProvider,
    private val circuitBreaker: CircuitBreaker,
) : BalanceProvider {

    override fun findByAccountId(accountId: AccountId): BalanceSnapshot? =
        try {
            circuitBreaker.executeSupplier { delegate.findByAccountId(accountId) }
        } catch (openCircuit: CallNotPermittedException) {
            throw StorageUnavailableException("Circuit breaker '${circuitBreaker.name}' is open", openCircuit)
        }
}
