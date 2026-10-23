package io.github.xccxcxxc.runnel

import io.github.xccxcxxc.runnel.data.db.FeedEntity
import io.github.xccxcxxc.runnel.data.db.FolderEntity
import io.github.xccxcxxc.runnel.sync.Opml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlTest {
    private fun feed(id: String, title: String, folder: String?, url: String?) =
        FeedEntity(id = id, title = title, folderId = folder, siteUrl = "https://$id.example", feedUrl = url, iconUrl = null, sortIndex = 0, localIcon = null)

    @Test
    fun foldersAndLooseFeedsAreExported() {
        val xml = Opml.build(
            "T & Co",
            listOf(FolderEntity("user/-/label/Tech", "科技", 0), FolderEntity("user/-/label/Empty", "空", 1)),
            listOf(
                feed("a", "A <b>", "user/-/label/Tech", "https://a.example/feed?x=1&y=2"),
                feed("b", "B", null, null),
                feed("c", "C", "user/-/label/Gone", "https://c.example/rss"),
            ),
        )
        assertTrue(xml, "<title>T &amp; Co</title>" in xml)
        // 文件夹里的订阅源缩进在文件夹下，空文件夹不输出
        assertTrue(xml, "<outline text=\"科技\" title=\"科技\">" in xml)
        assertTrue(xml, "xmlUrl=\"https://a.example/feed?x=1&amp;y=2\"" in xml)
        assertTrue(xml, "text=\"A &lt;b&gt;\"" in xml)
        assertEquals(0, Regex("text=\"空\"").findAll(xml).count())
        // 所属文件夹已不存在的订阅源当作未归类输出；没有 feedUrl 就不写 xmlUrl
        assertEquals(1, Regex("text=\"C\"").findAll(xml).count())
        assertTrue(xml, "<outline type=\"rss\" text=\"B\" title=\"B\" htmlUrl=\"https://b.example\"/>" in xml)
    }
}
