# Tapnod

**Accessible card payments for everyone.** When a shop charges your card, Tapnod tells you out loud, on your own phone and in your language (English, Pidgin, Yorùbá, Igbo or Hausa), who is charging you and how much. You approve with your fingerprint. Nobody else has to read the POS screen for you, and nobody needs your PIN.

Built for the people the usual POS flow leaves out: blind and low-vision customers, people who prefer their own language, older customers, and anyone who doesn't want to hand their card and PIN to a stranger.

**Live:** [tapnod-demo.onrender.com](https://tapnod-demo.onrender.com) · Forge sandbox: `https://teamXX.xtradot.co` (see [Deploy to the Forge sandbox](#deploy-to-the-forge-2026-sandbox))

This repo holds the whole demo:

| Part | What it is | Built with |
| --- | --- | --- |
| **Tapnod POS** (`app/`) | Android app the shop agent uses. Types the amount, reads the card by tap or camera, waits for the customer, prints a receipt. | Kotlin, Jetpack Compose, CameraX, ML Kit |
| **Landing page + customer web app** (`server/public/`) | Public landing page, and the app on the customer's phone: sign up, fingerprint sign-in, payments read aloud, notifications, fingerprint approval. | HTML, JavaScript, Web Push, WebAuthn passkeys |
| **Backend** (`server/`) | Connects the two. Customer accounts in Postgres, payment requests, fingerprint checks, notifications, Nigerian-voice audio. | Node.js, Postgres (`pg` is the only package) |

## How a payment works

```text
 Shop agent (POS)                Tapnod backend                   Customer's phone
 ----------------                --------------                   ----------------
 1. Types ₦15,000, taps Charge
 2. Customer taps card,
    agent scans it, or
    agent picks her by     --->  3. Finds the customer,
    name or Tapnod ID              creates a payment request ---> 4. Notification + voice:
    (card number hashed            (2-minute timer)                  "Bisi Ventures is asking you
     on the phone first)                                              to pay fifteen thousand naira"
                                                                  5. Fingerprint prompt opens,
                                   6. Checks the fingerprint <---    customer touches the sensor
                                      signature, approves
 7. Sees approved, says    <---
    "Payment approved",
    shows the receipt
```

The card number never leaves the POS phone. It is turned into a one-way token (HMAC-SHA256) the moment it's read, and only the last 4 digits are shown.

## Demo in 2 minutes (laptop + Android)

1. **Laptop:** open `https://<your-link>/pos`.
2. **Amaka's phone:** open `https://<your-link>/demo` in Chrome. It's already signed in as the demo customer (Amaka Okafor, account `0123456789`, PIN `1234`). Tap the screen once: the voice turns on, and the first time it saves her fingerprint.
3. Charge on the laptop and press **Card tapped**. The phone reads the amount aloud and the fingerprint prompt opens by itself; touching the sensor approves. The result goes back to "waiting" by itself.

The demo customer is created on start (turn off with `DEMO_CUSTOMER=off`), and any tapped card goes to her (`DEMO_AUTO_LINK=demo`).

## Fastest demo (laptop + phone)

1. **POS on the laptop:** open `https://<your-link>/pos` in any browser. Type the amount (keyboard works), press Charge, then **Card tapped** or **Choose customer**. A USB card reader that types the card ID also works.
2. **Customer phone:** open `https://<your-link>/app`, tap **Create an account**: name, 10-digit account number, a **4-digit PIN**. Done.
3. The phone reads the payment aloud. The customer taps **Approve** and enters the PIN on a big keypad. The voice says how many digits were typed, never which ones. Face ID or fingerprint is optional, in Settings.

## Quick start

### 1. Put the backend online (free, about 5 minutes)

1. On [render.com](https://render.com), sign in with GitHub, then **New > Blueprint** and pick this repo. It reads `render.yaml` and creates the web service **and a free Postgres database** for customer accounts.
2. Click **Apply**. You get a link like `https://tapnod-demo.onrender.com`.
3. Optional: under **Environment**, add `YARNGPT_API_KEY` to turn on Nigerian voices (see below).

Check it's alive: `https://<your-link>/healthz` shows `{"ok":true,"storage":"postgres"}`.

### 2. Install the POS app

1. Every push to `main` builds the APK in GitHub Actions (about 5 minutes).
2. On the agent's Android phone, open this repo's **Releases** page and download `Tapnod-POS.apk` from **Tapnod POS (latest build)**.
3. Open it and allow "install unknown apps" if Android asks.
4. In the app, open **Settings**, check the Backend URL and tap **Test connection**. When it works, the POS saves and goes live. The status line at the top says **LIVE · CONNECTED**.

Needs Android 8.0 or newer. NFC is optional: without it, use **Scan card with camera**.

### 3. Create a customer account

1. On the customer's phone, open `https://<your-link>` in **Chrome** (not inside WhatsApp or Instagram) and tap **Get started**.
2. Enter name, 10-digit account number, phone (optional) and a password, and pick a language. You get a **Tapnod ID** like `TN-4821-KX`.
3. Add your fingerprint: the phone saves a Tapnod passkey. From now on you sign in with your finger.
4. Turn on notifications. They keep arriving even after you sign out.

### 4. Make a payment

On the POS, type `15000`, tap **Charge**, then tap or scan a card, or tap **Choose customer** and pick her by name. Her phone gets a notification, speaks the payment and opens the fingerprint prompt by itself. Touching the sensor approves, and the POS announces "Payment approved".

## Tapnod POS (Android)

| Screen | What it does |
| --- | --- |
| Amount | Keypad; merchant fixed as Bisi Ventures (change it in Settings) |
| Tap card | Waits for a contactless tap, with **Scan with camera** and **Choose customer** as alternatives |
| Choose customer | Lists everyone who signed up (name, Tapnod ID, masked account); picking one charges them |
| Camera scan | Reads the card through the back camera, with a light button for dark rooms |
| Waiting | 2-minute countdown while the customer decides; the agent can cancel |
| Result | Green and spoken "Payment approved", or red Declined, within about 2 seconds |
| Receipt | The payment with its reference number |
| Not linked | Shown when a card isn't registered with Tapnod |
| Link card | Setup mode: pick a signed-up customer, tap their card |
| Settings | Simulator, Accept any tap, backend URL, terminal and merchant, voice |

### Reading the card

- **Tap (NFC):** reads contactless Visa, Mastercard and Verve cards over ISO-DEP with the open-source [EMV-NFC-Paycard-Enrollment](https://github.com/devnied/EMV-NFC-Paycard-Enrollment) library (`nfc/CardReader.kt`).
- **Camera:** ML Kit reads the text on the card, on the phone, and nothing is uploaded (`ui/CameraScan.kt`). A number only counts if it passes the Luhn checksum every card number has (`util/CardScan.kt`).
- **Accept any tap** (on by default): if a card is clearly there but its number can't be read, it counts as the demo card instead of failing. That covers some Verve cards, a card pulled away too fast, or a card with the number on the back. Turn it off to show strict reading.

### Simulator mode

For rehearsing without internet. Customers are simulated and the "customer" answers after about 4 seconds; set the answer to Approve, Decline or No answer in Settings. **Simulate tap** stands in for a card. The app starts live by default; switch Simulator on in Settings.

## Landing page and customer web app

**Landing page** (`/`): what Tapnod is, how a payment works in three steps, who it's for, the five languages, and buttons to sign up or sign in.

**Customer app** (`/app`):

- **Sign up** with full name, 10-digit account number, optional phone and a password. You get a **Tapnod ID** that shops can find you by.
- **Fingerprint sign-in.** After sign-up you add your fingerprint (a WebAuthn passkey). Signing in is then one touch, with no account number to type. Account number and password still work as a fallback.
- **Payments read aloud** in **English, Nigerian Pidgin, Yorùbá, Igbo or Hausa**, on the screen, in the voice and in notifications (`server/public/i18n.js`).
- **Fingerprint approval.** When a payment arrives, the fingerprint prompt opens by itself; touching the sensor approves. The server checks the passkey's signature against a challenge made for that exact payment. Phones without a fingerprint approve with the account password.
- **Notifications** (Web Push) arrive even when Chrome is closed or the customer is signed out (`server/public/sw.js`). Tapping one opens the app at the payment.
- **Nigerian voices** through [YarnGPT](https://yarngpt.ai/api-docs) when the backend has a key. Without one, the phone's own voice is used; phones mostly ship UK and US English voices only.
- **Settings:** language, voice, speed, play a sample, fingerprint, notifications on and test, account, sign out.

Phone browsers only allow sound and fingerprint prompts after the person has tapped the page, so the app asks for one tap after it reloads.

## Approval by phone call

For customers with a basic phone, or when the app isn't open. On the POS waiting screen, type her number (or leave it empty to use the one on her account) and press **Call her phone**.

```text
 POS: Call her phone ──> Tapnod server ──> LiveKit ──> caller agent ──> rings her phone
                                                         │
   "Hello Amaka. Bisi Ventures is asking you to pay fifteen thousand naira.
    To approve, press 1. To decline, press 2."   ->  1
                                                         │
 POS shows Approved <── Tapnod server records her choice <───┘
```

- Pressing 1 approves. Set `CALL_REQUIRE_PIN=on` to also ask for her 4-digit PIN (never read back, 3 tries).
- If she approves in the app while on the call, the call ends by itself.
- Each call carries a one-time code from the server, so nothing else can approve through the voice endpoint.

**Option A: free, with your own phones (no service at all).** Your Android phone makes a normal call from its own SIM; the laptop talks and listens.

1. Set `CALL_BRIDGE_KEY` on the server to any secret word, e.g. `tapnod2026`.
2. Laptop: open `https://<your-site>/callcenter?key=tapnod2026` in its **own window** and press **Start** (allow the microphone).
3. Android phone: open `https://<your-site>/gateway?key=tapnod2026` and keep it open.
4. POS: **Call her phone**. The Android phone shows a big **Call** button: tap it, tap Call, turn on **loudspeaker**, and place it next to the laptop.
5. The laptop speaks the payment; she presses 1 or 2 on her phone; the laptop hears the key tone (DTMF) and approves or declines. The page also has **She pressed 1 / 2** buttons as a backup for a noisy room.

When `CALL_BRIDGE_KEY` is set it is used first.

**Option 0: Twilio free trial (no card, 75 free minutes).** Simplest for one demo phone.

1. Sign up at twilio.com/try-twilio **with the Nigerian number you'll call** (it's verified automatically; trial calls only go to verified numbers in your sign-up country).
2. From the console, copy the **Account SID**, **Auth Token**, and the trial **From** number shown on Voice > Try out Voice.
3. Give the server `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` and `PUBLIC_URL=https://<your-site>`. Nothing to set in Twilio: each call tells Twilio where to fetch its script.

**Option 1: Africa's Talking (Nigerian number, cheapest).** No agent to deploy: the server does everything.

1. On africastalking.com (Live account, not Sandbox), request a Nigerian voice number: Voice, Phone numbers, request form, category **Test Number**.
2. Set the number's **callback URL** to `https://<your-site>/api/at/voice`.
3. Give the server `AT_USERNAME`, `AT_API_KEY` and `AT_VOICE_NUMBER` (e.g. `+234...`). Set `PUBLIC_URL=https://<your-site>` if your host doesn't pass https headers.

The POS uses Twilio if it's set, then Africa's Talking, then LiveKit (below).

**Option 2: LiveKit.** Set up (once):

1. In your [LiveKit Cloud](https://cloud.livekit.io) project, open **Telephony** and add an **outbound SIP trunk** from a provider that can call Nigerian numbers (for example Twilio or Telnyx). Copy its trunk ID (`ST_...`).
2. Deploy the agent in `caller/`:
   ```bash
   cd caller
   cp .env.example .env          # fill in the LiveKit keys, SIP_OUTBOUND_TRUNK_ID and DEEPGRAM_API_KEY
   lk agent create --secrets-file .env    # first time
   lk agent deploy                        # after changes
   ```
   To try it on your laptop instead: `pip install -r requirements.txt && python agent.py dev`.
3. Give the server the same LiveKit project: set `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` (and `DEMO_PHONE` for Amaka). On Kubernetes, create the `tapnod-calls` secret shown in `k8s/app.yaml`, then `kubectl rollout restart deployment/app`.

## Backend

Plain Node 18+. The only package is `pg`, for Postgres. Web Push, password hashing (scrypt), sessions and passkey checks all use Node's built-in crypto (`push.js`, `auth.js`).

```bash
cd server
npm install
node server.js                                   # data in memory, for a quick try
DATABASE_URL=postgres://... node server.js       # data in Postgres
```

Tables are created automatically on start: `customers`, `credentials` (passkey public keys), `cards` (linked card tokens) and `subscriptions` (notification endpoints). Payment requests last 2 minutes and stay in memory.

Fingerprint only works over https, so test it on Render or `localhost`, not on a `http://192.168...` address.

### Environment variables

| Name | What it does |
| --- | --- |
| `DATABASE_URL` | Postgres connection. Render's blueprint fills it in. Without it, data lives in memory and is lost on restart. |
| `TOKEN_SECRET` | Key for card tokens. Must match **Card token secret** in the POS settings. Default `tapnod-demo-secret`. |
| `YARNGPT_API_KEY` | Turns on Nigerian voices. Every call spends YarnGPT credits. |
| `VAPID_SECRET` | Signs sign-in sessions and keeps notification keys the same across restarts. Render generates it. |
| `CALL_BRIDGE_KEY` | Free phone calls with your own Android phone and the laptop (used first when set). |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` | Phone calls through Twilio. |
| `AT_USERNAME`, `AT_API_KEY`, `AT_VOICE_NUMBER` | Phone calls through Africa's Talking. |
| `PUBLIC_URL` | This site's public https address, for Twilio and Africa's Talking callbacks. |
| `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | Phone calls through LiveKit instead. |
| `DEMO_PHONE` | Number the POS rings for Amaka when none is typed, e.g. `08031234567`. |
| `DEMO_AUTO_LINK` | Demo shortcut for unknown cards: `latest` (default) links them to the most recent sign-up, a customer id links them to that customer, empty gives the real "Card not linked" behaviour. |

### API

Every POS request carries the header `X-Terminal-Id`. The card is identified by `card_token`, the lowercase hex HMAC-SHA256 of the card number, keyed with `TOKEN_SECRET`.

**POS side**

| Method and path | Purpose |
| --- | --- |
| `GET /api/health` | Any 2xx means online |
| `POST /api/pos/requests` | Create a payment request from a card read (`card_token`) or a picked customer (`customer_id`). `201` returns `request_id`, `customer_name`, `expires_at_ms`. Unknown card: `404` with `"error": "card_not_linked"` |
| `GET /api/pos/requests/{id}` | Polled every 0.7s: `status` is `pending`, `approved`, `declined`, `expired` or `cancelled`, plus `reference` |
| `POST /api/pos/requests/{id}/cancel` | Agent cancelled while waiting |
| `GET /api/pos/customers` | Everyone who signed up: name, Tapnod ID, masked account |
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

**Accounts**

| Method and path | Purpose |
| --- | --- |
| `POST /api/auth/signup` | `name`, `account_number` (10 digits), `phone`, `password`, `lang`. Returns a session `token` and the customer with their Tapnod ID |
| `POST /api/auth/login` | Account number and password |
| `POST /api/auth/passkey/login/options` then `POST /api/auth/passkey/login` | Fingerprint sign-in: a challenge, then the signed response |

**Signed-in customer** (header `Authorization: Bearer <token>`)

| Method and path | Purpose |
| --- | --- |
| `GET /api/me` | The customer, and whether a fingerprint is added |
| `POST /api/me/passkey/options` then `POST /api/me/passkey` | Add a fingerprint passkey |
| `GET /api/me/latest` | Newest payment for this customer, with its audio link when ready |
| `POST /api/me/prefs` | Save language and voice |
| `POST /api/me/subscribe` | Register this phone for notifications |
| `POST /api/me/test-notification` | Send a test notification |
| `POST /api/customer/requests/{id}/approve/options` then `.../approve` | Approve with a fingerprint signature for this payment (or `password` if the account has no fingerprint) |
| `POST /api/customer/requests/{id}/decline` | Decline |

**Other:** `GET /healthz`, `GET /api/config`, `GET /api/voices`, `POST /api/tts`.

Errors return `{ "message": "..." }`, and the POS shows that message.

## Deploy to the Forge 2026 sandbox

The backend (landing page, customer app and API) runs as one container. Every push to `main` that touches `server/` builds it for `linux/amd64` and publishes it to **`ghcr.io/tech-sis123/tapnod:latest`** (`.github/workflows/server-image.yml`). `k8s/app.yaml` follows the guide's reference manifest: Deployment `app` on port 8080, Service `app` on port 80, health check `/healthz`.

**Once:** make the image public so the cluster can pull it. On GitHub, open your profile, then **Packages**, then **tapnod**, then **Package settings**, then **Change visibility** and choose **Public**. Or keep it private and create a pull secret (guide 4.1), then uncomment `imagePullSecrets` in `k8s/app.yaml`.

**Deploy** (team lead, with the kubeconfig from the organisers):

```bash
export KUBECONFIG=~/forge/teamXX.yaml        # the file you were sent
kubectl apply -f k8s/app.yaml
kubectl rollout status deployment/app
curl -I https://teamXX.xtradot.co            # 200 = live
```

**Update after a new push:** `kubectl rollout restart deployment/app` (the manifest always pulls `latest`).

**Logs:** `kubectl logs deploy/app -f`

Notes: in the sandbox, data is kept in memory (one replica), so accounts reset if the pod restarts. Add a `DATABASE_URL` to use Postgres. Nigerian voices and phone notifications need the pod to reach the internet (YarnGPT, and Google's push service); ask the organisers if outbound traffic is allowed.

The POS app talks to whichever backend is in its Settings: set the Backend URL to `https://teamXX.xtradot.co` and tap **Test connection**.

## Demo day checklist

- [ ] Both phones on **your own hotspot**, not venue Wi-Fi
- [ ] Open the customer page about 5 minutes early: Render's free plan sleeps after 15 idle minutes and takes about a minute to wake
- [ ] POS status line says **LIVE · CONNECTED**
- [ ] Customer account created **in Chrome** on the customer's phone, fingerprint added, notifications on
- [ ] One test payment run end to end
- [ ] Phone volume up on both phones

## Demo shortcuts (what a real launch would change)

- **Account number:** taken as typed. A real launch would confirm it belongs to the customer (name enquiry, BVN or an OTP from the bank).
- **Free database:** Render's free Postgres expires 30 days after creation. Fine for the event; a launch needs a paid database with backups.
- **Auto-link:** `DEMO_AUTO_LINK` links any unknown card to the most recent sign-up.
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
  ui/Theme.kt            Colours (Swiss classic, shared with the web app), Inter, tiles
  data/TapnodApi.kt      HTTP client for the backend + offline simulator
  data/SettingsStore.kt  Saved settings
  util/CardScan.kt       Decides if the camera sees a card (Luhn check)
  util/CardToken.kt      Card number to one-way token
  util/                  Money words, voice and tones

server/
  server.js              Backend (POS, accounts and customer API)
  db.js                  Postgres storage (memory when no DATABASE_URL)
  auth.js                Passwords, sessions, fingerprint passkey checks
  push.js                Phone notifications (Web Push, built-in crypto)
  tts.js                 Nigerian voices through YarnGPT
  public/landing.html    Landing page
  public/app.html        Customer web app
  public/tapnod.css      Shared design system
  public/i18n.js         English, Pidgin, Yorùbá, Igbo, Hausa
  public/sw.js           Shows notifications when the page is closed

render.yaml              One-click deploy for the backend
.github/workflows/       Builds the POS APK and publishes it as a release
```

Inter font: SIL Open Font License (see `INTER_LICENSE.txt`).
