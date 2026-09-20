package io.legado.desktop.ui.font

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import java.io.File

data class FontOption(
    val name: String,
    val path: String? = null,
    val isSystemPreset: Boolean = false
)

object FontManager {

    private val fontCache = mutableMapOf<String, FontFamily>()

    // Standard preset options
    val presetOptions = listOf(
        FontOption("默认系统字体", null, isSystemPreset = true),
        FontOption("系统衬线体 (Serif)", "serif", isSystemPreset = true),
        FontOption("系统无衬线体 (Sans-Serif)", "sans-serif", isSystemPreset = true),
        FontOption("系统等宽字体 (Monospace)", "monospace", isSystemPreset = true)
    )

    /**
     * Scan Windows C:\Windows\Fonts directory to discover common Chinese and high-readability fonts.
     */
    fun getAvailableSystemFonts(): List<FontOption> {
        val fontsDir = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        val result = mutableListOf<FontOption>()
        result.addAll(presetOptions)

        if (!fontsDir.exists() || !fontsDir.isDirectory) return result

        val candidateFiles = listOf(
            "msyh.ttc" to "微软雅黑 (Microsoft YaHei)",
            "msyhbd.ttc" to "微软雅黑 粗体",
            "simsun.ttc" to "中易宋体 (SimSun)",
            "simhei.ttf" to "中易黑体 (SimHei)",
            "simkai.ttf" to "中易楷体 (KaiTi)",
            "simfang.ttf" to "中易仿宋 (FangSong)",
            "STSONG.TTF" to "华文宋体",
            "STKAITI.TTF" to "华文楷体",
            "STFANGSO.TTF" to "华文仿宋",
            "STXIHEI.TTF" to "华文细黑",
            "SourceHanSansCN-Regular.otf" to "思源黑体 (Source Han Sans)",
            "SourceHanSerifCN-Regular.otf" to "思源宋体 (Source Han Serif)"
        )

        for ((fileName, displayName) in candidateFiles) {
            val fontFile = File(fontsDir, fileName)
            if (fontFile.exists() && fontFile.canRead()) {
                result.add(FontOption(displayName, fontFile.absolutePath))
            }
        }

        return result
    }

    /**
     * Resolve a FontFamily from either a preset identifier, a Windows font path, or a custom external file path.
     */
    fun getFontFamily(path: String?): FontFamily {
        if (path.isNullOrBlank()) return FontFamily.Default

        // Presets
        when (path.lowercase()) {
            "serif" -> return FontFamily.Serif
            "sans-serif" -> return FontFamily.SansSerif
            "monospace" -> return FontFamily.Monospace
        }

        // Cache lookup
        fontCache[path]?.let { return it }

        // Load from file
        val file = File(path)
        if (file.exists() && file.isFile) {
            try {
                val family = FontFamily(Font(file))
                fontCache[path] = family
                return family
            } catch (e: Throwable) {
                // If direct loading fails (e.g. some TTC index issues), fallback to Default
                e.printStackTrace()
            }
        }

        return FontFamily.Default
    }
}
