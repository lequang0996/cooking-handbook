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

    /** null = user never touched it (default depends on language). */
    private val _translate = MutableStateFlow(if (sp.contains("translate")) sp.getBoolean("translate", true) else null)
    val translate: StateFlow<Boolean?> = _translate
    fun setTranslate(v: Boolean) { sp.edit().putBoolean("translate", v).apply(); _translate.value = v }

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
}
