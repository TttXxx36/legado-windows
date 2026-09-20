package io.legado.desktop.engine.hotkey

import io.legado.desktop.engine.tts.TtsEngine
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object GlobalMediaHotkeyManager {

    private var hotkeyProcess: Process? = null
    private var listenerThread: Thread? = null

    @Volatile
    var isEnabled: Boolean = false
        private set

    var onPlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            stop()
        })
    }

    /**
     * Start listening to global multimedia keys and Ctrl+Alt shortcuts.
     */
    @Synchronized
    fun start(
        onPlayPauseCallback: (() -> Unit)? = null,
        onNextCallback: (() -> Unit)? = null,
        onPrevCallback: (() -> Unit)? = null
    ): Boolean {
        if (isEnabled && hotkeyProcess?.isAlive == true) return true

        if (onPlayPauseCallback != null) onPlayPause = onPlayPauseCallback
        if (onNextCallback != null) onNext = onNextCallback
        if (onPrevCallback != null) onPrev = onPrevCallback

        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
        if (!isWindows) {
            println("[GlobalMediaHotkeyManager] Global hotkey helper is only supported on Windows.")
            return false
        }

        try {
            val exeFile = extractHelperExe()
            if (exeFile == null || !exeFile.exists()) {
                println("[GlobalMediaHotkeyManager] Failed to extract hotkey helper executable.")
                return false
            }

            val pb = ProcessBuilder(exeFile.absolutePath)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            hotkeyProcess = proc

            val thread = Thread({
                try {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
                    var line: String? = reader.readLine()
                    while (line != null) {
                        when (line.trim()) {
                            "PLAY_PAUSE" -> onPlayPause?.invoke()
                            "NEXT" -> onNext?.invoke()
                            "PREV" -> onPrev?.invoke()
                        }
                        line = reader.readLine()
                    }
                } catch (e: Throwable) {
                    // Process terminated
                } finally {
                    isEnabled = false
                }
            }, "GlobalMediaHotkeyListener")

            thread.isDaemon = true
            thread.start()
            listenerThread = thread
            isEnabled = true
            return true
        } catch (e: Throwable) {
            e.printStackTrace()
            isEnabled = false
            return false
        }
    }

    /**
     * Stop listening to global hotkeys.
     */
    @Synchronized
    fun stop() {
        isEnabled = false
        try {
            hotkeyProcess?.let { proc ->
                if (proc.isAlive) {
                    proc.destroyForcibly()
                }
            }
        } catch (ignored: Throwable) {}
        hotkeyProcess = null
        listenerThread = null
    }

    private fun extractHelperExe(): File? {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "legado_bin")
        if (!tempDir.exists()) tempDir.mkdirs()

        val targetFile = File(tempDir, "legado-hotkey.exe")
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile
        }

        val resStream = javaClass.getResourceAsStream("/bin/legado-hotkey.exe")
        if (resStream != null) {
            resStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return targetFile
        }

        // Fallback check if running from project directory
        val localFile = File("src/jvmMain/resources/bin/legado-hotkey.exe")
        if (localFile.exists()) {
            return localFile
        }

        return null
    }
}
