package com.botik.keyboard.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers what the user copies while Botik is the active keyboard. Pinned clips are kept
 * forever; the rest are trimmed to [MAX_UNPINNED]. Clips that apps mark as sensitive
 * (password managers do) are never stored.
 */
class ClipboardHistory(context: Context) {

    data class Clip(val text: String, val pinned: Boolean, val time: Long)

    private val prefs = context.applicationContext.getSharedPreferences("botik_clipboard", Context.MODE_PRIVATE)
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var clips: MutableList<Clip> = load()
    var enabled = true

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capture() }

    fun start() = clipboard.addPrimaryClipChangedListener(listener)

    fun stop() = clipboard.removePrimaryClipChangedListener(listener)

    /** Pinned first, then newest first. */
    fun items(): List<Clip> = clips.sortedWith(compareByDescending<Clip> { it.pinned }.thenByDescending { it.time })

    fun togglePin(clip: Clip) {
        val i = clips.indexOfFirst { it.text == clip.text }
        if (i >= 0) clips[i] = clips[i].copy(pinned = !clips[i].pinned)
        save()
    }

    fun remove(clip: Clip) {
        clips.removeAll { it.text == clip.text }
        save()
    }

    /** Removes everything except pinned clips. */
    fun clear() {
        clips.removeAll { !it.pinned }
        save()
    }

    private fun capture() {
        if (!enabled) return
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return
        val description = clip.description
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        ) {
            return
        }
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).text?.toString()?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_LENGTH) return
        val wasPinned = clips.firstOrNull { it.text == text }?.pinned ?: false
        clips.removeAll { it.text == text }
        clips.add(Clip(text, wasPinned, System.currentTimeMillis()))
        val unpinned = clips.filter { !it.pinned }.sortedByDescending { it.time }
        if (unpinned.size > MAX_UNPINNED) clips.removeAll(unpinned.drop(MAX_UNPINNED).toSet())
        save()
    }

    private fun load(): MutableList<Clip> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        MutableList(array.length()) { i ->
            val o = array.getJSONObject(i)
            Clip(o.getString("t"), o.optBoolean("p"), o.optLong("w"))
        }
    }.getOrDefault(mutableListOf())

    private fun save() {
        val array = JSONArray()
        clips.forEach { array.put(JSONObject().put("t", it.text).put("p", it.pinned).put("w", it.time)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "clips"
        const val MAX_UNPINNED = 40
        const val MAX_LENGTH = 5000
    }
}
