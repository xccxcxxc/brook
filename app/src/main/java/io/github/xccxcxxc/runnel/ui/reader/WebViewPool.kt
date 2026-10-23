package io.github.xccxcxxc.runnel.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import io.github.xccxcxxc.runnel.Graph
import io.github.xccxcxxc.runnel.content.ReaderImageCache
import io.github.xccxcxxc.runnel.data.OpenLinks

/**
 * WebView 池：第一次创建 WebView 要初始化 Chromium（100–300ms），放在启动后的空闲时刻提前做，
 * 打开文章时直接从池里拿。用 MutableContextWrapper 在 Activity 和 Application 之间切换，避免泄漏。
 */
object WebViewPool {
    private const val MAX_POOLED = 3
    private val pool = ArrayDeque<WebView>()

    /** 首帧之后、主线程空闲时预热 [count] 个。 */
    fun prewarm(context: Context, count: Int = 2) {
        val app = context.applicationContext
        Looper.myQueue().addIdleHandler {
            if (pool.size < count) pool.addLast(create(app))
            pool.size < count // 每次空闲只建一个，建够为止
        }
    }

    fun obtain(context: Context): WebView {
        val view = pool.removeFirstOrNull() ?: create(context.applicationContext)
        (view.context as MutableContextWrapper).baseContext = context
        return view
    }

    fun recycle(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        view.setOnScrollChangeListener(null)
        view.tag = null
        view.scrollTo(0, 0)
        (view.context as MutableContextWrapper).baseContext = view.context.applicationContext
        if (pool.size < MAX_POOLED) {
            view.loadUrl("about:blank")
            view.clearHistory()
            pool.addLast(view)
        } else {
            view.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(app: Context): WebView = WebView(MutableContextWrapper(app)).apply {
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        // 不要系统的拉伸回弹：到边缘继续拉是“切换上一篇/下一篇”，由阅读页自己画
        overScrollMode = View.OVER_SCROLL_NEVER
        settings.apply {
            // 正文里的脚本在同步时已经清掉；开 JS 只为嵌入的视频 iframe
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            loadsImagesAutomatically = true
        }
        webViewClient = ReaderClient
    }

    private object ReaderClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            return handleLink(view.context, request.url)
        }

        // 图片走 App 自己的磁盘缓存（ReaderImageCache），没网时看过的图照样显示；在后台线程调用，可以阻塞
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (request.isForMainFrame) return null
            val url = request.url
            if (!ReaderImageCache.isImageRequest(request.method, url.scheme, url.path, request.requestHeaders)) return null
            return Graph.get().readerImages.fetch(url.toString(), request.requestHeaders)
        }
    }

    /** 阅读页里点了链接：标题（打开原文）和正文链接都按“打开链接”设置在浏览器里打开。返回 true 表示已处理。 */
    fun handleLink(context: Context, url: Uri): Boolean {
        if (url.scheme == "about" || url.scheme == "data") return false
        val target = if (url.scheme == ReaderHtml.ORIGINAL_SCHEME) url.getQueryParameter("u")?.let(Uri::parse) ?: return true else url
        openInBrowser(context, target)
        return true
    }

    /** 按设置里的“打开链接”：系统默认浏览器，或应用内的浏览器标签页（Custom Tabs）。 */
    fun openInBrowser(context: Context, uri: Uri) {
        runCatching {
            if (Graph.get().prefs.settings.value.openLinks == OpenLinks.IN_APP) {
                val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
                // 不是 Activity 的 context（比如回到池里的 WebView）启动不了新页面，系统会直接拒绝
                if (context.findActivity() == null) tabs.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                tabs.launchUrl(context, uri)
            } else {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
