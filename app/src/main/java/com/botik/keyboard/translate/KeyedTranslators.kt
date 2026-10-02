package com.botik.keyboard.translate

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private fun keyError(e: HttpStatusException, service: String): TranslationException = when (e.code) {
    400 -> TranslationException("$service не поддерживает этот запрос или язык", e)
    401, 403 -> TranslationException("Неверный ключ $service или сервис недоступен в вашем регионе", e)
    429 -> TranslationException("Лимит $service исчерпан, попробуйте позже", e)
    456 -> TranslationException("Месячный лимит $service исчерпан", e)
    else -> TranslationException("Ошибка $service (${e.code})", e)
}

/**
 * Google Gemini with the user's own key from aistudio.google.com (the free tier is enough
 * for a keyboard). Uses the same native-speaker prompt as Claude.
 */
object GeminiTranslator {
    /** Alias Google keeps pointed at its current Flash model. */
    private const val MODEL = "gemini-flash-latest"
    private const val URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    fun translate(apiKey: String, text: String, target: Language, style: TranslationStyle, call: HttpCall): String {
        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", ClaudeTranslator.systemPrompt(target, style)))),
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", "<text>\n$text\n</text>"))),
                ),
            )
        val request = Request.Builder()
            .url(URL)
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(Http.JSON))
            .build()
        val response = try {
            call.execute(request)
        } catch (e: HttpStatusException) {
            throw keyError(e, "Gemini")
        }
        if (call.cancelled) return ""
        val candidates = JSONObject(response).optJSONArray("candidates")
            ?: throw TranslationException("Gemini отказался переводить этот текст")
        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw TranslationException("Gemini вернул пустой ответ")
        val out = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (!part.optBoolean("thought")) out.append(part.optString("text"))
        }
        return FreeTranslator.cleanLlmOutput(out.toString())
    }
}

/**
 * DeepL with the user's own key. "DeepL API Free" keys end in ":fx" and give 500 000
 * characters a month. Not an LLM, but very fluent for European languages.
 */
object DeepLTranslator {
    fun supports(target: Language): Boolean = targetCode(target) != null

    fun translate(apiKey: String, text: String, target: Language, style: TranslationStyle, call: HttpCall): String {
        val code = targetCode(target) ?: throw TranslationException("DeepL не переводит на ${target.nativeName}")
        val host = if (apiKey.endsWith(":fx")) "api-free.deepl.com" else "api.deepl.com"
        val body = JSONObject()
            .put("text", JSONArray().put(text))
            .put("target_lang", code)
        when (style) {
            TranslationStyle.CASUAL -> body.put("formality", "prefer_less")
            TranslationStyle.FORMAL -> body.put("formality", "prefer_more")
            TranslationStyle.AUTO -> Unit
        }
        val request = Request.Builder()
            .url("https://$host/v2/translate")
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .post(body.toString().toRequestBody(Http.JSON))
            .build()
        val response = try {
            call.execute(request)
        } catch (e: HttpStatusException) {
            throw keyError(e, "DeepL")
        }
        if (call.cancelled) return ""
        return JSONObject(response).getJSONArray("translations").getJSONObject(0).getString("text").trim()
    }

    private fun targetCode(target: Language): String? = when (target.code) {
        "en" -> "EN-US"
        "pt" -> "PT-PT"
        "zh" -> "ZH-HANS"
        "de", "fr", "es", "it", "uk", "tr", "pl", "cs", "nl", "sv", "fi", "el", "ja", "ko", "id",
        "ar", "he", "th", "vi",
        -> target.code.uppercase()
        else -> null
    }
}
