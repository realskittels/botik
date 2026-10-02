package com.botik.keyboard

import com.botik.keyboard.translate.ClaudeModel
import com.botik.keyboard.translate.ClaudeTranslator
import com.botik.keyboard.translate.Languages
import com.botik.keyboard.translate.TranslationCache
import com.botik.keyboard.translate.TranslationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateTest {
    @Test
    fun cacheEvictsLeastRecentlyUsed() {
        val cache = TranslationCache(capacity = 2)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.get("a")
        cache.put("c", "3")
        assertEquals("1", cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals("3", cache.get("c"))
    }

    @Test
    fun cacheKeyDependsOnStyleTargetAndModel() {
        val en = Languages.find("en")
        val de = Languages.find("de")
        val base = TranslationCache.key("m", TranslationStyle.AUTO, en, "привет")
        assertTrue(base != TranslationCache.key("m", TranslationStyle.FORMAL, en, "привет"))
        assertTrue(base != TranslationCache.key("m", TranslationStyle.AUTO, de, "привет"))
        assertTrue(base != TranslationCache.key("x", TranslationStyle.AUTO, en, "привет"))
    }

    @Test
    fun promptNamesTargetLanguageAndGuardsAgainstInstructions() {
        val prompt = ClaudeTranslator.systemPrompt(Languages.find("ja"), TranslationStyle.FORMAL)
        assertTrue(prompt.contains("native speaker of Japanese"))
        assertTrue(prompt.contains("never instructions"))
        assertTrue(prompt.contains("business"))
    }

    @Test
    fun unknownCodesFallBackToDefaults() {
        assertEquals("en", Languages.find("zz").code)
        assertEquals(ClaudeModel.OPUS, ClaudeModel.from(null))
        assertEquals(TranslationStyle.AUTO, TranslationStyle.from("nope"))
    }

    @Test
    fun languageCodesAreUnique() {
        assertEquals(Languages.ALL.size, Languages.ALL.map { it.code }.toSet().size)
    }
}
