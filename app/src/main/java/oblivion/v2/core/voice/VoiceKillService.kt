package oblivion.v2.core.voice

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import oblivion.v2.core.log.SecLog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oblivion.v2.MainActivity
import oblivion.v2.OblivionApp
import oblivion.v2.R
import oblivion.v2.core.wipe.WipeGateway
import javax.inject.Inject

/**
 * Service Voice Wipe — Étape 5.
 *
 * Règles :
 *  1. écoute active UNIQUEMENT lorsque l'écran est verrouillé (choix user)
 *  2. écran déverrouillé → micro coupé (batterie + vie privée)
 *  3. phrase-clé prononcée avec seuil strict → wipe immédiat
 *
 * Le service tourne en foreground avec notification persistante —
 * obligatoire pour ouvrir le micro en arrière-plan sur Android 14.
 * Le type de foreground service est [FOREGROUND_SERVICE_TYPE_MICROPHONE]
 * comme requis depuis Android 10.
 */
@AndroidEntryPoint
class VoiceKillService : Service() {

    @Inject lateinit var configStore: VoiceKillConfigStore
    @Inject lateinit var wipeGateway: WipeGateway

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recognizer by lazy { VoiceRecognizer(applicationContext) }
    private var unpackJob: Job? = null
    private var modelPath: String? = null
    /** Langue du modèle actuellement extrait (pour détecter un changement). */
    private var unpackedLanguage: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenLocked()
                Intent.ACTION_USER_PRESENT -> onUserPresent()
                Intent.ACTION_SCREEN_ON -> {
                    // Pas utilisé : on ne veut agir qu'au verrouillage réel.
                    // Android envoie SCREEN_ON même sur un écran qui reste
                    // verrouillé — on se base plutôt sur SCREEN_OFF +
                    // isDeviceLocked() pour décider.
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SecLog.d(TAG, "onCreate()")
        startForegroundWithType(buildNotification(State.Idle))

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // Déclenche unpack en arrière-plan dès le démarrage du service.
        unpackModelAsync(configStore.load().language)

        // Stoppe le service si l'utilisateur désactive le trigger.
        // Si la langue change pendant que le service tourne, on re-unpack.
        configStore.config
            .onEach { cfg ->
                if (!cfg.enabled) {
                    SecLog.d(TAG, "config.enabled=false → stopSelf()")
                    stopSelf()
                    return@onEach
                }
                if (cfg.language != unpackedLanguage) {
                    SecLog.d(TAG, "language changed → re-unpack '${cfg.language}'")
                    stopRecognition()
                    modelPath = null
                    unpackModelAsync(cfg.language)
                }
            }
            .launchIn(scope)

        // Si le téléphone est déjà verrouillé au démarrage du service
        // (activation depuis l'UI puis écran éteint), on démarre direct.
        if (isDeviceLocked()) {
            SecLog.d(TAG, "device already locked on service create")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SecLog.d(TAG, "onStartCommand()")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        SecLog.d(TAG, "onDestroy()")
        runCatching { unregisterReceiver(screenReceiver) }
        stopRecognition()
        scope.cancel()
        super.onDestroy()
    }

    // ── Events ──────────────────────────────────────────────────────────────

    private fun onScreenLocked() {
        SecLog.d(TAG, "screen off → will start listening if ready")
        // Attendre un court instant : sur certains OEM SCREEN_OFF précède
        // le verrouillage effectif.
        scope.launch {
            kotlinx.coroutines.delay(SCREEN_OFF_DELAY_MS)
            if (isDeviceLocked()) {
                startRecognition()
            } else {
                SecLog.d(TAG, "screen off but not locked → skip")
            }
        }
    }

    private fun onUserPresent() {
        SecLog.d(TAG, "user present → stop listening")
        stopRecognition()
    }

    // ── Recognizer lifecycle ────────────────────────────────────────────────

    private fun unpackModelAsync(language: String) {
        unpackJob?.cancel()
        unpackJob = scope.launch {
            updateNotification(State.UnpackingModel)
            try {
                val path = withContext(Dispatchers.IO) {
                    recognizer.unpackModel(language)
                }
                modelPath = path
                unpackedLanguage = language
                SecLog.d(TAG, "model[$language] ready at $path")
                updateNotification(State.Idle)
                // Si l'écran est déjà verrouillé au moment où le modèle
                // devient prêt, démarrer l'écoute tout de suite.
                if (isDeviceLocked()) startRecognition()
            } catch (t: Throwable) {
                SecLog.e(TAG, "unpack failed for language='$language': ${t.message}", t)
                updateNotification(State.ModelMissing)
            }
        }
    }

    private fun startRecognition() {
        if (recognizer.isRunning()) return
        val path = modelPath
        if (path == null) {
            SecLog.d(TAG, "startRecognition() but model not unpacked yet")
            return
        }
        val cfg = configStore.load()
        if (!cfg.isReady()) {
            SecLog.d(TAG, "config not ready (enabled=${cfg.enabled} phrase='${cfg.phrase}')")
            return
        }
        if (!hasRecordAudioPermission()) {
            SecLog.d(TAG, "RECORD_AUDIO permission not granted")
            updateNotification(State.PermMissing)
            return
        }
        try {
            recognizer.start(
                modelPath = path,
                phrase = cfg.phrase,
                strict = cfg.strict,
            ) {
                onPhraseMatched()
            }
            updateNotification(State.Listening)
            SecLog.d(TAG, "recognition started")
        } catch (t: Throwable) {
            SecLog.e(TAG, "recognizer.start() failed", t)
            updateNotification(State.Error)
        }
    }

    private fun stopRecognition() {
        if (!recognizer.isRunning()) {
            updateNotification(State.Idle)
            return
        }
        try {
            recognizer.stop()
        } catch (t: Throwable) {
            SecLog.e(TAG, "recognizer.stop() failed", t)
        }
        updateNotification(State.Idle)
    }

    private fun onPhraseMatched() {
        SecLog.d(TAG, "PHRASE MATCHED → wipe")
        updateNotification(State.Wiping)
        val result = wipeGateway.wipeNow()
        SecLog.d(TAG, "wipeNow() result=$result")
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun isDeviceLocked(): Boolean {
        val km = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return km.isDeviceLocked || km.isKeyguardLocked
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ── Notification ────────────────────────────────────────────────────────

    private enum class State { UnpackingModel, Idle, Listening, Wiping, ModelMissing, PermMissing, Error }

    private fun buildNotification(state: State): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when (state) {
            State.UnpackingModel -> getString(R.string.voice_notif_unpacking)
            State.Idle -> getString(R.string.voice_notif_idle)
            State.Listening -> getString(R.string.voice_notif_listening)
            State.Wiping -> getString(R.string.voice_notif_wiping)
            State.ModelMissing -> getString(R.string.voice_notif_model_missing)
            State.PermMissing -> getString(R.string.voice_notif_perm_missing)
            State.Error -> getString(R.string.voice_notif_error)
        }
        return NotificationCompat.Builder(this, OblivionApp.CHANNEL_VOICE_KILL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.voice_notif_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    private fun updateNotification(state: State) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    companion object {
        private const val TAG = "VoiceKillService"
        private const val NOTIFICATION_ID = 4202
        /** Délai avant vérification du verrouillage après SCREEN_OFF. */
        private const val SCREEN_OFF_DELAY_MS = 500L

        fun start(context: Context) {
            val intent = Intent(context, VoiceKillService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceKillService::class.java))
        }
    }
}
