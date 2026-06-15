package oblivion.v2.core.deadman

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import oblivion.v2.core.log.SecLog
import oblivion.v2.core.wipe.WipeGateway

/**
 * Worker périodique qui vérifie si l'échéance du Dead Man's Switch est
 * dépassée et déclenche le wipe si c'est le cas.
 *
 * On utilise le pattern [EntryPoint] Hilt plutôt que `hilt-work` complet :
 * plus simple, moins de dépendances, et ici c'est le seul Worker de l'app.
 *
 * Silencieux : pas de notification, pas de retour visible à l'utilisateur.
 * L'utilisateur final (le détenteur du téléphone dans un scénario adverse)
 * ne doit pas savoir qu'un switch est en place.
 */
class DeadmanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DeadmanEntryPoint {
        fun deadmanConfigStore(): DeadmanConfigStore
        fun wipeGateway(): WipeGateway
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                DeadmanEntryPoint::class.java,
            )
            val store = entryPoint.deadmanConfigStore()
            val gateway = entryPoint.wipeGateway()

            val cfg = store.load()
            SecLog.d(TAG, "doWork() enabled=${cfg.enabled} ready=${cfg.isReady()} remainingMs=${cfg.remainingMs()}")

            if (!cfg.isReady()) {
                // Rien à faire : switch désactivé ou pas encore de check-in.
                return Result.success()
            }

            if (cfg.isExpired()) {
                if (!gateway.isAdminActive()) {
                    SecLog.e(TAG, "Deadman expired but admin not active — cannot wipe")
                    return Result.success()
                }
                SecLog.e(TAG, "Deadman EXPIRED → wipeNow()")
                val r = gateway.wipeNow()
                SecLog.e(TAG, "wipeNow() returned $r")
            }
            Result.success()
        } catch (t: Throwable) {
            SecLog.e(TAG, "DeadmanWorker threw", t)
            // Retry pour ne pas rester silencieusement cassé.
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DeadmanWorker"
    }
}
