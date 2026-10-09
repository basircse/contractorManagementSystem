<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import type { Contractor } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const confirm = useConfirm()
const notify = useNotify()
const fmt = useFormat()

const rows = ref<Contractor[]>([])
const loading = ref(false)
const search = ref('')

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter((c) =>
    [c.name, c.ownerName, c.phone, c.username].some((v) => v?.toLowerCase().includes(q)),
  )
})
const counts = computed(() => ({
  total: rows.value.length,
  active: rows.value.filter((c) => c.status === 'ACTIVE').length,
  blocked: rows.value.filter((c) => c.status === 'BLOCKED').length,
}))

async function load() {
  loading.value = true
  try {
    rows.value = (await http.get<Contractor[]>('/admin/contractors')).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

// ------------------------------------------------------------ create / edit
const formOpen = ref(false)
const editing = ref<Contractor | null>(null)
const form = ref({ name: '', ownerName: '', phone: '', email: '', address: '', username: '', password: '' })
const saving = ref(false)

function openNew() {
  editing.value = null
  form.value = { name: '', ownerName: '', phone: '', email: '', address: '', username: '', password: '' }
  formOpen.value = true
}
function openEdit(c: Contractor) {
  editing.value = c
  form.value = { name: c.name, ownerName: c.ownerName ?? '', phone: c.phone ?? '', email: c.email ?? '', address: c.address ?? '', username: c.username ?? '', password: '' }
  formOpen.value = true
}
async function save() {
  saving.value = true
  const f = form.value
  const profile = { name: f.name, ownerName: f.ownerName || null, phone: f.phone || null, email: f.email || null, address: f.address || null }
  try {
    if (editing.value) {
      await http.put(`/admin/contractors/${editing.value.id}`, profile)
    } else {
      await http.post('/admin/contractors', { ...profile, username: f.username, password: f.password })
    }
    notify.success()
    formOpen.value = false
    await load()
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}

// ------------------------------------------------------------ block / unblock
const blockOpen = ref(false)
const blockTarget = ref<Contractor | null>(null)
const blockReason = ref('')
function openBlock(c: Contractor) {
  blockTarget.value = c
  blockReason.value = ''
  blockOpen.value = true
}
async function doBlock() {
  try {
    await http.post(`/admin/contractors/${blockTarget.value!.id}/block`, { reason: blockReason.value || null })
    blockOpen.value = false
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  }
}
function unblock(c: Contractor) {
  confirm.require({
    message: `${t('admin.unblock')}: ${c.name}?`,
    header: t('common.confirm'),
    acceptLabel: t('common.yes'),
    rejectLabel: t('common.no'),
    accept: async () => {
      try {
        await http.post(`/admin/contractors/${c.id}/unblock`)
        notify.success()
        await load()
      } catch (e) {
        notify.error(e)
      }
    },
  })
}

// ------------------------------------------------------------ reset password
const resetOpen = ref(false)
const resetTarget = ref<Contractor | null>(null)
const newPassword = ref('')
function openReset(c: Contractor) {
  resetTarget.value = c
  newPassword.value = ''
  resetOpen.value = true
}
async function doReset() {
  try {
    await http.post(`/admin/contractors/${resetTarget.value!.id}/reset-password`, { password: newPassword.value })
    resetOpen.value = false
    notify.success()
  } catch (e) {
    notify.error(e)
  }
}

function view(c: Contractor) {
  auth.startViewing(c.id, c.name)
  router.push({ name: 'dashboard' })
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('admin.title') }}</h1>
      <div class="actions">
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText v-model="search" :placeholder="t('common.search')" />
        </IconField>
        <Button :label="t('admin.newContractor')" icon="pi pi-plus" @click="openNew" />
      </div>
    </div>

    <div class="grid cols-3" style="margin-bottom: 16px">
      <div class="card stat"><span class="label">{{ t('admin.totalContractors') }}</span><span class="value">{{ fmt.num(counts.total) }}</span></div>
      <div class="card stat"><span class="label">{{ t('admin.activeContractors') }}</span><span class="value">{{ fmt.num(counts.active) }}</span></div>
      <div class="card stat"><span class="label">{{ t('admin.blockedContractors') }}</span><span class="value">{{ fmt.num(counts.blocked) }}</span></div>
    </div>

    <div class="card">
      <DataTable :value="filtered" :loading="loading" data-key="id" striped-rows size="small" responsive-layout="scroll">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('admin.company')">
          <template #body="{ data }">
            <div style="font-weight: 600">{{ data.name }}</div>
            <div class="muted small">{{ data.ownerName }}</div>
          </template>
        </Column>
        <Column :header="t('common.phone')">
          <template #body="{ data }">{{ fmt.digits(data.phone) }}</template>
        </Column>
        <Column field="username" :header="t('admin.loginUser')" />
        <Column :header="t('admin.lastLogin')">
          <template #body="{ data }"><span class="small">{{ fmt.dateTime(data.lastLoginAt) }}</span></template>
        </Column>
        <Column :header="t('common.status')">
          <template #body="{ data }">
            <Tag v-if="data.status === 'ACTIVE'" severity="success" :value="t('common.active')" />
            <Tag v-else severity="danger" :value="t('admin.blocked')" v-tooltip.top="data.blockedReason || undefined" />
          </template>
        </Column>
        <Column :header="t('common.actions')" style="width: 1%">
          <template #body="{ data }">
            <div class="flex nowrap" style="flex-wrap: nowrap">
              <Button :label="t('admin.viewBusiness')" icon="pi pi-eye" size="small" outlined @click="view(data)" />
              <Button icon="pi pi-pencil" size="small" text v-tooltip.top="t('common.edit')" :aria-label="t('common.edit')" @click="openEdit(data)" />
              <Button icon="pi pi-key" size="small" text v-tooltip.top="t('admin.resetPassword')" :aria-label="t('admin.resetPassword')" @click="openReset(data)" />
              <Button v-if="data.status === 'ACTIVE'" icon="pi pi-ban" size="small" text severity="danger" v-tooltip.top="t('admin.block')" :aria-label="t('admin.block')" @click="openBlock(data)" />
              <Button v-else icon="pi pi-lock-open" size="small" text severity="success" v-tooltip.top="t('admin.unblock')" :aria-label="t('admin.unblock')" @click="unblock(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="formOpen" modal :header="editing ? t('common.edit') : t('admin.newContractor')" :style="{ width: '560px', maxWidth: '95vw' }">
      <form class="form" @submit.prevent="save">
        <div class="field">
          <label for="c-name">{{ t('admin.company') }} *</label>
          <InputText id="c-name" v-model="form.name" required />
        </div>
        <div class="row">
          <div class="field">
            <label for="c-owner">{{ t('admin.owner') }}</label>
            <InputText id="c-owner" v-model="form.ownerName" />
          </div>
          <div class="field">
            <label for="c-phone">{{ t('common.phone') }}</label>
            <InputText id="c-phone" v-model="form.phone" />
          </div>
        </div>
        <div class="field">
          <label for="c-email">{{ t('common.email') }}</label>
          <InputText id="c-email" v-model="form.email" type="email" />
        </div>
        <div class="field">
          <label for="c-address">{{ t('common.address') }}</label>
          <Textarea id="c-address" v-model="form.address" rows="2" auto-resize />
        </div>
        <div v-if="!editing" class="row">
          <div class="field">
            <label for="c-user">{{ t('auth.username') }} *</label>
            <InputText id="c-user" v-model="form.username" required pattern="[A-Za-z0-9._-]+" minlength="3" />
          </div>
          <div class="field">
            <label for="c-pass">{{ t('auth.password') }} *</label>
            <Password v-model="form.password" input-id="c-pass" toggle-mask required :minlength="8" />
          </div>
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="formOpen = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:visible="blockOpen" modal :header="t('admin.block')" :style="{ width: '460px', maxWidth: '95vw' }">
      <form class="form" @submit.prevent="doBlock">
        <p style="margin: 0">{{ t('admin.blockConfirm', { name: blockTarget?.name }) }}</p>
        <div class="field">
          <label for="b-reason">{{ t('admin.blockReason') }} ({{ t('common.optional') }})</label>
          <Textarea id="b-reason" v-model="blockReason" rows="2" auto-resize />
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="blockOpen = false" />
          <Button type="submit" :label="t('admin.block')" severity="danger" icon="pi pi-ban" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:visible="resetOpen" modal :header="t('admin.resetPassword')" :style="{ width: '420px', maxWidth: '95vw' }">
      <form class="form" @submit.prevent="doReset">
        <p class="muted" style="margin: 0">{{ resetTarget?.name }} ({{ resetTarget?.username }})</p>
        <div class="field">
          <label for="r-pass">{{ t('auth.newPassword') }}</label>
          <Password v-model="newPassword" input-id="r-pass" toggle-mask required :minlength="8" />
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="resetOpen = false" />
          <Button type="submit" :label="t('common.save')" :disabled="newPassword.length < 8" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
