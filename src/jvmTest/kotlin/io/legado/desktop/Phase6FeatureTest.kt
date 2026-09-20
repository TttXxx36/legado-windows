package io.legado.desktop

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.engine.explore.ExploreEngine
import io.legado.desktop.engine.hotkey.GlobalMediaHotkeyManager
import io.legado.desktop.ui.font.FontManager
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class Phase6FeatureTest {

    @Test
    fun testExploreKindParsingTextFormat() {
        val rawKinds = """
            玄幻热门::https://example.com/xuanhuan/page_{{page}}.html
            仙侠修真::https://example.com/xianxia/page_{{page}}.html
            都市爽文::https://example.com/dushi/page_{{page}}.html
        """.trimIndent()

        val parsed = ExploreEngine.parseExploreKinds(rawKinds)
        assertEquals(3, parsed.size)
        assertEquals("玄幻热门", parsed[0].title)
        assertEquals("https://example.com/xuanhuan/page_{{page}}.html", parsed[0].url)
        assertEquals("都市爽文", parsed[2].title)
    }

    @Test
    fun testExploreKindParsingJsonFormat() {
        val jsonKinds = """
            [
                {"title": "总排行榜", "url": "/rank/total"},
                {"title": "月度热门", "url": "/rank/month?page={{page}}"}
            ]
        """.trimIndent()

        val parsed = ExploreEngine.parseExploreKinds(jsonKinds)
        assertEquals(2, parsed.size)
        assertEquals("总排行榜", parsed[0].title)
        assertEquals("/rank/total", parsed[0].url)
        assertEquals("月度热门", parsed[1].title)
        assertEquals("/rank/month?page={{page}}", parsed[1].url)
    }

    @Test
    fun testFontManagerPresetsAndResolution() {
        val presets = FontManager.presetOptions
        assertTrue(presets.isNotEmpty(), "Preset font options must not be empty")
        assertTrue(presets.any { it.name.contains("默认") })

        val defaultFamily = FontManager.getFontFamily(null)
        assertEquals(FontFamily.Default, defaultFamily)

        val serifFamily = FontManager.getFontFamily("serif")
        assertEquals(FontFamily.Serif, serifFamily)

        val sansFamily = FontManager.getFontFamily("sans-serif")
        assertEquals(FontFamily.SansSerif, sansFamily)

        val monoFamily = FontManager.getFontFamily("monospace")
        assertEquals(FontFamily.Monospace, monoFamily)

        val availableSystemFonts = FontManager.getAvailableSystemFonts()
        assertTrue(availableSystemFonts.size >= 4, "Should discover system or preset fonts")
    }

    @Test
    fun testAppDatabaseConfigStorage() = runBlocking {
        val key = "test_phase6_pref_key"
        val value = "phase6_custom_font_val"

        AppDatabase.setConfig(key, value)
        val fetched = AppDatabase.getConfig(key, "default_val")
        assertEquals(value, fetched)

        val nonExistent = AppDatabase.getConfig("non_existent_random_key_12345", "fallback_default")
        assertEquals("fallback_default", nonExistent)
    }

    @Test
    fun testGlobalMediaHotkeyManagerLifecycle() {
        // Test stopping when not started doesn't throw
        GlobalMediaHotkeyManager.stop()
        assertFalse(GlobalMediaHotkeyManager.isEnabled)

        var playPauseCalled = false
        GlobalMediaHotkeyManager.onPlayPause = { playPauseCalled = true }
        GlobalMediaHotkeyManager.onPlayPause?.invoke()
        assertTrue(playPauseCalled, "Callback invocation must work")
    }
}
