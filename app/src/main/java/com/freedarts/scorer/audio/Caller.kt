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
                engine.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                ready = true
                applyVoice()
            }
        }
    }
    private val tone: ToneGenerator? = try { ToneGenerator(AudioManager.STREAM_MUSIC, 70) } catch (e: Exception) { null }
    /** Laufender 180-Clip; solange er spielt, warten TTS-Ansagen in [pending] (kein SoundPool: der kappt Samples über ~1 MB PCM). */
    private var clip: MediaPlayer? = null
    private val pending = ArrayDeque<Pair<String, Boolean>>()

    var enabled = true
    var soundEffects = true
    /** Aufnahmen unter diesem Wert werden nicht angesagt (0 = alle, auch „Keine Punkte“). */
    var minScore = 0
    /** Englisch im PDC-Stil statt Deutsch. */
    var english = false; private set
    private var voiceName = ""

    fun setVoice(english: Boolean, voiceName: String) {
        if (english == this.english && voiceName == this.voiceName) return
        this.english = english; this.voiceName = voiceName
        if (ready) applyVoice()
    }

    private fun applyVoice() {
        val engine = tts ?: return
        val res = engine.setLanguage(if (english) Locale.UK else Locale.GERMAN)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) engine.language = Locale.ENGLISH
        voices().firstOrNull { it.name == voiceName }?.let { engine.voice = it }
    }

    /** Offline-Stimmen der gewählten Sprache (leer, solange TTS nicht bereit ist). */
    fun voices(): List<android.speech.tts.Voice> = if (!ready) emptyList() else
        (tts?.voices ?: emptySet()).filter { it.locale.language == (if (english) "en" else "de") && !it.isNetworkConnectionRequired }.sortedBy { it.name }

    /** Hörprobe der aktuellen Stimme. */
    fun sample() = say(t("Einhundertachtzig!", "One hundred and eighty!"), flush = true)

    private fun t(de: String, en: String) = if (english) en else de

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
        if (score < minScore) return
        val max = t("Einhundertachtzig!", "One hundred and eighty!")
        val none = t("Keine Punkte", "No score")
        when (score) {
            180 -> if (soundEffects) playClip(R.raw.one_eighty, max) else say(max)
            0 -> if (soundEffects) playClip(zeroClips.random(), none) else say(none)
            else -> say(score.toString())
        }
    }

    fun callRemaining(name: String, remaining: Int) = say(t("$name, du brauchst $remaining", "$name, you require $remaining"))
    fun callBust() = say(t("Bust", "No score"))
    fun callGameShot(winner: String) = say(t("Game Shot! $winner gewinnt", "Game shot, and the match! $winner"), flush = true)
    fun callLeg(winner: String) = say(t("Leg für $winner", "Game shot! Leg to $winner"))
    fun callPlayer(name: String) = say(t("$name, du bist dran", "$name, you're up"))

    fun beep() { if (soundEffects) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 80) }
    fun ding() { if (soundEffects) tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
    fun error() { if (soundEffects) tone?.startTone(ToneGenerator.TONE_PROP_NACK, 200) }

    fun shutdown() { stop(); tts?.shutdown(); tone?.release() }
}
