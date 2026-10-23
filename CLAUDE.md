# CLAUDE.md — Runnel 工程导航

> 2026-10-21 起对外名称是 **Runnel**（原名 Brook，和 txthinking/brook 撞名）。2026-10-23 包名、证书、类名、`runnel://` 链接、CI 的 `RUNNEL_*` Secrets 也都统一改成 Runnel（包名 `io.github.xccxcxxc.runnel`，证书 CN=Runnel）；只有模拟器 AVD `brook36` 因为是所有项目共用的，名字不变。**不要再改包名和证书**（上架 Play 后也改不了）。

Android RSS 阅读器（Kotlin + Compose），同步 FreshRSS / Miniflux（Google Reader API）。
**先读 `docs/HANDOFF.md`（当前状态与待办），再读 `docs/SPEC.md`**（页面、架构、动效参数、性能指标、编码规则）。用户最看重：手感接近 iOS、不卡。

## 构建（Windows 本地，PowerShell；多条命令用 `;` 连接）
- 环境（已设好用户级环境变量）：`JAVA_HOME` 指向 JDK 21（编译和测试都用 21，产物字节码 17），`ANDROID_HOME` 指向 Android SDK（platforms;android-37.0、build-tools;37.0.0、platform-tools、emulator、system-images;android-36;google_apis;x86_64）。已开着的终端/会话若报 “SDK location not found”，先 `$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"; .\gradlew.bat --stop`。
- 编译：`.\gradlew.bat :app:assembleRelease`（有 `%USERPROFILE%\.android\runnel-release.properties` 或 CI 的 `RUNNEL_KEYSTORE` 时用正式证书签名，否则退回 debug 证书；正式证书签的包可以给用户装，debug 证书的不行）。产物 `app\build\outputs\apk\release\app-release.apk`；验签：`& "$env:ANDROID_HOME\build-tools\37.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk`，正式包的 DN 是 `CN=Runnel, O=xccxcxxc`。
- 单元测试：`.\gradlew.bat :app:testDebugUnitTest`（纯逻辑 + 不碰数据库的 Robolectric 组件测试，首次约 5 分钟）。碰数据库、WebView、整页交互的测试是模拟器上的仪器测试（`app/src/androidTest`），**只用 `pwsh scripts/connected-test.ps1 [-Class 类名或类名#方法,…]` 跑**：自动开 brook36、只装到 brook36（手机连着也不碰）、超过 10 分钟直接杀掉模拟器和 Gradle（用户硬性要求），最后列出每组用时和失败。全套约 2 分钟；平时只跑和改动相关的类。改交互的 bug 先写测试复现。测试必须和生产一致，不要为了测试换组件或加测试分支。不做服务器联调测试，和服务器交互的功能由用户真机验证。
- 模拟器：AVD 名 `brook36`（API 36 / Android 16 google_apis x86_64，pixel_7 机型，GPU host，4 GB 内存，6 GB 数据分区；API 36.1 镜像在本机模拟器上 SurfaceFlinger 反复崩溃，别用）。启动：`Start-Process "$env:ANDROID_HOME\emulator\emulator.exe" -ArgumentList "-avd brook36 -no-snapshot -no-audio"`；`& "$env:ANDROID_HOME\platform-tools\adb.exe" wait-for-device` 后查 `adb shell getprop sys.boot_completed` 为 1，且 `adb shell dumpsys user` 里是 `RUNNING_UNLOCKED`，才算启动完（`scripts/connected-test.ps1` 会自动做）。所有项目共用这台；别用无界面模式开的模拟器，冷启动慢 10 倍。模拟器帧率不代表真机性能。
- 真机连接见 `docs/DEVICE.md`，测量用 `scripts/perf.sh`（Windows 上 adb 可直连手机）。

## 版本选择
AGP 9.4.1、Kotlin 2.4.20、Compose BOM 2026.09.00、Miuix 0.9.4、Room 2.7.1（见 `gradle/libs.versions.toml`），都验证过能编译；升级前先单独验证。

## 模型策略（进入对应工作前提醒用户切换）
| 工作 | 建议 |
|---|---|
| 同步引擎、页面栈/手势、阅读器、卡顿排查（Perfetto） | Opus high（个别难题 xhigh） |
| 普通页面、设置项、OPML、搜索、文案 | Sonnet medium–high |

## 提交
- 顺序：自测通过 → **先出测试包**（`assembleRelease`，告诉用户改了什么、怎么测）→ 再提交代码、写文档。
- 只在 `main` 上提交并推送，不建分支（要建先问用户）。

## 提交说明 = 发布说明
CI 把“自上一个包以来的提交标题和正文”原样写进 GitHub Release 的说明，用户下载包后靠它知道改了什么、测什么。所以每个改变行为的提交，正文里必须有：
- **改了什么**（用户能感知的变化，不写内部重构细节）；
- **真机怎么测**：具体到操作步骤和预期现象，例如“Reader View 页勾选订阅源 → 打开该源文章 → 应自动出全文”；
- 没法验证的写明“未在真机验证”。
纯文档/测试提交可以只写一行。回复用户时也同样列出这两项。

