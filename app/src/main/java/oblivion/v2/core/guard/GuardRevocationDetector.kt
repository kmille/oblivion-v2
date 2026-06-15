package oblivion.v2.core.guard

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import oblivion.v2.core.log.SecLog
import oblivion.v2.core.wipe.WipeGateway
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Détecte la révocation du [GuardAccessibilityService] et déclenche
 * un wipe en réponse.
 *
 * **Scénario protégé** : un attaquant (ou l'utilisateur sous contrainte)
 * désactive manuellement le service d'accessibilité d'Oblivion via
 * *Paramètres → Accessibilité → Oblivion → Off* pour neutraliser le
 * Garde-clefs. Sans cette détection, les détecteurs de PIN de détresse
 * sur le lockscreen deviendraient inopérants sans aucune conséquence.
 *
 * **Logique** :
 *  - Quand le service est connecté avec `masterEnabled = true`, on
 *    persiste un flag `armed = true` via [GuardConfigStore.markArmed].
 *  - Quand [checkAndWipeIfRevoked] est appelé (typiquement depuis
 *    `MainActivity.onResume`) :
 *      1. si `armed == true` ET
 *      2. si `masterEnabled == true` (l'utilisateur n'a pas désactivé
 *         le garde dans l'app) ET
 *      3. si le service d'accessibilité Oblivion n'est PAS activé dans
 *         les paramètres système,
 *    → on considère que le service a été révoqué hors de l'app → **wipe**.
 *
 * **Désarmement légitime** : l'utilisateur désactive `masterEnabled`
 * dans l'écran Garde-clefs → [GuardConfigStore.markDisarmed] est appelé →
 * plus aucune détection de révocation tant que `masterEnabled` reste off.
 */
@Singleton
class GuardRevocationDetector @Inject constructor(
    private val store: GuardConfigStore,
    private val wipeGateway: WipeGateway,
) {

    /**
     * Vérifie si le service d'accessibilité a été révoqué hors de l'app.
     * Déclenche un wipe immédiat si c'est le cas.
     *
     * @return `true` si une révocation a été détectée et un wipe lancé.
     */
    fun checkAndWipeIfRevoked(context: Context): Boolean {
        val cfg = store.config.value

        // 1. L'utilisateur a-t-il désarmé explicitement ?
        if (!cfg.masterEnabled) {
            // Si masterEnabled est off, on marque aussi le flag armed à false
            // pour éviter les déclenchements si l'utilisateur réactive.
            store.markDisarmed()
            return false
        }

        // 2. Le garde a-t-il déjà été vu armé au moins une fois ?
        if (!store.isArmed()) {
            // Jamais armé → on ne peut pas parler de révocation.
            // (cas d'une première activation où l'utilisateur n'a pas encore
            // accepté l'autorisation d'accessibilité).
            return false
        }

        // 3. Le service d'accessibilité est-il actuellement activé ?
        if (isAccessibilityServiceEnabled(context)) {
            return false
        }

        // Révocation détectée → wipe.
        SecLog.e(
            TAG,
            "Guard accessibility service REVOKED while masterEnabled=true → emergency wipe",
        )
        val result = wipeGateway.wipeNow()
        SecLog.e(TAG, "wipeNow() result=$result")
        return true
    }

    /**
     * `true` si `GuardAccessibilityService` est actuellement activé dans
     * les paramètres d'accessibilité du système.
     *
     * Utilise deux méthodes complémentaires (Settings.Secure + AccessibilityManager)
     * pour être robuste aux variantes OEM.
     */
    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull().orEmpty()

        val targetComponent = "${context.packageName}/" +
            GuardAccessibilityService::class.java.name
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equals(targetComponent, ignoreCase = true)) {
                return true
            }
        }

        // Fallback : AccessibilityManager.
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? AccessibilityManager ?: return false
        if (!am.isEnabled) return false
        return am.getEnabledAccessibilityServiceList(0).orEmpty().any {
            it.resolveInfo?.serviceInfo?.packageName == context.packageName
        }
    }

    private companion object {
        private const val TAG = "GuardRevocation"
    }
}
