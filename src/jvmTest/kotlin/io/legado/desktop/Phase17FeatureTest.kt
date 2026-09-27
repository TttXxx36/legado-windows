package io.legado.desktop

import io.legado.desktop.engine.tts.EdgeTtsEngine
import io.legado.desktop.engine.tts.SleepTimerMode
import io.legado.desktop.engine.tts.TtsVoice
import io.legado.desktop.ui.PaletteAction
import org.junit.Assert.*
import org.junit.Test

class Phase17FeatureTest {

    @Test
    fun testEdgeTtsVoiceList() {
        val voices = EdgeTtsEngine.VOICES
        assertEquals(6, voices.size)
        assertTrue(voices.any { it.id == "zh-CN-XiaoxiaoNeural" && it.name == "晓晓" })
        assertTrue(voices.any { it.id == "zh-CN-YunxiNeural" && it.name == "云希" })
        assertTrue(voices.any { it.id == "zh-CN-YunjianNeural" && it.name == "云健" })
        assertTrue(voices.any { it.id == "zh-CN-YunyangNeural" && it.name == "云扬" })
        assertTrue(voices.any { it.id == "zh-CN-liaoning-XiaobeiNeural" && it.name == "小北" })
        assertTrue(voices.any { it.id == "zh-TW-HsiaoChenNeural" && it.name == "晓臻" })
    }

    @Test
    fun testSplitIntoSentences() {
        val sampleText = """
            白日依山尽，黄河入海流。
            欲穷千里目？更上一层楼！
            这里还有省略号……以及分号；真是有趣！
        """.trimIndent()

        val sentences = EdgeTtsEngine.splitIntoSentences(sampleText)
        assertTrue(sentences.isNotEmpty())
        assertEquals(6, sentences.size)
        assertEquals("白日依山尽，黄河入海流。", sentences[0])
        assertEquals("欲穷千里目？", sentences[1])
        assertEquals("更上一层楼！", sentences[2])
        assertEquals("这里还有省略号……", sentences[3])
        assertEquals("以及分号；", sentences[4])
        assertEquals("真是有趣！", sentences[5])
    }

    @Test
    fun testSplitEmptyOrWhitespace() {
        assertEquals(emptyList<String>(), EdgeTtsEngine.splitIntoSentences(""))
        assertEquals(emptyList<String>(), EdgeTtsEngine.splitIntoSentences("   \n\n  \t  "))
    }

    @Test
    fun testEscapeSsml() {
        val raw = "Tom & Jerry <cartoon> \"best\" 'show'"
        val escaped = EdgeTtsEngine.escapeSsml(raw)
        assertEquals("Tom &amp; Jerry &lt;cartoon&gt; &quot;best&quot; &apos;show&apos;", escaped)
    }

    @Test
    fun testBuildSsml() {
        val voice = EdgeTtsEngine.VOICES[0] // Xiaoxiao
        val ssmlNormal = EdgeTtsEngine.buildSsml("你好世界", voice, 1.0f)
        assertTrue(ssmlNormal.contains("<speak version='1.0'"))
        assertTrue(ssmlNormal.contains("zh-CN-XiaoxiaoNeural"))
        assertTrue(ssmlNormal.contains("rate='+0%'"))
        assertTrue(ssmlNormal.contains("你好世界"))

        val ssmlFast = EdgeTtsEngine.buildSsml("快点读", voice, 1.5f)
        assertTrue(ssmlFast.contains("rate='+50%'"))

        val ssmlSlow = EdgeTtsEngine.buildSsml("慢点读", voice, 0.8f)
        assertTrue(ssmlSlow.contains("rate='-20%'"))
    }

    @Test
    fun testSleepTimerModeEnum() {
        assertEquals(0, SleepTimerMode.OFF.minutes)
        assertEquals(15, SleepTimerMode.MINUTES_15.minutes)
        assertEquals(30, SleepTimerMode.MINUTES_30.minutes)
        assertEquals(45, SleepTimerMode.MINUTES_45.minutes)
        assertEquals(60, SleepTimerMode.MINUTES_60.minutes)
        assertEquals(-1, SleepTimerMode.CHAPTER_END.minutes)
        assertEquals("读完本章后停止", SleepTimerMode.CHAPTER_END.displayName)
    }

    @Test
    fun testPaletteActionFiltering() {
        val actions = listOf(
            PaletteAction("ai", "AI 智能阅读助手", "速读、释义、人物谱系", "阅读增强", "✨") {},
            PaletteAction("tts", "微软 Edge-TTS 听书", "拟人神经语音朗读", "沉浸听书", "🎧") {},
            PaletteAction("export", "全书打包导出", "TXT / EPUB 离线导出", "离线导出", "📦") {},
            PaletteAction("source", "智能一键换源", "全网同名书源探测", "书源生态", "🔄") {}
        )

        val query1 = "TTS"
        val filtered1 = actions.filter {
            it.title.contains(query1, ignoreCase = true) ||
            it.subtitle.contains(query1, ignoreCase = true) ||
            it.category.contains(query1, ignoreCase = true)
        }
        assertEquals(1, filtered1.size)
        assertEquals("tts", filtered1[0].id)

        val query2 = "导出"
        val filtered2 = actions.filter {
            it.title.contains(query2, ignoreCase = true) ||
            it.subtitle.contains(query2, ignoreCase = true) ||
            it.category.contains(query2, ignoreCase = true)
        }
        assertEquals(1, filtered2.size)
        assertEquals("export", filtered2[0].id)
    }
}
