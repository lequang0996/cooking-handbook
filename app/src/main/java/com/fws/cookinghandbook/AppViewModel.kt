package com.fws.cookinghandbook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fws.cookinghandbook.data.Categories
import com.fws.cookinghandbook.data.Dish
import com.fws.cookinghandbook.util.Lang
import com.fws.cookinghandbook.util.TtsController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val cook = app as CookApp
    val prefs = cook.prefs
    val repository = cook.repository
    val dishes: StateFlow<List<Dish>> = repository.dishes
    val translations = cook.translations
    private val ttsLazy = lazy { TtsController(app) }
    val tts: TtsController get() = ttsLazy.value

    /** category id -> dish path currently shown in the home slider */
    private val _featured = MutableStateFlow<Map<String, String>>(emptyMap())
    val featured: StateFlow<Map<String, String>> = _featured
    private val _pickedAt = MutableStateFlow(prefs.pickedAt)
    val pickedAt: StateFlow<Long> = _pickedAt

    init {
        viewModelScope.launch {
            repository.load()
            ensureFeatured()
            translations.resumePending(dishes.value)
            syncIfDue()
            // keeps the slider fresh while the app stays open
            while (true) {
                val wait = (prefs.pickedAt + ROTATE_MS - System.currentTimeMillis()).coerceIn(1_000L, ROTATE_MS)
                delay(wait)
                ensureFeatured()
            }
        }
    }

    /** Re-draws the 10 random dishes only when 5 minutes have passed since the last draw (persisted, so it
     *  also counts the time the app was closed). Called on start, on resume and by the ticker above. */
    fun ensureFeatured() {
        val all = dishes.value
        if (all.isEmpty()) return
        val now = System.currentTimeMillis()
        val at = prefs.pickedAt
        val stale = at == 0L || now < at || now - at >= ROTATE_MS
        val current = if (stale) _featured.value.ifEmpty { prefs.loadPicks() } else prefs.loadPicks()
        val byPath = all.associateBy { it.path }
        val next = LinkedHashMap<String, String>()
        for (cat in Categories.all) {
            val pool = all.filter { it.categoryId == cat.id }
            if (pool.isEmpty()) continue
            val old = current[cat.id]
            next[cat.id] = if (!stale && old != null && byPath.containsKey(old)) old
            else (pool.filter { it.path != old }.ifEmpty { pool }).random().path
        }
        if (stale) { prefs.pickedAt = now; _pickedAt.value = now }
        prefs.savePicks(next)
        _featured.value = next
    }

    init {
        // a key was added in Settings: translate everything that was waiting for one
        viewModelScope.launch {
            prefs.apiKeys.collect { if (it.isNotEmpty()) translations.resumePending(dishes.value) }
        }
    }

    /** Background WorkManager checks once a day; this is only a fallback if that run was delayed (e.g. battery saver). */
    private suspend fun syncIfDue() {
        if (System.currentTimeMillis() - prefs.lastSync < DAY_MS) return
        val r = repository.sync()
        if (r.changed > 0) {
            ensureFeatured()
            translations.markChanged(r.changedPaths)
            translations.resumePending(dishes.value)
        }
    }

    /** Dishes matching [query] in the Chinese source or in the user's language (translated title and ingredients). */
    fun search(query: String): List<Dish> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val lang = Lang.current(getApplication())
        return dishes.value.filter { d ->
            d.title.contains(q, true) || d.ingredients.any { it.contains(q, true) } ||
                translations.searchText(d, lang)?.contains(q) == true
        }
    }

    override fun onCleared() {
        if (ttsLazy.isInitialized()) runCatching { ttsLazy.value.shutdown() }
    }

    companion object { const val ROTATE_MS = 5 * 60 * 1000L
        const val DAY_MS = 24 * 60 * 60 * 1000L }
}
