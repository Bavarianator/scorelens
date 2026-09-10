package com.freedarts.scorer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.ToneGenerator
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Caller: sagt Scores an (Text-to-Speech, offline über die Android-Sprachausgabe)
 * und spielt einfache Soundeffekte.
 */
class Caller(context: Context) {
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val res = engine.setLanguage(Locale.GERMAN)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) engine.language = Locale.ENGLISH
                engine.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                ready = true
            }
        }
    }
    private val tone: ToneGenerator? = try { ToneGenerator(AudioManager.STREAM_MUSIC, 70) } catch (e: Exception) { null }

    var enabled = true
    var soundEffects = true

    fun say(text: String) {
        if (!enabled || !ready) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "call-${System.nanoTime()}")
    }

    fun callScore(score: Int) {
        when (score) {
            180 -> say("Einhundertachtzig!")
            0 -> say("Keine Punkte")
            else -> say(score.toString())
        }
    }

    fun callRemaining(name: String, remaining: Int) = say("$name, du brauchst $remaining")
    fun callBust() = say("Bust")
    fun callGameShot(winner: String) = say("Game Shot! $winner gewinnt")
    fun callLeg(winner: String) = say("Leg für $winner")
    fun callPlayer(name: String) = say("$name, du bist dran")

    fun beep() { if (soundEffects) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 80) }
    fun ding() { if (soundEffects) tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
    fun error() { if (soundEffects) tone?.startTone(ToneGenerator.TONE_PROP_NACK, 200) }

    fun shutdown() { tts?.stop(); tts?.shutdown(); tone?.release() }
}
