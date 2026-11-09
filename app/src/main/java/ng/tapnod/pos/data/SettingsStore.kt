package ng.tapnod.pos.data

import android.content.Context

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("tapnod_pos", Context.MODE_PRIVATE)

    fun load(): PosSettings = PosSettings(
        simulator = prefs.getBoolean("simulator", true),
        simOutcome = runCatching {
            SimOutcome.valueOf(prefs.getString("sim_outcome", SimOutcome.APPROVE.name)!!)
        }.getOrDefault(SimOutcome.APPROVE),
        backendUrl = prefs.getString("backend_url", "") ?: "",
        terminalId = prefs.getString("terminal_id", "TPN-POS-001") ?: "TPN-POS-001",
        merchantName = prefs.getString("merchant_name", "Bisi Ventures") ?: "Bisi Ventures",
        merchantId = prefs.getString("merchant_id", "BISI-001") ?: "BISI-001",
        tokenSecret = prefs.getString("token_secret", "tapnod-demo-secret") ?: "tapnod-demo-secret",
        voice = prefs.getBoolean("voice", true)
    )

    var voiceName: String
        get() = prefs.getString("voice_name", "") ?: ""
        set(value) {
            prefs.edit().putString("voice_name", value).apply()
        }

    fun save(s: PosSettings) {
        prefs.edit()
            .putBoolean("simulator", s.simulator)
            .putString("sim_outcome", s.simOutcome.name)
            .putString("backend_url", s.backendUrl.trim().trimEnd('/'))
            .putString("terminal_id", s.terminalId.trim())
            .putString("merchant_name", s.merchantName.trim())
            .putString("merchant_id", s.merchantId.trim())
            .putString("token_secret", s.tokenSecret)
            .putBoolean("voice", s.voice)
            .apply()
    }
}
