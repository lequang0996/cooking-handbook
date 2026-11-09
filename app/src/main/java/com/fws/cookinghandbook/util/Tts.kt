package com.fws.cookinghandbook.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Reads a list of text chunks aloud and reports which chunk is being spoken. */
class TtsController(context: Context) {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var lastIndex = -1

    private val _current = MutableStateFlow(-1)
    val current: StateFlow<Int> = _current
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking
    private val _missingVoice = MutableStateFlow(false)
    val missingVoice: StateFlow<Boolean> = _missingVoice

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        utteranceId?.removePrefix("u")?.toIntOrNull()?.let { _current.value = it }
                    }
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.removePrefix("u")?.toIntOrNull() == lastIndex) reset()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { reset() }
                })
                pending?.invoke()
            }
            pending = null
        }
    }

    private fun reset() { _speaking.value = false; _current.value = -1 }

    fun speak(chunks: List<String>, start: Int, locale: Locale, rate: Float) {
        val e = engine ?: return
        if (!ready) { pending = { speak(chunks, start, locale, rate) }; return }
        val r = e.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            _missingVoice.value = true
            return
        }
        _missingVoice.value = false
        e.setSpeechRate(rate)
        e.stop()
        lastIndex = chunks.lastIndex
        chunks.forEachIndexed { i, text ->
            if (i >= start && text.isNotBlank()) {
                e.speak(text.take(TextToSpeech.getMaxSpeechInputLength() - 1), TextToSpeech.QUEUE_ADD, null, "u$i")
            }
        }
        _speaking.value = true
    }

    fun stop() { engine?.stop(); reset() }
    fun clearMissingVoice() { _missingVoice.value = false }
    fun shutdown() { engine?.stop(); engine?.shutdown(); engine = null }
}
