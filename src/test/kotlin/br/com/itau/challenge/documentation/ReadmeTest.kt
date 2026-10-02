/*
 * L90 ReadmeTest: protege o README contra desatualização conferindo só o que é mecânico (seções, ADRs,
 *     diagramas, alvos `make`, variáveis, classes e changes citadas); a prosa fica para a revisão independente.
 * L266-L275 should map each enunciado scenario: casa a linha pela primeira coluna da tabela de cenários, porque a
 *     tabela de níveis cita as mesmas palavras na descrição e daria falso positivo.
 * L82 CHANGE_NAME: reconhece nomes de change sem restringir o verbo, para o inventário acompanhar novos
 *     arquivos sem cadastro manual de prefixos (add-architecture-docs design D9).
 * L83 DESIGN_LINK: aceita decisões de changes com qualquer verbo, mantendo a validação do destino e de Dn.
 *
 * Spec: Seções obrigatórias do README; Visão da solução com fluxo e arquitetura hexagonal; Como rodar leva do zero à
 *     primeira consulta; Decisões registradas como ADR curto; Estratégia de testes com pirâmide e cobertura;
 *     Resiliência e observabilidade com as métricas a olhar; O que eu faria com mais tempo, com motivador; Como o
 *     repositório foi construído; Referências do README existem no repositório
 * Enunciado: Como começar → Consulte o README do starter-kit
 */
package br.com.itau.challenge.documentation

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

private val REQUIRED_SECTIONS = listOf(
    "Visão da solução",
    "Como rodar",
    "Decisões de arquitetura",
    "Estratégia de testes",
    "Resiliência e observabilidade",
    "O que eu faria com mais tempo",
    "Como o repositório foi construído",
    "Variáveis de ambiente",
    "Comandos do Makefile",
)
private val RUN_STEPS_IN_ORDER = listOf(
    "make up",
    "make kafka-topic-create",
    "make kafka-produce-transactions-events",
    "/balances/",
)
private val HTTP_STATUSES = listOf("`200`", "`400`", "`404`", "`503`", "`500`")
private val ADR_TOPICS = listOf("snapshot", "DynamoDB", "condicional", "at-least-once", "pausa", "ConsistentRead", "cache")
private val ADR_FIELDS = listOf("Contexto", "Decisão", "Consequência", "Detalhe")
private val ENUNCIADO_AMBIGUITIES = listOf("plural", "updated_at", "404", "DECLINED", "empate")
private val ENUNCIADO_TEST_SCENARIOS =
    listOf("duplicado", "fora de ordem", "conta inexistente", "concorrência", "dependência indisponível")
private val METRICS = listOf(
    "balance_transactions_processed_total",
    "spring_kafka_listener_seconds",
    "kafka_consumer_fetch_manager_records_lag_max",
    "resilience4j_circuitbreaker_state",
    "http_server_requests_seconds",
)
private val LAG_METRIC = METRICS[2]
private val MINIMUM_FUTURE_WORK_ITEMS = listOf(
    "número de sequência",
    "no futuro",
    "reprocessamento",
    "partições",
    "TransactWriteItems",
    "cache",
    "AdminClient",
    "traces",
    "alertas",
    "autenticação",
)
private val OPENSPEC_PARTS = listOf("project.md", "config.yaml", "specs/", "changes/archive/", "CLAUDE.md")
private val WRITE_CLIENT_VARIABLES = listOf(
    "BALANCE_TABLE_NAME",
    "DYNAMODB_CONNECTION_TIMEOUT",
    "DYNAMODB_SOCKET_TIMEOUT",
    "DYNAMODB_API_CALL_ATTEMPT_TIMEOUT",
    "DYNAMODB_API_CALL_TIMEOUT",
    "DYNAMODB_MAX_ATTEMPTS",
)
private val HELLO_ONLY_TARGETS = listOf("`make db-scan`", "`make http`")
private val COMPONENT_NAME = Regex("""\b[A-Z][a-z0-9]+(?:[A-Z][a-z0-9]+)+\b""")
private val TEST_CLASS_REFERENCE = Regex("""`([A-Z]\w*Test)`""")
private val CHANGE_NAME = Regex("""(?<=`)[a-z][a-z0-9]*(?:-[a-z0-9]+)+(?=`)""")
private val DESIGN_LINK = Regex("""\[([a-z][a-z0-9]*(?:-[a-z0-9]+)+) design ([^\]]+)]\(([^)]+)\)""")
private val DECISION_NUMBER = Regex("""\bD(\d+)\b""")
private val ENVIRONMENT_VARIABLE = Regex("""`([A-Z][A-Z0-9_]+)`""")
private val REASON_MARKER = Regex("""porque|pois|motivo""")
private const val DETAIL_LINE_START = "- **Detalhe:**"
private const val DECISION_LINE_START = "- **Decisão:**"

