package io.github.xccxcxxc.brook.sync

import io.github.xccxcxxc.brook.data.db.FeedEntity
import io.github.xccxcxxc.brook.data.db.FolderEntity

/** 把本地的订阅源和文件夹导出成 OPML 2.0（和别的阅读器互通）。 */
object Opml {
    fun build(title: String, folders: List<FolderEntity>, feeds: List<FeedEntity>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">\n")
        sb.append("  <head><title>").append(esc(title)).append("</title></head>\n  <body>\n")
        val folderIds = folders.mapTo(HashSet()) { it.id }
        for (f in folders.sortedBy { it.sortIndex }) {
            val inside = feeds.filter { it.folderId == f.id }
            if (inside.isEmpty()) continue
            sb.append("    <outline text=\"").append(esc(f.title)).append("\" title=\"").append(esc(f.title)).append("\">\n")
            inside.forEach { sb.append("      ").append(outline(it)).append('\n') }
            sb.append("    </outline>\n")
        }
        feeds.filter { it.folderId == null || it.folderId !in folderIds }.forEach { sb.append("    ").append(outline(it)).append('\n') }
        sb.append("  </body>\n</opml>\n")
        return sb.toString()
    }

    private fun outline(f: FeedEntity): String = buildString {
        append("<outline type=\"rss\" text=\"").append(esc(f.title)).append("\" title=\"").append(esc(f.title)).append('"')
        f.feedUrl?.let { append(" xmlUrl=\"").append(esc(it)).append('"') }
        f.siteUrl?.let { append(" htmlUrl=\"").append(esc(it)).append('"') }
        append("/>")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
