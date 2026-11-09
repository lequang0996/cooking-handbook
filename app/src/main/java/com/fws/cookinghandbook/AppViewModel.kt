package com.fws.cookinghandbook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fws.cookinghandbook.data.Categories
import com.fws.cookinghandbook.data.Dish
import com.fws.cookinghandbook.data.SyncResult
import com.fws.cookinghandbook.util.Lang
import com.fws.cookinghandbook.util.TranslationManager
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
    val translations by lazy { TranslationManager(app) }
    private val ttsLazy = lazy { TtsController(app) }
    val tts: TtsController get() = ttsLazy.value

    /** category id -> dish path currently shown in the home slider */
    private val _featured = MutableStateFlow<Map<String, String>>(emptyMap())
    val featured: StateFlow<Map<String, String>> = _featured
    private val _pickedAt = MutableStateFlow(prefs.pickedAt)
    val pickedAt: StateFlow<Long> = _pickedAt

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing
    private val _syncMessage = MutableStateFlow<SyncResult?>(null)
    val syncMessage: StateFlow<SyncResult?> = _syncMessage
    private val _lastSync = MutableStateFlow(prefs.lastSync)
    val lastSync: StateFlow<Long> = _lastSync

    init {
        viewModelScope.launch {
            repository.load()
            ensureFeatured()
            syncNow(silent = true)
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

    fun syncNow(silent: Boolean = false) {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            val r = repository.sync()
            _syncing.value = false
            _lastSync.value = prefs.lastSync
            if (!silent) _syncMessage.value = r
            if (r.changed > 0) ensureFeatured()
        }
    }
    fun consumeSyncMessage() { _syncMessage.value = null }

    /** Dishes matching [query] in the source text, the cached translation, or the query translated to Chinese. */
    fun search(query: String, queryInChinese: String?): List<Dish> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val lang = Lang.current(getApplication())
        val zhQ = queryInChinese?.trim()?.takeIf { it.isNotEmpty() }
        return dishes.value.filter { d ->
            d.title.contains(q, true) || d.ingredients.any { it.contains(q, true) } ||
                (zhQ != null && (d.title.contains(zhQ) || d.ingredients.any { it.contains(zhQ) })) ||
                (lang != "zh" && translations.cached(d.title, "zh", lang)?.lowercase()?.contains(q) == true)
        }
    }

    override fun onCleared() {
        if (ttsLazy.isInitialized()) runCatching { ttsLazy.value.shutdown() }
    }

    companion object { const val ROTATE_MS = 5 * 60 * 1000L }
}
