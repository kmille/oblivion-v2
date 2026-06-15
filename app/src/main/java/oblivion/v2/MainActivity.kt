package oblivion.v2

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import oblivion.v2.core.auth.BiometricAuthState
import oblivion.v2.core.guard.GuardRevocationDetector
import oblivion.v2.ui.auth.BiometricGate
import oblivion.v2.ui.nav.AppNavHost
import oblivion.v2.ui.theme.OblivionTheme
import javax.inject.Inject

/**
 * Activity unique de l'application (architecture single-activity).
 *
 * Héberge un NavHost Compose qui route entre les écrans :
 *  - Dashboard (accueil)
 *  - Guard / Garde-clefs (lockscreen triggers)
 *  - USB Kill (charger/USB detection)
 *  - Voice Wipe (phrase-clé Vosk offline)
 *  - Wipe Test (diagnostics)
 *
 * Sécurité :
 *  - FLAG_SECURE : empêche screenshots et enregistrements d'écran (un
 *    attaquant ne peut pas capturer les keywords SMS, phrases vocales,
 *    ou autres données sensibles affichées à l'écran).
 *  - [GuardRevocationDetector] : à chaque `onResume`, on vérifie que le
 *    GuardAccessibilityService n'a pas été désactivé hors de l'app. Si
 *    c'est le cas et que le garde était armé → wipe immédiat.
 *  - [BiometricGate] : tout l'écran est protégé par une authentification
 *    biométrique (empreinte / face / PIN système) avec timeout
 *    d'inactivité de 1 minute.
 *
 * Extension de [FragmentActivity] (au lieu de ComponentActivity) : requise
 * par `androidx.biometric.BiometricPrompt` qui utilise un Fragment interne.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var revocationDetector: GuardRevocationDetector
    @Inject lateinit var authState: BiometricAuthState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bloque screenshots et screen recording sur toutes les fenêtres.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        // On invalide toujours l'auth au cold start : le process vient
        // d'être créé, la dernière auth (s'il y en avait une) n'est plus
        // valide. L'utilisateur doit refaire le prompt biométrique.
        authState.invalidate()

        enableEdgeToEdge()
        setContent {
            OblivionTheme {
                BiometricGate(authState = authState) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Vérifie le timeout d'inactivité : si plus d'une minute s'est
        // écoulée depuis la dernière activité, on invalide l'auth pour
        // forcer un nouveau prompt biométrique.
        authState.checkStillValid()

        // Détection de révocation du service d'accessibilité : si
        // `masterEnabled == true` ET que le garde était armé ET que le
        // service d'accessibilité n'est plus actif → wipe.  Si wipe
        // est effectif, l'appareil est en train d'être effacé avant
        // même qu'on ne revienne de cet appel.
        runCatching {
            revocationDetector.checkAndWipeIfRevoked(this)
        }
    }
}
