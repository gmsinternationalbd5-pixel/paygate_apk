# Product Requirements Document (PRD) v3.0
## PayGate SMS Forwarder — Android Companion App
**Version:** 3.0 · Production Grade  
**Date:** October 13, 2026  
**Companion System:** PayGate Platform PRD v3.0 (Next.js + Supabase + Android Fleet)  
**Technology:** Kotlin + Jetpack Compose (Material 3 with Gate UI tokens)

---

## 1. Executive Summary & Role in the System
The **PayGate SMS Forwarder** runs on dedicated PayGate-owned Android phones that hold PayGate's wallet SIM cards (bKash, Nagad, and Rocket).

- **Core Mission:** Captures received-money SMS, keeps only wallet messages, saves each one to an encrypted Room outbox before any network call, and delivers it to `/v1/sms/webhook`.
- **Division of Authority:** The server parses, matches, and settles transactions; the app **never** decides whether a payment is valid or customer-confirmed.
- **Audience:** Operators and PayGate support. Merchants never install or configure this application.

### Pillars
1. **Never lose a message:** Durable local persistence before any network call; delivery retries across network drops with exponential backoff and jitter.
2. **Survive OEM battery management:** Persistent foreground service with watchdog and device-specific autostart guides.
3. **Expose nothing it does not need:** Strict sender allowlist, phone-number sender rejection, zero PII logging, and Android KeyStore hardware encryption.

---

## 2. Goals & Non-Goals

### Goals
- **Zero Message Loss:** Every qualifying wallet SMS is stored in the local encrypted database before the first network call and delivered at p95 under 5 s on a healthy connection.
- **Survivability:** Resilient against device reboots, app updates, process termination, Doze mode, and OEM battery policies (Xiaomi/MIUI, Samsung/OneUI, Oppo/ColorOS, Vivo/Funtouch, Tecno/HiOS).
- **Dual SIM Support:** SIM slot detection (slot 1, slot 2) and association with wallet operators.
- **Bilingual Support:** First-class English and Bengali (বাংলা) interface with instant in-app switching.

### Non-Goals
- Sending SMS messages, replying to customers, or initiating wallet transfers.
- Reading, storing, or uploading personal SMS, bank OTPs, or non-wallet messages.
- iOS support (Apple iOS restricts background SMS broadcast interception).
- Deciding payment validity on-device.
- Google Play distribution (distributed via MDM / private enterprise APK).

---

## 3. Personas & Needs

| Persona | Primary Needs |
|---|---|
| **Fleet Operator** | Pair a phone in under 2 minutes via QR code, glanceable green/red status per SIM, one-tap "Fix" on failing health rows. |
| **Support Agent** | Force queue sync ("Sync now"), trigger test SMS, export sanitized diagnostics without PII, inspect activity log with masked TrxID. |
| **PayGate Admin** | Real-time fleet telemetry via heartbeat, remote token revocation, remote queue flush, forced app updates, maintenance mode. |

---

## 4. Functional Requirements (FR) Traceability Matrix

