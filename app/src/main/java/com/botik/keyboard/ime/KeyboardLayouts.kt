package com.botik.keyboard.ime

enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, SYMBOLS, SYMBOLS_MORE, LETTERS, LANGUAGE }

/**
 * @param alt character typed on long press (also drawn as a small hint).
 * @param weight width relative to a normal key; [FILL] takes the remaining row width.
 */
data class Key(
    val label: String,
    val type: KeyType = KeyType.CHAR,
    val alt: String? = null,
    val weight: Float = 1f,
) {
    val isModifier: Boolean get() = type != KeyType.CHAR && type != KeyType.SPACE

    companion object {
        const val FILL = -1f
    }
}

class KeyboardLayout(val id: String, val rows: List<List<Key>>) {
    /** Number of normal-width keys that fit across; rows narrower than this are centred. */
    val columns: Float = rows.maxOf { row -> row.sumOf { if (it.weight == Key.FILL) 0.0 else it.weight.toDouble() } }.toFloat()
}

object KeyboardLayouts {
    const val RU = "ru"
    const val EN = "en"
    const val SYMBOLS = "sym"
    const val SYMBOLS_MORE = "sym2"

    private val DIGITS = "1234567890"

    /** One key per character of [s]; [alts] maps a character to its long-press alternative. */
    private fun chars(s: String, alts: Map<Char, String> = emptyMap()): List<Key> =
        s.map { c -> Key(c.toString(), alt = alts[c]) }

    /** Pairs each key of [row] with the character at the same position in [alts]. */
    private fun zipAlts(row: String, alts: String): Map<Char, String> =
        row.zip(alts).associate { (c, a) -> c to a.toString() }

    /** Digits as long-press alternatives for a top row. */
    private fun digitAlts(row: String): Map<Char, String> = zipAlts(row, DIGITS)

    private fun shift(weight: Float = 1.5f) = Key("⇧", KeyType.SHIFT, weight = weight)
    private fun delete(weight: Float = 1.5f) = Key("⌫", KeyType.DELETE, weight = weight)

    private fun bottomRow(lettersLabel: String?, comma: String = ",", period: String = "."): List<Key> = buildList {
        if (lettersLabel == null) {
            add(Key("?123", KeyType.SYMBOLS, weight = 1.5f))
            add(Key("🌐", KeyType.LANGUAGE))
        } else {
            add(Key(lettersLabel, KeyType.LETTERS, weight = 1.5f))
        }
        add(Key(comma, alt = if (comma == ",") "!" else null))
        add(Key(" ", KeyType.SPACE, weight = Key.FILL))
        add(Key(period, alt = if (period == ".") "?" else null))
        add(Key("⏎", KeyType.ENTER, weight = 1.5f))
    }

    val russian = KeyboardLayout(
        RU,
        listOf(
            chars("йцукенгшщзх", digitAlts("йцукенгшщз") + mapOf('е' to "ё", 'х' to "ъ")),
            chars("фывапролджэ"),
            // 11 columns like the rows above, so shift and delete are normal width here.
            listOf(shift(1f)) + chars("ячсмитьбю", mapOf('ь' to "ъ")) + listOf(delete(1f)),
            bottomRow(null),
        ),
    )

    val english = KeyboardLayout(
        EN,
        listOf(
            chars("qwertyuiop", digitAlts("qwertyuiop")),
            chars("asdfghjkl", zipAlts("asdfghjkl", "@#\$_&-+()")),
            listOf(shift()) + chars("zxcvbnm", zipAlts("zxcvbnm", "*\"':;!?")) +
                listOf(delete()),
            bottomRow(null),
        ),
    )

    val symbols = KeyboardLayout(
        SYMBOLS,
        listOf(
            chars("1234567890", mapOf('1' to "¹", '2' to "²", '3' to "³", '0' to "°")),
            chars("@#₽_&-+()/", mapOf('₽' to "$", '-' to "—", '+' to "±", '/' to "\\")),
            listOf(Key("=\\<", KeyType.SYMBOLS_MORE, weight = 1.5f)) +
                chars("*\"':;!?", mapOf('*' to "★", '"' to "«", '\'' to "’", '!' to "¡", '?' to "¿")) +
                listOf(delete()),
            bottomRow("АБВ"),
        ),
    )

    val symbolsMore = KeyboardLayout(
        SYMBOLS_MORE,
        listOf(
            chars("~`|•√π÷×¶∆"),
            chars("€$£¥^°={}\\"),
            listOf(Key("?123", KeyType.SYMBOLS, weight = 1.5f)) + chars("%©®™✓[]") + listOf(delete()),
            bottomRow("АБВ", comma = "<", period = ">"),
        ),
    )

    fun byId(id: String): KeyboardLayout = when (id) {
        EN -> english
        SYMBOLS -> symbols
        SYMBOLS_MORE -> symbolsMore
        else -> russian
    }
}
