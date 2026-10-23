package io.github.xccxcxxc.runnel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.runnel.ui.theme.Runnel

val BarHeight: Dp = 44.dp
val ToolbarHeight: Dp = 48.dp

/**
 * 顶栏：iOS 的导航栏。[collapse] 为 0 时透明（大标题还在列表里），为 1 时显示底色、分隔线和小标题。
 * [collapse] 只在 graphicsLayer / drawBehind 里读，滚动时不重组。
 */
@Composable
fun TopBar(
    title: String,
    collapse: State<Float>,
    modifier: Modifier = Modifier,
    navigation: @Composable (BoxScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = Runnel.colors
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val c = collapse.value
                drawRect(colors.bar, alpha = c)
                drawLine(colors.separator, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f, alpha = c)
            }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(BarHeight),
    ) {
        if (navigation != null) Box(Modifier.align(Alignment.CenterStart), content = navigation)
        Text(
            title,
            style = Runnel.type.headline,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 88.dp)
                .graphicsLayer { alpha = ((collapse.value - 0.6f) / 0.4f).coerceIn(0f, 1f) },
        )
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** 底部工具栏（半透明底色 + 顶部分隔线），自动让出导航栏高度。 */
@Composable
fun BottomToolbar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = Runnel.colors
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(colors.bar)
                drawLine(colors.separator, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
            }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(ToolbarHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** iOS 风格的图标按钮：没有水波纹，按下时变淡。 */
@Composable
fun BarButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Runnel.colors.icon,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .size(44.dp)
            .clickable(source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = if (!enabled) 0.3f else if (pressed) 0.35f else 1f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun BarTextButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Runnel.colors.icon,
    style: TextStyle = Runnel.type.body,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .height(44.dp)
            .clickable(source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = color.copy(alpha = if (!enabled) 0.3f else if (pressed) 0.35f else 1f))
    }
}

/** 列表行按下时的灰色高亮（代替水波纹）。 */
@Composable
fun Modifier.pressHighlight(onClick: () -> Unit): Modifier = pressHighlight(null, onClick)

/** 同上，另外支持长按（[onLongClick] 为 null 时和普通点击一样）。 */
@Composable
fun Modifier.pressHighlight(onLongClick: (() -> Unit)?, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val highlight = Runnel.colors.highlight
    return this
        .background(if (pressed) highlight else Color.Transparent)
        .then(
            if (onLongClick == null) Modifier.clickable(source, indication = null, onClick = onClick)
            else Modifier.combinedClickable(source, indication = null, onLongClick = onLongClick, onClick = onClick),
        )
}
