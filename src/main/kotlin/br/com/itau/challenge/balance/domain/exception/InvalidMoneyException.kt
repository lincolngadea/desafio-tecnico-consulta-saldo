/*
 * L9 InvalidMoneyException: lançada quando o valor ou a moeda são inválidos, para dado ruim ser rejeitado na
 *     construção do valor em vez de chegar a um snapshot.
 *
 * Enunciado: O que será avaliado → Tratamento de cenários adversos
 */
package br.com.itau.challenge.balance.domain.exception

class InvalidMoneyException(message: String) : IllegalArgumentException(message)
