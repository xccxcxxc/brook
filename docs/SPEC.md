# Runnel 规格（权威）

Android 原生 RSS 阅读器，追求 iOS 手感，通过 **Google Reader API** 同步自建的 FreshRSS / Miniflux。
最高要求：**滑动和动画的手感接近 iOS，不卡顿**。

## 1. 页面与交互

| 页面 | 内容 | 手势 / 交互 | 状态 |
|---|---|---|---|
| 登录 | FreshRSS / Miniflux / 其他 GReader；服务器、用户名、API 密码；“演示数据”；公网 http 地址提示明文传输（不拦截，`sync/Cleartext.kt`）。密码只用于登录、不保存；服务器 token 用 Android Keystore 加密（`data/SecretBox.kt`），账号在 `account.xml`、不进系统备份 | — | ✅ |
| 首页（订阅源） | 大标题；“全部未读/星标/文章”；文件夹（可展开）→ 订阅源；未读数；左上设置、右上搜索和“+” | 下拉刷新；底部三段筛选（星标/未读/全部）；同步状态 | ✅ |
| 添加订阅（表单） | 地址（订阅源或网站首页）；选文件夹 | 取消/添加；服务端 quickadd（+ subscription/edit 移入文件夹）后同步 | ✅ |
| 文章列表 | 来源图标+名称+相对时间；标题（3 行）；摘要（2 行）；右侧缩略图 | 右滑/左滑动作可选（无 / 切换已读 / 切换星标 / 在默认浏览器打开 / 分享 / 操作菜单；默认右滑已读、左滑星标，过阈值震动）；长按行弹出浮动菜单（行原地浮起成卡片、背景变暗；标为已读/未读、星标、将以上/以下标为已读、分享；侧滑的“操作菜单”仍是底部菜单）；从底部上拉可选（无 / 进入下一个订阅源〔先把当前列表标为已读，不询问〕 / 全部标为已读，松手后总会离开列表；到底后上拉更粘：橡皮筋只跟手指一半、阈值按手指距离约 150dp，底栏上方出现随拉动渐显的大“∨”〔全部标为已读时是“✓”〕，过阈值震动、变深并出“松手：…”提示）；进入下一个订阅源时新列表从下往上进场、当前列表被顶上去；下拉刷新；“N 篇新文章”提示条；全部标为已读（设置→通用→全部标为已读：操作前先询问〔默认关〕、打开下一个订阅源、按钮位置左/右；默认直接退回上一级）；底栏搜索按钮 → 大标题下展开 Filter 输入框（只过滤当前列表，空格分词、标题/摘要/订阅源名，✕ 清除，空时再点 ✕ 收起；筛选时“全部标为已读”只标筛选结果） | ✅ |
| 阅读页 | WebView 渲染；右上角“…”弹出阅读设置面板（主题、栏、颜色、字体字号行距、标题样式，改了文章即时变）；日期、标题、强调色来源名、作者；正文排版（正文浅灰、加粗/小标题/链接深色、链接无下划线） | 读到底继续上拉 → 下一篇、在顶部下拉 → 上一篇（橡皮筋阻尼，更粘：页面只跟手指一半、阈值按手指距离约 220dp；提示是随拉动渐显放大的大“∧/∨”，过阈值震动、翻转变深，下面是上/下一篇标题）；任意位置右滑跟手返回；底栏：已读、星标、全文、分享、浏览器打开、上一篇/下一篇 | ✅ |
| 设置（表单） | 目录：账号 / 通用 / 外观 / 阅读 / 动画 / 手势 / 手感与性能 / 版本；账号（名称、同步方式、保留已读、Reader View〔按订阅源勾选默认用全文打开〕、订阅源排序、标记已读方式、导出 OPML、移除账号）；通用（打开链接、排序、分组、全部已读、同步）、外观（主题、App 图标〔涟漪 / 页中溪，activity-alias 切换〕、字体字号、显示数量、图片预览、图标、文字预览）、阅读（Reader View 提取器〔只有 Readability〕、栏、颜色、字体、字号行距、标题样式、恢复默认）；帧统计在“手感与性能” | 底部弹出、下层缩小变暗；下拉标题栏或列表顶端继续下拉关闭；二级页面右滑返回 | ✅ |
| 所有页面 | iOS 式页面栈：推入从右滑入、下层左移 30% 并变暗 | 系统返回手势跟手（预测性返回），松手带速度完成/弹回 | ✅ |

待做（按优先级）：
1. （已做：滚动即标为已读、长按行弹出菜单）
2. 首页的全局搜索（`ui/search/SearchScreen`，LIKE 匹配标题/摘要）还是旧样式，等用户给截图再改；FTS 表建了但没用上（中文无分词）
3. 阅读页顶栏/底栏随滚动隐藏
4. OPML 导入导出（远程账号走服务端；预留本地账号）
5. 图片点开全屏查看；多账号

## 2. 架构

