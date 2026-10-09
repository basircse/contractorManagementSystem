<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import { EXPENSE_CATEGORIES, type ExpenseCategory, type Party, type SiteExpense, type WorkItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { isoDate, parseIso, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import { useLookups } from '@/composables/useLookups'
import LocationPicker, { type LocationValue } from '@/components/LocationPicker.vue'

const { t } = useI18n()
const auth = useAuthStore()
const confirm = useConfirm()
const notify = useNotify()
const fmt = useFormat()
const lookups = useLookups()
const canEdit = computed(() => !auth.readOnly)

const today = new Date()
const from = ref<Date | null>(new Date(today.getFullYear(), today.getMonth(), 1))
const to = ref<Date | null>(today)
const siteId = ref<number | null>(null)
const rows = ref<SiteExpense[]>([])
const loading = ref(false)
const total = computed(() => rows.value.reduce((s, e) => s + Number(e.amount), 0))
const byCategory = computed(() => {
  const m = new Map<ExpenseCategory, number>()
  for (const e of rows.value) m.set(e.category, (m.get(e.category) ?? 0) + Number(e.amount))
  return [...m.entries()].sort((a, b) => b[1] - a[1])
})
const categoryOptions = computed(() => EXPENSE_CATEGORIES.map((c) => ({ value: c, label: t(`expense.categories.${c}`) })))

async function load() {
  loading.value = true
  try {
    rows.value = (await http.get<SiteExpense[]>('/app/expenses', {
      params: { from: isoDate(from.value), to: isoDate(to.value), siteId: siteId.value ?? undefined },
    })).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await Promise.all([load(), lookups.load('sites', 'workItems', 'parties')])
})

const open = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const location = ref<LocationValue>({ siteId: null, buildingId: null, floorId: null, unitId: null })
const form = ref(blank())
function blank() {
  return { expenseDate: new Date() as Date | null, category: 'TRANSPORT' as ExpenseCategory, partyId: null as number | null,
    workItemId: null as number | null, amount: 0, description: '' }
}
function openNew() {
  editingId.value = null
  form.value = blank()
  location.value = { siteId: siteId.value, buildingId: null, floorId: null, unitId: null }
  open.value = true
}
function openEdit(e: SiteExpense) {
  editingId.value = e.id
  form.value = { expenseDate: parseIso(e.expenseDate), category: e.category, partyId: e.partyId, workItemId: e.workItemId,
    amount: Number(e.amount), description: e.description ?? '' }
  location.value = { siteId: e.siteId, buildingId: e.buildingId, floorId: e.floorId, unitId: e.unitId }
  open.value = true
}
async function save() {
  saving.value = true
  try {
    const f = form.value
    const body = { ...f, ...location.value, expenseDate: isoDate(f.expenseDate), description: f.description || null }
    if (editingId.value) await http.put(`/app/expenses/${editingId.value}`, body)
    else await http.post('/app/expenses', body)
    open.value = false
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
function remove(e: SiteExpense) {
  confirm.require({
    message: t('expense.deleteConfirm', { amount: fmt.money(e.amount) }), header: t('common.delete'), acceptLabel: t('common.yes'),
    rejectLabel: t('common.no'), acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/expenses/${e.id}`)
        notify.success()
        await load()
      } catch (err) {
        notify.error(err)
      }
    },
  })
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('expense.title') }}</h1>
      <div class="actions">
        <Button v-if="canEdit" :label="t('expense.new')" icon="pi pi-plus" @click="openNew" />
      </div>
    </div>

    <div class="card">
      <div class="filters">
        <div class="field">
          <label for="ex-from">{{ t('common.from') }}</label>
          <DatePicker v-model="from" input-id="ex-from" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="ex-to">{{ t('common.to') }}</label>
          <DatePicker v-model="to" input-id="ex-to" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="ex-site">{{ t('hierarchy.site') }}</label>
          <Select v-model="siteId" input-id="ex-site" :options="lookups.sites.value" option-label="name" option-value="id" :placeholder="t('common.all')" show-clear />
        </div>
        <Button :label="t('common.apply')" icon="pi pi-filter" :loading="loading" @click="load" />
        <span class="spacer" />
        <div class="stat" style="text-align: right"><span class="label">{{ t('common.total') }}</span><span class="value">{{ fmt.money(total) }}</span></div>
      </div>
      <div v-if="byCategory.length" class="flex mt">
        <Tag v-for="[c, amt] in byCategory" :key="c" severity="secondary" :value="`${t(`expense.categories.${c}`)}: ${fmt.money(amt)}`" />
      </div>
    </div>

    <div class="card">
      <DataTable :value="rows" :loading="loading" data-key="id" size="small" striped-rows paginator :rows="25">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('common.date')"><template #body="{ data }">{{ fmt.date(data.expenseDate) }}</template></Column>
        <Column :header="t('expense.category')"><template #body="{ data }">{{ t(`expense.categories.${data.category}`) }}</template></Column>
        <Column field="description" :header="t('doc.description')" />
        <Column :header="t('report.location')">
          <template #body="{ data }">{{ data.locationLabel }}<div v-if="data.workItemId" class="muted small">{{ fmt.itemName(lookups.workItems.value.find((w: WorkItem) => w.id === data.workItemId)) }}</div></template>
        </Column>
        <Column :header="t('doc.party')"><template #body="{ data }">{{ data.partyName ?? '' }}</template></Column>
        <Column :header="t('doc.amount')" body-class="num" header-class="num"><template #body="{ data }"><strong>{{ fmt.money(data.amount) }}</strong></template></Column>
        <Column v-if="canEdit" style="width: 1%" body-class="nowrap">
          <template #body="{ data }">
            <Button icon="pi pi-pencil" text size="small" :aria-label="t('common.edit')" @click="openEdit(data)" />
            <Button icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" @click="remove(data)" />
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="open" modal :header="editingId ? t('common.edit') : t('expense.new')" :style="{ width: '680px', maxWidth: '96vw' }">
      <form class="form" @submit.prevent="save">
        <div class="row">
          <div class="field">
            <label for="ef-date">{{ t('common.date') }} *</label>
            <DatePicker v-model="form.expenseDate" input-id="ef-date" date-format="dd/mm/yy" show-icon />
          </div>
          <div class="field">
            <label for="ef-cat">{{ t('expense.category') }} *</label>
            <Select v-model="form.category" input-id="ef-cat" :options="categoryOptions" option-label="label" option-value="value" />
          </div>
        </div>
        <LocationPicker v-model="location" />
        <div class="row">
          <div class="field">
            <label for="ef-item">{{ t('doc.workItem') }}</label>
            <Select v-model="form.workItemId" input-id="ef-item" :options="lookups.workItems.value" :option-label="(w: WorkItem) => fmt.itemName(w)" option-value="id" :placeholder="t('doc.noWorkItem')" show-clear filter />
          </div>
          <div class="field">
            <label for="ef-party">{{ t('expense.onCredit') }}</label>
            <Select v-model="form.partyId" input-id="ef-party" :options="lookups.parties.value" :option-label="(p: Party) => p.name" option-value="id" show-clear filter />
          </div>
        </div>
        <div class="row">
          <div class="field">
            <label for="ef-amt">{{ t('doc.amount') }} *</label>
            <InputNumber v-model="form.amount" input-id="ef-amt" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ " />
          </div>
          <div class="field">
            <label for="ef-desc">{{ t('doc.description') }}</label>
            <InputText id="ef-desc" v-model="form.description" />
          </div>
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="open = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="!form.expenseDate || !location.siteId || !form.amount" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
