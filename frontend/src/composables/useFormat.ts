import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { SkillTier, WorkItem } from '@/api/types'

const BN_DIGITS = '০১২৩৪৫৬৭৮৯'

/** Converts any Bangla digits typed by the user to ASCII so they can be parsed. */
export function toAsciiDigits(s: string): string {
  return s.replace(/[০-৯]/g, (d) => String(BN_DIGITS.indexOf(d)))
}

export function toBnDigits(s: string): string {
  return s.replace(/[0-9]/g, (d) => BN_DIGITS[Number(d)])
}

/** yyyy-mm-dd in local time (the API's date format). */
export function isoDate(d: Date | null | undefined): string | null {
  if (!d) return null
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export function parseIso(s: string | null | undefined): Date | null {
  if (!s) return null
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d)
}

/**
 * Locale-aware formatting. In Bangla, numbers use Bangla digits with lakh/crore grouping
 * (১২,৫০,০০০) and the Taka sign.
 */
export function useFormat() {
  const { locale, t } = useI18n()
  const intlLocale = computed(() => (locale.value === 'bn' ? 'bn-BD' : 'en-IN'))
  const isBn = computed(() => locale.value === 'bn')

  function num(v: number | string | null | undefined, maxFraction = 2): string {
    if (v === null || v === undefined || v === '') return ''
    return new Intl.NumberFormat(intlLocale.value, { maximumFractionDigits: maxFraction }).format(Number(v))
  }

  function money(v: number | string | null | undefined): string {
    if (v === null || v === undefined || v === '') return ''
    const n = new Intl.NumberFormat(intlLocale.value, { minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(Number(v))
    return `৳ ${n}`
  }

  function date(v: string | Date | null | undefined): string {
    if (!v) return ''
    const d = typeof v === 'string' ? (v.length === 10 ? parseIso(v)! : new Date(v)) : v
    return new Intl.DateTimeFormat(intlLocale.value, { day: 'numeric', month: 'short', year: 'numeric' }).format(d)
  }

  function dateTime(v: string | null | undefined): string {
    if (!v) return ''
    return new Intl.DateTimeFormat(intlLocale.value, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(v))
  }

  /** Plain digits (ids, phone numbers, level numbers) in the active script. */
  function digits(v: string | number | null | undefined): string {
    if (v === null || v === undefined) return ''
    return isBn.value ? toBnDigits(String(v)) : String(v)
  }

  function tier(v: SkillTier | string | null | undefined): string {
    return v ? t(`labour.tiers.${v}`) : ''
  }

  function itemName(w: Pick<WorkItem, 'nameBn' | 'nameEn'> | null | undefined): string {
    if (!w) return ''
    return isBn.value ? w.nameBn : w.nameEn
  }

  return { num, money, date, dateTime, digits, tier, itemName, intlLocale, isBn }
}
