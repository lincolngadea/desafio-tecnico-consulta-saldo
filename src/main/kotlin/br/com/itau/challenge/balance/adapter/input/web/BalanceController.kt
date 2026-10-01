/*
 * L16 BalanceController: adapter de entrada: só traduz protocolo, e as ausências viram `400` e `404` por
 *     `ResponseStatusException`, que o advice já converte em `problem+json`. Não decide regra de negócio.
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.adapter.input.web.dto.BalanceResponse
import br.com.itau.challenge.balance.port.input.GetBalanceUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class BalanceController(
    private val getBalanceUseCase: GetBalanceUseCase,
) : BalanceApi {

    override fun getBalance(accountId: String): BalanceResponse {
        val parsedAccountId =
            parseAccountId(accountId)
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId must be a canonical UUID")
        val snapshot =
            getBalanceUseCase.getBalance(parsedAccountId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No balance found for account $accountId")
        return snapshot.toResponse()
    }
}
