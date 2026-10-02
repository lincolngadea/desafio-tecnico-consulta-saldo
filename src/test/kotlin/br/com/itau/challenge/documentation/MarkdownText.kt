/*
 * L28 MarkdownSection: tipo próprio para a seção, para o teste não indexar listas de strings por posição.
 * L30 MarkdownTable: tipo próprio para a tabela, com cabeçalho e linhas separados, pelo mesmo motivo.
 * L32-L44 sectionsAtLevel: ignora linhas dentro de bloco de código, porque um `## ` de exemplo
 *     não é título de seção.
 * L54-L55 makeTargets: só olha trechos de código: na prosa "make" é uma palavra comum
 *     ("o make já vem no Linux").
 * L61-L67 tables: uma tabela só começa onde a linha seguinte é o separador `|-|-|`,
 *     para não confundir com um `|` solto.
 *
 * Spec: Seções obrigatórias do README; Visão da solução com fluxo e arquitetura hexagonal; Como rodar leva do zero à
 *     primeira consulta; Decisões registradas como ADR curto; Estratégia de testes com pirâmide e cobertura;
 *     Resiliência e observabilidade com as métricas a olhar; O que eu faria com mais tempo, com motivador; Como o
 *     repositório foi construído; Referências do README existem no repositório
 * Enunciado: Como começar → Consulte o README do starter-kit
 */
package br.com.itau.challenge.documentation

private const val FENCE = "```"
private val FENCED_BLOCK = Regex("""(?s)```[^\n]*\n(.*?)```""")
private val MERMAID_BLOCK = Regex("""(?s)```mermaid\n(.*?)```""")
private val INLINE_CODE = Regex("""`([^`\n]+)`""")
private val MAKE_TARGET = Regex("""\bmake\s+([a-z][a-z0-9-]*)""")
private val LINK_TARGET = Regex("""\[[^\]]*]\(([^)\s]+)\)""")
private val TABLE_SEPARATOR = Regex("""^\|[\s:|-]+\|$""")
private val BLANK_LINE = Regex("""\n\s*\n""")

data class MarkdownSection(val title: String, val body: String)

data class MarkdownTable(val header: List<String>, val rows: List<List<String>>)

fun String.sectionsAtLevel(level: Int): List<MarkdownSection> {
    val marker = "#".repeat(level) + " "
    val lines = lines()
    val insideFenceAfter = lines.runningFold(false) { inside, line -> if (line.startsWith(FENCE)) !inside else inside }
    val headings = lines.indices.filter { !insideFenceAfter[it + 1] && lines[it].startsWith(marker) }
    return headings.mapIndexed { position, heading ->
        val end = headings.getOrElse(position + 1) { lines.size }
        MarkdownSection(
            title = lines[heading].removePrefix(marker).trim(),
            body = lines.subList(heading + 1, end).joinToString("\n").trim(),
        )
    }
}

fun String.mermaidBlocks(): List<String> = MERMAID_BLOCK.findAll(this).map { it.groupValues[1] }.toList()

fun String.codeFragments(): List<String> {
    val fencedBlocks = FENCED_BLOCK.findAll(this).map { it.groupValues[1] }.toList()
    val inlineCode = INLINE_CODE.findAll(replace(FENCED_BLOCK, "")).map { it.groupValues[1] }.toList()
    return fencedBlocks + inlineCode
}

fun String.makeTargets(): Set<String> =
    codeFragments().flatMap { fragment -> MAKE_TARGET.findAll(fragment).map { it.groupValues[1] } }.toSet()

fun String.linkTargets(): List<String> = LINK_TARGET.findAll(this).map { it.groupValues[1] }.toList()

fun String.paragraphs(): List<String> = split(BLANK_LINE).map { it.trim() }.filter { it.isNotEmpty() }

fun String.tables(): List<MarkdownTable> {
    val lines = lines()
    return lines.indices.filter { startsTable(lines, it) }.map { start ->
        val block = lines.drop(start).takeWhile { it.startsWith("|") }
        MarkdownTable(header = cellsOf(block.first()), rows = block.drop(2).map(::cellsOf))
    }
}

private fun startsTable(lines: List<String>, index: Int): Boolean {
    val isFirstRow = lines[index].startsWith("|") && (index == 0 || !lines[index - 1].startsWith("|"))
    return isFirstRow && index + 1 < lines.size && TABLE_SEPARATOR.matches(lines[index + 1])
}

private fun cellsOf(row: String): List<String> =
    row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
