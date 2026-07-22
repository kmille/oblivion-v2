package oblivion.v2.core.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import oblivion.v2.core.admin.DeviceAdminManager
import oblivion.v2.core.log.SecLog

/**
 * Enveloppe unique autour d'EncryptedSharedPreferences.
 *
 * - Chiffrement AES256 pour les clés et valeurs (Keystore Android).
 * - **Réponse à la corruption** : si le fichier de prefs chiffrées est
 *   illisible (signe probable de tampering par un attaquant), on
 *   déclenche un **wipe immédiat** si l'admin device est actif. Si
 *   l'admin n'est pas actif (cas d'une première install ou rotation de
 *   clé Keystore légitime), on loggue et on repart avec un store vierge
 *   pour ne pas bloquer le boot de l'app.
 *
 * Utilisé par tous les stores spécialisés (GuardConfigStore, etc.).
 */
class SecurePrefs private constructor(val prefs: SharedPreferences) {

    companion object {
        private const val TAG = "SecurePrefs"
        private const val FILE_NAME = "oblivion_v2_secure_prefs"

        fun create(context: Context): SecurePrefs {
            val appCtx = context.applicationContext
            val masterKey = MasterKey.Builder(appCtx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = try {
                buildEncrypted(appCtx, masterKey)
            } catch (t: Throwable) {
                // The file can be unreadable for TWO very different
                // reasons:
                //  1) real tampering (file corrupted/modified);
                //  2) we're still in Direct Boot (before the first
                //     unlock): credential-encrypted storage (where
                //     MasterKey/EncryptedSharedPreferences live) is
                //     structurally inaccessible — THIS IS NOT TAMPERING,
                //     just a normal, transient system state.
                // We must NEVER wipe for reason #2.
                if (isLikelyDirectBootUnavailable(appCtx)) {
                    SecLog.w(
                        TAG,
                        "Prefs unreadable but device not yet unlocked " +
                            "(Direct Boot) → treating as storage-not-ready, NOT tampering",
                    )
                } else {
                    handleCorruption(appCtx, t)
                }
                // If the wipe failed (or admin isn't active, or we
                // deliberately skipped wiping because of Direct Boot),
                // start over with a blank store so the app stays
                // bootable. In that case the config is lost — that's the
                // price to pay to avoid a boot loop.
                appCtx.deleteSharedPreferences(FILE_NAME)
                buildEncrypted(appCtx, masterKey)
            }
            return SecurePrefs(prefs)
        }

        /**
         * `true` if the device hasn't been unlocked yet since the last
         * boot (i.e. we're in Direct Boot, credential-encrypted storage
         * is inaccessible by design — not a sign of tampering). Relies
         * on `UserManager.isUserUnlocked()`, the official system API for
         * this exact distinction.
         *
         * On error (old SDK, service unavailable), we conservatively
         * assume `false` (= "treat as possible tampering") so we don't
         * weaken detection on devices where this situation doesn't
         * actually occur.
         */
        private fun isLikelyDirectBootUnavailable(context: Context): Boolean {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return false
            return try {
                val um = context.getSystemService(android.os.UserManager::class.java)
                um?.isUserUnlocked == false
            } catch (t: Throwable) {
                SecLog.w(TAG, "isUserUnlocked check failed, assuming NOT direct-boot", t)
                false
            }
        }

        /**
         * Appelé quand EncryptedSharedPreferences ne peut pas lire le
         * fichier (exception au décryptage).
         *
         * Scénarios :
         *  - **Attaquant** : a modifié le fichier `oblivion_v2_secure_prefs.xml`
         *    pour casser le chiffrement → on veut wiper.
         *  - **Rotation de clé Keystore** : rare mais possible après certains
         *    reset de l'OS → wipe injustifié, mais l'admin n'est
         *    typiquement pas actif dans ce cas (il a été révoqué avec le
         *    reset).
         *  - **Premier démarrage** : le fichier n'existe pas → pas d'exception
         *    normalement (EncryptedSharedPreferences crée le fichier).
         */
        private fun handleCorruption(appCtx: Context, cause: Throwable) {
            SecLog.e(TAG, "EncryptedSharedPreferences unreadable (tampering suspected)", cause)
            val admin = DeviceAdminManager(appCtx)
            if (!admin.isActive()) {
                SecLog.w(TAG, "Admin not active → cannot wipe, will reset prefs")
                return
            }
            SecLog.e(TAG, "Admin active → triggering emergency wipe due to prefs corruption")
            try {
                admin.wipeData()
                // À ce stade le système est normalement en train de wiper.
                // On ne devrait jamais revenir ici mais par sécurité on
                // continue le flow pour que l'app ne crashe pas.
            } catch (t: Throwable) {
                SecLog.e(TAG, "Emergency wipe failed, falling back to reset", t)
            }
        }

        private fun buildEncrypted(
            context: Context,
            masterKey: MasterKey,
        ): SharedPreferences = EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
