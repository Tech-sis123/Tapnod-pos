package ng.tapnod.pos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ng.tapnod.pos.data.ApiException
import ng.tapnod.pos.data.CardInfo
import ng.tapnod.pos.data.CreateResult
import ng.tapnod.pos.data.Customer
import ng.tapnod.pos.data.HttpTapnodApi
import ng.tapnod.pos.data.PosSettings
import ng.tapnod.pos.data.REQUEST_TTL_MS
import ng.tapnod.pos.data.RequestStatus
import ng.tapnod.pos.data.SettingsStore
import ng.tapnod.pos.data.SimulatorApi
import ng.tapnod.pos.data.TapnodApi
import ng.tapnod.pos.util.CardToken
import ng.tapnod.pos.util.Money
import ng.tapnod.pos.util.Speaker

enum class TapMode { CHARGE, LINK }

enum class NfcStatus { READY, OFF, MISSING }

enum class Outcome { APPROVED, DECLINED, EXPIRED }

data class Sale(
    val amountNaira: Long,
    val card: CardInfo,
    val requestId: String,
    val customerName: String?,
    val startedAtMs: Long,
    val expiresAtMs: Long,
    val reference: String? = null,
    val finishedAtMs: Long? = null
)

sealed interface Screen {
    data object Amount : Screen
    data class TapCard(val mode: TapMode) : Screen
    data class Reading(val mode: TapMode) : Screen
    data class Waiting(val sale: Sale) : Screen
    data class Result(val outcome: Outcome, val sale: Sale) : Screen
    data class Receipt(val outcome: Outcome, val sale: Sale) : Screen
    data class NotLinked(val card: CardInfo) : Screen
    data object LinkPick : Screen
    data class LinkDone(val customer: Customer, val card: CardInfo) : Screen
    data class Failure(val title: String, val message: String, val retry: TapMode?) : Screen
    data object Settings : Screen
}

/** Test card used by "Simulate tap" and "Accept any tap". Not a real card. */
const val DEMO_CARD_NUMBER = "5399000000004821"

data class UiState(
    val screen: Screen = Screen.Amount,
    val amountDigits: String = "",
    val settings: PosSettings,
    val online: Boolean? = null,
    val nfc: NfcStatus = NfcStatus.READY,
    val secondsLeft: Int = (REQUEST_TTL_MS / 1000).toInt(),
    val customers: List<Customer> = emptyList(),
    val customersLoading: Boolean = false,
    val customersError: String? = null,
    val linkCustomer: Customer? = null,
    val pendingLinkCard: CardInfo? = null,
    val amountError: Boolean = false,
    val connectionTest: String? = null,
    val voiceLabel: String? = null,
    val voiceNigerian: Boolean = false,
    val voiceCount: Int = 0
) {
    val amountNaira: Long get() = amountDigits.toLongOrNull() ?: 0L
}

class PosViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

    private val _state = MutableStateFlow(UiState(settings = store.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val speaker = Speaker(app, store.voiceName) { refreshVoice() }
    private var api: TapnodApi = buildApi(store.load())

    private var pollJob: Job? = null
    private var healthJob: Job? = null

    init {
        startHealthChecks()
    }

    private fun buildApi(s: PosSettings): TapnodApi =
        if (s.simulator) SimulatorApi(getApplication(), s) else HttpTapnodApi(s)

    private fun go(screen: Screen) = _state.update { it.copy(screen = screen) }

    /* ---------------- Amount keypad ---------------- */

    fun press(key: String) {
        _state.update { s ->
            val next = (s.amountDigits + key).trimStart('0')
            if (next.length > 8) s else s.copy(amountDigits = next, amountError = false)
        }
    }

    fun backspace() = _state.update { it.copy(amountDigits = it.amountDigits.dropLast(1), amountError = false) }

    fun clearAmount() = _state.update { it.copy(amountDigits = "", amountError = false) }

    fun charge() {
        val s = _state.value
        if (s.amountNaira <= 0L) {
            _state.update { it.copy(amountError = true) }
            speaker.failure()
            return
        }
        go(Screen.TapCard(TapMode.CHARGE))
    }

    /* ---------------- NFC ---------------- */

    fun setNfcStatus(status: NfcStatus) = _state.update { it.copy(nfc = status) }

    /** Called on the NFC thread. Returns the mode if a tap is expected right now. */
    fun beginCardRead(): TapMode? {
        var mode: TapMode? = null
        _state.update { s ->
            val screen = s.screen
            if (screen is Screen.TapCard) {
                mode = screen.mode
                s.copy(screen = Screen.Reading(screen.mode))
            } else s
        }
        return mode
    }

    /** The card number is turned into a token here and goes no further. */
    fun onCardNumber(pan: String, mode: TapMode) {
        val secret = _state.value.settings.tokenSecret
        val card = CardInfo(
            token = CardToken.token(pan, secret),
            last4 = CardToken.last4(pan),
            scheme = CardToken.scheme(pan)
        )
        speaker.tick()
        viewModelScope.launch {
            when (mode) {
                TapMode.CHARGE -> createRequest(card)
                TapMode.LINK -> {
                    val customer = _state.value.linkCustomer
                    if (customer == null) go(Screen.LinkPick) else link(customer, card)
                }
            }
        }
    }

    fun onCardError(message: String, mode: TapMode) {
        speaker.failure()
        go(Screen.Failure("Card not read", message, mode))
    }

    /** Simulator-only: pretend a test card was tapped. */
    fun simulateTap() {
        val mode = beginCardRead() ?: return
        onCardNumber(DEMO_CARD_NUMBER, mode)
    }

    /** A card touched the phone but its number couldn't be read: use the demo card instead. */
    fun onAnyTap(mode: TapMode) = onCardNumber(DEMO_CARD_NUMBER, mode)

    fun cancelTap() {
        _state.update { it.copy(linkCustomer = null, pendingLinkCard = null) }
        go(Screen.Amount)
    }

    fun retry(mode: TapMode) = go(Screen.TapCard(mode))

    /* ---------------- Charge flow ---------------- */

    private suspend fun createRequest(card: CardInfo) {
        val amount = _state.value.amountNaira
        try {
            when (val r = api.createRequest(amount * 100, card)) {
                is CreateResult.NotLinked -> {
                    speaker.failure()
                    go(Screen.NotLinked(card))
                }
                is CreateResult.Created -> {
                    val sale = Sale(
                        amountNaira = amount,
                        card = card,
                        requestId = r.requestId,
                        customerName = r.customerName,
                        startedAtMs = System.currentTimeMillis(),
                        expiresAtMs = r.expiresAtMs
                    )
                    go(Screen.Waiting(sale))
                    startPolling(sale)
                }
            }
        } catch (e: ApiException) {
            speaker.failure()
            go(Screen.Failure("Couldn't reach Tapnod", e.message ?: "Network error.", TapMode.CHARGE))
        } catch (e: Exception) {
            speaker.failure()
            go(Screen.Failure("Something went wrong", e.message ?: "Unknown error.", TapMode.CHARGE))
        }
    }

    private fun startPolling(sale: Sale) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val left = ((sale.expiresAtMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                _state.update { it.copy(secondsLeft = left.toInt()) }
                if (left <= 0) {
                    finish(sale, Outcome.EXPIRED, null)
                    return@launch
                }
                try {
                    val r = api.status(sale.requestId)
                    when (r.status) {
                        RequestStatus.APPROVED -> { finish(sale, Outcome.APPROVED, r.reference); return@launch }
                        RequestStatus.DECLINED -> { finish(sale, Outcome.DECLINED, r.reference); return@launch }
                        RequestStatus.EXPIRED -> { finish(sale, Outcome.EXPIRED, r.reference); return@launch }
                        RequestStatus.CANCELLED -> { go(Screen.Amount); return@launch }
                        RequestStatus.PENDING -> Unit
                    }
                } catch (_: Exception) {
                    // Keep trying until the request expires. A blip in the network shouldn't end the sale.
                }
                delay(700)
            }
        }
    }

    private fun finish(sale: Sale, outcome: Outcome, reference: String?) {
        val done = sale.copy(
            reference = reference ?: ("TPN-" + sale.requestId.takeLast(6).uppercase()),
            finishedAtMs = System.currentTimeMillis()
        )
        go(Screen.Result(outcome, done))
        val voice = _state.value.settings.voice
        when (outcome) {
            Outcome.APPROVED -> {
                speaker.success()
                if (voice) speaker.speak("Payment approved. ${Money.words(sale.amountNaira)}.")
            }
            Outcome.DECLINED -> {
                speaker.failure()
                if (voice) speaker.speak("Declined by customer. No money moved.")
            }
            Outcome.EXPIRED -> {
                speaker.failure()
                if (voice) speaker.speak("No answer. The payment timed out.")
            }
        }
    }

    fun cancelWaiting() {
        val screen = _state.value.screen
        pollJob?.cancel()
        if (screen is Screen.Waiting) {
            viewModelScope.launch { runCatching { api.cancel(screen.sale.requestId) } }
        }
        go(Screen.Amount)
    }

    fun newSale() {
        pollJob?.cancel()
        _state.update {
            it.copy(
                screen = Screen.Amount,
                amountDigits = "",
                linkCustomer = null,
                pendingLinkCard = null,
                secondsLeft = (REQUEST_TTL_MS / 1000).toInt()
            )
        }
    }

    fun showReceipt() {
        val screen = _state.value.screen
        if (screen is Screen.Result) go(Screen.Receipt(screen.outcome, screen.sale))
    }

    fun backToResult() {
        val screen = _state.value.screen
        if (screen is Screen.Receipt) go(Screen.Result(screen.outcome, screen.sale))
    }

    /* ---------------- Link a card ---------------- */

    fun startLink(fromCard: CardInfo? = null) {
        _state.update { it.copy(pendingLinkCard = fromCard, linkCustomer = null) }
        go(Screen.LinkPick)
        loadCustomers()
    }

    fun loadCustomers() {
        _state.update { it.copy(customersLoading = true, customersError = null) }
        viewModelScope.launch {
            try {
                val list = api.customers()
                _state.update { it.copy(customers = list, customersLoading = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(customersLoading = false, customersError = e.message ?: "Couldn't load customers.")
                }
            }
        }
    }

    fun pickLinkCustomer(customer: Customer) {
        val pending = _state.value.pendingLinkCard
        _state.update { it.copy(linkCustomer = customer) }
        if (pending != null) {
            viewModelScope.launch { link(customer, pending) }
        } else {
            go(Screen.TapCard(TapMode.LINK))
        }
    }

    private suspend fun link(customer: Customer, card: CardInfo) {
        go(Screen.Reading(TapMode.LINK))
        try {
            api.linkCard(customer.id, card)
            speaker.success()
            if (_state.value.settings.voice) speaker.speak("Card linked to ${customer.name}.")
            _state.update { it.copy(pendingLinkCard = null) }
            go(Screen.LinkDone(customer, card))
        } catch (e: Exception) {
            speaker.failure()
            go(Screen.Failure("Couldn't link the card", e.message ?: "Network error.", TapMode.LINK))
        }
    }

    /* ---------------- Settings ---------------- */

    fun openSettings() {
        _state.update { it.copy(connectionTest = null) }
        refreshVoice()
        go(Screen.Settings)
    }

    fun closeSettings() = go(Screen.Amount)

    fun saveSettings(s: PosSettings) {
        store.save(s)
        val saved = store.load()
        api = buildApi(saved)
        _state.update { it.copy(settings = saved, online = null, connectionTest = null) }
        startHealthChecks()
    }

    fun testConnection(s: PosSettings) {
        _state.update { it.copy(connectionTest = "Testing…") }
        viewModelScope.launch {
            val ok = buildApi(s).health()
            _state.update {
                it.copy(connectionTest = if (ok) "Connected. Tapnod answered." else "No answer from that URL.")
            }
        }
    }

    /* ---------------- Voice ---------------- */

    fun refreshVoice() {
        val current = speaker.current()
        val count = speaker.options().size
        _state.update {
            it.copy(
                voiceLabel = current?.label,
                voiceNigerian = current?.nigerian == true,
                voiceCount = count
            )
        }
    }

    fun testVoice() {
        val amount = _state.value.amountNaira.takeIf { it > 0 } ?: 15_000L
        speaker.speak("Payment approved. ${Money.words(amount)}, to ${_state.value.settings.merchantName}.")
    }

    fun nextVoice() {
        val options = speaker.options()
        if (options.isEmpty()) return
        val idx = options.indexOfFirst { it.name == speaker.current()?.name }
        val next = options[(idx + 1).mod(options.size)]
        if (speaker.useVoice(next.name)) {
            store.voiceName = next.name
            refreshVoice()
            testVoice()
        }
    }

    private fun startHealthChecks() {
        healthJob?.cancel()
        healthJob = viewModelScope.launch {
            while (isActive) {
                val ok = runCatching { api.health() }.getOrDefault(false)
                _state.update { it.copy(online = ok) }
                delay(10_000)
            }
        }
    }

    override fun onCleared() {
        speaker.shutdown()
        super.onCleared()
    }
}
