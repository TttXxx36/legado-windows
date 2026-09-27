package io.legado.desktop.engine.tts

import javazoom.jl.player.Player
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class TtsVoice(
    val id: String,
    val name: String,
    val gender: String,
    val description: String
)

enum class SleepTimerMode(val displayName: String, val minutes: Int) {
    OFF("关闭定时", 0),
    MINUTES_15("15 分钟", 15),
    MINUTES_30("30 分钟", 30),
    MINUTES_45("45 分钟", 45),
    MINUTES_60("60 分钟", 60),
    CHAPTER_END("读完本章后停止", -1)
}

data class TtsPlayState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val currentSentenceText: String = "",
    val activeVoice: TtsVoice = EdgeTtsEngine.VOICES[0],
    val speedRate: Float = 1.0f,
    val timerMode: SleepTimerMode = SleepTimerMode.OFF,
    val remainingSeconds: Int = 0,
    val errorMessage: String? = null
)

object EdgeTtsEngine {

    val VOICES = listOf(
        TtsVoice("zh-CN-XiaoxiaoNeural", "晓晓", "女声", "温柔知性、温润亲切，经典有声读物首选"),
        TtsVoice("zh-CN-YunxiNeural", "云希", "男声", "活力自然、朝气沉浸，仙侠/玄幻/少年主角极佳"),
        TtsVoice("zh-CN-YunjianNeural", "云健", "男声", "沉稳厚重、磁性深沉，适合历史军事、悬疑评书"),
        TtsVoice("zh-CN-YunyangNeural", "云扬", "男声", "专业播音、吐字严谨，正规新闻与纪录片声线"),
        TtsVoice("zh-CN-liaoning-XiaobeiNeural", "小北", "女声", "东北特色、幽默活泼，轻松都市/搞笑网文神器"),
        TtsVoice("zh-TW-HsiaoChenNeural", "晓臻", "女声", "港台声线、甜美轻柔，言情/日常读物")
    )

    private val _playState = MutableStateFlow(TtsPlayState())
    val playState: StateFlow<TtsPlayState> = _playState.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var activePlayer: Player? = null

