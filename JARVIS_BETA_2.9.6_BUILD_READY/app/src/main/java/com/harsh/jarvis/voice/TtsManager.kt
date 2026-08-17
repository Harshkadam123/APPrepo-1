package com.harsh.jarvis.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.US
        }
    }

    fun speak(text: String) {
        if (!ready) return
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "JARVIS"
        )
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
