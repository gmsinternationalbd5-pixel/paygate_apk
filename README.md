# PayGate SMS Forwarder (পেগেট ফরোয়ার্ডার) — v3.0

[![Android](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84.svg?style=flat&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin_2.2-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Material3](https://img.shields.io/badge/Design-Gate_UI_Tokens-5B5BF0.svg?style=flat)](https://m3.material.io)
[![Room](https://img.shields.io/badge/Storage-Room_SQLite_Encrypted-FF6F00.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![Security](https://img.shields.io/badge/Security-Android_KeyStore_AES--GCM-00C853.svg?style=flat)](https://developer.android.com/training/articles/keystore)

Production-grade Android companion application for the **PayGate Platform PRD v3.0** (Next.js + Supabase + Android Fleet). The app operates on PayGate-owned Android devices holding PayGate wallet SIM cards (bKash, Nagad, and Rocket), captures received-money SMS, saves every qualifying candidate message to an encrypted outbox before any network transmission, and delivers it to `/v1/sms/webhook` with p95 delivery under 5 seconds.

---

## 🎯 Core Pillars

1. **Never Lose a Message:** Durable local storage in an encrypted Room SQLite outbox before any network request; exponential backoff retry with jitter across network drops and server outages.
2. **Survive OEM Battery Management:** Persistent foreground service (`dataSync`) with watchdog, boot autostart, and step-by-step device-specific OEM guides.
3. **Expose Nothing It Does Not Need:** Strict sender allowlist (`bKash`, `NAGAD`, `16216`), instant 11-digit mobile sender rejection, zero PII logging, hardware-backed Android KeyStore AES-GCM encryption, and mask-by-default TrxIDs.

---

## 🚀 Key Features in v3.0

- **🛡️ Multi-Layer Forgery Protection (FR-4, FR-5):**
  - Strict sender allowlist (default `bKash`, `NAGAD`, `16216`) with dynamic server updates (up to 10 entries).
  - Unconditional rejection of 10+ digit phone numbers, `+880`, `880`, and `01` senders.
  - Verification of incoming-money keywords (`received`, `Cash In`, `credited`, or Bengali `পেয়েছেন`, `ক্যাশ ইন`), valid amounts (`Tk` or `৳`), and transaction tokens (`TrxID`, `TxnId`, `Txn ID`, `TransactionID` with 4–16 alphanumerics).
  - Disqualification of OTPs, PINs, sent money, cash outs, and recharges.

- **🔍 Intelligent Reference Hint Extraction (FR-6):**
  - Extracts payment references labeled `Ref`, `Reference`, `Ref No` or Bengali `রেফ`, `রেফারেন্স` followed by 6–20 character tokens (e.g., `ORDER99X`, `REF934P9`, `BK991122`).
  - Automatically normalized to uppercase without whitespace or hyphens.
  - References are never mandatory, ensuring money is never dropped for lacking one.

- **💾 Offline Durability & Outbox Queue (FR-2, FR-7, FR-10):**
  - Messages saved to Room database in a single transaction before any network attempt.
  - Oldest-first FIFO delivery over TLS 1.2+ with bearer token authentication.
  - Messages older than 24 hours become marked as `STALE`, remain visible in the queue, and continue retrying.
  - Automatic queue drain immediately upon connectivity restoration.

- **🔑 Hardware-Backed Android KeyStore Vault (FR-13):**
  - Pairing bearer tokens stored using `AES/GCM/NoPadding` inside Android KeyStore.
  - Zero plaintext secrets on flash or memory dumps.
  - Dynamic token rotation via heartbeat (`rotate_token`).
  - Immediate local purge upon unpair or remote revocation.

- **⚡ Foreground Background Service (FR-14, FR-15):**
  - Runs as an ongoing `dataSync` foreground service with a low-priority notification showing SIM slots, forwarder health, queue depth, and last forward time.
  - Auto-restarts on `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, and `MY_PACKAGE_REPLACED`.

- **📡 Real-Time Fleet Telemetry & Heartbeat (FR-17, FR-18):**
  - Pings `POST /v1/devices/heartbeat` every 120 seconds (server-configurable) + WorkManager 15 min fallback.
  - Reports battery percentage, charging state, network type (Wi-Fi vs Cellular), queue depth, stale count, SMS and notification permission statuses, battery optimization exemption, clock skew, free storage (MB), and SIM states.
  - Executes remote admin commands: `FLUSH_QUEUE`, `WIPE_QUEUE`, `UPDATE_ALLOWLIST`, `ROTATE_TOKEN`, `FORCE_UPDATE`, and `SET_MAINTENANCE`.

- **🔒 Enterprise Security & Privacy Controls (FR-19, FR-20, FR-24):**
  - Mandatory 4-digit operator PIN for Settings, unpairing, and unmasking sensitive TrxIDs.
  - Optional Android Enterprise Kiosk mode locking navigation and system settings.
  - Local high-priority alerts for stuck queues (>10 min), revoked credentials, low storage (<200MB), and lost SMS permissions.
  - Cloud and device-to-device backup explicitly disabled in `data_extraction_rules.xml` and `backup_rules.xml`.

- **🧪 In-App SMS Simulator & Diagnostic Tools (FR-19, FR-22):**
  - On-device test panel supporting genuine bKash Received, bKash Cash In, Nagad, Rocket, and adversarial spoofing attempts.
  - Sanitized diagnostics export with zero PII (masked TrxIDs, zero SMS bodies) for support escalation.

- **🌐 Gate UI & Bilingual Support (FR-29):**
  - Modern Gate UI design system tokens (monochrome surfaces, `#5B5BF0` primary accent, clear status pill badges).
  - 100% complete English and Bengali (বাংলা) interface with instant in-app language switching.

---

## 🏗️ Architecture & Processing Pipeline

```text
[ Incoming SMS Broadcast (Telephony.Sms.Intents) ]
                      │
                      ▼
             [ SmsReceiver.goAsync() ]
             (Multipart PDU Reassembly)
                      │
                      ▼
                [ SmsFilter ]
     ┌────────────────┴────────────────┐
     ▼                                 ▼
[ Non-Wallet / OTP / Spoofed ]    [ Valid Wallet SMS ]
- 10+ digit sender numbers        - Allowlisted sender (bKash, NAGAD, 16216)
- Contains OTP/PIN/sent words     - Incoming money keyword (received, Cash In)
- Dropped: No storage, no log     - Tk/৳ amount + TrxID token
                                  - Extracted ref_hint (optional)
                                       │
                                       ▼
                       [ AppDatabase (Room + Encrypted) ]
                       Insert into QueuedSms (Status: PENDING)
                       SHA-256 client_msg_id generated
                                       │
                                       ▼
                       [ PayGateForwarderService (dataSync) ]
                                       │
                                       ▼
                            [ QueueSyncManager ]
                       POST /v1/sms/webhook (Oldest First)
                                       │
               ┌───────────────────────┼───────────────────────┐
               ▼                       ▼                       ▼
          [ HTTP 200 ]            [ HTTP 401/403 ]        [ 429 / 5xx / Offline ]
          - Mark DELIVERED        - Pause delivery        - Exponential backoff
          - Record latency        - Keep queue intact     - Jittered retry (2s to 15m)
          - Prune after 7 days    - Show re-pair screen   - Drain immediately on reconnect
                                  - Local urgent alert
```

---

## 📱 Supported SMS Formats & Test Scenarios

### 1. bKash "Received" with Reference
```text
You have received Tk 1,200.00 from 01712750278. Fee Tk 0.00. Balance Tk 1,200.00. TrxID DJC9SZQV0J and Ref REF934P923 at 12/10/2026 15:24
```
*App Action:* Forwarded to webhook. `ref_hint` extracted as `"REF934P923"`.

### 2. bKash "Cash In" Format
```text
Cash In Tk 2,500.00 from 01755650768 successful. Fee Tk 0.00. Balance Tk 2,505.39. TrxID DIS133PE5L at 28/09/2026 21:59.
```
*App Action:* Forwarded to webhook. `ref_hint` is `null`.

### 3. Nagad Received Format
```text
You have received Tk 850.00 from 01811223344. Balance Tk 2,400.00. TrxID 72AB3890 at 12/10/2026
```
*App Action:* Forwarded to webhook.

### 4. Rocket (16216) Format
```text
Tk 3,100.00 received from 01999887766. TxnId 98214. Rocket Balance Tk 5,000.00
```
*App Action:* Forwarded to webhook.

### 5. Adversarial / Spoofed Phone Number Attempt
```text
Sender: +8801712345678 or 01712345678
Body: You have received Tk 5,000.00 from 01711112222. TrxID FAKE12345
```
*App Action:* **Rejected instantly**. Phone number senders are permanently blocked. No storage, no upload.

---

## 🔌 API Specifications

Every request carries TLS 1.2+ encryption and standard headers:
- `Authorization: Bearer <device_token>`
- `X-Device-Id: <device_id>`
- `X-App-Version: 3.0.0`
- `X-Payload-Version: 3`

### 1. Forward SMS Webhook (`POST /v1/sms/webhook`)
```json
{
  "payload_version": 3,
  "sender": "bKash",
  "body": "You have received Tk 1,200.00 from 01712750278. Fee Tk 0.00. Balance Tk 1,200.00. TrxID DJC9SZQV0J and Ref REF934P923 at 12/10/2026 15:24",
  "client_msg_id": "8f4a13b5c7...64hex",
  "device_seq": 1042,
  "received_at": 1760282640,
  "sim_slot": 1,
  "ref_hint": "REF934P923",
  "parser_version": "app-3.0.0",
  "source": "live",
  "app_version": "3.0.0"
}
```

### 2. Device Pairing (`POST /v1/devices/pair`)
```json
{
  "pairing_code": "ABCD-1234",
  "device_name": "Fleet-Phone-01",
  "model": "Redmi Note 12",
  "android": "14",
  "app_version": "3.0.0"
}
```

### 3. Fleet Telemetry Heartbeat (`POST /v1/devices/heartbeat`)
```json
{
  "battery": 88,
  "charging": true,
  "network": "wifi",
  "queue_depth": 0,
  "stale_count": 0,
  "failed_count": 0,
  "perm_sms": true,
  "perm_notifications": true,
  "battery_exempt": true,
  "autostart_confirmed": true,
  "clock_skew_ms": 120,
  "free_storage_mb": 8200,
  "sim_state": [
    { "slot": 1, "present": true },
    { "slot": 2, "present": true }
  ],
  "ref_seen_count": 42,
  "no_ref_count": 1,
  "last_sms_at": 1760282640,
  "device_seq": 1042,
  "app_version": "3.0.0"
}
```

---

## 🔋 OEM Battery Management Setup Guide

To guarantee 24/7 background survivability against aggressive vendor power management:

- **Xiaomi / Redmi / POCO (MIUI / HyperOS):**
  - `Settings` -> `Apps` -> `Manage Apps` -> `PayGate Forwarder` -> Enable **Autostart**.
  - Set **Battery saver** to **No restrictions**.
  - In Recent Apps screen: Long press app card -> Tap the **Lock icon**.
- **Samsung (OneUI):**
  - `Settings` -> `Battery` -> `Background usage limits` -> Add PayGate Forwarder to **Never sleeping apps**.
  - Disable "Put unused apps to sleep".
- **Realme / Oppo (ColorOS):**
  - `Settings` -> `App Management` -> `PayGate Forwarder` -> Enable **Allow background activity** and **Auto-launch**.
- **Vivo / iQOO (FuntouchOS):**
  - `Settings` -> `Battery` -> `High background power consumption` -> Enable PayGate Forwarder.
- **Tecno / Infinix (HiOS / XOS):**
  - `Phone Master` -> `Auto-start management` -> Enable PayGate Forwarder.

---

## 🧪 Testing & Verification

Execute all unit and Robolectric test suites:

```bash
gradle :app:testDebugUnitTest
```

### Verified Test Suites
- `SmsFilterTest`: Validates regex filters, `ref_hint` parser, Bengali SMS patterns, 11-digit phone number rejection, and `client_msg_id` SHA-256 idempotency.
- `ExampleRobolectricTest`: Verifies app name, resource loading, and application context stability.

---

## 📄 License & Confidentiality
Confidential & Proprietary. Companion software for PayGate platform operations.
