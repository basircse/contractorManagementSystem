<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import type { CostSplit, ProfitReport } from '@/api/types'
import { isoDate, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import { useLookups } from '@/composables/useLookups'

const { t } = useI18n()
const notify = useNotify()
const fmt = useFormat()
const lookups = useLookups()

const from = ref<Date | null>(null)
const to = ref<Date | null>(null)
const siteId = ref<number | null>(null)
const report = ref<ProfitReport | null>(null)
const loading = ref(false)
const SOURCES: (keyof CostSplit)[] = ['labour', 'material', 'rental', 'subcontract', 'expense']

async function load() {
  loading.value = true
  try {
    report.value = (await http.get<ProfitReport>('/app/reports/profit', {
      params: { from: isoDate(from.value) ?? undefined, to: isoDate(to.value) ?? undefined, siteId: siteId.value ?? undefined },
    })).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await Promise.all([load(), lookups.load('sites')])
})

const total = computed(() => report.value?.total)
const costMax = computed(() => Math.max(1, ...SOURCES.map((s) => Number(total.value?.cost[s] ?? 0))))
const profitClass = (v: number | string) => (Number(v) < 0 ? 'loss' : 'gain')
function print() {
  window.print()
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('profit.title') }}</h1>
      <div class="actions no-print">
        <Button :label="t('common.print')" icon="pi pi-print" outlined @click="print" />
      </div>
    </div>

    <div class="card no-print">
      <div class="filters">
        <div class="field">
          <label for="pr-from">{{ t('common.from') }}</label>
          <DatePicker v-model="from" input-id="pr-from" date-format="dd/mm/yy" show-icon :placeholder="t('profit.allTime')" show-button-bar />
        </div>
        <div class="field">
          <label for="pr-to">{{ t('common.to') }}</label>
          <DatePicker v-model="to" input-id="pr-to" date-format="dd/mm/yy" show-icon show-button-bar />
        </div>
        <div class="field">
          <label for="pr-site">{{ t('hierarchy.site') }}</label>
          <Select v-model="siteId" input-id="pr-site" :options="lookups.sites.value" option-label="name" option-value="id" :placeholder="t('common.all')" show-clear />
        </div>
        <Button :label="t('common.apply')" icon="pi pi-filter" :loading="loading" @click="load" />
      </div>
      <p class="muted small" style="margin: 10px 0 0">{{ t('profit.hint') }}</p>
    </div>

    <template v-if="report && total">
      <div class="grid cols-4 mt">
        <div class="card stat"><span class="label">{{ t('profit.contract') }}</span><span class="value">{{ fmt.money(total.contractValue) }}</span></div>
        <div class="card stat"><span class="label">{{ t('profit.income') }}</span><span class="value">{{ fmt.money(total.billed) }}</span>
          <span class="muted small">{{ t('workOrder.received') }}: {{ fmt.money(total.received) }}</span></div>
        <div class="card stat"><span class="label">{{ t('profit.cost') }}</span><span class="value">{{ fmt.money(total.cost.total) }}</span></div>
        <div class="card stat"><span class="label">{{ t('profit.profit') }}</span><span class="value" :class="profitClass(total.profit)">{{ fmt.money(total.profit) }}</span>
          <span v-if="total.marginPercent !== null" class="muted small">{{ t('profit.margin') }}: {{ fmt.num(total.marginPercent, 1) }}%</span></div>
      </div>

      <div class="card mt">
        <h2>{{ t('profit.cost') }}</h2>
        <div v-for="s in SOURCES" :key="s" class="hbar">
          <div class="flex" style="justify-content: space-between"><span>{{ t(`profit.sources.${s}`) }}</span><strong>{{ fmt.money(total.cost[s]) }}</strong></div>
          <div class="bar"><span :style="{ width: (Number(total.cost[s]) / costMax) * 100 + '%' }" /></div>
        </div>
      </div>

      <div class="card">
        <h2>{{ t('profit.bySite') }}</h2>
        <DataTable :value="report.sites" size="small" striped-rows data-key="siteId">
          <template #empty><span class="muted">{{ t('common.none') }}</span></template>
          <Column :header="t('hierarchy.site')"><template #body="{ data }">{{ data.siteName }}<div class="muted small">{{ data.clientName }}</div></template></Column>
          <Column :header="t('profit.contract')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.contractValue) }}</template></Column>
          <Column :header="t('profit.income')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.billed) }}</template></Column>
          <Column v-for="s in SOURCES" :key="s" :header="t(`profit.sources.${s}`)" body-class="num small" header-class="num">
            <template #body="{ data }">{{ Number(data.cost[s]) ? fmt.money(data.cost[s]) : '—' }}</template>
          </Column>
          <Column :header="t('profit.cost')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.cost.total) }}</template></Column>
          <Column :header="t('profit.profit')" body-class="num" header-class="num">
            <template #body="{ data }">
              <strong :class="profitClass(data.profit)">{{ fmt.money(data.profit) }}</strong>
              <div v-if="data.marginPercent !== null" class="muted small">{{ fmt.num(data.marginPercent, 1) }}%</div>
            </template>
          </Column>
        </DataTable>
      </div>

      <div class="card">
        <h2>{{ t('profit.byItem') }}</h2>
        <DataTable :value="report.items" size="small" striped-rows>
          <template #empty><span class="muted">{{ t('common.none') }}</span></template>
          <Column :header="t('doc.workItem')">
            <template #body="{ data }">{{ data.workItemId ? fmt.itemName(data) : t('profit.unassigned') }}</template>
          </Column>
          <Column :header="t('profit.contract')" body-class="num" header-class="num"><template #body="{ data }">{{ Number(data.contractValue) ? fmt.money(data.contractValue) : '—' }}</template></Column>
          <Column :header="t('profit.income')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.billed) }}</template></Column>
          <Column v-for="s in SOURCES" :key="s" :header="t(`profit.sources.${s}`)" body-class="num small" header-class="num">
            <template #body="{ data }">{{ Number(data.cost[s]) ? fmt.money(data.cost[s]) : '—' }}</template>
          </Column>
          <Column :header="t('profit.cost')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.cost.total) }}</template></Column>
          <Column :header="t('profit.profit')" body-class="num" header-class="num">
            <template #body="{ data }"><strong :class="profitClass(data.profit)">{{ fmt.money(data.profit) }}</strong></template>
          </Column>
        </DataTable>
      </div>
    </template>
  </div>
</template>

<style scoped>
.hbar { display: grid; gap: 4px; margin-bottom: 10px; }
.gain { color: #047857; }
.loss { color: #b91c1c; }
</style>
