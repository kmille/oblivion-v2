package oblivion.v2.core.guard

import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import oblivion.v2.core.crypto.PinHasher
import oblivion.v2.core.prefs.SecurePrefs

/**
 * Lecture / écriture de la [GuardConfig] + compteur de tentatives échouées,
 * dans [SecurePrefs] (EncryptedSharedPreferences).
 *
 * Expose aussi un [StateFlow] pour que l'UI et l'AccessibilityService
 * observent les changements en temps réel.
 */
class GuardConfigStore(private val securePrefs: SecurePrefs) {

    private val prefs get() = securePrefs.prefs

    private val _config = MutableStateFlow(loadInternal())
    val config: StateFlow<GuardConfig> = _config.asStateFlow()

    /** Charge la config stockée (synchrone, pour init rapide au démarrage). */
    fun load(): GuardConfig = _config.value

    /** Sauvegarde la config et met à jour le flow. */
    fun save(config: GuardConfig) {
        prefs.edit {
            putBoolean(K_MASTER_ENABLED, config.masterEnabled)

            putBoolean(K_TYPE_A_ENABLED, config.typeAEnabled)
            putString(K_TYPE_A_HASH, config.typeAHash)
            putString(K_TYPE_A_SALT, config.typeASalt)

            putBoolean(K_TYPE_B_ENABLED, config.typeBEnabled)
            putInt(K_TYPE_B_LENGTH, config.typeBLength)

            putBoolean(K_EMERGENCY_ENABLED, config.emergencyEnabled)
            putString(K_EMERGENCY_HASH, config.emergencyHash)
            putString(K_EMERGENCY_SALT, config.emergencySalt)

            putBoolean(K_FAILED_ATTEMPTS_ENABLED, config.failedAttemptsEnabled)
            putInt(K_FAILED_ATTEMPTS_THRESHOLD, config.failedAttemptsThreshold)
        }
        _config.value = config
    }

    /**
     * Configure le PIN / mot de passe de détresse du Type A.
     * Génère un sel frais et stocke le hash.  Passe [pin] = "" pour
     * effacer et désactiver Type A.
     */
    fun setTypeAPin(pin: String) {
        val current = load()
        val next = if (pin.isEmpty()) {
            current.copy(typeAEnabled = false, typeAHash = "", typeASalt = "")
        } else {
            val salt = PinHasher.newSalt()
            current.copy(
                typeAEnabled = true,
                typeAHash = PinHasher.hash(pin, salt),
                typeASalt = salt,
            )
        }
        save(next)
    }

    /** Idem pour EMERGENCY. */
    fun setEmergencyPin(pin: String) {
        val current = load()
        val next = if (pin.isEmpty()) {
            current.copy(emergencyEnabled = false, emergencyHash = "", emergencySalt = "")
        } else {
            val salt = PinHasher.newSalt()
            current.copy(
                emergencyEnabled = true,
                emergencyHash = PinHasher.hash(pin, salt),
                emergencySalt = salt,
            )
        }
        save(next)
    }

    // ── Compteur de tentatives échouées ─────────────────────────────────────

    /** Renvoie la valeur actuelle du compteur (nombre d'échecs cumulés). */
    fun getFailedAttemptsCount(): Int =
        prefs.getInt(K_FAILED_ATTEMPTS_COUNT, 0)

    /** Incrémente le compteur et renvoie la nouvelle valeur. */
    fun incrementFailedAttemptsCount(): Int {
        val next = getFailedAttemptsCount() + 1
        prefs.edit { putInt(K_FAILED_ATTEMPTS_COUNT, next) }
        return next
    }

    /** Remise à zéro (à appeler au déverrouillage réussi). */
    fun resetFailedAttemptsCount() {
        prefs.edit { putInt(K_FAILED_ATTEMPTS_COUNT, 0) }
    }

    // ── Flag "armed" : détection de révocation de l'AccessibilityService ────
    //
    // On persiste ce flag quand on observe que :
    //   masterEnabled == true  &&  GuardAccessibilityService effectivement
    //   connecté au système.
    //
    // Plus tard, si on constate que `masterEnabled == true` mais que le
    // service n'est plus activé côté système, on en déduit une révocation
    // (user ou attaquant qui a désactivé le service dans Paramètres →
    // Accessibilité) et on déclenche un wipe.
    //
    // Ce flag est seulement remis à `false` quand l'utilisateur désactive
    // explicitement `masterEnabled` depuis l'app (désarmement légitime).

    /** `true` si le garde a été vu actif et que sa désactivation doit déclencher un wipe. */
    fun isArmed(): Boolean = prefs.getBoolean(K_GUARD_ARMED, false)

    /** Marque le garde comme armé (appeler quand le service se connecte avec masterEnabled=true). */
    fun markArmed() {
        if (!prefs.getBoolean(K_GUARD_ARMED, false)) {
            prefs.edit { putBoolean(K_GUARD_ARMED, true) }
        }
    }

    /** Désarme (appeler quand l'utilisateur désactive masterEnabled dans l'app). */
    fun markDisarmed() {
        if (prefs.getBoolean(K_GUARD_ARMED, false)) {
            prefs.edit { putBoolean(K_GUARD_ARMED, false) }
        }
    }

    // ── Interne ─────────────────────────────────────────────────────────────

    private fun loadInternal(): GuardConfig = GuardConfig(
        masterEnabled = prefs.getBoolean(K_MASTER_ENABLED, false),
        typeAEnabled = prefs.getBoolean(K_TYPE_A_ENABLED, false),
        typeAHash = prefs.getString(K_TYPE_A_HASH, "").orEmpty(),
        typeASalt = prefs.getString(K_TYPE_A_SALT, "").orEmpty(),
        typeBEnabled = prefs.getBoolean(K_TYPE_B_ENABLED, false),
        typeBLength = prefs.getInt(K_TYPE_B_LENGTH, 0),
        emergencyEnabled = prefs.getBoolean(K_EMERGENCY_ENABLED, false),
        emergencyHash = prefs.getString(K_EMERGENCY_HASH, "").orEmpty(),
        emergencySalt = prefs.getString(K_EMERGENCY_SALT, "").orEmpty(),
        failedAttemptsEnabled = prefs.getBoolean(K_FAILED_ATTEMPTS_ENABLED, false),
        failedAttemptsThreshold = prefs.getInt(K_FAILED_ATTEMPTS_THRESHOLD, 10),
    )

    private companion object {
        private const val K_MASTER_ENABLED = "guard.master_enabled"

        private const val K_TYPE_A_ENABLED = "guard.type_a.enabled"
        private const val K_TYPE_A_HASH = "guard.type_a.hash"
        private const val K_TYPE_A_SALT = "guard.type_a.salt"

        private const val K_TYPE_B_ENABLED = "guard.type_b.enabled"
        private const val K_TYPE_B_LENGTH = "guard.type_b.length"

        private const val K_EMERGENCY_ENABLED = "guard.emergency.enabled"
        private const val K_EMERGENCY_HASH = "guard.emergency.hash"
        private const val K_EMERGENCY_SALT = "guard.emergency.salt"

        private const val K_FAILED_ATTEMPTS_ENABLED = "guard.failed_attempts.enabled"
        private const val K_FAILED_ATTEMPTS_THRESHOLD = "guard.failed_attempts.threshold"
        private const val K_FAILED_ATTEMPTS_COUNT = "guard.failed_attempts.count"
        private const val K_GUARD_ARMED = "guard.armed"
    }
}
