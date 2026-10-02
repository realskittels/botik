package com.botik.keyboard.translate

import android.os.Handler
import android.os.Looper
import com.botik.keyboard.Prefs
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Glue between the keyboard UI and the translators: runs Claude off the main thread,
 * delivers streamed text back on the main thread, cancels stale requests and caches results.
 */
class TranslationEngine(private val prefs: Prefs) {

    interface Callback {
        fun onPartial(text: String)
        fun onDone(text: String)
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newCachedThreadPool { r ->
        Thread(r, "botik-translate").apply { isDaemon = true }
    }
    private val cache = TranslationCache()
    private var claude: ClaudeTranslator? = null
    private var claudeKey: String? = null
    private var current: ClaudeTranslator.Call? = null

    val offline = OfflineTranslator()

    val hasApiKey: Boolean get() = prefs.apiKey.isNotEmpty()

    fun cached(text: String, target: Language): String? =
        cache.get(TranslationCache.key(prefs.model.id, prefs.style, target, text))

    /** Creates (or reuses) the client so the first translation does not pay for setup. */
    fun warmUp() {
        if (hasApiKey) executor.execute { translator() }
    }

    @Synchronized
    private fun translator(): ClaudeTranslator {
        val key = prefs.apiKey
        val model = prefs.model
        val existing = claude
        if (existing != null && claudeKey == key && existing.model == model) return existing
        existing?.close()
        return ClaudeTranslator(key, model).also {
            claude = it
            claudeKey = key
        }
    }

    fun translate(text: String, target: Language, callback: Callback) {
        cancel()
        val style = prefs.style
        val cacheKey = TranslationCache.key(prefs.model.id, style, target, text)
        cache.get(cacheKey)?.let {
            callback.onDone(it)
            return
        }
        if (!hasApiKey) {
            callback.onError("Добавьте API-ключ Claude в приложении Botik")
            return
        }
        val call = ClaudeTranslator.Call()
        current = call
        executor.execute {
            try {
                val result = translator().translate(text, target, style, call) { partial ->
                    main.post { if (!call.cancelled) callback.onPartial(partial) }
                }
                if (call.cancelled) return@execute
                if (result.isBlank()) {
                    main.post { if (!call.cancelled) callback.onError("Пустой ответ, попробуйте ещё раз") }
                    return@execute
                }
                cache.put(cacheKey, result)
                main.post { if (!call.cancelled) callback.onDone(result) }
            } catch (e: TranslationException) {
                main.post { if (!call.cancelled) callback.onError(e.message ?: "Ошибка перевода") }
            }
        }
    }

    fun cancel() {
        current?.cancel()
        current = null
    }

    fun release() {
        cancel()
        offline.close()
        synchronized(this) {
            claude?.close()
            claude = null
        }
        executor.shutdown()
    }
}
