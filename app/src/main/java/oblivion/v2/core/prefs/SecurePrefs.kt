package oblivion.v2.core.prefs

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import oblivion.v2.core.admin.DeviceAdminManager
import oblivion.v2.core.log.SecLog

/**
 * Single wrapper around EncryptedSharedPreferences.
 *
 * - AES256 encryption for keys and values (Android Keystore).
 * - **Corruption response**: if the encrypted prefs file can't be
 *   decrypted, that's either real tampering by an attacker, or the
 *   device is still in Direct Boot (before the first unlock since
 *   reboot) — a normal, transient system state that must NOT be treated
 *   as tampering. See [isLikelyDirectBootUnavailable].
 *
 * Used by all specialized stores (GuardConfigStore, etc.).
 */
class SecurePrefs private constructor(val prefs: SharedPreferences) {

    companion object {
        private const val TAG = "SecurePrefs"
        private const val FILE_NAME = "oblivion_v2_secure_prefs"
        private const val FILE_NAME_DEVICE_PROTECTED = "oblivion_v2_guard_dp_prefs"

        /**
         * Standard store, in credential-encrypted storage. Used for
         * everything that doesn't need to run before the first unlock
         * (Decoy, SMS-Kill, Voice-Kill, Deadman, etc.).
         */
        fun create(context: Context): SecurePrefs = create(context, deviceProtected = false)

        /**
         * Store in **device-protected storage**, readable from boot,
         * *before* the first unlock (Direct Boot). Reserved for data
         * that must be checkable at that stage — typically
         * [oblivion.v2.core.guard.GuardConfigStore], so the duress PIN
         * works from the very first lockscreen after a reboot, on
         * File-Based-Encryption (FBE) devices without forced "Secure
         * Startup". See README § Known limitations for devices where
         * this remains impossible (FDE, Secure Startup enabled).
         */
        fun createDeviceProtected(context: Context): SecurePrefs =
            create(context, deviceProtected = true)

        private fun create(context: Context, deviceProtected: Boolean): SecurePrefs {
            val baseCtx = context.applicationContext
            val appCtx = if (deviceProtected) {
                // Separate context that reads/writes in device-protected
                // storage, accessible before the first unlock.
                baseCtx.createDeviceProtectedStorageContext()
            } else {
                baseCtx
            }
            val fileName = if (deviceProtected) FILE_NAME_DEVICE_PROTECTED else FILE_NAME

            if (deviceProtected) {
                // IMPORTANT: no EncryptedSharedPreferences/Tink/Keystore
                // here. Observed in practice: the MasterKey →
                // AndroidKeysetManager chain somewhere makes a call that
                // falls back onto the credential-encrypted Context
                // (likely a `context.applicationContext` internal to
                // Tink that doesn't preserve the device-protected
                // wrapper). Result: it crashed with the exact same
                // IllegalStateException("credential encrypted
                // storage...") we were trying to avoid, including in the
                // retry after deleting the file → uncaught crash.
                //
                // GuardConfigStore only ever stores a hash+salt anyway
                // (never the PIN in clear): the benefit of Keystore
                // encryption here is marginal, while reliability during
                // Direct Boot is critical. So we use plain, unencrypted
                // SharedPreferences on the device-protected context —
                // pure file I/O, no dependency on Keystore/Tink at all.
                return SecurePrefs(appCtx.getSharedPreferences(fileName, Context.MODE_PRIVATE))
            }

            val masterKey = MasterKey.Builder(appCtx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = try {
                buildEncrypted(appCtx, fileName, masterKey)
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
                        t,
                    )
                } else {
                    handleCorruption(appCtx, t)
                }
                // Retry itself guarded: if it fails again (e.g. still in
                // Direct Boot, for the exact same reason), we must NOT
                // crash the app — that would kill the whole process. Fall
                // back to an empty, non-persistent in-memory store
                // instead: degraded, but never a crash.
                try {
                    appCtx.deleteSharedPreferences(fileName)
                    buildEncrypted(appCtx, fileName, masterKey)
                } catch (t2: Throwable) {
                    SecLog.e(TAG, "Retry also failed, falling back to in-memory store", t2)
                    InMemoryPrefs()
                }
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
            return try {
                val um = context.getSystemService(android.os.UserManager::class.java)
                um?.isUserUnlocked == false
            } catch (t: Throwable) {
                SecLog.w(TAG, "isUserUnlocked check failed, assuming NOT direct-boot", t)
                false
            }
        }

        /**
         * Called when EncryptedSharedPreferences can't read the file
         * (decryption exception) and it's NOT a Direct Boot situation.
         *
         * Scenarios:
         *  - **Attacker**: modified `oblivion_v2_secure_prefs.xml` to
         *    break the encryption → wipe.
         *  - **Keystore key rotation**: rare but possible after certain
         *    OS resets → unjustified wipe, but admin is typically not
         *    active in that case (revoked along with the reset).
         *  - **First boot**: the file doesn't exist → no exception
         *    normally (EncryptedSharedPreferences creates the file).
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
                // At this point the system should be wiping already. We
                // should never get back here, but continue the flow
                // defensively so the app doesn't crash.
            } catch (t: Throwable) {
                SecLog.e(TAG, "Emergency wipe failed, falling back to reset", t)
            }
        }

        private fun buildEncrypted(
            context: Context,
            fileName: String,
            masterKey: MasterKey,
        ): SharedPreferences = EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Last-resort safety net: an in-memory implementation of
     * [SharedPreferences], used only if even the retry after deleting
     * the corrupted file fails. Nothing is persisted — lost on process
     * restart — but the app never crashes over a plain storage
     * availability issue.
     */
    private class InMemoryPrefs : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?) =
            map[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
        override fun contains(key: String) = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false
            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) =
                apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { pending[key] = REMOVE_MARKER }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                apply(); return true
            }
            override fun apply() {
                if (clearAll) map.clear()
                pending.forEach { (k, v) -> if (v === REMOVE_MARKER) map.remove(k) else map[k] = v }
                pending.clear()
            }
        }

        private companion object {
            val REMOVE_MARKER = Any()
        }
    }
}
