package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.InvalidMoneyException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/**
 * An amount in an ISO 4217 currency, always carrying exactly the currency's minor-unit scale
 * (e.g. `183.1 BRL` becomes `183.10`). Negative amounts are valid: balances come ready from the authorizer.
 */
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
