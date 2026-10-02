/*
 * L13 HexagonalPolicy: descobre o contexto pela estrutura de pacotes para proteger contextos futuros e
 *     mantém o núcleo restrito ao JDK/Kotlin e às camadas internas do próprio contexto.
 *
 * Spec: Núcleo livre de frameworks em cada contexto; Adapters acessam casos de uso por ports;
 *     Dependências internas apontam para dentro do próprio contexto; Verificação descobre todos os bounded contexts
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

class HexagonalPolicy {
    private val root = "br.com.itau.challenge"
    private val layers = setOf("domain", "port", "application", "adapter")
    private val dependencies = KotlinDependencies()
    private val standardPackages = ModuleLayer.boot().modules().filter { it.name.startsWith("java.") }.flatMap { it.packages }.toSet()

    fun contexts(files: List<KoFileDeclaration>): Set<String> = files.mapNotNull { location(it)?.first }.toSet()

    fun violations(files: List<KoFileDeclaration>): List<String> {
        if (contexts(files).isEmpty()) return listOf("No bounded contexts found in production scope")
        return files.flatMap { file ->
            val (context, layer) = location(file) ?: return@flatMap emptyList()
            dependencies.references(file, root).filterNot { allowed(it, context, layer) }
                .map { "${file.path}: $context.$layer depends on $it" }
        }
    }

    private fun location(file: KoFileDeclaration): Pair<String, String>? {
        val name = file.packagee?.name ?: return null
        if (!name.startsWith("$root.")) return null
        val names = name.removePrefix("$root.").split('.')
        return if (names.size >= 2 && names[0] !in setOf("configuration", "infrastructure") && names[1] in layers) {
            names[0] to names[1]
        } else null
    }

    private fun allowed(dependency: String, context: String, layer: String): Boolean {
        if (layer == "adapter") {
            if (dependency.startsWith("$root.configuration")) return false
            if (dependency.startsWith("$root.")) {
                val names = dependency.removePrefix("$root.").split('.')
                if (names.getOrNull(1) == "application") return false
            }
            return true
        }
        if (isStandardLibrary(dependency)) return true
        val innerLayers = when (layer) {
            "domain" -> setOf("domain")
            "port" -> setOf("domain", "port")
            else -> setOf("domain", "port", "application")
        }
        return innerLayers.any { dependency == "$root.$context.$it" || dependency.startsWith("$root.$context.$it.") }
    }

    private fun isStandardLibrary(dependency: String): Boolean {
        if (dependency.startsWith("java.") || dependency.startsWith("kotlin.")) return true
        if (!dependency.startsWith("javax.")) return false
        return standardPackages.any { dependency == it || dependency.startsWith("$it.") }
    }
}
