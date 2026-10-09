<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import type { Bill, BillSummary, ClientReceipt, WorkOrder, WorkOrderStatus } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { isoDate, parseIso, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import { statusSeverity, useLookups } from '@/composables/useLookups'
import WorkOrderDialog from '@/components/income/WorkOrderDialog.vue'
import BillDialog from '@/components/income/BillDialog.vue'
import ReceiptDialog from '@/components/income/ReceiptDialog.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const confirm = useConfirm()
const notify = useNotify()
const fmt = useFormat()
const lookups = useLookups()
const canEdit = computed(() => !auth.readOnly)

const wo = ref<WorkOrder | null>(null)
const id = computed(() => Number(route.params.id))

async function load() {
  try {
    wo.value = (await http.get<WorkOrder>(`/app/work-orders/${id.value}`)).data
    resetPlan()
  } catch (e) {
    notify.error(e)
  }
}
onMounted(async () => {
  await Promise.all([load(), lookups.load('workItems')])
})

const itemName = (wid: number | null) => {
  const w = lookups.workItems.value.find((x) => x.id === wid)
  return w ? fmt.itemName(w) : ''
}

// ------------------------------------------------------------------ header actions

const editOpen = ref(false)
async function setStatus(s: WorkOrderStatus) {
  try {
    wo.value = (await http.post<WorkOrder>(`/app/work-orders/${id.value}/status`, { status: s })).data
    notify.success()
  } catch (e) {
    notify.error(e)
  }
}
function remove() {
  if (!wo.value) return
  confirm.require({
    message: t('workOrder.deleteConfirm', { no: wo.value.woNo }), header: t('common.delete'),
    acceptLabel: t('common.yes'), rejectLabel: t('common.no'), acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/work-orders/${id.value}`)
        notify.success()
        router.replace('/app/work-orders')
      } catch (e) {
        notify.error(e)
      }
    },
  })
}

// ------------------------------------------------------------------ billing plan

interface PlanRow { id: number | null; title: string; dueDate: Date | null; amount: number; billId: number | null; billNo: string | null; due: boolean }
const plan = ref<PlanRow[]>([])
const planDirty = ref(false)
function resetPlan() {
  plan.value = (wo.value?.milestones ?? []).map((m) => ({
    id: m.id, title: m.title, dueDate: parseIso(m.dueDate), amount: Number(m.amount), billId: m.billId, billNo: m.billNo ?? null, due: !!m.due,
  }))
  planDirty.value = false
}
const planTotal = computed(() => plan.value.reduce((s, m) => s + (Number(m.amount) || 0), 0))
const planDiff = computed(() => Number(wo.value?.totals.contractValue ?? 0) - planTotal.value)
function addMilestone() {
  plan.value.push({ id: null, title: '', dueDate: null, amount: 0, billId: null, billNo: null, due: false })
  planDirty.value = true
}
function removeMilestone(i: number) {
  plan.value.splice(i, 1)
  planDirty.value = true
}

const genOpen = ref(false)
const gen = ref({ count: 4, period: 'MONTH' as 'WEEK' | 'MONTH', first: new Date() as Date | null })
const periodOptions = computed(() => (['WEEK', 'MONTH'] as const).map((p) => ({ value: p, label: t(`workOrder.periods.${p}`) })))
/** Splits what is not yet planned into equal weekly / monthly instalments. */
function generate() {
  const kept = plan.value.filter((m) => m.billId)
  const rest = Number(wo.value?.totals.contractValue ?? 0) - kept.reduce((s, m) => s + Number(m.amount), 0)
  const n = Math.max(1, Math.floor(gen.value.count))
  const each = Math.floor((rest / n) * 100) / 100
  const rows: PlanRow[] = []
  for (let i = 0; i < n; i++) {
    const d = new Date(gen.value.first ?? new Date())
    if (gen.value.period === 'WEEK') d.setDate(d.getDate() + 7 * i)
    else d.setMonth(d.getMonth() + i)
    const amount = i === n - 1 ? Math.round((rest - each * (n - 1)) * 100) / 100 : each
    rows.push({ id: null, title: t('workOrder.installment', { n: fmt.digits(kept.length + i + 1) }), dueDate: d, amount, billId: null, billNo: null, due: false })
  }
  plan.value = [...kept, ...rows]
  planDirty.value = true
  genOpen.value = false
}
async function savePlan() {
  try {
    wo.value = (await http.put<WorkOrder>(`/app/work-orders/${id.value}/milestones`,
      plan.value.map((m) => ({ id: m.id, title: m.title, dueDate: isoDate(m.dueDate), amount: m.amount || 0 })))).data
    resetPlan()
    notify.success()
  } catch (e) {
    notify.error(e)
  }
}

// ------------------------------------------------------------------ bills & receipts

const billOpen = ref(false)
const billEditing = ref<Bill | null>(null)
const billMilestone = ref<number | null>(null)
function newBill(milestoneId: number | null = null) {
  billEditing.value = null
  billMilestone.value = milestoneId
  billOpen.value = true
}
async function editBill(b: BillSummary) {
  try {
    billEditing.value = (await http.get<Bill>(`/app/bills/${b.id}`)).data
    billMilestone.value = null
    billOpen.value = true
  } catch (e) {
    notify.error(e)
  }
}
async function billAction(b: BillSummary, action: 'reopen' | 'cancel') {
  const run = async () => {
    try {
      await http.post(`/app/bills/${b.id}/${action}`)
      notify.success()
      await load()
    } catch (e) {
      notify.error(e)
    }
  }
  if (action === 'cancel') {
    confirm.require({ message: t('bill.cancelConfirm', { no: b.billNo }), header: t('bill.cancelBill'), acceptLabel: t('common.yes'), rejectLabel: t('common.no'), acceptProps: { severity: 'danger' }, accept: run })
  } else await run()
}
function deleteBill(b: BillSummary) {
  confirm.require({
    message: t('bill.deleteConfirm', { no: b.billNo }), header: t('common.delete'), acceptLabel: t('common.yes'), rejectLabel: t('common.no'),
    acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/bills/${b.id}`)
        notify.success()
        await load()
      } catch (e) {
        notify.error(e)
      }
    },
  })
}

