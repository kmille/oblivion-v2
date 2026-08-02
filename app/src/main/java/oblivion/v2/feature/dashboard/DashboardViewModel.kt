package oblivion.v2.feature.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import oblivion.v2.core.deadman.DeadmanConfig
import oblivion.v2.core.deadman.DeadmanConfigStore
import oblivion.v2.core.guard.GuardConfig
import oblivion.v2.core.guard.GuardConfigStore
import oblivion.v2.core.scheduled.ScheduledWipeConfig
import oblivion.v2.core.scheduled.ScheduledWipeStore
import oblivion.v2.core.system.SystemCheckHelper
import oblivion.v2.core.wipe.WipeGateway
import javax.inject.Inject

/**
 * ViewModel du Dashboard.
 *
 * Agrège les [StateFlow] de chaque trigger + l'état Device Admin +
 * les checks système (afficher mots de passe, confidentialité PIN).
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    app: Application,
    private val wipeGateway: WipeGateway,
    private val systemCheck: SystemCheckHelper,
    guardStore: GuardConfigStore,
    deadmanStore: DeadmanConfigStore,
    scheduledStore: ScheduledWipeStore,
) : AndroidViewModel(app) {

    val guardConfig: StateFlow<GuardConfig> = guardStore.config
    val deadmanConfig: StateFlow<DeadmanConfig> = deadmanStore.config
    val scheduledConfig: StateFlow<ScheduledWipeConfig> = scheduledStore.config

    private val _adminActive = MutableStateFlow(wipeGateway.isAdminActive())
    val adminActive: StateFlow<Boolean> = _adminActive.asStateFlow()

    private val _showPasswordOk = MutableStateFlow(systemCheck.isShowPasswordEnabled())
    val showPasswordOk: StateFlow<Boolean> = _showPasswordOk.asStateFlow()

    private val _pinPrivacyOk = MutableStateFlow(systemCheck.isEnhancedPinPrivacyDisabled())
    val pinPrivacyOk: StateFlow<Boolean> = _pinPrivacyOk.asStateFlow()

    /** Rafraîchit tous les états — appelé quand l'utilisateur revient sur le Dashboard. */
    fun refreshAdminState() {
        _adminActive.value = wipeGateway.isAdminActive()
        _showPasswordOk.value = systemCheck.isShowPasswordEnabled()
        _pinPrivacyOk.value = systemCheck.isEnhancedPinPrivacyDisabled()
    }

    fun requestAdminIntent() = wipeGateway.requestAdminIntent()

    val adminComponentName: String =
        wipeGateway.adminComponentName().flattenToShortString()
}
