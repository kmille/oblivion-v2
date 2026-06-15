package oblivion.v2.core.deadman

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import oblivion.v2.core.log.SecLog
import java.util.concurrent.TimeUnit

/**
 * Planifie (ou annule) le [DeadmanWorker] périodique via WorkManager.
 *
 * WorkManager garantit l'exécution même après redémarrage de l'appareil
 * (pourvu que le système soit sorti du mode Direct Boot).
 *
 * Fréquence : toutes les 15 minutes — granularité minimale offerte par
 * `PeriodicWorkRequest`.  Suffisant puisque l'intervalle minimum du
 * switch est 1 heure.
 */
object DeadmanScheduler {

    private const val TAG = "DeadmanScheduler"
    private const val UNIQUE_WORK_NAME = "oblivion.deadman.periodic"

    /** Planifie la tâche si [enabled] == true, l'annule sinon. */
    fun apply(context: Context, enabled: Boolean) {
        if (enabled) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        try {
            val req = PeriodicWorkRequestBuilder<DeadmanWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            // KEEP : si un job est déjà planifié, on ne le remplace pas.
            // Évite de réinitialiser le compteur WorkManager à chaque
            // ouverture de l'écran.
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
            SecLog.d(TAG, "cancel() done")
        } catch (t: Throwable) {
            SecLog.e(TAG, "cancel() threw", t)
        }
    }
}
