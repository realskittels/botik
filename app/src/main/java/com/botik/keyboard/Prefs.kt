package com.botik.keyboard

import android.content.Context
import android.content.SharedPreferences
import com.botik.keyboard.translate.ClaudeModel
import com.botik.keyboard.translate.Language
import com.botik.keyboard.translate.Languages
import com.botik.keyboard.translate.Provider
import com.botik.keyboard.translate.TranslationStyle

class Prefs(context: Context) {
    val raw: SharedPreferences =
        context.applicationContext.getSharedPreferences("botik", Context.MODE_PRIVATE)

    var apiKey: String
        get() = raw.getString(KEY_API, "").orEmpty().trim()
        set(v) = raw.edit().putString(KEY_API, v.trim()).apply()

    /** Defaults to Claude for users who set a Claude key before providers existed. */
    var provider: Provider
        get() = Provider.from(raw.getString(KEY_PROVIDER, null))
            ?: if (apiKey.isNotEmpty()) Provider.CLAUDE else Provider.FREE
        set(v) = raw.edit().putString(KEY_PROVIDER, v.id).apply()

    var geminiKey: String
        get() = raw.getString(KEY_GEMINI, "").orEmpty().trim()
        set(v) = raw.edit().putString(KEY_GEMINI, v.trim()).apply()

    var deeplKey: String
        get() = raw.getString(KEY_DEEPL, "").orEmpty().trim()
        set(v) = raw.edit().putString(KEY_DEEPL, v.trim()).apply()

    var model: ClaudeModel
        get() = ClaudeModel.from(raw.getString(KEY_MODEL, null))
        set(v) = raw.edit().putString(KEY_MODEL, v.id).apply()

    var style: TranslationStyle
        get() = TranslationStyle.from(raw.getString(KEY_STYLE, null))
        set(v) = raw.edit().putString(KEY_STYLE, v.id).apply()

    var target: Language
        get() = Languages.find(raw.getString(KEY_TARGET, "en"))
        set(v) {
            val recent = (listOf(v.code) + recentCodes.filter { it != v.code }).take(MAX_RECENT)
            raw.edit()
                .putString(KEY_TARGET, v.code)
                .putString(KEY_RECENT, recent.joinToString(","))
                .apply()
        }

    /** Most recently used targets first; shown at the top of the language panel. */
    val recentCodes: List<String>
        get() = raw.getString(KEY_RECENT, "en,de,es").orEmpty().split(",").filter { it.isNotBlank() }

    var autoReplace: Boolean
        get() = raw.getBoolean(KEY_AUTO_REPLACE, true)
        set(v) = raw.edit().putBoolean(KEY_AUTO_REPLACE, v).apply()

    var livePreview: Boolean
        get() = raw.getBoolean(KEY_LIVE, true)
        set(v) = raw.edit().putBoolean(KEY_LIVE, v).apply()

    var haptics: Boolean
        get() = raw.getBoolean(KEY_HAPTICS, true)
        set(v) = raw.edit().putBoolean(KEY_HAPTICS, v).apply()

    var sound: Boolean
        get() = raw.getBoolean(KEY_SOUND, false)
        set(v) = raw.edit().putBoolean(KEY_SOUND, v).apply()

    /** "system" or a [com.botik.keyboard.ime.KeyboardTheme] id. */
    var theme: String
        get() = raw.getString(KEY_THEME, "system").orEmpty()
        set(v) = raw.edit().putString(KEY_THEME, v).apply()

    var keyHeightScale: Float
        get() = raw.getFloat(KEY_HEIGHT, 1f)
        set(v) = raw.edit().putFloat(KEY_HEIGHT, v).apply()

    var numberRow: Boolean
        get() = raw.getBoolean(KEY_NUMBER_ROW, false)
        set(v) = raw.edit().putBoolean(KEY_NUMBER_ROW, v).apply()

    var clipboardHistory: Boolean
        get() = raw.getBoolean(KEY_CLIPBOARD, true)
        set(v) = raw.edit().putBoolean(KEY_CLIPBOARD, v).apply()

    /** Most recent first. */
    var recentEmoji: List<String>
        get() = raw.getString(KEY_EMOJI, "").orEmpty().split(" ").filter { it.isNotEmpty() }
        set(v) = raw.edit().putString(KEY_EMOJI, v.joinToString(" ")).apply()

    /** "ru" or "en" letters layout last used. */
    var letters: String
        get() = raw.getString(KEY_LETTERS, "ru").orEmpty()
        set(v) = raw.edit().putString(KEY_LETTERS, v).apply()

    companion object {
        const val KEY_API = "api_key"
        const val KEY_PROVIDER = "provider"
        const val KEY_GEMINI = "gemini_key"
        const val KEY_DEEPL = "deepl_key"
        const val KEY_MODEL = "model"
        const val KEY_STYLE = "style"
        const val KEY_TARGET = "target"
        const val KEY_RECENT = "recent"
        const val KEY_AUTO_REPLACE = "auto_replace"
        const val KEY_LIVE = "live_preview"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SOUND = "sound"
        const val KEY_THEME = "theme"
        const val KEY_HEIGHT = "key_height"
        const val KEY_LETTERS = "letters"
        const val KEY_NUMBER_ROW = "number_row"
        const val KEY_CLIPBOARD = "clipboard_history"
        const val KEY_EMOJI = "recent_emoji"
        private const val MAX_RECENT = 6
    }
}
