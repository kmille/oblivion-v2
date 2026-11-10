package oblivion.v2.core.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import oblivion.v2.core.log.SecLog
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import oblivion.v2.core.deadman.DeadmanConfigStore
import oblivion.v2.core.guard.detector.EmergencyDetector
import oblivion.v2.core.guard.detector.TypeADetector
import oblivion.v2.core.guard.detector.TypeBDetector
import oblivion.v2.core.wipe.WipeGateway
import javax.inject.Inject

/**
 * AccessibilityService du Garde-clefs.
 *
 * Rôle :
 *  - filtrer les événements émis par `com.android.systemui` pendant que
 *    l'écran est verrouillé
 *  - maintenir à jour la liste des [GuardDetector] actifs en fonction
 *    de la [GuardConfig] (observée via StateFlow)
 *  - dispatcher chaque événement aux détecteurs actifs
 *  - déclencher un [WipeGateway.wipeNow] dès qu'un détecteur matche
 *  - reset des détecteurs à l'écran-off et remise à zéro du compteur
 *    d'échecs au déverrouillage réussi (ACTION_USER_PRESENT)
 */
@AndroidEntryPoint
class GuardAccessibilityService : AccessibilityService() {

    @Inject lateinit var store: GuardConfigStore
    @Inject lateinit var wipeGateway: WipeGateway
    @Inject lateinit var revocationDetector: GuardRevocationDetector
    @Inject lateinit var deadmanConfigStore: DeadmanConfigStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var configJob: Job? = null

    private var keyguardManager: KeyguardManager? = null

