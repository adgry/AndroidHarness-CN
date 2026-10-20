package com.androidharness.app.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.androidharness.app.HarnessApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LocalModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val modelId = inputData.getString("modelId") ?: return Result.failure()
        val manager = (applicationContext as HarnessApp).container.localModels
        val model = manager.find(modelId) ?: return Result.failure()
        return try {
            setForeground(foreground(model.title))
            kotlinx.coroutines.coroutineScope {
                val cancellation = launch(kotlinx.coroutines.Dispatchers.Default, start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    try { kotlinx.coroutines.awaitCancellation() }
                    finally { manager.interruptDownload(modelId) }
                }
                try {
                    manager.install(modelId, { isStopped }) { received, total ->
                        setProgress(workDataOf("received" to received, "total" to total))
                        setForeground(foreground(model.title, (received * 100 / total).toInt()))
                    }
                } finally { cancellation.cancel() }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.message ?: "下载失败")))
        } catch (_: UnsatisfiedLinkError) {
            Result.failure(workDataOf("error" to "此架构不支持设备端转换，请下载已转换的 GGUF 模型。"))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground("本地模型")

    private fun foreground(title: String, progress: Int? = null): ForegroundInfo {
        val notifications = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "本地模型下载", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 $title")
            .setContentText("点击“取消”停止下载")
            .setOngoing(true).setOnlyAlertOnce(true).setProgress(100, progress ?: 0, progress == null)
            .addAction(0, "取消", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id.hashCode(), notification)
    }

    private companion object { const val CHANNEL = "local-model-downloads" }
}
