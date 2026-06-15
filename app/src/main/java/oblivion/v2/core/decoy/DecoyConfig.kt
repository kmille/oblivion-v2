package oblivion.v2.core.decoy

/**
 * Configuration du Mode Decoy (leurre).
 *
 * Principe (Option A) : l'utilisateur définit un PIN "leurre". Si ce PIN est
 * saisi sur le lockscreen pendant que le Garde-clefs est actif :
 *   1. Le wipe est déclenché silencieusement en arrière-plan.
 *   2. Une fausse page "Mise à jour système" plein écran s'affiche pour
 *      masquer le déclenchement — l'attaquant pense que le téléphone met
 *      à jour Android alors qu'il s'efface.
 *
 * ⚠️ Aucun PIN n'est stocké en clair. Seuls le hash SHA-256 + le sel
 * sont persistés (voir [oblivion.v2.core.crypto.PinHasher]).
 *
 * @property enabled Le mode decoy est-il actif ?
 * @property pinHash Hash SHA-256 du PIN leurre (Base64).
 * @property pinSalt Sel (Base64) utilisé pour le hash.
 */
data class DecoyConfig(
    val enabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
) {
    /** Vrai si la config peut être armée (toggle ON + PIN configuré). */
    fun isReady(): Boolean =
        enabled && pinHash.isNotEmpty() && pinSalt.isNotEmpty()

    /** Vrai si un PIN leurre est configuré (qu'il soit actif ou non). */
    fun isConfigured(): Boolean =
        pinHash.isNotEmpty() && pinSalt.isNotEmpty()

    companion object {
        /** Longueur minimale pour le PIN leurre (évite les collisions). */
        const val MIN_PIN_LENGTH: Int = 4
    }
}
