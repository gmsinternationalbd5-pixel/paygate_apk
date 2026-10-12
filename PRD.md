# Product Requirements Document (PRD)
## PayGate SMS Forwarder — Android Companion App
**Version:** 1.0 · Production Grade  
**Date:** October 12, 2026  
**Companion System:** PayGate Merchant SaaS (Next.js + Supabase)

---

## 1. Executive Summary
PayGate enables Bangladeshi online merchants to accept mobile financial service payments (bKash, Nagad, and Rocket) without complex provider APIs or merchant account delays. The server confirms incoming customer payments by matching customer-submitted Transaction IDs (TrxID) against genuine "money received" SMS messages arriving on the merchant's dedicated Android phone.

The **PayGate SMS Forwarder** is the Android companion application running on that merchant phone. It continuously watches for incoming financial wallet SMS, filters them against an on-device allowlist and regex pattern, persists them locally to a durable Room outbox, and delivers them reliably over HTTPS to the PayGate server webhook (`/v1/sms/webhook`).

Reliability, security, and battery longevity are the foundational product pillars.

---

## 2. Goals & Non-Goals

### Goals
- **Zero Message Loss:** Guarantee that every qualifying wallet payment SMS is durably stored before any network request and delivered to the PayGate server within 5 seconds on a healthy connection.
- **Aggressive OEM Survival:** Maintain background operation across reboots, app updates, and aggressive battery management found on Xiaomi (MIUI/HyperOS), Samsung (OneUI), Oppo/Realme (ColorOS), Vivo (FuntouchOS), Tecno, and Infinix devices.
- **Strict Forgery Resistance:** Enforce on-device sender allowlisting (bKash, NAGAD, 16216) and reject standard 11-digit phone numbers (`017xxxxxxxx`) to eliminate spoofing attacks.
- **Glanceable Device Health:** Provide merchants and admins with real-time fleet health (connection state, battery %, queue depth, last heartbeat, permission status).
- **Fast Onboarding:** Complete pairing via QR code in under 2 minutes with zero typing of secrets.

### Non-Goals (v1)
- Reading, storing, or uploading personal SMS, bank OTPs, or non-wallet messages.
- Sending SMS, replying to customers, or initiating wallet transactions.
- iOS support (iOS prevents background SMS broadcast interception).
- On-device transaction authority: The server remains the ultimate source of truth for transaction matching and settlement.

---

## 3. User Personas
| Persona | Context | Primary Needs |
|---|---|---|
| **Merchant Owner** | Small business owner using a dedicated Android device with the wallet SIM. | Simple 2-minute setup, clear green/red status, set-and-forget reliability. |
| **Merchant Staff** | Cashiers/operators checking phone operation during business hours. | Glanceable status card, one-tap "Sync Now" and test triggers. |
| **PayGate Admin** | Platform administrator operating the SaaS backend. | Fleet visibility, heartbeat telemetry, remote token revocation. |

---

## 4. Functional Requirements (FR)

| ID | Priority | Requirement Description | Implementation in Codebase |
|---|---|---|---|
| **FR-1** | **P0** | Capture incoming SMS via manifest-registered `SMS_RECEIVED` receiver, reassembling multipart PDUs into a single body. | `com.example.receiver.SmsReceiver` using `Telephony.Sms.Intents.getMessagesFromIntent` |
| **FR-2** | **P0** | Persist candidate messages to an offline Room database (`queued_sms`) before attempting network dispatch. | `com.example.data.local.db.AppDatabase` & `QueuedSmsDao` |
| **FR-3** | **P0** | Filter messages for wallet payment indicators ("received" or "Cash In") and valid TrxID tokens. Discard personal SMS and OTPs immediately without logging. | `com.example.domain.filter.SmsFilter.isWalletReceivedMessage` |
| **FR-4** | **P0** | Sender allowlist enforcement (`bKash`, `NAGAD`, `16216`). Reject standard 11-digit phone numbers unconditionally. | `com.example.domain.filter.SmsFilter.isAllowedSender` |
| **FR-5** | **P0** | Deliver to `POST /v1/sms/webhook` with bearer token over HTTPS. Exponential backoff retry with jitter (capped at 15m), 24h expiration check. | `com.example.service.QueueSyncManager.drainQueue` |
| **FR-6** | **P0** | Idempotent delivery using SHA-256 client message ID (`client_msg_id = SHA256(sender\|body\|timestamp)`). | `com.example.domain.filter.SmsFilter.generateClientMsgId` |
| **FR-7** | **P0** | Pair via QR code or manual code (`POST /v1/devices/pair`). Encrypt device token at rest using Android KeyStore (`AES/GCM/NoPadding`). | `com.example.data.security.KeystoreSecretManager` |
| **FR-8** | **P0** | Foreground service of type `dataSync` with persistent notification + `RECEIVE_BOOT_COMPLETED` restart. | `com.example.service.PayGateForwarderService` & `BootReceiver` |
| **FR-9** | **P0** | Guided onboarding with live health checklist (SMS, Notifications, Battery exemption, OEM auto-start). | `com.example.ui.screens.HomeScreen` & `SettingsScreen` |
| **FR-10**| **P0** | Home dashboard displaying connection status, last forwarded payment, queue depth, and test simulator. | `com.example.ui.screens.HomeScreen` |
| **FR-11**| **P1** | Heartbeat every 5 minutes reporting battery %, charging status, network type, queue depth, and permission states to `/v1/devices/heartbeat`. | `PayGateForwarderService.sendHeartbeatInternal` |
| **FR-12**| **P1** | Activity log of up to 200 items with delivery status, masked TrxID (`••••V0J`), and reveal toggle. | `com.example.ui.screens.ActivityLogScreen` |
| **FR-13**| **P1** | Local high-priority notifications on stuck queue or revoked device token. | `PayGateForwarderService.showLocalAlertNotification` |
| **FR-14**| **P1** | One-time backfill toggle for missed SMS during downtime with explicit merchant consent. | `PreferenceManager.backfillEnabled` |
| **FR-15**| **P1** | Dual-SIM slot identification recorded in `QueuedSms` and forwarded in webhook payload. | `SmsReceiver.resolveSimSlot` |
| **FR-18**| **P1** | Full English and Bengali (বাংলা) localization with instant language switching. | `res/values/strings.xml` & `res/values-bn/strings.xml` |

