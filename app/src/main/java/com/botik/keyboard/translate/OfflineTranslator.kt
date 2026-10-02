package com.botik.keyboard.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * On-device RU→X translation for the live draft shown while typing. Instant and free,
 * but less fluent than Claude. Keeps one translator open (the current target) to save memory.
 * All callbacks arrive on the main thread.
 */
class OfflineTranslator {
    private var translator: Translator? = null
    private var code: String? = null
    private var ready = false
    private var downloading = false

    fun isSupported(target: Language): Boolean =
        target.offline && TranslateLanguage.fromLanguageTag(target.code) != null

    private fun open(target: Language): Translator? {
        if (code == target.code) return translator
        close()
        val mlCode = TranslateLanguage.fromLanguageTag(target.code) ?: return null
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.RUSSIAN)
            .setTargetLanguage(mlCode)
            .build()
        return Translation.getClient(options).also {
            translator = it
            code = target.code
            ready = false
        }
    }

    /**
     * Downloads the ~30 MB model if it is missing. With [wifiOnly] the keyboard never spends
     * mobile data on its own; settings can force the download.
     */
    fun prepare(target: Language, wifiOnly: Boolean, onResult: (Boolean) -> Unit = {}) {
        val t = open(target) ?: return onResult(false)
        if (ready) return onResult(true)
        if (downloading && wifiOnly) return onResult(false)
        downloading = true
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        t.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                downloading = false
                if (translator === t) ready = true
                onResult(true)
            }
            .addOnFailureListener {
                downloading = false
                onResult(false)
            }
    }

    /** Calls [onResult] with the draft, or null when the model is not available yet. */
    fun translate(text: String, target: Language, onResult: (String?) -> Unit) {
        if (!isSupported(target)) return onResult(null)
        val t = open(target) ?: return onResult(null)
        if (!ready) {
            prepare(target, wifiOnly = true)
            return onResult(null)
        }
        t.translate(text)
            .addOnSuccessListener { if (translator === t) onResult(it) }
            .addOnFailureListener { onResult(null) }
    }

    fun close() {
        translator?.close()
        translator = null
        code = null
        ready = false
    }
}
