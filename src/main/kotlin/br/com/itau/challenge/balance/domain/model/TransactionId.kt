/*
 * L12 TransactionId: identifica o evento que produziu um snapshot. Desempata timestamps iguais, então todas as
 *     instâncias convergem para o mesmo snapshot, qualquer que seja a ordem de chegada.
 *
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
package br.com.itau.challenge.balance.domain.model

import java.util.UUID

@JvmInline
value class TransactionId(val value: UUID)
