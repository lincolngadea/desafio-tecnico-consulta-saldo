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

// Timeouts are checked first because they are also SdkClientException.
private fun SdkException.isTransient(): Boolean =
    when (this) {
        is ApiCallTimeoutException, is ApiCallAttemptTimeoutException -> true
        is AwsServiceException -> isThrottlingException || HttpStatusFamily.of(statusCode()) == HttpStatusFamily.SERVER_ERROR
        is SdkClientException -> hasIoCause()
        else -> false
    }

private fun Throwable.hasIoCause(): Boolean = generateSequence(cause) { it.cause }.any { it is IOException }
