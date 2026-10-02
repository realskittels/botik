package com.botik.keyboard.translate

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Free translation without any key.
 *
 * First choice is an LLM through the public Pollinations AI endpoint, prompted the same way as
 * Claude for native-sounding output. If it is rate-limited or down, falls back to the free Google
 * Translate web endpoint. Both are anonymous public services with no uptime or privacy guarantee,
 * which settings and the README tell the user. Blocking: call off the main thread.
 */
object FreeTranslator {
    private const val POLLINATIONS_URL = "https://text.pollinations.ai/openai"
    private const val GOOGLE_URL = "https://translate.googleapis.com/translate_a/single"

    fun translate(text: String, target: Language, style: TranslationStyle, call: HttpCall): String {
        val llmError = try {
            val result = llm(text, target, style, call)
            if (result.isNotBlank() || call.cancelled) return result
            null
        } catch (e: Exception) {
            if (call.cancelled) return ""
            e
        }
        return try {
            google(text, target, call)
        } catch (e: Exception) {
            if (call.cancelled) return ""
            throw TranslationException("Бесплатный перевод сейчас недоступен, проверьте интернет", llmError ?: e)
        }
    }

    private fun llm(text: String, target: Language, style: TranslationStyle, call: HttpCall): String {
        val body = JSONObject()
            .put("model", "openai")
            .put("private", true)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", ClaudeTranslator.systemPrompt(target, style)))
                    .put(JSONObject().put("role", "user").put("content", "<text>\n$text\n</text>")),
            )
        val request = Request.Builder()
            .url(POLLINATIONS_URL)
            .post(body.toString().toRequestBody(Http.JSON))
            .build()
        val response = call.execute(request)
        if (call.cancelled) return ""
        val content = JSONObject(response)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        return cleanLlmOutput(content)
    }

    /** The free web endpoint behind translate.google.com; good literal quality, less idiomatic. */
    fun google(text: String, target: Language, call: HttpCall): String {
        val url = GOOGLE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", googleCode(target))
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", text)
            .build()
        val response = call.execute(Request.Builder().url(url).get().build())
        if (call.cancelled) return ""
        // [[["translated", "original", ...], ...], ...]
        val segments = JSONArray(response).getJSONArray(0)
        val out = StringBuilder()
        for (i in 0 until segments.length()) {
            val segment = segments.optJSONArray(i) ?: continue
            out.append(segment.optString(0))
        }
        return out.toString().trim()
    }

    private fun googleCode(target: Language): String = when (target.code) {
        "zh" -> "zh-CN"
        "he" -> "iw"
        else -> target.code
    }

    /** Strips what free LLM endpoints sometimes wrap around the answer: echoed tags and a sponsor block. */
    fun cleanLlmOutput(raw: String): String {
        var s = raw.trim()
        val sponsor = Regex("""\n\s*(---|\*\*Sponsor|🌸)""").find(s)
        if (sponsor != null && s.substring(sponsor.range.first).contains("pollinations", ignoreCase = true)) {
            s = s.substring(0, sponsor.range.first).trim()
        }
        return s.removePrefix("<text>").removeSuffix("</text>").trim()
    }
}
