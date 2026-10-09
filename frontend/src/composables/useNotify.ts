import { useToast } from 'primevue/usetoast'
import { useI18n } from 'vue-i18n'
import { errorOf } from '@/api/http'

export function useNotify() {
  const toast = useToast()
  const { t, te, locale } = useI18n()

  /** Translated text for an API error (codes come from the backend's ErrorCode enum). */
  function errorText(e: unknown): string {
    const err = errorOf(e)
    const reason = err.params?.reason
    if (typeof reason === 'string' && te(`errors.reasons.${reason}`)) return t(`errors.reasons.${reason}`)
    const key = `errors.${err.code}`
    if (!te(key)) return err.message
    const params: Record<string, unknown> = { ...(err.params ?? {}) }
    if (typeof params.max === 'number') params.max = new Intl.NumberFormat(locale.value === 'bn' ? 'bn-BD' : 'en-IN').format(params.max)
    if (Array.isArray(params.names)) params.names = (params.names as string[]).join(', ')
    if (typeof params.tier === 'string') params.tier = t(`labour.tiers.${params.tier}`)
    return t(key, params)
  }

  function error(e: unknown) {
    toast.add({ severity: 'error', summary: errorText(e), life: 6000 })
  }

  function success(message?: string) {
    toast.add({ severity: 'success', summary: message ?? t('common.saved'), life: 2500 })
  }

  return { error, success, errorText }
}
