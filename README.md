# PayGate SMS Forwarder (পেগেট ফরোয়ার্ডার)

[![Android](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84.svg?style=flat&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin_2.2-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Storage-Room_SQLite-FF6F00.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![Security](https://img.shields.io/badge/Security-Android_KeyStore_AES--GCM-00C853.svg?style=flat)](https://developer.android.com/training/articles/keystore)

Production-grade Android companion application for the **PayGate** merchant payment gateway (Next.js + Supabase). The app monitors wallet SMS on merchant SIM cards (bKash, Nagad, Rocket), validates genuine payment receipts, and delivers them reliably to the PayGate server webhook within seconds.

---

## 🚀 Key Features

- **🛡️ Strict Forgery Protection:** Enforces an on-device sender allowlist (`bKash`, `NAGAD`, `16216`). Rejects regular 11-digit mobile numbers (`017xxxxxxxx`) automatically to prevent fraudulent payment confirmations.
- **💾 Offline Durability & Zero Loss:** Every qualifying candidate message is written to a local Room SQLite outbox database before any network request is attempted.
- **🔄 Resilient Exponential Backoff:** Retries undelivered messages with exponential backoff and jitter (5s, 10s, 20s... up to 15m) across weak network connections and server downtime.
- **🔑 Android KeyStore Vault:** Device authentication tokens are encrypted at rest with hardware-backed AES-GCM (`AndroidKeyStore`) and never stored in plain text or exported into backups.
- **⚡ Foreground Background Service:** Runs as a persistent `dataSync` foreground service with `BOOT_COMPLETED` restart and automatic queue drain.
- **📡 Automated Heartbeat Telemetry:** Pings the server every 5 minutes with device battery level, charging status, network type (Wi-Fi vs Cellular), queue depth, and permission states.
- **🧪 Built-in SMS & Attack Simulator:** Test incoming bKash, bKash Cash In, Nagad, Rocket, and adversarial spoofing attempts directly from the Home screen without sending real SMS.
- **🌐 Bilingual UI:** Complete English and Bengali (বাংলা) interface with instant in-app toggling.
- **🔒 Privacy by Design:** Only financial payment messages are parsed. Personal SMS, OTPs, and non-wallet texts are immediately discarded without logging.

---

## 🏗️ Architecture Overview

```text
[ Incoming SMS Broadcast ]
           │
           ▼
   [ SmsReceiver ]  ──( Multipart PDU Reassembly )
           │
           ▼
    [ SmsFilter ]
    ├── Allowlist Check (bKash, NAGAD, 16216)
    ├── Reject 11-digit senders (017xxxxxxxx)
    └── Regex Validation (Amount + TrxID + 'received' / 'Cash In')
           │ (Valid wallet receipt)
           ▼
   [ Room Database ]  ──( QueuedSms: status=PENDING, client_msg_id=SHA-256 )
           │
           ▼
[ PayGateForwarderService ] ──( Foreground Service 'dataSync' )
           │
           ▼
  [ QueueSyncManager ]
           │
           ├── Post to POST /v1/sms/webhook (Bearer Device Token)
           ├── HTTP 200: Mark DELIVERED + record latency
           ├── HTTP 401: Alert merchant (Device Revoked)
           └── HTTP 429/5xx: Exponential Backoff & Retry
```

---

## 📱 Supported SMS Formats

The app natively recognizes and parses standard Bangladeshi MFS formats:

### 1. bKash "Received" Format
```text
You have received Tk 1,200.00 from 01712750278. Fee Tk 0.00. Balance Tk 1,200.00. TrxID DJC9SZQV0J at 12/10/2026 15:24
```

### 2. bKash "Cash In" Format
```text
Cash In Tk 2,500.00 from 01755650768 successful. Fee Tk 0.00. Balance Tk 2,505.39. TrxID DIS133PE5L at 28/09/2026 21:59.
```

### 3. Nagad Format
```text
You have received Tk 850.00 from 01811223344. Balance Tk 2,400.00. TrxID 72AB3890 at 12/10/2026
```

### 4. Rocket (16216) Format
```text
Tk 3,100.00 received from 01999887766. TxnId 98214. Rocket Balance Tk 5,000.00
```

---

## 🔌 API Endpoints

### 1. Forward SMS Webhook
`POST /v1/sms/webhook`  
**Headers:** `Authorization: Bearer <device_token>`, `Content-Type: application/json`

```json
{
  "sender": "bKash",
  "body": "You have received Tk 1,200.00 from 01712750278. Fee Tk 0.00. Balance Tk 1,200.00. TrxID DJC9SZQV0J at 12/10/2026 15:24",
  "client_msg_id": "8f4a13b5c7...64hex",
  "received_at": 1760000000,
  "sim_slot": 1,
  "app_version": "1.0.0"
}
```

### 2. Device Pairing
`POST /v1/devices/pair`

```json
{
  "pairing_code": "ABCD-1234",
  "device_name": "Redmi 12",
  "model": "23053RN02A",
  "android": 14
}
```

### 3. Telemetry Heartbeat
`POST /v1/devices/heartbeat`  
**Headers:** `Authorization: Bearer <device_token>`

```json
{
  "battery": 82,
  "charging": true,
  "network": "wifi",
  "queue_depth": 0,
  "perm_sms": true,
  "battery_exempt": true,
  "app_version": "1.0.0"
}
```

---

## 🔋 OEM Battery Management Setup

To prevent OEM Android skins from killing background services:

- **Xiaomi / Redmi / POCO (MIUI / HyperOS):**  
  `Settings` -> `Apps` -> `Manage Apps` -> `PayGate Forwarder` -> Enable **Autostart** and set **Battery saver** to **No restrictions**.
- **Samsung (OneUI):**  
  `Settings` -> `Battery` -> `Background usage limits` -> Add PayGate Forwarder to **Never sleeping apps**.
- **Realme / Oppo (ColorOS):**  
  `Settings` -> `App Management` -> `Auto-launch` -> Enable PayGate Forwarder. Allow Background Activity.
- **Vivo / iQOO (FuntouchOS):**  
  `Settings` -> `Battery` -> `High background power consumption` -> Enable PayGate Forwarder.
- **Tecno / Infinix (HiOS / XOS):**  
  `Phone Master` -> `Auto-start management` -> Enable PayGate Forwarder.

---

## 🧪 Testing & Verification

Run local JVM unit and Robolectric tests:

```bash
gradle :app:testDebugUnitTest
```

### In-App SMS Simulator
On the **Home** tab, tap **"Test SMS Simulator"** to inject preset genuine and adversarial messages into the pipeline:
1. `bKash Recv` (Tests received payment format)
2. `bKash Cash In` (Tests cash-in format)
3. `Nagad` & `Rocket` (Tests provider regex)
4. `⚠️ Forge Test (11-digit)` (Confirms that fake SMS from mobile numbers are safely blocked)
5. Instant Connect (Emulator Test Mode) allows local pairing without a live server.

---

## 📄 License
Internal proprietary companion app for PayGate.
