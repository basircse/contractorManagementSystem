// Taka amount in words using the South-Asian system (hundred / thousand / lakh / crore),
// e.g. 1,25,500 -> "এক লক্ষ পঁচিশ হাজার পাঁচ শত টাকা মাত্র".

const BN = (
  'শূন্য এক দুই তিন চার পাঁচ ছয় সাত আট নয় দশ এগারো বারো তেরো চৌদ্দ পনেরো ষোলো সতেরো আঠারো উনিশ ' +
  'বিশ একুশ বাইশ তেইশ চব্বিশ পঁচিশ ছাব্বিশ সাতাশ আটাশ ঊনত্রিশ ত্রিশ একত্রিশ বত্রিশ তেত্রিশ চৌত্রিশ পঁয়ত্রিশ ছত্রিশ সাঁইত্রিশ আটত্রিশ ঊনচল্লিশ ' +
  'চল্লিশ একচল্লিশ বিয়াল্লিশ তেতাল্লিশ চুয়াল্লিশ পঁয়তাল্লিশ ছেচল্লিশ সাতচল্লিশ আটচল্লিশ ঊনপঞ্চাশ পঞ্চাশ একান্ন বায়ান্ন তিপ্পান্ন চুয়ান্ন পঞ্চান্ন ছাপ্পান্ন সাতান্ন আটান্ন ঊনষাট ' +
  'ষাট একষট্টি বাষট্টি তেষট্টি চৌষট্টি পঁয়ষট্টি ছেষট্টি সাতষট্টি আটষট্টি ঊনসত্তর সত্তর একাত্তর বাহাত্তর তিয়াত্তর চুয়াত্তর পঁচাত্তর ছিয়াত্তর সাতাত্তর আটাত্তর ঊনআশি ' +
  'আশি একাশি বিরাশি তিরাশি চুরাশি পঁচাশি ছিয়াশি সাতাশি আটাশি ঊননব্বই নব্বই একানব্বই বিরানব্বই তিরানব্বই চুরানব্বই পঁচানব্বই ছিয়ানব্বই সাতানব্বই আটানব্বই নিরানব্বই'
).split(' ')

const EN_ONES = 'zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen'.split(' ')
const EN_TENS = ['', '', 'twenty', 'thirty', 'forty', 'fifty', 'sixty', 'seventy', 'eighty', 'ninety']

function en99(n: number): string {
  if (n < 20) return EN_ONES[n]
  return EN_TENS[Math.floor(n / 10)] + (n % 10 ? '-' + EN_ONES[n % 10] : '')
}

/** Words for a whole number below 100 crore-crore, grouped crore / lakh / thousand / hundred / rest. */
function groups(n: number, lang: 'bn' | 'en'): string {
  if (n === 0) return lang === 'bn' ? BN[0] : EN_ONES[0]
  const w = (x: number) => (lang === 'bn' ? BN[x] : en99(x))
  const names = lang === 'bn' ? ['কোটি', 'লক্ষ', 'হাজার', 'শত'] : ['crore', 'lakh', 'thousand', 'hundred']
  const parts: string[] = []
  const crore = Math.floor(n / 1e7)
  if (crore) parts.push(`${crore >= 100 ? groups(crore, lang) : w(crore)} ${names[0]}`)
  n %= 1e7
  const lakh = Math.floor(n / 1e5)
  if (lakh) parts.push(`${w(lakh)} ${names[1]}`)
  n %= 1e5
  const thousand = Math.floor(n / 1e3)
  if (thousand) parts.push(`${w(thousand)} ${names[2]}`)
  n %= 1e3
  const hundred = Math.floor(n / 100)
  if (hundred) parts.push(`${w(hundred)} ${names[3]}`)
  n %= 100
  if (n) parts.push(lang === 'en' && parts.length ? `and ${w(n)}` : w(n))
  return parts.join(' ')
}

export function amountInWords(value: number | string, lang: 'bn' | 'en'): string {
  const v = Math.round(Math.abs(Number(value) || 0) * 100)
  const taka = Math.floor(v / 100)
  const paisa = v % 100
  if (lang === 'bn') {
    return `${groups(taka, 'bn')} টাকা${paisa ? ` ${BN[paisa]} পয়সা` : ''} মাত্র`
  }
  const s = `Taka ${groups(taka, 'en')}${paisa ? ` and ${en99(paisa)} paisa` : ''} only`
  return s.charAt(0).toUpperCase() + s.slice(1)
}
