package io.github.xccxcxxc.brook.sync

import android.util.Log
import io.github.xccxcxxc.brook.data.db.inTransaction
import io.github.xccxcxxc.brook.content.ArticleProcessor
import io.github.xccxcxxc.brook.data.Account
import io.github.xccxcxxc.brook.data.AccountType
import io.github.xccxcxxc.brook.data.FlagStore
import io.github.xccxcxxc.brook.data.Prefs
import io.github.xccxcxxc.brook.data.db.ArticleContentEntity
import io.github.xccxcxxc.brook.data.db.ArticleEntity
import io.github.xccxcxxc.brook.data.db.BrookDatabase
import io.github.xccxcxxc.brook.data.db.FeedEntity
import io.github.xccxcxxc.brook.data.db.FolderEntity
import io.github.xccxcxxc.brook.data.db.PendingActionEntity
import io.github.xccxcxxc.brook.sync.greader.GReaderApi
import io.github.xccxcxxc.brook.sync.greader.GReaderException
import io.github.xccxcxxc.brook.sync.greader.Item
import io.github.xccxcxxc.brook.sync.greader.ItemIds
import io.github.xccxcxxc.brook.sync.greader.Streams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val message: String) : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * 同步引擎：服务端（FreshRSS / Miniflux）负责抓取订阅，客户端只做同步。
 *
 * 顺序：提交离线操作 → 订阅/文件夹 → 未读和星标 id → 增量下载缺失文章（正文在这里一次性清洗）
 * → 按服务端对齐本地已读/星标 → 清理旧文章。
 */
