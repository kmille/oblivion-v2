package oblivion.v2.feature.deadman

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import oblivion.v2.core.deadman.DeadmanConfig
import oblivion.v2.core.deadman.DeadmanConfigStore
import oblivion.v2.core.deadman.DeadmanScheduler
import oblivion.v2.core.wipe.WipeGateway
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel de l'écran Dead Man's Switch.
 *
 * Propose de régler l'intervalle soit en heures (1–72) soit en jours
 * (1–60) via une bascule d'unité.  L'activation exige admin device
 * actif (sinon le wipe est impossible).
 *
 * Fonctionnement 100% silencieux : pas de notification, pas d'indicateur.
 */
@HiltViewModel
class DeadmanViewModel @Inject constructor(
    app: Application,
    private val store: DeadmanConfigStore,
    private val wipeGateway: WipeGateway,
) : AndroidViewModel(app) {

    val config: StateFlow<DeadmanConfig> = store.config

    fun isAdminActive(): Boolean = wipeGateway.isAdminActive()

    fun setEnabled(enabled: Boolean) {
        if (enabled && !wipeGateway.isAdminActive()) return
        store.setEnabled(enabled)
        DeadmanScheduler.apply(getApplication(), enabled)
    }

    /** Règle l'intervalle en heures (bornes MIN/MAX appliquées côté store). */
    fun setIntervalHours(hours: Long) {
        val safeHours = hours.coerceAtLeast(1L)
        store.setIntervalMs(TimeUnit.HOURS.toMillis(safeHours))
    }

    /** Règle l'intervalle en jours (converti en heures puis appliqué). */
    fun setIntervalDays(days: Long) {
        val safeDays = days.coerceAtLeast(1L)
        store.setIntervalMs(TimeUnit.DAYS.toMillis(safeDays))
    }

    /** Force un check-in manuel (utile si l'utilisateur ne fait pas d'auth biométrique). */
    fun checkInNow() {
        store.touchCheckIn()
    }

    /** Intervalle affiché en heures (arrondi). */
    fun intervalAsHours(cfg: DeadmanConfig): Long =
        TimeUnit.MILLISECONDS.toHours(cfg.intervalMs).coerceAtLeast(1L)

    /** Intervalle affiché en jours (arrondi). */
    fun intervalAsDays(cfg: DeadmanConfig): Long =
        TimeUnit.MILLISECONDS.toDays(cfg.intervalMs).coerceAtLeast(1L)
}
