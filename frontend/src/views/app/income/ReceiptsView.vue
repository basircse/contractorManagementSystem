<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import type { ClientReceipt } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { isoDate, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import ReceiptDialog from '@/components/income/ReceiptDialog.vue'

const { t } = useI18n()
const auth = useAuthStore()
const confirm = useConfirm()
const notify = useNotify()
const fmt = useFormat()
const canEdit = computed(() => !auth.readOnly)

const today = new Date()
const from = ref<Date | null>(new Date(today.getFullYear(), today.getMonth() - 2, 1))
const to = ref<Date | null>(today)
const rows = ref<ClientReceipt[]>([])
const loading = ref(false)
const total = computed(() => rows.value.reduce((s, r) => s + Number(r.amount), 0))

async function load() {
  loading.value = true
  try {
    rows.value = (await http.get<ClientReceipt[]>('/app/receipts', { params: { from: isoDate(from.value), to: isoDate(to.value) } })).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const open = ref(false)
function remove(r: ClientReceipt) {
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
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('receipt.title') }}</h1>
      <div class="actions">
        <Button v-if="canEdit" :label="t('receipt.new')" icon="pi pi-plus" @click="open = true" />
      </div>
    </div>

    <div class="card">
      <div class="filters">
        <div class="field">
          <label for="r-from">{{ t('common.from') }}</label>
          <DatePicker v-model="from" input-id="r-from" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="r-to">{{ t('common.to') }}</label>
          <DatePicker v-model="to" input-id="r-to" date-format="dd/mm/yy" show-icon />
        </div>
        <Button :label="t('common.apply')" icon="pi pi-filter" :loading="loading" @click="load" />
        <span class="spacer" />
        <div class="stat" style="text-align: right">
          <span class="label">{{ t('receipt.total') }}</span><span class="value">{{ fmt.money(total) }}</span>
        </div>
      </div>
    </div>

    <div class="card">
      <DataTable :value="rows" :loading="loading" data-key="id" size="small" striped-rows paginator :rows="25">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('common.date')"><template #body="{ data }">{{ fmt.date(data.receiptDate) }}</template></Column>
        <Column :header="t('hierarchy.client')"><template #body="{ data }">{{ data.clientName }}</template></Column>
        <Column :header="t('workOrder.short')">
          <template #body="{ data }"><RouterLink :to="`/app/work-orders/${data.workOrderId}`">{{ data.woNo }}</RouterLink></template>
        </Column>
        <Column :header="t('receipt.againstBill')">
          <template #body="{ data }">
            <RouterLink v-if="data.billId" :to="`/app/print/bill/${data.billId}`">{{ data.billNo }}</RouterLink>
            <span v-else class="muted">{{ t('receipt.advance') }}</span>
          </template>
        </Column>
        <Column :header="t('doc.method')"><template #body="{ data }">{{ t(`doc.payMethods.${data.method}`) }}<div v-if="data.reference" class="muted small">{{ data.reference }}</div></template></Column>
        <Column field="note" :header="t('common.note')" />
        <Column :header="t('doc.amount')" body-class="num" header-class="num"><template #body="{ data }"><strong>{{ fmt.money(data.amount) }}</strong></template></Column>
        <Column v-if="canEdit" style="width: 1%">
          <template #body="{ data }"><Button icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" @click="remove(data)" /></template>
        </Column>
      </DataTable>
    </div>

    <ReceiptDialog v-model:visible="open" @saved="load" />
  </div>
</template>
