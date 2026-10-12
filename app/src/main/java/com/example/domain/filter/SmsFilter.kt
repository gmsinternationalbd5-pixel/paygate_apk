package com.example.domain.filter

import com.example.domain.model.SmsMetadata
import java.security.MessageDigest
import java.util.Locale

object SmsFilter {

    private val PHONE_NUMBER_REGEX = Regex("""^(\+?880?|880?|0)1[3-9]\d{8}$""")
    private val DIGITS_ONLY_LONG = Regex("""^\+?\d{10,16}$""")

    private val TRX_ID_REGEX = Regex(
        """(?:TrxID|TxnId|Txn\s*ID|TxID|Tran\s*ID|Transaction\s*ID)[:\s#]*([A-Za-z0-9]{4,25})""",
        RegexOption.IGNORE_CASE
    )

    private val AMOUNT_REGEX = Regex(
        """(?:Tk|Tk\.|BDT|Amount|৳)\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val SENDER_NUMBER_REGEX = Regex(
        """(?:from|from\s+A/C:?)\s*(\+?88?01[3-9]\d{8})""",
        RegexOption.IGNORE_CASE
    )

    // FR-6: Extract ref_hint: label Ref, Reference, Ref No (or Bengali রেফ, রেফারেন্স), then an alphanumeric token
    private val REF_HINT_REGEX = Regex(
        """(?:Reference|Ref\s*No|\bRef\b|রেফারেন্স|রেফ)[:\s\-#]+([A-Za-z0-9\-_]{4,25})""",
        RegexOption.IGNORE_CASE
    )

    // FR-5: Incoming money keywords
    private val INCOMING_MONEY_KEYWORDS = listOf(
        "received",
        "cash in",
        "credited",
        "টাকা এসেছে",
        "ক্যাশ ইন",
        "জমা হয়েছে",
        "জমা হয়েছে"
    )

    // FR-5: Excluded words: OTP, PIN, sent, payment to, cash out, recharge
    private val EXCLUDED_KEYWORDS = listOf(
        "otp",
        "pin",
        "security code",
        "verification code",
        "payment to",
        "sent to",
        "cash out",
        "cashout",
        "recharge"
    )

    /**
     * FR-4: Sender allowlist enforced on the device.
     * Match sender address case-insensitively and trimmed.
     * Always reject phone-number senders (10+ digits, +880, 880, 01).
     */
    fun isAllowedSender(sender: String, allowedSenders: Set<String>): Boolean {
        val trimmed = sender.trim()
        if (trimmed.isEmpty()) return false

        // Check if sender looks like a phone number
        if (PHONE_NUMBER_REGEX.matches(trimmed) || DIGITS_ONLY_LONG.matches(trimmed)) {
            return false
        }
        if (trimmed.startsWith("+880") || trimmed.startsWith("880") || trimmed.startsWith("01")) {
            if (trimmed.length >= 10) return false
        }

        // Match case-insensitively against allowlist
        return allowedSenders.any { it.trim().equals(trimmed, ignoreCase = true) }
    }

    /**
     * FR-5: Qualify only if incoming money word, amount (Tk or ৳), and transaction token.
     * Excludes OTP, PIN, sent, payment to, cash out, recharge.
     */
    fun isWalletReceivedMessage(body: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)

        // Check exclusions first (e.g. OTP, sent, etc.)
        for (excluded in EXCLUDED_KEYWORDS) {
            if (lower.contains(excluded)) {
                return false
            }
        }

        // Check incoming money keywords
        val hasIncoming = INCOMING_MONEY_KEYWORDS.any { lower.contains(it) }
        if (!hasIncoming) return false

        // Must have TrxID pattern
        val hasTrxId = TRX_ID_REGEX.containsMatchIn(body)
        if (!hasTrxId) return false

        // Must have amount pattern (Tk or ৳)
        val hasAmount = AMOUNT_REGEX.containsMatchIn(body) || body.contains("Tk", ignoreCase = true) || body.contains("৳")
        return hasAmount
    }

    /**
     * FR-6: Extract ref_hint.
     * Label Ref, Reference or Ref No (any case, optional :, -, #) or Bengali label,
     * then a 4 to 25 character alphanumeric token.
     * Normalized to uppercase without spaces or hyphens.
     * Null if absent.
     */
    fun extractRefHint(body: String): String? {
        val match = REF_HINT_REGEX.find(body) ?: return null
        val raw = match.groupValues.getOrNull(1) ?: return null
        val cleaned = raw.replace("-", "").replace("_", "").trim().uppercase(Locale.ROOT)
        return if (cleaned.length >= 4) cleaned else null
    }

    /**
     * Extracts structured metadata for UI display, masking, and logging.
     */
    fun extractMetadata(sender: String, body: String): SmsMetadata {
        val provider = detectProvider(sender, body)
        val trxId = TRX_ID_REGEX.find(body)?.groupValues?.getOrNull(1)
        val amountValue = AMOUNT_REGEX.find(body)?.groupValues?.getOrNull(1)
        val amount = if (amountValue != null) "Tk $amountValue" else null
        val refHint = extractRefHint(body)
        val senderNumber = SENDER_NUMBER_REGEX.find(body)?.groupValues?.getOrNull(1)

        val isGenuine = isWalletReceivedMessage(body)

        return SmsMetadata(
            provider = provider,
            amount = amount,
            trxId = trxId,
            refHint = refHint,
            senderNumber = senderNumber,
            isGenuineWalletSms = isGenuine
        )
    }

    fun detectProvider(sender: String, body: String): String {
        val s = sender.trim().lowercase(Locale.ROOT)
        val b = body.lowercase(Locale.ROOT)

        return when {
            s == "16216" || s.contains("rocket") || b.contains("rocket") -> "Rocket"
            s == "nagad" || b.contains("nagad") -> "Nagad"
            s == "bkash" || b.contains("bkash") -> "bKash"
            else -> "Wallet"
        }
    }

    /**
     * FR-9: Idempotent delivery.
     * client_msg_id = SHA-256(sender | body | sms_timestamp | sim_slot)
     */
    fun generateClientMsgId(sender: String, body: String, receivedAt: Long, simSlot: Int = 1): String {
        val raw = "${sender.trim()}|${body.trim()}|$receivedAt|$simSlot"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
