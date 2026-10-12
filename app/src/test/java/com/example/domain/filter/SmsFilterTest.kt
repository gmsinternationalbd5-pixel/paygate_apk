package com.example.domain.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsFilterTest {

    private val allowedSenders = setOf("bKash", "NAGAD", "16216")

    @Test
    fun testAllowedSenders_valid() {
        assertTrue(SmsFilter.isAllowedSender("bKash", allowedSenders))
        assertTrue(SmsFilter.isAllowedSender("BKASH", allowedSenders))
        assertTrue(SmsFilter.isAllowedSender("bkash", allowedSenders))
        assertTrue(SmsFilter.isAllowedSender("NAGAD", allowedSenders))
        assertTrue(SmsFilter.isAllowedSender("nagad", allowedSenders))
        assertTrue(SmsFilter.isAllowedSender("16216", allowedSenders))
    }

    @Test
    fun testAllowedSenders_rejectNormalMobileNumbers() {
        // FR-4: Numeric 11-digit senders (normal phone numbers) are always rejected
        assertFalse(SmsFilter.isAllowedSender("01712345678", allowedSenders))
        assertFalse(SmsFilter.isAllowedSender("+8801712345678", allowedSenders))
        assertFalse(SmsFilter.isAllowedSender("01811223344", allowedSenders))
        assertFalse(SmsFilter.isAllowedSender("01999887766", allowedSenders))
        assertFalse(SmsFilter.isAllowedSender("unknown_bank", allowedSenders))
    }

    @Test
    fun testWalletReceivedMessage_bKashFormat1() {
        val sms = "You have received Tk 1,200.00 from 01712750278. Fee Tk 0.00. Balance Tk 1,200.00. TrxID DJC9SZQV0J at 12/10/2026 15:24"
        assertTrue(SmsFilter.isWalletReceivedMessage(sms))

        val meta = SmsFilter.extractMetadata("bKash", sms)
        assertEquals("bKash", meta.provider)
        assertEquals("Tk 1,200.00", meta.amount)
        assertEquals("DJC9SZQV0J", meta.trxId)
        assertEquals("••••QV0J", meta.maskedTrxId)
    }

    @Test
    fun testWalletReceivedMessage_bKashCashInFormat() {
        val sms = "Cash In Tk 2,500.00 from 01755650768 successful. Fee Tk 0.00. Balance Tk 2,505.39. TrxID DIS133PE5L at 28/09/2026 21:59."
        assertTrue(SmsFilter.isWalletReceivedMessage(sms))

        val meta = SmsFilter.extractMetadata("bKash", sms)
        assertEquals("bKash", meta.provider)
        assertEquals("Tk 2,500.00", meta.amount)
        assertEquals("DIS133PE5L", meta.trxId)
        assertEquals("••••PE5L", meta.maskedTrxId)
    }

    @Test
    fun testWalletReceivedMessage_nagadFormat() {
        val sms = "You have received Tk 850.00 from 01811223344. Balance Tk 2,400.00. TrxID 72AB3890 at 12/10/2026"
        assertTrue(SmsFilter.isWalletReceivedMessage(sms))

        val meta = SmsFilter.extractMetadata("NAGAD", sms)
        assertEquals("Nagad", meta.provider)
        assertEquals("Tk 850.00", meta.amount)
        assertEquals("72AB3890", meta.trxId)
    }

    @Test
    fun testWalletReceivedMessage_rocketFormat() {
        val sms = "Tk 3,100.00 received from 01999887766. TxnId 98214. Rocket Balance Tk 5,000.00"
        assertTrue(SmsFilter.isWalletReceivedMessage(sms))

        val meta = SmsFilter.extractMetadata("16216", sms)
        assertEquals("Rocket", meta.provider)
        assertEquals("Tk 3,100.00", meta.amount)
        assertEquals("98214", meta.trxId)
    }

    @Test
    fun testRejectNonWalletSms() {
        // Personal message
        assertFalse(SmsFilter.isWalletReceivedMessage("Hey bro, how are you? Let's meet at 5pm."))

        // OTP message
        assertFalse(SmsFilter.isWalletReceivedMessage("Your security code is 849201. Do not share with anyone."))

        // Outbound transaction (payment made, not received)
        assertFalse(SmsFilter.isWalletReceivedMessage("Payment Tk 350.00 to Merchant Coffee Shop successful. TrxID XYZ123"))
    }

    @Test
    fun testClientMsgId_stableAndIdempotent() {
        val hash1 = SmsFilter.generateClientMsgId("bKash", "You have received Tk 100 TrxID 123", 1760000000L, 1)
        val hash2 = SmsFilter.generateClientMsgId("bKash", "You have received Tk 100 TrxID 123", 1760000000L, 1)
        val hashSlot2 = SmsFilter.generateClientMsgId("bKash", "You have received Tk 100 TrxID 123", 1760000000L, 2)
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 produces 64 hex characters
        // Different SIM slot must produce different client_msg_id
        assertTrue(hash1 != hashSlot2)
    }

    @Test
    fun testExtractRefHint_englishAndBengali() {
        val smsWithRef = "You have received Tk 1,200.00 from 01712750278. TrxID DJC9SZQV0J and Ref REF934P9 at 12/10/2026"
        assertEquals("REF934P9", SmsFilter.extractRefHint(smsWithRef))

        val smsWithRefColon = "You have received Tk 500. TrxID ABC12345. Reference: ORDER99X"
        assertEquals("ORDER99X", SmsFilter.extractRefHint(smsWithRefColon))

        val smsBengaliRef = "ক্যাশ ইন Tk 1000 সফল হয়েছে। TrxID 998877 এবং রেফ: SHOP1000"
        assertEquals("SHOP1000", SmsFilter.extractRefHint(smsBengaliRef))

        val smsWithoutRef = "You have received Tk 500. TrxID ABC12345."
        assertEquals(null, SmsFilter.extractRefHint(smsWithoutRef))
    }

    @Test
    fun testBengaliIncomingSms_qualifies() {
        val bengaliSms = "টাকা এসেছে Tk 750.00। ব্যালেন্স Tk 1500.00। TrxID BN98214"
        assertTrue(SmsFilter.isWalletReceivedMessage(bengaliSms))
        val meta = SmsFilter.extractMetadata("bKash", bengaliSms)
        assertTrue(meta.isGenuineWalletSms)
        assertEquals("BN98214", meta.trxId)
    }
}
