package oblivion.v2.ui.decoy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import oblivion.v2.R
import oblivion.v2.core.log.SecLog

/**
 * Déclencheur de l'écran leurre "Mise à jour système" via une notification
 * **FullScreenIntent**.
 *
 * ── Pourquoi ce mécanisme ?
 * Sur Android 10+ (API 29+), il est impossible pour une app tierce d'afficher
 * une UI par-dessus l'écran de verrouillage depuis un service en arrière-plan :
 *   - `startActivity()` depuis un service est bloqué (Background Activity
 *     Launch restriction) quand l'écran est verrouillé ;
 *   - Un overlay `TYPE_APPLICATION_OVERLAY` peut être ajouté mais reste
 *     caché sous le window keyguard (protection anti-phishing) ;
 *   - Les types de windows privilégiés (SYSTEM_ALERT, SYSTEM_ERROR…) sont
 *     réservés aux apps système (signature).
 *
 * La **seule** méthode accessible à une app user pour afficher une UI
 * plein écran par-dessus le lockscreen est la notification avec
 * `setFullScreenIntent()`. Android dismisse alors lui-même le keyguard et
 * lance l'activity en plein écran. C'est exactement ce qu'utilisent les
 * apps d'appel entrant (WhatsApp, Signal, Messenger, le Phone system…).
 *
 * ── Permissions
 * - Android 13+ (API 33) : `POST_NOTIFICATIONS` (déjà présente).
 * - Android 14+ (API 34) : `USE_FULL_SCREEN_INTENT` doit être **accordée
 *   manuellement par l'utilisateur** via *Paramètres → Apps → Oblivion →
 *   Notifications → Notifications plein écran*. L'app `canUseFullScreenIntent()`
 *   permet de vérifier.
 * - Pré-Android 14 : la permission est auto-grantée à l'install.
 *
 * ── Fallback
 * Si la permission FSI n'est pas accordée, Android affichera la notification
 * comme une heads-up notification normale — ce n'est plus du plein écran
 * mais c'est toujours visible.
 */
object DecoyNotifier {

    private const val TAG = "DecoyNotifier"

    const val CHANNEL_ID = "oblivion_decoy_update"
    const val NOTIFICATION_ID = 0x0B1D0 // "0blido" ✨

    /**
     * Crée (ou re-crée idempotemment) le canal de notification utilisé pour
     * l'écran leurre. À appeler une fois au boot de l'app, p.ex. depuis
     * `OblivionApp.onCreate()`.
     *
     * Importance : HIGH — requis pour que la notification puisse prendre la
     * main en plein écran via fullScreenIntent.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // Channel silencieux (pas de son/vibration) — on ne veut pas
        // d'alerte audio qui trahirait la supercherie à l'attaquant.
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.decoy_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.decoy_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Vérifie si l'app a le droit de poster des notifications FullScreenIntent.
     * Sur Android 14+ cette permission doit être explicitement accordée par
     * l'utilisateur ; sur les versions antérieures elle est implicite.
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.canUseFullScreenIntent()
    }

    /**
     * Retourne l'Intent pour ouvrir la page paramètres "Notifications plein
     * écran" du système, où l'utilisateur peut activer la permission pour
     * notre app (Android 14+ uniquement).
     */
    fun fullScreenIntentSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Poste la notification leurre. Si la permission FSI est accordée,
     * Android lance immédiatement [DecoyActivity] plein écran par-dessus
     * le lockscreen. Sinon la notification s'affiche en heads-up normale.
     */
    fun trigger(context: Context) {
        val appCtx = context.applicationContext
        val nm = appCtx.getSystemService(NotificationManager::class.java)
        if (nm == null) {
            SecLog.e(TAG, "NotificationManager unavailable")
            return
        }
        ensureChannel(appCtx)

        // Intent plein écran → DecoyActivity. Le PendingIntent doit être
        // mutable=false + FLAG_UPDATE_CURRENT pour conformité API 31+.
        val activityIntent = Intent(appCtx, DecoyActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            appCtx,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif: Notification = Notification.Builder(appCtx, CHANNEL_ID)
            .setContentTitle(appCtx.getString(R.string.decoy_update_title))
            .setContentText(appCtx.getString(R.string.decoy_update_subtitle))
            // Icône système neutre — on ne fait pas de dépendance asset custom.
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(Notification.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, /*highPriority=*/true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        try {
            nm.notify(NOTIFICATION_ID, notif)
            SecLog.d(TAG, "Decoy fullScreenIntent posted (FSI permitted=${canUseFullScreenIntent(appCtx)})")
        } catch (t: Throwable) {
            SecLog.e(TAG, "notify() threw", t)
        }
    }

    /** Retire la notification (utile si on veut tester sans wipe). */
    fun cancel(context: Context) {
        val nm = context.applicationContext
            .getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(NOTIFICATION_ID)
    }
}
