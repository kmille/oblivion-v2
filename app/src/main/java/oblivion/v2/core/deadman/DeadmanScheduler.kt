package oblivion.v2.core.deadman

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import oblivion.v2.core.log.SecLog
import java.util.concurrent.TimeUnit

/**
 * Schedules (or cancels) the periodic [DeadmanWorker] via WorkManager
 * (main mechanism, device unlocked/in use), plus a safety-net alarm
 * [armSafetyNet] via `AlarmManager` (complementary mechanism, the only
 * one that covers the "reboot then never unlocked again" window —
 * WorkManager doesn't run during Direct Boot).
 *
 * WorkManager frequency: every 15 minutes — the minimum granularity
 * `PeriodicWorkRequest` offers. Fine since the switch's minimum interval
 * is 1 hour; [DeadmanWorker] refreshes the safety-net alarm on every
 * tick, so it stays at most 15 min "behind" a recent check-in or
 * interval change.
 */
object DeadmanScheduler {

    private const val TAG = "DeadmanScheduler"
    private const val UNIQUE_WORK_NAME = "oblivion.deadman.periodic"
    private const val SAFETY_NET_REQUEST_CODE = 0xDEAD_BEEF.toInt()

    /** Schedules the job if [enabled] == true, cancels it otherwise. */
    fun apply(context: Context, enabled: Boolean) {
        if (enabled) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        try {
            val req = PeriodicWorkRequestBuilder<DeadmanWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            // KEEP: if a job is already scheduled, don't replace it.
            // Avoids resetting the WorkManager counter every time the
            // screen is opened.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
            SecLog.d(TAG, "schedule() enqueued (15-min periodic, KEEP policy)")
        } catch (t: Throwable) {
            SecLog.e(TAG, "schedule() threw", t)
        }
    }

    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            cancelSafetyNet(context)
            SecLog.d(TAG, "cancel() done")
        } catch (t: Throwable) {
            SecLog.e(TAG, "cancel() threw", t)
        }
    }

    /**
     * Arms (or re-arms) the Direct-Boot-capable safety-net alarm for the
     * absolute deadline [targetMs]. Called when the switch is enabled
     * and on every [DeadmanWorker] tick to stay up to date.
     */
    fun armSafetyNet(context: Context, targetMs: Long) {
        try {
            val am = context.getSystemService(AlarmManager::class.java) ?: run {
                SecLog.e(TAG, "armSafetyNet: AlarmManager null")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                SecLog.e(TAG, "armSafetyNet: canScheduleExactAlarms == false — no-op")
                return
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMs, buildSafetyNetPendingIntent(context))
            SecLog.d(TAG, "armSafetyNet() scheduled for $targetMs")
        } catch (t: Throwable) {
            SecLog.e(TAG, "armSafetyNet() threw", t)
        }
    }

    fun cancelSafetyNet(context: Context) {
        try {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            am.cancel(buildSafetyNetPendingIntent(context))
            SecLog.d(TAG, "cancelSafetyNet() done")
        } catch (t: Throwable) {
            SecLog.e(TAG, "cancelSafetyNet() threw", t)
        }
    }

    private fun buildSafetyNetPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DeadmanAlarmReceiver::class.java).apply {
            action = DeadmanAlarmReceiver.ACTION_FIRE
            setPackage(context.packageName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, SAFETY_NET_REQUEST_CODE, intent, flags)
    }
}
