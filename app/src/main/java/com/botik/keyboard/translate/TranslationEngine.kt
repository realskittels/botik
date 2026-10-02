package com.botik.keyboard.translate

import android.os.Handler
import android.os.Looper
import com.botik.keyboard.Prefs
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Glue between the keyboard UI and the translators: runs the chosen provider off the main
 * thread, delivers results back on the main thread, cancels stale requests and caches results.
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
    /** Cancels the request in flight, whichever translator runs it. */
    private var current: (() -> Unit)? = null

    val offline = OfflineTranslator()

    val provider: Provider get() = prefs.provider

    /** Identifies the provider (and Claude model) in cache keys. */
    private fun engineId(p: Provider): String = if (p == Provider.CLAUDE) prefs.model.id else p.id

    fun cached(text: String, target: Language): String? =
        cache.get(TranslationCache.key(engineId(provider), prefs.style, target, text))

    /** Creates (or reuses) the Claude client so the first translation does not pay for setup. */
    fun warmUp() {
        if (provider == Provider.CLAUDE && prefs.apiKey.isNotEmpty()) executor.execute { claude() }
    }

    @Synchronized
    private fun claude(): ClaudeTranslator {
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
        val p = provider
        val style = prefs.style
        val cacheKey = TranslationCache.key(engineId(p), style, target, text)
        cache.get(cacheKey)?.let {
            callback.onDone(it)
            return
        }

        val missingKey = when (p) {
            Provider.CLAUDE -> prefs.apiKey.isEmpty()
            Provider.GEMINI -> prefs.geminiKey.isEmpty()
            Provider.DEEPL -> prefs.deeplKey.isEmpty()
            Provider.FREE, Provider.OFFLINE -> false
        }
        if (missingKey) {
            callback.onError("Добавьте ключ ${p.shortName} в приложении Botik")
            return
        }
        if (p == Provider.OFFLINE) {
            translateOffline(text, target, cacheKey, callback)
            return
        }

        var cancelled = false
        val claudeCall = ClaudeTranslator.Call()
        val httpCall = HttpCall()
        current = {
            cancelled = true
            claudeCall.cancel()
            httpCall.cancel()
        }
        executor.execute {
            try {
                val result = when (p) {
                    Provider.CLAUDE -> claude().translate(text, target, style, claudeCall) { partial ->
                        main.post { if (!cancelled) callback.onPartial(partial) }
                    }
                    Provider.GEMINI -> GeminiTranslator.translate(prefs.geminiKey, text, target, style, httpCall)
                    Provider.DEEPL ->
                        if (DeepLTranslator.supports(target)) {
                            DeepLTranslator.translate(prefs.deeplKey, text, target, style, httpCall)
                        } else {
                            // DeepL lacks some languages (e.g. Kazakh); use the free engine for those.
                            FreeTranslator.translate(text, target, style, httpCall)
                        }
                    Provider.FREE, Provider.OFFLINE -> FreeTranslator.translate(text, target, style, httpCall)
                }
                if (claudeCall.cancelled || httpCall.cancelled) return@execute
                if (result.isBlank()) {
                    main.post { if (!cancelled) callback.onError("Пустой ответ, попробуйте ещё раз") }
                    return@execute
                }
                cache.put(cacheKey, result)
                main.post { if (!cancelled) callback.onDone(result) }
            } catch (e: TranslationException) {
                main.post { if (!cancelled) callback.onError(e.message ?: "Ошибка перевода") }
            } catch (e: Exception) {
                main.post { if (!cancelled) callback.onError("Не удалось перевести: ${e.message}") }
            }
        }
    }

    /** ML Kit on the phone. The user asked for it explicitly, so the model may download on mobile data. */
    private fun translateOffline(text: String, target: Language, cacheKey: String, callback: Callback) {
        if (!offline.isSupported(target)) {
            callback.onError("Для языка «${target.nativeName}» нет офлайн-модели")
            return
        }
        var cancelled = false
        current = { cancelled = true }
        offline.prepare(target, wifiOnly = false) { ready ->
            if (cancelled) return@prepare
            if (!ready) {
                callback.onError("Не удалось скачать офлайн-модель, нужен интернет один раз")
                return@prepare
            }
            offline.translate(text, target) { result ->
                if (cancelled) return@translate
                if (result.isNullOrBlank()) {
                    callback.onError("Офлайн-перевод не удался")
                } else {
                    cache.put(cacheKey, result)
                    callback.onDone(result)
                }
            }
        }
    }

    fun cancel() {
        current?.invoke()
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
