// Customer-facing words in five languages. Used by the web page AND the server
// (for notifications and the Nigerian voice), so they always say the same thing.
//
// The Yoruba, Igbo and Hausa lines should be checked by a native speaker before a real launch.
(function (root) {
  const ONES = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten', 'eleven',
    'twelve', 'thirteen', 'fourteen', 'fifteen', 'sixteen', 'seventeen', 'eighteen', 'nineteen'];
  const TENS = ['', '', 'twenty', 'thirty', 'forty', 'fifty', 'sixty', 'seventy', 'eighty', 'ninety'];

  function under1000(n) {
    const parts = [];
    if (n >= 100) { parts.push(ONES[Math.floor(n / 100)] + ' hundred'); n %= 100; if (n) parts.push('and'); }
    if (n >= 20) parts.push(TENS[Math.floor(n / 10)] + (n % 10 ? '-' + ONES[n % 10] : ''));
    else if (n > 0 || !parts.length) parts.push(ONES[n]);
    return parts.join(' ');
  }
  function words(n) {
    if (n === 0) return 'zero';
    const out = [];
    for (const [v, name] of [[1e9, 'billion'], [1e6, 'million'], [1e3, 'thousand']]) {
      if (n >= v) { out.push(under1000(Math.floor(n / v)) + ' ' + name); n %= v; }
    }
    if (n) out.push((out.length && n < 100 ? 'and ' : '') + under1000(n));
    return out.join(' ');
  }
  // "fifteen thousand naira" for English and Pidgin voices.
  function moneyWords(kobo) {
    const naira = Math.floor(kobo / 100), k = kobo % 100;
    return words(naira) + ' naira' + (k ? ' ' + words(k) + ' kobo' : '');
  }
  // "₦15,000" for the screen.
  function moneyText(kobo) {
    const naira = Math.floor(kobo / 100), k = kobo % 100;
    const n = String(naira).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    return '₦' + n + (k ? '.' + String(k).padStart(2, '0') : '');
  }
  // "15,000 naira" for Yoruba, Igbo and Hausa voices: the voice reads the number itself.
  function moneySpoken(kobo, lang) {
    if (lang === 'en' || lang === 'pcm') return moneyWords(kobo);
    return moneyText(kobo).slice(1) + ' naira';
  }

  const LANGS = {
    en: {
      name: 'English',
      ask: '{m} is asking you to pay {a}. To approve, tap the big button and use your {bio}. Or decline.',
      notifTitle: '{m} is asking you to pay {a}',
      notifBody: 'Tapnod. Tap to approve with your fingerprint, or decline.',
      approved: 'Payment approved. You paid {a} to {m}.',
      declined: 'Payment declined. No money left your account.',
      expired: 'The payment timed out. No money left your account.',
      cancelled: '{m} cancelled this payment. No money left your account.',
      waiting: 'Waiting for a payment',
      waitingBody: 'Keep this page open, or allow notifications. When a shop charges your card, Tapnod tells you.',
      approve: 'Approve with {bio}',
      decline: 'Decline',
      repeat: 'Repeat',
      done: 'Done',
      paid: 'Approved',
      stopped: 'Declined',
      timedOut: 'Expired',
      cancelledTitle: 'Cancelled',
      secondsLeft: '{n} seconds left',
      tapToListen: 'Tap to listen',
      sample: 'Hello. This is how Tapnod will read your payments to you.',
    },
    pcm: {
      name: 'Pidgin',
      ask: '{m} wan collect {a} from you. Use your finger approve am, or cancel am.',
      notifTitle: 'You wan pay {a} give {m}?',
      notifBody: 'Tap am, use your finger approve.',
      approved: 'Payment don go. You don pay {a} give {m}.',
      declined: 'You don cancel the payment. No money comot from your account.',
      expired: 'Time don pass for this payment. No money comot from your account.',
      cancelled: '{m} don cancel this payment. No money comot from your account.',
      waiting: 'We dey wait for payment',
      waitingBody: 'Leave this page open, or allow notification. Once shop charge your card, Tapnod go tell you.',
      approve: 'Use finger approve',
      decline: 'Cancel am',
      repeat: 'Talk am again',
      done: 'E don do',
      paid: 'E don go',
      stopped: 'You cancel am',
      timedOut: 'Time don pass',
      cancelledTitle: 'Dem cancel am',
      secondsLeft: '{n} seconds remain',
      tapToListen: 'Tap am make you hear',
      sample: 'How far. Na so Tapnod go dey talk your payment give you.',
    },
    yo: {
      name: 'Yorùbá',
      ask: '{m} fẹ́ gba {a} lọ́wọ́ rẹ. Fi ìka rẹ fọwọ́ sí i, tàbí kọ̀ ọ́.',
      notifTitle: 'Ṣé o fẹ́ san {a} fún {m}?',
      notifBody: 'Tẹ ibí láti fi ìka rẹ fọwọ́ sí i.',
      approved: 'Ìsanwó ti lọ. O ti san {a} fún {m}.',
      declined: 'O ti kọ ìsanwó náà. Kò sí owó tó jáde nínú àkáǹtì rẹ.',
      expired: 'Àkókò ìsanwó yìí ti kọjá. Kò sí owó tó jáde nínú àkáǹtì rẹ.',
      cancelled: '{m} ti fagilé ìsanwó yìí. Kò sí owó tó jáde nínú àkáǹtì rẹ.',
      waiting: 'À ń dúró de ìsanwó',
      waitingBody: 'Fi ojú-ewé yìí sílẹ̀ ní ṣíṣí, tàbí gba ìfitónilétí láàyè. Tapnod yóò sọ fún ọ nígbà tí ṣọ́ọ̀bù kan bá gba owó.',
      approve: 'Fọwọ́ sí i pẹ̀lú ìka',
      decline: 'Kọ̀ ọ́',
      repeat: 'Tún sọ',
      done: 'Ó ti parí',
      paid: 'Ó ti lọ',
      stopped: 'O kọ̀ ọ́',
      timedOut: 'Àkókò ti kọjá',
      cancelledTitle: 'Wọ́n fagilé e',
      secondsLeft: 'Ìṣẹ́jú-àáyá {n} ló kù',
      tapToListen: 'Tẹ ibí láti gbọ́',
      sample: 'Ẹ n lẹ́. Báyìí ni Tapnod yóò ṣe máa ka ìsanwó rẹ fún ọ.',
    },
    ig: {
      name: 'Igbo',
      ask: '{m} chọrọ ka ị kwụọ {a}. Jiri mkpịsị aka gị kwado ya, ma ọ bụ jụ ya.',
      notifTitle: 'Ị chọrọ ịkwụ {m} {a}?',
      notifBody: 'Pịa ebe a ka i jiri mkpịsị aka kwado ya.',
      approved: 'Ịkwụ ụgwọ agaala. Ị kwụọla {m} {a}.',
      declined: 'Ị jụrụ ịkwụ ụgwọ a. Ọ dịghị ego si n\'akaụntụ gị pụọ.',
      expired: 'Oge ịkwụ ụgwọ a agafeela. Ọ dịghị ego si n\'akaụntụ gị pụọ.',
      cancelled: '{m} kagburu ịkwụ ụgwọ a. Ọ dịghị ego si n\'akaụntụ gị pụọ.',
      waiting: 'Anyị na-eche ịkwụ ụgwọ',
      waitingBody: 'Hapụ ibe a ka ọ meghee, ma ọ bụ kwe ka ọkwa bịa. Tapnod ga-agwa gị mgbe ụlọ ahịa chọrọ ego.',
      approve: 'Jiri mkpịsị aka kwado',
      decline: 'Jụ ya',
      repeat: 'Kwughachi',
      done: 'Ọ gwụla',
      paid: 'Agaala',
      stopped: 'Ị jụrụ ya',
      timedOut: 'Oge agafeela',
      cancelledTitle: 'Akagburu ya',
      secondsLeft: 'Sekọnd {n} fọdụrụ',
      tapToListen: 'Pịa ka ị nụ',
      sample: 'Ndewo. Otu a ka Tapnod ga-esi agụrụ gị ịkwụ ụgwọ gị.',
    },
    ha: {
      name: 'Hausa',
      ask: '{m} na neman ka biya {a}. Yi amfani da yatsanka ka amince, ko ka ƙi.',
      notifTitle: 'Za ka biya {m} {a}?',
      notifBody: 'Danna nan ka amince da yatsanka.',
      approved: 'An biya. Ka biya {m} {a}.',
      declined: 'Ka ƙi biyan kuɗin. Babu kuɗin da ya fita daga asusunka.',
      expired: 'Lokacin biyan kuɗin ya wuce. Babu kuɗin da ya fita daga asusunka.',
      cancelled: '{m} ya soke wannan biyan kuɗin. Babu kuɗin da ya fita daga asusunka.',
      waiting: 'Muna jiran biyan kuɗi',
      waitingBody: 'Bar wannan shafin a buɗe, ko ka yarda da sanarwa. Tapnod zai sanar da kai idan shago ya caje katinka.',
      approve: 'Amince da yatsa',
      decline: 'Ƙi',
      repeat: 'Maimaita',
      done: 'An gama',
      paid: 'An biya',
      stopped: 'Ka ƙi',
      timedOut: 'Lokaci ya wuce',
      cancelledTitle: 'An soke',
      secondsLeft: 'Saura daƙiƙa {n}',
      tapToListen: 'Danna ka ji',
      sample: 'Sannu. Haka Tapnod zai karanta maka biyan kuɗinka.',
    },
  };

  // What the phone calls its biometric check: "Face ID" on iPhone, "fingerprint" on most Android phones.
  let bio = 'fingerprint or face';
  function setBio(word) { bio = word; }

  function t(lang, key, vars) {
    const table = LANGS[lang] || LANGS.en;
    let s = table[key] != null ? table[key] : LANGS.en[key];
    for (const [k, v] of Object.entries({ bio, ...(vars || {}) })) s = s.split('{' + k + '}').join(v);
    return s;
  }

  // The sentence the voice reads and the text the screen shows for a request status.
  function sayFor(lang, status, req) {
    const key = status === 'pending' ? 'ask' : status;
    return {
      spoken: t(lang, key, { m: req.merchant_name, a: moneySpoken(req.amount_kobo, lang) }),
      shown: t(lang, key, { m: req.merchant_name, a: moneyText(req.amount_kobo) }),
    };
  }

  const api = { LANGS, t, sayFor, setBio, moneyText, moneyWords, moneySpoken };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.TapnodI18n = api;
})(typeof self !== 'undefined' ? self : this);
