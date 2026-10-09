<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import { PAY_METHODS, type BillSummary, type ClientReceipt, type PayMethod, type WorkOrderSummary } from '@/api/types'
import { isoDate, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'

/** Money received from a client, against a submitted bill or as an advance on the work order. */
const props = defineProps<{ workOrderId?: number | null; billId?: number | null }>()
const visible = defineModel<boolean>('visible', { required: true })
const emit = defineEmits<{ saved: [r: ClientReceipt] }>()
const { t } = useI18n()
const fmt = useFormat()
const notify = useNotify()

const workOrders = ref<WorkOrderSummary[]>([])
const bills = ref<BillSummary[]>([])
const saving = ref(false)
const form = ref({ workOrderId: null as number | null, billId: null as number | null, receiptDate: new Date() as Date | null, amount: 0, method: 'CASH' as PayMethod, reference: '', note: '' })
const methodOptions = computed(() => PAY_METHODS.map((m) => ({ value: m, label: t(`doc.payMethods.${m}`) })))
const billOptions = computed(() => [
  { id: null, label: t('receipt.advance') },
  ...bills.value.map((b) => ({ id: b.id, label: `${b.billNo} — ${b.title} (${t('bill.balance')}: ${fmt.money(b.balance)})` })),
])

watch(visible, async (v) => {
  if (!v) return
  form.value = { workOrderId: props.workOrderId ?? null, billId: props.billId ?? null, receiptDate: new Date(), amount: 0, method: 'CASH', reference: '', note: '' }
  try {
    if (!props.workOrderId) workOrders.value = (await http.get<WorkOrderSummary[]>('/app/work-orders')).data.filter((w) => w.status !== 'CANCELLED')
    await loadBills()
  } catch (e) {
    notify.error(e)
  }
})

async function loadBills() {
  bills.value = form.value.workOrderId
    ? (await http.get<BillSummary[]>('/app/bills', { params: { workOrderId: form.value.workOrderId, status: 'SUBMITTED' } })).data
        .filter((b) => Number(b.balance) > 0 || b.id === form.value.billId)
    : []
  const b = bills.value.find((x) => x.id === form.value.billId)
  if (b) form.value.amount = Number(b.balance)
}
async function pickWorkOrder() {
  form.value.billId = null
  await loadBills()
}
function pickBill() {
  const b = bills.value.find((x) => x.id === form.value.billId)
  form.value.amount = b ? Number(b.balance) : 0
}

async function save() {
  saving.value = true
  try {
    const f = form.value
    const r = (await http.post<ClientReceipt>('/app/receipts', {
      workOrderId: f.workOrderId, billId: f.billId, receiptDate: isoDate(f.receiptDate), amount: f.amount,
      method: f.method, reference: f.reference || null, note: f.note || null,
    })).data
    visible.value = false
    notify.success()
    emit('saved', r)
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Dialog v-model:visible="visible" modal :header="t('receipt.new')" :style="{ width: '540px', maxWidth: '95vw' }">
    <form class="form" @submit.prevent="save">
      <div v-if="!workOrderId" class="field">
        <label for="r-wo">{{ t('workOrder.short') }} *</label>
        <Select v-model="form.workOrderId" input-id="r-wo" :options="workOrders" option-value="id" filter
                :option-label="(w: WorkOrderSummary) => `${w.woNo} — ${w.clientName} · ${w.title}`" @change="pickWorkOrder" />
      </div>
      <div class="field">
        <label for="r-bill">{{ t('receipt.againstBill') }}</label>
        <Select v-model="form.billId" input-id="r-bill" :options="billOptions" option-label="label" option-value="id" :disabled="!form.workOrderId" @change="pickBill" />
      </div>
      <div class="row">
        <div class="field">
          <label for="r-date">{{ t('receipt.date') }} *</label>
          <DatePicker v-model="form.receiptDate" input-id="r-date" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="r-amount">{{ t('doc.amount') }} *</label>
          <InputNumber v-model="form.amount" input-id="r-amount" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ " />
        </div>
      </div>
      <div class="field">
        <label>{{ t('doc.method') }}</label>
        <SelectButton v-model="form.method" :options="methodOptions" option-label="label" option-value="value" :allow-empty="false" />
      </div>
      <div class="row">
        <div class="field">
          <label for="r-ref">{{ t('doc.reference') }}</label>
          <InputText id="r-ref" v-model="form.reference" />
        </div>
        <div class="field">
          <label for="r-note">{{ t('common.note') }}</label>
          <InputText id="r-note" v-model="form.note" />
        </div>
      </div>
      <div class="flex">
        <span class="spacer" />
        <Button type="button" :label="t('common.cancel')" text @click="visible = false" />
        <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="!form.workOrderId || !form.amount || !form.receiptDate" />
      </div>
    </form>
  </Dialog>
</template>
