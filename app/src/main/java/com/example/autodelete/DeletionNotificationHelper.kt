package com.example.autodelete

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R

object DeletionNotificationHelper {

    const val CHANNEL_ID = "worm_temp_copier_autodelete"
    private const val CHANNEL_NAME = "Worm Temp Deletions"
    private const val CHANNEL_DESC = "Notifications for temporary file deletion status and retention expiry."

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun canPostNotification(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyDeletionSuccess(
        context: Context,
        notificationId: Int,
        fileName: String,
        gameEditionName: String,
        retentionMinutes: Int
    ) {
        ensureChannel(context)
        if (!canPostNotification(context)) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val message = "Temporary Copy Deleted Successfully.\nFile: $fileName\nGame: $gameEditionName\nRetention: $retentionMinutes min"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.img_launcher_icon_1793784567802)
            .setContentTitle("Worm Temp Copier")
            .setContentText("Temporary Copy Deleted: $fileName")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }

    fun notifyDeletionFailure(
        context: Context,
        notificationId: Int,
        fileName: String,
        reason: String
    ) {
        ensureChannel(context)
        if (!canPostNotification(context)) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val message = "Temporary copy was not deleted.\nFile: $fileName\nReason: $reason"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.img_launcher_icon_1793784567802)
            .setContentTitle("Worm Temp Copier - Deletion Failed")
            .setContentText("Failed to delete $fileName: $reason")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }
}
