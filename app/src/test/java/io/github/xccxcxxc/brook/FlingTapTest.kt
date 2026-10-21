package io.github.xccxcxxc.brook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import io.github.xccxcxxc.brook.data.Settings
import io.github.xccxcxxc.brook.ui.components.BrookIcons
import io.github.xccxcxxc.brook.ui.components.SwipeAction
import io.github.xccxcxxc.brook.ui.components.SwipeRow
import io.github.xccxcxxc.brook.ui.components.pressHighlight
import io.github.xccxcxxc.brook.ui.theme.BrookTheme
import io.github.xccxcxxc.brook.ui.theme.listFling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** 列表快速滑动后点一下让它停住：只应该停住，不能打开点到的那一行（2026-10-20 真机反馈）。 */
@RunWith(RobolectricTestRunner::class)
// 开发机是 arm64 Linux，Robolectric 的原生运行时不支持，用纯 Java 的旧实现
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class FlingTapTest {
    @get:Rule
    val rule = createComposeRule()

    private val clicked = mutableListOf<Int>()
    private lateinit var listState: LazyListState

    @Test
    fun tapDuringFlingOnlyStops() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            // 和列表页一样：iOS 惯性 + 可左右滑的 SwipeRow + pressHighlight 点击
            BrookTheme(Settings()) {
                listState = rememberLazyListState()
                LazyColumn(Modifier.fillMaxSize().testTag("list"), state = listState, flingBehavior = listFling()) {
                    items(200) { i ->
                        SwipeRow(start = SwipeAction(BrookIcons.Circle, Color.Blue) {}, end = SwipeAction(BrookIcons.Star, Color.Yellow) {}) {
                            Box(Modifier.fillMaxWidth().height(80.dp).pressHighlight(onLongClick = {}) { clicked += i })
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 80) }
        rule.mainClock.advanceTimeBy(150)
        assertTrue("fling should be running", listState.isScrollInProgress)
        rule.onNodeWithTag("list").performTouchInput {
            down(center)
            advanceEventTime(60)
            up()
        }
        rule.mainClock.advanceTimeBy(1000)
        assertEquals(emptyList<Int>(), clicked)
    }
}
