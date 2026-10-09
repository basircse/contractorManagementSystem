import { usePrimeVue, type PrimeVueLocaleOptions } from 'primevue/config'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import { persistLang, primeLocales, type Lang } from '@/i18n'
import { useAuthStore } from '@/stores/auth'

export function useLanguage() {
  const { locale } = useI18n()
  const primevue = usePrimeVue()
  const auth = useAuthStore()

  function apply(lang: Lang) {
    locale.value = lang
    primevue.config.locale = { ...primevue.config.locale, ...primeLocales[lang] } as PrimeVueLocaleOptions
    persistLang(lang)
  }

  /** Switch language and remember it on the user's profile. */
  function setLang(lang: Lang) {
    apply(lang)
    if (auth.isLoggedIn) {
      http.put('/auth/language', { lang }).catch(() => {})
    }
  }

  return { locale, apply, setLang }
}
