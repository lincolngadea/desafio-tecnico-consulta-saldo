/*
 * L16 NoCredentialsInCodeTest: varre o código de produção atrás dos tipos de credencial estática do SDK, porque a
 *     regra é ausência de credencial escrita, e só uma varredura prova uma ausência.
 *
 * Spec: Configuração só por variável de ambiente
 * Enunciado: O que será avaliado → Production readiness
 */
package br.com.itau.challenge.observability

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

private val CREDENTIAL_LITERALS = listOf("AwsBasicCredentials", "StaticCredentialsProvider", "AwsSessionCredentials")

class NoCredentialsInCodeTest {

    @Test
    fun `should not write any aws credential in the production code`() {
        val offenders =
            File("src/main/kotlin")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file -> CREDENTIAL_LITERALS.any { file.readText().contains(it) } }
                .map { it.path }
                .toList()

        assertTrue(offenders.isEmpty(), "credentials written in code: $offenders")
    }
}
