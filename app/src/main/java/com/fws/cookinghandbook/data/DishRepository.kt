package com.fws.cookinghandbook.data

import android.content.Context
import com.fws.cookinghandbook.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/**
 * Source of truth for recipes.
 *  1. First start: loads the bundled snapshot (assets/dishes_seed.json) so the app works offline.
 *  2. sync(): asks GitHub for the file tree of Anduin2017/HowToCook, compares git blob SHAs with what is
 *     stored, downloads only new/changed files and drops deleted ones.
 */
class DishRepository(private val context: Context, private val prefs: Prefs) {
    private val cacheFile = File(context.filesDir, "dishes_cache.json")
    private val mutex = Mutex()
    private val raws = LinkedHashMap<String, RawDish>()

    private val _dishes = MutableStateFlow<List<Dish>>(emptyList())
    val dishes: StateFlow<List<Dish>> = _dishes

    suspend fun load() {
        mutex.withLock {
            if (raws.isNotEmpty()) return@withLock
            withContext(Dispatchers.IO) {
                var ok = false
                if (prefs.seedVersion == BuildConfig.VERSION_CODE && cacheFile.exists()) {
                    ok = runCatching { readArray(cacheFile.readText()) }.isSuccess
                    if (!ok) raws.clear()
                }
                if (!ok) { // first start, app upgrade, or damaged cache: start again from the bundled snapshot
                    readArray(context.assets.open("dishes_seed.json").bufferedReader().use { it.readText() })
                    prefs.seedVersion = BuildConfig.VERSION_CODE
                    prefs.treeEtag = null
                    persist()
                }
                publish()
            }
        }
    }

    private fun readArray(text: String) {
        val arr = JSONArray(text)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val r = RawDish(o.getString("path"), o.getString("sha"), o.getLong("updated"), o.getString("md"))
            raws[r.path] = r
        }
    }

    private fun persist() {
        val arr = JSONArray()
        raws.values.forEach {
            arr.put(JSONObject().put("path", it.path).put("sha", it.sha).put("updated", it.updatedAt).put("md", it.markdown))
        }
        val tmp = File(cacheFile.parentFile, "dishes_cache.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(cacheFile)
    }

    private fun publish() {
        _dishes.value = raws.values.mapNotNull { DishParser.parse(it) }
    }

    suspend fun sync(): SyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val tree = http("$API/git/trees/$BRANCH?recursive=1", prefs.treeEtag)
                if (tree.code == 304) { prefs.lastSync = System.currentTimeMillis(); return@withContext SyncResult(true, 0) }
                val items = JSONObject(tree.body).getJSONArray("tree")
                val remote = LinkedHashMap<String, String>()
                for (i in 0 until items.length()) {
                    val o = items.getJSONObject(i)
                    val p = o.getString("path")
                    if (o.getString("type") == "blob" && p.startsWith("dishes/") && p.endsWith(".md") && !p.startsWith("dishes/template/")) {
                        remote[p] = o.getString("sha")
                    }
                }
                if (remote.isEmpty()) return@withContext SyncResult(false) // never wipe the library on a bad answer

                val toFetch = remote.filter { (p, sha) -> raws[p]?.sha != sha }.keys.toList()
                val removed = raws.keys.filter { it !in remote }
                val now = System.currentTimeMillis()

                val downloaded = coroutineScope {
                    val gate = Semaphore(6)
                    toFetch.map { p ->
                        async {
                            gate.withPermit {
                                val md = http("$RAW/${encodePath(p)}", null).body
                                // few changes -> ask GitHub for the real commit date; many -> use "now"
                                val ts = if (toFetch.size <= 15) commitTime(p) ?: now else now
                                RawDish(p, remote.getValue(p), ts, md)
                            }
                        }
                    }.awaitAll()
                }
                downloaded.forEach { raws[it.path] = it }
                removed.forEach { raws.remove(it) }
                if (downloaded.isNotEmpty() || removed.isNotEmpty()) { persist(); publish() }
                prefs.treeEtag = tree.etag
                prefs.lastSync = now
                SyncResult(true, downloaded.size + removed.size)
            } catch (e: Exception) {
                SyncResult(false)
            }
        }
    }

    private fun commitTime(path: String): Long? = runCatching {
        val body = http("$API/commits?sha=$BRANCH&path=${encodePath(path)}&per_page=1", null).body
        val date = JSONArray(body).getJSONObject(0).getJSONObject("commit").getJSONObject("committer").getString("date")
        Instant.parse(date).toEpochMilli()
    }.getOrNull()

    private class Response(val code: Int, val body: String, val etag: String?)

    private fun http(url: String, etag: String?): Response {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "CookingHandbook/${BuildConfig.VERSION_NAME}")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (etag != null) c.setRequestProperty("If-None-Match", etag)
        try {
            val code = c.responseCode
            if (code == 304) return Response(304, "", etag)
            if (code !in 200..299) throw IllegalStateException("HTTP $code for $url")
            val body = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return Response(code, body, c.getHeaderField("ETag"))
        } finally {
            c.disconnect()
        }
    }

    private fun encodePath(p: String) =
        p.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    companion object {
        private const val OWNER_REPO = "Anduin2017/HowToCook"
        private const val BRANCH = "master"
        private const val API = "https://api.github.com/repos/$OWNER_REPO"
        private const val RAW = "https://raw.githubusercontent.com/$OWNER_REPO/$BRANCH"
    }
}
