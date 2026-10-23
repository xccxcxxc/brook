package io.github.xccxcxxc.runnel.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AccountType(val label: String) {
    FRESHRSS("FreshRSS"),
    MINIFLUX("Miniflux"),
    GREADER("Google Reader API"),
    DEMO("演示数据"),
}

@Immutable
data class Account(
    val type: AccountType,
    /** 已规范化的 GReader API 根地址（不含 /reader/api/0）。演示账号为空。 */
    val apiBase: String,
    val username: String,
    /** 用户给账号起的名字（首页大标题）；空 = 用服务器主机名。 */
    val description: String = "",
) {
    val isRemote: Boolean get() = type != AccountType.DEMO
}

/** “保留已读文章”能选的值（天；-1 一直保留，0 不保留）。 */
val KEEP_DAYS_OPTIONS = listOf(-1, 0, 1, 2, 3, 7, 14, 30)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 点链接怎么打开：系统默认浏览器 / 应用内的浏览器标签页（Custom Tabs）。 */
enum class OpenLinks { DEFAULT_BROWSER, IN_APP }

/** 文章列表的分组方式。 */
enum class GroupBy { DATE, FEED, NONE }

/**
 * 旧版的全局“阅读视图”开关：READABILITY = 所有订阅源都自动用 Reader View（全文）打开。
 * 新版改为按订阅源选（[Settings.readerViewFeeds]），在 Reader View 页改动后写回 DEFAULT。
 */
enum class ReaderView { DEFAULT, READABILITY }

/** 同步时机：手动 / 启动时 / 后台刷新。 */
enum class SyncMode { MANUAL, ON_START, BACKGROUND }

/** 首页订阅源排序。 */
enum class SortSubs { ALPHA, FEEDS_FIRST, FOLDERS_FIRST, UNREAD }

/** 列表行侧滑动作。 */
enum class SwipeKind { NONE, TOGGLE_READ, TOGGLE_STAR, OPEN_BROWSER, SHARE, ACTION_SHEET }

/** 列表拉到底继续上拉。 */
enum class PullBottom { NONE, NEXT_FEED, MARK_ALL_READ }

/** 动画速度。 */
enum class AnimSpeed(val factor: Float) { SLOW(0.7f), DEFAULT(1f), FAST(1.5f), FASTER(2.2f) }

enum class ReaderFont { DEFAULT, SERIF, MONO }

enum class AppFont { SANS, SERIF }

/** 列表缩略图大小。 */
enum class ImagePreview { NONE, SMALL, MEDIUM, LARGE }

@Immutable
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    // 阅读
    val readerView: ReaderView = ReaderView.DEFAULT,
    /** 默认用 Reader View（自动抓全文）打开的订阅源 id。 */
    val readerViewFeeds: Set<String> = emptySet(),
    /** 同步后预先下载正文图片（离线阅读）的订阅源 id（设置 → 账号 → 离线图片）。 */
    val offlineImageFeeds: Set<String> = emptySet(),
    val autoHideBars: Boolean = true,
    val coloredLinks: Boolean = false,
    val highContrast: Boolean = false,
    val readerFont: ReaderFont = ReaderFont.DEFAULT,
    val useDefaultFontSize: Boolean = false,
    val readerFontSize: Int = 20,
    val readerLineSpacing: Float = 1.8f,
    val titleSystemFont: Boolean = false,
    val titleScale: Float = 1.5f,
    val titleBold: Boolean = true,
    val titleUppercase: Boolean = false,
    val titleCenter: Boolean = false,
    // 账号
    val syncMode: SyncMode = SyncMode.BACKGROUND,
    /** 已读文章保留天数：-1 = 一直保留，0 = 不保留（下次同步清掉），其余为天数。 */
    val keepDays: Int = 30,
    val sortSubs: SortSubs = SortSubs.FOLDERS_FIRST,
    // 手势
    val swipeRight: SwipeKind = SwipeKind.TOGGLE_READ,
    val swipeLeft: SwipeKind = SwipeKind.TOGGLE_STAR,
    /** 列表拉到底继续上拉松手后做什么（默认全部标为已读，和之前的行为一致）。 */
    val pullBottom: PullBottom = PullBottom.MARK_ALL_READ,
    /** 阅读页拉动切换上一篇/下一篇。 */
    val pullToSwitch: Boolean = true,
    // 动画
    val animSpeed: AnimSpeed = AnimSpeed.DEFAULT,
    val animArticleSwitch: Boolean = true,
    val animListLoad: Boolean = true,
    // 通用
    val openLinks: OpenLinks = OpenLinks.DEFAULT_BROWSER,
    val markReadOnOpen: Boolean = true,
    val markReadOnScroll: Boolean = false,
    val oldestFirstUnread: Boolean = true,
    val groupBy: GroupBy = GroupBy.DATE,
    val autoNextFeed: Boolean = false,
    /** 列表页“全部标为已读”：先弹窗确认。 */
    val markAllAsk: Boolean = false,
    /** 全部标为已读后打开下一个有内容的订阅源/文件夹，而不是退回上一级。 */
    val markAllOpenNext: Boolean = false,
    /** “全部标为已读”按钮放在底栏左边（默认右边，搜索在另一边）。 */
    val markAllButtonLeft: Boolean = false,
    // 外观
    val grayFavicons: Boolean = false,
    val appFont: AppFont = AppFont.SANS,
    val useSystemFontSize: Boolean = true,
    /** App 字号档位 0..4（XS S M L XL），“跟随系统字号”关闭时生效。 */
    val appFontStep: Int = 2,
    val countStarred: Boolean = true,
    val countUnread: Boolean = true,
    val countAll: Boolean = true,
    val imagePreview: ImagePreview = ImagePreview.MEDIUM,
    val showFavicons: Boolean = true,
    val textPreview: Boolean = true,
    // 手感
    val iosFling: Boolean = true,
    val maxRefreshRate: Boolean = true,
    // 同步
    /** 后台同步间隔（分钟），只在同步方式为“后台刷新”时用。 */
    val syncIntervalMinutes: Int = 60,
) {
    /** 这个订阅源的文章是否默认用 Reader View（全文）打开；旧版全局 Readability 等于全选。 */
    fun autoReaderView(feedId: String): Boolean = readerView == ReaderView.READABILITY || feedId in readerViewFeeds
}

