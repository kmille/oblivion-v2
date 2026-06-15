package oblivion.v2.core.voice

/**
 * Configuration du trigger Voice Wipe.
 *
 * @param enabled       Master switch
 * @param phrase        Phrase-clé que l'utilisateur doit prononcer pour
 *                      déclencher le wipe.  Stockée en clair (on doit la
 *                      comparer au texte reconnu par Vosk en temps réel).
 * @param strict        `true` = match exact (recommandé) ; `false` = match
 *                      approximatif (permet les variations de reconnaissance).
 * @param language      Langue du modèle Vosk à utiliser : [LANG_FR] ou [LANG_EN].
 *                      Détermine quel sous-dossier d'assets est utilisé
 *                      (`assets/model-fr/` ou `assets/model-en/`).
 */
data class VoiceKillConfig(
    val enabled: Boolean = false,
    val phrase: String = "",
    val strict: Boolean = true,
    val language: String = LANG_FR,
) {
    fun isReady(): Boolean = enabled && phrase.isNotBlank()

    companion object {
        /** Longueur minimale d'une phrase-clé pour éviter les faux positifs. */
        const val MIN_PHRASE_LENGTH = 6
        const val MAX_PHRASE_LENGTH = 80

        /** Codes de langue supportés (correspondent à `assets/model-<code>/`). */
        const val LANG_FR = "fr"
        const val LANG_EN = "en"
    }
}
