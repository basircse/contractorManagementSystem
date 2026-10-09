<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import type { LabourBalance, LabourPayment } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { isoDate, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'

const { t } = useI18n()
const auth = useAuthStore()
const confirm = useConfirm()
const notify = useNotify()
const fmt = useFormat()
const canEdit = computed(() => !auth.readOnly)

const balances = ref<LabourBalance[]>([])
const payments = ref<LabourPayment[]>([])
const onlyDue = ref(true)
const loading = ref(false)

const shownBalances = computed(() => balances.value.filter((b) => !onlyDue.value || Number(b.due) !== 0))
const totals = computed(() => ({
  earned: shownBalances.value.reduce((s, b) => s + Number(b.earned), 0),
  paid: shownBalances.value.reduce((s, b) => s + Number(b.paid), 0),
  due: shownBalances.value.reduce((s, b) => s + Number(b.due), 0),
}))
const nameOf = (id: number) => balances.value.find((b) => b.labourId === id)?.name ?? '#' + id
const typeOptions = computed(() => (['WAGE', 'ADVANCE'] as const).map((v) => ({ value: v, label: t(`payment.types.${v}`) })))

async function load() {
  loading.value = true
  try {
    const [b, p] = await Promise.all([
      http.get<LabourBalance[]>('/app/reports/labour-balances'),
      http.get<LabourPayment[]>('/app/labour-payments'),
    ])
    balances.value = b.data
    payments.value = p.data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const open = ref(false)
const form = ref({ labourId: null as number | null, payDate: new Date() as Date | null, amount: 0, type: 'WAGE' as 'WAGE' | 'ADVANCE', note: '' })
const saving = ref(false)
function openPay(b?: LabourBalance) {
  form.value = { labourId: b?.labourId ?? null, payDate: new Date(), amount: b && Number(b.due) > 0 ? Number(b.due) : 0, type: 'WAGE', note: '' }
  open.value = true
}
async function save() {
  saving.value = true
  try {
    await http.post('/app/labour-payments', { ...form.value, payDate: isoDate(form.value.payDate), note: form.value.note || null })
    open.value = false
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
function remove(p: LabourPayment) {
  confirm.require({
    message: `${nameOf(p.labourId)} — ${fmt.money(p.amount)}`,
    header: t('common.delete'),
    acceptLabel: t('common.yes'),
    rejectLabel: t('common.no'),
    acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/labour-payments/${p.id}`)
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
      <h1>{{ t('payment.title') }}</h1>
      <div class="actions">
        <Button v-if="canEdit" :label="t('payment.newPayment')" icon="pi pi-plus" @click="openPay()" />
      </div>
    </div>

    <div class="grid cols-3" style="margin-bottom: 16px">
      <div class="card stat"><span class="label">{{ t('payment.earned') }}</span><span class="value">{{ fmt.money(totals.earned) }}</span></div>
      <div class="card stat"><span class="label">{{ t('payment.paid') }}</span><span class="value">{{ fmt.money(totals.paid) }}</span></div>
      <div class="card stat"><span class="label">{{ t('payment.due') }}</span><span class="value">{{ fmt.money(totals.due) }}</span></div>
    </div>

    <div class="card">
      <div class="flex" style="margin-bottom: 8px">
        <h2 style="margin: 0">{{ t('payment.balances') }}</h2>
        <span class="spacer" />
        <label class="flex small"><ToggleSwitch v-model="onlyDue" /> {{ t('payment.onlyDue') }}</label>
      </div>
      <DataTable :value="shownBalances" :loading="loading" data-key="labourId" size="small" striped-rows paginator :rows="20">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column field="name" :header="t('common.name')" sortable>
          <template #body="{ data }">
            <RouterLink :to="`/app/reports/labour/${data.labourId}`">{{ data.name }}</RouterLink>
            <div class="muted small">{{ fmt.tier(data.skillTier) }}</div>
          </template>
        </Column>
        <Column field="earned" :header="t('payment.earned')" sortable body-class="num" header-class="num">
          <template #body="{ data }">{{ fmt.money(data.earned) }}</template>
        </Column>
        <Column field="paid" :header="t('payment.paid')" sortable body-class="num">
          <template #body="{ data }">{{ fmt.money(data.paid) }}</template>
        </Column>
        <Column field="due" :header="t('payment.due')" sortable body-class="num">
          <template #body="{ data }"><strong :style="{ color: Number(data.due) < 0 ? '#b45309' : undefined }">{{ fmt.money(data.due) }}</strong></template>
        </Column>
        <Column v-if="canEdit" style="width: 1%">
          <template #body="{ data }">
            <Button :label="t('payment.newPayment')" size="small" outlined icon="pi pi-wallet" @click="openPay(data)" />
          </template>
        </Column>
      </DataTable>
    </div>

    <div class="card">
      <h2>{{ t('payment.recent') }}</h2>
      <DataTable :value="payments" :loading="loading" data-key="id" size="small" striped-rows>
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('common.date')">
          <template #body="{ data }">{{ fmt.date(data.payDate) }}</template>
        </Column>
        <Column :header="t('common.name')">
          <template #body="{ data }">{{ nameOf(data.labourId) }}</template>
        </Column>
        <Column :header="t('payment.type')">
          <template #body="{ data }"><Tag :severity="data.type === 'ADVANCE' ? 'warn' : 'success'" :value="t(`payment.types.${data.type}`)" /></template>
        </Column>
        <Column :header="t('payment.amount')" body-class="num" header-class="num">
          <template #body="{ data }">{{ fmt.money(data.amount) }}</template>
        </Column>
        <Column field="note" :header="t('common.note')" />
        <Column v-if="canEdit" style="width: 1%">
          <template #body="{ data }">
            <Button icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" @click="remove(data)" />
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="open" modal :header="t('payment.newPayment')" :style="{ width: '480px', maxWidth: '95vw' }">
      <form class="form" @submit.prevent="save">
        <div class="field">
          <label for="p-labour">{{ t('nav.labours') }} *</label>
          <Select input-id="p-labour" v-model="form.labourId" :options="balances" option-label="name" option-value="labourId" filter />
        </div>
        <div class="row">
          <div class="field">
            <label for="p-type">{{ t('payment.type') }}</label>
            <SelectButton v-model="form.type" :options="typeOptions" option-label="label" option-value="value" :allow-empty="false" />
          </div>
          <div class="field">
            <label for="p-date">{{ t('common.date') }}</label>
            <DatePicker v-model="form.payDate" input-id="p-date" show-icon date-format="dd/mm/yy" />
          </div>
        </div>
        <div class="field">
          <label for="p-amount">{{ t('payment.amount') }} *</label>
          <InputNumber v-model="form.amount" input-id="p-amount" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ " />
        </div>
        <div class="field">
          <label for="p-note">{{ t('common.note') }}</label>
          <InputText id="p-note" v-model="form.note" />
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="open = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="!form.labourId || !form.amount || !form.payDate" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
