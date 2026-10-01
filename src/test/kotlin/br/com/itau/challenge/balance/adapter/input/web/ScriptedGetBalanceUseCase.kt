/*
 * L19 ScriptedGetBalanceUseCase: fake que honra o contrato do port e deixa o teste roteirizar o resultado, sem
 *     depender de matchers do Mockito com a value class `AccountId`.
 * L35 ScriptedGetBalanceUseCaseConfiguration: substitui o caso de uso real pelo fake com `@Primary`, só nos testes
 *     que importam esta configuração.
 *
 * Spec: n/a (add-balance-query-api design D10)
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.domain.model.AccountId
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.port.input.GetBalanceUseCase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class ScriptedGetBalanceUseCase : GetBalanceUseCase {
    val requestedAccounts = mutableListOf<AccountId>()
    var outcome: () -> BalanceSnapshot? = { null }

    override fun getBalance(accountId: AccountId): BalanceSnapshot? {
        requestedAccounts.add(accountId)
        return outcome()
    }

    fun reset() {
        requestedAccounts.clear()
        outcome = { null }
    }
}

@TestConfiguration
class ScriptedGetBalanceUseCaseConfiguration {

    @Bean
    @Primary
    fun scriptedGetBalanceUseCase(): ScriptedGetBalanceUseCase = ScriptedGetBalanceUseCase()
}
