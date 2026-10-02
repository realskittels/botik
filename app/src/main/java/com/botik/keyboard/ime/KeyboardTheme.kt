package com.botik.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import com.botik.keyboard.Prefs

/** Colors for everything drawn by the keyboard. All values are ARGB ints. */
data class KeyboardTheme(
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
        val DARK = KeyboardTheme(
            isDark = true,
            backgroundTop = 0xFF1A1C23.toInt(),
            backgroundBottom = 0xFF101217.toInt(),
            key = 0xFF2A2E38.toInt(),
            keyPressed = 0xFF3D4352.toInt(),
            keySpecial = 0xFF20232B.toInt(),
            keyShadow = 0xFF0A0B0E.toInt(),
            text = 0xFFF2F4F8.toInt(),
            hint = 0xFF8B93A7.toInt(),
            accent = 0xFF7C5CFF.toInt(),
            accentEnd = 0xFF3FA9F5.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            barText = 0xFFF2F4F8.toInt(),
            barHint = 0xFF7F879A.toInt(),
            chip = 0xFF262A33.toInt(),
            previewBubble = 0xFF3A3F4D.toInt(),
        )

        val LIGHT = KeyboardTheme(
            isDark = false,
            backgroundTop = 0xFFEEF0F5.toInt(),
            backgroundBottom = 0xFFE1E5EC.toInt(),
            key = 0xFFFFFFFF.toInt(),
            keyPressed = 0xFFD9DEE8.toInt(),
            keySpecial = 0xFFCDD3DE.toInt(),
            keyShadow = 0xFFB4BBC8.toInt(),
            text = 0xFF14161C.toInt(),
            hint = 0xFF6F7788.toInt(),
            accent = 0xFF6A4DF5.toInt(),
            accentEnd = 0xFF2F95E8.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            barText = 0xFF14161C.toInt(),
            barHint = 0xFF7A8293.toInt(),
            chip = 0xFFFFFFFF.toInt(),
            previewBubble = 0xFFFFFFFF.toInt(),
        )

        fun resolve(context: Context, prefs: Prefs): KeyboardTheme = when (prefs.theme) {
            "dark" -> DARK
            "light" -> LIGHT
            else -> {
                val night = context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                if (night) DARK else LIGHT
            }
        }
    }
}
