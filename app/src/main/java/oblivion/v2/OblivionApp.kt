package oblivion.v2

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import oblivion.v2.core.deadman.DeadmanConfigStore
import oblivion.v2.core.deadman.DeadmanScheduler
import oblivion.v2.core.log.SecLog
import oblivion.v2.ui.decoy.DecoyNotifier

/**
 * Point d'entrée de l'application.
 *
 * L'annotation [HiltAndroidApp] génère le composant Hilt racine et
 * active l'injection de dépendances dans toute l'application.
 *
 * C'est la classe déclarée via `android:name=".OblivionApp"` dans le
 * manifeste.
 *
 * À la création on s'assure que les NotificationChannels nécessaires
 * existent (obligatoire sur Android 8+).
 */
@HiltAndroidApp
class OblivionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        reScheduleDeadmanIfEnabled()
    }

    /**
     * Au démarrage du process, si le Dead Man's Switch est activé on
     * s'assure que la tâche périodique WorkManager est bien en place.
     * WorkManager persiste la tâche côté système, donc ExistingPolicy.KEEP
     * fait que cet appel est un no-op si elle est déjà planifiée.
     */
    private fun reScheduleDeadmanIfEnabled() {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                this,
                AppEntryPoint::class.java,
            )
            val cfg = entryPoint.deadmanConfigStore().load()
            if (cfg.enabled) {
                DeadmanScheduler.schedule(this)
            }
        } catch (t: Throwable) {
            SecLog.e("OblivionApp", "reScheduleDeadmanIfEnabled threw", t)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun deadmanConfigStore(): DeadmanConfigStore
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return

        // Nettoie les anciens channels (Android ne permet pas d'augmenter
        // l'importance d'un channel existant ; on doit en créer un nouveau
        // et supprimer l'ancien).
        runCatching { nm.deleteNotificationChannel("oblivion.usb_kill") }
        runCatching { nm.deleteNotificationChannel("oblivion.usb_kill.v2") }

        val usbChannel = NotificationChannel(
            CHANNEL_USB_KILL,
            getString(R.string.usb_channel_name),
            // DEFAULT : la notif est visible dans la barre de statut +
            // tiroir principal (pas dans la section "silencieux").  On
            // désactive explicitement son et vibration pour garder la
            // discrétion.  Indispensable pour voir le compte à rebours.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.usb_channel_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        nm.createNotificationChannel(usbChannel)

        val voiceChannel = NotificationChannel(
            CHANNEL_VOICE_KILL,
            getString(R.string.voice_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.voice_channel_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        nm.createNotificationChannel(voiceChannel)

        // Mode Decoy (Option A) — channel pour la notif FullScreenIntent
        // qui déclenche la fausse "Mise à jour système" par-dessus le
        // lockscreen (cf DecoyNotifier.kt).
        runCatching { DecoyNotifier.ensureChannel(this) }
    }

    companion object {
        // v3 : bump d'importance (DEFAULT au lieu de LOW) pour que la notif
        // soit visible dans la barre de statut.  Incrémenter ce suffixe à
        // chaque changement d'importance du channel.
        const val CHANNEL_USB_KILL = "oblivion.usb_kill.v3"
        const val CHANNEL_VOICE_KILL = "oblivion.voice_kill.v1"
    }
}
