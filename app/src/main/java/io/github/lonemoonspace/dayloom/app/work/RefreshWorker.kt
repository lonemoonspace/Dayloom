package io.github.lonemoonspace.dayloom.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.lonemoonspace.dayloom.DayloomApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * The periodic background refresh. Its fully qualified class name and [WORK_NAME] are frozen from v1.0.0: WorkManager
 * instantiates scheduled work by class name.
 * 周期性后台刷新。它的全限定类名与 [WORK_NAME] 从 v1.0.0 起冻结：WorkManager 按类名实例化已排期的任务。
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val retry = DayloomApp.from(applicationContext).graph.backgroundRound.run()
        if (retry) Result.retry() else Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "background round failed, retrying", e)
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "dayloom.refresh"
        private const val TAG = "RefreshWorker"

        /**
         * Every 15 minutes, WorkManager's minimum; each source is throttled to its own cadence inside the round. No network
         * constraint: rules that only need the clock (expiry reminders) must run offline too, and offline sources are skipped
         * without a request. UPDATE keeps an existing schedule's timing but applies a changed request after an app update.
         * 每 15 分钟一次，即 WorkManager 的下限；每个来源在一轮里再按自己的节奏节流。不加联网约束：只依赖时间的规则（到期提醒）
         * 离线时也得运行，离线的来源会直接跳过、不发请求。UPDATE 保留已有排期的节奏，但 App 更新后改过的请求也会生效。
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
