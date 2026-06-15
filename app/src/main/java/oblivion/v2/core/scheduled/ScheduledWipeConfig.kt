package oblivion.v2.core.scheduled

/**
 * Configuration du Wipe Programmé.
 *
 * Principe : l'utilisateur fixe un instant futur (epoch millis).  Quand
 * cet instant est atteint, un AlarmManager.setExactAndAllowWhileIdle
 * déclenche un BroadcastReceiver qui lance le wipe.  L'annulation exige
 * une authentification biométrique.  Silencieux : aucune pré-alerte.
 *
 * @property enabled    `true` si une alarme est armée.
 * @property wipeAtMs   Timestamp absolu (System.currentTimeMillis) du wipe.
 *                      0 si aucune alarme n'est armée.
 */
data class ScheduledWipeConfig(
    val enabled: Boolean = false,
    val wipeAtMs: Long = 0L,
) {
    /** `true` si l'alarme est dans le futur. */
    fun isArmed(nowMs: Long = System.currentTimeMillis()): Boolean =
        enabled && wipeAtMs > nowMs

    /** Durée restante avant déclenchement (ms).  Négatif si passé. */
    fun remainingMs(nowMs: Long = System.currentTimeMillis()): Long =
        if (!enabled) 0L else wipeAtMs - nowMs
}
