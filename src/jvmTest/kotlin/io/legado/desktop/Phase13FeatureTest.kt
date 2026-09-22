package io.legado.desktop

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.engine.TextPagingEngine
import io.legado.desktop.ui.buildHighlightedText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Phase13FeatureTest {

    @Test
    fun testKinsokuHeadForbiddenRule() {
        // Construct a sentence of exactly 10 units where 11th character is a head-forbidden comma '，'
        // Under standard wrapping with maxUnits = 10, '，' would be placed on line 2.
        // Under Kinsoku Shori, '，' should either hang on line 1, or line 1 pops a character so line 2 starts with a regular character.
        val maxUnits = 10f
        val testText = "这是一段测试文字的内容，后面还有更多文字。"
        
        val linesWithKinsoku = TextPagingEngine.wrapParagraph(testText, maxUnits, enableKinsoku = true)
        assertTrue("Lines should be wrapped", linesWithKinsoku.size >= 2)

        for (i in 1 until linesWithKinsoku.size) {
            val firstChar = linesWithKinsoku[i].firstOrNull()
            assertNotNull(firstChar)
            assertFalse(
                "Line $i must not start with head-forbidden character '$firstChar'. Line: '${linesWithKinsoku[i]}'",
                firstChar!! in TextPagingEngine.HEAD_FORBIDDEN_CHARS
            )
        }
    }

    @Test
    fun testKinsokuTailForbiddenRule() {
        // Construct a text where an opening quote '《' falls at the end of the line
        val maxUnits = 10f
        // "这是一段测试长文《" has 8 chars = 8 Chinese chars (16 units) or with 10 units = 5 chars
        val testText = "这是一段《红楼梦》的经典文学赏析文章。"

        val lines = TextPagingEngine.wrapParagraph(testText, maxUnits, enableKinsoku = true)
        assertTrue("Lines should be wrapped", lines.size >= 2)

        for (i in 0 until lines.size - 1) {
            val lastChar = lines[i].lastOrNull()
            assertNotNull(lastChar)
            assertFalse(
                "Line $i must not end with tail-forbidden character '$lastChar'",
                lastChar!! in TextPagingEngine.TAIL_FORBIDDEN_CHARS
            )
        }
    }

    @Test
    fun testPunctuationHangingAlignment() {
        val maxUnits = 10f
        // 5 characters = 10 units. 6th character is a punctuation '。'
        val testText = "天地万物生。"
        val lines = TextPagingEngine.wrapParagraph(testText, maxUnits, enableKinsoku = true)
        
        // With punctuation hanging tolerance, '。' hangs at the end of line 1 instead of becoming an orphan on line 2
        assertEquals("Sentence with 5 chars + period should hang into 1 line", 1, lines.size)
        assertEquals("Line should contain hanging period", testText, lines[0])
    }

    @Test
    fun testDropCapsFirstCharStyling() {
        val paragraphText = "　　古之欲明明德于天下者，先治其国；欲治其国者，先齐其家。"
        
        // 1. With enableDropCaps = true & isFirstPage = true
        val dropCapsResult = buildHighlightedText(
            text = paragraphText,
            query = "",
            isFirstPage = true,
            enableDropCaps = true
        )

        val spanStyles = dropCapsResult.spanStyles
        assertTrue("Span styles should contain Drop Caps styling", spanStyles.isNotEmpty())
        
        // Find the style for the first character
        val dropStyle = spanStyles.firstOrNull { it.item.fontSize == 28.sp && it.item.fontWeight == FontWeight.ExtraBold }
        assertNotNull("Should have a 28sp ExtraBold style for drop cap", dropStyle)
        
        // The first non-whitespace character in paragraphText is '古' (index 2 after two full-width spaces)
        assertEquals("Drop cap start index should match first non-whitespace char", 2, dropStyle!!.start)
        assertEquals("Drop cap end index should be start + 1", 3, dropStyle.end)
        assertEquals('古', paragraphText[dropStyle.start])

        // 2. With enableDropCaps = false
        val noDropCapsResult = buildHighlightedText(
            text = paragraphText,
            query = "",
            isFirstPage = true,
            enableDropCaps = false
        )
        assertTrue("No drop caps styles should be present when disabled", noDropCapsResult.spanStyles.isEmpty())
    }

    @Test
    fun testPhase13SettingsPersistence() = runBlocking {
        // Test Kinsoku setting persistence
        AppDatabase.setConfig("reader_kinsoku", "true")
        assertEquals(true, AppDatabase.getConfig("reader_kinsoku", "false").toBoolean())

        AppDatabase.setConfig("reader_kinsoku", "false")
        assertEquals(false, AppDatabase.getConfig("reader_kinsoku", "true").toBoolean())

        // Test Drop Caps setting persistence
        AppDatabase.setConfig("reader_drop_caps", "true")
        assertEquals(true, AppDatabase.getConfig("reader_drop_caps", "false").toBoolean())

        // Test Art Title setting persistence
        AppDatabase.setConfig("reader_art_title", "true")
        assertEquals(true, AppDatabase.getConfig("reader_art_title", "false").toBoolean())

        // Test Paper Texture & Alpha persistence
        AppDatabase.setConfig("reader_paper_texture", "true")
        assertEquals(true, AppDatabase.getConfig("reader_paper_texture", "false").toBoolean())

        AppDatabase.setConfig("reader_paper_alpha", "0.12")
        assertEquals(0.12f, AppDatabase.getConfig("reader_paper_alpha", "0.06").toFloat(), 0.001f)
    }
}
