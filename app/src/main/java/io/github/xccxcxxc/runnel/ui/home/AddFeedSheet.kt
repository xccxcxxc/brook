package io.github.xccxcxxc.runnel.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xccxcxxc.runnel.Graph
import io.github.xccxcxxc.runnel.ui.components.BarTextButton
import io.github.xccxcxxc.runnel.ui.components.CheckRow
import io.github.xccxcxxc.runnel.ui.components.GroupedSection
import io.github.xccxcxxc.runnel.ui.components.RowDivider
import io.github.xccxcxxc.runnel.ui.components.SheetPage
import io.github.xccxcxxc.runnel.ui.theme.Runnel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 添加订阅（首页右上角 “+”）：填地址、选文件夹，交给服务器订阅后同步一次。 */
@Composable
fun AddFeedSheet(onDone: () -> Unit) {
    val graph = Graph.get()
    val colors = Runnel.colors
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val account by graph.prefs.account.collectAsStateWithLifecycle()
    val folders by graph.repo.folders.collectAsStateWithLifecycle(emptyList())
    var url by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val remote = account?.isRemote == true
    val focus = remember { FocusRequester() }

    // 表单滑上来之后再弹键盘：两个动画叠在一起会掉帧
    LaunchedEffect(remote) {
        if (!remote) return@LaunchedEffect
        delay(450)
        runCatching { focus.requestFocus() }
    }

    fun close() {
        focusManager.clearFocus()
        onDone()
    }

    fun add() {
        if (busy || !remote || url.isBlank()) return
        busy = true
        error = null
        scope.launch {
            runCatching { graph.sync.addSubscription(url, folder) }
                .onSuccess { close() }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    SheetPage(
        title = "添加订阅",
        leading = { BarTextButton("取消", color = colors.accent, onClick = ::close) },
        trailing = {
            if (busy) {
                CircularProgressIndicator(Modifier.padding(horizontal = 18.dp).size(20.dp), color = colors.accent, strokeWidth = 2.dp)
            } else {
                BarTextButton("添加", color = colors.accent, style = Runnel.type.headline, enabled = remote && url.isNotBlank(), onClick = ::add)
            }
        },
    ) {
        item(key = "url") {
            GroupedSection(
                footer = if (remote) "填订阅源地址，也可以直接填网站首页地址，服务器会从网页里找到订阅源。"
                else "演示数据不能添加订阅。请在设置 → 账号里退出登录，连接自己的 FreshRSS 或 Miniflux。",
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (url.isEmpty()) Text("https://example.com/feed", style = Runnel.type.body, color = colors.tertiaryLabel)
                        BasicTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                error = null
                            },
                            enabled = remote && !busy,
                            singleLine = true,
                            textStyle = Runnel.type.body.copy(color = colors.label),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("add_feed_url"),
                        )
                    }
                }
            }
            error?.let {
                Text(it, style = Runnel.type.footnote, color = colors.destructive, modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp))
            }
        }
        if (remote && folders.isNotEmpty()) {
            item(key = "folders") {
                GroupedSection("文件夹") {
                    CheckRow("默认", folder == null) { folder = null }
                    for (f in folders) {
                        RowDivider()
                        CheckRow(f.title, folder == f.id) { folder = f.id }
                    }
                }
            }
        }
    }
}
