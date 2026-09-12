package com.freedarts.scorer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.ToneGenerator
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import com.freedarts.scorer.R
import java.util.Locale

/**
 * Caller: sagt Scores an (Text-to-Speech, offline über die Android-Sprachausgabe)
 * und spielt einfache Soundeffekte.
 */
class Caller(context: Context) {
    private val ctx = context.applicationContext
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
    /** Laufender 180-Clip; solange er spielt, warten TTS-Ansagen in [pending] (kein SoundPool: der kappt Samples über ~1 MB PCM). */
    private var clip: MediaPlayer? = null
    private val pending = ArrayDeque<Pair<String, Boolean>>()

    var enabled = true
    var soundEffects = true

    /** [flush] verwirft noch nicht gesprochene Ansagen (z. B. Game Shot statt Score-Rückstau). */
    fun say(text: String, flush: Boolean = false) {
        if (!enabled || !ready) return
        if (clip != null) { if (flush) pending.clear(); pending += text to flush; return }
        tts?.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "call-${System.nanoTime()}")
    }

    /** Laufende und wartende Ansagen abbrechen (Match beendet oder verlassen). */
    fun stop() { tts?.stop(); pending.clear(); clip?.release(); clip = null }

    /**
     * Clip abspielen; TTS-Ansagen warten in [pending], bis er fertig ist (keine Überschneidung). Quellen myinstants.com,
     * keine freie Lizenz, nur privat: one_eighty = 180-russ-bray.mp3; zero_1..6 = Drachenlord (was-zitterstn-so, so-idiet,
     * halt-dein-mauuul, das-ist-geil, warum-liegd-da-ne-wurst, etzella).
     */
    private fun playClip(res: Int, fallback: String) {
        clip?.release()
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        clip = MediaPlayer.create(ctx, res, attrs, 0)?.apply {
            setOnCompletionListener {
                it.release(); clip = null
                while (pending.isNotEmpty()) pending.removeFirst().let { (t, f) -> say(t, f) }
            }
            start()
        } ?: run { say(fallback); null }
    }

    private val zeroClips = listOf(R.raw.zero_1, R.raw.zero_2, R.raw.zero_3, R.raw.zero_4, R.raw.zero_5, R.raw.zero_6)

    fun callScore(score: Int) {
        when (score) {
            180 -> if (soundEffects) playClip(R.raw.one_eighty, "Einhundertachtzig!") else say("Einhundertachtzig!")
            0 -> if (soundEffects) playClip(zeroClips.random(), "Keine Punkte") else say("Keine Punkte")
            else -> say(score.toString())
        }
    }

    fun callRemaining(name: String, remaining: Int) = say("$name, du brauchst $remaining")
    fun callBust() = say("Bust")
    fun callGameShot(winner: String) = say("Game Shot! $winner gewinnt", flush = true)
    fun callLeg(winner: String) = say("Leg für $winner")
    fun callPlayer(name: String) = say("$name, du bist dran")

    fun beep() { if (soundEffects) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 80) }
    fun ding() { if (soundEffects) tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
    fun error() { if (soundEffects) tone?.startTone(ToneGenerator.TONE_PROP_NACK, 200) }

    fun shutdown() { stop(); tts?.shutdown(); tone?.release() }
}
