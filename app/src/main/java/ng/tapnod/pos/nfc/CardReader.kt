package ng.tapnod.pos.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.github.devnied.emvnfccard.exception.CommunicationException
import com.github.devnied.emvnfccard.parser.EmvTemplate
import com.github.devnied.emvnfccard.parser.IProvider
import java.io.IOException

/**
 * Reads a contactless EMV bank card (Visa, Mastercard, Verve) held to the back of the phone.
 * Returns the card number only so the caller can turn it into a token straight away.
 */
object CardReader {

    sealed class Result {
        class Success(val pan: String) : Result()
        class Failure(val message: String) : Result()
    }

    fun read(tag: Tag): Result {
        val iso = IsoDep.get(tag)
            ?: return Result.Failure("That isn't a bank card. Tap a contactless debit card.")
        return try {
            iso.connect()
            iso.timeout = 5000

            val provider = object : IProvider {
                override fun transceive(pCommand: ByteArray): ByteArray {
                    try {
                        return iso.transceive(pCommand)
                    } catch (e: IOException) {
                        throw CommunicationException(e.message ?: "transceive failed")
                    }
                }

                override fun getAt(): ByteArray {
                    return iso.historicalBytes ?: iso.hiLayerResponse ?: ByteArray(0)
                }
            }

            val config = EmvTemplate.Config()
                .setContactLess(true)
                .setReadAllAids(true)
                .setReadTransactions(false)
                .setReadCplc(false)
                .setRemoveDefaultParsers(false)
                .setReadAt(false)

            val parser = EmvTemplate.Builder()
                .setProvider(provider)
                .setConfig(config)
                .build()

            val card = parser.readEmvCard()
            val pan = card?.cardNumber?.filter { it.isDigit() }.orEmpty()
            if (pan.length < 12) {
                Result.Failure("Couldn't read the card number. Hold the card flat and still.")
            } else {
                Result.Success(pan)
            }
        } catch (e: Exception) {
            Result.Failure("The card moved too soon. Tap again and hold it still for a second.")
        } finally {
            try {
                iso.close()
            } catch (_: Exception) {
            }
        }
    }
}
