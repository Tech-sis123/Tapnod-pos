package ng.tapnod.pos.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Speaks results out loud on the POS and plays short confirmation tones.
 * Picks a Nigerian English voice when the phone has one installed, and the best-quality voice otherwise.
 */
class Speaker(
    context: Context,
    private val preferredVoice: String,
    private val onReady: () -> Unit
) : TextToSpeech.OnInitListener {

    data class VoiceOption(val name: String, val label: String, val nigerian: Boolean)

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var queued: String? = null

    private val tones: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ready = true
        val options = options()
        val chosen = options.firstOrNull { it.name == preferredVoice } ?: options.firstOrNull()
        if (chosen == null || !useVoice(chosen.name)) {
            val r = tts.setLanguage(Locale("en", "NG"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.UK)
            }
        }
        // A touch slower and lower than default sounds less like a robot announcing a train.
        tts.setSpeechRate(0.9f)
        tts.setPitch(0.96f)
        onReady()
        queued?.let { speak(it) }
        queued = null
    }

    /** English voices installed on this phone, Nigerian first, then by quality. */
    fun options(): List<VoiceOption> {
        if (!ready) return emptyList()
        val all: Set<Voice> = runCatching { tts.voices }.getOrNull() ?: emptySet()
        return all
            .filter { v ->
                v.locale.language == "en" &&
                    !(v.features ?: emptySet()).contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
            .sortedWith(
                compareByDescending<Voice> { it.locale.country == "NG" }
                    .thenByDescending { it.quality }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenBy { it.name }
            )
            .map { VoiceOption(it.name, label(it), it.locale.country == "NG") }
    }

    fun current(): VoiceOption? {
        if (!ready) return null
        val v = runCatching { tts.voice }.getOrNull() ?: return null
        return VoiceOption(v.name, label(v), v.locale.country == "NG")
    }

    fun useVoice(name: String): Boolean {
        val v = runCatching { tts.voices }.getOrNull()?.firstOrNull { it.name == name } ?: return false
        return tts.setVoice(v) == TextToSpeech.SUCCESS
    }

    private fun label(v: Voice): String {
        val accent = if (v.locale.country == "NG") "Nigerian English" else v.locale.getDisplayName(Locale.UK)
        val quality = when {
            v.quality >= Voice.QUALITY_VERY_HIGH -> "very high quality"
            v.quality >= Voice.QUALITY_HIGH -> "high quality"
            else -> "normal quality"
        }
        val net = if (v.isNetworkConnectionRequired) "needs internet" else "works offline"
        return "$accent · $quality · $net"
    }

    fun speak(text: String) {
        if (!ready) {
            queued = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tapnod-" + System.nanoTime())
    }

    fun success() {
        tones?.startTone(ToneGenerator.TONE_PROP_ACK, 220)
        vibrate(longArrayOf(0, 60, 80, 60))
    }

    fun failure() {
        tones?.startTone(ToneGenerator.TONE_PROP_NACK, 300)
        vibrate(longArrayOf(0, 250))
    }

    fun tick() {
        vibrate(longArrayOf(0, 30))
    }

    private fun vibrate(pattern: LongArray) {
        val v = vibrator ?: return
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    fun shutdown() {
        runCatching { tts.stop(); tts.shutdown() }
        runCatching { tones?.release() }
    }
}
