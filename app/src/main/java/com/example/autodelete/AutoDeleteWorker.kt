package com.example.autodelete

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.domain.FileOperationEngine
import com.example.shizuku.ShizukuManager
import java.util.concurrent.TimeUnit

class AutoDeleteWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uniqueCopyId = inputData.getString(KEY_COPY_ID) ?: return Result.failure()
        val database = AppDatabase.getInstance(applicationContext)
        val deletionDao = database.scheduledDeletionDao()

        val record = deletionDao.getById(uniqueCopyId) ?: return Result.success()

        // If cancelled by user, stop
        if (record.cancelled || record.isDeleted) {
            return Result.success()
        }

        val shizukuManager = ShizukuManager(applicationContext)
        val engine = FileOperationEngine(applicationContext, shizukuManager)

        val (success, failureReason) = engine.safeDeleteTemporaryCopy(
            targetFilePath = record.destinationPath,
            expectedSha256 = record.expectedSha256
        )

        val notificationId = uniqueCopyId.hashCode()

        if (success) {
            deletionDao.markDeleted(uniqueCopyId)
            DeletionNotificationHelper.notifyDeletionSuccess(
                context = applicationContext,
                notificationId = notificationId,
                fileName = record.fileName,
                gameEditionName = record.gameEditionName,
                retentionMinutes = record.durationMinutes
            )
            return Result.success()
        } else {
            val reason = failureReason ?: "Unknown deletion failure"
            deletionDao.markFailed(uniqueCopyId, reason)
            DeletionNotificationHelper.notifyDeletionFailure(
                context = applicationContext,
                notificationId = notificationId,
                fileName = record.fileName,
                reason = reason
            )
            return Result.failure()
        }
    }

    companion object {
        const val KEY_COPY_ID = "key_unique_copy_id"

        fun scheduleDeletion(
            context: Context,
            uniqueCopyId: String,
            delayMillis: Long
        ) {
            val data = Data.Builder()
                .putString(KEY_COPY_ID, uniqueCopyId)
                .build()

            val request = OneTimeWorkRequestBuilder<AutoDeleteWorker>()
                .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("autodelete_$uniqueCopyId")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "work_$uniqueCopyId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelScheduledWork(context: Context, uniqueCopyId: String) {
            WorkManager.getInstance(context).cancelUniqueWork("work_$uniqueCopyId")
        }
    }
}
