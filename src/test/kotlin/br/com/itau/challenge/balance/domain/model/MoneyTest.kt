/*
 * L16 MoneyTest: arredondar dinheiro em silêncio é bug: o teste garante que casas a mais são rejeitadas, que a
 *     moeda é ISO 4217 e que a escala é normalizada, para o mesmo saldo nunca ter duas representações.
 *
 * Spec: Invariantes dos value objects do snapshot
 * Enunciado: O que construir → Exposição (API REST)
 */
package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.InvalidMoneyException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun `should reject the money when the currency code is outside ISO 4217`() {
        assertFailsWith<InvalidMoneyException> { Money.of(BigDecimal("10.00"), "XYZ") }
    }

    @Test
    fun `should reject the amount when it has more decimal places than the currency allows`() {
        assertFailsWith<InvalidMoneyException> { Money.of(BigDecimal("10.123"), "BRL") }
    }

    @Test
    fun `should reject the money when the currency has no minor units`() {
        assertFailsWith<InvalidMoneyException> { Money.of(BigDecimal("10"), "XAU") }
    }

    @Test
    fun `should normalize the amount to the currency scale when it has fewer decimal places`() {
        val money = Money.of(BigDecimal("183.1"), "BRL")

        assertEquals(BigDecimal("183.10"), money.amount)
        assertEquals(Money.of(BigDecimal("183.10"), "BRL"), money)
    }

    @Test
    fun `should accept the amount when its extra decimal places are trailing zeros`() {
        val money = Money.of(BigDecimal("183.100"), "BRL")

        assertEquals(BigDecimal("183.10"), money.amount)
    }

    @Test
    fun `should accept the amount when it is negative`() {
        val money = Money.of(BigDecimal("-50.25"), "BRL")

        assertEquals(BigDecimal("-50.25"), money.amount)
    }

    @Test
    fun `should expose the ISO 4217 currency code when the currency is valid`() {
        val money = Money.of(BigDecimal("1.00"), "BRL")

        assertEquals("BRL", money.currency.currencyCode)
    }
}
