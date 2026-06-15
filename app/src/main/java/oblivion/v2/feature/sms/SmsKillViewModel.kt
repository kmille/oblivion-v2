package oblivion.v2.feature.sms

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import oblivion.v2.core.sms.SmsKillConfig
import oblivion.v2.core.sms.SmsKillConfigStore
import oblivion.v2.core.sms.SmsKillReceiver
import oblivion.v2.core.wipe.WipeGateway
import javax.inject.Inject

@HiltViewModel
class SmsKillViewModel @Inject constructor(
    private val configStore: SmsKillConfigStore,
    private val wipeGateway: WipeGateway,
) : ViewModel() {

    val config: StateFlow<SmsKillConfig> = configStore.config

    fun setSenderNumber(value: String) = configStore.setSenderNumber(value)

    fun setKeyword(value: String) = configStore.setKeyword(value)

    fun setEnabled(value: Boolean) {
        val cfg = config.value
        if (value && cfg.keyword.length < SmsKillConfig.MIN_KEYWORD_LENGTH) return
        if (value && cfg.senderNumber.isBlank()) return
        configStore.setEnabled(value)
    }

    /**
     * Simule la réception d'un SMS pour tester le trigger sans carte SIM.
     * Appelle le même code de matching que [SmsKillReceiver].
     *
     * @return true si le keyword a matché (= wipe aurait été déclenché).
     */
    fun simulateSms(sender: String, body: String): Boolean {
        val cfg = configStore.load()
        if (!cfg.isReady()) return false

        val normalizedSender = SmsKillReceiver.normalizeSender(sender)
        if (!SmsKillReceiver.senderMatches(normalizedSender, cfg.senderNumber)) return false
        if (!SmsKillReceiver.keywordMatches(body, cfg.keyword)) return false

        // Match ! En vrai ça wiperait. En mode test on retourne juste true.
        return true
    }
}
