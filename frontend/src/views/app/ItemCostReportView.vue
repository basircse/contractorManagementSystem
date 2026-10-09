<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import type { Client, ItemCostReport } from '@/api/types'
import { isoDate, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import LocationPicker, { type LocationValue } from '@/components/LocationPicker.vue'

const { t } = useI18n()
const notify = useNotify()
const fmt = useFormat()

const today = new Date()
const from = ref<Date | null>(new Date(today.getFullYear(), today.getMonth(), 1))
const to = ref<Date | null>(today)
const clientId = ref<number | null>(null)
const clients = ref<Client[]>([])
const location = ref<LocationValue>({ siteId: null, buildingId: null, floorId: null, unitId: null })
const report = ref<ItemCostReport | null>(null)
const loading = ref(false)

const maxAmount = computed(() => Math.max(1, ...(report.value?.items ?? []).map((r) => Number(r.amount))))
function share(amount: number) {
  const total = Number(report.value?.totalAmount ?? 0)
  return total ? (Number(amount) / total) * 100 : 0
}

/** Pivot: one row per site, one column per work item. */
const pivot = computed(() => {
  const r = report.value
  if (!r) return { items: [], rows: [] as { siteName: string; cells: Record<number, number>; total: number }[] }
  const bySite = new Map<number, { siteName: string; cells: Record<number, number>; total: number }>()
  for (const x of r.bySite) {
    const row = bySite.get(x.siteId) ?? { siteName: x.siteName, cells: {}, total: 0 }
    row.cells[x.workItemId] = Number(x.amount)
    row.total += Number(x.amount)
    bySite.set(x.siteId, row)
  }
  return { items: r.items, rows: [...bySite.values()] }
})

function print() {
  window.print()
}

async function load() {
  loading.value = true
  try {
    report.value = (await http.get<ItemCostReport>('/app/reports/item-cost', {
      params: {
        from: isoDate(from.value), to: isoDate(to.value), clientId: clientId.value ?? undefined,
        siteId: location.value.siteId ?? undefined, buildingId: location.value.buildingId ?? undefined,
        floorId: location.value.floorId ?? undefined,
      },
    })).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  clients.value = (await http.get<Client[]>('/app/clients')).data
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('report.itemTitle') }}</h1>
      <div class="actions no-print">
        <Button :label="t('common.print')" icon="pi pi-print" outlined @click="print" />
      </div>
    </div>

    <div class="card no-print">
      <div class="filters">
        <div class="field">
          <label for="f-from">{{ t('common.from') }}</label>
          <DatePicker v-model="from" input-id="f-from" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="f-to">{{ t('common.to') }}</label>
          <DatePicker v-model="to" input-id="f-to" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="f-client">{{ t('hierarchy.client') }}</label>
          <Select v-model="clientId" input-id="f-client" :options="clients" option-label="name" option-value="id" :placeholder="t('common.all')" show-clear />
        </div>
      </div>
      <div class="mt"><LocationPicker v-model="location" :show-unit="false" :site-required="false" /></div>
      <div class="flex mt">
        <span class="spacer" />
        <Button :label="t('common.apply')" icon="pi pi-filter" :loading="loading" @click="load" />
      </div>
    </div>

    <template v-if="report">
      <div class="card">
        <div class="flex" style="margin-bottom: 8px">
          <h2 style="margin: 0">{{ fmt.date(from) }} – {{ fmt.date(to) }}</h2>
          <span class="spacer" />
          <span>{{ t('common.total') }}: <strong style="font-size: 1.2rem">{{ fmt.money(report.totalAmount) }}</strong></span>
        </div>
        <DataTable :value="report.items" size="small" striped-rows data-key="workItemId">
          <template #empty><span class="muted">{{ t('common.none') }}</span></template>
          <Column :header="t('nav.workItems')">
            <template #body="{ data }">{{ fmt.itemName(data) }}</template>
          </Column>
          <Column :header="t('common.days')" body-class="num" header-class="num">
            <template #body="{ data }">{{ fmt.num(data.days) }}</template>
          </Column>
          <Column header="OT" body-class="num" header-class="num">
            <template #body="{ data }">{{ fmt.num(data.otHours) }}</template>
          </Column>
          <Column :header="t('report.workers')" body-class="num" header-class="num">
            <template #body="{ data }">{{ fmt.num(data.workers) }}</template>
          </Column>
          <Column :header="t('attendance.cost')" body-class="num" header-class="num">
            <template #body="{ data }"><strong>{{ fmt.money(data.amount) }}</strong></template>
          </Column>
          <Column :header="t('report.share')" style="width: 22%">
            <template #body="{ data }">
              <div class="flex" style="flex-wrap: nowrap">
                <div class="bar" style="flex: 1"><span :style="{ width: (Number(data.amount) / maxAmount) * 100 + '%' }" /></div>
                <span class="small muted nowrap">{{ fmt.num(share(data.amount), 1) }}%</span>
              </div>
            </template>
          </Column>
          <ColumnGroup type="footer">
            <Row>
              <Column :footer="t('common.total')" />
              <Column :footer="fmt.num(report.totalDays)" footer-class="num" />
              <Column />
              <Column />
              <Column :footer="fmt.money(report.totalAmount)" footer-class="num" />
              <Column />
            </Row>
          </ColumnGroup>
        </DataTable>
      </div>

      <div v-if="pivot.rows.length > 1" class="card">
        <h2>{{ t('report.bySite') }}</h2>
        <div style="overflow-x: auto">
          <table class="pivot">
            <thead>
              <tr>
                <th>{{ t('hierarchy.site') }}</th>
                <th v-for="it in pivot.items" :key="it.workItemId" class="num">{{ fmt.itemName(it) }}</th>
                <th class="num">{{ t('common.total') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in pivot.rows" :key="r.siteName">
                <td>{{ r.siteName }}</td>
                <td v-for="it in pivot.items" :key="it.workItemId" class="num">{{ r.cells[it.workItemId] ? fmt.money(r.cells[it.workItemId]) : '—' }}</td>
                <td class="num"><strong>{{ fmt.money(r.total) }}</strong></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.pivot { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.pivot th, .pivot td { padding: 8px 10px; border-bottom: 1px solid var(--app-border); }
.pivot th { text-align: left; font-weight: 600; background: #f8fafa; white-space: nowrap; }
.pivot th.num { text-align: right; }
</style>
