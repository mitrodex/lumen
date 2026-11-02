package de.tb.lumen

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService

/**
 * Leerer „Sprachassistent“. Zweck: Android nimmt den aktiven Assistenten von der Sperre aus,
 * die nach einem Home-Druck ~5 s lang Activity-Starts aus dem Hintergrund zurückhält.
 * Ist Lumen der Assistent, kann der HomeWatcherService Lumen sofort öffnen.
 *
 * Aktivieren (macht auch die Selbstheilung):
 *   adb shell settings put secure voice_interaction_service de.tb.lumen/.LumenVoiceService
 */
class LumenVoiceService : VoiceInteractionService()

class LumenVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = VoiceInteractionSession(this)
}

/** Pflichtteil eines Assistenten; erkennt nichts. */
class LumenRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(android.speech.SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) = Unit
    override fun onStopListening(listener: Callback?) = Unit
}
