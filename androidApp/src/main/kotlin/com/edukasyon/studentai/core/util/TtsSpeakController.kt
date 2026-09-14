package com.edukasyon.studentai.core.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Framework TextToSpeech controller for on-device read-aloud on engines without Google Play services. */
class TtsSpeakController : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var voice: Locale = Locale.US

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** True once an engine reports ready with a supported locale. */
    val isReady: Boolean get() = _ready.value

    /** Start the system TTS engine; false when no engine can be created at all. */
    fun init(context: Context): Boolean {
        if (tts != null) return isReady
        return try {
            tts = TextToSpeech(context.applicationContext, this)
            true
        } catch (e: Exception) {
            tts = null
            false
        }
    }

    /** Speak [text] immediately; false when the engine is unavailable. */
    fun speak(text: String): Boolean {
        val engine = tts ?: return false
        if (!isReady || text.isBlank()) return false
        return try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jevi-read-aloud") == TextToSpeech.SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    /** Stop current and queued speech. */
    fun stop() {
        tts?.stop()
    }

    /** Release the engine; [init] can start a new one afterwards. */
    fun shutdown() {
        tts?.shutdown()
        tts = null
        _ready.value = false
    }

    /** Pick the first usable locale after engine init, en-US with fil-PH fallback. */
    override fun onInit(status: Int) {
        val engine = tts
        if (engine == null || status != TextToSpeech.SUCCESS) {
            _ready.value = false
            return
        }
        val selected = listOf(Locale.US, Locale("fil", "PH")).firstOrNull {
            engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
        }
        _ready.value = selected != null && engine.setLanguage(selected) != TextToSpeech.LANG_NOT_SUPPORTED
        if (selected != null) voice = selected
    }
}
