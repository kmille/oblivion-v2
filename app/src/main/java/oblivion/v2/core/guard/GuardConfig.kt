package oblivion.v2.core.guard

/**
 * Configuration complète du Garde-clefs.
 *
 * Les 4 détecteurs sont **combinables** (plusieurs actifs en même temps).
 * Si n'importe lequel matche pendant que [masterEnabled] est vrai et que
 * l'écran est verrouillé, le wipe est déclenché.
 *
 * ⚠️ Aucun PIN / mot de passe n'est stocké en clair.  Seuls les hash
 * SHA-256 + sel sont persistés (voir [oblivion.v2.core.crypto.PinHasher]).
 */
data class GuardConfig(
    /** Interrupteur maître (AccessibilityService actif ?). */
    val masterEnabled: Boolean = false,

    /** Type A : mot de passe de détresse #1 → wipe. */
    val typeAEnabled: Boolean = false,
    val typeAHash: String = "",
    val typeASalt: String = "",

    /**
     * Type B : longueur-piège.  Si un PIN de longueur exactement
     * [typeBLength] est saisi sur le lockscreen, wipe.
     *
     * Le vrai PIN de l'utilisateur DOIT avoir une longueur différente
     * sinon il déclenchera le wipe à chaque déverrouillage.
     */
    val typeBEnabled: Boolean = false,
    val typeBLength: Int = 0,

    /** EMERGENCY : mot de passe d'urgence → wipe (avec buffer visible). */
    val emergencyEnabled: Boolean = false,
    val emergencyHash: String = "",
    val emergencySalt: String = "",

    /**
     * Tentatives échouées : après [failedAttemptsThreshold] échecs de
     * déverrouillage, wipe.  Le compteur se remet à 0 à chaque
     * déverrouillage réussi.
     */
    val failedAttemptsEnabled: Boolean = false,
    val failedAttemptsThreshold: Int = 10,
) {
    companion object {
        /** Longueur minimale d'un PIN / pw de détresse pour éviter les faux positifs. */
        const val MIN_SECRET_LENGTH: Int = 4

        /** Longueur-piège minimale (Type B).  En dessous, trop de faux positifs. */
        const val MIN_TRAP_LENGTH: Int = 4

        /** Seuil min de tentatives avant wipe auto (éviter de se tirer dans le pied). */
        const val MIN_FAILED_ATTEMPTS: Int = 3
    }

    /** Sanity check utilisé par l'UI : true si la config est cohérente. */
    fun isValid(): Boolean {
        if (typeAEnabled && (typeAHash.isEmpty() || typeASalt.isEmpty())) return false
        if (typeBEnabled && typeBLength < MIN_TRAP_LENGTH) return false
        if (emergencyEnabled && (emergencyHash.isEmpty() || emergencySalt.isEmpty())) return false
        if (failedAttemptsEnabled && failedAttemptsThreshold < MIN_FAILED_ATTEMPTS) return false
        return true
    }

    /** true si au moins un détecteur est activé (sinon le service ne sert à rien). */
    fun hasAnyDetectorEnabled(): Boolean =
        typeAEnabled || typeBEnabled || emergencyEnabled || failedAttemptsEnabled
}
