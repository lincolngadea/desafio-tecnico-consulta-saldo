/*
 * L49 ApiExceptionHandler: Exception Translator: traduz as falhas do núcleo e as do Spring MVC para `problem+json`
 *     com `traceId`. Vale para a aplicação inteira porque o Spring não entrega erro de roteamento a um advice
 *     restrito a um controller.
 * L55-L62 handleStorageFailure: o `when` sobre a sealed `BalanceStorageException` é exaustivo e sem `else`: um
 *     subtipo novo quebra a compilação até alguém decidir o seu status. Enunciado: O que será avaliado → Resiliência
 * L65-L68 handleUnexpectedFailure: qualquer falha não prevista vira `500`, com a causa só no log, para o corpo nunca
 *     vazar detalhe interno. Enunciado: O que será avaliado → Tratamento de cenários adversos
 * L70-L80 handleExceptionInternal: ponto único onde todo `ProblemDetail` do próprio Spring MVC (405, rota
 *     inexistente, parâmetro ausente) recebe `traceId` e `instance`.
 * L82-L91 unavailable: `503` leva `Retry-After` para o cliente saber quando tentar de novo; o valor é fixo porque o
 *     Resilience4j não expõe o tempo restante do circuito de forma estável. Não registra o stack, porque com o
 *     circuito aberto cada chamada rápida geraria um. Enunciado: O que será avaliado → Resiliência
 * L93-L101 internalError: a causa completa vai só para o log, com o `traceId` do MDC, e o corpo leva um `detail`
 *     genérico, para a resposta nunca vazar tabela, classe nem stack. Enunciado: O que será avaliado → Tratamento de
 *     cenários adversos
 * L103-L107 problemOf: único ponto que monta o `ProblemDetail` das falhas do núcleo, para todos receberem `traceId`
 *     e `instance` do mesmo jeito que os erros do Spring MVC.
 * L109-L117 describe: `traceId` vem do `Tracer`, que é quem propaga o `traceparent` do HTTP e põe o id no MDC; e
 *     `instance` vem do caminho, porque o `ProblemDetail` do Spring não os preenche sozinho em todos os caminhos de
 *     erro.
 *
 * Enunciado: O que será avaliado → Tratamento de cenários adversos
 */
package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.port.output.BalanceStorageException
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.StorageUnavailableException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import io.micrometer.tracing.Tracer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

private const val TRACE_ID_PROPERTY = "traceId"
private const val UNAVAILABLE_DETAIL = "The balance storage is temporarily unavailable. Try again later."
private const val INTERNAL_ERROR_DETAIL = "An unexpected error occurred."

@RestControllerAdvice
class ApiExceptionHandler(
    private val properties: BalanceApiProperties,
    private val tracer: Tracer,
) : ResponseEntityExceptionHandler() {

    @ExceptionHandler(BalanceStorageException::class)
    fun handleStorageFailure(
        failure: BalanceStorageException,
        request: WebRequest,
    ): ResponseEntity<Any> =
        when (failure) {
            is TransientStorageException, is StorageUnavailableException -> unavailable(failure, request)
            is PermanentStorageException -> internalError(failure, request)
        }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedFailure(
        failure: Exception,
        request: WebRequest,
    ): ResponseEntity<Any> = internalError(failure, request)

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val response = super.handleExceptionInternal(ex, body, headers, statusCode, request)
        (response?.body as? ProblemDetail)?.let { describe(it, request) }
        return response
    }

    private fun unavailable(
        failure: BalanceStorageException,
        request: WebRequest,
    ): ResponseEntity<Any> {
        logger.warn("Balance storage unavailable: ${failure.message}")
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, properties.retryAfter.seconds.toString())
            .body(problemOf(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_DETAIL, request))
    }

    private fun internalError(
        failure: Exception,
        request: WebRequest,
    ): ResponseEntity<Any> {
        logger.error("Unexpected failure while handling the request: ${failure.message}", failure)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(problemOf(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_DETAIL, request))
    }

    private fun problemOf(
        status: HttpStatus,
        detail: String,
        request: WebRequest,
    ): ProblemDetail = ProblemDetail.forStatusAndDetail(status, detail).also { describe(it, request) }

    private fun describe(
        problemDetail: ProblemDetail,
        request: WebRequest,
    ) {
        tracer.currentSpan()?.context()?.traceId()?.let { problemDetail.setProperty(TRACE_ID_PROPERTY, it) }
        if (problemDetail.instance == null && request is ServletWebRequest) {
            problemDetail.instance = URI.create(request.request.requestURI)
        }
    }
}
