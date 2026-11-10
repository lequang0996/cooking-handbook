package com.fws.cookinghandbook.util

import android.content.Context
import com.fws.cookinghandbook.data.Dish
import com.fws.cookinghandbook.data.DishText
import com.fws.cookinghandbook.data.Prefs
import com.fws.cookinghandbook.data.asText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Recipe translations. Looked up in this order for a given dish + language:
 *  1. learned - translated by Gemini on this device (filesDir/translations/<lang>.json)
 *  2. bundled - shipped inside the app (assets/translations/<lang>.json)
 *  3. nothing - the UI shows the curated title (assets/titles.json) and the Chinese text
 *
 * Every stored translation remembers the git blob `sha` of the Chinese source it was made from. When a recipe changes
 * upstream the sha no longer matches, the old translation is ignored and Gemini translates the new version
 * (in the background after a sync, or at once when the recipe is opened).
 */
class TranslationManager(private val context: Context, private val prefs: Prefs) {
    private class Entry(val sha: String, val text: DishText)

    private class Task(val dish: Dish, val lang: String) {
        val id = "${dish.path}|$lang|${dish.sha}"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = GeminiClient { prefs.apiKeys.value }

    private val _version = MutableStateFlow(0)

    /** Bumps whenever a translation (or a language file) becomes available, so the UI can refresh. */
    val version: StateFlow<Int> = _version

    private val _busy = MutableStateFlow(0)

    /** Number of recipe translations waiting or running. */
    val busy: StateFlow<Int> = _busy

    private val _lastError = MutableStateFlow<String?>(null)

    /** "no_key", "busy" (all keys rate limited) or an error message; null after the last success. */
    val lastError: StateFlow<String?> = _lastError

    fun hasKeys() = prefs.apiKeys.value.isNotEmpty()

    // ------------------------------------------------------------------ storage

    private inner class LangStore(val lang: String) {
        @Volatile var loaded = false
        @Volatile private var loading = false
        @Volatile private var bundled: Map<String, Entry> = emptyMap()
        val learned = ConcurrentHashMap<String, Entry>()
        private var saveJob: Job? = null
        private val file get() = File(File(context.filesDir, "translations").apply { mkdirs() }, "$lang.json")

        fun lookup(path: String, sha: String): DishText? {
            learned[path]?.takeIf { it.sha == sha }?.let { return it.text }
            return bundled[path]?.takeIf { it.sha == sha }?.text
        }

        fun ensureLoaded() {
            if (loaded || loading) return
            loading = true
            scope.launch {
                runCatching {
                    context.assets.open("translations/$lang.json").bufferedReader(Charsets.UTF_8).use { readAll(it.readText()) }
                }.onSuccess { bundled = it }
                runCatching { if (file.exists()) learned.putAll(readAll(file.readText())) }
                loaded = true
                _version.value++
            }
        }

        fun scheduleSave() {
            saveJob?.cancel()
            saveJob = scope.launch {
                delay(3_000)
                runCatching {
                    val o = JSONObject()
                    learned.forEach { (p, e) -> o.put(p, encode(e)) }
                    val tmp = File(file.parentFile, "$lang.tmp")
                    tmp.writeText(o.toString())
                    tmp.renameTo(file)
                }
            }
        }
    }

    private val stores = ConcurrentHashMap<String, LangStore>()
    private fun store(lang: String): LangStore = stores.getOrPut(lang) { LangStore(lang).also { it.ensureLoaded() } }

    private fun readAll(json: String): Map<String, Entry> {
        val o = JSONObject(json)
        val out = HashMap<String, Entry>(o.length())
        for (k in o.keys()) {
            val e = o.getJSONObject(k)
            fun list(name: String): List<String> = e.optJSONArray(name)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            out[k] = Entry(
                e.getString("sha"),
                DishText(e.optString("title"), e.optString("intro"), list("ingredients"), e.optString("servingNote"), list("quantities"), list("steps")),
            )
        }
        return out
    }

    private fun encode(e: Entry): JSONObject = JSONObject()
        .put("sha", e.sha).put("title", e.text.title).put("intro", e.text.intro)
        .put("ingredients", JSONArray(e.text.ingredients)).put("servingNote", e.text.servingNote)
        .put("quantities", JSONArray(e.text.quantities)).put("steps", JSONArray(e.text.steps))

    // ------------------------------------------------------------------ curated titles (assets/titles.json)

