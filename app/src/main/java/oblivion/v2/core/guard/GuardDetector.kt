package oblivion.v2.core.guard

import android.view.accessibility.AccessibilityEvent

/**
 * Contrat commun aux 4 détecteurs du Garde-clefs.
 *
 * Un détecteur reçoit les [AccessibilityEvent] émis par le lockscreen
 * (`com.android.systemui`) et décide s'il faut déclencher un wipe.
 *
 * Chaque détecteur est responsable de son état interne (compteur, buffer).
 * Les états sont remis à zéro via [reset], appelé au screen-off ou au
 * déverrouillage par l'AccessibilityService.
 */
interface GuardDetector {

    /** Nom lisible (logs / diagnostic). */
    val name: String

    /**
     * Traite [event].  Renvoie `true` UNIQUEMENT si le détecteur vient de
     * matcher (wipe à déclencher).  Les détecteurs qui ont juste mis à jour
     * leur état interne renvoient `false`.
     */
    fun onEvent(event: AccessibilityEvent): Boolean

    /**
     * Remet l'état interne à zéro (screen-off, changement de fenêtre,
     * déverrouillage réussi, etc.).
     */
    fun reset()
}
