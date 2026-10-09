<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import type { WorkItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'

const { t } = useI18n()
const auth = useAuthStore()
const notify = useNotify()
const fmt = useFormat()
const canEdit = computed(() => !auth.readOnly)

const UOMS = ['SFT', 'CFT', 'RFT', 'CUM', 'SQM', 'RM', 'NOS', 'KG', 'LS']

const rows = ref<WorkItem[]>([])
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    rows.value = (await http.get<WorkItem[]>('/app/work-items', { params: { includeInactive: true } })).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const open = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ code: '', nameBn: '', nameEn: '', uom: 'SFT', sortOrder: 100, active: true })
const saving = ref(false)

function openNew() {
  editingId.value = null
  form.value = { code: '', nameBn: '', nameEn: '', uom: 'SFT', sortOrder: (rows.value.length + 1) * 10, active: true }
  open.value = true
}
function openEdit(w: WorkItem) {
  editingId.value = w.id
  form.value = { code: w.code, nameBn: w.nameBn, nameEn: w.nameEn, uom: w.uom, sortOrder: w.sortOrder, active: w.active }
  open.value = true
}
async function save() {
  saving.value = true
  try {
    if (editingId.value) await http.put(`/app/work-items/${editingId.value}`, form.value)
    else await http.post('/app/work-items', form.value)
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
      <h1>{{ t('workItem.title') }}</h1>
      <div class="actions">
        <Button v-if="canEdit" :label="t('common.add')" icon="pi pi-plus" @click="openNew" />
      </div>
    </div>
    <div class="card">
      <DataTable :value="rows" :loading="loading" data-key="id" size="small" striped-rows>
        <Column :header="t('workItem.sortOrder')" style="width: 80px">
          <template #body="{ data }">{{ fmt.digits(data.sortOrder) }}</template>
        </Column>
        <Column field="code" :header="t('common.code')" />
        <Column field="nameBn" :header="t('workItem.nameBn')" />
        <Column field="nameEn" :header="t('workItem.nameEn')" />
        <Column field="uom" :header="t('workItem.uom')" />
        <Column :header="t('common.status')">
          <template #body="{ data }">
            <Tag :severity="data.active ? 'success' : 'secondary'" :value="data.active ? t('common.active') : t('common.inactive')" />
          </template>
        </Column>
        <Column v-if="canEdit" style="width: 1%">
          <template #body="{ data }">
            <Button icon="pi pi-pencil" text size="small" :aria-label="t('common.edit')" @click="openEdit(data)" />
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="open" modal :header="editingId ? t('common.edit') : t('common.add')" :style="{ width: '520px', maxWidth: '95vw' }">
      <form class="form" @submit.prevent="save">
        <div class="row">
          <div class="field">
            <label for="w-code">{{ t('common.code') }} *</label>
            <InputText id="w-code" v-model="form.code" :disabled="!!editingId" required pattern="[A-Za-z0-9_]+" />
          </div>
          <div class="field">
            <label for="w-uom">{{ t('workItem.uom') }} *</label>
            <Select input-id="w-uom" v-model="form.uom" :options="UOMS" editable />
          </div>
        </div>
        <div class="field">
          <label for="w-bn">{{ t('workItem.nameBn') }} *</label>
          <InputText id="w-bn" v-model="form.nameBn" required />
        </div>
        <div class="field">
          <label for="w-en">{{ t('workItem.nameEn') }} *</label>
          <InputText id="w-en" v-model="form.nameEn" required />
        </div>
        <div class="row">
          <div class="field">
            <label for="w-order">{{ t('workItem.sortOrder') }}</label>
            <InputNumber v-model="form.sortOrder" input-id="w-order" :use-grouping="false" :locale="fmt.intlLocale.value" />
          </div>
          <div class="field">
            <label for="w-active">{{ t('common.active') }}</label>
            <ToggleSwitch v-model="form.active" input-id="w-active" />
          </div>
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="open = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
