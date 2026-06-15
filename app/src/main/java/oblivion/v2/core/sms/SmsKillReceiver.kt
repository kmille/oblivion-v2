package oblivion.v2.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import oblivion.v2.core.log.SecLog
import dagger.hilt.android.AndroidEntryPoint
import oblivion.v2.core.wipe.WipeGateway
import javax.inject.Inject

/**
 * BroadcastReceiver pour les SMS entrants — Étape 4 SMS Wipe.
 *
 * Déclaré dans le manifest avec `android.provider.Telephony.SMS_RECEIVED`.
 * Quand un SMS arrive :
 *  1. Vérifie que le trigger est activé et configuré.
 *  2. Vérifie que l'expéditeur correspond au numéro autorisé.
 *  3. Vérifie que le corps du SMS contient le mot-clé secret.
 *  4. Si tout matche → wipe immédiat.
 *
 * La comparaison du mot-clé est insensible à la casse.
 * La comparaison du numéro ignore les espaces et tirets.
 */
@AndroidEntryPoint
class SmsKillReceiver : BroadcastReceiver() {

    @Inject lateinit var configStore: SmsKillConfigStore
    @Inject lateinit var wipeGateway: WipeGateway

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val cfg = configStore.load()
        if (!cfg.isReady()) {
            SecLog.d(TAG, "SMS received but trigger not ready → ignore")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        for (sms in messages) {
            val sender = normalizeSender(sms.displayOriginatingAddress ?: continue)
            val body = sms.displayMessageBody ?: continue

            SecLog.d(TAG, "SMS from=$sender body_len=${body.length}")

            if (!senderMatches(sender, cfg.senderNumber)) {
                SecLog.d(TAG, "sender mismatch → skip")
                continue
            }

            if (keywordMatches(body, cfg.keyword)) {
                SecLog.d(TAG, "KEYWORD MATCH from $sender → WIPE")
                val result = wipeGateway.wipeNow()
                SecLog.d(TAG, "wipeNow() result=$result")
                return // Un seul wipe suffit.
            } else {
                SecLog.d(TAG, "keyword not found in body → skip")
            }
        }
    }

    companion object {
        private const val TAG = "SmsKillReceiver"

        /**
         * Normalise un numéro : supprime espaces, tirets, parenthèses.
         * "+33 6 12 34 56 78" → "+33612345678"
         */
        fun normalizeSender(number: String): String =
            number.replace(Regex("[\\s\\-().]"), "")

        /**
         * Compare deux numéros normalisés. Gère le cas où l'un est
         * au format international (+33...) et l'autre en local (06...).
         */
        fun senderMatches(received: String, configured: String): Boolean {
            val r = normalizeSender(received)
            val c = normalizeSender(configured)
            if (r == c) return true
            // Matching par suffixe : si les 9 derniers chiffres sont identiques
            // ça matche (gère +33612... vs 0612...).
            if (r.length >= 9 && c.length >= 9) {
                return r.takeLast(9) == c.takeLast(9)
            }
            return false
        }

        /**
         * Vérifie si le corps du SMS contient le mot-clé (insensible à la casse).
         */
        fun keywordMatches(body: String, keyword: String): Boolean =
            body.lowercase().contains(keyword.lowercase())
    }
}