class ReadmeTest {

    private val readme = File("README.md").readText()
    private val facts = RepositoryFacts()
    private val sections = readme.sectionsAtLevel(2).associate { it.title to it.body }

    private fun section(title: String): String = sections[title] ?: fail("README has no section \"$title\"")

    private fun adrs(): List<MarkdownSection> =
        section("Decisões de arquitetura").sectionsAtLevel(3).filter { it.title.startsWith("ADR-") }

    private fun adr(number: Int): MarkdownSection = adrs().first { it.title.startsWith("ADR-$number.") }

    private fun List<MarkdownTable>.rowTexts(): List<String> = flatMap { it.rows }.map { it.joinToString(" | ") }

    @Test
    fun `should have every required section when the readme is read`() {
        val missing = REQUIRED_SECTIONS - sections.keys

        assertTrue(missing.isEmpty(), "missing sections: $missing")
    }

    @Test
    fun `should keep the required sections in the requested order when the readme is read`() {
        val requiredTitlesInReadme = sections.keys.filter { it in REQUIRED_SECTIONS }

        assertEquals(REQUIRED_SECTIONS, requiredTitlesInReadme)
    }

    @Test
    fun `should not keep the candidate instructions of the starter kit when the readme is read`() {
        assertFalse(readme.contains("Instruções para o candidato"))
    }

    @Test
    fun `should draw exactly two mermaid diagrams when the solution overview is read`() {
        val diagrams = section("Visão da solução").mermaidBlocks()

        assertEquals(2, diagrams.size)
    }

    @Test
    fun `should name only components that exist in the code when the diagrams are read`() {
        val diagramText = section("Visão da solução").mermaidBlocks().joinToString("\n")

        val components = COMPONENT_NAME.findAll(diagramText).map { it.value }.toSet()

        assertTrue(components.isNotEmpty())
        assertEquals(emptySet(), components - facts.declaredTypes)
    }

    @Test
    fun `should cite kafka dynamodb and the balance endpoint when the overview paragraph is read`() {
        val overview = section("Visão da solução").paragraphs().first()

        assertContains(overview, "Kafka")
        assertContains(overview, "DynamoDB")
        assertContains(overview, "GET /balances/{accountId}")
    }

