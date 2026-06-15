package oblivion.v2.core.guard.detector

import android.view.accessibility.AccessibilityEvent
import oblivion.v2.core.guard.GuardDetector

/**
 * Type B : détecteur par LONGUEUR-PIÈGE.
 *
 * Principe : l'utilisateur configure une longueur (ex: 8).  Si quelqu'un
 * tape un PIN de cette longueur exacte sur le lockscreen, wipe — peu
 * importe les chiffres.
 *
 * Usage typique : sous la contrainte, l'utilisateur tape 8 chiffres au
 * hasard au lieu de son vrai PIN (qui doit être d'une longueur
 * différente).  Le téléphone s'efface silencieusement.
 *
 * On compte les clics (chiffres du clavier) via `TYPE_VIEW_CLICKED`.
 * Le bouton "supprimer" décrémente, le bouton "OK/enter" valide.
 * Une annonce "wrong/incorrect" valide aussi (PIN soumis automatiquement
 * sur Pixel quand on atteint la longueur attendue).
 */
class TypeBDetector(
    private val targetLength: Int,
) : GuardDetector {

    override val name: String = "TypeB"

    private var pos = 0

    override fun onEvent(event: AccessibilityEvent): Boolean {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                // Géré ci-dessous
            }
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> {
                // Android annonce "Wrong PIN" / "Incorrect" quand le PIN
                // soumis est refusé.  À ce moment on sait qu'une saisie
                // complète vient d'être tentée.
                for (raw in event.text.orEmpty()) {
                    val t = raw?.toString()?.lowercase().orEmpty()
                    if (t.startsWith(WRONG_TEXT) || t.startsWith(INCORRECT_TEXT)) {
                        val ok = pos >= targetLength
                        pos = 0
                        return ok
                    }
                }
                return false
            }
            else -> return false
        }

        val desc = event.contentDescription?.toString()?.lowercase()
        return when (desc) {
            BUTTON_DELETE_DESC -> {
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
                    pos = 0
                } else if (pos > 0) {
                    pos--
                }
                false
            }
            BUTTON_OK_DESC, BUTTON_ENTER_DESC -> {
                val ok = pos >= targetLength
                pos = 0
                ok
            }
            null -> {
                pos = 0
                false
            }
            else -> {
                pos++
                // Si la longueur cible est atteinte dès ce keypress, match
                // immédiat (certains lockscreens soumettent automatiquement
                // quand la longueur attendue est atteinte, et on ne recevra
                // jamais l'événement OK).
                pos >= targetLength
            }
        }
    }

    override fun reset() {
        pos = 0
    }

    private companion object {
        private const val BUTTON_DELETE_DESC = "delete"
        private const val BUTTON_OK_DESC = "ok"
        private const val BUTTON_ENTER_DESC = "enter"
        private const val WRONG_TEXT = "wrong"
        private const val INCORRECT_TEXT = "incorrect"
    }
}
