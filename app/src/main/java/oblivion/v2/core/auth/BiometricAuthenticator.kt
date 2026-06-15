package oblivion.v2.core.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import oblivion.v2.core.log.SecLog

/**
 * Façade autour de [BiometricPrompt] pour déclencher l'authentification
 * depuis n'importe où (MainActivity, composables via LocalContext, etc.).
 *
 * On accepte **empreinte**, **face**, ET **PIN/pattern/password système**
 * (via `DEVICE_CREDENTIAL`) — ce dernier est crucial pour que l'app reste
 * utilisable si la biométrie n'est pas configurée.
 *
 * Note sécurité : on utilise les allowedAuthenticators `BIOMETRIC_WEAK` +
 * `DEVICE_CREDENTIAL` car c'est la seule combo qui fonctionne sur API 29+
 * sans passer par des clés cryptographiques. Pour Oblivion c'est largement
 * suffisant : l'authentification sert uniquement à protéger l'UI de l'app,
 * pas à dériver des clés.
 */
object BiometricAuthenticator {

    private const val TAG = "BiometricAuth"

    /**
     * Résultat sommaire de l'appel [canAuthenticate].
     */
    enum class Capability {
        /** L'appareil peut authentifier (biométrie ou credential système). */
        AVAILABLE,

        /** Aucune biométrie ni PIN système configuré — l'utilisateur doit en créer un. */
        NONE_ENROLLED,

        /** Matériel absent ou temporairement indisponible — l'app ne peut pas verrouiller. */
        UNAVAILABLE,
    }

    /**
     * Vérifie la capacité d'authentification sans lancer le prompt.
     */
    fun canAuthenticate(context: Context): Capability {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        return when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> Capability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Capability.NONE_ENROLLED
            else -> Capability.UNAVAILABLE
        }
    }

    /**
     * Lance le prompt. Appelle [onSuccess] si l'utilisateur s'authentifie,
     * [onFailure] en cas d'erreur ou annulation.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        onSuccess: () -> Unit,
        onFailure: (errorCode: Int, errorMessage: String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    SecLog.d(TAG, "Authentication succeeded")
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    SecLog.w(TAG, "Authentication error code=$errorCode")
                    onFailure(errorCode, errString.toString())
                }

                override fun onAuthenticationFailed() {
                    // Appelé quand la biométrie est reconnue comme invalide
                    // (ex : mauvaise empreinte). Pas d'erreur terminale —
                    // l'utilisateur peut ré-essayer. Pas besoin d'agir ici.
                    SecLog.d(TAG, "Authentication failed (retryable)")
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()

        prompt.authenticate(info)
    }
}
