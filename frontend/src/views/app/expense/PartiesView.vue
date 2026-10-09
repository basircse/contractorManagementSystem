<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import { PARTY_TYPES, PAY_METHODS, type Party, type PartyPayment, type PartyType, type PayMethod, type StatementLine } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { isoDate, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'

const { t } = useI18n()
const auth = useAuthStore()
const confirm = useConfirm()
const notify = useNotify()
const fmt = useFormat()
const canEdit = computed(() => !auth.readOnly)

const rows = ref<Party[]>([])
const payments = ref<PartyPayment[]>([])
const loading = ref(false)
const type = ref<PartyType | null>(null)
const onlyDue = ref(false)
const typeOptions = computed(() => [{ value: null, label: t('common.all') }, ...PARTY_TYPES.map((v) => ({ value: v, label: t(`party.types.${v}`) }))])
const methodOptions = computed(() => PAY_METHODS.map((m) => ({ value: m, label: t(`doc.payMethods.${m}`) })))
const shown = computed(() => rows.value.filter((p) => (!type.value || p.type === type.value) && (!onlyDue.value || Number(p.due) !== 0)))
const totals = computed(() => ({
  charged: shown.value.reduce((s, p) => s + Number(p.charged), 0),
  paid: shown.value.reduce((s, p) => s + Number(p.paid), 0),
  due: shown.value.reduce((s, p) => s + Number(p.due), 0),
}))

async function load() {
  loading.value = true
  try {
    const [p, pay] = await Promise.all([
      http.get<Party[]>('/app/parties', { params: { includeInactive: true } }),
      http.get<PartyPayment[]>('/app/party-payments'),
    ])
    rows.value = p.data
    payments.value = pay.data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

// ------------------------------------------------------------------ party form

const open = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = ref({ name: '', type: 'SUPPLIER' as PartyType, trade: '', phone: '', address: '', note: '', active: true })
function openNew() {
  editingId.value = null
  form.value = { name: '', type: type.value ?? 'SUPPLIER', trade: '', phone: '', address: '', note: '', active: true }
  open.value = true
}
function openEdit(p: Party) {
  editingId.value = p.id
  form.value = { name: p.name, type: p.type, trade: p.trade ?? '', phone: p.phone ?? '', address: p.address ?? '', note: p.note ?? '', active: p.active }
  open.value = true
}
async function save() {
  saving.value = true
  try {
    if (editingId.value) await http.put(`/app/parties/${editingId.value}`, form.value)
    else await http.post('/app/parties', form.value)
    open.value = false
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}

// ------------------------------------------------------------------ payments

const payOpen = ref(false)
const pay = ref({ partyId: null as number | null, payDate: new Date() as Date | null, amount: 0, method: 'CASH' as PayMethod, reference: '', note: '' })
function openPay(p?: Party) {
  pay.value = { partyId: p?.id ?? null, payDate: new Date(), amount: p && Number(p.due) > 0 ? Number(p.due) : 0, method: 'CASH', reference: '', note: '' }
  payOpen.value = true
}
async function savePay() {
  saving.value = true
  try {
    const f = pay.value
    await http.post('/app/party-payments', { ...f, payDate: isoDate(f.payDate), reference: f.reference || null, note: f.note || null })
    payOpen.value = false
    notify.success()
    await load()
    if (statementOf.value) await openStatement(statementOf.value)
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
function removePayment(p: PartyPayment) {
  confirm.require({
    message: t('party.deletePaymentConfirm', { name: p.partyName ?? '', amount: fmt.money(p.amount) }), header: t('common.delete'),
    acceptLabel: t('common.yes'), rejectLabel: t('common.no'), acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/party-payments/${p.id}`)
        notify.success()
        await load()
      } catch (e) {
        notify.error(e)
      }
    },
  })
}

// ------------------------------------------------------------------ statement

const statementOpen = ref(false)
const statementOf = ref<Party | null>(null)
const statement = ref<StatementLine[]>([])
async function openStatement(p: Party) {
  try {
    const { data } = await http.get<{ party: Party; lines: StatementLine[] }>(`/app/parties/${p.id}/statement`)
    statementOf.value = data.party
    statement.value = data.lines
    statementOpen.value = true
  } catch (e) {
    notify.error(e)
  }
}
function printStatement() {
  window.print()
}
</script>

<template>
  <div class="page">
    <div class="page-header no-print">
      <h1>{{ t('party.title') }}</h1>
      <div class="actions">
        <Button v-if="canEdit" :label="t('party.pay')" icon="pi pi-wallet" outlined @click="openPay()" />
        <Button v-if="canEdit" :label="t('party.new')" icon="pi pi-plus" @click="openNew" />
      </div>
    </div>

    <div class="grid cols-3 no-print" style="margin-bottom: 16px">
      <div class="card stat"><span class="label">{{ t('party.charged') }}</span><span class="value">{{ fmt.money(totals.charged) }}</span></div>
      <div class="card stat"><span class="label">{{ t('party.paid') }}</span><span class="value">{{ fmt.money(totals.paid) }}</span></div>
      <div class="card stat"><span class="label">{{ t('party.due') }}</span><span class="value">{{ fmt.money(totals.due) }}</span></div>
    </div>

    <div class="card no-print">
      <div class="flex" style="margin-bottom: 8px">
        <SelectButton v-model="type" :options="typeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" />
        <span class="spacer" />
        <label class="flex small"><ToggleSwitch v-model="onlyDue" /> {{ t('party.onlyDue') }}</label>
      </div>
      <DataTable :value="shown" :loading="loading" data-key="id" size="small" striped-rows paginator :rows="25">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('common.name')" sortable sort-field="name">
          <template #body="{ data }">
            <a href="#" class="link" @click.prevent="openStatement(data)">{{ data.name }}</a>
            <Tag v-if="!data.active" severity="secondary" :value="t('common.inactive')" style="margin-left: 6px" />
            <div class="muted small">{{ [data.trade, data.phone ? fmt.digits(data.phone) : null].filter(Boolean).join(' · ') }}</div>
          </template>
        </Column>
        <Column :header="t('doc.type')"><template #body="{ data }">{{ t(`party.types.${data.type}`) }}</template></Column>
        <Column :header="t('party.charged')" body-class="num" header-class="num" sortable sort-field="charged"><template #body="{ data }">{{ fmt.money(data.charged) }}</template></Column>
        <Column :header="t('party.paid')" body-class="num" header-class="num"><template #body="{ data }">{{ fmt.money(data.paid) }}</template></Column>
        <Column :header="t('party.due')" body-class="num" header-class="num" sortable sort-field="due">
          <template #body="{ data }"><strong :class="{ adv: Number(data.due) < 0 }">{{ fmt.money(data.due) }}</strong></template>
        </Column>
        <Column style="width: 1%" body-class="nowrap">
          <template #body="{ data }">
            <Button icon="pi pi-list" text size="small" :aria-label="t('party.statement')" v-tooltip.top="t('party.statement')" @click="openStatement(data)" />
            <template v-if="canEdit">
              <Button icon="pi pi-wallet" text size="small" severity="success" :aria-label="t('party.pay')" v-tooltip.top="t('party.pay')" @click="openPay(data)" />
              <Button icon="pi pi-pencil" text size="small" :aria-label="t('common.edit')" v-tooltip.top="t('common.edit')" @click="openEdit(data)" />
            </template>
          </template>
        </Column>
      </DataTable>
    </div>

    <div class="card no-print">
      <h2>{{ t('party.recentPayments') }}</h2>
      <DataTable :value="payments" size="small" striped-rows data-key="id">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('common.date')"><template #body="{ data }">{{ fmt.date(data.payDate) }}</template></Column>
        <Column field="partyName" :header="t('doc.party')" />
        <Column :header="t('doc.method')"><template #body="{ data }">{{ t(`doc.payMethods.${data.method}`) }}<span v-if="data.reference" class="muted small"> · {{ data.reference }}</span></template></Column>
        <Column field="note" :header="t('common.note')" />
        <Column :header="t('doc.amount')" body-class="num" header-class="num"><template #body="{ data }"><strong>{{ fmt.money(data.amount) }}</strong></template></Column>
        <Column v-if="canEdit" style="width: 1%">
          <template #body="{ data }">
            <Button v-if="!data.purchaseId" icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" @click="removePayment(data)" />
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="open" modal :header="editingId ? t('common.edit') : t('party.new')" :style="{ width: '520px', maxWidth: '95vw' }">
      <form class="form" @submit.prevent="save">
        <div class="field">
          <label for="pt-name">{{ t('common.name') }} *</label>
          <InputText id="pt-name" v-model="form.name" required />
        </div>
        <div class="field">
          <label>{{ t('doc.type') }}</label>
          <SelectButton v-model="form.type" :options="PARTY_TYPES.map((v) => ({ value: v, label: t(`party.types.${v}`) }))" option-label="label" option-value="value" :allow-empty="false" />
        </div>
        <div class="row">
          <div class="field">
            <label for="pt-trade">{{ t('party.trade') }}</label>
            <InputText id="pt-trade" v-model="form.trade" />
          </div>
          <div class="field">
            <label for="pt-phone">{{ t('common.phone') }}</label>
            <InputText id="pt-phone" v-model="form.phone" />
          </div>
        </div>
        <div class="field">
          <label for="pt-addr">{{ t('common.address') }}</label>
          <InputText id="pt-addr" v-model="form.address" />
        </div>
        <div class="field">
          <label for="pt-note">{{ t('common.note') }}</label>
          <InputText id="pt-note" v-model="form.note" />
        </div>
        <label v-if="editingId" class="flex small"><ToggleSwitch v-model="form.active" /> {{ t('common.active') }}</label>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="open = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="!form.name" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:visible="payOpen" modal :header="t('party.pay')" :style="{ width: '500px', maxWidth: '95vw' }">
      <form class="form" @submit.prevent="savePay">
        <div class="field">
          <label for="pp-party">{{ t('doc.party') }} *</label>
          <Select v-model="pay.partyId" input-id="pp-party" :options="rows.filter((p) => p.active)" option-value="id" filter
                  :option-label="(p: Party) => `${p.name} (${t('party.due')}: ${fmt.money(p.due)})`" :placeholder="t('party.select')" />
        </div>
        <div class="row">
          <div class="field">
            <label for="pp-date">{{ t('common.date') }} *</label>
            <DatePicker v-model="pay.payDate" input-id="pp-date" date-format="dd/mm/yy" show-icon />
          </div>
          <div class="field">
            <label for="pp-amount">{{ t('doc.amount') }} *</label>
            <InputNumber v-model="pay.amount" input-id="pp-amount" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ " />
          </div>
        </div>
        <div class="field">
          <label>{{ t('doc.method') }}</label>
          <SelectButton v-model="pay.method" :options="methodOptions" option-label="label" option-value="value" :allow-empty="false" />
        </div>
        <div class="row">
          <div class="field">
            <label for="pp-ref">{{ t('doc.reference') }}</label>
            <InputText id="pp-ref" v-model="pay.reference" />
          </div>
          <div class="field">
            <label for="pp-note">{{ t('common.note') }}</label>
            <InputText id="pp-note" v-model="pay.note" />
          </div>
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="payOpen = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="!pay.partyId || !pay.amount || !pay.payDate" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:visible="statementOpen" modal maximizable :header="`${t('party.statement')} — ${statementOf?.name ?? ''}`" :style="{ width: '900px', maxWidth: '98vw' }">
      <template v-if="statementOf">
        <div class="grid cols-3" style="margin-bottom: 12px">
          <div class="stat"><span class="label">{{ t('party.charged') }}</span><span class="value">{{ fmt.money(statementOf.charged) }}</span></div>
          <div class="stat"><span class="label">{{ t('party.paid') }}</span><span class="value">{{ fmt.money(statementOf.paid) }}</span></div>
          <div class="stat"><span class="label">{{ t('party.due') }}</span><span class="value">{{ fmt.money(statementOf.due) }}</span></div>
        </div>
        <DataTable :value="statement" size="small" striped-rows>
          <template #empty><span class="muted">{{ t('common.none') }}</span></template>
          <Column :header="t('common.date')"><template #body="{ data }">{{ fmt.date(data.date) }}</template></Column>
          <Column :header="t('doc.details')">
            <template #body="{ data }">
              {{ t(`party.kinds.${data.kind}`) }}<span v-if="data.note" class="muted"> · {{ data.note }}</span>
              <div v-if="data.siteName" class="muted small">{{ data.siteName }}</div>
            </template>
          </Column>
          <Column :header="t('party.charge')" body-class="num" header-class="num"><template #body="{ data }">{{ Number(data.charge) ? fmt.money(data.charge) : '' }}</template></Column>
          <Column :header="t('party.payment')" body-class="num" header-class="num"><template #body="{ data }">{{ Number(data.payment) ? fmt.money(data.payment) : '' }}</template></Column>
          <Column :header="t('party.balance')" body-class="num" header-class="num"><template #body="{ data }"><strong>{{ fmt.money(data.balance) }}</strong></template></Column>
        </DataTable>
        <div class="flex mt no-print">
          <Button :label="t('common.print')" icon="pi pi-print" outlined size="small" @click="printStatement" />
          <span class="spacer" />
          <Button v-if="canEdit" :label="t('party.pay')" icon="pi pi-wallet" size="small" @click="openPay(statementOf)" />
        </div>
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.link { color: var(--p-primary-700); font-weight: 600; text-decoration: none; }
.adv { color: #047857; }
</style>
