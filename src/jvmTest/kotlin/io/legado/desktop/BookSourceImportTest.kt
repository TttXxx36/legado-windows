package io.legado.desktop

import io.legado.desktop.engine.BookSourceEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookSourceImportTest {

    @Test
    fun testParseStandardArray() {
        val json = """
            [
                {
                    "bookSourceName": "起点中文网",
                    "bookSourceUrl": "https://www.qidian.com"
                },
                {
                    "bookSourceName": "纵横中文网",
                    "bookSourceUrl": "https://www.zongheng.com"
                }
            ]
        """.trimIndent()

        val list = BookSourceEngine.parseBookSources(json)
        assertEquals(2, list.size)
        assertEquals("起点中文网", list[0].bookSourceName)
        assertEquals("纵横中文网", list[1].bookSourceName)
    }

    @Test
    fun testParseSingleObject() {
        val json = """
            {
                "bookSourceName": "笔趣阁",
                "bookSourceUrl": "https://www.biquge.com"
            }
        """.trimIndent()

        val list = BookSourceEngine.parseBookSources(json)
        assertEquals(1, list.size)
        assertEquals("笔趣阁", list[0].bookSourceName)
    }

    @Test
    fun testParseApiEnvelopeDataArray() {
        val json = """
            {
                "code": 200,
                "msg": "success",
                "data": [
                    {
                        "bookSourceName": "源神书源1",
                        "bookSourceUrl": "https://source1.com"
                    },
                    {
                        "bookSourceName": "源神书源2",
                        "bookSourceUrl": "https://source2.com"
                    }
                ]
            }
        """.trimIndent()

        val list = BookSourceEngine.parseBookSources(json)
        assertEquals(2, list.size)
        assertEquals("源神书源1", list[0].bookSourceName)
        assertEquals("源神书源2", list[1].bookSourceName)
    }

    @Test
    fun testParseEnvelopeListArray() {
        val json = """
            {
                "status": 1,
                "list": [
                    {
                        "bookSourceName": "网络精选源",
                        "bookSourceUrl": "https://select.net"
                    }
                ]
            }
        """.trimIndent()

        val list = BookSourceEngine.parseBookSources(json)
        assertEquals(1, list.size)
        assertEquals("网络精选源", list[0].bookSourceName)
    }

    @Test
    fun testResilientSkipCorruptedElements() {
        // One element is broken/invalid, but valid elements must be preserved
        val json = """
            [
                {
                    "bookSourceName": "有效书源1",
                    "bookSourceUrl": "https://valid1.com"
                },
                {
                    "corruptField": 12345
                },
                {
                    "bookSourceName": "有效书源2",
                    "bookSourceUrl": "https://valid2.com"
                }
            ]
        """.trimIndent()

        val list = BookSourceEngine.parseBookSources(json)
        assertEquals(2, list.size)
        assertTrue(list.any { it.bookSourceName == "有效书源1" })
        assertTrue(list.any { it.bookSourceName == "有效书源2" })
    }
}
