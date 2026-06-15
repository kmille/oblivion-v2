package oblivion.v2.feature.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import oblivion.v2.core.voice.VoiceKillConfig
import oblivion.v2.core.voice.VoiceKillConfigStore
import oblivion.v2.core.voice.VoiceKillService
import oblivion.v2.core.wipe.WipeGateway
import javax.inject.Inject

/**
 * ViewModel de l'écran Voice Wipe.
 *
 * Pilote la [VoiceKillConfig] et démarre / arrête le [VoiceKillService]
 * foreground (avec type MICROPHONE) selon l'état du toggle.
 */
@HiltViewModel
class VoiceKillViewModel @Inject constructor(
    app: Application,
    private val store: VoiceKillConfigStore,
    private val wipeGateway: WipeGateway,
) : AndroidViewModel(app) {

    val config: StateFlow<VoiceKillConfig> = store.config

    fun isAdminActive(): Boolean = wipeGateway.isAdminActive()

    fun setEnabled(enabled: Boolean) {
        if (enabled && !wipeGateway.isAdminActive()) return
        // On vérifie seulement que la phrase est configurée — pas
        // config.isReady() qui exige enabled=true (cercle vicieux).
        if (enabled && config.value.phrase.isBlank()) return
        store.save(config.value.copy(enabled = enabled))
        val ctx = getApplication<Application>()
        if (enabled) VoiceKillService.start(ctx) else VoiceKillService.stop(ctx)
    }

    fun setPhrase(phrase: String) {
        val trimmed = phrase.trim()
        store.save(config.value.copy(phrase = trimmed))
    }

    fun setStrict(strict: Boolean) {
        store.save(config.value.copy(strict = strict))
    }

    /**
     * Change la langue du modèle Vosk (ex. `"fr"` ou `"en"`).
     * Le service détecte le changement et re-extrait le modèle correspondant.
     */
    fun setLanguage(language: String) {
        if (language == config.value.language) return
        store.save(config.value.copy(language = language))
    }
}
