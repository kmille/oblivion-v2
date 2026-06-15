package oblivion.v2.core.guard.detector

import oblivion.v2.core.log.SecLog
import android.view.accessibility.AccessibilityEvent
import oblivion.v2.core.crypto.PinHasher
import oblivion.v2.core.guard.GuardDetector

/**
 * EMERGENCY : second mot de passe de détresse, indépendant du Type A.
 *
 * Même moteur que [TypeADetector] (chemin CLICK primaire + chemin TEXT
 * de secours) : le wipe se déclenche **sans appuyer sur valider**.
 *
 * Différence : on reset explicitement au `TYPE_WINDOW_STATE_CHANGED`
 * (changement de fenêtre = nouvelle tentative), comportement hérité de
 * l'ancienne app pour EMERGENCY.
 */
class EmergencyDetector(
    private val expectedHash: String,
    private val salt: String,
) : GuardDetector {

    override val name: String = "Emergency"

    private val clickBuffer = StringBuilder()
    private val textBuffer = StringBuilder()
    private var textPos = 0

    override fun onEvent(event: AccessibilityEvent): Boolean {
        return when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                reset()
                false
            }
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
            SecLog.d(TAG, "Emergency (click): hash match → wipe")
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
            SecLog.d(TAG, "Emergency (text): hash match → wipe")
            return true
        }
        return false
    }

    override fun reset() {
        clickBuffer.clear()
        textBuffer.clear()
        textPos = 0
    }

    private companion object {
        private const val TAG = "EmergencyDetector"
        private const val DOT_CHAR = '•'
        private const val MIN_CHECK = 4
        private const val MAX_BUFFER = 64
        private const val BUTTON_DELETE_DESC = "delete"
        private const val BUTTON_OK_DESC = "ok"
        private const val BUTTON_ENTER_DESC = "enter"
    }
}
