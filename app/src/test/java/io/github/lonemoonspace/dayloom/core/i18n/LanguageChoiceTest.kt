package io.github.lonemoonspace.dayloom.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageChoiceTest {
    @Test
    fun `tags map to choices, regional variants included`() {
        assertEquals(LanguageChoice.ENGLISH, LanguageChoice.fromTag("en"))
        assertEquals(LanguageChoice.ENGLISH, LanguageChoice.fromTag("en-GB"))
        assertEquals(LanguageChoice.CHINESE, LanguageChoice.fromTag("zh-Hans-CN"))
        assertEquals(LanguageChoice.SYSTEM, LanguageChoice.fromTag("nb-NO"))
        assertEquals(LanguageChoice.SYSTEM, LanguageChoice.fromTag(null))
    }
}
