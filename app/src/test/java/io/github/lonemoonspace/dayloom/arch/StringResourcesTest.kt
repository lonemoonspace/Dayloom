package io.github.lonemoonspace.dayloom.arch

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * English and Chinese resources must have exactly the same keys and the same placeholders per key, in every source set.
 * Lint catches missing translations too, but this also checks placeholders, which lint does not.
 * 每个源集里中英文资源的键必须完全一致，每个键的占位符也必须一致。Lint 也会查缺失的翻译，但不查占位符，所以这里一起检查。
 */
class StringResourcesTest {

    private data class Entry(val placeholders: List<String>, val translatable: Boolean)

    private fun read(file: File): Map<String, Entry> {
        if (!file.isFile) return emptyMap()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = mutableMapOf<String, Entry>()
        for (tag in listOf("string", "plurals")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                val name = element.getAttribute("name")
                val prefix = if (tag == "plurals") "plurals:" else ""
                result["$prefix$name"] = Entry(
                    placeholders = PLACEHOLDER.findAll(element.textContent).map { it.value }.toList().sorted().distinct(),
                    translatable = element.getAttribute("translatable") != "false",
                )
            }
        }
        return result
    }

    @Test
    fun `every source set has identical keys and placeholders in English and Chinese`() {
        val sourceSets = listOf("src/main/res", "src/debug/res").map(::File).filter { it.isDirectory }
        assertTrue("no resources found; is the working directory the module directory?", sourceSets.isNotEmpty())
        for (res in sourceSets) {
            val english = read(File(res, "values/strings.xml")).filterValues { it.translatable }
            val chinese = read(File(res, "values-zh/strings.xml"))
            assertEquals("keys in $res", english.keys.sorted(), chinese.keys.sorted())
            english.forEach { (key, entry) ->
                assertEquals("placeholders of $key in $res", entry.placeholders, chinese.getValue(key).placeholders)
            }
        }
    }

    private companion object {
        val PLACEHOLDER = Regex("""%(\d+\$)?[sdf]""")
    }
}
