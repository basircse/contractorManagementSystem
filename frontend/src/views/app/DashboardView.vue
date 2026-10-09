<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import type { Dashboard } from '@/api/types'
import { useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'

const { t } = useI18n()
const notify = useNotify()
const fmt = useFormat()

const d = ref<Dashboard | null>(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    d.value = (await http.get<Dashboard>('/app/reports/dashboard')).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
})

const itemMax = computed(() => Math.max(1, ...(d.value?.byItem ?? []).map((x) => Number(x.amount))))
const siteMax = computed(() => Math.max(1, ...(d.value?.bySite ?? []).map((x) => Number(x.amount))))

/** Last 30 days, zero-filled, as simple columns. */
const trend = computed(() => {
  const map = new Map((d.value?.trend ?? []).map((x) => [x.date, Number(x.amount)]))
  const days: { date: string; amount: number }[] = []
  const end = new Date()
  for (let i = 29; i >= 0; i--) {
    const x = new Date(end)
    x.setDate(end.getDate() - i)
    const key = `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
    days.push({ date: key, amount: map.get(key) ?? 0 })
  }
  return days
})
const trendMax = computed(() => Math.max(1, ...trend.value.map((x) => x.amount)))
const SOURCES = ['labour', 'material', 'rental', 'subcontract', 'expense'] as const
const splitMax = computed(() => Math.max(1, ...SOURCES.map((s) => Number(d.value?.periodSplit[s] ?? 0))))
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('nav.dashboard') }}</h1>
    </div>

    <div v-if="loading && !d" class="muted">{{ t('common.loading') }}</div>
    <template v-if="d">
      <div class="grid cols-4">
        <div class="card stat"><span class="label">{{ t('dashboard.todayCost') }}</span><span class="value">{{ fmt.money(d.todayCost) }}</span></div>
        <div class="card stat"><span class="label">{{ t('dashboard.workersToday') }}</span><span class="value">{{ fmt.num(d.workersToday) }}</span><span class="muted small">{{ t('dashboard.activeLabours') }}: {{ fmt.num(d.activeLabours) }}</span></div>
        <div class="card stat"><span class="label">{{ t('dashboard.periodCost') }}</span><span class="value">{{ fmt.money(d.periodCost) }}</span><span class="muted small">{{ t('dashboard.activeSites') }}: {{ fmt.num(d.activeSites) }}</span></div>
        <div class="card stat"><span class="label">{{ t('dashboard.totalDue') }}</span><span class="value">{{ fmt.money(d.totalDue) }}</span></div>
      </div>

      <h2 class="section">{{ t('dashboard.money') }}</h2>
      <div class="grid cols-4">
        <RouterLink to="/app/bills" class="card stat link-card"><span class="label">{{ t('dashboard.billed') }}</span><span class="value">{{ fmt.money(d.periodBilled) }}</span>
          <span class="muted small">{{ t('dashboard.received') }}: {{ fmt.money(d.periodReceived) }}</span></RouterLink>
        <RouterLink to="/app/work-orders" class="card stat link-card"><span class="label">{{ t('dashboard.receivable') }}</span><span class="value">{{ fmt.money(d.receivable) }}</span>
          <span v-if="d.milestonesDue" class="small warn">{{ t('dashboard.milestonesDue') }}: {{ fmt.num(d.milestonesDue) }}</span></RouterLink>
        <RouterLink to="/app/bills" class="card stat link-card" :class="{ alert: d.overdueBills > 0 }"><span class="label">{{ t('dashboard.overdue') }}</span><span class="value">{{ fmt.money(d.overdueAmount) }}</span>
          <span class="muted small">{{ t('dashboard.bills', { n: fmt.num(d.overdueBills) }) }}</span></RouterLink>
        <RouterLink to="/app/parties" class="card stat link-card"><span class="label">{{ t('dashboard.partyDue') }}</span><span class="value">{{ fmt.money(d.partyDue) }}</span>
          <span class="muted small">{{ t('dashboard.totalDue') }}: {{ fmt.money(d.totalDue) }}</span></RouterLink>
      </div>

      <div class="card mt">
        <h2>{{ t('dashboard.costSplit') }}</h2>
        <div v-for="s in SOURCES" :key="s" class="hbar">
          <div class="flex" style="justify-content: space-between"><span>{{ t(`profit.sources.${s}`) }}</span><strong>{{ fmt.money(d.periodSplit[s]) }}</strong></div>
          <div class="bar"><span :style="{ width: (Number(d.periodSplit[s]) / splitMax) * 100 + '%' }" /></div>
        </div>
      </div>

      <div class="card mt">
        <h2>{{ t('dashboard.trend') }}</h2>
        <div class="trend" role="img" :aria-label="t('dashboard.trend')">
          <div v-for="x in trend" :key="x.date" class="tcol" v-tooltip.top="`${fmt.date(x.date)}: ${fmt.money(x.amount)}`">
            <span :style="{ height: (x.amount / trendMax) * 100 + '%' }" />
          </div>
        </div>
        <div class="flex small muted" style="justify-content: space-between">
          <span>{{ fmt.date(trend[0].date) }}</span>
          <span>{{ fmt.date(trend[trend.length - 1].date) }}</span>
        </div>
      </div>

      <div class="grid cols-2 mt">
        <div class="card">
          <h2>{{ t('dashboard.costByItem') }}</h2>
          <div v-if="!d.byItem.length" class="muted">{{ t('common.none') }}</div>
          <div v-for="x in d.byItem" :key="x.id" class="hbar">
            <div class="flex" style="justify-content: space-between"><span>{{ fmt.isBn.value ? x.name : x.nameEn }}</span><strong>{{ fmt.money(x.amount) }}</strong></div>
            <div class="bar"><span :style="{ width: (Number(x.amount) / itemMax) * 100 + '%' }" /></div>
          </div>
        </div>
        <div class="card">
          <h2>{{ t('dashboard.costBySite') }}</h2>
          <div v-if="!d.bySite.length" class="muted">{{ t('common.none') }}</div>
          <div v-for="x in d.bySite" :key="x.id" class="hbar">
            <div class="flex" style="justify-content: space-between"><span>{{ x.name }}</span><strong>{{ fmt.money(x.amount) }}</strong></div>
            <div class="bar"><span :style="{ width: (Number(x.amount) / siteMax) * 100 + '%' }" /></div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.trend { display: flex; align-items: flex-end; gap: 3px; height: 160px; padding: 4px 0 6px; border-bottom: 1px solid var(--app-border); margin-bottom: 6px; }
.tcol { flex: 1; height: 100%; display: flex; align-items: flex-end; }
.tcol span { display: block; width: 100%; background: var(--p-primary-400); border-radius: 3px 3px 0 0; min-height: 1px; }
.tcol:hover span { background: var(--p-primary-600); }
.hbar { display: grid; gap: 4px; margin-bottom: 12px; }
.section { font-size: 1rem; font-weight: 600; margin: 20px 0 10px; color: var(--app-muted); }
.link-card { text-decoration: none; color: inherit; }
.link-card:hover { border-color: var(--p-primary-300); }
.link-card.alert { border-color: #f87171; }
.warn { color: #b45309; }
</style>
