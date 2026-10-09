<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import { SKILL_TIERS, type AttendanceDay, type Labour, type SkillTier, type WorkItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { isoDate, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import LocationPicker, { type LocationValue } from '@/components/LocationPicker.vue'
import EditDayDialog from '@/components/EditDayDialog.vue'

const { t } = useI18n()
const auth = useAuthStore()
const confirm = useConfirm()
const notify = useNotify()
const fmt = useFormat()
const canEdit = computed(() => !auth.readOnly)

const LOC_KEY = 'ccms.attendance.location'
function savedLocation(): LocationValue {
  try {
    const v = localStorage.getItem(LOC_KEY)
    if (v) return JSON.parse(v)
  } catch {
    /* ignore */
  }
  return { siteId: null, buildingId: null, floorId: null, unitId: null }
}

const date = ref<Date>(new Date())
const location = ref<LocationValue>(savedLocation())
const workItemId = ref<number | null>(null)
const fraction = ref(1)
const otHours = ref(0)
const selectedIds = ref<number[]>([])
const tierFilter = ref<SkillTier | null>(null)
const search = ref('')

const workItems = ref<WorkItem[]>([])
const labours = ref<Labour[]>([])
const day = ref<AttendanceDay[]>([])
const loadingDay = ref(false)
const saving = ref(false)

const fractionOptions = computed(() => [
  { value: 1, label: t('attendance.fullDay') },
  { value: 0.5, label: t('attendance.halfDay') },
  { value: 0.25, label: t('attendance.quarterDay') },
])
const tierOptions = computed(() => SKILL_TIERS.map((v) => ({ value: v, label: fmt.tier(v) })))
const itemById = computed(() => new Map(workItems.value.map((w) => [w.id, w])))

/** How much of today is already booked per worker. */
const usedByLabour = computed(() => new Map(day.value.map((d) => [d.labourId, Number(d.totalFraction)])))
const visibleLabours = computed(() => {
  const q = search.value.trim().toLowerCase()
  return labours.value.filter((l) => (!tierFilter.value || l.skillTier === tierFilter.value) && (!q || l.name.toLowerCase().includes(q)))
})
function remaining(l: Labour) {
  return 1 - (usedByLabour.value.get(l.id) ?? 0)
}
function canTake(l: Labour) {
  return remaining(l) + 1e-9 >= fraction.value
}
function toggle(l: Labour) {
  if (!canTake(l)) return
  const i = selectedIds.value.indexOf(l.id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(l.id)
}
function selectAllVisible() {
  const ids = visibleLabours.value.filter(canTake).map((l) => l.id)
  const allSelected = ids.every((id) => selectedIds.value.includes(id))
  selectedIds.value = allSelected ? selectedIds.value.filter((id) => !ids.includes(id)) : [...new Set([...selectedIds.value, ...ids])]
}

const dayTotal = computed(() => day.value.reduce((s, d) => s + Number(d.cost), 0))
const canSubmit = computed(() => !!location.value.siteId && !!workItemId.value && selectedIds.value.length > 0)

async function loadDay() {
  loadingDay.value = true
  try {
    day.value = (await http.get<AttendanceDay[]>('/app/attendance', { params: { date: isoDate(date.value) } })).data
    selectedIds.value = selectedIds.value.filter((id) => {
      const l = labours.value.find((x) => x.id === id)
      return l && canTake(l)
    })
  } catch (e) {
    notify.error(e)
  } finally {
    loadingDay.value = false
  }
}

onMounted(async () => {
  try {
    const [w, l] = await Promise.all([http.get<WorkItem[]>('/app/work-items'), http.get<Labour[]>('/app/labours')])
    workItems.value = w.data
    labours.value = l.data
  } catch (e) {
    notify.error(e)
  }
  await loadDay()
})
watch(date, loadDay)
watch(location, (v) => {
  try {
    localStorage.setItem(LOC_KEY, JSON.stringify(v))
  } catch {
    /* ignore */
  }
}, { deep: true })

function shiftDay(n: number) {
  const d = new Date(date.value)
  d.setDate(d.getDate() + n)
  date.value = d
}

async function submit() {
  saving.value = true
  try {
    await http.post('/app/attendance/assign', {
      workDate: isoDate(date.value),
      labourIds: selectedIds.value,
      slice: { ...location.value, workItemId: workItemId.value, dayFraction: fraction.value, otHours: otHours.value || 0 },
    })
    notify.success(t('attendance.assigned', { n: fmt.num(selectedIds.value.length) }))
    selectedIds.value = []
    otHours.value = 0
    await loadDay()
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}

// ------------------------------------------------------------ edit / delete a day
const editOpen = ref(false)
const editRow = ref<AttendanceDay | null>(null)
function openEdit(row: AttendanceDay) {
  editRow.value = row
  editOpen.value = true
}
function remove(row: AttendanceDay) {
  confirm.require({
    message: `${row.labourName}: ${t('attendance.deleteConfirm')}`,
    header: t('common.delete'),
    acceptLabel: t('common.yes'),
    rejectLabel: t('common.no'),
    acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/attendance/${row.id}`)
        notify.success()
        await loadDay()
      } catch (e) {
        notify.error(e)
      }
    },
  })
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('attendance.title') }}</h1>
      <div class="actions">
        <Button icon="pi pi-chevron-left" text rounded :aria-label="'-1'" @click="shiftDay(-1)" />
        <DatePicker v-model="date" date-format="dd/mm/yy" show-icon :max-date="new Date()" input-id="att-date" />
        <Button icon="pi pi-chevron-right" text rounded :aria-label="'+1'" @click="shiftDay(1)" />
      </div>
    </div>

    <div v-if="canEdit" class="card">
      <h2>{{ t('attendance.step1') }}</h2>
      <LocationPicker v-model="location" />

      <h2 class="mt">{{ t('attendance.step2') }}</h2>
      <div class="items">
        <button
          v-for="w in workItems" :key="w.id" type="button" class="item-chip"
          :class="{ on: workItemId === w.id }" @click="workItemId = w.id"
        >{{ fmt.itemName(w) }}</button>
      </div>
      <div class="flex mt">
        <SelectButton v-model="fraction" :options="fractionOptions" option-label="label" option-value="value" :allow-empty="false" />
        <label class="flex small" for="att-ot">
          {{ t('attendance.otHours') }}
          <InputNumber v-model="otHours" input-id="att-ot" :min="0" :max="12" :max-fraction-digits="1" :locale="fmt.intlLocale.value" show-buttons button-layout="horizontal" :step="1" :input-style="{ width: '4rem' }" />
        </label>
      </div>

      <h2 class="mt">{{ t('attendance.step3') }}</h2>
      <div class="flex">
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText v-model="search" :placeholder="t('common.search')" />
        </IconField>
        <Select v-model="tierFilter" :options="tierOptions" option-label="label" option-value="value" :placeholder="t('labour.skillTier')" show-clear />
        <Button :label="t('common.all')" icon="pi pi-check-square" text @click="selectAllVisible" />
        <span class="muted small">{{ t('attendance.selected', { n: fmt.num(selectedIds.length) }) }}</span>
      </div>
      <div class="workers mt">
        <button
          v-for="l in visibleLabours" :key="l.id" type="button" class="worker"
          :class="{ on: selectedIds.includes(l.id), full: !canTake(l) }" :disabled="!canTake(l)" @click="toggle(l)"
        >
          <i :class="selectedIds.includes(l.id) ? 'pi pi-check-circle' : 'pi pi-circle'" />
          <span class="wname">{{ l.name }}</span>
          <span class="wtier">{{ fmt.tier(l.skillTier) }}</span>
          <span v-if="(usedByLabour.get(l.id) ?? 0) > 0" class="wleft">
            {{ remaining(l) > 0 ? `${fmt.num(remaining(l))} ${t('attendance.remaining')}` : t('attendance.alreadyFull') }}
          </span>
        </button>
      </div>
      <div class="flex mt">
        <span class="spacer" />
        <Button :label="t('attendance.assign')" icon="pi pi-save" :disabled="!canSubmit" :loading="saving" @click="submit" />
      </div>
    </div>

    <div class="card">
      <div class="flex" style="margin-bottom: 8px">
        <h2 style="margin: 0">{{ t('attendance.todays') }} — {{ fmt.date(date) }}</h2>
        <span class="spacer" />
        <span class="muted">{{ t('attendance.workers') }}: {{ fmt.num(day.length) }} · {{ t('common.total') }}: <strong>{{ fmt.money(dayTotal) }}</strong></span>
      </div>
      <DataTable :value="day" :loading="loadingDay" data-key="id" size="small" striped-rows>
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('common.name')">
          <template #body="{ data }">
            <div style="font-weight: 600">{{ data.labourName }}</div>
            <div class="muted small">{{ fmt.tier(data.skillTier) }}</div>
          </template>
        </Column>
        <Column :header="t('attendance.worked')">
          <template #body="{ data }">
            <div v-for="a in data.allocations" :key="a.id" class="alloc">
              <Tag :value="fmt.itemName(itemById.get(a.workItemId))" severity="info" />
              <span class="small">{{ fmt.num(a.dayFraction) }} {{ t('common.days') }}<template v-if="Number(a.otHours) > 0"> + {{ fmt.num(a.otHours) }} {{ t('common.hours') }} OT</template></span>
              <span class="muted small">{{ a.locationLabel }}</span>
            </div>
          </template>
        </Column>
        <Column :header="t('attendance.cost')" body-class="num" header-class="num">
          <template #body="{ data }">{{ fmt.money(data.cost) }}</template>
        </Column>
        <Column v-if="canEdit" style="width: 1%">
          <template #body="{ data }">
            <div class="flex" style="flex-wrap: nowrap">
              <Button icon="pi pi-pencil" text size="small" :aria-label="t('attendance.editDay')" v-tooltip.top="t('attendance.editDay')" @click="openEdit(data)" />
              <Button icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" @click="remove(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <EditDayDialog v-if="editRow" v-model:visible="editOpen" :row="editRow" :work-items="workItems" @saved="loadDay" />
  </div>
</template>

<style scoped>
.items { display: flex; flex-wrap: wrap; gap: 8px; }
.item-chip {
  border: 1px solid var(--app-border); background: #fff; border-radius: 999px; padding: 7px 14px; cursor: pointer;
  font: inherit; color: inherit;
}
.item-chip:hover { border-color: var(--p-primary-400); }
.item-chip.on { background: var(--p-primary-600); border-color: var(--p-primary-600); color: #fff; }
.workers { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 8px; }
.worker {
  display: grid; grid-template-columns: auto 1fr; grid-template-rows: auto auto; column-gap: 8px; align-items: center;
  text-align: left; border: 1px solid var(--app-border); background: #fff; border-radius: 10px; padding: 8px 10px;
  cursor: pointer; font: inherit; color: inherit;
}
.worker i { grid-row: span 2; font-size: 1.1rem; color: var(--app-muted); }
.worker.on { border-color: var(--p-primary-500); background: #f0fdfa; }
.worker.on i { color: var(--p-primary-600); }
.worker.full { opacity: 0.45; cursor: not-allowed; }
.wname { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wtier { font-size: 0.8rem; color: var(--app-muted); }
.wleft { grid-column: 2; font-size: 0.75rem; color: #b45309; }
.alloc { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; padding: 2px 0; }
</style>
