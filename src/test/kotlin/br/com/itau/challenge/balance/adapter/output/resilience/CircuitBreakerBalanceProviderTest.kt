/*
 * L36 ScriptedBalanceProvider: fake que honra o contrato do port e deixa o teste roteirizar o resultado de cada
 *     chamada.
 * L46 CircuitBreakerBalanceProviderTest: usa o circuito de leitura criado pela configuração de produção, para provar
 *     o que conta como falha, e `null` (conta inexistente) conta como sucesso.
 *
 * Spec: Circuit breaker de leitura com a classificação de erros compartilhada
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.resilience

import br.com.itau.challenge.balance.adapter.output.dynamodb.SNAPSHOT
import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.port.output.BalanceProvider
import br.com.itau.challenge.balance.port.output.BalanceStorageException
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

private const val READ_BREAKER_NAME = "balance-storage-read"
private const val WINDOW_SIZE = 4
private const val FAILURE_RATE_THRESHOLD = 50f
private const val HALF_OPEN_CALLS = 3

private class ScriptedBalanceProvider : BalanceProvider {
    var calls = 0
    var outcome: () -> BalanceSnapshot? = { SNAPSHOT }

    override fun findByAccountId(accountId: AccountId): BalanceSnapshot? {
        calls++
        return outcome()
    }
}

class CircuitBreakerBalanceProviderTest {

    private val delegate = ScriptedBalanceProvider()
    private val circuitBreaker =
        BalanceCircuitBreakerConfig().balanceReadCircuitBreaker(
            CircuitBreakerProperties(
                failureRateThreshold = FAILURE_RATE_THRESHOLD,
                slidingWindowSize = WINDOW_SIZE,
                waitDurationInOpenState = Duration.ofMinutes(1),
                halfOpenCalls = HALF_OPEN_CALLS,
            ),
            CircuitBreakerRegistry.ofDefaults(),
        )
    private val provider = CircuitBreakerBalanceProvider(delegate, circuitBreaker)

    private fun failTransientlyTimes(times: Int) {
        delegate.outcome = { throw TransientStorageException("throttled", RuntimeException()) }
        repeat(times) { assertFailsWith<TransientStorageException> { provider.findByAccountId(SNAPSHOT.accountId) } }
    }

    @Test
    fun `should open the circuit when the transient failure rate reaches the threshold`() {
        failTransientlyTimes(WINDOW_SIZE)

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
    }

    @Test
    fun `should keep the circuit closed when the provider keeps failing permanently`() {
        delegate.outcome = { throw PermanentStorageException("malformed", RuntimeException()) }

        repeat(WINDOW_SIZE * 2) { assertFailsWith<PermanentStorageException> { provider.findByAccountId(SNAPSHOT.accountId) } }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    fun `should keep the circuit closed when the accounts have no snapshot`() {
        delegate.outcome = { null }

        val results = List(WINDOW_SIZE * 2) { provider.findByAccountId(SNAPSHOT.accountId) }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertNull(results.distinct().single())
    }

    @Test
    fun `should return the snapshot of the provider when the circuit is closed`() {
        val snapshot = provider.findByAccountId(SNAPSHOT.accountId)

        assertSame(SNAPSHOT, snapshot)
    }

    @Test
    fun `should not reach the provider when the circuit is open`() {
        failTransientlyTimes(WINDOW_SIZE)
        val callsBeforeOpen = delegate.calls

        assertFailsWith<StorageUnavailableException> { provider.findByAccountId(SNAPSHOT.accountId) }

        assertEquals(callsBeforeOpen, delegate.calls)
    }

    @Test
    fun `should fail with a port exception that names the open circuit when the circuit is open`() {
        failTransientlyTimes(WINDOW_SIZE)

        val failure = assertFailsWith<StorageUnavailableException> { provider.findByAccountId(SNAPSHOT.accountId) }

        assertIs<BalanceStorageException>(failure)
        assertContains(failure.message.orEmpty(), READ_BREAKER_NAME)
    }

    @Test
    fun `should close the circuit when all the probe calls succeed in the half open state`() {
        failTransientlyTimes(WINDOW_SIZE)
        circuitBreaker.transitionToHalfOpenState()
        delegate.outcome = { SNAPSHOT }

        repeat(HALF_OPEN_CALLS) { provider.findByAccountId(SNAPSHOT.accountId) }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }
}