/**
 * 账号和设置都放 SharedPreferences（量小，读一次进内存），对外暴露 StateFlow。
 * 账号单独放 `account` 文件，不进系统备份（`res/xml/backup_rules.xml`、`data_extraction_rules.xml`）；
 * 密码只在登录时用一次、不保存，服务器发的 token 用 [SecretBox]（Android Keystore）加密后保存。
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("runnel", Context.MODE_PRIVATE)
    private val acc: SharedPreferences = context.getSharedPreferences(ACCOUNT_FILE, Context.MODE_PRIVATE)

    /** 解密后的 token，第一次 [authToken] 时填上。 */
    @Volatile private var cachedToken: String? = null

    init {
        migrateLegacyAccount()
    }

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    var lastSyncAt: Long
        get() = sp.getLong("lastSyncAt", 0L)
        set(value) = sp.edit { putLong("lastSyncAt", value) }

    /** 上次同步时服务端时间（秒），增量拉取用 `ot` 参数。 */
    var lastSyncServerSec: Long
        get() = sp.getLong("lastSyncServerSec", 0L)
        set(value) = sp.edit { putLong("lastSyncServerSec", value) }

    /** 登录成功后保存账号和服务器发的 token（加密）；null = 退出登录，同时清掉同步进度。 */
    fun saveAccount(account: Account?, authToken: String = "") {
        val encrypted = if (account == null) "" else SecretBox.encrypt(authToken)
        acc.edit(commit = true) {
            clear()
            if (account != null) {
                putString("type", account.type.name)
                putString("base", account.apiBase)
                putString("user", account.username)
                putString("desc", account.description)
                putString("token", encrypted)
            }
        }
        if (account == null) sp.edit { remove("lastSyncAt"); remove("lastSyncServerSec") }
        synchronized(this) { cachedToken = authToken.takeIf { account != null } }
        _account.value = account
    }


    /**
     * 当前账号的 token，第一次用时才解密（不放在启动路径上）。
     * 解不开（例如系统备份恢复到了新设备）返回空串，服务器会报认证失败，退出后重新登录即可。
     */
    fun authToken(): String {
        cachedToken?.let { return it }
        return synchronized(this) {
            cachedToken ?: (SecretBox.decrypt(acc.getString("token", "") ?: "") ?: "").also { cachedToken = it }
        }
    }

    fun setAccountDescription(text: String) {
        val a = _account.value ?: return
        acc.edit { putString("desc", text.trim()) }
        _account.value = a.copy(description = text.trim())
    }

    /**
     * 旧版把账号（含明文密码和 token）放在 `runnel` 文件里：搬到 `account` 文件、token 加密，
     * 删掉明文密码和 token。只在升级后第一次启动时做一次。
     */
    private fun migrateLegacyAccount() {
        if (!sp.contains("acc_type")) return
        val token = sp.getString("acc_token", "") ?: ""
        acc.edit(commit = true) {
            putString("type", sp.getString("acc_type", null))
            putString("base", sp.getString("acc_base", "") ?: "")
            putString("user", sp.getString("acc_user", "") ?: "")
            putString("desc", sp.getString("acc_desc", "") ?: "")
            // Keystore 出错也不能让启动崩掉：token 留空，同步会报认证失败，重新登录即可
            putString("token", runCatching { SecretBox.encrypt(token) }.getOrDefault(""))
        }
        sp.edit(commit = true) {
            listOf("acc_type", "acc_base", "acc_user", "acc_pass", "acc_token", "acc_desc").forEach { remove(it) }
        }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        sp.edit {
            putString("theme", s.theme.name)
            putString("readerView", s.readerView.name)
            putStringSet("readerViewFeeds", s.readerViewFeeds)
            putStringSet("offlineImageFeeds", s.offlineImageFeeds)
            putBoolean("autoHideBars", s.autoHideBars)
            putBoolean("coloredLinks", s.coloredLinks)
            putBoolean("highContrast", s.highContrast)
            putString("readerFont", s.readerFont.name)
            putBoolean("useDefaultFontSize", s.useDefaultFontSize)
            putInt("readerFontSize", s.readerFontSize)
            putFloat("readerLineSpacing", s.readerLineSpacing)
            putBoolean("titleSystemFont", s.titleSystemFont)
            putFloat("titleScale", s.titleScale)
            putBoolean("titleBold", s.titleBold)
            putBoolean("titleUppercase", s.titleUppercase)
            putBoolean("titleCenter", s.titleCenter)
            putString("openLinks", s.openLinks.name)
            putBoolean("markReadOnOpen", s.markReadOnOpen)
            putBoolean("markReadOnScroll", s.markReadOnScroll)
            putBoolean("oldestFirstUnread", s.oldestFirstUnread)
            putString("groupBy", s.groupBy.name)
            putBoolean("autoNextFeed", s.autoNextFeed)
            putBoolean("markAllAsk", s.markAllAsk)
            putBoolean("markAllOpenNext", s.markAllOpenNext)
            putBoolean("markAllButtonLeft", s.markAllButtonLeft)
            putBoolean("grayFavicons", s.grayFavicons)
            putString("appFont", s.appFont.name)
            putBoolean("useSystemFontSize", s.useSystemFontSize)
            putInt("appFontStep", s.appFontStep)
            putBoolean("countStarred", s.countStarred)
            putBoolean("countUnread", s.countUnread)
            putBoolean("countAll", s.countAll)
            putString("imagePreview", s.imagePreview.name)
            putBoolean("showFavicons", s.showFavicons)
            putBoolean("textPreview", s.textPreview)
            putBoolean("iosFling", s.iosFling)
            putBoolean("maxRefreshRate", s.maxRefreshRate)
            putInt("syncIntervalMinutes", s.syncIntervalMinutes)
            putInt("keepDays", s.keepDays)
            putString("syncMode", s.syncMode.name)
            putString("sortSubs", s.sortSubs.name)
            putString("swipeRight", s.swipeRight.name)
            putString("swipeLeft", s.swipeLeft.name)
            putString("pullBottom", s.pullBottom.name)
            putBoolean("pullToSwitch", s.pullToSwitch)
            putString("animSpeed", s.animSpeed.name)
            putBoolean("animArticleSwitch", s.animArticleSwitch)
            putBoolean("animListLoad", s.animListLoad)
        }
        _settings.value = s
    }

    private fun readAccount(): Account? {
        val type = acc.getString("type", null)?.let { runCatching { AccountType.valueOf(it) }.getOrNull() }
            ?: return null
        return Account(
            type = type,
            apiBase = acc.getString("base", "") ?: "",
            username = acc.getString("user", "") ?: "",
            description = acc.getString("desc", "") ?: "",
        )
    }

    private inline fun <reified E : Enum<E>> enumOf(key: String, default: E): E =
        sp.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun readSettings(): Settings {
        val d = Settings()
        return Settings(
            theme = enumOf("theme", d.theme),
            readerView = enumOf("readerView", d.readerView),
            readerViewFeeds = sp.getStringSet("readerViewFeeds", null)?.toSet() ?: d.readerViewFeeds,
            offlineImageFeeds = sp.getStringSet("offlineImageFeeds", null)?.toSet() ?: d.offlineImageFeeds,
            autoHideBars = sp.getBoolean("autoHideBars", d.autoHideBars),
            coloredLinks = sp.getBoolean("coloredLinks", d.coloredLinks),
            highContrast = sp.getBoolean("highContrast", d.highContrast),
            // 旧版本只有“衬线”开关
            readerFont = if (sp.contains("readerFont")) enumOf("readerFont", d.readerFont)
            else if (sp.getBoolean("readerSerif", false)) ReaderFont.SERIF else d.readerFont,
            useDefaultFontSize = sp.getBoolean("useDefaultFontSize", d.useDefaultFontSize),
            readerFontSize = sp.getInt("readerFontSize", d.readerFontSize),
            readerLineSpacing = sp.getFloat("readerLineSpacing", d.readerLineSpacing),
            titleSystemFont = sp.getBoolean("titleSystemFont", d.titleSystemFont),
            titleScale = sp.getFloat("titleScale", d.titleScale),
            titleBold = sp.getBoolean("titleBold", d.titleBold),
            titleUppercase = sp.getBoolean("titleUppercase", d.titleUppercase),
            titleCenter = sp.getBoolean("titleCenter", d.titleCenter),
            openLinks = enumOf("openLinks", d.openLinks),
            markReadOnOpen = sp.getBoolean("markReadOnOpen", d.markReadOnOpen),
            markReadOnScroll = sp.getBoolean("markReadOnScroll", d.markReadOnScroll),
            oldestFirstUnread = sp.getBoolean("oldestFirstUnread", d.oldestFirstUnread),
            groupBy = enumOf("groupBy", d.groupBy),
            autoNextFeed = sp.getBoolean("autoNextFeed", d.autoNextFeed),
            markAllAsk = sp.getBoolean("markAllAsk", d.markAllAsk),
            markAllOpenNext = sp.getBoolean("markAllOpenNext", d.markAllOpenNext),
            markAllButtonLeft = sp.getBoolean("markAllButtonLeft", d.markAllButtonLeft),
            grayFavicons = sp.getBoolean("grayFavicons", d.grayFavicons),
            appFont = enumOf("appFont", d.appFont),
            useSystemFontSize = sp.getBoolean("useSystemFontSize", d.useSystemFontSize),
            appFontStep = sp.getInt("appFontStep", d.appFontStep),
            countStarred = sp.getBoolean("countStarred", d.countStarred),
            countUnread = sp.getBoolean("countUnread", d.countUnread),
            countAll = sp.getBoolean("countAll", d.countAll),
            imagePreview = enumOf("imagePreview", d.imagePreview),
            showFavicons = sp.getBoolean("showFavicons", d.showFavicons),
            textPreview = sp.getBoolean("textPreview", d.textPreview),
            iosFling = sp.getBoolean("iosFling", d.iosFling),
            maxRefreshRate = sp.getBoolean("maxRefreshRate", d.maxRefreshRate),
            // 旧版本里“后台同步”间隔 0 = 关闭，对应现在的“启动时”
            syncMode = if (sp.contains("syncMode")) enumOf("syncMode", d.syncMode)
            else if (sp.getInt("syncIntervalMinutes", d.syncIntervalMinutes) == 0) SyncMode.ON_START else SyncMode.BACKGROUND,
            syncIntervalMinutes = sp.getInt("syncIntervalMinutes", d.syncIntervalMinutes).takeIf { it > 0 } ?: d.syncIntervalMinutes,
            keepDays = sp.getInt("keepDays", d.keepDays).takeIf { it in KEEP_DAYS_OPTIONS } ?: d.keepDays,
            sortSubs = enumOf("sortSubs", d.sortSubs),
            swipeRight = enumOf("swipeRight", d.swipeRight),
            swipeLeft = enumOf("swipeLeft", d.swipeLeft),
            // 旧版是开关 pullBottomMarkAll（开 = 全部标为已读）
            pullBottom = if (sp.contains("pullBottom")) enumOf("pullBottom", d.pullBottom)
            else if (sp.getBoolean("pullBottomMarkAll", true)) PullBottom.MARK_ALL_READ else PullBottom.NONE,
            pullToSwitch = sp.getBoolean("pullToSwitch", d.pullToSwitch),
            animSpeed = enumOf("animSpeed", d.animSpeed),
            animArticleSwitch = sp.getBoolean("animArticleSwitch", d.animArticleSwitch),
            animListLoad = sp.getBoolean("animListLoad", d.animListLoad),
        )
    }

    companion object {
        /** 账号所在的 SharedPreferences 文件名；备份规则里按 `account.xml` 排除。 */
        const val ACCOUNT_FILE = "account"
    }
}
