package ng.tapnod.pos.data

/** What the POS keeps about a tapped card. Never the card number. */
data class CardInfo(
    val token: String,
    val last4: String,
    val scheme: String
)

data class Customer(
    val id: String,
    val name: String,
    val note: String = ""
)

sealed class CreateResult {
    data class Created(
        val requestId: String,
        val customerName: String?,
        val expiresAtMs: Long
    ) : CreateResult()

    data object NotLinked : CreateResult()
}

enum class RequestStatus { PENDING, APPROVED, DECLINED, EXPIRED, CANCELLED }

data class StatusResult(
    val status: RequestStatus,
    val reference: String?
)

enum class SimOutcome { APPROVE, DECLINE, NO_ANSWER }

data class PosSettings(
    val simulator: Boolean,
    val simOutcome: SimOutcome,
    val backendUrl: String,
    val terminalId: String,
    val merchantName: String,
    val merchantId: String,
    val tokenSecret: String,
    val voice: Boolean,
    /** Demo safety net: if the card number can't be read, still accept the tap as the demo card. */
    val acceptAnyTap: Boolean
)

class ApiException(message: String) : Exception(message)
