package com.yourname.pillpantry.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.yourname.pillpantry.data.FirebaseRepository
import com.yourname.pillpantry.notifications.NotificationHelper
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Evening reminder to log any vitamins not yet taken today, targeting
 * ~9:00pm America/Chicago. Same caveat as [PortionDecayWorker]: Android
 * won't reliably fire a background job at an exact time for a personal
 * app — Doze mode and battery optimization can delay this by a while.
 * There's no meaningful "catch-up" for a reminder the way there is for
 * portion decay (a missed reminder from yesterday isn't useful today), so
 * this is simply best-effort — usually close to 9pm, not guaranteed to
 * the minute.
 *
 * Requires network (see [schedule]) since checking who's "not taken today"
 * off a possibly-stale offline Firestore cache could produce a wrong
 * (usually false-negative) reminder; WorkManager will wait for
 * connectivity before running if none is available at the scheduled time.
 */
class PillReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        return try {
            val pending = FirebaseRepository().getVitaminsNotTakenToday(userId)
            if (pending.isNotEmpty()) {
                NotificationHelper.sendPillReminderAlert(applicationContext, pending.map { it.name })
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "pill_reminder_daily"
        private const val REMINDER_HOUR = 21 // 9:00 PM

        /** Schedules (or re-uses) a daily job targeting ~9pm America/Chicago. */
        fun schedule(context: Context) {
            val zone = ZoneId.of("America/Chicago")
            val now = ZonedDateTime.now(zone)
            var nextRun = now.withHour(REMINDER_HOUR).withMinute(0).withSecond(0).withNano(0)
            if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
            val initialDelay = Duration.between(now, nextRun)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PillReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
