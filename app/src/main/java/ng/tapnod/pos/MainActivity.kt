package ng.tapnod.pos

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ng.tapnod.pos.nfc.CardReader
import ng.tapnod.pos.ui.TapnodApp
import ng.tapnod.pos.ui.TapnodTheme

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private val vm: PosViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    private var resumed = false
    private var wantReader = false
    private var readerOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A POS screen should never go to sleep mid-sale.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        vm.setNfcStatus(nfcStatus())

        setContent {
            TapnodTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                val needsReader = state.screen is Screen.TapCard
                LaunchedEffect(needsReader) {
                    wantReader = needsReader
                    updateReader()
                }
                TapnodApp(
                    state = state,
                    vm = vm,
                    onOpenNfcSettings = { openNfcSettings() },
                    onOpenTtsSettings = { openTtsSettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        vm.setNfcStatus(nfcStatus())
        updateReader()
    }

    override fun onPause() {
        resumed = false
        disableReader()
        super.onPause()
    }

    private fun nfcStatus(): NfcStatus {
        val adapter = nfcAdapter ?: return NfcStatus.MISSING
        return if (adapter.isEnabled) NfcStatus.READY else NfcStatus.OFF
    }

    private fun updateReader() {
        val adapter = nfcAdapter
        if (adapter == null || !adapter.isEnabled || !resumed || !wantReader) {
            disableReader()
            return
        }
        if (readerOn) return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        runCatching {
            adapter.enableReaderMode(this, this, flags, extras)
            readerOn = true
        }
    }

    private fun disableReader() {
        if (!readerOn) return
        runCatching { nfcAdapter?.disableReaderMode(this) }
        readerOn = false
    }

    /** Runs on an NFC binder thread, not the main thread. */
    override fun onTagDiscovered(tag: Tag) {
        val mode = vm.beginCardRead() ?: return
        when (val result = CardReader.read(tag)) {
            is CardReader.Result.Success -> vm.onCardNumber(result.pan, mode)
            is CardReader.Result.Failure ->
                // The card WAS detected, we just couldn't read its number.
                // With "Accept any tap" on, treat it as the demo card so the demo never stalls.
                if (vm.state.value.settings.acceptAnyTap) vm.onAnyTap(mode)
                else vm.onCardError(result.message, mode)
        }
        runOnUiThread { updateReader() }
    }

    /** Android's text-to-speech settings, where the Nigerian English voice can be installed. */
    private fun openTtsSettings() {
        runCatching { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            .onFailure {
                runCatching {
                    startActivity(Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                }
            }
    }

    private fun openNfcSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) } }
    }
}
