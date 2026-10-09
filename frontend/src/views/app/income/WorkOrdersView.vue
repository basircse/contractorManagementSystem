<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { http } from '@/api/http'
import type { WorkOrder, WorkOrderStatus, WorkOrderSummary } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import { statusSeverity } from '@/composables/useLookups'
import WorkOrderDialog from '@/components/income/WorkOrderDialog.vue'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const notify = useNotify()
const fmt = useFormat()
const canEdit = computed(() => !auth.readOnly)

const rows = ref<WorkOrderSummary[]>([])
const loading = ref(false)
const status = ref<WorkOrderStatus | null>('ACTIVE')
const statusOptions = computed(() => [
  { value: null, label: t('common.all') },
  ...(['ACTIVE', 'COMPLETED', 'CANCELLED'] as const).map((s) => ({ value: s, label: t(`workOrder.statuses.${s}`) })),
])
const shown = computed(() => rows.value.filter((w) => !status.value || w.status === status.value))
const totals = computed(() => {
  const sum = (f: (w: WorkOrderSummary) => number) => shown.value.reduce((s, w) => s + Number(f(w)), 0)
  return {
    contract: sum((w) => w.totals.contractValue),
    billed: sum((w) => w.totals.billedGross),
    received: sum((w) => w.totals.received),
    receivable: sum((w) => Math.max(0, w.totals.receivable)),
  }
})

async function load() {
  loading.value = true
  try {
    rows.value = (await http.get<WorkOrderSummary[]>('/app/work-orders')).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const open = ref(false)
function created(wo: WorkOrder) {
  router.push(`/app/work-orders/${wo.id}`)
}
const progress = (w: WorkOrderSummary) => (Number(w.totals.contractValue) ? Math.min(100, (Number(w.totals.billedGross) / Number(w.totals.contractValue)) * 100) : 0)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('workOrder.title') }}</h1>
      <div class="actions">
        <SelectButton v-model="status" :options="statusOptions" option-label="label" option-value="value" :allow-empty="false" size="small" />
        <Button v-if="canEdit" :label="t('workOrder.new')" icon="pi pi-plus" @click="open = true" />
      </div>
    </div>

    <div class="grid cols-4" style="margin-bottom: 16px">
      <div class="card stat"><span class="label">{{ t('workOrder.contractValue') }}</span><span class="value">{{ fmt.money(totals.contract) }}</span></div>
      <div class="card stat"><span class="label">{{ t('workOrder.billed') }}</span><span class="value">{{ fmt.money(totals.billed) }}</span></div>
      <div class="card stat"><span class="label">{{ t('workOrder.received') }}</span><span class="value">{{ fmt.money(totals.received) }}</span></div>
      <div class="card stat"><span class="label">{{ t('workOrder.receivable') }}</span><span class="value">{{ fmt.money(totals.receivable) }}</span></div>
    </div>

    <div class="card">
      <DataTable :value="shown" :loading="loading" data-key="id" size="small" striped-rows paginator :rows="20"
                 row-hover class="clickable" @row-click="router.push(`/app/work-orders/${$event.data.id}`)">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('workOrder.no')">
          <template #body="{ data }">
            <RouterLink :to="`/app/work-orders/${data.id}`" class="link">{{ data.woNo }}</RouterLink>
            <div class="muted small">{{ fmt.date(data.woDate) }}</div>
          </template>
        </Column>
        <Column :header="t('doc.title')">
          <template #body="{ data }">{{ data.title }}<div class="muted small">{{ data.clientName }} · {{ data.siteName }}</div></template>
        </Column>
        <Column :header="t('workOrder.contractValue')" body-class="num" header-class="num">
          <template #body="{ data }">{{ fmt.money(data.totals.contractValue) }}</template>
        </Column>
        <Column :header="t('workOrder.billed')" style="min-width: 150px">
          <template #body="{ data }">
            <div class="num">{{ fmt.money(data.totals.billedGross) }}</div>
            <div class="bar"><span :style="{ width: progress(data) + '%' }" /></div>
          </template>
        </Column>
        <Column :header="t('workOrder.received')" body-class="num" header-class="num">
          <template #body="{ data }">{{ fmt.money(data.totals.received) }}</template>
        </Column>
        <Column :header="t('workOrder.receivable')" body-class="num" header-class="num">
          <template #body="{ data }"><strong :class="{ adv: Number(data.totals.receivable) < 0 }">{{ fmt.money(data.totals.receivable) }}</strong></template>
        </Column>
        <Column :header="t('workOrder.nextMilestone')">
          <template #body="{ data }">
            <template v-if="data.nextMilestone">
              <div>{{ data.nextMilestone.title }} · {{ fmt.money(data.nextMilestone.amount) }}</div>
              <Tag v-if="data.dueMilestones" severity="warn" :value="`${t('workOrder.due')} (${fmt.num(data.dueMilestones)})`" />
              <span v-else class="muted small">{{ fmt.date(data.nextMilestone.dueDate) }}</span>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </Column>
        <Column :header="t('common.status')">
          <template #body="{ data }"><Tag :severity="statusSeverity(data.status)" :value="t(`workOrder.statuses.${data.status}`)" /></template>
        </Column>
      </DataTable>
    </div>

    <WorkOrderDialog v-model:visible="open" :work-order="null" @saved="created" />
  </div>
</template>

<style scoped>
.link { color: var(--p-primary-700); font-weight: 600; text-decoration: none; }
.adv { color: #047857; }
.clickable :deep(tbody tr) { cursor: pointer; }
</style>
