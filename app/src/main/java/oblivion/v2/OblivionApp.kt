package oblivion.v2

import android.app.Application
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
        // Nettoie les anciens channels USB/Voice (features retirées).
        runCatching { nm.deleteNotificationChannel("oblivion.usb_kill") }
        runCatching { nm.deleteNotificationChannel("oblivion.usb_kill.v2") }
        runCatching { nm.deleteNotificationChannel("oblivion.usb_kill.v3") }
        runCatching { nm.deleteNotificationChannel("oblivion.voice_kill.v1") }
    }

    companion object
}
