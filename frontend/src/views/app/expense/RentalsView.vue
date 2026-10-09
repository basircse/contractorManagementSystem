<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import type { Material, Party, Rental, WorkItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { isoDate, parseIso, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import { statusSeverity, useLookups } from '@/composables/useLookups'
import LocationPicker, { type LocationValue } from '@/components/LocationPicker.vue'

const { t } = useI18n()
const auth = useAuthStore()
const confirm = useConfirm()
const notify = useNotify()
const fmt = useFormat()
const lookups = useLookups()
const canEdit = computed(() => !auth.readOnly)

const rows = ref<Rental[]>([])
const loading = ref(false)
const showReturned = ref(false)
const siteId = ref<number | null>(null)
const shown = computed(() => rows.value.filter((r) => (showReturned.value || r.status === 'OUT') && (!siteId.value || r.siteId === siteId.value)))
const totals = computed(() => ({
  charged: shown.value.reduce((s, r) => s + Number(r.charged), 0),
  accrued: shown.value.reduce((s, r) => s + Number(r.accrued), 0),
}))
const expanded = ref<Record<number, boolean>>({})

const itemLabel = (r: Rental) => {
  const m = lookups.materials.value.find((x) => x.id === r.materialId)
  return [m ? fmt.itemName(m) : null, r.description].filter(Boolean).join(' — ')
}

async function load() {
  loading.value = true
  try {
    rows.value = (await http.get<Rental[]>('/app/rentals')).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await Promise.all([load(), lookups.load('sites', 'workItems', 'materials', 'parties')])
})
const rentables = computed(() => [...lookups.materials.value].sort((a, b) => (a.kind === b.kind ? 0 : a.kind === 'RENTABLE' ? -1 : 1)))

// ------------------------------------------------------------------ new / edit

const open = ref(false)
const editing = ref<Rental | null>(null)
const saving = ref(false)
const location = ref<LocationValue>({ siteId: null, buildingId: null, floorId: null, unitId: null })
const form = ref(blank())
function blank() {
  return { partyId: null as number | null, materialId: null as number | null, description: '', uom: '', workItemId: null as number | null,
    quantity: 1, ratePerDay: 0, startDate: new Date() as Date | null, note: '' }
}
const locked = computed(() => !!editing.value?.events.length)
function openNew() {
  editing.value = null
  form.value = blank()
  location.value = { siteId: siteId.value, buildingId: null, floorId: null, unitId: null }
  open.value = true
}
function openEdit(r: Rental) {
  editing.value = r
  form.value = { partyId: r.partyId, materialId: r.materialId, description: r.description ?? '', uom: r.uom ?? '', workItemId: r.workItemId,
    quantity: Number(r.quantity), ratePerDay: Number(r.ratePerDay), startDate: parseIso(r.startDate), note: r.note ?? '' }
  location.value = { siteId: r.siteId, buildingId: r.buildingId, floorId: r.floorId, unitId: r.unitId }
  open.value = true
}
function pickMaterial(id: number | null) {
  form.value.materialId = id
  const m = lookups.materials.value.find((x) => x.id === id)
  if (m && !form.value.uom) form.value.uom = m.uom
}
async function save() {
  saving.value = true
  try {
    const f = form.value
    const body = { ...f, ...location.value, startDate: isoDate(f.startDate), description: f.description || null, uom: f.uom || null, note: f.note || null }
    if (editing.value) await http.put(`/app/rentals/${editing.value.id}`, body)
    else await http.post('/app/rentals', body)
    open.value = false
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}

// ------------------------------------------------------------------ charge / return

const actOpen = ref(false)
const actKind = ref<'charge' | 'return'>('charge')
const actOf = ref<Rental | null>(null)
const act = ref({ date: new Date() as Date | null, quantity: 0, note: '' })
function openAct(r: Rental, kind: 'charge' | 'return') {
  actOf.value = r
  actKind.value = kind
  const d = new Date()
  if (kind === 'charge') d.setDate(0) // last day of the previous month
  act.value = { date: kind === 'charge' && d >= (parseIso(r.chargedUntil ?? r.startDate) ?? d) ? d : new Date(), quantity: Number(r.quantityOut), note: '' }
  actOpen.value = true
}
/** Rent the action will post, for the preview. */
const preview = computed(() => {
  const r = actOf.value
  if (!r || !act.value.date) return 0
  const from = parseIso(r.chargedUntil) ? new Date(parseIso(r.chargedUntil)!.getTime() + 864e5) : parseIso(r.startDate)!
  const days = Math.max(0, Math.round((act.value.date.getTime() - from.getTime()) / 864e5) + 1)
  return Math.round(Number(r.quantityOut) * Number(r.ratePerDay) * days * 100) / 100
})
async function runAct() {
  if (!actOf.value) return
  saving.value = true
  try {
    const body = actKind.value === 'charge'
      ? { date: isoDate(act.value.date), note: act.value.note || null }
      : { date: isoDate(act.value.date), quantity: act.value.quantity, note: act.value.note || null }
    await http.post(`/app/rentals/${actOf.value.id}/${actKind.value}`, body)
    actOpen.value = false
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
function undo(r: Rental) {
  const last = r.events[r.events.length - 1]
  if (!last) return
  confirm.require({
    message: t('rental.undoConfirm', { type: t(`rental.events.${last.type}`), date: fmt.date(last.eventDate) }), header: t('rental.undo'),
    acceptLabel: t('common.yes'), rejectLabel: t('common.no'), acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/rentals/${r.id}/events/last`)
        notify.success()
        await load()
      } catch (e) {
        notify.error(e)
      }
    },
  })
}
function remove(r: Rental) {
  confirm.require({
    message: t('rental.deleteConfirm'), header: t('common.delete'), acceptLabel: t('common.yes'), rejectLabel: t('common.no'),
    acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/rentals/${r.id}`)
        notify.success()
        await load()
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
      <h1>{{ t('rental.title') }}</h1>
      <div class="actions">
        <Select v-model="siteId" :options="lookups.sites.value" option-label="name" option-value="id" :placeholder="t('hierarchy.site')" show-clear :aria-label="t('hierarchy.site')" />
        <label class="flex small"><ToggleSwitch v-model="showReturned" /> {{ t('rental.showReturned') }}</label>
        <Button v-if="canEdit" :label="t('rental.new')" icon="pi pi-plus" @click="openNew" />
      </div>
    </div>
    <p class="hint"><i class="pi pi-info-circle" /> {{ t('rental.chargeHint') }}</p>

    <div class="grid cols-2 mt" style="margin-bottom: 16px">
      <div class="card stat"><span class="label">{{ t('rental.charged') }}</span><span class="value">{{ fmt.money(totals.charged) }}</span></div>
      <div class="card stat"><span class="label">{{ t('rental.accrued') }}</span><span class="value">{{ fmt.money(totals.accrued) }}</span><span class="muted small">{{ t('rental.accruedHint') }}</span></div>
    </div>

    <div class="card">
      <DataTable :value="shown" :loading="loading" data-key="id" size="small" striped-rows>
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('material.material')">
          <template #body="{ data }">
            <strong>{{ itemLabel(data) }}</strong>
            <div class="muted small">{{ data.partyName ?? '—' }} · {{ data.locationLabel }}</div>
          </template>
        </Column>
        <Column :header="t('rental.outQty')" body-class="num" header-class="num">
          <template #body="{ data }">
            <strong>{{ fmt.num(data.quantityOut, 3) }}</strong> <span class="muted small">/ {{ fmt.num(data.quantity, 3) }} {{ data.uom }}</span>
          </template>
        </Column>
        <Column :header="t('rental.ratePerDay')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.ratePerDay) }}</template></Column>
        <Column :header="t('rental.start')">
          <template #body="{ data }">
            {{ fmt.date(data.startDate) }}
            <div class="muted small">{{ fmt.num(data.daysOut) }} {{ t('rental.daysOut') }}<span v-if="data.chargedUntil"> · {{ t('rental.chargedUntil') }} {{ fmt.date(data.chargedUntil) }}</span></div>
          </template>
        </Column>
        <Column :header="t('rental.charged')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.charged) }}</template></Column>
        <Column :header="t('rental.accrued')" body-class="num" header-class="num"><template #body="{ data }"><strong>{{ fmt.money(data.accrued) }}</strong></template></Column>
        <Column :header="t('common.status')">
          <template #body="{ data }">
            <Tag :severity="statusSeverity(data.status)" :value="t(`rental.statuses.${data.status}`)" />
            <div v-if="data.endDate" class="muted small">{{ fmt.date(data.endDate) }}</div>
          </template>
        </Column>
        <Column style="width: 1%" body-class="nowrap">
          <template #body="{ data }">
            <Button v-if="data.events.length" :icon="expanded[data.id] ? 'pi pi-chevron-up' : 'pi pi-history'" text size="small" :aria-label="t('rental.history')"
                    v-tooltip.top="t('rental.history')" @click="expanded[data.id] = !expanded[data.id]" />
            <template v-if="canEdit">
              <Button v-if="data.status === 'OUT'" icon="pi pi-calculator" text size="small" :aria-label="t('rental.charge')" v-tooltip.top="t('rental.charge')" @click="openAct(data, 'charge')" />
              <Button v-if="data.status === 'OUT'" icon="pi pi-reply" text size="small" severity="success" :aria-label="t('rental.giveBack')" v-tooltip.top="t('rental.giveBack')" @click="openAct(data, 'return')" />
              <Button v-if="data.events.length" icon="pi pi-undo" text size="small" severity="secondary" :aria-label="t('rental.undo')" v-tooltip.top="t('rental.undo')" @click="undo(data)" />
              <Button icon="pi pi-pencil" text size="small" :aria-label="t('common.edit')" @click="openEdit(data)" />
              <Button icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" @click="remove(data)" />
            </template>
          </template>
        </Column>
      </DataTable>

      <template v-for="r in shown" :key="'h' + r.id">
        <div v-if="expanded[r.id]" class="history">
          <div class="small"><strong>{{ itemLabel(r) }}</strong> — {{ t('rental.history') }}</div>
          <table>
            <tr v-for="e in r.events" :key="e.id">
              <td>{{ fmt.date(e.eventDate) }}</td>
              <td>{{ t(`rental.events.${e.type}`) }}<span v-if="e.type === 'RETURN'"> · {{ fmt.num(e.quantity, 3) }} {{ r.uom }}</span></td>
              <td class="muted">
                <template v-if="e.days">{{ fmt.date(e.chargeFrom) }} – {{ fmt.date(e.chargeTo) }} · {{ fmt.num(e.chargedQty, 3) }} × {{ fmt.num(e.days) }} {{ t('common.days') }}</template>
              </td>
              <td class="num">{{ fmt.money(e.amount) }}</td>
              <td class="muted">{{ e.note }}</td>
            </tr>
          </table>
        </div>
      </template>
    </div>

    <Dialog v-model:visible="open" modal :header="editing ? t('common.edit') : t('rental.new')" :style="{ width: '720px', maxWidth: '96vw' }">
      <form class="form" @submit.prevent="save">
        <div class="row">
          <div class="field">
            <label for="rt-mat">{{ t('material.material') }}</label>
            <Select :model-value="form.materialId" input-id="rt-mat" :options="rentables" :option-label="(m: Material) => fmt.itemName(m)" option-value="id" show-clear filter :disabled="locked" @update:model-value="pickMaterial" />
          </div>
          <div class="field">
            <label for="rt-desc">{{ t('doc.description') }}</label>
            <InputText id="rt-desc" v-model="form.description" />
          </div>
        </div>
        <div class="field">
          <label for="rt-party">{{ t('rental.owner') }}</label>
          <Select v-model="form.partyId" input-id="rt-party" :options="lookups.parties.value" :option-label="(p: Party) => p.name" option-value="id" show-clear filter :disabled="locked" />
        </div>
        <LocationPicker v-if="!locked" v-model="location" />
        <div v-if="!locked" class="field">
          <label for="rt-item">{{ t('doc.workItem') }}</label>
          <Select v-model="form.workItemId" input-id="rt-item" :options="lookups.workItems.value" :option-label="(w: WorkItem) => fmt.itemName(w)" option-value="id" :placeholder="t('doc.noWorkItem')" show-clear filter />
        </div>
        <div class="grid cols-4">
          <div class="field">
            <label for="rt-qty">{{ t('doc.quantity') }} *</label>
            <InputNumber v-model="form.quantity" input-id="rt-qty" :min="0" :max-fraction-digits="3" :locale="fmt.intlLocale.value" :disabled="locked" />
          </div>
          <div class="field">
            <label for="rt-uom">{{ t('doc.uom') }}</label>
            <InputText id="rt-uom" v-model="form.uom" :disabled="locked" />
          </div>
          <div class="field">
            <label for="rt-rate">{{ t('rental.ratePerDay') }}</label>
            <InputNumber v-model="form.ratePerDay" input-id="rt-rate" :min="0" :max-fraction-digits="4" :locale="fmt.intlLocale.value" prefix="৳ " :disabled="locked" />
          </div>
          <div class="field">
            <label for="rt-start">{{ t('rental.start') }} *</label>
            <DatePicker v-model="form.startDate" input-id="rt-start" date-format="dd/mm/yy" show-icon :disabled="locked" />
          </div>
        </div>
        <small class="muted">{{ t('rental.borrowedHint') }}</small>
        <div class="field">
          <label for="rt-note">{{ t('common.note') }}</label>
          <InputText id="rt-note" v-model="form.note" />
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="open = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="!location.siteId || !form.quantity || !form.startDate || (!form.materialId && !form.description)" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:visible="actOpen" modal :header="actKind === 'charge' ? t('rental.charge') : t('rental.giveBack')" :style="{ width: '460px', maxWidth: '95vw' }">
      <form v-if="actOf" class="form" @submit.prevent="runAct">
        <div class="small"><strong>{{ itemLabel(actOf) }}</strong> · {{ t('rental.outQty') }}: {{ fmt.num(actOf.quantityOut, 3) }} {{ actOf.uom }}</div>
        <div class="row">
          <div class="field">
            <label for="ra-date">{{ actKind === 'charge' ? t('rental.chargeUntil') : t('common.date') }} *</label>
            <DatePicker v-model="act.date" input-id="ra-date" date-format="dd/mm/yy" show-icon />
          </div>
          <div v-if="actKind === 'return'" class="field">
            <label for="ra-qty">{{ t('rental.returnQty') }} *</label>
            <InputNumber v-model="act.quantity" input-id="ra-qty" :min="0" :max="Number(actOf.quantityOut)" :max-fraction-digits="3" :locale="fmt.intlLocale.value" />
          </div>
        </div>
        <div class="field">
          <label for="ra-note">{{ t('common.note') }}</label>
          <InputText id="ra-note" v-model="act.note" />
        </div>
        <div class="small">{{ t('rental.charged') }}: <strong>{{ fmt.money(preview) }}</strong></div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="actOpen = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="!act.date || (actKind === 'return' && !act.quantity)" />
        </div>
      </form>
    </Dialog>
  </div>
</template>

<style scoped>
.hint { margin: 0; color: var(--app-muted); font-size: 0.9rem; display: flex; gap: 6px; align-items: baseline; }
.history { margin-top: 12px; padding: 10px 12px; background: #f8fafa; border: 1px solid var(--app-border); border-radius: 8px; }
.history table { width: 100%; border-collapse: collapse; font-size: 0.88rem; margin-top: 6px; }
.history td { padding: 4px 6px; border-bottom: 1px solid var(--app-border); }
</style>
