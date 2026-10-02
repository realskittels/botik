package com.botik.keyboard.translate

/** Small thread-safe LRU so repeated phrases translate instantly and cost nothing. */
class TranslationCache(private val capacity: Int = 128) {
    private val map = object : LinkedHashMap<String, String>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > capacity
    }

    @Synchronized
    fun get(key: String): String? = map[key]

    @Synchronized
    fun put(key: String, value: String) {
        map[key] = value
    }

    companion object {
        fun key(model: String, style: TranslationStyle, target: Language, text: String): String =
            "$model|${style.id}|${target.code}|$text"
    }
}
