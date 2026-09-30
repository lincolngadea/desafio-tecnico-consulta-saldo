/*
 * L16 Money: valor em uma moeda ISO 4217, sempre com exatamente a escala das unidades menores da moeda (ex.: `183.1
 *     BRL` vira `183.10`). Valores negativos são válidos: o saldo já vem calculado pelo autorizador.
 * L19-L29 of: casas decimais a mais são rejeitadas, e não arredondadas, para o saldo nunca mudar em silêncio.
 *
 * Enunciado: O que construir → Exposição (API REST) → Contrato de resposta → balance.amount
 */
package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.InvalidMoneyException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

@ConsistentCopyVisibility
data class Money private constructor(val amount: BigDecimal, val currency: Currency) {

    companion object {
        fun of(amount: BigDecimal, currencyCode: String): Money {
            val currency = isoCurrency(currencyCode)
            val minorUnits = currency.defaultFractionDigits
            if (minorUnits < 0) {
                throw InvalidMoneyException("Currency '$currencyCode' has no minor units and cannot hold a balance")
            }
            if (amount.stripTrailingZeros().scale() > minorUnits) {
                throw InvalidMoneyException("Amount $amount has more than $minorUnits decimal places allowed by $currencyCode")
            }
            return Money(amount.setScale(minorUnits, RoundingMode.UNNECESSARY), currency)
        }

        private fun isoCurrency(currencyCode: String): Currency =
            try {
                Currency.getInstance(currencyCode)
            } catch (_: IllegalArgumentException) {
                throw InvalidMoneyException("Currency '$currencyCode' is not an ISO 4217 code")
            }
    }
}
