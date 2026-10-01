/*
 * L22 OpenApiDocumentationTest: lê a especificação gerada em `/v3/api-docs`, e não um arquivo escrito à mão, para
 *     provar que ela reflete o código.
 *
 * Spec: Documentação OpenAPI gerada a partir do código
 * Enunciado: Referências → API e testes → Documentando APIs com OpenAPI/Swagger
 */
package br.com.itau.challenge.balance.adapter.input.web

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

private const val BALANCE_OPERATION = "$.paths['/balances/{accountId}'].get"

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `should serve the swagger ui`() {
        mockMvc.get("/swagger-ui/index.html").andExpect {
            status { isOk() }
            content { string(containsString("swagger")) }
        }
    }

    @Test
    fun `should describe the balance path with a uuid account id parameter`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$BALANCE_OPERATION.parameters[0].name") { value("accountId") }
            jsonPath("$BALANCE_OPERATION.parameters[0].in") { value("path") }
            jsonPath("$BALANCE_OPERATION.parameters[0].schema.format") { value("uuid") }
        }
    }

    @Test
    fun `should describe the five responses of the balance query`() {
        mockMvc.get("/v3/api-docs").andExpect {
            jsonPath("$BALANCE_OPERATION.responses['200']") { exists() }
            jsonPath("$BALANCE_OPERATION.responses['400']") { exists() }
            jsonPath("$BALANCE_OPERATION.responses['404']") { exists() }
            jsonPath("$BALANCE_OPERATION.responses['503']") { exists() }
            jsonPath("$BALANCE_OPERATION.responses['500']") { exists() }
        }
    }

    @Test
    fun `should declare the retry after header in the 503 response`() {
        mockMvc.get("/v3/api-docs").andExpect {
            jsonPath("$BALANCE_OPERATION.responses['503'].headers['Retry-After']") { exists() }
        }
    }

    @Test
    fun `should describe the success schema with the contract fields`() {
        mockMvc.get("/v3/api-docs").andExpect {
            jsonPath("$.components.schemas.BalanceResponse.properties.id") { exists() }
            jsonPath("$.components.schemas.BalanceResponse.properties.owner") { exists() }
            jsonPath("$.components.schemas.BalanceResponse.properties.balance") { exists() }
            jsonPath("$.components.schemas.BalanceResponse.properties.updated_at") { exists() }
            jsonPath("$.components.schemas.MoneyResponse.properties.amount") { exists() }
            jsonPath("$.components.schemas.MoneyResponse.properties.currency") { exists() }
        }
    }

    @Test
    fun `should describe the error schema with the trace id`() {
        mockMvc.get("/v3/api-docs").andExpect {
            jsonPath("$.components.schemas.ProblemDetail.properties.traceId") { exists() }
        }
    }

    @Test
    fun `should keep the hello path documented`() {
        mockMvc.get("/v3/api-docs").andExpect { jsonPath("$.paths['/hello']") { exists() } }
    }
}
