/*
 * L13 SnapshotVersion: a definição autoritativa de qual snapshot de saldo é o mais recente: vence o maior timestamp
 *     do evento, e o empate é resolvido pelo texto canônico em minúsculas do id da transação, comparado
 *     lexicograficamente. Mesmo timestamp e mesmo id de transação significam o mesmo evento: nenhuma versão é mais
 *     nova.
 * L18-L19 ORDER: a ordem textual (e não `UUID.compareTo`, que compara longs com sinal) mantém a regra idêntica a
 *     uma comparação de strings em qualquer armazenamento.
 *
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
package br.com.itau.challenge.balance.domain.model

data class SnapshotVersion(val timestamp: EventTimestamp, val transactionId: TransactionId) : Comparable<SnapshotVersion> {

    override fun compareTo(other: SnapshotVersion): Int = ORDER.compare(this, other)

    private companion object {
        val ORDER: Comparator<SnapshotVersion> =
            compareBy<SnapshotVersion> { it.timestamp }.thenBy { it.transactionId.value.toString() }
    }
}
