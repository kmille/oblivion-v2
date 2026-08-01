package oblivion.v2.core.deadman

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import oblivion.v2.core.log.SecLog
import oblivion.v2.core.wipe.WipeGateway

/**
 * Safety net for the Dead Man's Switch: fired by [DeadmanScheduler]'s
 * `AlarmManager` at the computed deadline (`lastCheckInMs + intervalMs`).
 *
 * The periodic [DeadmanWorker] (WorkManager, every 15 min) remains the
 * main mechanism while the device is unlocked/in use. This receiver only
 * covers the "reboot then never unlocked again" window — WorkManager
 * doesn't run during Direct Boot, this receiver does (see manifest:
 * `directBootAware="true"`).
 */
class DeadmanAlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DeadmanAlarmEntryPoint {
        fun deadmanConfigStore(): DeadmanConfigStore
        fun wipeGateway(): WipeGateway
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return

        try {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                DeadmanAlarmEntryPoint::class.java,
            )
            val store = ep.deadmanConfigStore()
            val gateway = ep.wipeGateway()
            val cfg = store.load()

            SecLog.d(TAG, "onReceive: enabled=${cfg.enabled} isExpired=${cfg.isExpired()}")

            if (!cfg.isReady() || !cfg.isExpired()) {
                // False trigger (e.g. a check-in happened in the
                // meantime without a clean re-arm): nothing to do.
                return
            }

            if (!gateway.isAdminActive()) {
                SecLog.e(TAG, "Deadman expired but admin not active — cannot wipe")
                return
            }

            SecLog.e(TAG, "Deadman EXPIRED (safety-net alarm) → wipeNow()")
            val r = gateway.wipeNow()
            SecLog.e(TAG, "wipeNow() returned $r")
        } catch (t: Throwable) {
            SecLog.e(TAG, "onReceive threw", t)
        }
    }

    companion object {
        private const val TAG = "DeadmanAlarmReceiver"
        const val ACTION_FIRE = "oblivion.v2.action.DEADMAN_ALARM_FIRE"
    }
}
