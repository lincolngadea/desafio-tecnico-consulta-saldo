/*
 * L18 ClasspathPackages: usa os pacotes reais dos jars de teste para reconhecer chamadas qualificadas a
 *     funções Kotlin sem nome de tipo, sem confundir cadeias comuns de receivers com dependências.
 * L30-L32 dependencyClasspath: o Gradle entrega o classpath em arquivo porque, como propriedade de sistema,
 *     ele estoura o limite de linha de comando do Windows; java.class.path atende a execução direta.
 *
 * Spec: Núcleo livre de frameworks em cada contexto
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge.architecture

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.readText

object ClasspathPackages {
    private const val CLASSPATH_FILE_PROPERTY = "architecture.test.classpath.file"

    val names: Set<String> by lazy {
        dependencyClasspath()
            .split(File.pathSeparator).asSequence()
            .map { Path.of(it) }
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".jar") }
            .flatMap(::jarPackages)
            .toSet().also { check(it.isNotEmpty()) { "No dependency packages found in the test runtime classpath" } }
    }

    private fun dependencyClasspath(): String =
        System.getProperty(CLASSPATH_FILE_PROPERTY)?.let { Path.of(it).readText() }
            ?: System.getProperty("java.class.path")

    private fun jarPackages(path: Path): Set<String> = JarFile(path.toFile()).use { jar ->
        jar.entries().asSequence()
            .map { it.name }
            .filter { it.endsWith(".class") && it.contains('/') && !it.startsWith("META-INF/") }
            .map { it.substringBeforeLast('/').replace('/', '.') }
            .toSet()
    }
}
