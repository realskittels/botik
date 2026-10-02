package com.botik.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import com.botik.keyboard.Prefs

/** Colors for everything drawn by the keyboard. All values are ARGB ints. */
data class KeyboardTheme(
    val id: String,
    val title: String,
    val isDark: Boolean,
    val backgroundTop: Int,
    val backgroundBottom: Int,
    val key: Int,
    val keyPressed: Int,
    val keySpecial: Int,
    val keyShadow: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val accentEnd: Int,
    val onAccent: Int,
    val barText: Int,
    val barHint: Int,
    val chip: Int,
    val previewBubble: Int,
) {
    companion object {
        val AMETHYST = KeyboardTheme(
            id = "dark", title = "Аметист (тёмная)", isDark = true,
            backgroundTop = 0xFF1A1C23.toInt(), backgroundBottom = 0xFF101217.toInt(),
            key = 0xFF2A2E38.toInt(), keyPressed = 0xFF3D4352.toInt(), keySpecial = 0xFF20232B.toInt(),
            keyShadow = 0xFF0A0B0E.toInt(), text = 0xFFF2F4F8.toInt(), hint = 0xFF8B93A7.toInt(),
            accent = 0xFF7C5CFF.toInt(), accentEnd = 0xFF3FA9F5.toInt(), onAccent = 0xFFFFFFFF.toInt(),
            barText = 0xFFF2F4F8.toInt(), barHint = 0xFF7F879A.toInt(), chip = 0xFF262A33.toInt(),
            previewBubble = 0xFF3A3F4D.toInt(),
        )

        val LIGHT = KeyboardTheme(
            id = "light", title = "Светлая", isDark = false,
            backgroundTop = 0xFFEEF0F5.toInt(), backgroundBottom = 0xFFE1E5EC.toInt(),
            key = 0xFFFFFFFF.toInt(), keyPressed = 0xFFD9DEE8.toInt(), keySpecial = 0xFFCDD3DE.toInt(),
            keyShadow = 0xFFB4BBC8.toInt(), text = 0xFF14161C.toInt(), hint = 0xFF6F7788.toInt(),
            accent = 0xFF6A4DF5.toInt(), accentEnd = 0xFF2F95E8.toInt(), onAccent = 0xFFFFFFFF.toInt(),
            barText = 0xFF14161C.toInt(), barHint = 0xFF7A8293.toInt(), chip = 0xFFFFFFFF.toInt(),
            previewBubble = 0xFFFFFFFF.toInt(),
        )

        val OCEAN = KeyboardTheme(
            id = "ocean", title = "Океан", isDark = true,
            backgroundTop = 0xFF0B2233.toInt(), backgroundBottom = 0xFF06131F.toInt(),
            key = 0xFF15354A.toInt(), keyPressed = 0xFF1F4D69.toInt(), keySpecial = 0xFF102A3C.toInt(),
            keyShadow = 0xFF030B12.toInt(), text = 0xFFE6F6FF.toInt(), hint = 0xFF7FA8C2.toInt(),
            accent = 0xFF00B4D8.toInt(), accentEnd = 0xFF48CAE4.toInt(), onAccent = 0xFF04121C.toInt(),
            barText = 0xFFE6F6FF.toInt(), barHint = 0xFF6F98B2.toInt(), chip = 0xFF14324A.toInt(),
            previewBubble = 0xFF24597A.toInt(),
        )

        val SUNSET = KeyboardTheme(
            id = "sunset", title = "Закат", isDark = true,
            backgroundTop = 0xFF2A1621.toInt(), backgroundBottom = 0xFF170C14.toInt(),
            key = 0xFF3B2230.toInt(), keyPressed = 0xFF573246.toInt(), keySpecial = 0xFF2E1A26.toInt(),
            keyShadow = 0xFF0D060A.toInt(), text = 0xFFFFEFF3.toInt(), hint = 0xFFC4919F.toInt(),
            accent = 0xFFFF6B6B.toInt(), accentEnd = 0xFFFFB347.toInt(), onAccent = 0xFF2A0E14.toInt(),
            barText = 0xFFFFEFF3.toInt(), barHint = 0xFFB08593.toInt(), chip = 0xFF3A2130.toInt(),
            previewBubble = 0xFF64394F.toInt(),
        )

        val MINT = KeyboardTheme(
            id = "mint", title = "Мята", isDark = false,
            backgroundTop = 0xFFE8F6F1.toInt(), backgroundBottom = 0xFFD5EEE5.toInt(),
            key = 0xFFFFFFFF.toInt(), keyPressed = 0xFFCBE8DD.toInt(), keySpecial = 0xFFBFE2D5.toInt(),
            keyShadow = 0xFFA6CDBF.toInt(), text = 0xFF10261F.toInt(), hint = 0xFF5E8577.toInt(),
            accent = 0xFF12B886.toInt(), accentEnd = 0xFF38D9A9.toInt(), onAccent = 0xFFFFFFFF.toInt(),
            barText = 0xFF10261F.toInt(), barHint = 0xFF5E8577.toInt(), chip = 0xFFFFFFFF.toInt(),
            previewBubble = 0xFFFFFFFF.toInt(),
        )

        /** Pure black saves battery on OLED screens. */
        val AMOLED = KeyboardTheme(
            id = "amoled", title = "AMOLED (чёрная)", isDark = true,
            backgroundTop = 0xFF000000.toInt(), backgroundBottom = 0xFF000000.toInt(),
            key = 0xFF161616.toInt(), keyPressed = 0xFF2C2C2C.toInt(), keySpecial = 0xFF0E0E0E.toInt(),
            keyShadow = 0xFF000000.toInt(), text = 0xFFF5F5F5.toInt(), hint = 0xFF8A8A8A.toInt(),
            accent = 0xFFB388FF.toInt(), accentEnd = 0xFF8C9EFF.toInt(), onAccent = 0xFF000000.toInt(),
            barText = 0xFFF5F5F5.toInt(), barHint = 0xFF7A7A7A.toInt(), chip = 0xFF161616.toInt(),
            previewBubble = 0xFF2A2A2A.toInt(),
        )

        val ALL = listOf(AMETHYST, OCEAN, SUNSET, MINT, AMOLED, LIGHT)

        /** "system" follows the phone's dark mode: Amethyst at night, Light by day. */
        fun resolve(context: Context, prefs: Prefs): KeyboardTheme =
            ALL.firstOrNull { it.id == prefs.theme } ?: run {
                val night = context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                if (night) AMETHYST else LIGHT
            }
    }
}