    private var currentSentences: List<String> = emptyList()
    private var activeChapterTitle: String = ""
    var onChapterFinished: (() -> Unit)? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Split chapter text into natural grammatical sentences for synchronized tracking
     */
    fun splitIntoSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val results = mutableListOf<String>()
        val lines = text.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Split by sentence ending punctuation (handles compound '！？' and '……' cleanly)
            val tokens = trimmed.split(Regex("(?<=[。！？!?；;])(?!([。！？!?；;]))|(?<=……)|(?<=\\n)"))
            for (token in tokens) {
                val clean = token.trim()
                if (clean.isNotBlank()) {
                    results.add(clean)
                }
            }
        }
        return results
    }

    /**
     * Escape text for SSML XML body
     */
    fun escapeSsml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Generate standard SSML payload for Edge-TTS
     */
    fun buildSsml(text: String, voice: TtsVoice, speedRate: Float): String {
        val ratePercent = ((speedRate - 1.0f) * 100).roundToInt()
        val rateStr = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"
        val escaped = escapeSsml(text)

        return """
            <speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>
                <voice name='${voice.id}'>
                    <prosody pitch='+0Hz' rate='$rateStr' volume='+0%'>
                        $escaped
                    </prosody>
                </voice>
            </speak>
        """.trimIndent()
    }

    /**
     * Start reading a chapter from a specified sentence index
     */
    fun startChapter(
        chapterTitle: String,
        content: String,
        startIndex: Int = 0,
        voice: TtsVoice = _playState.value.activeVoice,
        speedRate: Float = _playState.value.speedRate
    ) {
        stop()

        activeChapterTitle = chapterTitle
        currentSentences = splitIntoSentences(content)
        if (currentSentences.isEmpty()) return

        val safeStart = startIndex.coerceIn(0, currentSentences.size - 1)

        _playState.value = _playState.value.copy(
            isPlaying = true,
            isBuffering = true,
            currentSentenceIndex = safeStart,
            totalSentences = currentSentences.size,
            currentSentenceText = currentSentences[safeStart],
            activeVoice = voice,
            speedRate = speedRate,
            errorMessage = null
        )

        playbackJob = coroutineScope.launch {
            for (idx in safeStart until currentSentences.size) {
                if (!isActive) break

                val sentence = currentSentences[idx]
                _playState.value = _playState.value.copy(
                    currentSentenceIndex = idx,
                    currentSentenceText = sentence,
                    isBuffering = true
                )

                try {
                    // 1. Synthesize audio via Edge-TTS
                    val mp3Bytes = synthesizeSentence(sentence, voice, speedRate)

                    _playState.value = _playState.value.copy(isBuffering = false)

                    // 2. Play audio stream via JLayer
                    playMp3Stream(mp3Bytes)

                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    // Fallback to local Windows SAPI if network fails
                    try {
                        TtsEngine.speak(sentence)
                        // Wait estimated duration based on text length
                        val delayMs = ((sentence.length * 280L) / speedRate).toLong().coerceIn(600L, 10000L)
                        delay(delayMs)
                    } catch (_: Exception) {}
                }
            }

            // Chapter finished
            if (_playState.value.timerMode == SleepTimerMode.CHAPTER_END) {
                stop()
            } else {
                _playState.value = _playState.value.copy(
                    isPlaying = false,
                    isBuffering = false
                )
                onChapterFinished?.invoke()
            }
        }
    }

    /**
     * Synthesize single sentence via Edge-TTS WebSocket
     */
    suspend fun synthesizeSentence(
        text: String,
        voice: TtsVoice,
        speedRate: Float
    ): ByteArray = withContext(Dispatchers.IO) {
        val ssml = buildSsml(text, voice, speedRate)
        val audioBuffer = ByteArrayOutputStream()
        val connectionId = UUID.randomUUID().toString().replace("-", "")

        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaheadwork/v1?trustedclienttoken=6A5AA1D4EA6511CF838E00A0C90EF14A&ConnectionId=$connectionId"

        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .build()

        val completable = CompletableDeferred<ByteArray>()

        val webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val timestamp = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.US).format(Date())

                // 1. Send speech.config
                val configPayload = """
                    Path: speech.config
                    X-RequestId: $connectionId
                    X-Timestamp: $timestamp
                    Content-Type: application/json; charset=utf-8

                    {"context":{"system":{"name":"SpeechSDK","version":"1.12.1-rc.1","build":"JavaScript","lang":"JavaScript"}}}
                """.trimIndent().replace("\n", "\r\n")
                ws.send(configPayload)

                // 2. Send SSML synthesis request
                val ssmlPayload = """
                    Path: ssml
                    X-RequestId: $connectionId
                    X-Timestamp: $timestamp
                    Content-Type: application/ssml+xml

                    $ssml
                """.trimIndent().replace("\n", "\r\n")
                ws.send(ssmlPayload)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    ws.close(1000, "Done")
                    completable.complete(audioBuffer.toByteArray())
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (data.size > 2) {
                    val headerLength = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    val offset = 2 + headerLength
                    if (data.size > offset) {
                        audioBuffer.write(data, offset, data.size - offset)
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                completable.completeExceptionally(t)
            }
        })

        try {
            withTimeout(15000L) {
                completable.await()
            }
        } catch (e: TimeoutCancellationException) {
            webSocket.cancel()
            throw e
        }
    }

    /**
     * Play raw MP3 bytes synchronously on current coroutine
     */
    private suspend fun playMp3Stream(bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext
        val bis = ByteArrayInputStream(bytes)
        try {
            val player = Player(bis)
            activePlayer = player
            player.play()
        } catch (_: Exception) {
        } finally {
            try {
                activePlayer?.close()
            } catch (_: Exception) {}
            activePlayer = null
        }
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            activePlayer?.close()
        } catch (_: Exception) {}
        activePlayer = null
        _playState.value = _playState.value.copy(isPlaying = false, isBuffering = false)
    }

    fun resume() {
        if (currentSentences.isNotEmpty()) {
            val idx = _playState.value.currentSentenceIndex
            startChapter(activeChapterTitle, currentSentences.joinToString(" "), startIndex = idx)
        }
    }

    fun stop() {
        pause()
        _playState.value = _playState.value.copy(
            isPlaying = false,
            isBuffering = false,
            currentSentenceIndex = 0,
            currentSentenceText = ""
        )
    }

    fun previousSentence() {
        val newIdx = (_playState.value.currentSentenceIndex - 1).coerceAtLeast(0)
        startChapter(activeChapterTitle, currentSentences.joinToString(" "), startIndex = newIdx)
    }

    fun nextSentence() {
        val newIdx = (_playState.value.currentSentenceIndex + 1).coerceAtMost(currentSentences.size - 1)
        startChapter(activeChapterTitle, currentSentences.joinToString(" "), startIndex = newIdx)
    }

    fun setVoice(voice: TtsVoice) {
        _playState.value = _playState.value.copy(activeVoice = voice)
        if (_playState.value.isPlaying) {
            resume()
        }
    }

    fun setSpeedRate(rate: Float) {
        _playState.value = _playState.value.copy(speedRate = rate.coerceIn(0.5f, 2.5f))
        if (_playState.value.isPlaying) {
            resume()
        }
    }

    fun setSleepTimer(mode: SleepTimerMode) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        if (mode == SleepTimerMode.OFF || mode == SleepTimerMode.CHAPTER_END) {
            _playState.value = _playState.value.copy(timerMode = mode, remainingSeconds = 0)
            return
        }

        val totalSeconds = mode.minutes * 60
        _playState.value = _playState.value.copy(timerMode = mode, remainingSeconds = totalSeconds)

        sleepTimerJob = coroutineScope.launch {
            var remain = totalSeconds
            while (remain > 0 && isActive) {
                delay(1000L)
                remain--
                _playState.value = _playState.value.copy(remainingSeconds = remain)
            }
            if (isActive && remain <= 0) {
                stop()
                _playState.value = _playState.value.copy(timerMode = SleepTimerMode.OFF, remainingSeconds = 0)
            }
        }
    }
}
