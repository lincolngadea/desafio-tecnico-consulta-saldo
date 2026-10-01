/*
 * L17-L31 transactionEventJson: monta o payload de exemplo do enunciado, com os campos que variam por teste, para os
 *     testes usarem o contrato real.
 *
 * Spec: n/a (add-transaction-ingestion design D3)
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
package br.com.itau.challenge.balance.adapter.input.kafka

const val EVENT_TRANSACTION_ID = "8e8ae808-b154-48b5-9f3e-553935cc4543"
const val EVENT_ACCOUNT_ID = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975"
const val EVENT_OWNER_ID = "315e3cfe-f4af-4cd2-b298-a449e614349a"
const val EVENT_TIMESTAMP_MICROS = 1751641364589998L
const val EVENT_BALANCE_AMOUNT = "183.12"
const val EVENT_CURRENCY = "BRL"

fun transactionEventJson(
    transactionId: String = EVENT_TRANSACTION_ID,
    status: String = "APPROVED",
    timestampMicros: Long = EVENT_TIMESTAMP_MICROS,
    accountId: String = EVENT_ACCOUNT_ID,
    balanceJson: String = """{"amount": $EVENT_BALANCE_AMOUNT, "currency": "$EVENT_CURRENCY"}""",
): String =
    """
    {
      "transaction": {"id": "$transactionId", "type": "DEBIT", "amount": 100.00, "currency": "BRL",
                      "status": "$status", "timestamp": $timestampMicros},
      "account": {"id": "$accountId", "owner": "$EVENT_OWNER_ID", "created_at": 1634874339000000,
                  "status": "ENABLED", "balance": $balanceJson}
    }
    """.trimIndent()
