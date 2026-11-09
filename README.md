# Tapnod POS

The agent side of the Tapnod demo. An Android phone acts as the POS: the agent types the amount, the customer taps a real contactless bank card on the back of the phone, and the POS waits for the customer to approve on her own phone with her fingerprint.

Built with Kotlin and Jetpack Compose. Swiss typography (Inter), bento grid layout, black, paper and lime.

## Install on your phone

1. Push to `main`. GitHub Actions builds the APK in about 5 minutes.
2. On your Android phone, open the repo's **Releases** page and download `Tapnod-POS.apk` from **Tapnod POS (latest build)**.
3. Open the file. If Android asks, allow your browser or Files app to "install unknown apps".
4. Open **Tapnod POS**. Turn on NFC when asked.

Needs Android 8.0 or newer, with NFC, for real card taps. Without NFC it still runs in Simulator mode.

## Try it in 60 seconds (no backend needed)

The app starts in **Simulator mode**, so it works offline.

1. Tap **Link card**, pick **Amaka Okafor**, and tap your contactless debit card on the back of the phone. (No card handy? Tap **Simulate tap**.)
2. Type `15000`, tap **Charge**, and tap the same card.
3. After about 4 seconds the simulated customer answers and the POS shows **Approved** and says "Payment approved".
4. In **Settings**, set the simulated answer to **Decline** or **No answer** to rehearse the other outcomes.

## What each requirement maps to

| # | Requirement | Where |
| --- | --- | --- |
| A1 | Keypad, merchant fixed as Bisi Ventures | `AmountScreen`, merchant name in Settings |
| A2 | Tap screen with animation | `TapScreen`, `PulseCard` |
| A3 | Reads real EMV contactless cards, hashes the number on the phone | `nfc/CardReader.kt`, `util/CardToken.kt` |
| A4 | Card number never stored, logged or sent; only last 4 shown | `PosViewModel.onCardNumber` drops it after hashing |
| A5 | Waiting screen with 2-minute countdown | `WaitingScreen` |
| A6 | Approved (green + voice) or Declined (red) within 2s | `ResultScreen`, polling every 0.7s |
| A7 | "Card not linked to Tapnod" | `NotLinkedScreen` |
| A8 | Link-card setup mode | `LinkPickScreen`, `LinkDoneScreen` |
| A9 | Receipt with reference | `ReceiptScreen` |

## Backend contract (live mode)

Turn Simulator off in Settings and enter your backend URL. Every request carries the header `X-Terminal-Id`. The card is identified by `card_token` = lowercase hex HMAC-SHA256 of the card number, keyed with the **Card token secret** in Settings. The backend must use the same secret when it links cards, and never receives the card number.

### `GET /api/health`
Any `2xx` means online.

### `POST /api/pos/requests`
Create a payment request after a card tap.

```json
{
  "amount_kobo": 1500000,
  "currency": "NGN",
  "merchant_id": "BISI-001",
  "merchant_name": "Bisi Ventures",
  "terminal_id": "TPN-POS-001",
  "card_token": "9f2c...",
  "card_last4": "4821",
  "card_scheme": "VISA"
}
```

`201` response:

```json
{ "request_id": "req_8f2a1c", "customer_name": "Amaka Okafor", "expires_at_ms": 1794210000000 }
```

Unknown card: `404` (or any body with `"error": "card_not_linked"`).

### `GET /api/pos/requests/{request_id}`
Polled every 0.7s while waiting.

```json
{ "status": "pending | approved | declined | expired | cancelled", "reference": "TPN-8F2A1C" }
```

### `POST /api/pos/requests/{request_id}/cancel`
The agent cancelled while waiting.

### `GET /api/pos/customers`
For link-card mode.

```json
{ "customers": [ { "id": "cus_amaka", "name": "Amaka Okafor", "note": "Smartphone, screen reader on" } ] }
```

### `POST /api/pos/cards/link`

```json
{ "customer_id": "cus_amaka", "card_token": "9f2c...", "card_last4": "4821", "card_scheme": "VISA" }
```

Errors can return `{ "message": "..." }` and the POS shows that message.

## Customer web app and demo backend (`server/`)

The customer side is a web page, served by a tiny backend in `server/` (plain Node, no dependencies). One deploy gives you both the API the POS talks to and the page the customer opens.

**Flow:** POS detects the tap, then `POST /api/pos/requests`. The customer page checks every second, vibrates, reads the amount aloud ("Bisi Ventures is asking you to pay fifteen thousand naira"), and the customer approves with her fingerprint. The POS sees `approved` on its next poll and says "Payment approved".

### Run it

```bash
cd server
node server.js          # http://localhost:3000
```

### Deploy (free) on Render

1. render.com, **New > Blueprint**, pick this repo. It reads `render.yaml`.
2. You get a link like `https://tapnod-demo.onrender.com`.
3. POS: Settings, turn **Simulator** off, Backend URL = that link.
4. Customer phone: open `https://tapnod-demo.onrender.com/?c=cus_amaka` in Chrome, tap **Start**, and set up the fingerprint once.

The free plan sleeps after 15 idle minutes and takes about a minute to wake. Open the customer page a few minutes before the demo; its polling keeps the server awake.

### Notes

- The demo card (the one "Simulate tap" and "Accept any tap" use) is linked to Amaka automatically on start, so no Link card step is needed after a restart. Cards linked with **Link card** live in memory and reset when the server restarts.
- `TOKEN_SECRET` must match **Card token secret** in the POS settings. Both default to `tapnod-demo-secret`.
- Fingerprint uses WebAuthn (a passkey on the phone). It needs https, so it works on Render, not on a plain `http://192.168...` address. Without it the page asks for an on-screen confirmation instead.
- Demo shortcut: the fingerprint check happens on the phone and is not verified by the server. In production the server would issue the challenge and verify the signed response.
- Customer endpoints: `GET /api/customer/{id}`, `GET /api/customer/{id}/latest`, `POST /api/customer/requests/{request_id}/approve` and `/decline`.

## Accept any tap

Settings has **Accept any tap** (on by default). If a card touches the phone but its number can't be read (some Verve cards, a card pulled away too fast, an NFC sticker), the tap still counts as the demo card instead of showing "Card not read". Turn it off to show strict real-card reading.

## Card reading

Chrome's Web NFC only reads NFC tags, not bank cards, which is why the POS is a native app. Card reading uses Android `IsoDep` and the open-source [EMV-NFC-Paycard-Enrollment](https://github.com/devnied/EMV-NFC-Paycard-Enrollment) library. Visa and Mastercard read most reliably. Test your exact card early.

For the demo, this app reads the card itself. In a pilot, the POS provider's certified card reader does this and hands Tapnod a token.

## Project layout

```
app/src/main/java/ng/tapnod/pos/
  MainActivity.kt        NFC reader mode on/off, hands taps to the view model
  PosViewModel.kt        Screens, sale flow, polling, link flow, settings
  nfc/CardReader.kt      EMV contactless read
  data/TapnodApi.kt      HTTP client for the backend + offline simulator
  data/SettingsStore.kt  Saved settings
  util/                  Money words, card token, voice and tones
  ui/Theme.kt            Swiss palette, Inter, bento Tile
  ui/Screens.kt          Every screen

server/
  server.js              Demo backend (POS + customer API)
  public/index.html      Customer web app
```

Inter font: SIL Open Font License (see `INTER_LICENSE.txt`).
