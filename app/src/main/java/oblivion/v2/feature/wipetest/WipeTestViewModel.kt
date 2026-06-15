package oblivion.v2.feature.wipetest

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import oblivion.v2.core.wipe.WipeGateway

/**
 * ViewModel de l'écran de test du wipe (Étape 1).
 */
class WipeTestViewModel(app: Application) : AndroidViewModel(app) {

    private val gateway = WipeGateway(app)

    private val _adminActive = MutableStateFlow(gateway.isAdminActive())
    val adminActive: StateFlow<Boolean> = _adminActive.asStateFlow()

    /** Dernier résultat du wipe (affiché en Snackbar pour diagnostic). */
    private val _lastWipeMessage = MutableStateFlow<String?>(null)
    val lastWipeMessage: StateFlow<String?> = _lastWipeMessage.asStateFlow()

    /** ComponentName exact utilisé (diagnostic). */
    val adminComponentName: String = gateway.adminComponentName().flattenToShortString()

    /** À rappeler à chaque résumé de l'activity (onResume) pour rafraîchir l'UI. */
    fun refreshAdminState() {
        _adminActive.value = gateway.isAdminActive()
    }

    /** Intent système pour demander l'activation de l'admin device. */
    fun requestAdminIntent() = gateway.requestAdminIntent()

    /**
     * Déclenche le wipe IRRÉVERSIBLE.  Ne doit être appelé qu'après
     * double confirmation utilisateur.
     *
     * Si l'appareil ne s'éteint pas dans la foulée, le message remonté
     * dans [lastWipeMessage] nous dit pourquoi.
     */
    fun triggerWipeNow() {
        val result = gateway.wipeNow()
        _lastWipeMessage.value = when (result) {
            is WipeGateway.WipeResult.Called ->
                "DPM.wipeData() appelé sans exception. Si l'appareil ne se réinitialise pas, " +
                    "le système a ignoré l'appel silencieusement. " +
                    "Vérifie logcat (tag=WipeGateway)."
            is WipeGateway.WipeResult.NoDpm ->
                "Erreur : DevicePolicyManager est null."
            is WipeGateway.WipeResult.NotAdmin ->
                "Admin inactif pour ${result.expected}. Active-le d'abord."
            is WipeGateway.WipeResult.Error ->
                "Exception ${result.type} : ${result.message}"
        }
    }

    fun clearWipeMessage() {
        _lastWipeMessage.value = null
    }
}
