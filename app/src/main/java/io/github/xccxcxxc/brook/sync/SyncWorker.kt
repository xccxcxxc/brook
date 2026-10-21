package io.github.xccxcxxc.brook.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.xccxcxxc.brook.Graph
import java.util.concurrent.TimeUnit

/**
 * 后台定时同步（尽力而为：部分国产系统会限制后台）。
 * 抓取由服务端完成，这里只是把结果拉下来，回到前台时还会再同步一次。
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = Graph.get()
        if (graph.sync.sync().isFailure) return Result.retry()
        // 等离线图片下完：worker 一结束进程就可能被系统回收
        graph.offlineImages.requestPrefetch().join()
        return Result.success()
    }

    companion object {
        private const val NAME = "brook-sync"

        fun schedule(context: Context, intervalMinutes: Int) {
            val wm = WorkManager.getInstance(context)
            if (intervalMinutes <= 0) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