---

## 5. API Contracts

### 5.1 SMS Webhook
`POST /v1/sms/webhook`  
**Headers:** `Authorization: Bearer <device_token>`, `Content-Type: application/json`

```json
{
  "sender": "bKash",
  "body": "You have received Tk 1,200.00 from 01712750278. Fee Tk 0.00. Balance Tk 1,200.00. TrxID DJC9SZQV0J at 12/10/2026 15:24",
  "client_msg_id": "8f4a13b...64hex",
  "received_at": 1760000000,
  "sim_slot": 1,
  "app_version": "1.0.0"
}
```

**Responses:**
- `200 OK`: `{"stored": true, "trx_id": "DJC9SZQV0J"}` or `{"ignored": true}` -> Mark delivered.
- `401 Unauthorized`: Token invalid or revoked -> Halt forwarding, notify merchant.
- `429 / 5xx`: Server error or rate limited -> Retry with exponential backoff.
- `4xx`: Client error -> Mark non-retryable failed.

### 5.2 Device Pairing
`POST /v1/devices/pair`

```json
{
  "pairing_code": "ABCD-1234",
  "device_name": "Redmi 12",
  "model": "23053RN02A",
  "android": 14
}
```

**Response (`201 Created`):**
```json
{
  "device_id": "dev_98213",
  "device_token": "sec_tok_b82a...",
  "allowed_senders": ["bKash", "NAGAD", "16216"],
  "heartbeat_seconds": 300
}
```

### 5.3 Device Heartbeat
`POST /v1/devices/heartbeat`  
**Headers:** `Authorization: Bearer <device_token>`

```json
{
  "battery": 88,
  "charging": true,
  "network": "wifi",
  "queue_depth": 0,
  "perm_sms": true,
  "battery_exempt": true,
  "app_version": "1.0.0"
}
```

---

## 6. Security & Privacy Architecture
1. **Data Minimization:** Only messages matching allowlisted financial senders and payment keywords are persisted or transmitted. Personal SMS and OTPs are discarded in the receiver without logging or storing.
2. **KeyStore Token Encryption:** Master device token is encrypted at rest using AES-GCM-256 backed by `AndroidKeyStore`.
3. **Backup Protection:** `android:allowBackup="false"` with full backup extraction exclusion rules in `backup_rules.xml` and `data_extraction_rules.xml`.
4. **Transport Security:** Cleartext HTTP traffic is disabled (`android:usesCleartextTraffic="false"`). TLS 1.2+ is strictly enforced.
5. **No PII Logging:** Phone numbers and payment details are never output to standard Android Logcat.

---

## 7. Supported Wallet SMS Formats
- **bKash Received:**
  `You have received Tk 1,200.00 from 01712750278. Fee Tk 0.00. Balance Tk 1,200.00. TrxID DJC9SZQV0J at 12/10/2026 15:24`
- **bKash Cash In:**
  `Cash In Tk 2,500.00 from 01755650768 successful. Fee Tk 0.00. Balance Tk 2,505.39. TrxID DIS133PE5L at 28/09/2026 21:59.`
- **Nagad Received:**
  `You have received Tk 850.00 from 01811223344. Balance Tk 2,400.00. TrxID 72AB3890 at 12/10/2026`
- **Rocket Received:**
  `Tk 3,100.00 received from 01999887766. TxnId 98214. Rocket Balance Tk 5,000.00`
- **Adversarial / Forged:**
  SMS sent from customer phone `01712345678` -> Automatically rejected by `SmsFilter.isAllowedSender`.
