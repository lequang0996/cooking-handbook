package com.fws.cookinghandbook.util

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * On-device translation (ML Kit) with a persistent cache, so each sentence is translated only once.
 * Codes are ML Kit language codes: zh, en, es, pt, ru, ja, ko, vi.
 */
class TranslationManager(private val context: Context) {
    private val file = File(context.filesDir, "translation_cache.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mem = ConcurrentHashMap<String, String>()
    private val translators = ConcurrentHashMap<String, Translator>()
    private val ready = ConcurrentHashMap.newKeySet<String>()
    private var saveJob: Job? = null

    init {
        runCatching {
            if (file.exists()) {
                val o = JSONObject(file.readText())
                o.keys().forEach { mem[it] = o.getString(it) }
            }
        }
    }

    private fun key(text: String, src: String, dst: String) = "v2|$src>$dst:${text.hashCode()}:${text.length}"

    // ---- hand-written dish titles (assets/titles.json): exact English and Vietnamese names ----
    private val glossary: Map<String, Map<String, String>> by lazy {
        runCatching {
            val o = JSONObject(context.assets.open("titles.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
            o.keys().asSequence().associateWith { k -> o.getJSONObject(k).let { g -> g.keys().asSequence().associateWith { g.getString(it) } } }
        }.getOrDefault(emptyMap())
    }

    /** Title for [dst] if it is available without any model download (curated en/vi, or already cached). */
    fun titleCached(zh: String, dst: String): String? {
        if (dst == "zh") return zh
        val g = glossary[zh]
        if (g != null) {
            g[dst]?.let { return it }
            g["en"]?.let { en -> return cached(en, "en", dst) }
        }
        return cached(zh, "zh", dst)
    }

    /** Dish title in [dst]: curated for en/vi; other languages translate the curated English title (better than zh→xx). */
    suspend fun title(zh: String, dst: String): String? {
        titleCached(zh, dst)?.let { return it }
        val en = glossary[zh]?.get("en")
        return if (en != null) translate(en, "en", dst) else translate(zh, "zh", dst)
    }

    fun cached(text: String, src: String, dst: String): String? = mem[key(text, src, dst)]

    /** Translated text, or null when the language pack is not available yet (e.g. offline on first use). */
    suspend fun translate(text: String, src: String, dst: String): String? {
        if (src == dst || text.isBlank()) return text
        cached(text, src, dst)?.let { return it }
        return try {
            val pair = "$src>$dst"
            val tr = translators.getOrPut(pair) {
                Translation.getClient(
                    TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(dst).build()
                )
            }
            if (pair !in ready) {
                tr.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                ready.add(pair)
            }
            val out = tr.translate(text).await()
            mem[key(text, src, dst)] = out
            scheduleSave()
            out
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(3_000)
            runCatching {
                val o = JSONObject()
                mem.forEach { (k, v) -> o.put(k, v) }
                file.writeText(o.toString())
            }
        }
    }

    companion object {
        val supported = setOf(
            TranslateLanguage.ENGLISH, TranslateLanguage.CHINESE, TranslateLanguage.SPANISH,
            TranslateLanguage.PORTUGUESE, TranslateLanguage.RUSSIAN, TranslateLanguage.JAPANESE,
            TranslateLanguage.KOREAN, TranslateLanguage.VIETNAMESE,
        )
    }
}
