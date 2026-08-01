package oblivion.v2.core.deadman

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import oblivion.v2.core.log.SecLog

/**
 * BOOT_COMPLETED / LOCKED_BOOT_COMPLETED receiver: re-arms the Dead Man's
 * Switch safety-net alarm after a reboot (AlarmManager loses its alarms
 * on restart). `directBootAware="true"`: runs even before the first
 * unlock, exactly like
 * [oblivion.v2.core.scheduled.ScheduledWipeBootReceiver].
 *
 * If the deadline (`lastCheckInMs + intervalMs`) has already passed by
 * boot time, fires the wipe immediately instead of re-arming.
 */
class DeadmanBootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DeadmanBootEntryPoint {
        fun deadmanConfigStore(): DeadmanConfigStore
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
                DeadmanBootEntryPoint::class.java,
            )
            val cfg = ep.deadmanConfigStore().load()
            if (!cfg.isReady()) {
                SecLog.d(TAG, "Not ready (disabled or no check-in yet) — nothing to arm")
                return
            }

            val targetMs = cfg.lastCheckInMs + cfg.intervalMs
            val now = System.currentTimeMillis()
            if (targetMs <= now) {
                SecLog.e(TAG, "Deadline already passed — firing now")
                val fireIntent = Intent(context, DeadmanAlarmReceiver::class.java).apply {
                    this.action = DeadmanAlarmReceiver.ACTION_FIRE
                    setPackage(context.packageName)
                }
                context.sendBroadcast(fireIntent)
            } else {
                SecLog.d(TAG, "Re-arming safety-net alarm for $targetMs")
                DeadmanScheduler.armSafetyNet(context, targetMs)
            }
        } catch (t: Throwable) {
            SecLog.e(TAG, "onReceive threw", t)
        }
    }

    companion object {
        private const val TAG = "DeadmanBootReceiver"
    }
}
