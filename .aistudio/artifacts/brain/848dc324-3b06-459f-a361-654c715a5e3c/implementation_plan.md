# PayGate SMS Forwarder v2.0 (PRD v3.0)

A production-grade, fleet-hardened Android companion application for PayGate-owned mobile devices running wallet SIMs (bKash, Nagad, Rocket). Upgrades the forwarding engine with strict Reference Hint extraction, multi-SIM wallet registry mapping, Gate UI design tokens, hardware-backed Keystore & encrypted database storage, expedited WorkManager handoffs, and real-time fleet telemetry.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following operational and security defaults have been aligned based on user clarification:
> - **Security & Action Protection:** Operator setup with optional PIN protection (4-digit security PIN protecting Settings, unpair actions, and 10-second message reveal).
> - **Fleet Deployment Mode:** Standard fleet operator mode with an optional kiosk lock toggle for dedicated unattended devices.
> - **Development & Diagnostics Mode:** Embedded simulated mock server responding with instant 200 OK for live emulator testing and rapid validation alongside live HTTPS webhooks.
> - **Reference Hint Constraint:** Standardized reference extraction targeting 8-character alphanumeric tokens (`length 8`, e.g., `REF934P9`), normalized to uppercase without hyphens or spaces.
> - **Merchant Business Context:** 5% fee model, 1,000 BDT minimum payout threshold via bank or MFS, server-side data residency in regional cloud (Singapore `asia-southeast1`).

---

## 1. Overview & Core Concept

- **What It Does:** The app runs exclusively on PayGate-owned Android devices holding merchant wallet SIMs. It intercepts incoming SMS via a high-priority broadcast receiver, immediately filters out all non-wallet and forged messages, extracts transaction IDs and reference hints (`ref_hint`), and writes every candidate message to an encrypted outbox before making any network request. A resilient foreground service delivers messages to `/v1/sms/webhook` with monotonic device sequence numbers (`device_seq`).
- **Target Audience / Persona:** 
  - *Fleet Operator:* Pairs phones in under 2 minutes via QR code, monitors SIM wallet health, fixes failing settings in one tap.
  - *Support Agent:* Diagnoses queues, triggers manual syncs, tests simulated receipts, and inspects activity logs with 10-second masked reveals.
  - *PayGate Admin:* Oversees fleet health, sends remote commands (token rotation, maintenance mode, flush queue), and tracks reference coverage.
- **Key Value:** Absolute message durability (zero message loss), aggressive survival across OEM battery killers (Xiaomi, Samsung, Realme, Oppo, Vivo, Tecno), and strict anti-forgery isolation.

---

## 2. User Experience & Visual Design (Gate UI)

The UI adheres strictly to the **Gate UI design system** with fixed branding (dynamic color disabled):

### Visual Identity & Theme Tokens
- **Backgrounds:** Light `#FAFAF9` (`bg`), Dark `#0B0B0E`.
- **Surfaces & Cards:** Light `#FFFFFF` / `#F4F4F2` (`surface` / `surface-2`), Dark `#141418` / `#1C1C22`.
- **Borders & Dividers:** Hairline borders `#E7E7E3` (Light) and `#2A2A31` (Dark) before shadows.
- **Ink & Typography:** Primary text `#0E0E11` (Light) and `#F5F5F7` (Dark); secondary `#55555E` / `#A4A4AE`.
- **Gate Accent:** `#5B5BF0` (Light), `#8B8BFF` (Dark) for interactive buttons and focus states.
- **Status Indicators:** Success `#12B76A` (`#32D583`), Warning `#F79009` (`#FDB022`), Error/Danger `#F04438` (`#F97066`).
- **Monospace Tokens:** TrxID, Reference code, and hashes rendered in clean monospace font.

