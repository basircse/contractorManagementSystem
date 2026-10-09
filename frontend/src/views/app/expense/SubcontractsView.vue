<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import type { Party, Subcontract, SubcontractBill, WorkItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { isoDate, parseIso, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import { statusSeverity, useLookups } from '@/composables/useLookups'
import LocationPicker, { type LocationValue } from '@/components/LocationPicker.vue'

type Status = Subcontract['status']

const { t } = useI18n()
const auth = useAuthStore()
const confirm = useConfirm()
const notify = useNotify()
const fmt = useFormat()
const lookups = useLookups()
const canEdit = computed(() => !auth.readOnly)

const rows = ref<Subcontract[]>([])
const loading = ref(false)
const status = ref<Status | null>('ACTIVE')
const statusOptions = computed(() => [
  { value: null, label: t('common.all') },
  ...(['ACTIVE', 'COMPLETED', 'CANCELLED'] as const).map((s) => ({ value: s, label: t(`subcontract.statuses.${s}`) })),
])
const shown = computed(() => rows.value.filter((s) => !status.value || s.status === status.value))
const totals = computed(() => ({
  contract: shown.value.reduce((s, x) => s + Number(x.contractAmount), 0),
  billed: shown.value.reduce((s, x) => s + Number(x.billedAmount), 0),
}))
const subParties = computed(() => [...lookups.parties.value].sort((a, b) => (a.type === b.type ? 0 : a.type === 'SUBCONTRACTOR' ? -1 : 1)))

async function load() {
  loading.value = true
  try {
    rows.value = (await http.get<Subcontract[]>('/app/subcontracts')).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await Promise.all([load(), lookups.load('workItems', 'parties')])
})

// ------------------------------------------------------------------ form

const open = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const location = ref<LocationValue>({ siteId: null, buildingId: null, floorId: null, unitId: null })
const form = ref(blank())
function blank() {
  return { partyId: null as number | null, title: '', workItemId: null as number | null, uom: '', quantity: null as number | null,
    rate: null as number | null, contractAmount: 0, startDate: new Date() as Date | null, note: '' }
}
const computedAmount = computed(() => (form.value.quantity && form.value.rate ? Math.round(form.value.quantity * form.value.rate * 100) / 100 : null))
function openNew() {
  editingId.value = null
  form.value = blank()
  location.value = { siteId: null, buildingId: null, floorId: null, unitId: null }
  open.value = true
}
function openEdit(s: Subcontract) {
  editingId.value = s.id
  form.value = { partyId: s.partyId, title: s.title, workItemId: s.workItemId, uom: s.uom ?? '', quantity: s.quantity !== null ? Number(s.quantity) : null,
    rate: s.rate !== null ? Number(s.rate) : null, contractAmount: Number(s.contractAmount), startDate: parseIso(s.startDate), note: s.note ?? '' }
  location.value = { siteId: s.siteId, buildingId: s.buildingId, floorId: s.floorId, unitId: s.unitId }
  open.value = true
}
async function save() {
  saving.value = true
  try {
    const f = form.value
    const body = { ...f, ...location.value, startDate: isoDate(f.startDate), uom: f.uom || null, note: f.note || null }
    if (editingId.value) await http.put(`/app/subcontracts/${editingId.value}`, body)
    else await http.post('/app/subcontracts', body)
    open.value = false
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
async function setStatus(s: Subcontract, st: Status) {
  try {
    await http.post(`/app/subcontracts/${s.id}/status`, { status: st })
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  }
}
function remove(s: Subcontract) {
  confirm.require({
    message: `${s.title} — ${fmt.money(s.contractAmount)}`, header: t('common.delete'), acceptLabel: t('common.yes'), rejectLabel: t('common.no'),
    acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/subcontracts/${s.id}`)
        notify.success()
        await load()
      } catch (e) {
        notify.error(e)
      }
    },
  })
}

// ------------------------------------------------------------------ bills

const detailOpen = ref(false)
const detail = ref<Subcontract | null>(null)
const bill = ref({ billDate: new Date() as Date | null, quantity: null as number | null, amount: null as number | null, note: '' })
async function openDetail(s: Subcontract) {
  try {
    detail.value = (await http.get<Subcontract>(`/app/subcontracts/${s.id}`)).data
    bill.value = { billDate: new Date(), quantity: null, amount: null, note: '' }
    detailOpen.value = true
  } catch (e) {
    notify.error(e)
  }
}
async function addBill() {
  if (!detail.value) return
  saving.value = true
  try {
    detail.value = (await http.post<Subcontract>(`/app/subcontracts/${detail.value.id}/bills`, {
      ...bill.value, billDate: isoDate(bill.value.billDate), note: bill.value.note || null,
    })).data
    bill.value = { billDate: new Date(), quantity: null, amount: null, note: '' }
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
function deleteBill(b: SubcontractBill) {
  confirm.require({
    message: t('subcontract.deleteBillConfirm', { amount: fmt.money(b.amount) }), header: t('common.delete'), acceptLabel: t('common.yes'),
    rejectLabel: t('common.no'), acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        detail.value = (await http.delete<Subcontract>(`/app/subcontract-bills/${b.id}`)).data
        notify.success()
        await load()
      } catch (e) {
        notify.error(e)
      }
    },
  })
}
const billPreview = computed(() => bill.value.amount ?? (detail.value?.rate && bill.value.quantity ? Math.round(bill.value.quantity * Number(detail.value.rate) * 100) / 100 : null))
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('subcontract.title') }}</h1>
      <div class="actions">
        <SelectButton v-model="status" :options="statusOptions" option-label="label" option-value="value" :allow-empty="false" size="small" />
        <Button v-if="canEdit" :label="t('subcontract.new')" icon="pi pi-plus" @click="openNew" />
      </div>
    </div>

    <div class="grid cols-3" style="margin-bottom: 16px">
      <div class="card stat"><span class="label">{{ t('subcontract.contractAmount') }}</span><span class="value">{{ fmt.money(totals.contract) }}</span></div>
      <div class="card stat"><span class="label">{{ t('subcontract.billed') }}</span><span class="value">{{ fmt.money(totals.billed) }}</span></div>
      <div class="card stat"><span class="label">{{ t('subcontract.remaining') }}</span><span class="value">{{ fmt.money(totals.contract - totals.billed) }}</span></div>
    </div>

    <div class="card">
      <DataTable :value="shown" :loading="loading" data-key="id" size="small" striped-rows paginator :rows="25">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('subcontract.work')">
          <template #body="{ data }">
            <a href="#" class="link" @click.prevent="openDetail(data)">{{ data.title }}</a>
            <div class="muted small">{{ data.locationLabel }}<span v-if="data.workItemId"> · {{ fmt.itemName(lookups.workItems.value.find((w: WorkItem) => w.id === data.workItemId)) }}</span></div>
          </template>
        </Column>
        <Column field="partyName" :header="t('doc.party')" />
        <Column :header="t('subcontract.contractAmount')" body-class="num" header-class="num">
          <template #body="{ data }">
            {{ fmt.money(data.contractAmount) }}
            <div v-if="data.quantity && data.rate" class="muted small">{{ fmt.num(data.quantity, 3) }} {{ data.uom }} × {{ fmt.money(data.rate) }}</div>
          </template>
        </Column>
        <Column :header="t('subcontract.billed')" style="min-width: 150px">
          <template #body="{ data }">
            <div class="num">{{ fmt.money(data.billedAmount) }}</div>
            <div class="bar"><span :style="{ width: Math.min(100, (Number(data.billedAmount) / Math.max(1, Number(data.contractAmount))) * 100) + '%' }" /></div>
          </template>
        </Column>
        <Column :header="t('subcontract.remaining')" body-class="num" header-class="num"><template #body="{ data }"><strong>{{ fmt.money(data.remaining) }}</strong></template></Column>
        <Column :header="t('common.status')">
          <template #body="{ data }"><Tag :severity="statusSeverity(data.status)" :value="t(`subcontract.statuses.${data.status}`)" /></template>
        </Column>
        <Column style="width: 1%" body-class="nowrap">
          <template #body="{ data }">
            <Button icon="pi pi-file-edit" text size="small" :aria-label="t('subcontract.addBill')" v-tooltip.top="t('subcontract.bills')" @click="openDetail(data)" />
            <template v-if="canEdit">
              <Button v-if="data.status === 'ACTIVE'" icon="pi pi-check" text size="small" severity="success" :aria-label="t('workOrder.complete')" v-tooltip.top="t('workOrder.complete')" @click="setStatus(data, 'COMPLETED')" />
              <Button v-else icon="pi pi-replay" text size="small" :aria-label="t('workOrder.reactivate')" v-tooltip.top="t('workOrder.reactivate')" @click="setStatus(data, 'ACTIVE')" />
              <Button icon="pi pi-pencil" text size="small" :aria-label="t('common.edit')" @click="openEdit(data)" />
              <Button v-if="!Number(data.billedAmount)" icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" @click="remove(data)" />
            </template>
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="open" modal :header="editingId ? t('common.edit') : t('subcontract.new')" :style="{ width: '720px', maxWidth: '96vw' }">
      <form class="form" @submit.prevent="save">
        <div class="row">
          <div class="field">
            <label for="sc-party">{{ t('doc.party') }} *</label>
            <Select v-model="form.partyId" input-id="sc-party" :options="subParties" :option-label="(p: Party) => `${p.name}${p.trade ? ' — ' + p.trade : ''}`" option-value="id" filter />
          </div>
          <div class="field">
            <label for="sc-start">{{ t('workOrder.startDate') }}</label>
            <DatePicker v-model="form.startDate" input-id="sc-start" date-format="dd/mm/yy" show-icon />
          </div>
        </div>
        <div class="field">
          <label for="sc-title">{{ t('subcontract.work') }} *</label>
          <InputText id="sc-title" v-model="form.title" />
        </div>
        <LocationPicker v-model="location" />
        <div class="field">
          <label for="sc-item">{{ t('doc.workItem') }}</label>
          <Select v-model="form.workItemId" input-id="sc-item" :options="lookups.workItems.value" :option-label="(w: WorkItem) => fmt.itemName(w)" option-value="id" :placeholder="t('doc.noWorkItem')" show-clear filter />
        </div>
        <div class="grid cols-4">
          <div class="field">
            <label for="sc-qty">{{ t('doc.quantity') }}</label>
            <InputNumber v-model="form.quantity" input-id="sc-qty" :min="0" :max-fraction-digits="3" :locale="fmt.intlLocale.value" />
          </div>
          <div class="field">
            <label for="sc-uom">{{ t('doc.uom') }}</label>
            <InputText id="sc-uom" v-model="form.uom" />
          </div>
          <div class="field">
            <label for="sc-rate">{{ t('doc.rate') }}</label>
            <InputNumber v-model="form.rate" input-id="sc-rate" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ " />
          </div>
          <div class="field">
            <label for="sc-amt">{{ t('subcontract.contractAmount') }}</label>
            <InputNumber v-if="computedAmount === null" v-model="form.contractAmount" input-id="sc-amt" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ " />
            <strong v-else style="padding-top: 8px">{{ fmt.money(computedAmount) }}</strong>
          </div>
        </div>
        <small class="muted">{{ t('subcontract.lumpSumHint') }}</small>
        <div class="field">
          <label for="sc-note">{{ t('common.note') }}</label>
          <InputText id="sc-note" v-model="form.note" />
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="open = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="!form.partyId || !form.title || !location.siteId" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:visible="detailOpen" modal :header="detail ? `${detail.title} — ${detail.partyName}` : ''" :style="{ width: '760px', maxWidth: '96vw' }">
      <template v-if="detail">
        <div class="grid cols-3" style="margin-bottom: 12px">
          <div class="stat"><span class="label">{{ t('subcontract.contractAmount') }}</span><span class="value">{{ fmt.money(detail.contractAmount) }}</span></div>
          <div class="stat"><span class="label">{{ t('subcontract.billed') }}</span><span class="value">{{ fmt.money(detail.billedAmount) }}</span></div>
          <div class="stat"><span class="label">{{ t('subcontract.remaining') }}</span><span class="value">{{ fmt.money(detail.remaining) }}</span></div>
        </div>
        <DataTable :value="detail.bills ?? []" size="small" striped-rows>
          <template #empty><span class="muted">{{ t('common.none') }}</span></template>
          <Column :header="t('common.date')"><template #body="{ data }">{{ fmt.date(data.billDate) }}</template></Column>
          <Column :header="t('doc.quantity')" body-class="num" header-class="num"><template #body="{ data }">{{ data.quantity !== null ? `${fmt.num(data.quantity, 3)} ${detail?.uom ?? ''}` : '' }}</template></Column>
          <Column field="note" :header="t('common.note')" />
          <Column :header="t('doc.amount')" body-class="num" header-class="num"><template #body="{ data }"><strong>{{ fmt.money(data.amount) }}</strong></template></Column>
          <Column v-if="canEdit" style="width: 1%">
            <template #body="{ data }"><Button icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" @click="deleteBill(data)" /></template>
          </Column>
        </DataTable>
        <form v-if="canEdit && detail.status !== 'CANCELLED'" class="form mt" @submit.prevent="addBill">
          <h3 class="sub">{{ t('subcontract.addBill') }}</h3>
          <div class="grid cols-3">
            <div class="field">
              <label for="sb-date">{{ t('common.date') }} *</label>
              <DatePicker v-model="bill.billDate" input-id="sb-date" date-format="dd/mm/yy" show-icon />
            </div>
            <div class="field">
              <label for="sb-qty">{{ t('doc.quantity') }}</label>
              <InputNumber v-model="bill.quantity" input-id="sb-qty" :min="0" :max-fraction-digits="3" :locale="fmt.intlLocale.value" />
            </div>
            <div class="field">
              <label for="sb-amt">{{ t('doc.amount') }}</label>
              <InputNumber v-model="bill.amount" input-id="sb-amt" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ "
                           :placeholder="detail.rate ? t('subcontract.billAmountHint') : ''" />
            </div>
          </div>
          <div class="field">
            <label for="sb-note">{{ t('common.note') }}</label>
            <InputText id="sb-note" v-model="bill.note" />
          </div>
          <div class="flex">
            <span class="small">{{ t('doc.amount') }}: <strong>{{ billPreview !== null ? fmt.money(billPreview) : '—' }}</strong></span>
            <span class="spacer" />
            <Button type="submit" :label="t('subcontract.addBill')" icon="pi pi-plus" :loading="saving" :disabled="!bill.billDate || !billPreview" />
          </div>
        </form>
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.link { color: var(--p-primary-700); font-weight: 600; text-decoration: none; }
.sub { font-size: 0.95rem; margin: 0; }
</style>
