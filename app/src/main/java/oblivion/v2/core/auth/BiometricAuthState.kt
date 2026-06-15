package oblivion.v2.core.auth

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import oblivion.v2.core.deadman.DeadmanConfigStore
import oblivion.v2.core.log.SecLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestion centralisée de l'état d'authentification biométrique.
 *
 * **Rôle** : garder en mémoire (process-scoped) si l'utilisateur s'est
 * authentifié récemment via empreinte/face/PIN système, et exiger une
 * nouvelle authentification après [INACTIVITY_TIMEOUT_MS] d'inactivité.
 *
 * **Pas de persistance** : l'état est volontairement perdu au redémarrage
 * du process (kill/reboot) — on exige toujours une nouvelle
 * authentification au cold start.
 *
 * **Timestamp basé sur [SystemClock.elapsedRealtime]** : ne peut pas être
 * trompé par un changement d'heure système (contrairement à
 * `System.currentTimeMillis`).
 */
@Singleton
class BiometricAuthState @Inject constructor(
    private val deadmanConfigStore: DeadmanConfigStore,
) {

    /** Timestamp elapsedRealtime de la dernière auth réussie, ou 0 si jamais. */
    @Volatile
    private var lastAuthAtElapsedMs: Long = 0L

    private val _isAuthenticated = MutableStateFlow(false)

    /** `true` si l'utilisateur est actuellement considéré comme authentifié. */
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    /**
     * Marque une authentification réussie (à appeler depuis le callback
     * onAuthenticationSucceeded du BiometricPrompt).
     *
     * Déclenche aussi un check-in Dead Man's Switch : l'utilisateur
     * prouvant sa présence, le compteur d'inactivité est remis à zéro.
     */
    fun markAuthenticated() {
        lastAuthAtElapsedMs = SystemClock.elapsedRealtime()
        _isAuthenticated.value = true
        // Dead Man's Switch : check-in silencieux à chaque auth réussie.
        try {
            deadmanConfigStore.touchCheckIn()
        } catch (t: Throwable) {
            SecLog.e(TAG, "touchCheckIn threw", t)
        }
    }

    /**
     * Invalide explicitement l'authentification (ex : au kill d'app ou
     * sur demande de l'utilisateur).
     */
    fun invalidate() {
        lastAuthAtElapsedMs = 0L
        _isAuthenticated.value = false
    }

    /**
     * Vérifie si l'authentification est toujours valide compte tenu
     * du timeout d'inactivité. Doit être appelée à chaque `onResume` de
     * l'activity. Si expirée, invalide l'état.
     *
     * @return `true` si encore valide, `false` si expiration détectée.
     */
    fun checkStillValid(): Boolean {
        if (lastAuthAtElapsedMs == 0L) {
            _isAuthenticated.value = false
            return false
        }
        val elapsed = SystemClock.elapsedRealtime() - lastAuthAtElapsedMs
        val stillValid = elapsed < INACTIVITY_TIMEOUT_MS
        if (!stillValid) {
            invalidate()
        } else {
            // On rafraîchit le "last auth" pour que l'app reste débloquée
            // tant qu'elle est activement utilisée.
            lastAuthAtElapsedMs = SystemClock.elapsedRealtime()
        }
        return stillValid
    }

    companion object {
        private const val TAG = "BiometricAuthState"
        /** 1 minute d'inactivité avant de redemander l'authentification. */
        const val INACTIVITY_TIMEOUT_MS: Long = 60_000L
    }
}
