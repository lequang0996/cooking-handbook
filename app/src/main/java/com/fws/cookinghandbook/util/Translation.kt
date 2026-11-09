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
class TranslationManager(context: Context) {
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

    private fun key(text: String, src: String, dst: String) = "$src>$dst:${text.hashCode()}:${text.length}"

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
