<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import type { Material, MaterialKind } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'

const { t } = useI18n()
const auth = useAuthStore()
const notify = useNotify()
const fmt = useFormat()
const canEdit = computed(() => !auth.readOnly)

const UOMS = ['BAG', 'KG', 'TON', 'CFT', 'SFT', 'RFT', 'NOS', 'PCS', 'LTR', 'LS']
const kindOptions = computed(() => (['CONSUMABLE', 'RENTABLE'] as MaterialKind[]).map((k) => ({ value: k, label: t(`material.kinds.${k}`) })))

const rows = ref<Material[]>([])
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    rows.value = (await http.get<Material[]>('/app/materials', { params: { includeInactive: true } })).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const open = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ code: '', nameBn: '', nameEn: '', uom: 'NOS', kind: 'CONSUMABLE' as MaterialKind, sortOrder: 100, active: true })
const saving = ref(false)

function openNew() {
  editingId.value = null
  form.value = { code: '', nameBn: '', nameEn: '', uom: 'NOS', kind: 'CONSUMABLE', sortOrder: (rows.value.length + 1) * 10, active: true }
  open.value = true
}
function openEdit(m: Material) {
  editingId.value = m.id
  form.value = { code: m.code, nameBn: m.nameBn, nameEn: m.nameEn, uom: m.uom, kind: m.kind, sortOrder: m.sortOrder, active: m.active }
  open.value = true
}
async function save() {
  saving.value = true
  try {
    if (editingId.value) await http.put(`/app/materials/${editingId.value}`, form.value)
    else await http.post('/app/materials', form.value)
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
      <h1>{{ t('material.title') }}</h1>
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
        <Column :header="t('material.kind')">
          <template #body="{ data }">{{ t(`material.kinds.${data.kind}`) }}</template>
        </Column>
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
            <label for="m-code">{{ t('common.code') }} *</label>
            <InputText id="m-code" v-model="form.code" :disabled="!!editingId" required pattern="[A-Za-z0-9_]+" />
          </div>
          <div class="field">
            <label for="m-uom">{{ t('workItem.uom') }} *</label>
            <Select input-id="m-uom" v-model="form.uom" :options="UOMS" editable />
          </div>
        </div>
        <div class="field">
          <label for="m-bn">{{ t('workItem.nameBn') }} *</label>
          <InputText id="m-bn" v-model="form.nameBn" required />
        </div>
        <div class="field">
          <label for="m-en">{{ t('workItem.nameEn') }} *</label>
          <InputText id="m-en" v-model="form.nameEn" required />
        </div>
        <div class="field">
          <label>{{ t('material.kind') }}</label>
          <SelectButton v-model="form.kind" :options="kindOptions" option-label="label" option-value="value" :allow-empty="false" />
        </div>
        <div class="row">
          <div class="field">
            <label for="m-order">{{ t('workItem.sortOrder') }}</label>
            <InputNumber v-model="form.sortOrder" input-id="m-order" :use-grouping="false" :locale="fmt.intlLocale.value" />
          </div>
          <div class="field">
            <label for="m-active">{{ t('common.active') }}</label>
            <ToggleSwitch v-model="form.active" input-id="m-active" />
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
