<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import type { PricedLine, Quotation, QuotationStatus, WorkOrder } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { isoDate, parseIso, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import { statusSeverity, useLookups } from '@/composables/useLookups'
import LinesEditor from '@/components/LinesEditor.vue'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const confirm = useConfirm()
const notify = useNotify()
const fmt = useFormat()
const lookups = useLookups()
const canEdit = computed(() => !auth.readOnly)

const rows = ref<Quotation[]>([])
const loading = ref(false)
const status = ref<QuotationStatus | null>(null)
const statusOptions = computed(() => [
  { value: null, label: t('common.all') },
  ...(['DRAFT', 'SENT', 'ACCEPTED', 'REJECTED'] as const).map((s) => ({ value: s, label: t(`quotation.statuses.${s}`) })),
])
const shown = computed(() => rows.value.filter((q) => !status.value || q.status === status.value))

async function load() {
  loading.value = true
  try {
    rows.value = (await http.get<Quotation[]>('/app/quotations')).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await Promise.all([load(), lookups.load('clients', 'sites', 'workItems')])
})

// ------------------------------------------------------------------ editor

const open = ref(false)
const editing = ref<Quotation | null>(null)
const saving = ref(false)
const form = ref({
  quoteNo: '', quoteDate: new Date() as Date | null, validUntil: null as Date | null, clientId: null as number | null,
  siteId: null as number | null, title: '', discount: 0, terms: '', notes: '', lines: [] as PricedLine[],
})
const readonlyForm = computed(() => !canEdit.value || (editing.value?.status === 'ACCEPTED' && !!editing.value.workOrderId))
const subtotal = computed(() => form.value.lines.reduce((s, l) => s + Math.round((Number(l.quantity) || 0) * (Number(l.rate) || 0) * 100) / 100, 0))
const total = computed(() => Math.max(0, subtotal.value - (Number(form.value.discount) || 0)))

function openNew() {
  editing.value = null
  const valid = new Date()
  valid.setDate(valid.getDate() + 30)
  form.value = { quoteNo: '', quoteDate: new Date(), validUntil: valid, clientId: null, siteId: null, title: '', discount: 0, terms: '', notes: '',
    lines: [{ id: null, workItemId: null, description: '', uom: null, quantity: 1, rate: 0 }] }
  open.value = true
}
async function openEdit(q: Quotation) {
  try {
    const full = (await http.get<Quotation>(`/app/quotations/${q.id}`)).data
    editing.value = full
    form.value = {
      quoteNo: full.quoteNo, quoteDate: parseIso(full.quoteDate), validUntil: parseIso(full.validUntil), clientId: full.clientId,
      siteId: full.siteId, title: full.title, discount: Number(full.discount), terms: full.terms ?? '', notes: full.notes ?? '',
      lines: (full.lines ?? []).map((l) => ({ ...l, quantity: Number(l.quantity), rate: Number(l.rate) })),
    }
    open.value = true
  } catch (e) {
    notify.error(e)
  }
}
async function save() {
  saving.value = true
  try {
    const f = form.value
    const body = {
      quoteNo: f.quoteNo || null, quoteDate: isoDate(f.quoteDate), validUntil: isoDate(f.validUntil), clientId: f.clientId,
      siteId: f.siteId, title: f.title, discount: f.discount || 0, terms: f.terms || null, notes: f.notes || null,
      lines: f.lines.filter((l) => l.description?.trim()).map((l) => ({ workItemId: l.workItemId, description: l.description, uom: l.uom, quantity: l.quantity || 0, rate: l.rate || 0 })),
    }
    if (editing.value) await http.put(`/app/quotations/${editing.value.id}`, body)
    else await http.post('/app/quotations', body)
    open.value = false
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}

// ------------------------------------------------------------------ actions

async function setStatus(q: Quotation, s: QuotationStatus) {
  try {
    await http.post(`/app/quotations/${q.id}/status`, { status: s })
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  }
}
async function copy(q: Quotation) {
  try {
    const c = (await http.post<Quotation>(`/app/quotations/${q.id}/copy`)).data
    notify.success(`${t('quotation.revise')}: ${c.quoteNo}`)
    await load()
    await openEdit(c)
  } catch (e) {
    notify.error(e)
  }
}
function remove(q: Quotation) {
  confirm.require({
    message: t('quotation.deleteConfirm', { no: q.quoteNo }), header: t('common.delete'),
    acceptLabel: t('common.yes'), rejectLabel: t('common.no'), acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/quotations/${q.id}`)
        notify.success()
        await load()
      } catch (e) {
        notify.error(e)
      }
    },
  })
}

const convertOpen = ref(false)
const converting = ref<Quotation | null>(null)
const conv = ref({ siteId: null as number | null, woDate: new Date() as Date | null, retentionPercent: 0, startDate: null as Date | null, endDate: null as Date | null, clientRef: '' })
function openConvert(q: Quotation) {
  converting.value = q
  conv.value = { siteId: q.siteId, woDate: new Date(), retentionPercent: 0, startDate: null, endDate: null, clientRef: '' }
  convertOpen.value = true
}
async function convert() {
  if (!converting.value) return
  saving.value = true
  try {
    const c = conv.value
    const wo = (await http.post<WorkOrder>(`/app/quotations/${converting.value.id}/work-order`, {
      siteId: c.siteId, woDate: isoDate(c.woDate), retentionPercent: c.retentionPercent || 0,
      startDate: isoDate(c.startDate), endDate: isoDate(c.endDate), clientRef: c.clientRef || null,
    })).data
    convertOpen.value = false
    notify.success()
    router.push(`/app/work-orders/${wo.id}`)
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('quotation.title') }}</h1>
      <div class="actions">
        <SelectButton v-model="status" :options="statusOptions" option-label="label" option-value="value" :allow-empty="false" size="small" />
        <Button v-if="canEdit" :label="t('quotation.new')" icon="pi pi-plus" @click="openNew" />
      </div>
    </div>

    <div class="card">
      <DataTable :value="shown" :loading="loading" data-key="id" size="small" striped-rows paginator :rows="20">
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column :header="t('quotation.no')">
          <template #body="{ data }"><a href="#" class="link" @click.prevent="openEdit(data)">{{ data.quoteNo }}</a></template>
        </Column>
        <Column :header="t('common.date')">
          <template #body="{ data }">{{ fmt.date(data.quoteDate) }}</template>
        </Column>
        <Column :header="t('hierarchy.client')">
          <template #body="{ data }">{{ data.clientName }}<div v-if="data.siteName" class="muted small">{{ data.siteName }}</div></template>
        </Column>
        <Column field="title" :header="t('doc.title')" />
        <Column :header="t('doc.grandTotal')" body-class="num" header-class="num">
          <template #body="{ data }"><strong>{{ fmt.money(data.total) }}</strong></template>
        </Column>
        <Column :header="t('common.status')">
          <template #body="{ data }">
            <Tag :severity="statusSeverity(data.status)" :value="t(`quotation.statuses.${data.status}`)" />
            <div v-if="data.workOrderId" class="small"><RouterLink :to="`/app/work-orders/${data.workOrderId}`">{{ data.workOrderNo }}</RouterLink></div>
          </template>
        </Column>
        <Column style="width: 1%" body-class="nowrap">
          <template #body="{ data }">
            <Button icon="pi pi-print" text size="small" v-tooltip.top="t('common.print')" :aria-label="t('common.print')" @click="router.push(`/app/print/quotation/${data.id}`)" />
            <template v-if="canEdit">
              <Button v-if="!data.workOrderId && data.status === 'DRAFT'" icon="pi pi-send" text size="small" v-tooltip.top="t('quotation.markSent')" :aria-label="t('quotation.markSent')" @click="setStatus(data, 'SENT')" />
              <Button v-if="!data.workOrderId && data.status !== 'REJECTED'" icon="pi pi-check-circle" text size="small" severity="success" v-tooltip.top="t('quotation.convert')" :aria-label="t('quotation.convert')" @click="openConvert(data)" />
              <Button v-if="!data.workOrderId && data.status === 'SENT'" icon="pi pi-ban" text size="small" severity="secondary" v-tooltip.top="t('quotation.markRejected')" :aria-label="t('quotation.markRejected')" @click="setStatus(data, 'REJECTED')" />
              <Button v-if="!data.workOrderId && data.status === 'REJECTED'" icon="pi pi-undo" text size="small" severity="secondary" v-tooltip.top="t('quotation.markDraft')" :aria-label="t('quotation.markDraft')" @click="setStatus(data, 'DRAFT')" />
              <Button icon="pi pi-copy" text size="small" severity="secondary" v-tooltip.top="t('quotation.revise')" :aria-label="t('quotation.revise')" @click="copy(data)" />
              <Button v-if="!data.workOrderId" icon="pi pi-trash" text size="small" severity="danger" v-tooltip.top="t('common.delete')" :aria-label="t('common.delete')" @click="remove(data)" />
            </template>
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="open" modal maximizable :header="editing ? `${t('quotation.title')} ${editing.quoteNo}` : t('quotation.new')" :style="{ width: '1100px', maxWidth: '98vw' }">
      <form class="form" @submit.prevent="save">
        <div class="grid cols-4">
          <div class="field">
            <label for="q-no">{{ t('quotation.no') }}</label>
            <InputText id="q-no" v-model="form.quoteNo" :placeholder="t('doc.autoNumber')" :disabled="readonlyForm" />
          </div>
          <div class="field">
            <label for="q-date">{{ t('common.date') }} *</label>
            <DatePicker v-model="form.quoteDate" input-id="q-date" date-format="dd/mm/yy" show-icon :disabled="readonlyForm" />
          </div>
          <div class="field">
            <label for="q-valid">{{ t('quotation.validUntil') }}</label>
            <DatePicker v-model="form.validUntil" input-id="q-valid" date-format="dd/mm/yy" show-icon :disabled="readonlyForm" />
          </div>
          <div class="field">
            <label for="q-client">{{ t('hierarchy.client') }} *</label>
            <Select v-model="form.clientId" input-id="q-client" :options="lookups.clients.value" option-label="name" option-value="id" filter :disabled="readonlyForm" @change="form.siteId = null" />
          </div>
        </div>
        <div class="row">
          <div class="field">
            <label for="q-title">{{ t('doc.title') }} *</label>
            <InputText id="q-title" v-model="form.title" :disabled="readonlyForm" />
          </div>
          <div class="field">
            <label for="q-site">{{ t('quotation.siteOptional') }}</label>
            <Select v-model="form.siteId" input-id="q-site" :options="lookups.sitesOf(form.clientId)" option-label="name" option-value="id" show-clear :disabled="readonlyForm || !form.clientId" />
          </div>
        </div>
        <div class="field">
          <label>{{ t('doc.lines') }}</label>
          <LinesEditor v-model="form.lines" :work-items="lookups.workItems.value" :readonly="readonlyForm" />
        </div>
        <div class="totals">
          <div><span>{{ t('doc.subtotal') }}</span><strong>{{ fmt.money(subtotal) }}</strong></div>
          <div>
            <label for="q-discount">{{ t('doc.discount') }}</label>
            <InputNumber v-model="form.discount" input-id="q-discount" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ " :disabled="readonlyForm" size="small" />
          </div>
          <div class="grand"><span>{{ t('doc.grandTotal') }}</span><strong>{{ fmt.money(total) }}</strong></div>
        </div>
        <div class="row">
          <div class="field">
            <label for="q-terms">{{ t('quotation.terms') }}</label>
            <Textarea id="q-terms" v-model="form.terms" rows="3" auto-resize :disabled="readonlyForm" />
          </div>
          <div class="field">
            <label for="q-notes">{{ t('doc.notes') }}</label>
            <Textarea id="q-notes" v-model="form.notes" rows="3" auto-resize :disabled="readonlyForm" />
          </div>
        </div>
        <div class="flex">
          <Button v-if="editing" type="button" :label="t('common.print')" icon="pi pi-print" outlined @click="router.push(`/app/print/quotation/${editing.id}`)" />
          <span class="spacer" />
          <Button type="button" :label="readonlyForm ? t('doc.close') : t('common.cancel')" text @click="open = false" />
          <Button v-if="!readonlyForm" type="submit" :label="t('common.save')" :loading="saving" :disabled="!form.clientId || !form.title || !form.quoteDate" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:visible="convertOpen" modal :header="t('quotation.convert')" :style="{ width: '560px', maxWidth: '95vw' }">
      <form v-if="converting" class="form" @submit.prevent="convert">
        <Message severity="info" :closable="false">{{ t('quotation.convertHint') }} — {{ converting.quoteNo }} · {{ fmt.money(converting.total) }}</Message>
        <div class="row">
          <div class="field">
            <label for="c-site">{{ t('hierarchy.site') }} *</label>
            <Select v-model="conv.siteId" input-id="c-site" :options="lookups.sitesOf(converting.clientId)" option-label="name" option-value="id" />
          </div>
          <div class="field">
            <label for="c-date">{{ t('common.date') }} *</label>
            <DatePicker v-model="conv.woDate" input-id="c-date" date-format="dd/mm/yy" show-icon />
          </div>
        </div>
        <div class="row">
          <div class="field">
            <label for="c-ret">{{ t('workOrder.retention') }}</label>
            <InputNumber v-model="conv.retentionPercent" input-id="c-ret" :min="0" :max="100" :max-fraction-digits="2" :locale="fmt.intlLocale.value" suffix=" %" />
          </div>
          <div class="field">
            <label for="c-ref">{{ t('workOrder.clientRef') }}</label>
            <InputText id="c-ref" v-model="conv.clientRef" />
          </div>
        </div>
        <div class="row">
          <div class="field">
            <label for="c-start">{{ t('workOrder.startDate') }}</label>
            <DatePicker v-model="conv.startDate" input-id="c-start" date-format="dd/mm/yy" show-icon />
          </div>
          <div class="field">
            <label for="c-end">{{ t('workOrder.endDate') }}</label>
            <DatePicker v-model="conv.endDate" input-id="c-end" date-format="dd/mm/yy" show-icon />
          </div>
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="convertOpen = false" />
          <Button type="submit" :label="t('quotation.convert')" icon="pi pi-check" :loading="saving" :disabled="!conv.siteId || !conv.woDate" />
        </div>
      </form>
    </Dialog>
  </div>
</template>

<style scoped>
.link { color: var(--p-primary-700); font-weight: 600; text-decoration: none; }
.totals { display: grid; justify-content: end; gap: 6px; }
.totals > div { display: flex; align-items: center; justify-content: space-between; gap: 24px; min-width: 300px; }
.totals .grand { font-size: 1.15rem; border-top: 1px solid var(--app-border); padding-top: 6px; }
</style>
