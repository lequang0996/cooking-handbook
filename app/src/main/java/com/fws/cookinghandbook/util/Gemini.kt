package com.fws.cookinghandbook.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Raised when the API answered with an error status. [code] is the HTTP status. */
class GeminiHttpException(val code: Int, message: String) : IOException("HTTP $code: $message")

/** There is no usable API key at all (none saved, or every saved key was rejected). */
class NoApiKeyException : IOException("No usable Gemini API key")

/** Every key is rate limited right now; try again later. */
class AllKeysBusyException : IOException("All Gemini API keys are rate limited")

/** "AIz...x9Q": the first and last three characters only. Used everywhere a key is shown. */
fun maskKey(key: String): String = if (key.length <= 8) "•••" else key.take(3) + "..." + key.takeLast(3)

/**
 * Minimal client for the Gemini `generateContent` REST endpoint.
 * Keys are used round-robin; a key that answers 429/5xx is parked for a while, a key that is rejected
 * (400 invalid / 401 / 403) is parked for longer, and the request moves on to the next key.
 */
class GeminiClient(private val keys: () -> List<String>) {
    private val cursor = AtomicInteger(0)
    private val parkedUntil = ConcurrentHashMap<String, Long>()

    /** Sends one prompt and returns the model's text (JSON, because of responseMimeType). */
    suspend fun generateJson(system: String, user: String): String = withContext(Dispatchers.IO) {
        var attempts = 0
        var last: Exception? = null
        while (attempts < MAX_ATTEMPTS) {
            val key = nextKey()          // may suspend until a parked key is free; throws if none will be soon
            try {
                return@withContext call(key, system, user)
            } catch (e: GeminiHttpException) {
                last = e
                val now = System.currentTimeMillis()
                when {
                    e.code == 429 -> parkedUntil[key] = now + RATE_LIMIT_PARK_MS
                    e.code in 500..599 -> parkedUntil[key] = now + SERVER_PARK_MS
                    e.code == 401 || e.code == 403 || (e.code == 400 && e.message.orEmpty().contains("API key", true)) ->
                        parkedUntil[key] = now + INVALID_KEY_PARK_MS
                    else -> throw e      // a real 400/404: retrying with another key will not help
                }
            } catch (e: IOException) {
                last = e
                delay(1_500)             // network hiccup
            }
            attempts++
        }
        throw last ?: IOException("Gemini request failed")
    }

    private suspend fun nextKey(): String {
        while (true) {
            val all = keys()
            if (all.isEmpty()) throw NoApiKeyException()
            val now = System.currentTimeMillis()
            val start = cursor.getAndIncrement()
            for (i in all.indices) {
                val k = all[Math.floorMod(start + i, all.size)]
                if ((parkedUntil[k] ?: 0L) <= now) return k
            }
            val soonest = all.minOf { parkedUntil[it] ?: 0L }
            val wait = soonest - now
            if (wait > MAX_WAIT_MS) {
                // everything is parked for long: distinguish "all invalid" from "just busy"
                if (all.all { (parkedUntil[it] ?: 0L) - now > RATE_LIMIT_PARK_MS }) throw NoApiKeyException()
                throw AllKeysBusyException()
            }
            delay(wait.coerceAtLeast(250))
        }
    }

    private fun call(key: String, system: String, user: String): String {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
            .put("generationConfig", JSONObject().put("temperature", 0.2).put("responseMimeType", "application/json"))
            .toString()

        val c = URL("$ENDPOINT/$MODEL:generateContent").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 120_000
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("x-goog-api-key", key)
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = runCatching { c.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }.getOrNull().orEmpty()
                val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault(err.take(200))
                throw GeminiHttpException(code, msg)
            }
            val text = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            return buildString { for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text")) }
        } catch (e: org.json.JSONException) {
            throw IOException("Unexpected Gemini response", e)
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val MODEL = "gemini-3.5-flash-lite"
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MAX_ATTEMPTS = 6
        private const val MAX_WAIT_MS = 20_000L
        private const val RATE_LIMIT_PARK_MS = 60_000L
        private const val SERVER_PARK_MS = 5_000L
        private const val INVALID_KEY_PARK_MS = 10 * 60_000L
    }
}
