<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import { PAY_METHODS, type Material, type PayMethod, type Purchase, type PurchaseLine, type WorkItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { isoDate, parseIso, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import { useLookups } from '@/composables/useLookups'
import LocationPicker, { type LocationValue } from '@/components/LocationPicker.vue'

const { t } = useI18n()
const auth = useAuthStore()
const confirm = useConfirm()
const notify = useNotify()
const fmt = useFormat()
const lookups = useLookups()
const canEdit = computed(() => !auth.readOnly)

const today = new Date()
const from = ref<Date | null>(new Date(today.getFullYear(), today.getMonth(), 1))
const to = ref<Date | null>(today)
const siteId = ref<number | null>(null)
const rows = ref<Purchase[]>([])
const loading = ref(false)
const total = computed(() => rows.value.reduce((s, p) => s + Number(p.totalAmount), 0))
const methodOptions = computed(() => PAY_METHODS.map((m) => ({ value: m, label: t(`doc.payMethods.${m}`) })))

const materialName = (id: number | null) => {
  const m = lookups.materials.value.find((x) => x.id === id)
  return m ? fmt.itemName(m) : ''
}
const lineLabel = (l: PurchaseLine) => l.description || materialName(l.materialId)

async function load() {
  loading.value = true
  try {
    rows.value = (await http.get<Purchase[]>('/app/purchases', {
      params: { from: isoDate(from.value), to: isoDate(to.value), siteId: siteId.value ?? undefined },
    })).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await Promise.all([load(), lookups.load('sites', 'workItems', 'materials', 'parties')])
})

// ------------------------------------------------------------------ form

const open = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const location = ref<LocationValue>({ siteId: null, buildingId: null, floorId: null, unitId: null })
const form = ref(blank())
function blank() {
  return {
    purchaseDate: new Date() as Date | null, partyId: null as number | null, invoiceNo: '', workItemId: null as number | null,
    paidAmount: 0, payMethod: 'CASH' as PayMethod, note: '',
    lines: [{ materialId: null, description: '', uom: '', quantity: 1, rate: 0 }] as PurchaseLine[],
  }
}
const formTotal = computed(() => form.value.lines.reduce((s, l) => s + Math.round((Number(l.quantity) || 0) * (Number(l.rate) || 0) * 100) / 100, 0))

function openNew() {
  editingId.value = null
  form.value = blank()
  location.value = { siteId: siteId.value, buildingId: null, floorId: null, unitId: null }
  open.value = true
}
function openEdit(p: Purchase) {
  editingId.value = p.id
  form.value = {
    purchaseDate: parseIso(p.purchaseDate), partyId: p.partyId, invoiceNo: p.invoiceNo ?? '', workItemId: p.workItemId,
    paidAmount: Number(p.paidAmount), payMethod: 'CASH', note: p.note ?? '',
    lines: p.lines.map((l) => ({ materialId: l.materialId, description: l.description ?? '', uom: l.uom ?? '', quantity: Number(l.quantity), rate: Number(l.rate) })),
  }
  location.value = { siteId: p.siteId, buildingId: p.buildingId, floorId: p.floorId, unitId: p.unitId }
  open.value = true
}
function pickMaterial(l: PurchaseLine, id: number | null) {
  l.materialId = id
  const m = lookups.materials.value.find((x) => x.id === id)
  if (m && !l.uom) l.uom = m.uom
}
async function save() {
  saving.value = true
  try {
    const f = form.value
    const body = {
      purchaseDate: isoDate(f.purchaseDate), partyId: f.partyId, invoiceNo: f.invoiceNo || null, ...location.value,
      workItemId: f.workItemId, paidAmount: f.partyId ? f.paidAmount || 0 : null, payMethod: f.payMethod, note: f.note || null,
      lines: f.lines.filter((l) => l.materialId || l.description?.trim()).map((l) => ({ ...l, description: l.description || null, uom: l.uom || null })),
    }
    if (editingId.value) await http.put(`/app/purchases/${editingId.value}`, body)
    else await http.post('/app/purchases', body)
    open.value = false
    notify.success()
    await Promise.all([load(), lookups.load('parties')])
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
function remove(p: Purchase) {
  confirm.require({
    message: t('purchase.deleteConfirm', { amount: fmt.money(p.totalAmount) }), header: t('common.delete'),
    acceptLabel: t('common.yes'), rejectLabel: t('common.no'), acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/purchases/${p.id}`)
        notify.success()
        await load()
      } catch (e) {
        notify.error(e)
      }
    },
  })
}
const canSave = computed(() => !!form.value.purchaseDate && !!location.value.siteId && form.value.lines.some((l) => (l.materialId || l.description?.trim()) && Number(l.quantity) > 0))
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('purchase.title') }}</h1>
      <div class="actions">
        <Button v-if="canEdit" :label="t('purchase.new')" icon="pi pi-plus" @click="openNew" />
      </div>
    </div>

    <div class="card">
      <div class="filters">
        <div class="field">
          <label for="pu-from">{{ t('common.from') }}</label>
          <DatePicker v-model="from" input-id="pu-from" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="pu-to">{{ t('common.to') }}</label>
          <DatePicker v-model="to" input-id="pu-to" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="pu-site">{{ t('hierarchy.site') }}</label>
          <Select v-model="siteId" input-id="pu-site" :options="lookups.sites.value" option-label="name" option-value="id" :placeholder="t('common.all')" show-clear />
        </div>
        <Button :label="t('common.apply')" icon="pi pi-filter" :loading="loading" @click="load" />
        <span class="spacer" />
        <div class="stat" style="text-align: right"><span class="label">{{ t('common.total') }}</span><span class="value">{{ fmt.money(total) }}</span></div>
      </div>
    </div>

    <div class="card">
      <DataTable :value="rows" :loading="loading" data-key="id" size="small" striped-rows paginator :rows="25">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('common.date')"><template #body="{ data }">{{ fmt.date(data.purchaseDate) }}<div v-if="data.invoiceNo" class="muted small">{{ data.invoiceNo }}</div></template></Column>
        <Column :header="t('purchase.items')">
          <template #body="{ data }">
            <div v-for="l in data.lines" :key="l.id" class="small">{{ lineLabel(l) }} — {{ fmt.num(l.quantity, 3) }} {{ l.uom }} × {{ fmt.money(l.rate) }}</div>
          </template>
        </Column>
        <Column :header="t('report.location')">
          <template #body="{ data }">{{ data.locationLabel }}<div v-if="data.workItemId" class="muted small">{{ fmt.itemName(lookups.workItems.value.find((w: WorkItem) => w.id === data.workItemId)) }}</div></template>
        </Column>
        <Column :header="t('doc.party')"><template #body="{ data }">{{ data.partyName ?? t('purchase.cash') }}</template></Column>
        <Column :header="t('doc.amount')" body-class="num" header-class="num">
          <template #body="{ data }">
            <strong>{{ fmt.money(data.totalAmount) }}</strong>
            <div v-if="data.partyId && Number(data.paidAmount) < Number(data.totalAmount)" class="small due">{{ t('purchase.onCredit') }}: {{ fmt.money(Number(data.totalAmount) - Number(data.paidAmount)) }}</div>
          </template>
        </Column>
        <Column v-if="canEdit" style="width: 1%" body-class="nowrap">
          <template #body="{ data }">
            <Button icon="pi pi-pencil" text size="small" :aria-label="t('common.edit')" @click="openEdit(data)" />
            <Button icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" @click="remove(data)" />
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="open" modal maximizable :header="editingId ? t('common.edit') : t('purchase.new')" :style="{ width: '960px', maxWidth: '98vw' }">
      <form class="form" @submit.prevent="save">
        <div class="grid cols-3">
          <div class="field">
            <label for="pf-date">{{ t('common.date') }} *</label>
            <DatePicker v-model="form.purchaseDate" input-id="pf-date" date-format="dd/mm/yy" show-icon />
          </div>
          <div class="field">
            <label for="pf-party">{{ t('doc.party') }}</label>
            <Select v-model="form.partyId" input-id="pf-party" :options="lookups.parties.value" option-label="name" option-value="id" :placeholder="t('purchase.cash')" show-clear filter />
          </div>
          <div class="field">
            <label for="pf-inv">{{ t('purchase.invoiceNo') }}</label>
            <InputText id="pf-inv" v-model="form.invoiceNo" />
          </div>
        </div>
        <LocationPicker v-model="location" />
        <div class="field">
          <label for="pf-item">{{ t('doc.workItem') }}</label>
          <Select v-model="form.workItemId" input-id="pf-item" :options="lookups.workItems.value" :option-label="(w: WorkItem) => fmt.itemName(w)" option-value="id" :placeholder="t('doc.noWorkItem')" show-clear filter />
        </div>

        <div class="lines">
          <table>
            <thead>
              <tr>
                <th style="width: 220px">{{ t('material.material') }}</th>
                <th>{{ t('doc.description') }}</th>
                <th style="width: 80px">{{ t('doc.uom') }}</th>
                <th style="width: 120px" class="num">{{ t('doc.quantity') }}</th>
                <th style="width: 130px" class="num">{{ t('doc.rate') }}</th>
                <th style="width: 120px" class="num">{{ t('doc.amount') }}</th>
                <th style="width: 40px" />
              </tr>
            </thead>
            <tbody>
              <tr v-for="(l, i) in form.lines" :key="i">
                <td>
                  <Select :model-value="l.materialId" :options="lookups.materials.value" :option-label="(m: Material) => fmt.itemName(m)" option-value="id"
                          show-clear filter size="small" class="w-full" :aria-label="t('material.material')" @update:model-value="pickMaterial(l, $event)" />
                </td>
                <td><InputText v-model="l.description" size="small" class="w-full" :placeholder="l.materialId ? '' : t('purchase.itemHint')" :aria-label="t('doc.description')" /></td>
                <td><InputText v-model="l.uom" size="small" class="w-full" :aria-label="t('doc.uom')" /></td>
                <td><InputNumber v-model="l.quantity" :min="0" :max-fraction-digits="3" :locale="fmt.intlLocale.value" size="small" input-class="num" class="w-full" :aria-label="t('doc.quantity')" /></td>
                <td><InputNumber v-model="l.rate" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" size="small" input-class="num" class="w-full" :aria-label="t('doc.rate')" /></td>
                <td class="num">{{ fmt.money(Math.round((Number(l.quantity) || 0) * (Number(l.rate) || 0) * 100) / 100) }}</td>
                <td><Button icon="pi pi-times" text rounded size="small" severity="danger" :aria-label="t('doc.removeLine')" @click="form.lines.splice(i, 1)" /></td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td colspan="5"><Button :label="t('doc.addLine')" icon="pi pi-plus" text size="small" @click="form.lines.push({ materialId: null, description: '', uom: '', quantity: 1, rate: 0 })" /></td>
                <td class="num"><strong>{{ fmt.money(formTotal) }}</strong></td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>

        <div v-if="form.partyId" class="row">
          <div class="field">
            <label for="pf-paid">{{ t('purchase.paidNow') }}</label>
            <InputNumber v-model="form.paidAmount" input-id="pf-paid" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ " />
          </div>
          <div class="field">
            <label>{{ t('doc.method') }}</label>
            <SelectButton v-model="form.payMethod" :options="methodOptions" option-label="label" option-value="value" :allow-empty="false" size="small" />
          </div>
        </div>
        <div class="field">
          <label for="pf-note">{{ t('common.note') }}</label>
          <InputText id="pf-note" v-model="form.note" />
        </div>
        <div class="flex">
          <span v-if="form.partyId" class="small">{{ t('purchase.onCredit') }}: <strong>{{ fmt.money(Math.max(0, formTotal - (Number(form.paidAmount) || 0))) }}</strong></span>
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="open = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="!canSave" />
        </div>
      </form>
    </Dialog>
  </div>
</template>

<style scoped>
.lines { overflow-x: auto; }
.lines table { width: 100%; border-collapse: collapse; min-width: 780px; }
.lines th { text-align: left; font-size: 0.8rem; color: var(--app-muted); font-weight: 500; padding: 6px 4px; border-bottom: 1px solid var(--app-border); }
.lines th.num { text-align: right; }
.lines td { padding: 4px; border-bottom: 1px solid var(--app-border); }
.lines tfoot td { border: none; }
.w-full { width: 100%; }
.due { color: #b45309; }
:deep(.p-inputnumber-input) { width: 100%; }
</style>
