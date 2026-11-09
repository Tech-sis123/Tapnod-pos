# Tapnod

**Tapnod makes card payments accessible.** When a shop charges your card, your own phone tells you out loud, in your language, who is charging you and how much. You approve with your fingerprint. Nobody else has to read the amount for you, and nobody needs your PIN.

Built for people the usual POS flow leaves out: blind and low-vision customers, people who can't read the small screen, and anyone who doesn't trust a stranger to read an amount for them.

This repo holds the whole demo:

| Part | What it is | Built with |
| --- | --- | --- |
| **Tapnod POS** (`app/`) | Android app the shop agent uses. Types the amount, reads the card by tap or camera, waits for the customer, prints a receipt. | Kotlin, Jetpack Compose, CameraX, ML Kit |
| **Customer web app** (`server/public/`) | Page on the customer's phone. Reads the payment aloud, sends a notification, approves with fingerprint. | HTML, JavaScript, Web Push, WebAuthn |
| **Backend** (`server/`) | Connects the two. Holds payment requests, sends notifications, makes Nigerian-voice audio. | Node.js, no dependencies |

## How a payment works

```text
 Shop agent (POS)                Tapnod backend                   Customer's phone
 ----------------                --------------                   ----------------
 1. Types ₦15,000, taps Charge
 2. Customer taps card,
    or agent scans it      --->  3. Finds whose card it is
    (card number hashed            creates a payment request ---> 4. Notification + voice:
     on the phone first)           (2-minute timer)                  "Bisi Ventures is asking you
                                                                      to pay fifteen thousand naira"
                                                                  5. Fingerprint prompt opens,
                                   6. Marks it approved      <---    customer touches the sensor
 7. Sees approved, says    <---
    "Payment approved",
    shows the receipt
```

The card number never leaves the POS phone. It is turned into a one-way token (HMAC-SHA256) the moment it's read, and only the last 4 digits are shown.

## Quick start

### 1. Put the backend online (free, about 5 minutes)

