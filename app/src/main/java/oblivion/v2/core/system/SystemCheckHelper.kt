package oblivion.v2.core.system

import android.content.Context
import android.provider.Settings
import oblivion.v2.core.log.SecLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vérifie que les réglages système nécessaires au bon fonctionnement
 * du Garde-clefs (service d'accessibilité) sont correctement configurés.
 *
 * Ces réglages ne peuvent PAS être modifiés par l'app (nécessite
 * WRITE_SECURE_SETTINGS) — on se contente de les lire et d'avertir
 * l'utilisateur sur le Dashboard s'ils sont mal configurés.
 */
@Singleton
class SystemCheckHelper @Inject constructor(
    @ApplicationContext private val appCtx: Context,
) {

    /**
     * Vérifie si « Afficher les mots de passe » est activé.
     *
     * Ce réglage permet au service d'accessibilité de voir les caractères
     * tapés brièvement au lieu de `•`. Sans lui, Type A et Emergency ne
     * peuvent pas détecter le PIN de détresse sur le lockscreen.
     *
     * Clé : [Settings.System.TEXT_SHOW_PASSWORD] (1 = activé, 0 = désactivé).
     */
    fun isShowPasswordEnabled(): Boolean {
        return try {
            Settings.System.getInt(
                appCtx.contentResolver,
                Settings.System.TEXT_SHOW_PASSWORD,
                0,
            ) == 1
        } catch (e: Exception) {
            SecLog.d(TAG, "Cannot read TEXT_SHOW_PASSWORD: ${e.message}")
            false // On considère désactivé en cas de doute.
        }
    }

    /**
     * Vérifie si « Confidentialité renforcée du code » est DÉSACTIVÉE.
     *
     * Ce réglage Pixel (enhanced_pin_privacy) masque les animations de
     * saisie du PIN. Quand il est activé (= 1), le service d'accessibilité
     * ne reçoit plus les événements de frappe, ce qui bloque la détection.
     *
     * On veut que ce réglage soit DÉSACTIVÉ (0) pour que tout fonctionne.
     *
     * Clé : `Settings.Secure.enhanced_pin_privacy` (Pixel only, API 34+).
     * Retourne `true` si le réglage est bon (désactivé ou absent).
     */
    fun isEnhancedPinPrivacyDisabled(): Boolean {
        return try {
            val value = Settings.Secure.getInt(
                appCtx.contentResolver,
                ENHANCED_PIN_PRIVACY_KEY,
                0, // Default 0 = désactivé = OK
            )
            value == 0
        } catch (e: Exception) {
            SecLog.d(TAG, "Cannot read enhanced_pin_privacy: ${e.message}")
            true // Si le réglage n'existe pas (non-Pixel), c'est OK.
        }
    }

    companion object {
        private const val TAG = "SystemCheckHelper"
        /**
         * Clé Pixel pour la confidentialité renforcée du code.
         * N'existe pas sur tous les OEM — si absent, on considère OK.
         */
        private const val ENHANCED_PIN_PRIVACY_KEY = "enhanced_pin_privacy"
    }
}