### Key Screens & Navigation
1. **Welcome Screen:** PayGate mark, bilingual greeting, language toggle (EN / বাংলা), and "Get Started" onboarding entry.
2. **Setup & Health Checklist:** Progress indicator with expandable `HealthRow` items (SMS permission, Notifications, Battery exemption, OEM Auto-start, Background data, Lock in recents, SIM wallet mapping). Each row includes an inline **Fix** button.
3. **Pairing Screen:** QR code camera viewfinder with laser scan animation, fallback manual code input, error banners (expired, used, invalid, offline), and wallet-per-SIM confirmation card.
4. **Home Screen:** Live status card per SIM slot and mapped wallet, Stat Cards (Queue Depth, Stale Count, Delivered Count), Last Payment card with `ReferenceChip`, "Sync Now", and "Test Simulator".
5. **Activity Log Screen:** Filter tabs (All, Pending, Failed, Delivered), masked TrxID (`••••V0J`) with 10-second auto-remask reveal timer, reference badge, and retry triggers.
6. **Settings Screen:** Language switcher (English / বাংলা), 4-digit App PIN configuration, Backfill consent dialog, OEM optimization guides, Diagnostic export, and Kiosk Lock mode toggle.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Reference Hint Extraction (FR-6)**
  - *Chosen Approach:* Extract `ref_hint` using flexible prefix matching (`Ref`, `Reference`, `Ref No`, or Bengali `রেফ`) followed by an alphanumeric string normalized to uppercase (target length 8).
  - *Why:* Enables high-speed automated payment reconciliation while keeping the server as the ultimate parsing authority.
  - *Alternative Considered:* Mandatory reference rule — rejected because genuine customer money must never be dropped if a customer omitted the reference.

- **Decision 2: Local Encryption & Zero Network-First Writes (FR-2, FR-13)**
  - *Chosen Approach:* Every candidate message is committed to Room storage with hardware-backed Android KeyStore encryption prior to network dispatch.
  - *Why:* Prevents message loss in the event of an instantaneous app kill, out-of-memory event, or cellular radio drop.

- **Decision 3: Dual-SIM Wallet Registry Enforcement (FR-15)**
  - *Chosen Approach:* The pairing response binds specific wallet providers and masked phone numbers to SIM Slot 1 and SIM Slot 2. If an SMS arrives on an unmapped SIM slot, forwarding is paused and a local alert is raised.
  - *Why:* Prevents misattributing funds or processing transactions on unauthorized secondary SIM cards.

- **Decision 4: Heartbeat Remote Command Control (FR-21, FR-27)**
  - *Chosen Approach:* Heartbeat payload sent every 120 seconds. The server response can return remote command instructions (`rotate_token`, `flush_queue`, `update_allowlist`, `maintenance_mode`).
  - *Why:* Allows central fleet management of locked unattended phones without requiring physical access.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        ANDROID OS & TELEPHONY                          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ SMS Broadcast (PDUs)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        RECEIVER & FILTER LAYER                         │
│  ┌───────────────────────┐         ┌────────────────────────────────┐  │
│  │   SmsReceiver         │────────▶│  SmsFilter (Anti-Forgery)      │  │
│  │   - Multipart reasm.  │         │  - Allowlist (bKash, NAGAD...) │  │
│  │   - goAsync() handoff │         │  - Reject 11-digit numbers     │  │
│  │   - SIM slot lookup   │         │  - Extract TrxID & ref_hint (8)│  │
│  └───────────────────────┘         └───────────────┬────────────────┘  │
└────────────────────────────────────────────────────┼───────────────────┘
                                                     │ Valid Wallet SMS
                                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        PERSISTENCE & SECURITY                          │
│  ┌─────────────────────────────────┐   ┌────────────────────────────┐  │
│  │  AppDatabase (Room SQLite)       │◀──│  KeystoreSecretManager     │  │
│  │  - Table: queued_sms            │   │  - AES-256-GCM Hardware    │  │
│  │  - Monotonic device_seq         │   │  - Token Rotation Support  │  │
│  │  - client_msg_id (SHA-256)      │   └────────────────────────────┘  │
│  └────────────────┬────────────────┘                                   │
└───────────────────┼────────────────────────────────────────────────────┘
                    │ Trigger Drain
                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        SERVICE & DELIVERY LAYER                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  PayGateForwarderService (Foreground Service 'dataSync')         │  │
│  │  - WorkManager expedited task fallback                           │  │
│  │  - BootReceiver (BOOT_COMPLETED, LOCKED_BOOT_COMPLETED)          │  │
│  │  - 120s Heartbeat Engine with Remote Command Execution           │  │
│  └────────────────────────────────┬─────────────────────────────────┘  │
└───────────────────────────────────┼────────────────────────────────────┘
                                    │ HTTPS Webhook (v3)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      PAYGATE PLATFORM WEBHOOK                          │
