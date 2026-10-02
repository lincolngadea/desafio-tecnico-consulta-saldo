/*
 * L15 ClasspathPackages: usa os pacotes reais dos jars de teste para reconhecer chamadas qualificadas a
 *     funções Kotlin sem nome de tipo, sem confundir cadeias comuns de receivers com dependências.
 *
 * Spec: Núcleo livre de frameworks em cada contexto
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge.architecture

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

object ClasspathPackages {
    val names: Set<String> by lazy {
        System.getProperty("architecture.test.classpath", System.getProperty("java.class.path"))
            .split(File.pathSeparator).asSequence()
            .map { Path.of(it) }
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".jar") }
            .flatMap(::jarPackages)
            .toSet().also { check(it.isNotEmpty()) { "No dependency packages found in the test runtime classpath" } }
    }

    private fun jarPackages(path: Path): Set<String> = JarFile(path.toFile()).use { jar ->
        jar.entries().asSequence()
            .map { it.name }
            .filter { it.endsWith(".class") && it.contains('/') && !it.startsWith("META-INF/") }
            .map { it.substringBeforeLast('/').replace('/', '.') }
            .toSet()
    }
}
