package oblivion.v2.core.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import oblivion.v2.core.log.SecLog

/**
 * DeviceAdminReceiver d'Oblivion.
 *
 * **Sécurité — protection contre la révocation de l'admin** :
 *
 * Android appelle [onDisableRequested] quand l'utilisateur (ou un attaquant)
 * tente de désactiver l'admin via *Paramètres → Sécurité → Apps admin*.
 * À ce moment précis, l'admin est **encore actif**, donc on a l'opportunité
 * de déclencher un wipe avant que la révocation ne soit confirmée.
 *
 * La désactivation "propre" depuis l'app elle-même passe par
 * [android.app.admin.DevicePolicyManager.removeActiveAdmin] qui **ne
 * déclenche pas** `onDisableRequested` — uniquement `onDisabled` — donc
 * cette voie n'entraîne pas de wipe involontaire.
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    /**
     * Appelé quand l'utilisateur clique "Désactiver" dans les paramètres
     * système *avant* que la désactivation ne soit effective.
     *
     * On wipe immédiatement : un attaquant qui tente de neutraliser
     * Oblivion via les paramètres déclenche la protection.
     *
     * Retourne un message (non affiché dans la pratique car on wipe avant).
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        SecLog.e(TAG, "Admin disable REQUESTED via Settings → triggering emergency wipe")
        try {
            DeviceAdminManager(context).wipeData()
        } catch (t: Throwable) {
            SecLog.e(TAG, "Emergency wipe failed on disable request", t)
        }
        // Si wipe() ne tue pas le process immédiatement, ce warning est affiché.
        return "Désactiver Oblivion supprimera toutes les données de l'appareil."
    }

    /**
     * Appelé après la révocation effective (soit via Settings, soit via
     * `removeActiveAdmin()` depuis l'app). À ce stade on ne peut plus
     * wiper (l'admin n'est plus actif).
     */
    override fun onDisabled(context: Context, intent: Intent) {
        SecLog.w(TAG, "Admin has been disabled")
        super.onDisabled(context, intent)
    }

    companion object {
        private const val TAG = "DeviceAdminReceiver"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, DeviceAdminReceiver::class.java)
        }
    }
}
