/*
 * L37 ScriptedBalanceRepository: fake que honra o contrato do port e deixa o teste roteirizar o resultado de cada
 *     chamada.
 * L47 CircuitBreakerBalanceRepositoryTest: usa o circuito criado pela configuração de produção, para provar o que
 *     conta como falha. A espera do estado aberto não é aguardada: `transitionToHalfOpenState` mantém o teste
 *     rápido.
 *
 * Spec: Falhas transitórias do armazenamento abrem o circuito; Circuito aberto falha rápido com exceção do port;
 *     Circuito semiaberto sonda a dependência
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.resilience

import br.com.itau.challenge.balance.adapter.output.dynamodb.SNAPSHOT
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.SnapshotSaveResult
import br.com.itau.challenge.balance.port.output.BalanceRepository
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
import kotlin.test.assertSame

private const val BREAKER_NAME = "balance-storage"
private const val WINDOW_SIZE = 4
private const val FAILURE_RATE_THRESHOLD = 50f
private const val HALF_OPEN_CALLS = 3

private class ScriptedBalanceRepository : BalanceRepository {
    var calls = 0
    var outcome: () -> SnapshotSaveResult = { SnapshotSaveResult.Applied }

    override fun saveIfNewer(snapshot: BalanceSnapshot): SnapshotSaveResult {
        calls++
        return outcome()
    }
}

class CircuitBreakerBalanceRepositoryTest {

    private val delegate = ScriptedBalanceRepository()
    private val circuitBreaker =
        BalanceCircuitBreakerConfig().balanceWriteCircuitBreaker(
            CircuitBreakerProperties(
                failureRateThreshold = FAILURE_RATE_THRESHOLD,
                slidingWindowSize = WINDOW_SIZE,
                waitDurationInOpenState = Duration.ofMinutes(1),
                halfOpenCalls = HALF_OPEN_CALLS,
            ),
            CircuitBreakerRegistry.ofDefaults(),
        )
    private val repository = CircuitBreakerBalanceRepository(delegate, circuitBreaker)

    private fun failTransientlyTimes(times: Int) {
        delegate.outcome = { throw TransientStorageException("throttled", RuntimeException()) }
        repeat(times) { assertFailsWith<TransientStorageException> { repository.saveIfNewer(SNAPSHOT) } }
    }

    @Test
    fun `should open the circuit when the transient failure rate reaches the threshold`() {
        failTransientlyTimes(WINDOW_SIZE)

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
    }

    @Test
    fun `should keep the circuit closed when the repository keeps failing permanently`() {
        delegate.outcome = { throw PermanentStorageException("access denied", RuntimeException()) }

        repeat(WINDOW_SIZE * 2) { assertFailsWith<PermanentStorageException> { repository.saveIfNewer(SNAPSHOT) } }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    fun `should keep the circuit closed when the repository ignores stale snapshots`() {
        delegate.outcome = { SnapshotSaveResult.StaleIgnored }

        val results = List(WINDOW_SIZE * 2) { repository.saveIfNewer(SNAPSHOT) }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertEquals(listOf(SnapshotSaveResult.StaleIgnored), results.distinct())
    }

    @Test
    fun `should keep the circuit closed when the repository ignores duplicated snapshots`() {
        delegate.outcome = { SnapshotSaveResult.DuplicateIgnored }

        val results = List(WINDOW_SIZE * 2) { repository.saveIfNewer(SNAPSHOT) }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertEquals(listOf(SnapshotSaveResult.DuplicateIgnored), results.distinct())
    }

    @Test
    fun `should not reach the repository when the circuit is open`() {
        failTransientlyTimes(WINDOW_SIZE)
        val callsBeforeOpen = delegate.calls

        assertFailsWith<StorageUnavailableException> { repository.saveIfNewer(SNAPSHOT) }

        assertEquals(callsBeforeOpen, delegate.calls)
    }

    @Test
    fun `should fail with a port exception that names the open circuit when the circuit is open`() {
        failTransientlyTimes(WINDOW_SIZE)

        val failure = assertFailsWith<StorageUnavailableException> { repository.saveIfNewer(SNAPSHOT) }

        assertIs<BalanceStorageException>(failure)
        assertContains(failure.message.orEmpty(), BREAKER_NAME)
    }

    @Test
    fun `should close the circuit when all the probe calls succeed in the half open state`() {
        failTransientlyTimes(WINDOW_SIZE)
        circuitBreaker.transitionToHalfOpenState()
        delegate.outcome = { SnapshotSaveResult.Applied }

        val probeResults = List(HALF_OPEN_CALLS) { repository.saveIfNewer(SNAPSHOT) }

        assertEquals(listOf(SnapshotSaveResult.Applied), probeResults.distinct())
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    fun `should stay half open when only part of the probe calls has completed`() {
        failTransientlyTimes(WINDOW_SIZE)
        circuitBreaker.transitionToHalfOpenState()
        delegate.outcome = { throw TransientStorageException("still down", RuntimeException()) }

        assertFailsWith<TransientStorageException> { repository.saveIfNewer(SNAPSHOT) }

        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.state)
    }

    @Test
    fun `should reopen the circuit when all the probe calls fail in the half open state`() {
        failTransientlyTimes(WINDOW_SIZE)
        circuitBreaker.transitionToHalfOpenState()
        delegate.outcome = { throw TransientStorageException("still down", RuntimeException()) }

        repeat(HALF_OPEN_CALLS) { assertFailsWith<TransientStorageException> { repository.saveIfNewer(SNAPSHOT) } }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
        assertFailsWith<StorageUnavailableException> { repository.saveIfNewer(SNAPSHOT) }
    }
}
