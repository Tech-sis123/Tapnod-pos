// Small helpers shared by the phone-call providers (Infobip and Africa's Talking).

// Nigerian numbers: 0803 123 4567 -> +2348031234567. Anything already in +country form is kept.
function normalisePhone(raw) {
  let s = String(raw || '').replace(/[^\d+]/g, '');
  if (!s) return '';
  if (s.startsWith('+')) return /^\+\d{8,15}$/.test(s) ? s : '';
  if (s.startsWith('234')) s = '+' + s;
  else if (s.startsWith('0') && s.length === 11) s = '+234' + s.slice(1);
  else return '';
  return /^\+\d{8,15}$/.test(s) ? s : '';
}

const maskPhone = (p) => (p ? p.slice(0, 7) + ' ··· ' + p.slice(-2) : '');

module.exports = { normalisePhone, maskPhone };
