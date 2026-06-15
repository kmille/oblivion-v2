package oblivion.v2.feature.decoy

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import oblivion.v2.core.decoy.DecoyConfig
import oblivion.v2.core.decoy.DecoyConfigStore
import oblivion.v2.core.wipe.WipeGateway
import oblivion.v2.ui.decoy.DecoyNotifier
import javax.inject.Inject

/**
 * ViewModel de l'écran Mode Decoy (leurre, Option A).
 *
 * Responsabilités :
 *  - exposer la [DecoyConfig] courante
 *  - gérer l'enregistrement du PIN leurre (hashé côté store)
 *  - gérer l'interrupteur master
 *  - indiquer si l'Admin device est actif (pré-requis pour le wipe)
 *
 * Fonctionnement à l'usage :
 *  1. L'utilisateur configure son PIN leurre (ex: 9876).
 *  2. Active le toggle.
 *  3. Si le GuardAccessibilityService est actif, le DecoyDetector
 *     s'arme automatiquement.
 *  4. Quand le PIN leurre est tapé sur le lockscreen → fausse page
 *     "Mise à jour système" + wipe en arrière-plan.
 */
@HiltViewModel
class DecoyViewModel @Inject constructor(
    app: Application,
    private val store: DecoyConfigStore,
    private val wipeGateway: WipeGateway,
) : AndroidViewModel(app) {

    val config: StateFlow<DecoyConfig> = store.config

    fun isAdminActive(): Boolean = wipeGateway.isAdminActive()

    /**
     * Permission "Notifications plein écran" (USE_FULL_SCREEN_INTENT).
     * Requise pour l'écran leurre sur Android 14+ (auto-grantée avant).
     * Sans elle, le wipe part quand même mais aucune fausse MAJ ne s'affiche.
     */
    fun hasFullScreenIntentPermission(): Boolean =
        DecoyNotifier.canUseFullScreenIntent(getApplication())

    /**
     * Intent pour ouvrir la page "Notifications plein écran" des paramètres
     * système (Android 14+ uniquement). `null` si la version d'Android ne
     * nécessite pas de grant manuel.
     */
    fun fullScreenIntentSettings(): Intent? =
        DecoyNotifier.fullScreenIntentSettingsIntent(getApplication())

    fun setEnabled(enabled: Boolean) {
        // On bloque l'activation si l'admin n'est pas actif : sans admin,
        // le wipe ne partira pas et le leurre sert à rien.
        if (enabled && !wipeGateway.isAdminActive()) return
        // Pareil : impossible d'activer sans PIN configuré.
        if (enabled && !store.config.value.isConfigured()) return
        store.setEnabled(enabled)
    }

    /** Enregistre un nouveau PIN leurre (hashé). Vide = effacement. */
    fun setDecoyPin(pin: String) {
        store.setDecoyPin(pin)
    }

    /** Efface le PIN et désactive. */
    fun clearDecoyPin() {
        store.setDecoyPin("")
    }
}
