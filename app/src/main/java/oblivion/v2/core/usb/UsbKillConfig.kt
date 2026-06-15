package oblivion.v2.core.usb

/**
 * Configuration du trigger USB Kill.
 *
 * - [enabled] : le service USB tourne-t-il ?  Quand true, le service
 *   foreground est actif en permanence et surveille les branchements.
 * - [graceSeconds] : délai de grâce entre détection du branchement et
 *   wipe.  Permet de débrancher en urgence si c'est un faux positif.
 *   0 = wipe immédiat.
 *
 * Règle de déclenchement (hardcodée à l'étape 3) :
 *  - branchement détecté ET écran verrouillé ⇒ compte à rebours
 *  - débranchement pendant le compte à rebours ⇒ abort
 *  - compte à rebours atteint zéro ⇒ wipe
 */
data class UsbKillConfig(
    val enabled: Boolean = false,
    val graceSeconds: Int = DEFAULT_GRACE_SECONDS,
) {
    fun isValid(): Boolean = graceSeconds in MIN_GRACE_SECONDS..MAX_GRACE_SECONDS

    companion object {
        const val MIN_GRACE_SECONDS = 0
        const val MAX_GRACE_SECONDS = 30
        const val DEFAULT_GRACE_SECONDS = 5
    }
}
