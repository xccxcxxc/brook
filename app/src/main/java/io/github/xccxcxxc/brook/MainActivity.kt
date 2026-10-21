package io.github.xccxcxxc.brook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xccxcxxc.brook.data.SyncMode
import io.github.xccxcxxc.brook.perf.PerfMonitor
import io.github.xccxcxxc.brook.perf.RefreshRate
import io.github.xccxcxxc.brook.sync.SyncState
import io.github.xccxcxxc.brook.ui.components.SheetHost
import io.github.xccxcxxc.brook.ui.components.SheetState
import io.github.xccxcxxc.brook.ui.components.behindSheet
import io.github.xccxcxxc.brook.ui.home.AddFeedSheet
import io.github.xccxcxxc.brook.ui.home.HomeScreen
import io.github.xccxcxxc.brook.ui.list.ArticleListScreen
import io.github.xccxcxxc.brook.ui.login.LoginScreen
import io.github.xccxcxxc.brook.ui.nav.NavHost
import io.github.xccxcxxc.brook.ui.nav.Navigator
import io.github.xccxcxxc.brook.ui.nav.Route
import io.github.xccxcxxc.brook.ui.reader.ReaderScreen
import io.github.xccxcxxc.brook.ui.reader.WebViewPool
import io.github.xccxcxxc.brook.ui.search.SearchScreen
import io.github.xccxcxxc.brook.ui.settings.SettingsSheet
import io.github.xccxcxxc.brook.ui.theme.Brook
import io.github.xccxcxxc.brook.ui.theme.BrookTheme
import io.github.xccxcxxc.brook.ui.theme.Motion

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = Graph.get()
        val autoDemo = intent.getBooleanExtra(EXTRA_DEMO, false)
        PerfMonitor.install(this)
        RefreshRate.apply(this, graph.prefs.settings.value.maxRefreshRate)
        setContent { BrookRoot(this, autoDemo) }
        // 首帧之后空闲时预热 WebView，第一次打开文章不用等 Chromium 初始化
        window.decorView.post { WebViewPool.prewarm(this) }
    }

    override fun onStart() {
        super.onStart()
        // 同步方式为“手动”时不自动同步
        if (Graph.get().prefs.settings.value.syncMode != SyncMode.MANUAL) Graph.get().sync.syncIfStale()
    }

    override fun onResume() {
        super.onResume()
        PerfMonitor.setTracking(true)
    }

    override fun onPause() {
        PerfMonitor.setTracking(false)
        super.onPause()
    }

    companion object {
        /** macrobenchmark 用：没有账号时自动载入演示数据。 */
        const val EXTRA_DEMO = "brook.demo"
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BrookRoot(activity: MainActivity, autoDemo: Boolean) {
    val graph = Graph.get()
    val settings by graph.prefs.settings.collectAsStateWithLifecycle()

    // 设置 → 动画 → 速度：全局缩放所有弹簧动画
    SideEffect { Motion.speed = settings.animSpeed.factor }
    LaunchedEffect(settings.maxRefreshRate) {
        RefreshRate.apply(activity, settings.maxRefreshRate)
        PerfMonitor.updateRefreshRate(activity)
    }
    // 帧统计：卡顿时是否正在同步
    LaunchedEffect(Unit) { graph.sync.state.collect { PerfMonitor.setSyncing(it is SyncState.Running) } }

    BrookTheme(settings) {
        val scope = rememberCoroutineScope()
        val navigator = remember {
            Navigator(if (graph.prefs.account.value == null) Route.Login else Route.Home, scope)
        }
        val sheet = remember { SheetState<Sheet>(scope) }
        val dark = Brook.colors.isDark
        val view = LocalView.current
        SideEffect {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            // 表单弹出后状态栏后面是黑底（下层页面缩小露出的部分），图标改成浅色
            controller.isAppearanceLightStatusBars = !dark && !sheet.isOpen
            controller.isAppearanceLightNavigationBars = !dark
        }
        LaunchedEffect(navigator.top, sheet.content) {
            PerfMonitor.setScreen(
                when (sheet.content) {
                    Sheet.SETTINGS -> "Settings"
                    Sheet.ADD_FEED -> "AddFeed"
                    null -> navigator.top.route.perfName
                },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brook.colors.background)
                .drawBehind { if (sheet.progress > 0f) drawRect(Color.Black, alpha = sheet.progress) }
                .semantics { testTagsAsResourceId = true },
        ) {
            Box(Modifier.fillMaxSize().behindSheet(sheet)) {
                NavHost(navigator) { entry ->
                    when (val route = entry.route) {
                        Route.Login -> LoginScreen(autoDemo) { navigator.replaceAll(Route.Home) }
                        Route.Home -> HomeScreen(
                            onOpen = { source, filter -> navigator.push(Route.Articles(source, filter)) },
                            onSettings = { sheet.show(Sheet.SETTINGS) },
                            onSearch = { navigator.push(Route.Search) },
                            onAdd = { sheet.show(Sheet.ADD_FEED) },
                        )
                        is Route.Articles -> ArticleListScreen(
                            source = route.source,
                            filter = route.filter,
                            onBack = navigator::pop,
                            onOpenArticle = { navigator.push(Route.Reader(it)) },
                            onReplace = { source, filter -> navigator.replaceTop(Route.Articles(source, filter)) },
                        )
                        is Route.Reader -> ReaderScreen(route.session, onBack = navigator::pop)
                        Route.Search -> SearchScreen(
                            onBack = navigator::pop,
                            onOpenArticle = { navigator.push(Route.Reader(it)) },
                        )
                    }
                }
            }
            SheetHost(sheet) { kind ->
                when (kind) {
                    Sheet.SETTINGS -> SettingsSheet(
                        onDone = { sheet.dismiss() },
                        onLoggedOut = {
                            sheet.hideNow()
                            navigator.replaceAll(Route.Login)
                        },
                    )
                    Sheet.ADD_FEED -> AddFeedSheet(onDone = { sheet.dismiss() })
                }
            }
        }
    }
}

/** 从底部弹出的表单（设置、添加订阅都是表单，不是推入的页面）。 */
private enum class Sheet { SETTINGS, ADD_FEED }
