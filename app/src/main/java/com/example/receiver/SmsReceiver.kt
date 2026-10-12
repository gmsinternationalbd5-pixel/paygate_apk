package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.example.data.local.db.AppDatabase
import com.example.data.local.entity.QueuedSms
import com.example.data.prefs.PreferenceManager
import com.example.domain.filter.SmsFilter
import com.example.service.PayGateForwarderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        // FR-1: Reassemble multipart SMS into single contiguous body
        val sender = messages[0].displayOriginatingAddress
            ?: messages[0].originatingAddress
            ?: return

        val fullBody = StringBuilder()
        for (msg in messages) {
            fullBody.append(msg.displayMessageBody ?: msg.messageBody ?: "")
        }
        val bodyText = fullBody.toString()
        val receivedAt = messages[0].timestampMillis.let {
            if (it > 0) it else System.currentTimeMillis()
        }

        val prefs = PreferenceManager(context.applicationContext)
        val allowedSenders = prefs.allowedSenders

        // FR-4: Enforce sender allowlist (bKash, NAGAD, 16216) & reject phone-number senders (10+ digits, +880, 01)
        if (!SmsFilter.isAllowedSender(sender, allowedSenders)) {
            // FR-3: Discard non-qualifying SMS immediately: no storage, no logging
            return
        }

        // FR-5: Filter for incoming money word, amount (Tk or ৳), and transaction token
        if (!SmsFilter.isWalletReceivedMessage(bodyText)) {
            // FR-3: Discard immediately
            return
        }

        // Determine SIM slot (1 or 2)
        val simSlot = resolveSimSlot(context, intent)

        // FR-6: Extract metadata including ref_hint
        val metadata = SmsFilter.extractMetadata(sender, bodyText)

        // FR-9: client_msg_id = SHA-256(sender | body | sms_timestamp | sim_slot)
        val clientMsgId = SmsFilter.generateClientMsgId(sender, bodyText, receivedAt, simSlot)
        val seq = prefs.nextDeviceSeq()

        val queuedSms = QueuedSms(
            clientMsgId = clientMsgId,
            deviceSeq = seq,
            sender = sender,
            body = bodyText,
            receivedAt = receivedAt,
            simSlot = simSlot,
            refHint = metadata.refHint,
            source = QueuedSms.SOURCE_LIVE,
            status = QueuedSms.STATUS_PENDING,
            attemptCount = 0,
            trxId = metadata.trxId,
            amount = metadata.amount,
            provider = metadata.provider
        )

        // FR-1 & FR-2: Persist to Room inside goAsync() before any network call
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context.applicationContext)
                val rowId = db.queuedSmsDao().insertOrIgnore(queuedSms)
                if (rowId != -1L) {
                    PayGateForwarderService.triggerDrain(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun resolveSimSlot(context: Context, intent: Intent): Int {
        var subId = intent.getIntExtra("subscription", -1)
        if (subId == -1) {
            subId = intent.getIntExtra("simId", -1)
        }
        if (subId == -1) {
            subId = intent.getIntExtra("slot", -1)
        }

        if (subId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val info = sm?.getActiveSubscriptionInfo(subId)
                if (info != null) {
                    return info.simSlotIndex + 1 // 1-indexed for display
                }
            } catch (_: SecurityException) {
                // READ_PHONE_STATE fallback
            }
        }
        return 1
    }
}
