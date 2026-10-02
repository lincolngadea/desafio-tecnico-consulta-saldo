/*
 * L25 KotlinDependencies: usa o parser Kotlin já transitivo de Konsist para que aliases e referências
 *     qualificadas não escapem do guard, sem interpretar comentários ou strings como dependências.
 *
 * Spec: Núcleo livre de frameworks em cada contexto
 * Enunciado: O que será avaliado → Qualidade de código
 */
package br.com.itau.challenge.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective

class KotlinDependencies {
    @OptIn(org.jetbrains.kotlin.K1Deprecation::class)
    companion object {
        private val environment = KotlinCoreEnvironment.createForProduction(
            Disposer.newDisposable(),
            CompilerConfiguration(),
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
        private val factory = KtPsiFactory(environment.project, false)
    }

    private val packages = ClasspathPackages

    fun references(file: KoFileDeclaration, projectRoot: String): Set<String> {
        val syntax = factory.createFile(file.text)
        val references = syntax.importDirectives.mapNotNull { it.importedFqName?.asString() }.toMutableSet()
        syntax.accept(object : KtTreeVisitorVoid() {
            override fun visitImportDirective(directive: KtImportDirective) = Unit

            override fun visitPackageDirective(directive: KtPackageDirective) = Unit

            override fun visitUserType(type: KtUserType) {
                if (type.parent !is KtUserType) {
                    qualifiedType(type)?.takeIf { it.contains('.') && it.first().isLowerCase() }?.let(references::add)
                }
                super.visitUserType(type)
            }

            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                if (expression.parent !is KtDotQualifiedExpression) {
                    qualifiedExpression(expression)?.takeIf { isPackageReference(it, projectRoot) }?.let(references::add)
                }
                super.visitDotQualifiedExpression(expression)
            }
        })
        return references
    }

    private fun qualifiedType(type: KtUserType): String? {
        val name = type.referenceExpression?.getReferencedName() ?: return null
        return type.qualifier?.let { qualifiedType(it)?.let { prefix -> "$prefix.$name" } } ?: name
    }

    private fun qualifiedExpression(expression: KtExpression?): String? = when (expression) {
        is KtNameReferenceExpression -> expression.getReferencedName()
        is KtCallExpression -> qualifiedExpression(expression.calleeExpression)
        is KtDotQualifiedExpression -> qualifiedExpression(expression.receiverExpression)?.let { receiver ->
            qualifiedExpression(expression.selectorExpression)?.let { selector -> "$receiver.$selector" }
        }
        else -> null
    }

    private fun isPackageReference(reference: String, projectRoot: String): Boolean {
        if (reference.startsWith("$projectRoot.") || packages.names.any { reference.startsWith("$it.") }) return true
        val names = reference.split('.')
        val typeIndex = names.indexOfFirst { it.firstOrNull()?.isUpperCase() == true }
        return typeIndex >= 1 && names.take(typeIndex).all { it.firstOrNull()?.isLowerCase() == true }
    }
}
