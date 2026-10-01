/*
 * L26 BalanceApi: concentra a rota e a documentação OpenAPI (os cinco status e o `Retry-After` do `503`) numa
 *     interface, para as anotações não se misturarem ao fluxo do controller; o Spring herda o mapeamento da
 *     interface. Enunciado: Referências → API e testes → Documentando APIs com OpenAPI/Swagger
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.adapter.input.web.dto.BalanceResponse
import br.com.itau.challenge.balance.adapter.input.web.dto.ProblemDetailSchema
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

private const val PROBLEM_JSON = "application/problem+json"

@Tag(name = "Balances", description = "Consulta do saldo mais atual de uma conta")
interface BalanceApi {

    @GetMapping("/balances/{accountId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "Consulta o saldo mais atual de uma conta")
    @ApiResponse(responseCode = "200", description = "Saldo mais atual da conta")
    @ApiResponse(
        responseCode = "400",
        description = "accountId não é um UUID",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetailSchema::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "A conta não tem saldo registrado",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetailSchema::class))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "Armazenamento indisponível; tente de novo depois do intervalo de Retry-After",
        headers = [Header(name = "Retry-After", description = "Segundos até tentar de novo", schema = Schema(type = "integer", example = "5"))],
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetailSchema::class))],
    )
    @ApiResponse(
        responseCode = "500",
        description = "Falha inesperada",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetailSchema::class))],
    )
    fun getBalance(
        @Parameter(description = "Identificador da conta (UUID)", schema = Schema(type = "string", format = "uuid"))
        @PathVariable("accountId")
        accountId: String,
    ): BalanceResponse
}
