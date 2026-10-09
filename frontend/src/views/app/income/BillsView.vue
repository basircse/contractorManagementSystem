<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { http } from '@/api/http'
import type { BillSummary } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import { statusSeverity, useLookups } from '@/composables/useLookups'
import ReceiptDialog from '@/components/income/ReceiptDialog.vue'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const notify = useNotify()
const fmt = useFormat()
const lookups = useLookups()
const canEdit = computed(() => !auth.readOnly)

const rows = ref<BillSummary[]>([])
const loading = ref(false)
const clientId = ref<number | null>(null)
const onlyOpen = ref(false)
const shown = computed(() =>
  rows.value
    .filter((b) => !clientId.value || b.clientId === clientId.value)
    .filter((b) => !onlyOpen.value || (b.status === 'SUBMITTED' && Number(b.balance) > 0)),
)
const totals = computed(() => {
  const sub = shown.value.filter((b) => b.status === 'SUBMITTED')
  const sum = (f: (b: BillSummary) => number) => sub.reduce((s, b) => s + Number(f(b)), 0)
  return {
    net: sum((b) => b.netAmount),
    received: sum((b) => b.received),
    balance: sum((b) => b.balance),
    overdue: sub.filter((b) => b.overdue).reduce((s, b) => s + Number(b.balance), 0),
  }
})

async function load() {
  loading.value = true
  try {
    rows.value = (await http.get<BillSummary[]>('/app/bills')).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await Promise.all([load(), lookups.load('clients')])
})

const receiptOpen = ref(false)
const receiptFor = ref<BillSummary | null>(null)
function receive(b: BillSummary) {
  receiptFor.value = b
  receiptOpen.value = true
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('bill.title') }}</h1>
      <div class="actions">
        <Select v-model="clientId" :options="lookups.clients.value" option-label="name" option-value="id" :placeholder="t('hierarchy.client')" show-clear filter :aria-label="t('hierarchy.client')" />
        <label class="flex small"><ToggleSwitch v-model="onlyOpen" /> {{ t('bill.onlyOpen') }}</label>
      </div>
    </div>

    <div class="grid cols-4" style="margin-bottom: 16px">
      <div class="card stat"><span class="label">{{ t('bill.net') }}</span><span class="value">{{ fmt.money(totals.net) }}</span></div>
      <div class="card stat"><span class="label">{{ t('bill.received') }}</span><span class="value">{{ fmt.money(totals.received) }}</span></div>
      <div class="card stat"><span class="label">{{ t('bill.balance') }}</span><span class="value">{{ fmt.money(totals.balance) }}</span></div>
      <div class="card stat"><span class="label">{{ t('bill.overdue') }}</span><span class="value" :class="{ red: totals.overdue > 0 }">{{ fmt.money(totals.overdue) }}</span></div>
    </div>

    <div class="card">
      <DataTable :value="shown" :loading="loading" data-key="id" size="small" striped-rows paginator :rows="25">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('bill.no')">
          <template #body="{ data }"><RouterLink :to="`/app/print/bill/${data.id}`" class="link">{{ data.billNo }}</RouterLink><div class="muted small">{{ fmt.date(data.billDate) }}</div></template>
        </Column>
        <Column :header="t('doc.title')">
          <template #body="{ data }">
            {{ data.title }}
            <div class="muted small"><RouterLink :to="`/app/work-orders/${data.workOrderId}`">{{ data.woNo }}</RouterLink> · {{ data.clientName }} · {{ data.siteName }}</div>
          </template>
        </Column>
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
            <div v-if="data.dueDate && data.status === 'SUBMITTED' && data.payStatus !== 'PAID'" class="muted small">{{ fmt.date(data.dueDate) }}</div>
          </template>
        </Column>
        <Column style="width: 1%" body-class="nowrap">
          <template #body="{ data }">
            <Button icon="pi pi-print" text size="small" :aria-label="t('common.print')" v-tooltip.top="t('common.print')" @click="router.push(`/app/print/bill/${data.id}`)" />
            <Button v-if="canEdit && data.status === 'SUBMITTED' && Number(data.balance) > 0" icon="pi pi-wallet" text size="small" severity="success"
                    :aria-label="t('bill.receive')" v-tooltip.top="t('bill.receive')" @click="receive(data)" />
          </template>
        </Column>
      </DataTable>
    </div>

    <ReceiptDialog v-model:visible="receiptOpen" :work-order-id="receiptFor?.workOrderId" :bill-id="receiptFor?.id" @saved="load" />
  </div>
</template>

<style scoped>
.link { color: var(--p-primary-700); font-weight: 600; text-decoration: none; }
.red { color: #b91c1c; }
</style>
