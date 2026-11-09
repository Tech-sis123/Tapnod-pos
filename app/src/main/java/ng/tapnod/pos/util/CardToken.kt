package ng.tapnod.pos.util

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Turns a card number into a one-way token on the phone.
 * The raw card number is never stored, logged, shown or sent anywhere.
 */
object CardToken {

    fun token(pan: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal(pan.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun last4(pan: String): String = pan.takeLast(4)

    fun scheme(pan: String): String {
        val p2 = pan.take(2).toIntOrNull() ?: -1
        val p4 = pan.take(4).toIntOrNull() ?: -1
        val p6 = pan.take(6).toIntOrNull() ?: -1
        return when {
            p4 in 5060..5061 || p4 in 5078..5079 || p6 in 650002..650027 -> "VERVE"
            pan.startsWith("4") -> "VISA"
            p2 in 51..55 || p4 in 2221..2720 -> "MASTERCARD"
            else -> "CARD"
        }
    }
}
