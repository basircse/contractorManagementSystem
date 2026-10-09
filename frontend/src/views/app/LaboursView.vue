<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import { SKILL_TIERS, type Labour, type SkillTier } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { isoDate, parseIso, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'

const { t } = useI18n()
const auth = useAuthStore()
const notify = useNotify()
const fmt = useFormat()
const canEdit = computed(() => !auth.readOnly)

const rows = ref<Labour[]>([])
const loading = ref(false)
const search = ref('')
const tierFilter = ref<SkillTier | null>(null)
const showInactive = ref(false)

const tierOptions = computed(() => SKILL_TIERS.map((v) => ({ value: v, label: fmt.tier(v) })))
const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  return rows.value.filter(
    (l) =>
      (showInactive.value || l.active) &&
      (!tierFilter.value || l.skillTier === tierFilter.value) &&
      (!q || l.name.toLowerCase().includes(q) || (l.phone ?? '').includes(q)),
  )
})

async function load() {
  loading.value = true
  try {
    rows.value = (await http.get<Labour[]>('/app/labours', { params: { includeInactive: true } })).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const open = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ name: '', phone: '', nid: '', address: '', skillTier: 'HELPER' as SkillTier, joinedOn: null as Date | null, active: true })
const saving = ref(false)

function openNew() {
  editingId.value = null
  form.value = { name: '', phone: '', nid: '', address: '', skillTier: 'HELPER', joinedOn: new Date(), active: true }
  open.value = true
}
function openEdit(l: Labour) {
  editingId.value = l.id
  form.value = { name: l.name, phone: l.phone ?? '', nid: l.nid ?? '', address: l.address ?? '', skillTier: l.skillTier, joinedOn: parseIso(l.joinedOn), active: l.active }
  open.value = true
}
async function save() {
  saving.value = true
  const f = form.value
  const body = { ...f, phone: f.phone || null, nid: f.nid || null, address: f.address || null, joinedOn: isoDate(f.joinedOn) }
  try {
    if (editingId.value) await http.put(`/app/labours/${editingId.value}`, body)
    else await http.post('/app/labours', body)
    open.value = false
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('labour.title') }}</h1>
      <div class="actions">
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText v-model="search" :placeholder="t('common.search')" />
        </IconField>
        <Select v-model="tierFilter" :options="tierOptions" option-label="label" option-value="value" :placeholder="t('labour.skillTier')" show-clear />
        <label class="flex small"><ToggleSwitch v-model="showInactive" /> {{ t('common.inactive') }}</label>
        <Button v-if="canEdit" :label="t('labour.newLabour')" icon="pi pi-plus" @click="openNew" />
      </div>
    </div>
    <div class="card">
      <DataTable :value="filtered" :loading="loading" data-key="id" size="small" striped-rows paginator :rows="25">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column field="name" :header="t('common.name')" sortable />
        <Column field="skillTier" :header="t('labour.skillTier')" sortable>
          <template #body="{ data }">{{ fmt.tier(data.skillTier) }}</template>
        </Column>
        <Column :header="t('common.phone')">
          <template #body="{ data }">{{ fmt.digits(data.phone) }}</template>
        </Column>
        <Column :header="t('labour.joinedOn')">
          <template #body="{ data }">{{ fmt.date(data.joinedOn) }}</template>
        </Column>
        <Column :header="t('common.status')">
          <template #body="{ data }">
            <Tag :severity="data.active ? 'success' : 'secondary'" :value="data.active ? t('common.active') : t('common.inactive')" />
          </template>
        </Column>
        <Column style="width: 1%">
          <template #body="{ data }">
            <div class="flex" style="flex-wrap: nowrap">
              <RouterLink :to="`/app/reports/labour/${data.id}`">
                <Button icon="pi pi-history" text size="small" v-tooltip.top="t('labour.history')" :aria-label="t('labour.history')" />
              </RouterLink>
              <Button v-if="canEdit" icon="pi pi-pencil" text size="small" :aria-label="t('common.edit')" @click="openEdit(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="open" modal :header="editingId ? t('common.edit') : t('labour.newLabour')" :style="{ width: '520px', maxWidth: '95vw' }">
      <form class="form" @submit.prevent="save">
        <div class="field">
          <label for="l-name">{{ t('common.name') }} *</label>
          <InputText id="l-name" v-model="form.name" required autofocus />
        </div>
        <div class="row">
          <div class="field">
            <label for="l-tier">{{ t('labour.skillTier') }} *</label>
            <Select input-id="l-tier" v-model="form.skillTier" :options="tierOptions" option-label="label" option-value="value" />
          </div>
          <div class="field">
            <label for="l-phone">{{ t('common.phone') }}</label>
            <InputText id="l-phone" v-model="form.phone" />
          </div>
        </div>
        <div class="row">
          <div class="field">
            <label for="l-nid">{{ t('labour.nid') }}</label>
            <InputText id="l-nid" v-model="form.nid" />
          </div>
          <div class="field">
            <label for="l-joined">{{ t('labour.joinedOn') }}</label>
            <DatePicker v-model="form.joinedOn" input-id="l-joined" show-icon date-format="dd/mm/yy" />
          </div>
        </div>
        <div class="field">
          <label for="l-address">{{ t('common.address') }}</label>
          <InputText id="l-address" v-model="form.address" />
        </div>
        <label class="flex"><ToggleSwitch v-model="form.active" /> {{ t('common.active') }}</label>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="open = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