1. On [render.com](https://render.com), sign in with GitHub, then **New > Blueprint** and pick this repo. It reads `render.yaml`.
2. Click **Apply**. You get a link like `https://tapnod-demo.onrender.com`.
3. Optional: under **Environment**, add `YARNGPT_API_KEY` to turn on Nigerian voices (see below).

Check it's alive: `https://<your-link>/api/health` shows `{"ok":true}`.

### 2. Install the POS app

1. Every push to `main` builds the APK in GitHub Actions (about 5 minutes).
2. On the agent's Android phone, open this repo's **Releases** page and download `Tapnod-POS.apk` from **Tapnod POS (latest build)**.
3. Open it and allow "install unknown apps" if Android asks.
4. In the app, open **Settings**, check the Backend URL and tap **Test connection**. When it works, the POS saves and goes live. The status line at the top says **LIVE · CONNECTED**.

Needs Android 8.0 or newer. NFC is optional: without it, use **Scan card with camera**.

### 3. Open the customer app

1. On the customer's phone, open `https://<your-link>/?c=cus_amaka` in **Chrome**, not inside WhatsApp or Instagram.
2. Pick a language and tap **Start**. The phone asks for a fingerprint straight away, to create a Tapnod passkey.
3. Turn on notifications.

### 4. Make a payment

On the POS, type `15000`, tap **Charge**, then tap or scan a card. The customer's phone speaks the payment and opens the fingerprint prompt by itself. Touching the sensor approves, and the POS announces "Payment approved".

## Tapnod POS (Android)

| Screen | What it does |
| --- | --- |
| Amount | Keypad; merchant fixed as Bisi Ventures (change it in Settings) |
| Tap card | Waits for a contactless tap, with **Scan card with camera** as the alternative |
| Camera scan | Reads the card through the back camera, with a light button for dark rooms |
| Waiting | 2-minute countdown while the customer decides; the agent can cancel |
| Result | Green and spoken "Payment approved", or red Declined, within about 2 seconds |
| Receipt | The payment with its reference number |
| Not linked | Shown when a card isn't registered with Tapnod |
| Link card | Setup mode: pick a customer, tap their card |
| Settings | Simulator, Accept any tap, backend URL, terminal and merchant, voice |

### Reading the card

- **Tap (NFC):** reads contactless Visa, Mastercard and Verve cards over ISO-DEP with the open-source [EMV-NFC-Paycard-Enrollment](https://github.com/devnied/EMV-NFC-Paycard-Enrollment) library (`nfc/CardReader.kt`).
- **Camera:** ML Kit reads the text on the card, on the phone, and nothing is uploaded (`ui/CameraScan.kt`). A number only counts if it passes the Luhn checksum every card number has (`util/CardScan.kt`).
- **Accept any tap** (on by default): if a card is clearly there but its number can't be read, it counts as the demo card instead of failing. That covers some Verve cards, a card pulled away too fast, or a card with the number on the back. Turn it off to show strict reading.

### Simulator mode

For rehearsing without internet. Customers are simulated and the "customer" answers after about 4 seconds; set the answer to Approve, Decline or No answer in Settings. **Simulate tap** stands in for a card. The app starts live by default; switch Simulator on in Settings.

## Customer web app

- **Speaks every payment** in **English, Nigerian Pidgin, Yorùbá, Igbo or Hausa**. The screen, voice and notifications all follow the chosen language (`server/public/i18n.js`).
- **Nigerian voices** through [YarnGPT](https://yarngpt.ai/api-docs) when the backend has a key. Without one, the phone's own voice is used; phones mostly ship UK and US English voices only.
- **Fingerprint** with a WebAuthn passkey. It's set up on the Start tap, and the prompt opens by itself for every new payment. The Approve button stays as a backup.
- **Notifications** (Web Push) that arrive even when Chrome is closed (`server/public/sw.js`).
- **Settings:** language, voice, speed, play a sample, notifications on and test, fingerprint.
- Keeps the screen awake while live. If Chrome blocks the voice, a **Tap here to turn on Tapnod's voice** bar appears.

Phone browsers only allow sound and fingerprint prompts after the person has tapped the page. That's why there is one **Start** tap, and one tap after reloading the page.

## Backend

Plain Node 18+, no npm packages. Web Push encryption and VAPID signing use Node's built-in crypto (`server/push.js`).

```bash
cd server
node server.js          # http://localhost:3000
```

Fingerprint only works over https, so test it on Render or `localhost`, not on a `http://192.168...` address.

### Environment variables

| Name | What it does |
| --- | --- |
| `TOKEN_SECRET` | Key for card tokens. Must match **Card token secret** in the POS settings. Default `tapnod-demo-secret`. |
| `YARNGPT_API_KEY` | Turns on Nigerian voices. Every call spends YarnGPT credits. |
| `VAPID_SECRET` | Keeps notification keys the same across restarts. Render generates it. |
| `DEMO_AUTO_LINK` | Demo shortcut: unknown cards are linked to this customer on first use (`cus_amaka`). Empty = real "Card not linked" behaviour. |

### API

Every POS request carries the header `X-Terminal-Id`. The card is identified by `card_token`, the lowercase hex HMAC-SHA256 of the card number, keyed with `TOKEN_SECRET`.

**POS side**

| Method and path | Purpose |
| --- | --- |
| `GET /api/health` | Any 2xx means online |
| `POST /api/pos/requests` | Create a payment request after a card read. `201` returns `request_id`, `customer_name`, `expires_at_ms`. Unknown card: `404` with `"error": "card_not_linked"` |
| `GET /api/pos/requests/{id}` | Polled every 0.7s: `status` is `pending`, `approved`, `declined`, `expired` or `cancelled`, plus `reference` |
| `POST /api/pos/requests/{id}/cancel` | Agent cancelled while waiting |
| `GET /api/pos/customers` | Customers for link-card mode |
| `POST /api/pos/cards/link` | Link a `card_token` to a `customer_id` |

Create-request body:

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

**Customer side**

| Method and path | Purpose |
| --- | --- |
| `GET /api/config` | Notification public key, whether Nigerian voices are on, languages |
| `GET /api/voices` | Nigerian voices available |
| `POST /api/tts` | Make a voice sample (`text`, `voice`) |
| `GET /api/customer/{id}` | Name and saved settings |
| `GET /api/customer/{id}/latest` | Newest payment for this customer, with its audio link when ready |
| `POST /api/customer/{id}/prefs` | Save language and voice |
| `POST /api/customer/{id}/subscribe` | Register this phone for notifications |
| `POST /api/customer/{id}/test-notification` | Send a test notification |
| `POST /api/customer/requests/{id}/approve` and `/decline` | The customer's answer |

Errors return `{ "message": "..." }`, and the POS shows that message.

## Demo day checklist

- [ ] Both phones on **your own hotspot**, not venue Wi-Fi
- [ ] Open the customer page about 5 minutes early: Render's free plan sleeps after 15 idle minutes and takes about a minute to wake
- [ ] POS status line says **LIVE · CONNECTED**
- [ ] Customer page opened **in Chrome**, Start done, fingerprint set up, notifications on
- [ ] One test payment run end to end
- [ ] Phone volume up on both phones

## Demo shortcuts (what a real launch would change)

- **Fingerprint:** checked on the phone and not verified by the server. In production the server would issue the challenge and verify the signed response.
- **Memory only:** the backend keeps data in memory, so it resets on restart. The customer page re-sends its settings and notification registration every minute to recover.
- **Auto-link:** `DEMO_AUTO_LINK` links any unknown card to Amaka.
- **Card reading on a phone:** in a pilot, a certified POS terminal reads the card and hands Tapnod a token.
- **Translations:** the Yorùbá, Igbo and Hausa lines need checking by native speakers.

## Project layout

```text
app/src/main/java/ng/tapnod/pos/
  MainActivity.kt        NFC reader on/off, hands taps to the view model
  PosViewModel.kt        Screens, sale flow, polling, link flow, settings
  nfc/CardReader.kt      EMV contactless read
  ui/CameraScan.kt       Camera card scanning
  ui/Screens.kt          Every POS screen
  ui/Theme.kt            Colours, Inter font, tiles
  data/TapnodApi.kt      HTTP client for the backend + offline simulator
  data/SettingsStore.kt  Saved settings
  util/CardScan.kt       Decides if the camera sees a card (Luhn check)
  util/CardToken.kt      Card number to one-way token
  util/                  Money words, voice and tones

server/
  server.js              Backend (POS + customer API)
  push.js                Phone notifications (Web Push, built-in crypto)
  tts.js                 Nigerian voices through YarnGPT
  public/index.html      Customer web app
  public/i18n.js         English, Pidgin, Yorùbá, Igbo, Hausa
  public/sw.js           Shows notifications when the page is closed

render.yaml              One-click deploy for the backend
.github/workflows/       Builds the POS APK and publishes it as a release
```

Inter font: SIL Open Font License (see `INTER_LICENSE.txt`).
