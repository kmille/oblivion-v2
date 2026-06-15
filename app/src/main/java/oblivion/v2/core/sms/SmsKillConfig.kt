package oblivion.v2.core.sms

/**
 * Configuration du trigger SMS Wipe.
 *
 * @property enabled   Master switch.
 * @property senderNumber  Numéro de téléphone autorisé (ex: "+33612345678").
 *                         Seuls les SMS provenant de ce numéro sont analysés.
 * @property keyword   Mot-clé secret qui déclenche le wipe.
 */
data class SmsKillConfig(
    val enabled: Boolean = false,
    val senderNumber: String = "",
    val keyword: String = "",
) {
    /** Prêt à fonctionner : activé + numéro + mot-clé renseignés. */
    fun isReady(): Boolean =
        enabled && senderNumber.isNotBlank() && keyword.length >= MIN_KEYWORD_LENGTH

    companion object {
        const val MIN_KEYWORD_LENGTH = 4
        const val MAX_KEYWORD_LENGTH = 50
    }
}
