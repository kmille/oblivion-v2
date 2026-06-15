package oblivion.v2.core.guard.detector

import oblivion.v2.core.log.SecLog
import android.view.accessibility.AccessibilityEvent
import oblivion.v2.core.guard.GuardConfigStore
import oblivion.v2.core.guard.GuardDetector

/**
 * Détecteur de TENTATIVES ÉCHOUÉES au lockscreen.
 *
 * Android émet une `TYPE_ANNOUNCEMENT` à chaque code PIN / mot de passe
 * incorrect.  Le texte exact dépend de la locale système (FR/EN) et de
 * l'OEM.  On fait donc un matching **assez large** (insensible à la casse,
 * `contains` et non `startsWith`, vocabulaire FR + EN).
 *
 * À chaque détection confirmée :
 *  - on incrémente le compteur persisté dans [GuardConfigStore]
 *  - si le compteur atteint [threshold] ⇒ match ⇒ wipe
 *
 * Le compteur ne se remet à zéro qu'au **déverrouillage réussi** (via
 * `ACTION_USER_PRESENT` dans le service), il survit donc aux redémarrages.
 */
class FailedAttemptsDetector(
    private val store: GuardConfigStore,
    private val threshold: Int,
) : GuardDetector {

    override val name: String = "FailedAttempts"

    override fun onEvent(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_ANNOUNCEMENT) return false

        val phrases = event.text.orEmpty().mapNotNull { it?.toString() }
        if (phrases.isEmpty()) return false

        // Log systématique pour faciliter le debug sur appareils variés.
        SecLog.d(TAG, "ANNOUNCEMENT: $phrases")

        val matched = phrases.any { raw ->
            val t = raw.lowercase()
            KEYWORDS.any { kw -> t.contains(kw) }
        }
        if (!matched) return false

        val count = store.incrementFailedAttemptsCount()
        SecLog.d(TAG, "Failed attempt detected. Count = $count / threshold = $threshold")
        return count >= threshold
    }

    override fun reset() {
        // Pas d'état local — compteur persistant, reset uniquement au
        // déverrouillage réussi (géré par GuardAccessibilityService).
    }

    private companion object {
        private const val TAG = "FailedAttemptsDetector"

        /**
         * Vocabulaire « PIN/mot de passe refusé », FR + EN.
         *  - "incorrect" : identique FR/EN (« PIN incorrect », « Password incorrect »)
         *  - "wrong"     : EN (« Wrong PIN »)
         *  - "erron"     : préfixe FR (« erroné », « erronée »)
         *  - "réessay" / "essay" : FR (« Réessayez dans … »)
         *  - "try again" : EN
         *  - "faux"      : FR (« code faux »)
         */
        private val KEYWORDS = listOf(
            "incorrect",
            "wrong",
            "erron",
            "réessay",
            "try again",
            "faux",
        )
    }
}