    @Test
    fun `should list the run steps in order when the run section is read`() {
        val run = section("Como rodar")

        val positions = RUN_STEPS_IN_ORDER.map { run.indexOf(it) }

        assertTrue(positions.all { it >= 0 }, "missing steps: $positions")
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun `should cite only make targets that exist when the readme is read`() {
        val cited = readme.makeTargets()

        assertTrue(cited.isNotEmpty())
        assertEquals(emptySet(), cited - facts.makeTargets)
    }

    @Test
    fun `should document the five statuses and the error format when the run section is read`() {
        val run = section("Como rodar")

        val statuses = run.tables().flatMap { it.rows }.map { it.first() }

        assertTrue(statuses.containsAll(HTTP_STATUSES), "statuses found: $statuses")
        assertContains(run, "application/problem+json")
        assertContains(run, "Cache-Control: no-store")
    }

    @Test
    fun `should state the event generator limitation when the run section is read`() {
        val run = section("Como rodar")

        assertContains(run, "conta aleatória", ignoreCase = true)
        assertContains(run, "duplicado", ignoreCase = true)
        assertContains(run, "fora de ordem", ignoreCase = true)
        assertContains(run, "Redpanda Console")
    }

    @Test
    fun `should not mention the personal remote docker setup when the readme is read`() {
        assertFalse(readme.contains(".local/"))
        assertFalse(readme.contains("docker-host"))
    }

    @Test
    fun `should have seven numbered adrs about the requested topics when the decisions are read`() {
        val adrs = adrs()

        assertEquals((1..ADR_TOPICS.size).map { "ADR-$it" }, adrs.map { it.title.substringBefore('.') })
        adrs.zip(ADR_TOPICS).forEach { (adr, topic) -> assertContains(adr.title, topic, ignoreCase = true) }
    }

    @Test
    fun `should give every adr context decision consequence and detail when the decisions are read`() {
        adrs().forEach { adr ->
            ADR_FIELDS.forEach { field ->
                val filledField = Regex("""(?m)^- \*\*$field:\*\*\s+\S""")
                assertTrue(filledField.containsMatchIn(adr.body), "${adr.title} has no filled $field")
            }
        }
    }

    @Test
    fun `should point every adr to decisions that exist in the design of an archived change when the details are read`() {
        adrs().forEach { adr ->
            val detail = adr.body.lines().first { it.startsWith(DETAIL_LINE_START) }

            val links = DESIGN_LINK.findAll(detail).toList()

            assertTrue(links.isNotEmpty(), "${adr.title} cites no design decision")
            links.forEach { link ->
                val (change, decisions, designPath) = link.destructured
                assertTrue(change in facts.archivedChanges, "${adr.title} cites an unknown change: $change")
                val design = File(designPath).readText()
                DECISION_NUMBER.findAll(decisions).map { it.groupValues[1] }.forEach { number ->
                    assertContains(design, "### D$number.", message = "${adr.title}: $change has no decision D$number")
                }
            }
        }
    }

    @Test
    fun `should state the condition and the tie break when the conditional write adr is read`() {
        val body = adr(3).body

        assertContains(body, "ConditionExpression")
        assertContains(body, "SnapshotVersion")
        assertContains(body, "maior id", ignoreCase = true)
    }

    @Test
    fun `should say that only permanent errors reach the dead letter topic and dependency failures pause when the dead letter adr is read`() {
        val decision = adr(5).body.lines().first { it.startsWith(DECISION_LINE_START) }

        assertContains(decision, "só erro permanente")
        assertContains(decision, "pausa")
    }

    @Test
    fun `should report the decided enunciado ambiguities when the decisions are read`() {
        val ambiguities = section("Decisões de arquitetura").sectionsAtLevel(3)
            .first { it.title.startsWith("Ambiguidades do enunciado") }

        ENUNCIADO_AMBIGUITIES.forEach { assertContains(ambiguities.body, it) }
    }

    @Test
    fun `should give the command of each test level when the test strategy is read`() {
        val strategy = section("Estratégia de testes")

        assertContains(strategy, "./gradlew check")
        assertContains(strategy, "make integration-test")
    }

    @Test
    fun `should map each enunciado scenario to a test class when the test strategy is read`() {
        val rows = section("Estratégia de testes").tables().flatMap { it.rows }

        ENUNCIADO_TEST_SCENARIOS.forEach { scenario ->
            val row = rows.firstOrNull { it.first().contains(scenario, ignoreCase = true) }

            assertNotNull(row, "no row for $scenario")
            assertTrue(TEST_CLASS_REFERENCE.containsMatchIn(row.joinToString(" | ")), "no test class for $scenario")
        }
    }

    @Test
    fun `should cite only test classes that exist when the readme is read`() {
        val cited = TEST_CLASS_REFERENCE.findAll(readme).map { it.groupValues[1] }.toSet()

        assertTrue(cited.isNotEmpty())
        assertEquals(emptySet(), cited - facts.testClassNames)
    }

    @Test
    fun `should state the coverage gate and the report path when the test strategy is read`() {
        val strategy = section("Estratégia de testes")

        assertContains(strategy, "90%")
        assertContains(strategy, "build/reports/jacoco/test/html/index.html")
    }

    @Test
    fun `should cite the five metrics to watch when the observability section is read`() {
        val observability = section("Resiliência e observabilidade")

        METRICS.forEach { assertContains(observability, it) }
    }

    @Test
    fun `should warn that the lag metric is unreliable when the lag row is read`() {
        val lagRow = section("Resiliência e observabilidade").tables().rowTexts().first { it.contains(LAG_METRIC) }

        assertContains(lagRow, "pausad", ignoreCase = true)
        assertContains(lagRow, "circuito", ignoreCase = true)
    }

    @Test
    fun `should explain why readiness does not depend on the dependencies when the probes are read`() {
        val paragraph = section("Resiliência e observabilidade").paragraphs().first { it.contains("não depende") }

        assertContains(paragraph, "DynamoDB")
        assertContains(paragraph, "Kafka")
        assertTrue(REASON_MARKER.containsMatchIn(paragraph), "no reason given")
    }

    @Test
    fun `should give every future work item a motivator when the table is read`() {
        val table = section("O que eu faria com mais tempo").tables().first { "Item" in it.header && "Motivador" in it.header }

        assertTrue(table.rows.isNotEmpty())
        table.rows.forEach { assertTrue(it[1].isNotBlank(), "no motivator for ${it[0]}") }
    }

    @Test
    fun `should list the minimum future work items when the table is read`() {
        val items = section("O que eu faria com mais tempo").tables().flatMap { it.rows }.map { it.first() }

        MINIMUM_FUTURE_WORK_ITEMS.forEach { keyword ->
            assertTrue(items.any { it.contains(keyword, ignoreCase = true) }, "no item about $keyword")
        }
    }

    @Test
    fun `should explain the openspec folders when the construction section is read`() {
        val construction = section("Como o repositório foi construído")

        OPENSPEC_PARTS.forEach { assertContains(construction, it) }
    }

    @Test
    fun `should list every archived change and cite only existing ones when the construction section is read`() {
        val construction = section("Como o repositório foi construído")

        val cited = CHANGE_NAME.findAll(construction).map { it.value }.toSet()

        assertEquals(emptySet(), facts.archivedChanges - cited)
        assertEquals(emptySet(), cited - facts.archivedChanges - facts.activeChanges)
    }

    @Test
    fun `should link only files that exist when the readme is read`() {
        val localLinks = readme.linkTargets().filterNot { it.startsWith("http") || it.startsWith("#") }

        val missing = localLinks.map { it.substringBefore('#') }.filterNot { facts.exists(it) }

        assertEquals(emptyList(), missing)
    }

    @Test
    fun `should cite the enunciado by section and never link it when the readme is read`() {
        assertContains(readme, "O que será avaliado")
        assertFalse(readme.linkTargets().any { it.contains(".challenge") })
    }

    @Test
    fun `should document only environment variables that exist when the variables table is read`() {
        val documented = documentedVariables()

        assertTrue(documented.isNotEmpty())
        assertEquals(emptyList(), documented.filterNot { facts.environmentSources.contains(it) })
    }

    @Test
    fun `should document the write client variables when the variables table is read`() {
        val documented = documentedVariables()

        assertTrue(documented.containsAll(WRITE_CLIENT_VARIABLES), "missing: ${WRITE_CLIENT_VARIABLES - documented.toSet()}")
    }

    @Test
    fun `should list only make targets that exist when the makefile table is read`() {
        val listed = section("Comandos do Makefile").makeTargets()

        assertTrue(listed.isNotEmpty())
        assertEquals(emptySet(), listed - facts.makeTargets)
    }

    @Test
    fun `should flag the hello only targets when the makefile table is read`() {
        val rows = section("Comandos do Makefile").tables().rowTexts()

        HELLO_ONLY_TARGETS.forEach { target ->
            val row = rows.first { it.contains(target) }

            assertContains(row, "hello")
        }
    }

    private fun documentedVariables(): List<String> = section("Variáveis de ambiente").tables()
        .flatMap { it.rows }
        .flatMap { row -> ENVIRONMENT_VARIABLE.findAll(row.first()).map { it.groupValues[1] } }
}