    private val glossary: Map<String, Map<String, String>> by lazy {
        runCatching {
            val o = JSONObject(context.assets.open("titles.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
            o.keys().asSequence().associateWith { k -> o.getJSONObject(k).let { g -> g.keys().asSequence().associateWith { g.getString(it) } } }
        }.getOrDefault(emptyMap())
    }

    // ------------------------------------------------------------------ reading

    /** Full translation of [dish] into [lang], or null while it is not available yet. */
    fun text(dish: Dish, lang: String): DishText? {
        if (lang == "zh") return dish.asText()
        return store(lang).lookup(dish.path, dish.sha)
    }

    /** Best available title: curated name, else the translated title, else the Chinese original. */
    fun title(dish: Dish, lang: String): String {
        if (lang == "zh") return dish.title
        return glossary[dish.title]?.get(lang)?.takeIf { it.isNotBlank() }
            ?: store(lang).lookup(dish.path, dish.sha)?.title?.takeIf { it.isNotBlank() }
            ?: dish.title
    }

    /** The recipe intro in [lang] when translated, else the Chinese intro. */
    fun intro(dish: Dish, lang: String): String = text(dish, lang)?.intro?.takeIf { it.isNotBlank() } ?: dish.intro

    /** Lower-cased translated title + ingredients, for searching in the user's language (null for Chinese). */
    fun searchText(dish: Dish, lang: String): String? {
        if (lang == "zh") return null
        val t = store(lang).lookup(dish.path, dish.sha)
        return (title(dish, lang) + "\n" + t?.ingredients?.joinToString("\n").orEmpty()).lowercase()
    }

    private fun upToDate(dish: Dish, lang: String) = store(lang).lookup(dish.path, dish.sha) != null

    // ------------------------------------------------------------------ queue

    private val lock = Any()
    private val queue = ArrayDeque<Task>()
    private val queued = HashSet<String>()
    private var workers = 0

    /** The user opened [dish]: translate it into [lang] first, ahead of any background work. */
    fun request(dish: Dish, lang: String) = submit(dish, lang, front = true)

    /** Remembers dishes that are new or changed, so they get translated even if this run is interrupted. */
    fun markChanged(paths: Collection<String>) {
        if (paths.isNotEmpty()) prefs.pendingTranslation = prefs.pendingTranslation + paths
    }

    /** Queues every remembered dish for all languages (background, non-blocking). */
    fun resumePending(all: Collection<Dish>) {
        if (!hasKeys()) return
        for (d in pendingDishes(all)) for (l in Lang.targets) submit(d, l, front = false)
    }

    private fun pendingDishes(all: Collection<Dish>): List<Dish> {
        val pending = prefs.pendingTranslation
        if (pending.isEmpty() || all.isEmpty()) return emptyList() // empty list = recipes not loaded yet: do not prune
        val found = all.filter { it.path in pending }
        val gone = pending - found.map { it.path }.toSet() // removed upstream: nothing to translate any more
        if (gone.isNotEmpty()) prefs.pendingTranslation = pending - gone
        return found
    }

    /**
     * For WorkManager: translate all remembered dishes and wait until the queue is empty (or [timeoutMs] passed).
     * Returns false when something is still missing and the work should be retried later.
     */
    suspend fun translatePendingAndWait(all: Collection<Dish>, timeoutMs: Long = 9 * 60_000L): Boolean {
        val dishes = pendingDishes(all)
        if (dishes.isEmpty() || !hasKeys()) return true // without a key nothing can be done; they stay remembered
        for (l in Lang.targets) while (!store(l).loaded) delay(100)
        for (d in dishes) for (l in Lang.targets) submit(d, l, front = false)
        val end = System.currentTimeMillis() + timeoutMs
        while (_busy.value > 0 && System.currentTimeMillis() < end) delay(1_000)
        return dishes.all { d -> Lang.targets.all { l -> upToDate(d, l) } }
    }

    private fun submit(dish: Dish, lang: String, front: Boolean) {
        if (lang == "zh" || !hasKeys() || upToDate(dish, lang)) return
        if (!store(lang).loaded) { // decide only after the bundled file is read, so bundled recipes are never re-translated
            scope.launch {
                while (!store(lang).loaded) delay(100)
                submit(dish, lang, front)
            }
            return
        }
        val task = Task(dish, lang)
        synchronized(lock) {
            if (!queued.add(task.id)) {
                if (front) { queue.removeAll { it.id == task.id }; queue.addFirst(task) }
                return
            }
            if (front) queue.addFirst(task) else queue.addLast(task)
            _busy.value = queued.size
        }
        startWorkers()
    }

    private fun startWorkers() {
        val want = prefs.apiKeys.value.size.coerceIn(1, MAX_PARALLEL)
        synchronized(lock) {
            while (workers < want && queue.isNotEmpty()) { workers++; scope.launch { workerLoop() } }
        }
    }

    private suspend fun workerLoop() {
        try {
            while (true) {
                val task = synchronized(lock) { queue.removeFirstOrNull() } ?: break
                try {
                    translateNow(task)
                    _lastError.value = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _lastError.value = when (e) {
                        is NoApiKeyException -> "no_key"
                        is AllKeysBusyException -> "busy"
                        else -> e.message ?: "error"
                    }
                    // the same would happen to every other task: stop now, they are queued again next time
                    if (e is NoApiKeyException || e is AllKeysBusyException) dropQueue()
                } finally {
                    synchronized(lock) { queued.remove(task.id); _busy.value = queued.size }
                }
            }
        } finally {
            val again = synchronized(lock) { workers--; queue.isNotEmpty() }
            if (again) startWorkers()
        }
    }

    private fun dropQueue() = synchronized(lock) { queue.clear(); queued.clear(); _busy.value = 0 }

    private suspend fun translateNow(task: Task) {
        val d = task.dish
        val source = JSONObject()
            .put("title", d.title).put("intro", d.intro).put("ingredients", JSONArray(d.ingredients))
            .put("servingNote", d.servingNote).put("quantities", JSONArray(d.quantities)).put("steps", JSONArray(d.steps))
            .toString()
        val system = systemPrompt(task.lang)

        var result: DishText? = null
        var failure: Exception? = null
        for (attempt in 1..2) { // one retry if the model returned a malformed or incomplete answer
            try {
                result = parse(client.generateJson(system, source), d)
                break
            } catch (e: JSONException) {
                failure = e
            } catch (e: IllegalStateException) {
                failure = e
            }
        }
        val text = result ?: throw (failure ?: IllegalStateException("empty translation"))

        val curated = glossary[d.title]?.get(task.lang)?.takeIf { it.isNotBlank() } // keep the hand-written dish names
        val st = store(task.lang)
        st.learned[d.path] = Entry(d.sha, if (curated != null) text.copy(title = curated) else text)
        st.scheduleSave()
        if (Lang.targets.all { upToDate(d, it) }) prefs.pendingTranslation = prefs.pendingTranslation - d.path
        _version.value++
    }

    /** Parses the model answer and checks that it has the same shape as the source recipe. */
    private fun parse(json: String, d: Dish): DishText {
        val o = JSONObject(json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        fun list(name: String, expected: Int): List<String> {
            val a = o.getJSONArray(name)
            check(a.length() == expected) { "$name: expected $expected items, got ${a.length()}" }
            return List(a.length()) { a.getString(it).trim() }
        }
        val out = DishText(
            title = o.getString("title").trim(),
            intro = o.optString("intro").trim(),
            ingredients = list("ingredients", d.ingredients.size),
            servingNote = o.optString("servingNote").trim(),
            quantities = list("quantities", d.quantities.size),
            steps = list("steps", d.steps.size),
        )
        check(out.title.isNotEmpty()) { "empty title" }
        check(out.steps.all { it.isNotEmpty() }) { "empty step" }
        check(d.intro.isBlank() || out.intro.isNotEmpty()) { "empty intro" }
        return out
    }

    private fun systemPrompt(lang: String): String = """
You are a professional culinary translator and recipe editor. Translate the recipe in the user's JSON from Simplified Chinese into ${LANGUAGE_NAMES[lang] ?: lang}.

Return ONLY one JSON object with exactly the same keys as the input (title, intro, ingredients, servingNote, quantities, steps). Arrays must keep exactly the same number of items in the same order; strings that are empty stay empty.

Rules:
- Translate every string completely and naturally, the way a good cookbook in the target language would say it. No Chinese characters may remain, except where a dish or ingredient is conventionally kept in Chinese (then add the translation).
- title: the natural, well-known name of the dish in the target language (not a word-by-word translation when an established name exists).
- steps: clear imperative cooking instructions. Keep each step as one string; keep any line breaks and the "• " bullet markers inside a step.
- Keep every number, quantity, time, temperature and unit exactly right. Use the units common in the target language (g, kg, ml, tbsp, tsp, min); do not convert between measurement systems.
- Use the correct culinary terms for ingredients, tools and techniques. Keep brand names as they are.
- Do not add, omit, explain or comment on anything.
""".trimIndent()

    companion object {
        private const val MAX_PARALLEL = 3
        private val LANGUAGE_NAMES = mapOf(
            "en" to "English", "es" to "Spanish", "pt" to "Brazilian Portuguese", "ru" to "Russian",
            "ja" to "Japanese", "ko" to "Korean", "vi" to "Vietnamese",
        )
    }
}
