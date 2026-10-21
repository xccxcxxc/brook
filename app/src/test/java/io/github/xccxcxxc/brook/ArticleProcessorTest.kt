package io.github.xccxcxxc.brook

import io.github.xccxcxxc.brook.content.ArticleProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleProcessorTest {
    @Test
    fun cleansAndResolves() {
        val html = """
            <div style="color:#000"><script>alert(1)</script>
            <p>Hello <a href="/post/2">link</a></p>
            <img data-src="/img/a.jpg" src="data:image/gif;base64,R0lGOD" width="800" height="600">
            <img src="https://feeds.feedburner.com/~r/x/~4/abc" width="1" height="1">
            <iframe src="https://www.youtube.com/embed/xyz"></iframe>
            <iframe src="https://ads.example.com/frame"></iframe>
            <p>   </p></div>
        """.trimIndent()
        val p = ArticleProcessor.process(html, "https://example.com/blog/1")
        assertFalse(p.html.contains("<script"))
        assertFalse(p.html.contains("style="))
        assertTrue(p.html, p.html.contains("href=\"https://example.com/post/2\""))
        assertTrue(p.html, p.html.contains("src=\"https://example.com/img/a.jpg\""))
        assertFalse(p.html.contains("feedburner"))
        assertTrue(p.html.contains("youtube.com/embed/xyz"))
        assertFalse(p.html.contains("<iframe src=\"https://ads.example.com"))
        assertTrue(p.html.contains("https://ads.example.com/frame"))
        assertEquals("https://example.com/img/a.jpg", p.thumbUrl)
        assertEquals("Hello link 嵌入内容 ↗", p.summary)
    }

    @Test
    fun summaryAndReadingTime() {
        val zh = "<p>" + "中文内容".repeat(400) + "</p>"
        val p = ArticleProcessor.process(zh, null)
        assertEquals(201, p.summary.length)
        assertTrue(p.summary.endsWith("…"))
        assertEquals(4, p.readingMinutes)
        assertEquals(null, p.thumbUrl)
    }

    @Test
    fun titleEntities() {
        assertEquals("A & B <C>", ArticleProcessor.cleanTitle("A &amp; B &lt;C&gt;"))
        assertEquals("Bold title", ArticleProcessor.cleanTitle("<b>Bold</b> title"))
    }

    /** srcset 挑一张适合手机屏幕的写进 src（阅读页只用 src，离线图片也只下 src）。 */
    @Test
    fun picksSrcsetCandidateIntoSrc() {
        assertEquals("b.jpg", ArticleProcessor.pickSrcset("a.jpg 640w, b.jpg 1280w, c.jpg 2560w"))
        assertEquals("c.jpg", ArticleProcessor.pickSrcset("a.jpg 300w, c.jpg 1000w"))
        assertEquals("b.jpg", ArticleProcessor.pickSrcset("a.jpg 1x, b.jpg 2x, c.jpg 3x"))
        assertEquals("a.jpg", ArticleProcessor.pickSrcset("a.jpg"))
        // 地址里带逗号（Cloudinary）不能被拆开
        assertEquals(
            "https://res.cloudinary.com/x/image/upload/w_1400,c_fill/a.jpg",
            ArticleProcessor.pickSrcset("https://res.cloudinary.com/x/image/upload/w_700,c_fill/a.jpg 700w, https://res.cloudinary.com/x/image/upload/w_1400,c_fill/a.jpg 1400w"),
        )
        assertEquals("b.jpg", ArticleProcessor.pickSrcset("a.jpg, b.jpg 2x"))
        assertEquals(null, ArticleProcessor.pickSrcset("  "))
        val p = ArticleProcessor.process(
            """<p><img src="/s.jpg" srcset="/m.jpg 800w, /l.jpg 1600w" alt="x"><img data-src="/lazy.jpg" data-srcset="/lz.jpg 1300w"></p>""",
            "https://ex.com/post/1",
        )
        assertTrue(p.html, p.html.contains("src=\"https://ex.com/l.jpg\""))
        assertTrue(p.html, p.html.contains("src=\"https://ex.com/lz.jpg\""))
    }
}
