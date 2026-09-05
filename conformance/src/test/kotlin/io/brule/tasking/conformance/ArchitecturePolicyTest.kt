package io.brule.tasking.conformance

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Kotlin PSI policy: inspect syntax nodes, never comments or string contents.
 * Type aliases/import aliases and fully qualified names cannot hide top types.
 * Exhaustive Value consumers supply the domain's compile-time type boundary. */
class ArchitecturePolicyTest {
    // Pinned compiler PSI construction only; no K1 semantic analysis is used.
    @OptIn(org.jetbrains.kotlin.K1Deprecation::class, CompilerConfiguration.Internals::class)
    private fun violations(source: String, path: String = "test.kt"): List<String> {
        val disposable = Disposer.newDisposable()
        try {
            val environment = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
            val file = KtPsiFactory(environment.project, false).createFile(source)
            val errors = mutableListOf<String>()
            val approvedBoundary = path == "core/src/main/kotlin/io/brule/tasking/core/YamlValues.kt"
            fun annotationReason(annotation: KtAnnotationEntry): String? {
                val argument = annotation.valueArguments.singleOrNull()?.getArgumentExpression() as? KtStringTemplateExpression ?: return null
                if (argument.entries.any { it !is KtLiteralStringTemplateEntry }) return null
                return argument.entries.joinToString("") { it.text }.takeIf { it.isNotBlank() }
            }
            fun permittedLocal(node: PsiElement): Boolean {
                if (!approvedBoundary) return false
                val function = PsiTreeUtil.getParentOfType(node, KtNamedFunction::class.java) ?: return false
                if (function.bodyExpression?.textRange?.contains(node.textRange) != true) return false
                return generateSequence(node.parent) { it.parent }.filterIsInstance<KtModifierListOwner>().any { owner ->
                    owner.annotationEntries.any { it.shortName?.asString() == "UntypedBoundary" && annotationReason(it) != null }
                }
            }
            file.importDirectives.forEach { imported ->
                if (imported.importedFqName?.asString() in setOf("kotlin.Any", "java.lang.Object")) errors += "top-type import forbidden"
                if (imported.importedFqName?.asString() == "kotlin.Suppress" && imported.aliasName != null) errors += "suppression alias forbidden"
            }
            PsiTreeUtil.collectElementsOfType(file, KtUserType::class.java).forEach { type ->
                if (type.referencedName in setOf("Any", "Object") && !permittedLocal(type)) errors += "untyped type forbidden: ${type.text}"
                if (type.referencedName == "Suppress" && PsiTreeUtil.getParentOfType(type, KtTypeAlias::class.java) != null) errors += "suppression typealias forbidden"
            }
            PsiTreeUtil.collectElementsOfType(file, KtAnnotationEntry::class.java).forEach { annotation ->
                when (annotation.shortName?.asString()) {
                    "Suppress" -> {
                        // Reject all suppressions outside the codec so a const
                        // alias or concatenated annotation argument cannot
                        // conceal UNCHECKED_CAST from the policy.
                        if (!permittedLocal(annotation)) errors += "warning suppression forbidden outside reviewed decoder"
                    }
                    "UntypedBoundary" -> if (!approvedBoundary || annotationReason(annotation) == null) errors += "unapproved or unexplained boundary"
                }
            }
            return errors
        } finally { Disposer.dispose(disposable) }
    }

    @Test fun `repository has no untyped protocol state or unchecked cast escapes`() {
        val root = Path.of(System.getProperty("tasking.root"))
        val errors = mutableListOf<String>()
        for (module in listOf("core", "repository", "compatibility", "cli", "conformance")) {
            Files.walk(root.resolve("$module/src")).use { paths ->
                paths.filter { it.toString().endsWith(".kt") }.sorted().forEach { path ->
                    val relative = root.relativize(path).toString().replace('\\', '/')
                    errors += violations(Files.readString(path), relative).map { "$relative: $it" }
                }
            }
        }
        assertTrue(errors.isEmpty(), errors.joinToString("\n"))
    }

    @Test fun `policy detects generic nullable qualified and aliased escapes without flagging prose`() {
        listOf("val x: Any", "val x: Any?", "val x: Map<String, Any?>", "val x: List<Any?>",
            "typealias Escape = kotlin.Any", "import kotlin.Any as Escape\nval x: Escape",
            "@Suppress(\"UNCHECKED_CAST\") fun x() = Unit",
            "const val ESCAPE = \"UNCHECKED_CAST\"\n@Suppress(ESCAPE) fun x() = Unit",
            "import kotlin.Suppress as Ignore\n@Ignore(\"UNCHECKED_CAST\") fun x() = Unit",
            "@UntypedBoundary(\"convenient\") fun x() = Unit").forEach {
            assertTrue(violations(it).isNotEmpty(), it)
        }
        assertTrue(violations("// Any? is prohibited\nval example = \"Map<String, Any?>\"").isEmpty())
        assertTrue(violations("@UntypedBoundary(\"SnakeYAML\") fun decode(): Any = TODO()",
            "core/src/main/kotlin/io/brule/tasking/core/YamlValues.kt").isNotEmpty())
    }
}