const receiptOpen = ref(false)
const receiptBill = ref<number | null>(null)
function receive(billId: number | null = null) {
  receiptBill.value = billId
  receiptOpen.value = true
}
function deleteReceipt(r: ClientReceipt) {
  confirm.require({
    message: t('receipt.deleteConfirm', { amount: fmt.money(r.amount) }), header: t('common.delete'), acceptLabel: t('common.yes'),
    rejectLabel: t('common.no'), acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/receipts/${r.id}`)
        notify.success()
        await load()
      } catch (e) {
        notify.error(e)
      }
    },
  })
}
const billNo = (billId: number | null) => wo.value?.bills.find((b) => b.id === billId)?.billNo
</script>

<template>
  <div v-if="wo" class="page">
    <div class="page-header">
      <div>
        <div class="muted small"><RouterLink to="/app/work-orders">{{ t('workOrder.title') }}</RouterLink> › {{ wo.woNo }}</div>
        <h1>{{ wo.title }}</h1>
        <div class="muted">
          {{ wo.clientName }} · {{ wo.siteName }} · {{ fmt.date(wo.woDate) }}
          <span v-if="wo.clientRef"> · {{ t('bill.clientRef') }} {{ wo.clientRef }}</span>
          <span v-if="wo.quotationNo"> · {{ t('workOrder.fromQuotation') }} {{ wo.quotationNo }}</span>
        </div>
      </div>
      <div class="actions">
        <Tag :severity="statusSeverity(wo.status)" :value="t(`workOrder.statuses.${wo.status}`)" />
        <template v-if="canEdit">
          <Button :label="t('common.edit')" icon="pi pi-pencil" outlined size="small" @click="editOpen = true" />
          <Button v-if="wo.status === 'ACTIVE'" :label="t('workOrder.complete')" icon="pi pi-check" outlined size="small" severity="success" @click="setStatus('COMPLETED')" />
          <Button v-else :label="t('workOrder.reactivate')" icon="pi pi-replay" outlined size="small" @click="setStatus('ACTIVE')" />
          <Button v-if="wo.status === 'ACTIVE'" :label="t('workOrder.cancelWo')" icon="pi pi-ban" text size="small" severity="secondary" @click="setStatus('CANCELLED')" />
          <Button v-if="!wo.bills.length && !wo.receipts.length" icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" @click="remove" />
        </template>
      </div>
    </div>

    <div class="grid cols-4">
      <div class="card stat"><span class="label">{{ t('workOrder.contractValue') }}</span><span class="value">{{ fmt.money(wo.totals.contractValue) }}</span>
        <span class="muted small">{{ t('workOrder.unbilled') }}: {{ fmt.money(wo.totals.unbilled) }}</span></div>
      <div class="card stat"><span class="label">{{ t('workOrder.billed') }}</span><span class="value">{{ fmt.money(wo.totals.billedGross) }}</span>
        <span class="muted small">{{ t('workOrder.retentionHeld') }}: {{ fmt.money(wo.totals.retentionHeld) }}</span></div>
      <div class="card stat"><span class="label">{{ t('workOrder.received') }}</span><span class="value">{{ fmt.money(wo.totals.received) }}</span>
        <span class="muted small">{{ t('bill.net') }}: {{ fmt.money(wo.totals.billedNet) }}</span></div>
      <div class="card stat" :class="{ warn: Number(wo.totals.receivable) > 0 }">
        <span class="label">{{ Number(wo.totals.receivable) < 0 ? t('workOrder.advance') : t('workOrder.receivable') }}</span>
        <span class="value">{{ fmt.money(Math.abs(Number(wo.totals.receivable))) }}</span>
        <Button v-if="canEdit && wo.status !== 'CANCELLED'" :label="t('bill.receive')" icon="pi pi-wallet" size="small" text style="align-self: flex-start; padding-left: 0" @click="receive()" />
      </div>
    </div>

    <div class="card mt">
      <h2>{{ t('doc.lines') }}</h2>
      <DataTable :value="wo.lines" size="small" striped-rows data-key="id">
        <template #empty><span class="muted">{{ t('workOrder.lumpSum') }}: {{ fmt.money(wo.contractValue) }}</span></template>
        <Column :header="t('doc.sl')" style="width: 40px"><template #body="{ index }">{{ fmt.digits(index + 1) }}</template></Column>
        <Column :header="t('doc.description')">
          <template #body="{ data }">{{ data.description }}<div v-if="data.workItemId" class="muted small">{{ itemName(data.workItemId) }}</div></template>
        </Column>
        <Column :header="t('doc.quantity')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.num(data.quantity, 3) }} {{ data.uom }}</template></Column>
        <Column :header="t('doc.rate')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.rate) }}</template></Column>
        <Column :header="t('doc.amount')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.amount) }}</template></Column>
        <Column :header="t('workOrder.billedQty')" style="min-width: 160px">
          <template #body="{ data }">
            <div class="flex" style="flex-wrap: nowrap">
              <div class="bar" style="flex: 1"><span :style="{ width: Math.min(100, (Number(data.billedQty) / Math.max(Number(data.quantity), 0.0001)) * 100) + '%' }" /></div>
              <span class="small nowrap">{{ fmt.num(data.billedQty, 3) }}</span>
            </div>
          </template>
        </Column>
      </DataTable>
      <div v-if="Number(wo.discount) > 0" class="flex small" style="justify-content: flex-end; margin-top: 6px">
        {{ t('doc.discount') }}: − {{ fmt.money(wo.discount) }} · <strong>{{ t('workOrder.contractValue') }}: {{ fmt.money(wo.contractValue) }}</strong>
      </div>
    </div>

    <div class="card">
      <div class="flex" style="margin-bottom: 8px">
        <h2 style="margin: 0">{{ t('workOrder.schedule') }}</h2>
        <span class="spacer" />
        <template v-if="canEdit">
          <Button :label="t('workOrder.generate')" icon="pi pi-calendar" size="small" outlined @click="genOpen = true" />
          <Button :label="t('workOrder.addMilestone')" icon="pi pi-plus" size="small" text @click="addMilestone" />
        </template>
      </div>
      <p class="muted small" style="margin-top: 0">{{ t('workOrder.scheduleHint') }}</p>
      <table class="plan">
        <thead>
          <tr>
            <th style="width: 36px">{{ t('doc.sl') }}</th>
            <th>{{ t('workOrder.milestone') }}</th>
            <th style="width: 170px">{{ t('workOrder.dueDate') }}</th>
            <th style="width: 170px" class="num">{{ t('doc.amount') }}</th>
            <th style="width: 200px">{{ t('common.status') }}</th>
            <th v-if="canEdit" style="width: 40px" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="(m, i) in plan" :key="i">
            <td class="muted">{{ fmt.digits(i + 1) }}</td>
            <td><InputText v-model="m.title" size="small" class="w-full" :disabled="!canEdit || !!m.billId" :aria-label="t('workOrder.milestone')" @input="planDirty = true" /></td>
            <td><DatePicker v-model="m.dueDate" date-format="dd/mm/yy" size="small" :disabled="!canEdit || !!m.billId" :aria-label="t('workOrder.dueDate')" @update:model-value="planDirty = true" /></td>
            <td>
              <InputNumber v-model="m.amount" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" size="small" input-class="num" class="w-full"
                           :disabled="!canEdit || !!m.billId" :aria-label="t('doc.amount')" @input="planDirty = true" />
            </td>
            <td>
              <Tag v-if="m.billId" severity="success" :value="t('workOrder.billedBy', { no: m.billNo })" />
              <template v-else-if="m.id && !planDirty">
                <Tag v-if="m.due" severity="warn" :value="t('workOrder.due')" />
                <Button v-if="canEdit && wo.status === 'ACTIVE'" :label="t('workOrder.billNow')" size="small" text icon="pi pi-file-edit" @click="newBill(m.id)" />
              </template>
            </td>
            <td v-if="canEdit">
              <Button v-if="!m.billId" icon="pi pi-times" text rounded size="small" severity="danger" :aria-label="t('common.delete')" @click="removeMilestone(i)" />
            </td>
          </tr>
          <tr v-if="!plan.length"><td colspan="6" class="muted small">{{ t('common.none') }}</td></tr>
        </tbody>
      </table>
      <div class="flex mt">
        <span class="small">{{ t('workOrder.planTotal') }}: <strong>{{ fmt.money(planTotal) }}</strong></span>
        <span v-if="Math.abs(planDiff) > 0.009" class="small warn-text">· {{ t('workOrder.planDiff') }}: {{ fmt.money(planDiff) }}</span>
        <span class="spacer" />
        <template v-if="planDirty && canEdit">
          <Button :label="t('common.cancel')" text size="small" @click="resetPlan" />
          <Button :label="t('workOrder.saveSchedule')" icon="pi pi-save" size="small" @click="savePlan" />
        </template>
      </div>
    </div>

    <div class="card">
      <div class="flex" style="margin-bottom: 8px">
        <h2 style="margin: 0">{{ t('nav.bills') }}</h2>
        <span class="spacer" />
        <Button v-if="canEdit && wo.status === 'ACTIVE'" :label="t('bill.new')" icon="pi pi-plus" size="small" @click="newBill()" />
      </div>
      <DataTable :value="wo.bills" size="small" striped-rows data-key="id">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('bill.no')">
          <template #body="{ data }"><RouterLink :to="`/app/print/bill/${data.id}`" class="link">{{ data.billNo }}</RouterLink><div class="muted small">{{ fmt.date(data.billDate) }}</div></template>
        </Column>
        <Column field="title" :header="t('doc.title')" />
        <Column :header="t('bill.gross')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.grossAmount) }}</template></Column>
        <Column :header="t('bill.net')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.netAmount) }}</template></Column>
        <Column :header="t('bill.received')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.received) }}</template></Column>
        <Column :header="t('bill.balance')" body-class="num" header-class="num"><template #body="{ data }"><strong>{{ fmt.money(data.balance) }}</strong></template></Column>
        <Column :header="t('common.status')">
          <template #body="{ data }">
            <div class="flex" style="gap: 4px">
              <Tag v-if="data.status !== 'SUBMITTED'" :severity="statusSeverity(data.status)" :value="t(`bill.statuses.${data.status}`)" />
              <Tag v-else :severity="statusSeverity(data.payStatus)" :value="t(`bill.payStatuses.${data.payStatus}`)" />
              <Tag v-if="data.overdue" severity="danger" :value="t('bill.overdue')" />
            </div>
            <div v-if="data.dueDate && data.status === 'SUBMITTED' && data.payStatus !== 'PAID'" class="muted small">{{ t('bill.dueDate') }}: {{ fmt.date(data.dueDate) }}</div>
          </template>
        </Column>
        <Column style="width: 1%" body-class="nowrap">
          <template #body="{ data }">
            <Button icon="pi pi-print" text size="small" :aria-label="t('common.print')" v-tooltip.top="t('common.print')" @click="router.push(`/app/print/bill/${data.id}`)" />
            <template v-if="canEdit">
              <Button v-if="data.status === 'DRAFT'" icon="pi pi-pencil" text size="small" :aria-label="t('common.edit')" v-tooltip.top="t('common.edit')" @click="editBill(data)" />
              <Button v-if="data.status === 'SUBMITTED' && Number(data.balance) > 0" icon="pi pi-wallet" text size="small" severity="success" :aria-label="t('bill.receive')" v-tooltip.top="t('bill.receive')" @click="receive(data.id)" />
              <Button v-if="data.status === 'SUBMITTED' && !Number(data.received)" icon="pi pi-undo" text size="small" severity="secondary" :aria-label="t('bill.reopen')" v-tooltip.top="t('bill.reopen')" @click="billAction(data, 'reopen')" />
              <Button v-if="data.status === 'SUBMITTED' && !Number(data.received)" icon="pi pi-ban" text size="small" severity="danger" :aria-label="t('bill.cancelBill')" v-tooltip.top="t('bill.cancelBill')" @click="billAction(data, 'cancel')" />
              <Button v-if="data.status !== 'SUBMITTED'" icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" v-tooltip.top="t('common.delete')" @click="deleteBill(data)" />
            </template>
          </template>
        </Column>
      </DataTable>
    </div>

    <div class="card">
      <div class="flex" style="margin-bottom: 8px">
        <h2 style="margin: 0">{{ t('nav.receipts') }}</h2>
        <span class="spacer" />
        <Button v-if="canEdit" :label="t('bill.receive')" icon="pi pi-wallet" size="small" outlined @click="receive()" />
      </div>
      <DataTable :value="wo.receipts" size="small" striped-rows data-key="id">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('common.date')"><template #body="{ data }">{{ fmt.date(data.receiptDate) }}</template></Column>
        <Column :header="t('receipt.againstBill')"><template #body="{ data }">{{ data.billId ? billNo(data.billId) : t('receipt.advance') }}</template></Column>
        <Column :header="t('doc.method')"><template #body="{ data }">{{ t(`doc.payMethods.${data.method}`) }}<span v-if="data.reference" class="muted small"> · {{ data.reference }}</span></template></Column>
        <Column field="note" :header="t('common.note')" />
        <Column :header="t('doc.amount')" body-class="num" header-class="num"><template #body="{ data }"><strong>{{ fmt.money(data.amount) }}</strong></template></Column>
        <Column v-if="canEdit" style="width: 1%">
          <template #body="{ data }"><Button icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" @click="deleteReceipt(data)" /></template>
        </Column>
      </DataTable>
    </div>

    <WorkOrderDialog v-model:visible="editOpen" :work-order="wo" @saved="load" />
    <BillDialog v-model:visible="billOpen" :work-order="wo" :bill="billEditing" :milestone-id="billMilestone" :work-items="lookups.workItems.value" @saved="load" />
    <ReceiptDialog v-model:visible="receiptOpen" :work-order-id="wo.id" :bill-id="receiptBill" @saved="load" />

    <Dialog v-model:visible="genOpen" modal :header="t('workOrder.generate')" :style="{ width: '420px', maxWidth: '95vw' }">
      <form class="form" @submit.prevent="generate">
        <div class="row">
          <div class="field">
            <label for="g-n">{{ t('workOrder.installments') }}</label>
            <InputNumber v-model="gen.count" input-id="g-n" :min="1" :max="120" :locale="fmt.intlLocale.value" show-buttons />
          </div>
          <div class="field">
            <label for="g-p">{{ t('workOrder.every') }}</label>
            <SelectButton v-model="gen.period" :options="periodOptions" option-label="label" option-value="value" :allow-empty="false" />
          </div>
        </div>
        <div class="field">
          <label for="g-first">{{ t('workOrder.firstDue') }}</label>
          <DatePicker v-model="gen.first" input-id="g-first" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="genOpen = false" />
          <Button type="submit" :label="t('workOrder.generate')" :disabled="!gen.first || !gen.count" />
        </div>
      </form>
    </Dialog>
  </div>
</template>

<style scoped>
.link { color: var(--p-primary-700); font-weight: 600; text-decoration: none; }
.stat.warn { border-color: #f59e0b; }
.plan { width: 100%; border-collapse: collapse; }
.plan th { text-align: left; font-size: 0.8rem; color: var(--app-muted); font-weight: 500; padding: 6px 4px; border-bottom: 1px solid var(--app-border); }
.plan th.num { text-align: right; }
.plan td { padding: 4px; border-bottom: 1px solid var(--app-border); }
.w-full { width: 100%; }
.warn-text { color: #b45309; }
:deep(.p-inputnumber-input) { width: 100%; }
@media (max-width: 760px) { .plan { min-width: 700px; } .card:has(.plan) { overflow-x: auto; } }
</style>
