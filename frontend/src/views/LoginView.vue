<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useLanguage } from '@/composables/useLanguage'
import { useNotify } from '@/composables/useNotify'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const { locale, setLang } = useLanguage()
const notify = useNotify()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

const langOptions = [
  { label: 'বাংলা', value: 'bn' },
  { label: 'English', value: 'en' },
]
const lang = computed({
  get: () => locale.value,
  set: (v: string) => v && setLang(v as 'bn' | 'en'),
})

async function submit() {
  loading.value = true
  error.value = ''
  try {
    const me = await auth.login(username.value.trim(), password.value)
    if (me.preferredLang && me.preferredLang !== locale.value) setLang(me.preferredLang)
    router.replace(me.role === 'ADMIN' ? { name: 'contractors' } : { name: 'dashboard' })
  } catch (e) {
    error.value = notify.errorText(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <div class="login card">
      <div class="lang">
        <SelectButton v-model="lang" :options="langOptions" option-label="label" option-value="value" :allow-empty="false" size="small" />
      </div>
      <div class="logo"><i class="pi pi-building-columns" /></div>
      <h1>{{ t('app.name') }}</h1>
      <p class="muted">{{ t('auth.subtitle') }}</p>
      <form class="form" @submit.prevent="submit">
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
        <div class="field">
          <label for="username">{{ t('auth.username') }}</label>
          <InputText id="username" v-model="username" autocomplete="username" required autofocus />
        </div>
        <div class="field">
          <label for="password">{{ t('auth.password') }}</label>
          <Password v-model="password" input-id="password" :feedback="false" toggle-mask autocomplete="current-password" required />
        </div>
        <Button type="submit" :label="t('auth.login')" icon="pi pi-sign-in" :loading="loading" />
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-wrap { min-height: 100vh; display: grid; place-items: center; padding: 16px; background: linear-gradient(160deg, #0f2a2a 0%, #134e4a 55%, #f4f6f8 55%); }
.login { width: 400px; max-width: 100%; padding: 28px; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.12); }
.lang { display: flex; justify-content: flex-end; }
.logo { width: 56px; height: 56px; border-radius: 14px; background: #ccfbf1; color: #0f766e; display: grid; place-items: center; font-size: 1.6rem; margin: 6px 0 12px; }
h1 { margin: 0; font-size: 1.4rem; }
p { margin: 4px 0 18px; }
</style>
