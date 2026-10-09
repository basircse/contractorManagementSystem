<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import { setUnauthorizedHandler } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const toast = useToast()
const { t } = useI18n()

// Expired token or contractor blocked by admin: drop the session and go to login.
setUnauthorizedHandler((code) => {
  if (!auth.isLoggedIn) return
  auth.logout()
  toast.add({ severity: 'warn', summary: t(`errors.${code}`), life: 6000 })
  router.replace({ name: 'login' })
})
</script>

<template>
  <Toast position="top-center" />
  <ConfirmDialog />
  <RouterView />
</template>
