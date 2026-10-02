/*
 * L21 RepositoryFacts: reúne do repositório o que o README cita, para o `ReadmeTest`
 *     comparar o texto com o que existe.
 * L34-L36 archivedChanges: tira o prefixo de data da pasta, porque o README cita a change só pelo
 *     nome.
 * L38 activeChanges: aceita também a change ainda não arquivada, para o teste passar antes e
 *     depois do `archive`.
 *
 * Spec: Como o repositório foi construído; Referências do README existem no repositório
 * Enunciado: Como começar → Consulte o README do starter-kit
 */
package br.com.itau.challenge.documentation

import java.io.File

private val MAKE_TARGET_DECLARATION = Regex("""(?m)^([a-zA-Z0-9_-]+):""")
private val TYPE_DECLARATION = Regex("""\b(?:class|interface|object)\s+([A-Z]\w*)""")
private val CHANGE_DATE_PREFIX = Regex("""^\d{4}-\d{2}-\d{2}-""")
private const val ARCHIVE_FOLDER = "archive"

class RepositoryFacts(private val root: File = File(".")) {

    val makeTargets: Set<String> = MAKE_TARGET_DECLARATION.findAll(read("Makefile")).map { it.groupValues[1] }.toSet()

    val declaredTypes: Set<String> = kotlinFilesUnder("src/main/kotlin")
        .flatMap { file -> TYPE_DECLARATION.findAll(file.readText()).map { it.groupValues[1] } }
        .toSet()

    val testClassNames: Set<String> =
        (kotlinFilesUnder("src/test/kotlin") + kotlinFilesUnder("src/integrationTest/kotlin"))
            .map { it.nameWithoutExtension }
            .toSet()

    val archivedChanges: Set<String> = folderNames("openspec/changes/$ARCHIVE_FOLDER")
        .map { it.replace(CHANGE_DATE_PREFIX, "") }
        .toSet()

    val activeChanges: Set<String> = folderNames("openspec/changes").filter { it != ARCHIVE_FOLDER }.toSet()

    val environmentSources: String = listOf("src/main/resources/application.yaml", "docker-compose.yml", "Dockerfile")
        .joinToString("\n") { read(it) }

    fun exists(relativePath: String): Boolean = File(root, relativePath).exists()

    private fun read(relativePath: String): String = File(root, relativePath).readText()

    private fun kotlinFilesUnder(relativePath: String): Sequence<File> =
        File(root, relativePath).walkTopDown().filter { it.extension == "kt" }

    private fun folderNames(relativePath: String): List<String> =
        File(root, relativePath).listFiles { file -> file.isDirectory }.orEmpty().map { it.name }
}
