package oblivion.v2.core.scheduled

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import oblivion.v2.core.log.SecLog

/**
 * Helper autour de [AlarmManager] pour armer / désarmer l'alarme du
 * wipe programmé.
 *
 * Utilise `setExactAndAllowWhileIdle` — seul mode qui perce Doze pour
 * des échéances au-delà de quelques heures.  Sur Android 12+ exige la
 * permission `SCHEDULE_EXACT_ALARM` (runtime, non-dangereuse mais
 * soumise au toggle utilisateur dans Paramètres).
 */
object ScheduledWipeScheduler {

    private const val TAG = "ScheduledWipeScheduler"
    private const val REQUEST_CODE = 0xDEAD_C0DE.toInt()

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    /** Arme l'alarme au [wipeAtMs] absolu. */
    fun arm(context: Context, wipeAtMs: Long) {
        try {
            val am = context.getSystemService(AlarmManager::class.java) ?: run {
                SecLog.e(TAG, "AlarmManager null")
                return
            }
            val pi = buildPendingIntent(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                SecLog.e(TAG, "canScheduleExactAlarms == false — arm() no-op")
                return
            }

            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                wipeAtMs,
                pi,
            )
            SecLog.d(TAG, "arm() scheduled for $wipeAtMs")
        } catch (t: Throwable) {
            SecLog.e(TAG, "arm() threw", t)
        }
    }

    fun cancel(context: Context) {
        try {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = buildPendingIntent(context)
            am.cancel(pi)
            SecLog.d(TAG, "cancel() done")
        } catch (t: Throwable) {
            SecLog.e(TAG, "cancel() threw", t)
        }
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduledWipeReceiver::class.java).apply {
            action = ScheduledWipeReceiver.ACTION_FIRE
            setPackage(context.packageName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
