package oblivion.v2.core.scheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import oblivion.v2.core.log.SecLog

/**
 * Receiver BOOT_COMPLETED : ré-arme l'alarme programmée après un reboot.
 *
 * AlarmManager perd ses alarmes quand l'appareil redémarre ; sans ce
 * receiver le wipe programmé serait silencieusement annulé.
 *
 * Si l'échéance est déjà dans le passé au moment du boot, on déclenche
 * le wipe immédiatement (sinon on arme pour la date future).
 */
class ScheduledWipeBootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun scheduledWipeStore(): ScheduledWipeStore
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        SecLog.d(TAG, "onReceive action=$action")
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        try {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                BootEntryPoint::class.java,
            )
            val cfg = ep.scheduledWipeStore().load()
            if (!cfg.enabled || cfg.wipeAtMs <= 0L) {
                SecLog.d(TAG, "No schedule to re-arm")
                return
            }

            val now = System.currentTimeMillis()
            if (cfg.wipeAtMs <= now) {
                // Échéance passée pendant le reboot → déclencher maintenant
                // via le receiver principal.
                SecLog.e(TAG, "Schedule in the past — firing now")
                val fireIntent = Intent(context, ScheduledWipeReceiver::class.java).apply {
                    this.action = ScheduledWipeReceiver.ACTION_FIRE
                    setPackage(context.packageName)
                }
                context.sendBroadcast(fireIntent)
            } else {
                SecLog.d(TAG, "Re-arming alarm for ${cfg.wipeAtMs}")
                ScheduledWipeScheduler.arm(context, cfg.wipeAtMs)
            }
        } catch (t: Throwable) {
            SecLog.e(TAG, "onReceive threw", t)
        }
    }

    companion object {
        private const val TAG = "ScheduledWipeBootReceiver"
    }
}
