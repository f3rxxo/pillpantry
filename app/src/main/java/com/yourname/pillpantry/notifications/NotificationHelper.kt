package com.yourname.pillpantry.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

private const val CHANNEL_ID = "refill_alerts"
private const val CHANNEL_NAME = "Refill alerts"

object NotificationHelper {

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a tracked vitamin or grocery item is running low"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Fires a local notification. Caller is responsible for having requested
     * POST_NOTIFICATIONS permission on Android 13+ before calling this.
     */
    fun sendRefillAlert(context: Context, vitaminName: String, pillsLeft: Long) {
        // Simple stable icon reference; using the app's own launcher icon
        // avoids needing an extra dedicated notification drawable.
        val smallIcon = context.applicationInfo.icon

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("Time to reorder!")
            .setContentText(
                "$vitaminName: only $pillsLeft pill${if (pillsLeft == 1L) "" else "s"} left."
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(vitaminName.hashCode(), notification)
    }

    /**
     * Fires when a grocery item gets auto-added to the shopping list (daily
     * portion decay crossing the threshold). Unlike [sendRefillAlert], this
     * is also called from [com.yourname.pillpantry.work.PortionDecayWorker],
     * which can run with the app fully closed — that's the whole point of
     * routing it through here rather than only firing when the app happens
     * to be open.
     */
    fun sendShoppingListAlert(context: Context, itemName: String, portionsLeft: Long) {
        val smallIcon = context.applicationInfo.icon

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("Added to shopping list")
            .setContentText(
                "$itemName: only $portionsLeft portion${if (portionsLeft == 1L) "" else "s"} left."
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify("shopping_${itemName}".hashCode(), notification)
    }
}
