<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import type { Labour, LabourHistory } from '@/api/types'
import { isoDate, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const notify = useNotify()
const fmt = useFormat()

const labours = ref<Labour[]>([])
const labourId = ref<number | null>(route.params.id ? Number(route.params.id) : null)
const today = new Date()
const from = ref<Date | null>(new Date(today.getFullYear(), today.getMonth() - 1, today.getDate()))
const to = ref<Date | null>(today)
const data = ref<LabourHistory | null>(null)
const loading = ref(false)

async function load() {
  if (!labourId.value) {
    data.value = null
    return
  }
  loading.value = true
  try {
    data.value = (await http.get<LabourHistory>(`/app/reports/labour/${labourId.value}`, {
      params: { from: isoDate(from.value), to: isoDate(to.value) },
    })).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}

watch(labourId, (id) => {
  router.replace({ name: 'labourReport', params: { id: id ?? undefined } })
  load()
})
onMounted(async () => {
  labours.value = (await http.get<Labour[]>('/app/labours', { params: { includeInactive: true } })).data
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('report.labourTitle') }}</h1>
    </div>
    <div class="card">
      <div class="filters">
        <div class="field" style="min-width: 240px">
          <label for="h-labour">{{ t('report.selectLabour') }}</label>
          <Select v-model="labourId" input-id="h-labour" :options="labours" option-label="name" option-value="id" filter :placeholder="t('report.selectLabour')" />
        </div>
        <div class="field">
          <label for="h-from">{{ t('common.from') }}</label>
          <DatePicker v-model="from" input-id="h-from" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="h-to">{{ t('common.to') }}</label>
          <DatePicker v-model="to" input-id="h-to" date-format="dd/mm/yy" show-icon />
        </div>
        <Button :label="t('common.apply')" icon="pi pi-filter" :loading="loading" :disabled="!labourId" @click="load" />
      </div>
    </div>

    <template v-if="data">
      <div class="grid cols-4 mt">
        <div class="card stat">
          <span class="label">{{ data.labour.name }}</span>
          <span class="value" style="font-size: 1.1rem">{{ fmt.tier(data.labour.skillTier) }}</span>
          <span class="muted small">{{ fmt.digits(data.labour.phone) }}</span>
        </div>
        <div class="card stat">
          <span class="label">{{ t('report.periodTotal') }}</span>
          <span class="value">{{ fmt.money(data.periodAmount) }}</span>
          <span class="muted small">{{ fmt.num(data.periodDays) }} {{ t('common.days') }}</span>
        </div>
        <div class="card stat">
          <span class="label">{{ t('payment.earned') }} / {{ t('payment.paid') }}</span>
          <span class="value" style="font-size: 1.1rem">{{ fmt.money(data.totalEarned) }}</span>
          <span class="muted small">{{ t('payment.paid') }}: {{ fmt.money(data.totalPaid) }}</span>
        </div>
        <div class="card stat">
          <span class="label">{{ t('payment.due') }}</span>
          <span class="value">{{ fmt.money(data.due) }}</span>
        </div>
      </div>

      <div class="grid cols-2 mt">
        <div class="card">
          <h2>{{ t('report.byItem') }}</h2>
          <DataTable :value="data.byItem" size="small">
            <template #empty><span class="muted">{{ t('common.none') }}</span></template>
            <Column :header="t('nav.workItems')">
              <template #body="{ data: r }">{{ fmt.isBn.value ? r.itemBn : r.itemEn }}</template>
            </Column>
            <Column :header="t('common.days')" body-class="num" header-class="num">
              <template #body="{ data: r }">{{ fmt.num(r.days) }}</template>
            </Column>
            <Column :header="t('attendance.cost')" body-class="num" header-class="num">
              <template #body="{ data: r }">{{ fmt.money(r.amount) }}</template>
            </Column>
          </DataTable>
        </div>
        <div class="card">
          <h2>{{ t('report.bySite') }}</h2>
          <DataTable :value="data.bySite" size="small">
            <template #empty><span class="muted">{{ t('common.none') }}</span></template>
            <Column field="siteName" :header="t('hierarchy.site')" />
            <Column :header="t('common.days')" body-class="num" header-class="num">
              <template #body="{ data: r }">{{ fmt.num(r.days) }}</template>
            </Column>
            <Column :header="t('attendance.cost')" body-class="num" header-class="num">
              <template #body="{ data: r }">{{ fmt.money(r.amount) }}</template>
            </Column>
          </DataTable>
        </div>
      </div>

      <div class="card">
        <h2>{{ t('labour.history') }}</h2>
        <DataTable :value="data.lines" size="small" striped-rows paginator :rows="30">
          <template #empty><span class="muted">{{ t('common.none') }}</span></template>
          <Column :header="t('common.date')">
            <template #body="{ data: r }"><span class="nowrap">{{ fmt.date(r.date) }}</span></template>
          </Column>
          <Column :header="t('report.location')">
            <template #body="{ data: r }">
              {{ r.siteName }}<span v-if="r.buildingName" class="muted"> › {{ r.buildingName }}</span><span v-if="r.floorName" class="muted"> › {{ r.floorName }}</span><span v-if="r.unitName" class="muted"> › {{ r.unitName }}</span>
            </template>
          </Column>
          <Column :header="t('nav.workItems')">
            <template #body="{ data: r }">{{ fmt.isBn.value ? r.itemBn : r.itemEn }}</template>
          </Column>
          <Column :header="t('common.days')" body-class="num" header-class="num">
            <template #body="{ data: r }">{{ fmt.num(r.days) }}</template>
          </Column>
          <Column header="OT" body-class="num" header-class="num">
            <template #body="{ data: r }">{{ Number(r.otHours) ? fmt.num(r.otHours) : '' }}</template>
          </Column>
          <Column :header="t('attendance.cost')" body-class="num" header-class="num">
            <template #body="{ data: r }">{{ fmt.money(r.amount) }}</template>
          </Column>
        </DataTable>
      </div>
    </template>
  </div>
</template>
