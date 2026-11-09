package ng.tapnod.pos.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Everything the POS needs from Tapnod. See README.md for the HTTP contract. */
interface TapnodApi {
    suspend fun health(): Boolean
    /** [customerId] set = the agent picked the customer on the POS instead of reading a card. */
    suspend fun createRequest(amountKobo: Long, card: CardInfo, customerId: String? = null): CreateResult
    suspend fun status(requestId: String): StatusResult
    suspend fun cancel(requestId: String)
    suspend fun customers(): List<Customer>
    suspend fun linkCard(customerId: String, card: CardInfo)
}

const val REQUEST_TTL_MS = 120_000L

/* ------------------------------------------------------------------ */
/* Live backend over HTTP                                              */
/* ------------------------------------------------------------------ */

class HttpTapnodApi(private val settings: PosSettings) : TapnodApi {

    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun url(path: String): String {
        val base = settings.backendUrl.trim().trimEnd('/')
        if (base.isEmpty()) throw ApiException("No backend URL. Add one in Settings or turn on Simulator.")
        return base + path
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url(path))
                .header("X-Terminal-Id", settings.terminalId)
                .header("Accept", "application/json")
            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post((body ?: JSONObject()).toString().toRequestBody(json))
                else -> throw IllegalArgumentException(method)
            }
            try {
                client.newCall(builder.build()).execute().use { res ->
                    res.code to (res.body?.string() ?: "")
                }
            } catch (e: java.io.IOException) {
                throw ApiException("Can't reach Tapnod. Check the internet or the backend URL.")
            }
        }

    override suspend fun health(): Boolean = try {
        call("GET", "/api/health").first in 200..299
    } catch (e: Exception) {
        false
    }

    override suspend fun createRequest(amountKobo: Long, card: CardInfo, customerId: String?): CreateResult {
        val body = JSONObject()
            .put("amount_kobo", amountKobo)
            .put("currency", "NGN")
            .put("merchant_id", settings.merchantId)
            .put("merchant_name", settings.merchantName)
            .put("terminal_id", settings.terminalId)
            .put("card_last4", card.last4)
            .put("card_scheme", card.scheme)
        if (customerId != null) body.put("customer_id", customerId) else body.put("card_token", card.token)
        val (code, text) = call("POST", "/api/pos/requests", body)
        if (code == 404) return CreateResult.NotLinked
        if (code !in 200..299) throw ApiException(errorMessage(text, code))
        val o = JSONObject(text)
        if (o.optString("error") == "card_not_linked") return CreateResult.NotLinked
        val expires = o.optLong("expires_at_ms", 0L).takeIf { it > 0 }
            ?: (System.currentTimeMillis() + REQUEST_TTL_MS)
        return CreateResult.Created(
            requestId = o.getString("request_id"),
            customerName = o.optString("customer_name").takeIf { it.isNotBlank() },
            expiresAtMs = expires
        )
    }

    override suspend fun status(requestId: String): StatusResult {
        val (code, text) = call("GET", "/api/pos/requests/$requestId")
        if (code !in 200..299) throw ApiException(errorMessage(text, code))
        val o = JSONObject(text)
        val status = when (o.optString("status").lowercase()) {
            "approved" -> RequestStatus.APPROVED
            "declined" -> RequestStatus.DECLINED
            "expired" -> RequestStatus.EXPIRED
            "cancelled", "canceled" -> RequestStatus.CANCELLED
            else -> RequestStatus.PENDING
        }
        return StatusResult(status, o.optString("reference").takeIf { it.isNotBlank() })
    }

    override suspend fun cancel(requestId: String) {
        runCatching { call("POST", "/api/pos/requests/$requestId/cancel") }
    }

    override suspend fun customers(): List<Customer> {
        val (code, text) = call("GET", "/api/pos/customers")
        if (code !in 200..299) throw ApiException(errorMessage(text, code))
        val arr: JSONArray = runCatching { JSONObject(text).getJSONArray("customers") }
            .getOrElse { JSONArray(text) }
        return (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            Customer(c.getString("id"), c.getString("name"), c.optString("note"))
        }
    }

    override suspend fun linkCard(customerId: String, card: CardInfo) {
        val body = JSONObject()
            .put("customer_id", customerId)
            .put("card_token", card.token)
            .put("card_last4", card.last4)
            .put("card_scheme", card.scheme)
        val (code, text) = call("POST", "/api/pos/cards/link", body)
        if (code !in 200..299) throw ApiException(errorMessage(text, code))
    }

    private fun errorMessage(text: String, code: Int): String {
        val msg = runCatching { JSONObject(text).optString("message") }.getOrNull()
        return if (!msg.isNullOrBlank()) msg else "Tapnod answered with an error ($code)."
    }
}

/* ------------------------------------------------------------------ */
/* Offline simulator: lets the POS run with no backend                 */
/* ------------------------------------------------------------------ */

class SimulatorApi(context: Context, private val settings: PosSettings) : TapnodApi {

    private val prefs = context.getSharedPreferences("tapnod_sim", Context.MODE_PRIVATE)

    private data class Pending(val createdAt: Long, val customerName: String)

    private val pending = ConcurrentHashMap<String, Pending>()

    private val demoCustomers = listOf(
        Customer("cus_amaka", "Amaka Okafor", "Smartphone, screen reader on"),
        Customer("cus_ngozi", "Mama Ngozi", "Button phone, call to approve"),
        Customer("cus_tunde", "Tunde Bakare", "Smartphone")
    )

    override suspend fun health(): Boolean = true

    override suspend fun createRequest(amountKobo: Long, card: CardInfo, customerId: String?): CreateResult {
        kotlinx.coroutines.delay(450)
        val customerId = customerId ?: prefs.getString("card_${card.token}", null) ?: return CreateResult.NotLinked
        val name = demoCustomers.firstOrNull { it.id == customerId }?.name ?: "Customer"
        val id = "req_" + UUID.randomUUID().toString().take(8)
        pending[id] = Pending(System.currentTimeMillis(), name)
        return CreateResult.Created(id, name, System.currentTimeMillis() + REQUEST_TTL_MS)
    }

    override suspend fun status(requestId: String): StatusResult {
        val p = pending[requestId] ?: return StatusResult(RequestStatus.CANCELLED, null)
        val elapsed = System.currentTimeMillis() - p.createdAt
        if (elapsed < 4_500) return StatusResult(RequestStatus.PENDING, null)
        val ref = "TPN-" + requestId.takeLast(6).uppercase()
        return when (settings.simOutcome) {
            SimOutcome.APPROVE -> StatusResult(RequestStatus.APPROVED, ref)
            SimOutcome.DECLINE -> StatusResult(RequestStatus.DECLINED, ref)
            SimOutcome.NO_ANSWER ->
                if (elapsed > REQUEST_TTL_MS) StatusResult(RequestStatus.EXPIRED, ref)
                else StatusResult(RequestStatus.PENDING, null)
        }
    }

    override suspend fun cancel(requestId: String) {
        pending.remove(requestId)
    }

    override suspend fun customers(): List<Customer> = demoCustomers

    override suspend fun linkCard(customerId: String, card: CardInfo) {
        kotlinx.coroutines.delay(350)
        prefs.edit().putString("card_${card.token}", customerId).apply()
    }
}
