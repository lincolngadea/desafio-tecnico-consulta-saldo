package br.com.itau.challenge.balance.port.output

/** Failure of the balance storage, classified by whether a later retry may succeed. */
sealed class BalanceStorageException(message: String, cause: Throwable) : RuntimeException(message, cause)

/** Throttling, server error, timeout or network failure: retrying later may succeed. */
class TransientStorageException(message: String, cause: Throwable) : BalanceStorageException(message, cause)

/** Invalid request, missing table, denied access or malformed stored data: retrying will not help. */
class PermanentStorageException(message: String, cause: Throwable) : BalanceStorageException(message, cause)
