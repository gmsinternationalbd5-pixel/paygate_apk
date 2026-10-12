Gate UI Design System & PayGate Platform Architecture (DESIGN.md)
Product: PayGate Platform — Bangladeshi Merchant MFS Payment Gateway SaaS
Version: 3.1
Design System: Gate UI Design System
Date: October 2026
Status: Production Architecture & Design Specification
1. Executive Summary & Philosophy
Gate UI is the unified, calm, precise design system engineered for the PayGate Merchant SaaS and its mobile client endpoints.
Core Principles
Calm, Precise, Fast: A quiet canvas (#FAFAF9 light / #0B0B0E dark), hairlines instead of heavy drop shadows, and high-density tabular numbers.
Domain-Native Authenticity: Specifically crafted for Bangladeshi commerce—incorporating native bKash, Nagad, and Rocket branding with subtle brand dots rather than garish full-fill pills.
Typography-First Clarity: Pairing crisp geometric sans (Inter) with tabular monospace (JetBrains Mono) and native bilingual typography (Noto Sans Bengali with +10% line-height compensation).
Zero-Fluff Discipline: No decorative pulsing status dots on static labels, no artificial scoreboards, and no unwired controls.
2. Design Tokens & Palette Specifications
Gate UI tokens are centralized in src/tokens.json and mirrored in CSS variables and Tailwind utilities:
2.1 Theme Palette
Token	Light Value	Dark Value	Purpose / Usage
--bg	#FAFAF9	#0B0B0E	Master application background
--surface	#FFFFFF	#141418	Primary cards, modals, table surfaces
--surface-2	#F4F4F2	#1C1C22	Inputs, table headers, hover highlights
--border	#E7E7E3	#2A2A31	1px hairline borders
--ink	#0E0E11	#F5F5F7	Primary text, primary CTA buttons
--ink-2	#55555E	#A4A4AE	Secondary text, subtle descriptions
--ink-3	#8A8A94	#6F6F7A	Placeholder text, captions, timestamps
--accent	#5B5BF0	#8B8BFF	Interactive links, active states, focus ring
--accent-soft	#EEEEFF	#23234A	Reference chip background, active tab pill
--success	#12B76A	#32D583	Verified payment, online status, KYB badge
--warning	#F79009	#FDB022	Needs review, limit warnings, pending payouts
--danger	#F04438	#F97066	Rejected, expired, error alerts
2.2 Provider Brand Accents
Per Gate UI specifications, provider colors are restricted strictly to indicators and brand dots (never overwhelming full-surface button fills):
bKash: #E2136E
Nagad: #F6921E
Rocket: #8C3494
3. Typographic Hierarchy & Bilingual Rules
Gate UI strictly honors the 2+1 Font Rule:
Primary Interface Font: Inter
Weight spectrum: Regular (400), Medium (500), SemiBold (600), Bold (700).
Display: 32px / 40px line-height.
Section Headings: 20px / 28px.
Body Prose: 14px / 22px (desktop), 15px / 24px (mobile).
Bengali Typography: Noto Sans Bengali
Automatically engaged when language toggle is set to বাংলা (.lang-bn).
Line-height increased by +10% (line-height: 1.65) to prevent glyph clipping on complex conjuncts and diacritics.
Data & Telemetry Font: JetBrains Mono with tabular-nums
Strictly enforced on all:
Transaction IDs (TrxID)
8-Character payment references (K7M4X2QH)
Financial amounts in Taka (Tk 1,200.00)
API Keys & HMAC signatures
Timestamps and order IDs
4. Layout Architecture & Spatial Math
4.1 4-Point Grid & Radii Hierarchy
Base Grid: 
 to 
 spacing increments.
Form Controls & Inputs: 
 radius (rounded-lg), 
 height (
 on touch screens).
Buttons: 
–
 radius (rounded-xl), 
 default height.
Card Containers: 
 radius (rounded-2xl).
Sheets & Large Drawers: 
 radius.
Pills / Status Chips: 
 full radius (rounded-full).
Nested border-radius follows strict mathematical harmony:
4.2 Top Bar Contract
The top header complies with the single-row, three-zone contract:
5. Navigation & Information Architecture
5.1 Collapsible Sidebar (Merchant SaaS)
A persistent desktop sidebar with instant toggle transition (
):
Overview: Metric summary, volume stats, recent payments, attention queue.
Payments (8): Full data table with search, provider/status filters, CSV export, and drawer inspection.
Pricing Plans: Server-authoritative plan management, version history, and checkout triggers.
Settlements & Ledger: Double-entry accounting ledger statements, available balance, and minimum Tk 1,000 payout request.
Developers & Webhooks: Live/test API keys, HMAC-SHA256 signature verifier tester, curl snippets, and webhook logs.
Settings & KYB: Verified business identity badges, bank accounts, auto-confirm mode toggle, and 2FA status.
5.2 Mobile Bottom Navigation (md:hidden)
For mobile viewport ergonomics, primary navigation transitions to a sticky bottom tab bar honoring iOS safe area insets (env(safe-area-inset-bottom)):
Touch target height: 
 (
 WCAG compliance).
Badged notification indicators.
Seamless one-thumb switching.
6. Progressive Web App (PWA) Architecture
6.1 Installability & Standards Compliance
Web App Manifest (vite-plugin-pwa):
id: '/', scope: '/', start_url: '/'
name: 'PayGate Platform — Merchant SaaS'
short_name: 'PayGate' (≤ 12 characters to prevent mobile truncation)
display: 'standalone'
orientation: 'portrait-primary'
theme_color: '#FAFAF9', background_color: '#FAFAF9'
Icon Portfolio:
public/icon.svg: Scalable brand vector for desktop tabs.
public/apple-touch-icon.png: 
 PNG for iOS home screen.
public/pwa-192x192.png: 
 PNG for Android launcher.
public/pwa-512x512.png: 
 PNG for high-DPI splash screen.
public/pwa-maskable-512x512.png: 
 with safe-zone margin (10–15% inner padding) for Android adaptive icons.
6.2 In-App Install Prompt UI
Chromium / Android: Listens for beforeinstallprompt and triggers native installation upon clicking PWAInstallButton.
iOS Safari: Provides guided interactive modal instructions ("1. Tap Share in Safari → 2. Tap Add to Home Screen").
Standalone Mode Suppression: Automatically hides install buttons when running in standalone PWA window.
Offline Indicator: Ambient toast banner warning when offline without obstructing interface usability.
7. 6-Stage Checkout Lifecycle (PRD v3.1)
code
Code
[Merchant App]
      │ (POST /v1/payments)
      ▼
[PayGate Pay Page / Popup]
      │
      ├──> Stage 1: Initial (Wallet & Reference Display + Copy Buttons)
      │
      ├──> Stage 2: Ready to Confirm (Customer enters TrxID)
      │
      ├──> Stage 3: Loading / Verifying (Live pulse, 90s Realtime hold)
      │         │
      │         ├── SMS Matches TrxID + Ref + Amount
      │         ▼
      ├──> Stage 4: Success / Paid (Webhook sent, Net credited)
      │
      ├──> Stage 5: Needs Review (Underpaid / Conflict flagged)
      │
      └──> Stage 6: Locked / Expired (15-min lockout on 5 typos)
8. Security & Double-Entry Accounting Invariants
Reference Check-Character Math:
Format: 8 characters total (7-character base + 1 polynomial check character).
Charset excludes look-alikes: 0, O, 1, I, L.
Single-use, normalized case-insensitively.
Double-Entry Ledger Integrity:
Customer Pay-in: Credit Merchant Payable (Gross amount).
Platform Fee: Debit Merchant Payable (5.00% / fee_rate_bps = 500).
Payout Disbursement: Debit Merchant Payable (Net amount).
Maker-Checker Payout Enforcement:
Initiator (Maker) cannot be the approving officer (Checker).
Minimum payout threshold: Tk 1,000.00.
HMAC-SHA256 Webhooks:
Header: PayGate-Signature: t=<unix>,v1=<hex>.
Signature formula: 
.
5-minute replay attack tolerance window.