package io.github.xccxcxxc.brook.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.brook.Graph
import io.github.xccxcxxc.brook.data.AccountType
import io.github.xccxcxxc.brook.ui.components.GroupedSection
import io.github.xccxcxxc.brook.ui.components.RowDivider
import io.github.xccxcxxc.brook.ui.components.SegmentedRow
import io.github.xccxcxxc.brook.ui.components.pressHighlight
import io.github.xccxcxxc.brook.ui.theme.Brook
import io.github.xccxcxxc.brook.sync.isPublicCleartext
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(autoDemo: Boolean, onLoggedIn: () -> Unit) {
    val graph = Graph.get()
    val colors = Brook.colors
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf(AccountType.FRESHRSS) }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { block() }
                .onSuccess { onLoggedIn() }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    // macrobenchmark 启动时带参数：直接进演示数据
    LaunchedEffect(autoDemo) { if (autoDemo) run { graph.loginDemo() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.groupedBackground)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Spacer(Modifier.height(36.dp))
        Text("Runnel", style = Brook.type.largeTitle, color = colors.label, modifier = Modifier.padding(horizontal = 32.dp))
        Text(
            "连接你的 FreshRSS 或 Miniflux 服务器",
            style = Brook.type.subhead,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
        )
        GroupedSection(
            footer = when (type) {
                AccountType.FRESHRSS -> "地址填 FreshRSS 首页地址（会自动加上 /api/greader.php）。需要在 FreshRSS「设置 → 认证」里允许 API 访问，并在「设置 → 账户」里设置 API 密码，这里填 API 密码。"
                AccountType.MINIFLUX -> "地址填 Miniflux 首页地址。在 Miniflux「设置 → 集成 → Google Reader」里启用，并设置单独的用户名和密码，这里填那一组。"
                else -> "填 Google Reader 兼容 API 的根地址（/accounts/ClientLogin 的上一级）。"
            },
        ) {
            SegmentedRow(
                null,
                listOf(AccountType.FRESHRSS to "FreshRSS", AccountType.MINIFLUX to "Miniflux", AccountType.GREADER to "其他 GReader"),
                type,
            ) { type = it }
            RowDivider()
            Field("服务器", server, "https://rss.example.com", KeyboardType.Uri) { server = it }
            RowDivider()
            Field("用户名", user, "username", KeyboardType.Text) { user = it }
            RowDivider()
            Field("密码", pass, "API 密码", KeyboardType.Password, secret = true) { pass = it }
        }
        if (isPublicCleartext(server)) {
            Text(
                "这个地址用的是 http，登录时的密码和之后的内容都会明文传输。服务器在公网上的话，建议改用 https。",
                style = Brook.type.footnote,
                color = colors.destructive,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp).testTag("cleartext_warning"),
            )
        }
        error?.let {
            Text(it, style = Brook.type.footnote, color = colors.destructive, modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp))
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = if (busy) "正在连接…" else "登录",
            busy = busy,
            enabled = server.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.testTag("login_button"),
        ) { run { graph.login(type, server, user, pass) } }
        Spacer(Modifier.height(10.dp))
        Text(
            "先用演示数据看看",
            style = Brook.type.body,
            color = colors.accent,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(8.dp))
                .pressHighlight { run { graph.loginDemo() } }
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("demo_button"),
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    placeholder: String,
    keyboard: KeyboardType,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    val colors = Brook.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Brook.type.body, color = colors.label, modifier = Modifier.width(72.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = Brook.type.body, color = colors.tertiaryLabel)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = Brook.type.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, busy: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Brook.colors
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) colors.accent else colors.highlight)
            .then(if (enabled && !busy) Modifier.pressHighlight(onClick) else Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = Brook.type.headline, color = if (enabled) Color.White else colors.tertiaryLabel)
    }
}
