package oblivion.v2.core.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * ⚠️ FICHIER COPIÉ À L'IDENTIQUE depuis l'app originale qui fonctionne
 * (`me.lucky.oblivion.admin.DeviceAdminManager`).  Seul le package est
 * changé.  Aucune modification de la logique n'est autorisée ici : si le
 * wipe marche dans l'ancienne app, c'est parce que ces 3 lignes
 * `dpm?.wipeData(flags)` avec `WIPE_SILENTLY` sur Android 10+ fonctionnent
 * exactement ainsi.
 */
class DeviceAdminManager(private val ctx: Context) {
    private val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
    private val deviceAdmin by lazy { ComponentName(ctx, DeviceAdminReceiver::class.java) }

    fun remove() = dpm?.removeActiveAdmin(deviceAdmin)
    fun isActive() = dpm?.isAdminActive(deviceAdmin) ?: false

    fun wipeData() {
        var flags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            flags = flags.or(DevicePolicyManager.WIPE_SILENTLY)
        dpm?.wipeData(flags)
    }

    fun makeRequestIntent() =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin)
}
