<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import type { Bill, Milestone, PricedLine, WorkItem, WorkOrder } from '@/api/types'
import { isoDate, parseIso, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import LinesEditor from '@/components/LinesEditor.vue'

type Kind = 'MILESTONE' | 'ITEMS' | 'CUSTOM'

/**
 * Raise or edit a client bill for one work order:
 *  - MILESTONE: one planned milestone / instalment
 *  - ITEMS: measured quantities against the work-order lines (running bill)
 *  - CUSTOM: free lines (extra work, retention release ...)
 */
const props = defineProps<{ workOrder: WorkOrder; bill?: Bill | null; milestoneId?: number | null; workItems: WorkItem[] }>()
const visible = defineModel<boolean>('visible', { required: true })
const emit = defineEmits<{ saved: [bill: Bill] }>()
const { t } = useI18n()
const fmt = useFormat()
const notify = useNotify()
const confirm = useConfirm()

const kind = ref<Kind>('MILESTONE')
const kindOptions = computed(() => (['MILESTONE', 'ITEMS', 'CUSTOM'] as Kind[]).map((k) => ({ value: k, label: t(`bill.kinds.${k}`) })))
const saving = ref(false)
const form = ref(blank())
const itemQty = ref<Record<number, number>>({})
const custom = ref<PricedLine[]>([])

function blank() {
  const due = new Date()
  due.setDate(due.getDate() + 15)
  return {
    billNo: '', billDate: new Date() as Date | null, dueDate: due as Date | null, periodFrom: null as Date | null, periodTo: null as Date | null,
    title: '', milestoneId: null as number | null, autoRetention: true, retentionAmount: 0, deductionAmount: 0, deductionNote: '', notes: '',
  }
}

const openMilestones = computed(() => props.workOrder.milestones.filter((m) => !m.billId || m.billId === props.bill?.id))
const milestone = computed(() => props.workOrder.milestones.find((m) => m.id === form.value.milestoneId) ?? null)

watch(visible, (v) => {
  if (!v) return
  const b = props.bill
  itemQty.value = {}
  custom.value = []
  if (b) {
    form.value = {
      billNo: b.billNo, billDate: parseIso(b.billDate), dueDate: parseIso(b.dueDate), periodFrom: parseIso(b.periodFrom), periodTo: parseIso(b.periodTo),
      title: b.title, milestoneId: b.milestoneId, autoRetention: false, retentionAmount: Number(b.retentionAmount),
      deductionAmount: Number(b.deductionAmount), deductionNote: b.deductionNote ?? '', notes: b.notes ?? '',
    }
    if (b.milestoneId) kind.value = 'MILESTONE'
    else if (b.lines.some((l) => l.workOrderLineId)) {
      kind.value = 'ITEMS'
      for (const l of b.lines) if (l.workOrderLineId) itemQty.value[l.workOrderLineId] = Number(l.quantity)
    } else {
      kind.value = 'CUSTOM'
      custom.value = b.lines.map((l) => ({ workItemId: l.workItemId, description: l.description, uom: l.uom, quantity: Number(l.quantity), rate: Number(l.rate) }))
    }
  } else {
    form.value = blank()
    const preset = props.milestoneId ?? openMilestones.value[0]?.id ?? null
    kind.value = preset ? 'MILESTONE' : props.workOrder.lines.length ? 'ITEMS' : 'CUSTOM'
    form.value.milestoneId = kind.value === 'MILESTONE' ? preset : null
    if (kind.value === 'CUSTOM') custom.value = [{ workItemId: null, description: '', uom: 'LS', quantity: 1, rate: 0 }]
    suggestTitle()
  }
})

function suggestTitle() {
  if (props.bill) return
  if (kind.value === 'MILESTONE' && milestone.value) form.value.title = milestone.value.title
  else if (kind.value === 'ITEMS') form.value.title = `${t('bill.invoice')} — ${fmt.digits(props.workOrder.bills.filter((b) => b.status !== 'CANCELLED').length + 1)}`
}
watch(() => [kind.value, form.value.milestoneId], suggestTitle)

/** Lines that will be sent, depending on the bill type. */
const lines = computed<PricedLine[]>(() => {
  if (kind.value === 'MILESTONE') {
    const m = milestone.value
    return m ? [{ workItemId: null, description: m.title, uom: 'LS', quantity: 1, rate: Number(m.amount) }] : []
  }
  if (kind.value === 'ITEMS') {
    return props.workOrder.lines
      .filter((l) => Number(itemQty.value[l.id] || 0) > 0)
      .map((l) => ({ workOrderLineId: l.id, workItemId: l.workItemId, description: l.description, uom: l.uom, quantity: Number(itemQty.value[l.id]), rate: Number(l.rate) }))
  }
  return custom.value.filter((l) => l.description?.trim())
})
const gross = computed(() => lines.value.reduce((s, l) => s + Math.round(Number(l.quantity) * Number(l.rate) * 100) / 100, 0))
const retention = computed(() =>
  form.value.autoRetention ? Math.round(gross.value * Number(props.workOrder.retentionPercent)) / 100 : Number(form.value.retentionAmount) || 0,
)
const net = computed(() => gross.value - retention.value - (Number(form.value.deductionAmount) || 0))

/** Already billed (submitted) quantity of a work-order line, excluding this bill. */
function remaining(l: WorkOrder['lines'][number]) {
  return Number(l.quantity) - Number(l.billedQty)
}

async function save(submit: boolean) {
  const f = form.value
  const body = {
    billNo: f.billNo || null, billDate: isoDate(f.billDate), dueDate: isoDate(f.dueDate), workOrderId: props.workOrder.id,
    milestoneId: kind.value === 'MILESTONE' ? f.milestoneId : null, periodFrom: isoDate(f.periodFrom), periodTo: isoDate(f.periodTo),
    title: f.title, retentionAmount: f.autoRetention ? null : f.retentionAmount || 0, deductionAmount: f.deductionAmount || 0,
    deductionNote: f.deductionNote || null, notes: f.notes || null, submit,
    lines: lines.value.map((l) => ({ workOrderLineId: l.workOrderLineId ?? null, workItemId: l.workItemId, description: l.description, uom: l.uom, quantity: l.quantity, rate: l.rate })),
  }
  saving.value = true
  try {
    const saved = props.bill
      ? (await http.put<Bill>(`/app/bills/${props.bill.id}`, body)).data
      : (await http.post<Bill>('/app/bills', body)).data
    visible.value = false
    notify.success()
    emit('saved', saved)
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
function submit() {
  confirm.require({
    message: t('bill.submitConfirm'), header: t('bill.submit'), acceptLabel: t('common.yes'), rejectLabel: t('common.no'),
    accept: () => save(true),
  })
}
const canSave = computed(() => !!form.value.title && !!form.value.billDate && lines.value.length > 0 && net.value >= 0)
</script>

<template>
  <Dialog v-model:visible="visible" modal maximizable :header="bill ? `${t('bill.invoice')} ${bill.billNo}` : `${t('bill.new')} — ${workOrder.woNo}`" :style="{ width: '1000px', maxWidth: '98vw' }">
    <form class="form" @submit.prevent="save(false)">
      <div class="field">
        <label>{{ t('bill.kind') }}</label>
        <SelectButton v-model="kind" :options="kindOptions" option-label="label" option-value="value" :allow-empty="false" />
      </div>

      <div v-if="kind === 'MILESTONE'" class="field">
        <label for="b-ms">{{ t('workOrder.milestone') }} *</label>
        <Select v-if="openMilestones.length" v-model="form.milestoneId" input-id="b-ms" :options="openMilestones" option-value="id"
                :option-label="(m: Milestone) => `${m.title} — ${fmt.money(m.amount)}${m.dueDate ? ' (' + fmt.date(m.dueDate) + ')' : ''}`" :placeholder="t('bill.selectMilestone')" />
        <Message v-else severity="warn" :closable="false">{{ t('bill.noMilestones') }}</Message>
      </div>

      <div v-else-if="kind === 'ITEMS'" class="items">
        <table>
          <thead>
            <tr>
              <th>{{ t('doc.description') }}</th>
              <th class="num">{{ t('bill.contractQty') }}</th>
              <th class="num">{{ t('workOrder.billedQty') }}</th>
              <th class="num">{{ t('workOrder.remainingQty') }}</th>
              <th class="num" style="width: 150px">{{ t('bill.thisQty') }}</th>
              <th class="num">{{ t('doc.rate') }}</th>
              <th class="num">{{ t('doc.amount') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in workOrder.lines" :key="l.id">
              <td>{{ l.description }} <span class="muted small">{{ l.uom }}</span></td>
              <td class="num">{{ fmt.num(l.quantity, 3) }}</td>
              <td class="num">{{ fmt.num(l.billedQty, 3) }}</td>
              <td class="num" :class="{ neg: remaining(l) < 0 }">{{ fmt.num(remaining(l), 3) }}</td>
              <td>
                <InputNumber v-model="itemQty[l.id]" :min="0" :max-fraction-digits="3" :locale="fmt.intlLocale.value" size="small"
                             input-class="num" class="w-full" :aria-label="`${t('bill.thisQty')} ${l.description}`" />
              </td>
              <td class="num">{{ fmt.money(l.rate) }}</td>
              <td class="num">{{ fmt.money(Math.round(Number(itemQty[l.id] || 0) * Number(l.rate) * 100) / 100) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="field">
        <LinesEditor v-model="custom" :work-items="workItems" />
      </div>

      <div class="grid cols-4">
        <div class="field">
          <label for="b-no">{{ t('bill.no') }}</label>
          <InputText id="b-no" v-model="form.billNo" :placeholder="t('doc.autoNumber')" />
        </div>
        <div class="field">
          <label for="b-date">{{ t('bill.date') }} *</label>
          <DatePicker v-model="form.billDate" input-id="b-date" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="b-due">{{ t('bill.dueDate') }}</label>
          <DatePicker v-model="form.dueDate" input-id="b-due" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="b-from">{{ t('bill.period') }}</label>
          <div class="flex" style="flex-wrap: nowrap">
            <DatePicker v-model="form.periodFrom" input-id="b-from" date-format="dd/mm/yy" :placeholder="t('common.from')" />
            <DatePicker v-model="form.periodTo" date-format="dd/mm/yy" :placeholder="t('common.to')" :aria-label="t('common.to')" />
          </div>
        </div>
      </div>
      <div class="field">
        <label for="b-title">{{ t('doc.title') }} *</label>
        <InputText id="b-title" v-model="form.title" />
      </div>

      <div class="summary">
        <div><span>{{ t('bill.gross') }}</span><strong>{{ fmt.money(gross) }}</strong></div>
        <div>
          <span>
            {{ t('bill.retention') }}
            <label class="small muted auto"><Checkbox v-model="form.autoRetention" binary input-id="b-auto" /> {{ t('bill.autoRetention', { p: fmt.num(workOrder.retentionPercent) }) }}</label>
          </span>
          <strong v-if="form.autoRetention">− {{ fmt.money(retention) }}</strong>
          <InputNumber v-else v-model="form.retentionAmount" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ " size="small" :aria-label="t('bill.retention')" />
        </div>
        <div>
          <span>{{ t('bill.deduction') }}</span>
          <InputNumber v-model="form.deductionAmount" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ " size="small" :aria-label="t('bill.deduction')" />
        </div>
        <div v-if="Number(form.deductionAmount) > 0">
          <span>{{ t('bill.deductionNote') }}</span>
          <InputText v-model="form.deductionNote" size="small" :aria-label="t('bill.deductionNote')" />
        </div>
        <div class="grand"><span>{{ t('bill.net') }}</span><strong :class="{ neg: net < 0 }">{{ fmt.money(net) }}</strong></div>
      </div>

      <div class="field">
        <label for="b-notes">{{ t('doc.notes') }}</label>
        <Textarea id="b-notes" v-model="form.notes" rows="2" auto-resize />
      </div>

      <div class="flex">
        <span class="spacer" />
        <Button type="button" :label="t('common.cancel')" text @click="visible = false" />
        <Button type="submit" :label="t('bill.saveDraft')" outlined :loading="saving" :disabled="!canSave" />
        <Button type="button" :label="t('bill.submit')" icon="pi pi-send" :loading="saving" :disabled="!canSave" @click="submit" />
      </div>
    </form>
  </Dialog>
</template>

<style scoped>
.items { overflow-x: auto; }
.items table { width: 100%; border-collapse: collapse; min-width: 760px; }
.items th { text-align: left; font-size: 0.8rem; color: var(--app-muted); font-weight: 500; padding: 6px; border-bottom: 1px solid var(--app-border); }
.items th.num { text-align: right; }
.items td { padding: 4px 6px; border-bottom: 1px solid var(--app-border); }
.w-full { width: 100%; }
.neg { color: #b91c1c; }
.summary { display: grid; justify-content: end; gap: 8px; }
.summary > div { display: flex; align-items: center; justify-content: space-between; gap: 24px; min-width: 380px; }
.summary .grand { font-size: 1.2rem; border-top: 1px solid var(--app-border); padding-top: 8px; }
.auto { display: inline-flex; align-items: center; gap: 4px; margin-left: 8px; }
@media (max-width: 640px) { .summary > div { min-width: 0; } }
</style>
