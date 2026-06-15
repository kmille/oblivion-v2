package oblivion.v2.core.guard.detector

import android.view.accessibility.AccessibilityEvent
import oblivion.v2.core.crypto.PinHasher
import oblivion.v2.core.guard.GuardDetector
import oblivion.v2.core.log.SecLog

/**
 * Decoy (Mode leurre, Option A) : détecteur par PIN leurre.
 *
 * Même moteur que [TypeADetector] (chemin CLICK primaire + chemin TEXT
 * de secours) : le wipe se déclenche dès que le dernier chiffre du PIN
 * leurre est tapé sur le lockscreen.
 *
 * ⚠️ Particularité : quand ce détecteur matche, le [GuardAccessibilityService]
 * doit en plus **lancer la fausse page "Mise à jour système"** pour masquer
 * le déclenchement. Le service distingue Decoy des autres détecteurs via le
 * champ [name].
 */
class DecoyDetector(
    private val expectedHash: String,
    private val salt: String,
) : GuardDetector {

    override val name: String = NAME

    private val clickBuffer = StringBuilder()
    private val textBuffer = StringBuilder()
    private var textPos = 0

    override fun onEvent(event: AccessibilityEvent): Boolean {
        return when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> handleClick(event)
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> handleText(event)
            else -> false
        }
    }

    private fun handleClick(event: AccessibilityEvent): Boolean {
        val desc = event.contentDescription?.toString()?.lowercase()?.trim().orEmpty()
        if (desc.isEmpty()) return false

        when {
            desc == BUTTON_DELETE_DESC -> {
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
                    clickBuffer.clear()
                } else if (clickBuffer.isNotEmpty()) {
                    clickBuffer.deleteCharAt(clickBuffer.length - 1)
                }
                return false
            }
            desc == BUTTON_OK_DESC || desc == BUTTON_ENTER_DESC -> {
                return tryVerifyClick()
            }
            else -> {
                val ch = desc.firstOrNull() ?: return false
                if (!ch.isDigit()) return false
                if (clickBuffer.length >= MAX_BUFFER) clickBuffer.deleteCharAt(0)
                clickBuffer.append(ch)
                return tryVerifyClick()
            }
        }
    }

    private fun tryVerifyClick(): Boolean {
        if (clickBuffer.length < MIN_CHECK) return false
        return if (PinHasher.verify(clickBuffer.toString(), expectedHash, salt)) {
            SecLog.d(TAG, "Decoy (click): hash match → wipe + fake update")
            true
        } else false
    }

    private fun handleText(event: AccessibilityEvent): Boolean {
        val text = event.text?.firstOrNull()?.toString()
        if (text.isNullOrEmpty()) {
            textBuffer.clear()
            textPos = 0
            return false
        }

        if (textPos > text.length) {
            if (textPos > 0) {
                textPos--
                if (textBuffer.length > textPos) textBuffer.setLength(textPos)
            }
            return false
        }

        val c = text.elementAtOrNull(textPos) ?: return false
        if (c == DOT_CHAR) return false
        if (!c.isDigit()) return false

        if (textBuffer.length < MAX_BUFFER) textBuffer.append(c)
        textPos++

        if (textBuffer.length >= MIN_CHECK &&
            PinHasher.verify(textBuffer.toString(), expectedHash, salt)
        ) {
            SecLog.d(TAG, "Decoy (text): hash match → wipe + fake update")
            return true
        }
        return false
    }

    override fun reset() {
        clickBuffer.clear()
        textBuffer.clear()
        textPos = 0
    }

    companion object {
        /** Nom exposé — utilisé par le service pour distinguer Decoy des autres. */
        const val NAME: String = "Decoy"

        private const val TAG = "DecoyDetector"
        private const val DOT_CHAR = '•'
        private const val MIN_CHECK = 4
        private const val MAX_BUFFER = 64
        private const val BUTTON_DELETE_DESC = "delete"
        private const val BUTTON_OK_DESC = "ok"
        private const val BUTTON_ENTER_DESC = "enter"
    }
}
