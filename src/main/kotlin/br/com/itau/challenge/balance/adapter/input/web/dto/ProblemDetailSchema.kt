/*
 * L12 ProblemDetailSchema: existe só para o OpenAPI mostrar o corpo de erro: o `ProblemDetail` do Spring leva o
 *     `traceId` como membro de extensão, que o springdoc não enxerga na classe. Nunca é instanciado.
 *
 * Enunciado: Referências → API e testes → Documentando APIs com OpenAPI/Swagger
 */
package br.com.itau.challenge.balance.adapter.input.web.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "ProblemDetail", description = "Erro no formato application/problem+json (RFC 9457)")
data class ProblemDetailSchema(
    @field:Schema(example = "about:blank")
    val type: String,
    @field:Schema(example = "Not Found")
    val title: String,
    @field:Schema(example = "404")
    val status: Int,
    @field:Schema(example = "No balance found for account 5b19c8b6-0cc4-4c72-a989-0c2ee15fa975")
    val detail: String,
    @field:Schema(example = "/balances/5b19c8b6-0cc4-4c72-a989-0c2ee15fa975")
    val instance: String,
    @field:Schema(description = "Identificador de correlação da requisição", example = "4bf92f3577b34da6a3ce929d0e0e4736")
    val traceId: String,
)
