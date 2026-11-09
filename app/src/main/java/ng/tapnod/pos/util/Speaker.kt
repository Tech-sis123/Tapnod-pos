package ng.tapnod.pos.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speaks results out loud on the POS and plays short confirmation tones. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

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
        val nigeria = Locale("en", "NG")
        val result = tts.setLanguage(nigeria)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.UK)
        }
        tts.setSpeechRate(0.95f)
        ready = true
        queued?.let { speak(it) }
        queued = null
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
