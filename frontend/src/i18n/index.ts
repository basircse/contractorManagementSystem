import { createI18n } from 'vue-i18n'
import type { PrimeVueLocaleOptions } from 'primevue/config'
import bn from './bn'
import en from './en'

export type Lang = 'bn' | 'en'
const LANG_KEY = 'ccms.lang'

function storedLang(): Lang {
  try {
    const v = localStorage.getItem(LANG_KEY)
    return v === 'en' ? 'en' : 'bn'
  } catch {
    return 'bn'
  }
}

export const i18n = createI18n({
  legacy: false,
  locale: storedLang(),
  fallbackLocale: 'en',
  messages: { bn, en },
})

/** Calendar texts for PrimeVue's DatePicker etc. */
export const primeLocales: Record<Lang, Partial<PrimeVueLocaleOptions>> = {
  bn: {
    firstDayOfWeek: 6,
    dayNames: ['রবিবার', 'সোমবার', 'মঙ্গলবার', 'বুধবার', 'বৃহস্পতিবার', 'শুক্রবার', 'শনিবার'],
    dayNamesShort: ['রবি', 'সোম', 'মঙ্গল', 'বুধ', 'বৃহঃ', 'শুক্র', 'শনি'],
    dayNamesMin: ['র', 'সো', 'ম', 'বু', 'বৃ', 'শু', 'শ'],
    monthNames: ['জানুয়ারি', 'ফেব্রুয়ারি', 'মার্চ', 'এপ্রিল', 'মে', 'জুন', 'জুলাই', 'আগস্ট', 'সেপ্টেম্বর', 'অক্টোবর', 'নভেম্বর', 'ডিসেম্বর'],
    monthNamesShort: ['জানু', 'ফেব্রু', 'মার্চ', 'এপ্রি', 'মে', 'জুন', 'জুলা', 'আগ', 'সেপ্টে', 'অক্টো', 'নভে', 'ডিসে'],
    today: 'আজ',
    clear: 'মুছুন',
    weekHeader: 'সপ্তাহ',
    emptyMessage: 'কোনো ফলাফল নেই',
    emptyFilterMessage: 'কোনো ফলাফল নেই',
    emptySearchMessage: 'কোনো ফলাফল নেই',
    emptySelectionMessage: 'কিছু নির্বাচিত নয়',
    selectionMessage: '{0}টি নির্বাচিত',
    accept: 'হ্যাঁ',
    reject: 'না',
    choose: 'বাছাই',
    upload: 'আপলোড',
    cancel: 'বাতিল',
    dateFormat: 'dd/mm/yy',
  },
  en: {
    firstDayOfWeek: 6,
    dayNames: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
    dayNamesShort: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
    dayNamesMin: ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'],
    monthNames: ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'],
    monthNamesShort: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
    today: 'Today',
    clear: 'Clear',
    weekHeader: 'Wk',
    emptyMessage: 'No results',
    emptyFilterMessage: 'No results',
    emptySearchMessage: 'No results',
    emptySelectionMessage: 'Nothing selected',
    selectionMessage: '{0} selected',
    accept: 'Yes',
    reject: 'No',
    choose: 'Choose',
    upload: 'Upload',
    cancel: 'Cancel',
    dateFormat: 'dd/mm/yy',
  },
}

export function persistLang(lang: Lang) {
  try {
    localStorage.setItem(LANG_KEY, lang)
  } catch {
    /* storage unavailable */
  }
  document.documentElement.lang = lang
}
