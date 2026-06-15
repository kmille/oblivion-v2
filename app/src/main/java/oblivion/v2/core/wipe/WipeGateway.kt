package oblivion.v2.core.wipe

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import oblivion.v2.core.log.SecLog
import oblivion.v2.core.admin.DeviceAdminManager
import oblivion.v2.core.admin.DeviceAdminReceiver

/**
 * Façade unique pour déclencher le wipe depuis n'importe quel point de
 * l'application (bouton manuel, AccessibilityService, USB, SMS, Voice).
 *
 * La seule implémentation du wipe délègue à [DeviceAdminManager.wipeData]
 * qui contient les appels `DevicePolicyManager.wipeData(flags)` avec
 * `WIPE_SILENTLY` sur Android 10+ — **copiés à l'identique** de l'app
 * originale qui fonctionne.
 */
class WipeGateway(private val context: Context) {

    private val appCtx = context.applicationContext
    private val admin = DeviceAdminManager(appCtx)

    /** `true` si l'application a les droits d'admin device (prérequis du wipe). */
    fun isAdminActive(): Boolean = admin.isActive()

    /**
     * Renvoie l'Intent à lancer pour que l'utilisateur active l'admin device.
     * L'appelant doit le lancer via `startActivity(...)`.
     */
    fun requestAdminIntent() = admin.makeRequestIntent()

    /**
     * Retire l'admin device (utile pour désinstaller proprement).
     */
    fun removeAdmin() {
        admin.remove()
    }

    /**
     * Renvoie le ComponentName complet utilisé par l'admin (diagnostic).
     */
    fun adminComponentName(): ComponentName =
        ComponentName(appCtx, DeviceAdminReceiver::class.java)

    // ─── Politique native "wipe après N tentatives échouées" ───────────
    // On délègue au système Android via DPM.setMaximumFailedPasswordsForWipe :
    // bien plus fiable que d'essayer de compter les tentatives nous-mêmes
    // via AccessibilityService (annonces FR/EN/OEM très variables).

    /**
     * Configure le seuil système.  [count] = 0 désactive la politique.
     * Valide sans lever d'exception si l'admin n'est pas actif (no-op).
     */
    fun setMaxFailedAttemptsForWipe(count: Int) {
        val dpm = appCtx.getSystemService(DevicePolicyManager::class.java) ?: return
        val component = adminComponentName()
        if (!dpm.isAdminActive(component)) {
            SecLog.d(TAG, "setMaxFailedAttemptsForWipe: admin not active → no-op")
            return
        }
        val safe = count.coerceAtLeast(0)
        try {
            dpm.setMaximumFailedPasswordsForWipe(component, safe)
            SecLog.d(TAG, "setMaximumFailedPasswordsForWipe($safe) OK")
        } catch (t: Throwable) {
            SecLog.e(TAG, "setMaximumFailedPasswordsForWipe threw", t)
        }
    }

    /**
     * Lit la valeur actuellement configurée côté système (0 si désactivé).
     */
    fun getMaxFailedAttemptsForWipe(): Int {
        val dpm = appCtx.getSystemService(DevicePolicyManager::class.java) ?: return 0
        val component = adminComponentName()
        if (!dpm.isAdminActive(component)) return 0
        return runCatching { dpm.getMaximumFailedPasswordsForWipe(component) }.getOrDefault(0)
    }

    /**
     * Lit le compteur système "tentatives échouées depuis le dernier
     * déverrouillage réussi".  0 si l'admin est inactif.
     */
    fun getCurrentFailedAttempts(): Int {
        val dpm = appCtx.getSystemService(DevicePolicyManager::class.java) ?: return 0
        val component = adminComponentName()
        if (!dpm.isAdminActive(component)) return 0
        return runCatching { dpm.currentFailedPasswordAttempts }.getOrDefault(0)
    }

    /**
     * Exécute le wipe.  ⚠️ IRRÉVERSIBLE.
     *
     * @return un [WipeResult] décrivant ce qui s'est passé.  En cas de
     *         succès l'appareil est normalement effacé avant même que la
     *         valeur de retour puisse être utilisée.
     */
    fun wipeNow(): WipeResult {
        val dpm = appCtx.getSystemService(DevicePolicyManager::class.java)
        if (dpm == null) {
            SecLog.e(TAG, "DevicePolicyManager service is null")
            return WipeResult.NoDpm
        }

        val component = adminComponentName()
        val active = dpm.isAdminActive(component)
        SecLog.d(TAG, "wipeNow() component=$component active=$active")

        if (!active) {
            return WipeResult.NotAdmin(component.flattenToShortString())
        }

        return try {
            SecLog.d(TAG, "Calling admin.wipeData() NOW…")
            admin.wipeData()
            // Si on arrive ici, soit le wipe est en cours (le process va mourir),
            // soit l'appel a été ignoré silencieusement par le système.
            SecLog.d(TAG, "admin.wipeData() returned without throwing")
            WipeResult.Called
        } catch (t: Throwable) {
            SecLog.e(TAG, "admin.wipeData() threw: ${t.javaClass.simpleName}: ${t.message}", t)
            WipeResult.Error(t.javaClass.simpleName, t.message ?: "(no message)")
        }
    }

    sealed class WipeResult {
        /** DPM introuvable — ne devrait jamais arriver sur un appareil normal. */
        object NoDpm : WipeResult()
        /** Appel au DPM émis sans exception. */
        object Called : WipeResult()
        /** Admin n'est pas actif pour ce ComponentName. */
        data class NotAdmin(val expected: String) : WipeResult()
        /** Exception levée par wipeData(). */
        data class Error(val type: String, val message: String) : WipeResult()
    }

    private companion object {
        private const val TAG = "WipeGateway"
    }
}
