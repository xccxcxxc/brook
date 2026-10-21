package io.github.xccxcxxc.brook

import android.app.Application
import io.github.xccxcxxc.brook.data.db.inTransaction
import io.github.xccxcxxc.brook.content.FullTextFetcher
import io.github.xccxcxxc.brook.content.OfflineImages
import io.github.xccxcxxc.brook.content.ReaderImageCache
import io.github.xccxcxxc.brook.data.Account
import io.github.xccxcxxc.brook.data.AccountType
import io.github.xccxcxxc.brook.data.ArticleRepository
import io.github.xccxcxxc.brook.data.FlagStore
import io.github.xccxcxxc.brook.data.Prefs
import io.github.xccxcxxc.brook.data.db.BrookDatabase
import io.github.xccxcxxc.brook.sync.DemoData
import io.github.xccxcxxc.brook.sync.SyncEngine
import io.github.xccxcxxc.brook.sync.SyncWorker
import io.github.xccxcxxc.brook.sync.greader.GReaderApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 手写依赖装配（App 很小，不引入 DI 框架）。 */
class AppGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = Prefs(app)
    val db = BrookDatabase.create(app)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // 很多站点（尤其挂了 Cloudflare 的）会拒绝默认的 okhttp UA，图标和图片都拿不到
        .addInterceptor { chain ->
            val req = chain.request()
            chain.proceed(if (req.header("User-Agent") != null) req else req.newBuilder().header("User-Agent", BROWSER_UA).build())
        }
        .build()
    val flags = FlagStore()
    val sync = SyncEngine(db, prefs, http, flags, scope, afterSync = { offlineImages.requestPrefetch() })
    val repo = ArticleRepository(db, prefs, flags, sync, FullTextFetcher(http))
    /** 阅读页图片的磁盘缓存，第一次用到时才建。 */
    val readerImages by lazy { ReaderImageCache(http, app.cacheDir.resolve("reader-images")) }
    val offlineImages by lazy { OfflineImages(db, prefs, { readerImages }, scope) }

    /** 登录 GReader 兼容服务，成功后做首次同步。 */
    suspend fun login(type: AccountType, server: String, username: String, password: String) {
        val base = GReaderApi.normalizeBase(type, server)
        val token = GReaderApi(http, base).login(username, password)
        resetData()
        prefs.lastSyncServerSec = 0
        prefs.saveAccount(Account(type, base, username), token)
        sync.sync().getOrThrow()
        val s = prefs.settings.value
        SyncWorker.schedule(app, if (s.syncMode == io.github.xccxcxxc.brook.data.SyncMode.BACKGROUND) s.syncIntervalMinutes else 0)
    }

    suspend fun loginDemo() {
        resetData()
        withContext(Dispatchers.IO) { DemoData.populate(db) }
        prefs.saveAccount(Account(AccountType.DEMO, "", "demo"))
        prefs.lastSyncAt = System.currentTimeMillis()
    }

    suspend fun logout() {
        SyncWorker.cancel(app)
        prefs.saveAccount(null)
        resetData()
    }

    private suspend fun resetData() {
        withContext(Dispatchers.IO) {
            db.inTransaction {
                db.articleDao().clearArticles()
                db.articleDao().clearContent()
                db.feedDao().clearFeeds()
                db.feedDao().clearFolders()
                db.pendingDao().clear()
            }
        }
        flags.clear()
    }
}

const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

object Graph {
    private lateinit var graph: AppGraph
    fun init(app: Application) {
        graph = AppGraph(app)
    }

    fun get(): AppGraph = graph
}
