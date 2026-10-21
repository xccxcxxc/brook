package io.github.xccxcxxc.brook

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.xccxcxxc.brook.sync.SyncWorker
import okio.Path.Companion.toOkioPath

class BrookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        val graph = Graph.get()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.http })) }
                .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("images").toOkioPath())
                        .maxSizeBytes(256L * 1024 * 1024)
                        .build()
                }
                .build()
        }
        val account = graph.prefs.account.value
        if (account?.isRemote == true) SyncWorker.schedule(this, graph.prefs.settings.value.syncIntervalMinutes)
    }
}