    /** Liste mutable (rebuild à chaque changement de config). */
    private var detectors: List<GuardDetector> = emptyList()
    private var masterEnabled = false

    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    SecLog.d(TAG, "SCREEN_OFF → reset detectors")
                    detectors.forEach(GuardDetector::reset)
                }
                Intent.ACTION_USER_PRESENT -> {
                    SecLog.d(TAG, "USER_PRESENT → reset detectors (failed-count géré par DPM)")
                    detectors.forEach(GuardDetector::reset)
                    // Le compteur de tentatives échouées est géré nativement
                    // par Android (setMaximumFailedPasswordsForWipe) — il se
                    // remet à zéro tout seul à chaque déverrouillage réussi.

                    // Inactivity Wipe : tout déverrouillage réel de l'appareil
                    // compte comme preuve de présence, pas seulement
                    // l'ouverture d'Oblivion elle-même — sinon utiliser le
                    // téléphone normalement toute la journée sans jamais
                    // ouvrir l'app ferait quand même courir le compte à
                    // rebours vers un wipe.
                    try {
                        deadmanConfigStore.touchCheckIn()
                    } catch (t: Throwable) {
                        SecLog.e(TAG, "touchCheckIn from USER_PRESENT threw", t)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SecLog.d(TAG, "Service onCreate")
        keyguardManager = getSystemService(KeyguardManager::class.java)
        registerReceiver(
            lockReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )

        // Observer les changements de config en direct : si l'utilisateur
        // modifie la config depuis l'UI, le service réagit sans redémarrer.
        configJob = store.config
            .onEach { rebuildDetectors(it) }
            .launchIn(scope)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.also {
            it.eventTypes =
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                    AccessibilityEvent.TYPE_ANNOUNCEMENT or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            it.flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            it.notificationTimeout = 100L
            it.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            it.packageNames = arrayOf(SYSTEMUI_PACKAGE)
        }
        SecLog.d(TAG, "Service connected. Listening to $SYSTEMUI_PACKAGE")
    }

    override fun onDestroy() {
        super.onDestroy()
        SecLog.d(TAG, "Service onDestroy")
        configJob?.cancel()
        scope.cancel()
        runCatching { unregisterReceiver(lockReceiver) }
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!masterEnabled) return
        // Ne scruter que pendant le lockscreen.
        if (keyguardManager?.isDeviceLocked != true) {
            detectors.forEach(GuardDetector::reset)
            return
        }
        // Filtrer par package source.
        val pkg = event.packageName?.toString()
        if (pkg != null && pkg != SYSTEMUI_PACKAGE) return

        for (detector in detectors) {
            val matched = runCatching { detector.onEvent(event) }
                .onFailure { SecLog.e(TAG, "Detector ${detector.name} threw", it) }
                .getOrDefault(false)
            if (matched) {
                SecLog.d(TAG, "DETECTOR MATCH: ${detector.name} → wipe")
                wipeGateway.wipeNow()
                // Reset tous les détecteurs pour éviter qu'un second match
                // reparte — même si le wipe est irréversible, au cas où
                // le système l'ignorerait pour une raison quelconque.
                detectors.forEach(GuardDetector::reset)
                return
            }
        }
    }

    /**
     * Reconstruit la liste des détecteurs actifs en fonction de [config].
     */
    private fun rebuildDetectors(config: GuardConfig) {
        masterEnabled = config.masterEnabled && config.hasAnyDetectorEnabled()
        val next = mutableListOf<GuardDetector>()
        if (config.masterEnabled) {
            if (config.typeAEnabled && config.typeAHash.isNotEmpty()) {
                next += TypeADetector(config.typeAHash, config.typeASalt)
            }
            if (config.typeBEnabled && config.typeBLength >= GuardConfig.MIN_TRAP_LENGTH) {
                next += TypeBDetector(config.typeBLength)
            }
            if (config.emergencyEnabled && config.emergencyHash.isNotEmpty()) {
                next += EmergencyDetector(config.emergencyHash, config.emergencySalt)
            }
        }
        detectors = next

        // "Failed attempts" est géré par le système via DPM, pas par un
        // AccessibilityDetector — on configure le seuil natif.
        val wipeThreshold = if (config.masterEnabled &&
            config.failedAttemptsEnabled &&
            config.failedAttemptsThreshold >= GuardConfig.MIN_FAILED_ATTEMPTS
        ) config.failedAttemptsThreshold else 0
        wipeGateway.setMaxFailedAttemptsForWipe(wipeThreshold)

        // ── Mise à jour du flag "armed" pour la détection de révocation ──
        // Si l'utilisateur active masterEnabled et que le service est
        // connecté (on est ici donc c'est le cas), on persiste le fait
        // que le garde est armé. Toute désactivation future du service
        // hors de l'app sera interprétée comme une révocation → wipe.
        if (config.masterEnabled) {
            store.markArmed()
        } else {
            // L'utilisateur a désactivé le garde depuis l'app → désarmement
            // légitime. On remet le flag à false pour éviter un faux positif.
            store.markDisarmed()
        }

        SecLog.d(
            TAG,
            "Rebuilt detectors: masterEnabled=$masterEnabled, " +
                "active=${next.map { it.name }}, " +
                "dpmFailedThreshold=$wipeThreshold, " +
                "armed=${store.isArmed()}",
        )
    }

    /**
     * Appelé quand le service est "unbound" par le système.
     *
     * Principaux scénarios :
     *  - **Révocation** : l'utilisateur (ou un attaquant) a désactivé le
     *    service d'accessibilité Oblivion via *Paramètres → Accessibilité*.
     *    Dans ce cas, si `masterEnabled` est encore `true` ET qu'on était
     *    armé, on déclenche un wipe immédiat — l'attaquant ne doit pas
     *    pouvoir neutraliser le garde sans conséquence.
     *  - **Mise à jour / reboot de l'app** : onUnbind peut être appelé
     *    lors d'un update. Dans ce cas la détection externe (MainActivity
     *    onResume via GuardRevocationDetector) prend le relais — si on
     *    est vraiment révoqué le wipe partira à la prochaine ouverture.
     *  - **Low memory** : le système peut tuer un accessibility service
     *    en cas de pression mémoire extrême (rare). Ici on se contente de
     *    vérifier l'état effectif via Settings.Secure — si le service est
     *    marqué enabled mais just tué, pas de wipe.
     *
     * On délègue à [GuardRevocationDetector] qui fait la vérification
     * complète (masterEnabled + armed + service effectivement disabled).
     */
    override fun onUnbind(intent: Intent?): Boolean {
        SecLog.w(TAG, "onUnbind called — checking for revocation")
        try {
            revocationDetector.checkAndWipeIfRevoked(applicationContext)
        } catch (t: Throwable) {
            SecLog.e(TAG, "Revocation check failed in onUnbind", t)
        }
        return super.onUnbind(intent)
    }

    private companion object {
        private const val TAG = "GuardAccessibility"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
    }
}
