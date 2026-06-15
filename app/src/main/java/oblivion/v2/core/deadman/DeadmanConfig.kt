package oblivion.v2.core.deadman

import java.util.concurrent.TimeUnit

/**
 * Configuration du "Dead Man's Switch".
 *
 * Principe : si l'utilisateur ne s'authentifie pas biométriquement dans
 * l'application pendant [intervalMs] millisecondes, un wipe automatique
 * est déclenché.
 *
 * Cas d'usage : user détenu, téléphone confisqué → absence de check-in
 * régulier → wipe sans intervention humaine.
 *
 * @property enabled        Master switch.
 * @property intervalMs     Durée d'inactivité avant wipe, en ms.
 * @property lastCheckInMs  Timestamp `System.currentTimeMillis` du dernier
 *                          check-in réussi (0 si jamais).
 */
data class DeadmanConfig(
    val enabled: Boolean = false,
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    val lastCheckInMs: Long = 0L,
) {
    /** Prêt à fonctionner : activé + au moins un check-in effectué. */
    fun isReady(): Boolean = enabled && lastCheckInMs > 0 && intervalMs >= MIN_INTERVAL_MS

    /** Durée restante avant wipe (ms). Négatif si déjà expiré. */
    fun remainingMs(nowMs: Long = System.currentTimeMillis()): Long =
        if (lastCheckInMs == 0L) intervalMs else (lastCheckInMs + intervalMs) - nowMs

    /** `true` si l'échéance est dépassée. */
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        isReady() && remainingMs(nowMs) <= 0

    companion object {
        /** Intervalle minimum : 1 heure — évite les déclenchements accidentels. */
        val MIN_INTERVAL_MS: Long = TimeUnit.HOURS.toMillis(1)

        /** Intervalle maximum : 60 jours — pas utile au-delà. */
        val MAX_INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(60)

        /** Intervalle par défaut : 48 heures. */
        val DEFAULT_INTERVAL_MS: Long = TimeUnit.HOURS.toMillis(48)
    }
}
