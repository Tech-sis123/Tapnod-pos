package ng.tapnod.pos.util

import java.text.NumberFormat
import java.util.Locale

object Money {

    private val grouping: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

    /** "15000" -> "₦15,000". Amounts are whole naira on the keypad. */
    fun format(naira: Long): String = "₦" + grouping.format(naira)

    fun formatKobo(kobo: Long): String = format(kobo / 100)

    /** 15000 -> "fifteen thousand naira" */
    fun words(naira: Long): String {
        if (naira <= 0L) return "zero naira"
        return numberToWords(naira) + " naira"
    }

    private val ones = arrayOf(
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen"
    )
    private val tens = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    )

    private fun underThousand(n: Int): String {
        val parts = mutableListOf<String>()
        val hundreds = n / 100
        val rest = n % 100
        if (hundreds > 0) parts += "${ones[hundreds]} hundred"
        if (rest > 0) {
            if (hundreds > 0) parts += "and"
            if (rest < 20) {
                parts += ones[rest]
            } else {
                val t = tens[rest / 10]
                val o = ones[rest % 10]
                parts += if (o.isEmpty()) t else "$t-$o"
            }
        }
        return parts.joinToString(" ")
    }

    fun numberToWords(value: Long): String {
        if (value == 0L) return "zero"
        val scales = listOf(
            1_000_000_000L to "billion",
            1_000_000L to "million",
            1_000L to "thousand"
        )
        var n = value
        val parts = mutableListOf<String>()
        for ((size, name) in scales) {
            if (n >= size) {
                parts += "${underThousand((n / size).toInt())} $name"
                n %= size
            }
        }
        if (n > 0) {
            val tail = underThousand(n.toInt())
            parts += if (parts.isNotEmpty() && n < 100) "and $tail" else tail
        }
        return parts.joinToString(" ")
    }
}
