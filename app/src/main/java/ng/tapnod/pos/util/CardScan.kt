package ng.tapnod.pos.util

/**
 * Decides, from the text the camera sees, whether a bank card is in front of it.
 *
 * Two ways to count as "a card":
 *  1. A 13 to 19 digit number that passes the Luhn check (the checksum every card number has).
 *     That's a real card number, so the real card is used.
 *  2. No readable number, but the text looks like a card (VISA, Verve, VALID THRU, bank names,
 *     groups of 4 digits). Then we know a card is there but not which one.
 */
object CardScan {

    sealed class Hit {
        class Number(val pan: String) : Hit()
        data object CardSeen : Hit()
    }

    private val CARD_WORDS = listOf(
        "VISA", "MASTERCARD", "MASTER CARD", "VERVE", "DEBIT", "CREDIT", "VALID THRU", "VALID FROM",
        "GOOD THRU", "EXPIRES", "CARDHOLDER", "PREPAID", "CONTACTLESS", "AFRIGO",
        "ACCESS", "ZENITH", "GTBANK", "GUARANTY", "UBA", "FIRST BANK", "FIRSTBANK", "FIDELITY",
        "STANBIC", "STERLING", "UNION BANK", "WEMA", "ECOBANK", "POLARIS", "KEYSTONE",
        "FCMB", "OPAY", "MONIEPOINT", "KUDA", "PALMPAY", "PROVIDUS", "JAIZ", "TITAN"
    )

    private val GROUP_OF_FOUR = Regex("""\b\d{4}\b""")
    private val EXPIRY = Regex("""\b(0[1-9]|1[0-2])\s?/\s?\d{2}\b""")

    fun find(text: String): Hit? {
        numberIn(text)?.let { return Hit.Number(it) }
        val upper = text.uppercase()
        val words = CARD_WORDS.count { upper.contains(it) }
        val groups = GROUP_OF_FOUR.findAll(text).count()
        val hasExpiry = EXPIRY.containsMatchIn(text)
        val looksLikeCard = words >= 1 && (groups >= 2 || hasExpiry) || words >= 2 || groups >= 3
        return if (looksLikeCard) Hit.CardSeen else null
    }

    /** Looks for a Luhn-valid card number, line by line, ignoring spaces and dashes inside it. */
    fun numberIn(text: String): String? {
        for (line in text.lines() + text.replace("\n", " ")) {
            val digits = line.replace(Regex("""[\s\-]"""), "")
            for (match in Regex("""\d{13,19}""").findAll(digits)) {
                val candidate = match.value
                if (luhn(candidate)) return candidate
            }
        }
        return null
    }

    fun luhn(number: String): Boolean {
        var sum = 0
        var double = false
        for (i in number.indices.reversed()) {
            var d = number[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0 && number.any { it != '0' }
    }
}
