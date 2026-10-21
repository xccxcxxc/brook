package io.github.xccxcxxc.brook

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso.pressBack
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xccxcxxc.brook.data.Account
import io.github.xccxcxxc.brook.data.AccountType
import io.github.xccxcxxc.brook.data.OpenLinks
import io.github.xccxcxxc.brook.data.SyncMode
import io.github.xccxcxxc.brook.data.db.ArticleContentEntity
import io.github.xccxcxxc.brook.data.db.ArticleEntity
import io.github.xccxcxxc.brook.data.db.FeedEntity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 阅读页跑在模拟器的真实 WebView 里：首页 → 订阅源 → 点文章进阅读页。
 * 只有一篇文章，阅读页里只有一个 WebView。
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val graph get() = Graph.get()
    private val original = "https://ex.com/post?id=1&x=2"

    @Before
    fun setUp() {
        runBlocking { graph.logout() }
        graph.prefs.updateSettings { it.copy(syncMode = SyncMode.MANUAL, openLinks = OpenLinks.DEFAULT_BROWSER, titleUppercase = false) }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** 72×72 的 PNG（Discourse 表情原图就是这个尺寸），内嵌成 data URI，不依赖网络。 */
    private fun png72(): String {
        val bmp = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val out = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun openArticle(body: String, second: Boolean = false) {
        val now = System.currentTimeMillis()
        runBlocking {
            graph.db.feedDao().upsertFeeds(listOf(FeedEntity("feed/test", "测试源", null, "https://ex.com", null, null, 0)))
            graph.db.articleDao().insertArticles(
                listOf(ArticleEntity(1, "feed/test", "阅读页测试文章", "作者", original, now - 60_000, "摘要", null, 1, false, false, now)),
            )
            graph.db.articleDao().insertContents(listOf(ArticleContentEntity(1, body, null)))
            if (second) {
                // 比第一篇新：“未读”旧→新排，它是下一篇
                graph.db.articleDao().insertArticles(
                    listOf(ArticleEntity(2, "feed/test", "第二篇", "作者", "https://ex.com/2", now, "摘要", null, 1, false, false, now)),
                )
                graph.db.articleDao().insertContents(listOf(ArticleContentEntity(2, "<p>第二篇正文</p>", null)))
            }
        }
        graph.prefs.saveAccount(Account(AccountType.DEMO, "", "demo"))
        scenario = ActivityScenario.launch(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
        rule.waitUntilAtLeastOneExists(hasText("测试源"), 30_000)
        rule.onNodeWithText("测试源").performClick()
        rule.waitUntilAtLeastOneExists(hasText("阅读页测试文章"), 15_000)
        rule.onNodeWithText("阅读页测试文章").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("reader_pager"), 10_000)
        // 页面载入完成、标题链接已经排好版
        waitFor { js("document.readyState=='complete' && !!document.querySelector('a.original') && document.querySelector('h1.title').getBoundingClientRect().height>0") == "true" }
    }

    private fun waitFor(timeoutMs: Long = 15_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        var last: Result<Boolean>? = null
        while (System.currentTimeMillis() < end) {
            last = runCatching(check)
            if (last.getOrDefault(false)) return
            Thread.sleep(100)
        }
        error("等待超时：$last")
    }

    private fun findWebView(v: View): WebView? = when {
        v is WebView && v.isAttachedToWindow -> v
        v is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { findWebView(v.getChildAt(it)) }
        else -> null
    }

    /** 在阅读页的 WebView 里执行脚本，返回 JSON 结果。 */
    private fun js(script: String): String {
        var result: String? = null
        val latch = CountDownLatch(1)
        scenario!!.onActivity { activity ->
            val web = findWebView(activity.window.decorView) ?: return@onActivity latch.countDown()
            web.evaluateJavascript(script) { result = it; latch.countDown() }
        }
        latch.await(5, TimeUnit.SECONDS)
        return result ?: error("没有找到 WebView")
    }

    /** 像手指一样点 WebView 里 [selector] 元素的中心（CSS 像素换算成屏幕像素）。 */
    private fun tapInWebView(selector: String) {
        val rect = JSONArray(js("(function(){var r=document.querySelector('$selector').getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2,window.devicePixelRatio];})()"))
        val ratio = rect.getDouble(2)
        val x = (rect.getDouble(0) * ratio).toFloat()
        val y = (rect.getDouble(1) * ratio).toFloat()
        val at = CoordinatesProvider { view ->
            val loc = IntArray(2).also(view::getLocationOnScreen)
            floatArrayOf(loc[0] + x, loc[1] + y)
        }
        onView(allOf(isAssignableFrom(WebView::class.java), isDisplayed()))
            .perform(GeneralClickAction(Tap.SINGLE, at, Press.FINGER, 0, 0))
    }

    /** 点标题 = 在浏览器里打开原文。真实 WebView 的点击要走到 shouldOverrideUrlLoading。 */
    @Test
    fun tapTitleOpensOriginal() {
        openArticle("<p>正文</p>")
        Intents.init()
        try {
            intending(not(isInternal())).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            tapInWebView("h1.title")
            waitFor { Intents.getIntents().any { it.action == Intent.ACTION_VIEW } }
            intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(original)))
        } finally {
            Intents.release()
        }
    }

    /**
     * 正文里的表情小图（用户截图：linux.do 的 😭 被放大居中）要和文字同一行、和字一样高；
     * 普通图片照旧单独一行。FreshRSS 会去掉 class，所以样本里不带 class。
     */
    @Test
    fun emojiImagesStayInlineAtTextSize() {
        val png = png72()
        openArticle(
            "<p id=\"a\"><img src=\"https://linux.do/images/emoji/twemoji/sob.png?v=12\" title=\":sob:\" alt=\":sob:\">背景：Discourse 表情</p>" +
                "<p id=\"b\"><img src=\"$png\" alt=\":thinking:\">只有短码 alt 的表情</p>" +
                "<p id=\"c\"><img src=\"$png\" alt=\"普通图片\"></p>",
        )
        waitFor { js("document.querySelector('#b img').complete && document.querySelector('#c img').complete") == "true" }
        fun style(sel: String) = JSONArray(
            js("(function(){var i=document.querySelector('$sel');var s=getComputedStyle(i);return [s.display,i.getBoundingClientRect().height,parseFloat(getComputedStyle(document.body).fontSize)];})()"),
        )
        for (sel in listOf("#a img", "#b img")) {
            val s = style(sel)
            assertEquals("$sel display", "inline", s.getString(0))
            assertTrue("$sel 高 ${s.getDouble(1)}px，字号 ${s.getDouble(2)}px", s.getDouble(1) <= s.getDouble(2) * 1.35)
        }
        val normal = style("#c img")
        assertEquals("普通图片 display", "block", normal.getString(0))
        assertEquals("普通图片按原尺寸", 72.0, normal.getDouble(1), 0.5)
    }

    /**
     * 右上角“…”弹出阅读设置面板：在面板里改设置，后面的文章立刻跟着变；返回键收起面板、留在阅读页。
     */
    @Test
    fun moreButtonOpensStylePanel() {
        openArticle("<p>正文</p>")
        rule.onNodeWithContentDescription("阅读设置").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("reader_style_panel"), 5_000)
        rule.waitForIdle() // 等面板升起的动画停下再点
        rule.onNodeWithText("ABC").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue("点了 ABC 设置没变", graph.prefs.settings.value.titleUppercase)
        var last = ""
        runCatching { waitFor { js("getComputedStyle(document.querySelector('h1.title')).textTransform").also { last = it } == "\"uppercase\"" } }
            .onFailure { error("标题样式没变：$last") }
        waitFor { js("getComputedStyle(document.querySelector('h1.title')).textTransform") == "\"uppercase\"" }
        pressBack()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("reader_style_panel")).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("reader_pager").assertExists()
    }

    /** 包着图片的链接（Discourse 的大图链接）下面不显示“域名 →”（用户要求去掉），图片照常显示、点了仍能打开。 */
    @Test
    fun imageLinksShowNoHostLabel() {
        openArticle("<p>正文</p><p><a href=\"https://cdn3.ldstatic.com/original/x.png\"><img src=\"${png72()}\" alt=\"截图\"></a></p>")
        waitFor { js("document.querySelector('article a img').complete") == "true" }
        assertEquals("false", js("document.body.innerText.indexOf('ldstatic')>=0"))
        assertEquals("72", js("Math.round(document.querySelector('article a img').getBoundingClientRect().height)"))
    }

    /** 当前是不是第一篇：顶部“上一篇”提示（透明时也在语义树里）显示“已是第一篇”。 */
    private fun onFirstArticle() = rule.onAllNodes(hasText("已是第一篇")).fetchSemanticsNodes().isNotEmpty()

    /** 在阅读页上从下往上拉 [distance]，[steps] 帧拉完，停住后松手。 */
    private fun pullUp(distance: androidx.compose.ui.unit.Dp, steps: Int, hold: Int) {
        rule.onNodeWithTag("reader_pager").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(centerX, height * 0.8f))
            val dy = distance.toPx() / steps
            repeat(steps) { moveBy(androidx.compose.ui.geometry.Offset(0f, -dy), delayMillis = 16) }
            repeat(hold) { moveBy(androidx.compose.ui.geometry.Offset.Zero, delayMillis = 16) }
            up()
        }
        rule.waitForIdle()
    }

    /**
     * 正文到底后上拉切下一篇要“粘”一些（和列表上拉一样）：快速往上一划约 160dp 不切篇
     * （以前约 140dp 手指距离就切了），慢慢拉够距离才切（2026-10-20）。
     */
    @Test
    fun quickSwipeAtBottomDoesNotSwitchButLongPullDoes() {
        openArticle("<p>很短的正文</p>", second = true)
        assertTrue("应从第一篇开始", onFirstArticle())
        pullUp(160.dp, steps = 6, hold = 0)
        Thread.sleep(800)
        assertTrue("快速一划不该切到下一篇", onFirstArticle())
        pullUp(400.dp, steps = 30, hold = 5)
        rule.waitUntil(5_000) { !onFirstArticle() }
    }

    /**
     * 正文图片走 App 自己的磁盘缓存（ReaderImageCache）：真实 WebView 的图片请求要被拦截下来、存进缓存，
     * 服务器说不让缓存也要存（没网时看过的图照样显示，2026-10-20 用户反馈）。URL 不带扩展名，靠 Accept 头识别。
     */
    @Test
    fun articleImagesGoThroughOwnCache() {
        val dir = ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir.resolve("reader-images")
        dir.deleteRecursively()
        val png = Base64.decode(png72().substringAfter(","), Base64.NO_WRAP)
        val server = mockwebserver3.MockWebServer()
        server.start()
        try {
            server.enqueue(
                mockwebserver3.MockResponse.Builder()
                    .headers(okhttp3.Headers.headersOf("Content-Type", "image/png", "Cache-Control", "no-store"))
                    .body(okio.Buffer().write(png))
                    .build(),
            )
            openArticle("<p>正文</p><p><img id=\"pic\" src=\"${server.url("/img?id=1")}\" alt=\"图\"></p>")
            waitFor { js("document.querySelector('#pic').complete && document.querySelector('#pic').naturalWidth") == "72" }
            assertEquals(1, server.requestCount)
            assertTrue("图片没进缓存：${dir.list()?.toList()}", dir.walk().any { it.isFile && it.name.endsWith(".1") && it.length() == png.size.toLong() })
        } finally {
            server.close()
        }
    }

    private fun scrolledDeep(): WebView {
        openArticle((1..300).joinToString("") { "<p>第 $it 段正文，后面还有一些文字凑够一行。</p>" }, second = true)
        assertTrue(onFirstArticle())
        var web: WebView? = null
        scenario!!.onActivity { web = findWebView(it.window.decorView) }
        val w = web!!
        scenario!!.onActivity { w.scrollTo(0, 4000) }
        waitFor { w.scrollY > 0 }
        return w
    }

    /**
     * 正文滚到深处后 WebView 第一次获得焦点（真机：一直在滑，滑到深处后按住图片），三星真机上随后有人直接调用
     * 父容器的 requestChildRectangleOnScreen，传入 WebView 带滚动距离的整屏矩形（顶部 = scrollY）。Compose 把它转成
     * bringIntoView，翻页器以为要往下滚好几屏，一闪跳到后面的文章（2026-10-20 真机日志：scrollY=35912 时从第 1 篇
     * 跳到第 4 篇）。模拟器上获得焦点不会触发这次调用，这里直接重放。
     */
    @Test
    fun focusedRectFromParentWhileScrolledDoesNotSwitch() {
        val w = scrolledDeep()
        scenario!!.onActivity {
            val drawing = android.graphics.Rect().also(w::getDrawingRect)
            (w.parent as android.view.ViewGroup).requestChildRectangleOnScreen(w, drawing, false)
        }
        Thread.sleep(1000)
        rule.waitForIdle()
        assertTrue("翻到了别的文章", onFirstArticle())
    }

    /** 同上，WebView 请求“把可见的一块显示出来”（无障碍服务等会这样做）也不能翻走。 */
    @Test
    fun webViewRectRequestWhileScrolledDoesNotSwitch() {
        val w = scrolledDeep()
        scenario!!.onActivity { w.requestRectangleOnScreen(android.graphics.Rect(0, w.scrollY + 100, w.width, w.scrollY + 300), true) }
        Thread.sleep(1000)
        rule.waitForIdle()
        assertTrue("请求可见区域后翻到了下一篇", onFirstArticle())
    }
}
