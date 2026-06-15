package oblivion.v2.feature.scheduled

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import oblivion.v2.core.scheduled.ScheduledWipeConfig
import oblivion.v2.core.scheduled.ScheduledWipeScheduler
import oblivion.v2.core.scheduled.ScheduledWipeStore
import oblivion.v2.core.wipe.WipeGateway
import javax.inject.Inject

/**
 * ViewModel du Wipe Programmé.
 *
 * L'arm/désarm s'exécute en silence : aucune pré-alerte ni notification.
 * L'annulation (désarm) est protégée par biométrie côté UI.
 */
@HiltViewModel
class ScheduledWipeViewModel @Inject constructor(
    app: Application,
    private val store: ScheduledWipeStore,
    private val wipeGateway: WipeGateway,
) : AndroidViewModel(app) {

    val config: StateFlow<ScheduledWipeConfig> = store.config

    fun isAdminActive(): Boolean = wipeGateway.isAdminActive()

    fun canScheduleExactAlarms(): Boolean =
        ScheduledWipeScheduler.canScheduleExactAlarms(getApplication())

    /** Arme l'alarme pour l'instant absolu [wipeAtMs]. Appelé depuis l'UI. */
    fun arm(wipeAtMs: Long) {
        if (!wipeGateway.isAdminActive()) return
        if (wipeAtMs <= System.currentTimeMillis()) return
        val ctx = getApplication<Application>()
        store.saveArmed(wipeAtMs)
        ScheduledWipeScheduler.arm(ctx, wipeAtMs)
    }

    /**
     * Désarme l'alarme.  **À N'APPELER QU'APRÈS AUTH BIOMÉTRIQUE RÉUSSIE.**
     * La couche UI doit garantir ce prérequis.
     */
    fun disarmAfterAuth() {
        val ctx = getApplication<Application>()
        ScheduledWipeScheduler.cancel(ctx)
        store.clear()
    }
}