```
ui/        Compose 界面：nav（自写页面栈+转场）、home、list、reader、settings、login、components、theme
data/      Room（db/）、Prefs、ArticleRepository、FlagStore（本次运行的已读/星标覆盖层）
sync/      SyncEngine（同步流程）、greader/（GReader API 客户端）、SyncWorker、DemoData
content/   ArticleProcessor（jsoup 清洗/摘要/缩略图/阅读时长）、FullTextFetcher（Readability）
perf/      PerfMonitor（JankStats/StrictMode；按页面、按操作〔推入/返回/下一个订阅源/滚动/同步〕统计，记最近 12 次卡顿；release 包 logcat 标签 BrookPerf）、RefreshRate
```

- **列表快照**：打开列表时一次查出轻量字段（正文不进列表查询），之后不随数据库变化重排；行上的已读/星标以 `FlagStore` 为准。同步有新文章时显示“N 篇新文章”，点了才换新快照。
- **重活放同步时**：HTML 清洗、摘要、缩略图、阅读时长都在同步（Default 线程池）里算好写库；滚动和阅读时不解析 HTML。
- **离线优先**：已读/星标先改内存和数据库并写入 `pending_actions`，3 秒后批量提交；同步开始时先提交再拉取。
- **同步流程**：提交离线操作 → 订阅/文件夹 → 未读 id（≤2 万）、星标 id → 增量 id（`ot`）→ 下载缺失文章（每块 100 篇，3 路并发；Miniflux 要求 POST 带 `T`）→ 按服务端对齐已读/星标（跳过仍有本地操作的）→ 清理已读旧文章。
- **表单（sheet）**：设置、添加订阅不进页面栈，是盖在页面栈上的 `SheetHost`（`ui/components/Sheet.kt`），表单内二级页面用 `SheetPages`；进度只在 graphicsLayer/drawBehind 里读。
- **页面栈**：组合栈顶三层，下层不可见时 alpha=0（仍在组合里，侧滑返回第一帧就有内容）；第三层不画、不进无障碍树，只为返回/“下一个订阅源”结束时不用现组合（2026-10-19 量过：首页现组合一帧约 15–20 ms〔模拟器 UI 线程〕）；转场只改 graphicsLayer。页面状态挂在 `NavEntry.retain()`，出栈时取消协程。

## 3. 动效参数（Motion tokens，`ui/theme/Motion.kt`）

iOS 弹簧 `(response, dampingFraction)` → Compose：`stiffness = (2π / response)²`，`dampingRatio = dampingFraction`。
例：iOS 默认 `spring(response: 0.5, damping: 1)` ≈ `spring(dampingRatio = 1f, stiffness ≈ 158f)`。

| 用途 | response | damping |
|---|---|---|
| 页面推入/返回 | 0.42 s | 1.0 |
| 侧滑返回松手 | 0.35 s | 1.0（带手指速度） |
| 行侧滑回弹、小动作 | 0.30 s | 0.86 |
| 文章切换（竖向） | 0.45 s | 1.0 |
| 拉动切篇松手回位 | 0.35 s | 1.0 |
| 表单弹出/收起 | 0.5 s | 1.0（下层缩到 0.92） |
| 下拉表单松手 | 0.4 s | 1.0（带手指速度） |

**惯性**：iOS UIScrollView 减速率 0.998/ms ⇒ `v(t) = v0·e^(−2.002t)`；Compose `exponentialDecay(frictionMultiplier = 2.002 / 4.2 ≈ 0.477)`。设置里可关。
**回弹**：全局 `LocalOverscrollFactory = MiuixOverscrollFactory`（iOS 式橡皮筋）。

## 4. 性能指标（每轮必测，`scripts/perf.sh bench`）

| 场景 | 指标 | 目标 |
|---|---|---|
| 滚动“全部文章”（5000 篇演示数据） | `frameOverrunMs` P99 | ≤ 0 |
| 同上 | 卡顿帧（JankStats / gfxinfo） | < 1% |
| 冷启动 | `timeToInitialDisplayMs` | < 500 ms |
| 点开文章 | 到首屏内容 | < 150 ms（WebView 预热 + 邻页预渲染） |
| 连续翻 10 篇 | 掉帧 | 0 |

规则：只用 **release / benchmark 包**判断卡不卡（debug 版 Compose 本来就慢）。

## 5. 编码规则（违反就会卡）

1. 滚动/动画相关状态只在 `graphicsLayer {}`、`offset {}`、`drawBehind {}` 等 lambda 里读，不在组合阶段读。
2. 列表项参数用 `@Immutable` 类型，`LazyColumn` 必须给 `key` 和 `contentType`。
3. 主线程不做 IO / HTML 解析；需要的数据在同步时预计算。
4. 图片按显示尺寸解码（Coil `size()`），列表缩略图不淡入。
5. 新增动画参数加到 `Motion`，不要散落魔法数。
6. 阅读页 CSS 不允许横向滚动（代码块换行、表格压缩），否则和右滑返回手势冲突。
7. 手势判断放在 `PointerEventPass.Initial`（先于 WebView），接管后消费事件；跟手的位移手势要挂在**不随转场移动**的节点上（如 NavHost），否则坐标会被自身位移抵消。
8. 组合函数里的回调**不要读组合时的局部变量**（`val built = state.built`、`remember(built) { … }` 这类）：局部函数引用 `::foo` 会被 Compose 编译器记住、不随重组更新，读到的是第一次组合的旧值。回调里只读状态对象（`state.xxx`、`prefs.settings.value`）的实时值。
