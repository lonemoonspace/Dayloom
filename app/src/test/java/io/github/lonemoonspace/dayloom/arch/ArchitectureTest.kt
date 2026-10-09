package io.github.lonemoonspace.dayloom.arch

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces the layering of design §3.3 by scanning the sources; a violation fails CI. Never relax a rule to make a change pass:
 * fix the dependency instead.
 * 通过扫描源码保证设计文档 §3.3 的分层；违反即 CI 红。不许为了让改动通过而放宽规则，应该修正依赖。
 */
class ArchitectureTest {

    private class Source(val file: File, val layer: String, val feature: String?) {
        val text: String = file.readText()
        val path: String = file.invariantSeparatorsPath
        /** Fully qualified references, from imports or inline. / 完全限定引用，来自 import 或行内。 */
        val references: List<String> = REFERENCE.findAll(text.lineSequence().filterNot { it.startsWith("package ") }.joinToString("\n"))
            .map { it.value }.toList()
    }

    private val sources: List<Source> = listOf("src/main/java", "src/debug/java", "src/release/java")
        .map { File(it, ROOT_PATH) }
        .filter { it.isDirectory }
        .flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                val parts = relative.split('/')
                val layer = if (parts.size == 1) "app" else parts[0]
                Source(file, layer, if (layer == "feature") parts.getOrNull(1) else null)
            }.toList()
        }

    @Test
    fun `the scan finds the sources`() {
        assertTrue("no sources found; is the working directory the module directory?", sources.size > 20)
        assertTrue(sources.any { it.layer == "core" })
        assertTrue(sources.any { it.layer == "app" })
    }

    @Test
    fun `core depends on neither app nor features`() {
        val violations = sources.filter { it.layer == "core" }.flatMap { s ->
            s.references.filter { it.startsWith("$ROOT.app.") || it.startsWith("$ROOT.feature.") }.map { "${s.path}: $it" }
        }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `features depend on neither app nor other features`() {
        val violations = sources.filter { it.layer == "feature" }.flatMap { s ->
            s.references.filter { ref ->
                ref.startsWith("$ROOT.app.") ||
                    (ref.startsWith("$ROOT.feature.") && !ref.startsWith("$ROOT.feature.${s.feature}."))
            }.map { "${s.path}: $it" }
        }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `only the registry files reference feature modules from the app layer`() {
        val violations = sources.filter { it.layer == "app" && it.file.name !in REGISTRY_FILES }.flatMap { s ->
            s.references.filter { it.startsWith("$ROOT.feature.") }.map { "${s.path}: $it" }
        }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `policies stay free of Android so they run as plain JVM tests`() {
        val violations = sources.filter { it.file.name.endsWith("Policy.kt") }.flatMap { s ->
            s.text.lineSequence().filter { it.startsWith("import android.") || it.startsWith("import androidx.") }
                .map { "${s.path}: $it" }.toList()
        }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `only core time reads the system clock or zone`() {
        val violations = sources.filterNot { it.path.contains("/core/time/") }.flatMap { s ->
            SYSTEM_TIME.findAll(s.text).map { "${s.path}: ${it.value}" }.toList()
        }
        assertEquals(emptyList<String>(), violations)
    }

    private companion object {
        const val ROOT = "io.github.lonemoonspace.dayloom"
        const val ROOT_PATH = "io/github/lonemoonspace/dayloom"
        val REFERENCE = Regex("""io\.github\.lonemoonspace\.dayloom(\.[A-Za-z_][A-Za-z0-9_]*)+""")
        val REGISTRY_FILES = setOf("ModuleRegistry.kt", "VariantModules.kt")
        val SYSTEM_TIME = Regex(
            """(ZonedDateTime|LocalDateTime|LocalDate|LocalTime|Instant|OffsetDateTime)\.now\(|ZoneId\.systemDefault\(|System\.currentTimeMillis\(""",
        )
    }
}
