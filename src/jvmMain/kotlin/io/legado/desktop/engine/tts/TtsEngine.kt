package io.legado.desktop.engine.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object TtsEngine {
    private var currentProcess: Process? = null
    var isSpeaking: Boolean = false
        private set

    private var lastText: String = ""
    private var lastRate: Int = 0

    fun isPlaying(): Boolean = isSpeaking

    fun speak(text: String, rate: Int = 0) {
        stop()
        if (text.isBlank()) return

        lastText = text
        lastRate = rate

        val cleanText = text.take(1500)
            .replace("`", " ")
            .replace("\"", "'")
            .replace("\n", " ")
            .replace("\r", " ")

        val script = """
            Add-Type -AssemblyName System.Speech;
            ${'$'}synth = New-Object System.Speech.Synthesis.SpeechSynthesizer;
            ${'$'}synth.Rate = $rate;
            ${'$'}synth.Speak("$cleanText");
        """.trimIndent()

        try {
            val process = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true)
                .start()
            currentProcess = process
            isSpeaking = true

            CoroutineScope(Dispatchers.IO).launch {
                process.waitFor()
                if (currentProcess == process) {
                    isSpeaking = false
                    currentProcess = null
                }
            }
        } catch (e: Exception) {
            isSpeaking = false
            currentProcess = null
        }
    }

    fun pause() {
        stop()
    }

    fun resume() {
        if (lastText.isNotBlank()) {
            speak(lastText, lastRate)
        }
    }

    fun stop() {
        try {
            currentProcess?.destroyForcibly()
        } catch (e: Exception) {
            // Ignore
        }
        currentProcess = null
        isSpeaking = false
    }
}
