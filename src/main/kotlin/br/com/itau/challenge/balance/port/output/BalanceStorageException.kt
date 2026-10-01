/*
 * L16 BalanceStorageException: falha do armazenamento de saldo, classificada por poder ou não dar certo tentar de
 *     novo mais tarde. Assim quem chama pode tentar de novo só o que pode dar certo e degradar de forma controlada
 *     com a dependência fora do ar.
 * L18 TransientStorageException: throttling, erro de servidor, timeout ou falha de rede: tentar de novo mais tarde
 *     pode dar certo.
 * L20 PermanentStorageException: requisição inválida, tabela inexistente, acesso negado ou dado gravado malformado:
 *     tentar de novo não ajuda, e só esconderia um problema de configuração ou de dados.
 * L22 StorageUnavailableException: o circuito do repositório está aberto: tentar agora não ajuda, mas mais tarde pode
 *     dar certo. É um tipo próprio para o consumer pausar em vez de gastar tentativas ou mandar o evento para a DLT.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
package br.com.itau.challenge.balance.port.output

sealed class BalanceStorageException(message: String, cause: Throwable) : RuntimeException(message, cause)

class TransientStorageException(message: String, cause: Throwable) : BalanceStorageException(message, cause)

class PermanentStorageException(message: String, cause: Throwable) : BalanceStorageException(message, cause)

class StorageUnavailableException(message: String, cause: Throwable) : BalanceStorageException(message, cause)
