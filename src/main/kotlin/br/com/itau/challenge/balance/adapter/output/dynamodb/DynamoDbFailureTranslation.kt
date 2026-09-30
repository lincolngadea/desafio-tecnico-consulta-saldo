/*
 * L22-L30 translatingSdkFailures: converte toda falha do SDK na exceção transitória ou permanente do port, para
 *     nenhum tipo do SDK aparecer na assinatura do port e quem chama poder decidir se tenta de novo.
 * L37-L43 isTransient: os timeouts vêm primeiro porque também são SdkClientException.
 * L45 hasIoCause: a falha de I/O pode estar em qualquer nível da cadeia de causas, e não só na causa direta.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.port.output.BalanceStorageException
import br.com.itau.challenge.balance.port.output.PermanentStorageException
import br.com.itau.challenge.balance.port.output.TransientStorageException
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException
import software.amazon.awssdk.core.exception.ApiCallTimeoutException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.http.HttpStatusFamily
import java.io.IOException

internal fun <T> translatingSdkFailures(
    operation: String,
    call: () -> T,
): T =
    try {
        call()
    } catch (failure: SdkException) {
        throw failure.toStorageException(operation)
    }

private fun SdkException.toStorageException(operation: String): BalanceStorageException {
    val description = "Failed to $operation: ${message ?: javaClass.simpleName}"
    return if (isTransient()) TransientStorageException(description, this) else PermanentStorageException(description, this)
}

private fun SdkException.isTransient(): Boolean =
    when (this) {
        is ApiCallTimeoutException, is ApiCallAttemptTimeoutException -> true
        is AwsServiceException -> isThrottlingException || HttpStatusFamily.of(statusCode()) == HttpStatusFamily.SERVER_ERROR
        is SdkClientException -> hasIoCause()
        else -> false
    }

private fun Throwable.hasIoCause(): Boolean = generateSequence(cause) { it.cause }.any { it is IOException }
