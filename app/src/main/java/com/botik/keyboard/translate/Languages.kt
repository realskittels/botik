package com.botik.keyboard.translate

/**
 * A translation target. [englishName] goes into the prompt, [nativeName] is shown in the UI,
 * [offline] says whether ML Kit has an on-device model for it.
 */
data class Language(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val flag: String,
    val offline: Boolean = true,
)

object Languages {
    val SOURCE = Language("ru", "Русский", "Russian", "🇷🇺")

    val ALL: List<Language> = listOf(
        Language("en", "English", "English", "🇬🇧"),
        Language("de", "Deutsch", "German", "🇩🇪"),
        Language("fr", "Français", "French", "🇫🇷"),
        Language("es", "Español", "Spanish", "🇪🇸"),
        Language("it", "Italiano", "Italian", "🇮🇹"),
        Language("pt", "Português", "Portuguese", "🇵🇹"),
        Language("uk", "Українська", "Ukrainian", "🇺🇦"),
        Language("be", "Беларуская", "Belarusian", "🇧🇾"),
        Language("kk", "Қазақша", "Kazakh", "🇰🇿", offline = false),
        Language("uz", "Oʻzbekcha", "Uzbek", "🇺🇿", offline = false),
        Language("tr", "Türkçe", "Turkish", "🇹🇷"),
        Language("pl", "Polski", "Polish", "🇵🇱"),
        Language("cs", "Čeština", "Czech", "🇨🇿"),
        Language("nl", "Nederlands", "Dutch", "🇳🇱"),
        Language("sv", "Svenska", "Swedish", "🇸🇪"),
        Language("fi", "Suomi", "Finnish", "🇫🇮"),
        Language("el", "Ελληνικά", "Greek", "🇬🇷"),
        Language("he", "עברית", "Hebrew", "🇮🇱"),
        Language("ar", "العربية", "Arabic", "🇸🇦"),
        Language("hi", "हिन्दी", "Hindi", "🇮🇳"),
        Language("zh", "中文", "Simplified Chinese", "🇨🇳"),
        Language("ja", "日本語", "Japanese", "🇯🇵"),
        Language("ko", "한국어", "Korean", "🇰🇷"),
        Language("th", "ไทย", "Thai", "🇹🇭"),
        Language("vi", "Tiếng Việt", "Vietnamese", "🇻🇳"),
        Language("id", "Bahasa Indonesia", "Indonesian", "🇮🇩"),
        Language("ka", "ქართული", "Georgian", "🇬🇪"),
        Language("hy", "Հայերեն", "Armenian", "🇦🇲", offline = false),
        Language("az", "Azərbaycanca", "Azerbaijani", "🇦🇿", offline = false),
    )

    private val byCode = ALL.associateBy { it.code }

    fun find(code: String?): Language = byCode[code] ?: ALL.first()
}

enum class TranslationStyle(val id: String) {
    /** Match the register of the original. */
    AUTO("auto"),
    CASUAL("casual"),
    FORMAL("formal");

    companion object {
        fun from(id: String?): TranslationStyle = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/** Where the "✨ Перевод" button sends text. */
enum class Provider(val id: String, val title: String, val shortName: String) {
    FREE("free", "Бесплатно: нейросеть Pollinations AI + Google, без ключа", "бесплатно"),
    GEMINI("gemini", "Google Gemini: бесплатный ключ", "Gemini"),
    DEEPL("deepl", "DeepL: бесплатный ключ", "DeepL"),
    CLAUDE("claude", "Claude: свой ключ, максимальное качество", "Claude"),
    OFFLINE("offline", "Только офлайн: на телефоне, без интернета", "офлайн");

    companion object {
        fun from(id: String?): Provider? = entries.firstOrNull { it.id == id }
    }
}
