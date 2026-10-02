package com.botik.keyboard

import com.botik.keyboard.ime.Key
import com.botik.keyboard.ime.KeyType
import com.botik.keyboard.ime.KeyboardLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {
    private val all = listOf(
        KeyboardLayouts.russian,
        KeyboardLayouts.english,
        KeyboardLayouts.symbols,
        KeyboardLayouts.symbolsMore,
    )

    @Test
    fun russianHasAllThirtyThreeLetters() {
        val letters = KeyboardLayouts.russian.rows.flatten()
            .filter { it.type == KeyType.CHAR && it.label.single().isLetter() }
            .flatMap { listOfNotNull(it.label, it.alt?.takeIf { a -> a.single().isLetter() }) }
            .toSet()
        val alphabet = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя".map { it.toString() }.toSet()
        assertEquals(alphabet, letters)
    }

    @Test
    fun russianRowsFitElevenColumns() {
        assertEquals(11f, KeyboardLayouts.russian.columns)
    }

    @Test
    fun noRowIsWiderThanTheLayout() {
        for (layout in all) {
            for (row in layout.rows) {
                val fixed = row.sumOf { if (it.weight == Key.FILL) 0.0 else it.weight.toDouble() }
                assertTrue("${layout.id}: $row", fixed <= layout.columns + 1e-6)
            }
        }
    }

    @Test
    fun everyLayoutHasDeleteAndEnter() {
        for (layout in all) {
            val types = layout.rows.flatten().map { it.type }.toSet()
            assertTrue(layout.id, KeyType.DELETE in types)
            assertTrue(layout.id, KeyType.ENTER in types)
            assertTrue(layout.id, KeyType.SPACE in types)
        }
    }

    @Test
    fun yoIsOnLongPressOfYe() {
        val ye = KeyboardLayouts.russian.rows.flatten().first { it.label == "е" }
        assertEquals("ё", ye.alt)
    }
}