| ID | Pri | Requirement Description | Implementation in Codebase |
|---|---|---|---|
| **FR-1** | **P0** | Manifest `SMS_RECEIVED` receiver; reassemble multipart PDUs; hand off with `goAsync()` plus expedited processing within 10 s. | `com.example.receiver.SmsReceiver` using `Telephony.Sms.Intents.getMessagesFromIntent` |
| **FR-2** | **P0** | Insert into Room in one transaction before any network call; failed insert raises local alert. | `com.example.data.local.db.AppDatabase` & `QueuedSmsDao` |
| **FR-3** | **P0** | Discard non-qualifying SMS immediately: no storage, no logging. | `com.example.domain.filter.SmsFilter.isWalletReceivedMessage` |
| **FR-4** | **P0** | Sender allowlist (default `bKash`, `NAGAD`, `16216`), case-insensitive and trimmed; dynamic server update (max 10 entries); reject phone-number senders (10+ digits, `+880`, `880`, `01`). | `com.example.domain.filter.SmsFilter.isAllowedSender` & `PrefManager` |
| **FR-5** | **P0** | Qualify only if incoming-money word (`received`, `Cash In`, `credited`, Bengali equivalents), amount (`Tk` or `৳`), and transaction token (`TrxID`, `TxnId`, `Txn ID`, `TransactionID` + 4–16 alphanumeric). Exclude OTP, PIN, sent, payment to, cash out, recharge. | `com.example.domain.filter.SmsFilter.isWalletReceivedMessage` |
| **FR-6** | **P0** | Extract `ref_hint`: label `Ref`, `Reference`, `Ref No` (or Bengali `রেফ`, `রেফারেন্স`) + 6–20 char token, normalized uppercase without spaces/hyphens; null if absent. Server re-parses as authority. | `com.example.domain.filter.SmsFilter.extractRefHint` |
| **FR-7** | **P0** | POST to `/v1/sms/webhook` over TLS 1.2+ with bearer token; oldest first, one request in flight, 10 s timeout. | `com.example.service.QueueSyncManager.drainQueue` |
| **FR-8** | **P0** | Exponential backoff with jitter, base 2 s, cap 15 min; reset on success or network change. | `com.example.service.QueueSyncManager.calculateBackoffMs` |
| **FR-9** | **P0** | `client_msg_id = SHA-256(sender \| body \| sms_timestamp \| sim_slot)`; server dedupes. | `com.example.domain.filter.SmsFilter.generateClientMsgId` |
| **FR-10**| **P0** | Retain until resolved: mark delivered only after 2xx; rows older than 24 h become stale, stay visible, and keep retrying. | `com.example.data.local.entity.QueuedSms` & `QueuedSmsDao` |
| **FR-11**| **P0** | Drain the queue at once on a connectivity change. | `NetworkCallback` in `PayGateForwarderService` |
| **FR-12**| **P0** | Pair by QR (manual code fallback) issued from Admin console; single-use code expires in 10 min; response lists wallet on each SIM slot; distinct errors for expired, used, invalid, offline. | `com.example.ui.screens.PairingScreen` & `AuthRepository` |
| **FR-13**| **P0** | Token in Android KeyStore (AES-GCM); cleared on unpair; rotate on 401 with valid refresh token; fail unpairs device. | `com.example.data.security.KeystoreSecretManager` |
| **FR-14**| **P0** | Foreground service of type `dataSync` with ongoing low-priority notification; shows SIM slots, forwarder state, queue depth, last forward time. | `com.example.service.PayGateForwarderService` |
| **FR-15**| **P0** | Restart foreground service on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, process resurrection. | `com.example.receiver.BootReceiver` |
| **FR-16**| **P0** | Battery optimization exemption prompt during pairing; inline guide per OEM. | `com.example.ui.screens.SettingsScreen` & OEM guides |
| **FR-17**| **P0** | Heartbeat every 5 min to `POST /v1/devices/heartbeat`: battery, charging, network, queue depth, permission states, app version, sim slots; 3 consecutive failures turns indicator amber. | `PayGateForwarderService.sendHeartbeatInternal` |
| **FR-18**| **P0** | Remote commands in heartbeat response: `force_sync`, `wipe_queue`, `update_allowlist`, `re_pair`, `set_maintenance`. | `PayGateForwarderService.handleHeartbeatCommands` |
| **FR-19**| **P1** | In-app SMS simulator: preset genuine bKash, bKash Cash In, Nagad, Rocket, adversarial forged SMS, custom SMS injector. | `com.example.ui.screens.HomeScreen` Test SMS Simulator |
| **FR-20**| **P1** | Activity log up to 500 items: sender, masked TrxID (`••••V0J`), timestamp, delivery state, latency, filter/reject reason; reveal toggle behind PIN. | `com.example.ui.screens.ActivityLogScreen` |
| **FR-21**| **P1** | High-priority local alerts: stuck queue (>5 min on healthy network), revoked token (401), low battery (<15% unplugged), SMS permission lost. | `PayGateForwarderService.showLocalAlertNotification` |
| **FR-22**| **P1** | Diagnostics export: shareable JSON/text file containing device model, OS version, SIM carriers, queue counts, last 20 log entries with masked TrxID and zero SMS bodies. | `com.example.ui.screens.ActivityLogScreen` Export Diagnostics |
| **FR-23**| **P1** | Maintenance mode toggle: pauses forwarding while continuing local queueing; visual yellow badge. | `PrefManager.isMaintenanceMode` & `QueueSyncManager` |
| **FR-24**| **P2** | Storage monitor: alert if free space < 100 MB; auto-purge delivered messages older than 30 days. | `AppDatabase.cleanDeliveredOlderThan` |
| **FR-25**| **P2** | Optional Kiosk lock mode: prevents accidental app exit or unauthorized tampering. | `SettingsScreen` Kiosk Toggle |
| **FR-26**| **P2** | Auto-update checker: notifies operator of new enterprise APK releases from Admin console. | `VersionChecker` & Heartbeat telemetry |

---

## 5. Security & Privacy Architecture

- **Token Storage:** Hardware-backed Android KeyStore AES-256-GCM encryption. Never written to logs, SharedPreferences, or external storage.
- **Backup Exclusion:** `data_extraction_rules.xml` and `backup_rules.xml` explicitly prohibit cloud and device-to-device backup of database and credentials.
- **Data Minimization:** Only qualifying wallet SMS messages are captured. Personal SMS, bank OTPs, and private messages are dropped immediately in memory without storage or logging.
- **TrxID Masking:** User interface masks transaction IDs (`••••V0J`) by default. Unmasking requires deliberate user action and optional security PIN.
- **TLS Enforcement:** Strict TLS 1.2+ HTTPS communication to the webhook with authorization header bearer authentication.