class SyncEngine(
    private val db: BrookDatabase,
    private val prefs: Prefs,
    private val http: OkHttpClient,
    private val flags: FlagStore,
    private val scope: CoroutineScope,
    /** 同步成功后调用（离线图片在这里开始预下载，不拖慢同步）。 */
    private val afterSync: () -> Unit = {},
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(prefs.lastSyncAt)
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    /** 每次同步插入新文章后加一，列表据此显示“N 篇新文章”。 */
    private val _dataVersion = MutableStateFlow(0L)
    val dataVersion: StateFlow<Long> = _dataVersion.asStateFlow()

    private var flushJob: Job? = null

    fun requestSync() {
        scope.launch { sync() }
    }

    /** 回到前台时调用：距上次同步超过 [maxAgeMs] 才同步。 */
    fun syncIfStale(maxAgeMs: Long = 10 * 60_000L) {
        if (System.currentTimeMillis() - prefs.lastSyncAt > maxAgeMs) requestSync()
    }

    /** 本地标记后延迟几秒批量提交，不等完整同步。 */
    fun scheduleFlush() {
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(3_000)
            val account = prefs.account.value ?: return@launch
            if (!account.isRemote) return@launch
            runCatching { mutex.withLock { pushPending(api(account)) } }
                .onFailure { Log.w(TAG, "flush pending failed", it) }
        }
    }

    /** 返回新增文章数；正在同步时直接返回 0。 */
    suspend fun sync(): Result<Int> {
        val account = prefs.account.value ?: return Result.success(0)
        if (!mutex.tryLock()) return Result.success(0)
        return try {
            _state.value = SyncState.Running("正在同步")
            val added = withContext(Dispatchers.IO) {
                if (account.type == AccountType.DEMO) DemoData.addFresh(db, 3) else syncGReader(account)
            }
            val now = System.currentTimeMillis()
            prefs.lastSyncAt = now
            _lastSyncAt.value = now
            if (added > 0) _dataVersion.value++
            _state.value = SyncState.Idle
            afterSync()
            Result.success(added)
        } catch (e: Exception) {
            Log.w(TAG, "sync failed", e)
            _state.value = SyncState.Failed(e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        } finally {
            mutex.unlock()
        }
    }

    fun api(account: Account) = GReaderApi(http, account.apiBase, prefs.authToken())

    /** 添加订阅（可指定文件夹），然后同步一次，新源和它的文章才会出现在首页。 */
    suspend fun addSubscription(input: String, folderId: String?) {
        val account = prefs.account.value?.takeIf { it.isRemote } ?: throw GReaderException("演示数据不能添加订阅")
        val url = input.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        val api = api(account)
        val streamId = api.quickAdd(url) ?: throw GReaderException("这个地址没有找到订阅源")
        if (folderId != null) api.moveSubscription(streamId, folderId)
        requestSync()
    }

    private suspend fun syncGReader(account: Account): Int {
        val api = api(account)
        pushPending(api, showProgress = true)

        _state.value = SyncState.Running("更新订阅列表")
        syncSubscriptions(api)

        _state.value = SyncState.Running("同步文章状态")
        val syncStartSec = System.currentTimeMillis() / 1000
        val unread = api.itemIds(Streams.READING_LIST, exclude = Streams.READ, limit = MAX_UNREAD_IDS)
        val starred = api.itemIds(Streams.STARRED, limit = MAX_STARRED_IDS)
        val since = prefs.lastSyncServerSec.takeIf { it > 0 }?.minus(3600)
        val recent = api.itemIds(Streams.READING_LIST, limit = if (since == null) FIRST_SYNC_RECENT else 5_000, newerThanSec = since)

        val articleDao = db.articleDao()
        val local = articleDao.allIds().toHashSet()
        val wanted = LinkedHashSet<Long>().apply {
            addAll(unread.take(MAX_UNREAD_FETCH))
            addAll(starred)
            addAll(recent)
        }
        val missing = wanted.filter { it !in local }
        val added = downloadArticles(api, missing)

        _state.value = SyncState.Running("整理")
        reconcileFlags(unread, starred, unreadComplete = unread.size < MAX_UNREAD_IDS)
        cleanup()
        prefs.lastSyncServerSec = syncStartSec
        // 图标在后台慢慢找，不拖慢同步
        scope.launch(Dispatchers.IO) { runCatching { IconFinder(http).fillMissing(db) } }
        return added
    }

    private suspend fun syncSubscriptions(api: GReaderApi) {
        val subs = api.subscriptions()
        // 已经找到的本地图标要保留，不能被这次同步覆盖成 null
        val knownIcons = db.feedDao().allFeeds().associate { it.id to it.localIcon }
        val folders = LinkedHashMap<String, FolderEntity>()
        val feeds = subs.mapIndexed { index, s ->
            val cat = s.categories.firstOrNull { it.id.contains("/label/") }
            if (cat != null) {
                folders.getOrPut(cat.id) {
                    FolderEntity(cat.id, cat.label?.takeIf(String::isNotBlank) ?: cat.id.substringAfterLast('/'), folders.size)
                }
            }
            FeedEntity(
                id = s.id,
                title = ArticleProcessor.cleanTitle(s.title).ifBlank { s.url ?: s.id },
                folderId = cat?.id,
                siteUrl = s.htmlUrl?.takeIf(String::isNotBlank),
                feedUrl = s.url?.takeIf(String::isNotBlank),
                iconUrl = rebaseIcon(s.iconUrl, prefs.account.value?.apiBase.orEmpty()),
                sortIndex = index,
                localIcon = knownIcons[s.id],
            )
        }
        val feedDao = db.feedDao()
        db.inTransaction {
            feedDao.upsertFolders(folders.values.toList())
            feedDao.upsertFeeds(feeds)
            feedDao.deleteFoldersExcept(folders.keys.toList())
            feedDao.deleteFeedsExcept(feeds.map { it.id })
            db.articleDao().deleteOrphans()
        }
    }

    /** 分块并发下载正文，HTML 清洗放在 Default 线程池。 */
    private suspend fun downloadArticles(api: GReaderApi, ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        val articleDao = db.articleDao()
        var done = 0
        var added = 0
        val now = System.currentTimeMillis()
        for (group in ids.chunked(CONTENT_CHUNK * PARALLEL)) {
            _state.value = SyncState.Running("下载文章 $done/${ids.size}")
            val items = coroutineScope {
                group.chunked(CONTENT_CHUNK).map { chunk -> async { api.contents(chunk) } }.awaitAll().flatten()
            }
            val converted = withContext(Dispatchers.Default) { items.mapNotNull { toEntities(it, now) } }
            db.inTransaction {
                articleDao.insertArticles(converted.map { it.first })
                articleDao.insertContents(converted.map { it.second })
            }
            done += group.size
            added += converted.size
        }
        return added
    }

    private suspend fun reconcileFlags(unread: List<Long>, starred: List<Long>, unreadComplete: Boolean) {
        val articleDao = db.articleDao()
        val unreadSet = unread.toHashSet()
        val starredSet = starred.toHashSet()
        val toRead = ArrayList<Long>()
        val toUnread = ArrayList<Long>()
        val toStar = ArrayList<Long>()
        val toUnstar = ArrayList<Long>()
        db.inTransaction {
            // 同步期间用户又做了本地操作的文章，以本地为准
            val pending = db.pendingDao().pendingArticleIds().toHashSet()
            for (f in articleDao.allFlags()) {
                if (f.id in pending) continue
                if (unreadComplete) {
                    val shouldRead = f.id !in unreadSet
                    if (shouldRead != f.isRead) (if (shouldRead) toRead else toUnread) += f.id
                }
                val shouldStar = f.id in starredSet
                if (shouldStar != f.isStarred) (if (shouldStar) toStar else toUnstar) += f.id
            }
            toRead.chunked(SQL_CHUNK).forEach { articleDao.setRead(it, true) }
            toUnread.chunked(SQL_CHUNK).forEach { articleDao.setRead(it, false) }
            toStar.chunked(SQL_CHUNK).forEach { articleDao.setStarred(it, true) }
            toUnstar.chunked(SQL_CHUNK).forEach { articleDao.setStarred(it, false) }
        }
        // 已打开的列表快照跟着更新
        if (toRead.size + toUnread.size + toStar.size + toUnstar.size > 0) {
            withContext(Dispatchers.Main) {
                flags.setRead(toRead, true)
                flags.setRead(toUnread, false)
                flags.setStarred(toStar, true)
                flags.setStarred(toUnstar, false)
            }
        }
    }

    private suspend fun cleanup() {
        val articleDao = db.articleDao()
        val keepDays = prefs.settings.value.keepDays
        // -1 = 一直保留已读文章；0 = 不保留
        if (keepDays >= 0) articleDao.deleteOldRead(System.currentTimeMillis() - keepDays * 24L * 3600_000L)
        articleDao.deleteOrphanContent()
    }

    /**
     * 提交离线操作：同一篇文章的多次操作只取最后一次。
     * 只有完整同步里才显示进度（[showProgress]）：[scheduleFlush] 的后台提交不能改同步状态，
     * 否则没人把它改回 Idle，列表顶上会一直转圈。
     */
    private suspend fun pushPending(api: GReaderApi, showProgress: Boolean = false) {
        val pendingDao = db.pendingDao()
        val actions = pendingDao.all()
        if (actions.isEmpty()) return
        if (showProgress) _state.value = SyncState.Running("提交本地操作")
        val read = LinkedHashMap<Long, Boolean>()
        val star = LinkedHashMap<Long, Boolean>()
        for (a in actions) when (a.action) {
            PendingActionEntity.READ -> read[a.articleId] = true
            PendingActionEntity.UNREAD -> read[a.articleId] = false
            PendingActionEntity.STAR -> star[a.articleId] = true
            PendingActionEntity.UNSTAR -> star[a.articleId] = false
        }
        read.filterValues { it }.keys.chunked(EDIT_CHUNK).forEach { api.editTag(it, add = Streams.READ) }
        read.filterValues { !it }.keys.chunked(EDIT_CHUNK).forEach { api.editTag(it, remove = Streams.READ) }
        star.filterValues { it }.keys.chunked(EDIT_CHUNK).forEach { api.editTag(it, add = Streams.STARRED) }
        star.filterValues { !it }.keys.chunked(EDIT_CHUNK).forEach { api.editTag(it, remove = Streams.STARRED) }
        pendingDao.deleteUpTo(actions.last().id)
    }

    companion object {
        private const val TAG = "BrookSync"
        private const val MAX_UNREAD_IDS = 20_000
        private const val MAX_UNREAD_FETCH = 5_000
        private const val MAX_STARRED_IDS = 10_000
        private const val FIRST_SYNC_RECENT = 500
        private const val CONTENT_CHUNK = 100
        private const val PARALLEL = 3
        private const val EDIT_CHUNK = 250
        const val SQL_CHUNK = 500

        /**
         * 服务端给的图标地址常带着服务端自己配置的 BASE_URL（Miniflux 没配时就是 http://localhost），
         * 手机上访问不到。Miniflux 的图标路径是公开的 /feed-icon/<id>，统一改挂到登录用的服务器地址上；
         * 其它指向 localhost 的地址也换成服务器的协议和主机。
         */
        fun rebaseIcon(icon: String?, apiBase: String): String? {
            if (icon.isNullOrBlank()) return null
            val base = apiBase.trimEnd('/')
            if (base.isEmpty()) return icon
            if (icon.contains("/feed-icon/")) return base + "/feed-icon/" + icon.substringAfter("/feed-icon/")
            val uri = runCatching { java.net.URI(icon) }.getOrNull() ?: return icon
            val origin = runCatching { java.net.URI(base) }.getOrNull() ?: return icon
            val local = uri.host == null || uri.host in setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")
            if (!local) return icon
            val tail = (uri.rawPath ?: "") + (uri.rawQuery?.let { "?$it" } ?: "")
            return "${origin.scheme}://${origin.rawAuthority}" + (if (tail.startsWith("/")) tail else "/$tail")
        }

        fun toEntities(item: Item, now: Long): Pair<ArticleEntity, ArticleContentEntity>? {
            val id = runCatching { ItemIds.parse(item.id) }.getOrNull() ?: return null
            val feedId = item.origin?.streamId ?: return null
            val url = (item.canonical + item.alternate).firstNotNullOfOrNull { it.href?.takeIf(String::isNotBlank) }
            val raw = item.content?.content?.takeIf(String::isNotBlank) ?: item.summary?.content
            val enclosureImage = item.enclosure.firstOrNull { it.type?.startsWith("image") == true }?.href
            val p = ArticleProcessor.process(raw, url, enclosureImage)
            val published = when {
                item.published > 0 -> item.published * 1000
                else -> item.crawlTimeMsec?.toLongOrNull() ?: now
            }
            val article = ArticleEntity(
                id = id,
                feedId = feedId,
                title = ArticleProcessor.cleanTitle(item.title).ifBlank { p.summary.take(60).ifBlank { "（无标题）" } },
                author = item.author?.takeIf(String::isNotBlank),
                url = url,
                publishedAt = published,
                summary = p.summary,
                thumbUrl = p.thumbUrl,
                readingMinutes = p.readingMinutes,
                isRead = item.categories.any { it.endsWith("/state/com.google/read") },
                isStarred = item.categories.any { it.endsWith("/state/com.google/starred") },
                fetchedAt = now,
            )
            return article to ArticleContentEntity(id, p.html, null)
        }
    }
}
