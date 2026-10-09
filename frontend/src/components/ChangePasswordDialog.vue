<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import { useNotify } from '@/composables/useNotify'

const visible = defineModel<boolean>('visible', { required: true })
const { t } = useI18n()
const notify = useNotify()

const current = ref('')
const next = ref('')
const saving = ref(false)
watch(visible, (v) => {
  if (v) current.value = next.value = ''
})

async function submit() {
  saving.value = true
  try {
    await http.post('/auth/change-password', { currentPassword: current.value, newPassword: next.value })
    notify.success(t('auth.passwordChanged'))
    visible.value = false
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Dialog v-model:visible="visible" modal :header="t('auth.changePassword')" :style="{ width: '420px', maxWidth: '95vw' }">
    <form class="form" @submit.prevent="submit">
      <div class="field">
        <label for="cp-current">{{ t('auth.currentPassword') }}</label>
        <Password v-model="current" input-id="cp-current" :feedback="false" toggle-mask required />
      </div>
      <div class="field">
        <label for="cp-new">{{ t('auth.newPassword') }}</label>
        <Password v-model="next" input-id="cp-new" toggle-mask required :minlength="8" />
      </div>
      <div class="flex">
        <span class="spacer" />
        <Button type="button" :label="t('common.cancel')" text @click="visible = false" />
        <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="next.length < 8 || !current" />
      </div>
    </form>
  </Dialog>
</template>
