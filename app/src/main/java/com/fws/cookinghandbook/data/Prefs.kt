package com.fws.cookinghandbook.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Small SharedPreferences wrapper exposing the values the UI observes as StateFlows. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(sp.getInt("theme", 0)) // 0 system, 1 light, 2 dark
    val theme: StateFlow<Int> = _theme
    fun setTheme(v: Int) { sp.edit().putInt("theme", v).apply(); _theme.value = v }

    // ---- Gemini API keys (one per line in the UI, stored newline-separated) ----
    private val _apiKeys = MutableStateFlow(parseKeys(sp.getString("gemini_keys", "").orEmpty()))
    val apiKeys: StateFlow<List<String>> = _apiKeys

    /** Adds every key found in [text] (any mix of newlines, spaces, commas); duplicates are ignored. Returns how many were new. */
    fun addApiKeys(text: String): Int {
        val fresh = parseKeys(text).filter { it !in _apiKeys.value }
        if (fresh.isNotEmpty()) saveKeys(_apiKeys.value + fresh)
        return fresh.size
    }
    fun removeApiKey(key: String) = saveKeys(_apiKeys.value - key)
    fun clearApiKeys() = saveKeys(emptyList())

    private fun saveKeys(list: List<String>) {
        sp.edit().putString("gemini_keys", list.joinToString("\n")).apply()
        _apiKeys.value = list
    }

    /** Dishes that are new or changed upstream and not yet translated into every language (survives restarts). */
    var pendingTranslation: Set<String>
        get() = sp.getStringSet("pending_translation", emptySet()).orEmpty().toSet()
        set(v) { sp.edit().putStringSet("pending_translation", v).apply() }

    private val _ttsRate = MutableStateFlow(sp.getFloat("tts_rate", 2.0f))
    val ttsRate: StateFlow<Float> = _ttsRate
    fun setTtsRate(v: Float) { sp.edit().putFloat("tts_rate", v).apply(); _ttsRate.value = v }

    private val _favorites = MutableStateFlow(sp.getStringSet("favorites", emptySet()).orEmpty().toSet())
    val favorites: StateFlow<Set<String>> = _favorites
    fun toggleFavorite(path: String) {
        val next = _favorites.value.toMutableSet().also { if (!it.add(path)) it.remove(path) }
        sp.edit().putStringSet("favorites", next).apply()
        _favorites.value = next
    }

    // ---- featured slider (category -> dish path) and the moment it was last drawn ----
    var pickedAt: Long
        get() = sp.getLong("picked_at", 0L)
        set(v) { sp.edit().putLong("picked_at", v).apply() }

    fun loadPicks(): Map<String, String> {
        val s = sp.getString("picks", null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }
    fun savePicks(m: Map<String, String>) { sp.edit().putString("picks", JSONObject(m as Map<*, *>).toString()).apply() }

    // ---- sync bookkeeping ----
    var lastSync: Long
        get() = sp.getLong("last_sync", 0L)
        set(v) { sp.edit().putLong("last_sync", v).apply() }
    var treeEtag: String?
        get() = sp.getString("tree_etag", null)
        set(v) { sp.edit().putString("tree_etag", v).apply() }
    var seedVersion: Int
        get() = sp.getInt("seed_version", -1)
        set(v) { sp.edit().putInt("seed_version", v).apply() }

    companion object {
        fun parseKeys(text: String): List<String> =
            text.split('\n', '\r', ' ', '\t', ',', ';').map { it.trim().trim('"', '\'') }.filter { it.length >= 8 }.distinct()
    }
}