│  POST /v1/sms/webhook (device_seq, ref_hint, client_msg_id, sim_slot)  │
│  POST /v1/devices/pair (wallet mappings per slot)                      │
│  POST /v1/devices/heartbeat (telemetry, queue depth, remote commands)   │
└────────────────────────────────────────────────────────────────────────┘
```

### Data Models & Schemas

1. **`QueuedSms` Entity (Room Database):**
   - `id`: Long (Primary Key)
   - `clientMsgId`: String (SHA-256 unique index over `sender|body|receivedAt|simSlot`)
   - `deviceSeq`: Long (Monotonically incrementing sequence number)
   - `sender`: String (`bKash`, `NAGAD`, `16216`)
   - `body`: String (Reassembled raw SMS text)
   - `receivedAt`: Long (Epoch milliseconds)
   - `simSlot`: Int (1 or 2)
   - `refHint`: String? (Normalized 8-character reference token, e.g., `REF934P9`)
   - `trxId`: String? (Parsed transaction token)
   - `amount`: String? (Parsed currency amount, e.g., `Tk 1,200.00`)
   - `provider`: String (`bKash`, `Nagad`, `Rocket`)
   - `status`: String (`PENDING`, `DELIVERED`, `IGNORED`, `STALE`, `FAILED_PERMANENT`, `AUTH_PAUSED`)
   - `attemptCount`: Int
   - `lastAttemptAt`: Long?
   - `deliveredAt`: Long?
   - `source`: String (`live` or `backfill`)

2. **`PreferenceManager` & Secure Vault:**
   - Hardware-encrypted `device_token` with automatic rotation support from heartbeat responses.
   - `walletRegistry`: JSON-encoded mapping of `{ slot: 1, provider: "bkash", numberMasked: "017•••••78" }`.
   - Security PIN hash (Argon2 or PBKDF2/SHA-256) for protecting administrative actions.
   - Kiosk mode active toggle.

3. **`WebhookRequest` (Version 3 API Payload):**
   ```json
   {
     "payload_version": 3,
     "sender": "bKash",
     "body": "You have received Tk 1,200.00 from 01712750278. Fee Tk 0.00. Balance Tk 1,200.00. TrxID DJC9SZQV0J and Ref REF934P923 at 12/10/2026 15:24",
     "client_msg_id": "8f4a13b5c7...64hex",
     "device_seq": 1042,
     "received_at": 1760282640,
     "sim_slot": 1,
     "ref_hint": "REF934P9",
     "parser_version": "app-3.0.0",
     "source": "live",
     "app_version": "3.0.0"
   }
   ```

4. **`HeartbeatRequest` & `HeartbeatResponse`:**
   - Reports `battery`, `charging`, `network`, `queue_depth`, `stale_count`, `failed_count`, `perm_sms`, `battery_exempt`, `clock_skew_ms`, `free_storage_mb`, `sim_state`, `ref_seen_count`, `no_ref_count`, `device_seq`.
   - Response handles `allowed_senders`, `rotate_token`, `min_app_version`, `maintenance`, and `commands` array (`FLUSH_QUEUE`, `REVOKE_TOKEN`, `RESEND_STALE`).

---

## 5. Implementation Stages & Verification Plan

1. **Stage 1: Core Engine Upgrade (FR-1 to FR-11)**
   - Update `SmsFilter` with 8-character reference hint extraction and Bengali keyword coverage.
   - Update `QueuedSms` and `QueuedSmsDao` with `device_seq`, `ref_hint`, `source`, and stale status tracking.
   - Implement exponential backoff schedule (base 2s, max 15m, jitter).
   - Build API v3 payloads and headers (`X-Device-Id`, `X-App-Version`, `X-Payload-Version: 3`).

2. **Stage 2: Pairing & Wallet Registry (FR-12 to FR-16)**
   - Update pairing models to store registered wallet configurations per SIM slot.
   - Enforce SIM wallet validation before queue dispatch.
   - Implement token rotation mechanism via heartbeat.

3. **Stage 3: Gate UI & Setup Checklist (FR-17 to FR-20, FR-29)**
   - Implement Gate UI tokens (`Color.kt`, `Theme.kt`, `Type.kt`).
   - Build the interactive Setup Checklist with inline **Fix** action buttons.
   - Build App PIN lock dialog for Settings, reveal actions, and unpairing.
   - Add Kiosk Mode toggle.

4. **Stage 4: Telemetry & Activity Log (FR-21 to FR-28)**
   - Implement 120-second heartbeat engine with telemetry metrics and remote command processor.
   - Build Activity Log screen with 10-second auto-remasking countdown.
   - Implement consent-gated 24h backfill.

5. **Stage 5: Verification & Testing**
   - Unit tests covering all SMS test fixtures (bKash with reference, bKash Cash In, Nagad, Rocket, OTP rejection, 11-digit forge rejection).
   - Verify deterministic `client_msg_id` calculation with `sim_slot`.
   - Robolectric testing and build compilation verification.
