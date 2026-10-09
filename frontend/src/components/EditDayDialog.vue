<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import type { AttendanceDay, WorkItem } from '@/api/types'
import { useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import LocationPicker, { type LocationValue } from '@/components/LocationPicker.vue'

const props = defineProps<{ row: AttendanceDay; workItems: WorkItem[] }>()
const visible = defineModel<boolean>('visible', { required: true })
const emit = defineEmits<{ saved: [] }>()
const { t } = useI18n()
const fmt = useFormat()
const notify = useNotify()

interface EditSlice { key: number; loc: LocationValue; workItemId: number | null; dayFraction: number; otHours: number }
let seq = 0
const slices = ref<EditSlice[]>([])
const note = ref('')
const saving = ref(false)

watch(visible, (v) => {
  if (!v) return
  note.value = props.row.note ?? ''
  slices.value = props.row.allocations.map((a) => ({
    key: ++seq,
    loc: { siteId: a.siteId, buildingId: a.buildingId, floorId: a.floorId, unitId: a.unitId },
    workItemId: a.workItemId,
    dayFraction: Number(a.dayFraction),
    otHours: Number(a.otHours),
  }))
}, { immediate: true })

const total = computed(() => slices.value.reduce((s, x) => s + (x.dayFraction || 0), 0))
const itemOptions = computed(() => props.workItems.map((w) => ({ value: w.id, label: fmt.itemName(w) })))

function add() {
  const last = slices.value[slices.value.length - 1]
  slices.value.push({
    key: ++seq,
    loc: last ? { ...last.loc } : { siteId: null, buildingId: null, floorId: null, unitId: null },
    workItemId: null,
    dayFraction: Math.max(0, Math.round((1 - total.value) * 100) / 100),
    otHours: 0,
  })
}

async function save() {
  saving.value = true
  try {
    await http.put('/app/attendance/day', {
      labourId: props.row.labourId,
      workDate: props.row.workDate,
      note: note.value || null,
      allocations: slices.value.map((s) => ({ ...s.loc, workItemId: s.workItemId, dayFraction: s.dayFraction, otHours: s.otHours || 0 })),
    })
    notify.success()
    visible.value = false
    emit('saved')
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Dialog v-model:visible="visible" modal :header="`${t('attendance.editDay')} — ${row.labourName}, ${fmt.date(row.workDate)}`" :style="{ width: '860px', maxWidth: '96vw' }">
    <div class="form">
      <div v-for="(s, i) in slices" :key="s.key" class="slice">
        <LocationPicker v-model="s.loc" compact />
        <div class="slice-row">
          <div class="field grow">
            <label :for="`ed-item-${s.key}`">{{ t('attendance.step2') }}</label>
            <Select :input-id="`ed-item-${s.key}`" v-model="s.workItemId" :options="itemOptions" option-label="label" option-value="value" />
          </div>
          <div class="field">
            <label :for="`ed-frac-${s.key}`">{{ t('attendance.dayFraction') }}</label>
            <InputNumber v-model="s.dayFraction" :input-id="`ed-frac-${s.key}`" :min="0" :max="1" :step="0.25" :max-fraction-digits="2" show-buttons :locale="fmt.intlLocale.value" :input-style="{ width: '5rem' }" />
          </div>
          <div class="field">
            <label :for="`ed-ot-${s.key}`">{{ t('attendance.otHours') }}</label>
            <InputNumber v-model="s.otHours" :input-id="`ed-ot-${s.key}`" :min="0" :max="12" :max-fraction-digits="1" show-buttons :locale="fmt.intlLocale.value" :input-style="{ width: '5rem' }" />
          </div>
          <Button icon="pi pi-trash" text severity="danger" :aria-label="t('common.delete')" @click="slices.splice(i, 1)" />
        </div>
      </div>
      <div class="flex">
        <Button :label="t('attendance.addSlice')" icon="pi pi-plus" text @click="add" />
        <span class="spacer" />
        <span :class="total > 1 ? 'over' : 'muted'">{{ t('common.total') }}: {{ fmt.num(total) }} {{ t('common.days') }}</span>
      </div>
      <div class="field">
        <label for="ed-note">{{ t('common.note') }}</label>
        <InputText id="ed-note" v-model="note" />
      </div>
      <div class="flex">
        <span class="spacer" />
        <Button :label="t('common.cancel')" text @click="visible = false" />
        <Button :label="t('common.save')" icon="pi pi-save" :loading="saving" :disabled="total > 1 || slices.some((s) => !s.loc.siteId || !s.workItemId)" @click="save" />
      </div>
    </div>
  </Dialog>
</template>

<style scoped>
.slice { border: 1px solid var(--app-border); border-radius: 10px; padding: 12px; display: grid; gap: 10px; }
.slice-row { display: flex; gap: 10px; align-items: flex-end; flex-wrap: wrap; }
.slice-row .field { display: flex; flex-direction: column; gap: 4px; }
.slice-row .field label { font-size: 0.8rem; color: var(--app-muted); }
.grow { flex: 1; min-width: 200px; }
.grow :deep(.p-select) { width: 100%; }
.over { color: #b91c1c; font-weight: 600; }
</style>
