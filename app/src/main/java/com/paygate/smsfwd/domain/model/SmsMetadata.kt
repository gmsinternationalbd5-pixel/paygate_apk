package com.paygate.smsfwd.domain.model

data class SmsMetadata(
    val provider: String,
    val amount: String?,
    val trxId: String?,
    val refHint: String?,
    val senderNumber: String?,
    val isGenuineWalletSms: Boolean
) {
    val maskedTrxId: String
        get() {
            val id = trxId ?: return "••••"
            return if (id.length > 4) {
                "••••" + id.takeLast(4)
            } else {
                id
            }
        }
}
