/*
 * L20 AccountIdParserTest: confere que só a forma canônica de 36 caracteres é aceita, incluindo `1-1-1-1-1`, que
 *     `UUID.fromString` aceitaria.
 *
 * Spec: accountId inválido responde 400
 * Enunciado: O que construir → Exposição (API REST) → Contrato de request → accountId
 */
package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.domain.model.AccountId
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val CANONICAL_ACCOUNT_ID = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975"

class AccountIdParserTest {

    @Test
    fun `should parse the account id when the text is a lowercase canonical uuid`() {
        assertEquals(AccountId(UUID.fromString(CANONICAL_ACCOUNT_ID)), parseAccountId(CANONICAL_ACCOUNT_ID))
    }

    @Test
    fun `should parse the same account id when the text is an uppercase canonical uuid`() {
        assertEquals(AccountId(UUID.fromString(CANONICAL_ACCOUNT_ID)), parseAccountId(CANONICAL_ACCOUNT_ID.uppercase()))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "abc",
            "1-1-1-1-1",
            "5b19c8b60cc44c72a9890c2ee15fa975",
            "5b19c8b6-0cc4-4c72-a989-0c2ee15fa97",
            "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975-",
            "5b19c8b6-0cc4-4c72-a989-0c2ee15fa97g",
            " 5b19c8b6-0cc4-4c72-a989-0c2ee15fa975",
            "",
        ],
    )
    fun `should return null when the text is not a canonical uuid`(text: String) {
        assertNull(parseAccountId(text))
    }
}
