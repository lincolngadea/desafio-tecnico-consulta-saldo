/*
 * L23-L24 RESPONSE_ZONE e UPDATED_AT_FORMAT: o exemplo do enunciado usa o offset de Brasília e três dígitos de
 *     milissegundo fixos; a zona (e não o offset `-03:00`) mantém o offset certo em datas com horário de verão, e
 *     `ISO_OFFSET_DATE_TIME` omitiria os zeros finais da fração.
 * L26-L32 toResponse: só traduz o snapshot para o contrato HTTP, sem decisão de negócio.
 * L34-L40 toResponseInstant: os microssegundos do evento ficam no DynamoDB, onde decidem a ordem; a resposta usa
 *     milissegundos, truncados e não arredondados, para o instante nunca passar o do evento. `updated_at` é o
 *     `transaction.timestamp` do evento que gerou o snapshot. Enunciado: O que construir → Exposição (API REST) →
 *     Contrato de resposta → updated_at
 *
 * Enunciado: O que construir → Exposição (API REST) → Contrato de resposta
 */
package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.adapter.input.web.dto.BalanceResponse
import br.com.itau.challenge.balance.domain.model.BalanceSnapshot
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val RESPONSE_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")
private val UPDATED_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX")

internal fun BalanceSnapshot.toResponse(): BalanceResponse =
    BalanceResponse(
        id = accountId.value,
        owner = ownerId.value,
        balance = BalanceResponse.MoneyResponse(amount = balance.amount, currency = balance.currency.currencyCode),
        updatedAt = version.timestamp.toResponseInstant(),
    )

private fun EventTimestamp.toResponseInstant(): String =
    Instant
        .EPOCH
        .plus(epochMicros, ChronoUnit.MICROS)
        .truncatedTo(ChronoUnit.MILLIS)
        .atZone(RESPONSE_ZONE)
        .format(UPDATED_AT_FORMAT)
