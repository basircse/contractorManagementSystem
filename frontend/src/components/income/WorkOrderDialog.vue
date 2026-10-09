<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import type { PricedLine, WorkOrder } from '@/api/types'
import { isoDate, parseIso, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import { useLookups } from '@/composables/useLookups'
import LinesEditor from '@/components/LinesEditor.vue'

/** Create or edit a work order with its priced lines. */
const props = defineProps<{ workOrder: WorkOrder | null }>()
const visible = defineModel<boolean>('visible', { required: true })
const emit = defineEmits<{ saved: [wo: WorkOrder] }>()
const { t } = useI18n()
const fmt = useFormat()
const notify = useNotify()
const lookups = useLookups()
onMounted(() => lookups.load('clients', 'sites', 'workItems'))

const saving = ref(false)
const form = ref(blank())
function blank() {
  return {
    woNo: '', woDate: new Date() as Date | null, clientId: null as number | null, siteId: null as number | null, title: '',
    clientRef: '', startDate: null as Date | null, endDate: null as Date | null, retentionPercent: 0, discount: 0,
    contractValue: 0, notes: '', lines: [] as PricedLine[],
  }
}
watch(visible, (v) => {
  if (!v) return
  const w = props.workOrder
  form.value = w
    ? {
        woNo: w.woNo, woDate: parseIso(w.woDate), clientId: w.clientId, siteId: w.siteId, title: w.title, clientRef: w.clientRef ?? '',
        startDate: parseIso(w.startDate), endDate: parseIso(w.endDate), retentionPercent: Number(w.retentionPercent),
        discount: Number(w.discount), contractValue: Number(w.contractValue), notes: w.notes ?? '',
        lines: w.lines.map((l) => ({ id: l.id, workItemId: l.workItemId, description: l.description, uom: l.uom, quantity: Number(l.quantity), rate: Number(l.rate) })),
      }
    : { ...blank(), lines: [{ id: null, workItemId: null, description: '', uom: null, quantity: 1, rate: 0 }] }
})

const subtotal = computed(() => form.value.lines.reduce((s, l) => s + Math.round((Number(l.quantity) || 0) * (Number(l.rate) || 0) * 100) / 100, 0))
const hasLines = computed(() => form.value.lines.some((l) => l.description?.trim()))
const value = computed(() => (hasLines.value ? Math.max(0, subtotal.value - (Number(form.value.discount) || 0)) : Number(form.value.contractValue) || 0))

async function save() {
  saving.value = true
  try {
    const f = form.value
    const body = {
      woNo: f.woNo || null, woDate: isoDate(f.woDate), clientId: f.clientId, siteId: f.siteId, title: f.title,
      clientRef: f.clientRef || null, startDate: isoDate(f.startDate), endDate: isoDate(f.endDate),
      retentionPercent: f.retentionPercent || 0, discount: hasLines.value ? f.discount || 0 : 0, contractValue: f.contractValue || 0,
      notes: f.notes || null,
      lines: f.lines.filter((l) => l.description?.trim()).map((l) => ({ id: l.id ?? null, workItemId: l.workItemId, description: l.description, uom: l.uom, quantity: l.quantity || 0, rate: l.rate || 0 })),
    }
    const wo = props.workOrder
      ? (await http.put<WorkOrder>(`/app/work-orders/${props.workOrder.id}`, body)).data
      : (await http.post<WorkOrder>('/app/work-orders', body)).data
    visible.value = false
    notify.success()
    emit('saved', wo)
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Dialog v-model:visible="visible" modal maximizable :header="workOrder ? `${t('workOrder.short')} ${workOrder.woNo}` : t('workOrder.new')" :style="{ width: '1100px', maxWidth: '98vw' }">
    <form class="form" @submit.prevent="save">
      <div class="grid cols-4">
        <div class="field">
          <label for="wo-no">{{ t('workOrder.no') }}</label>
          <InputText id="wo-no" v-model="form.woNo" :placeholder="t('doc.autoNumber')" />
        </div>
        <div class="field">
          <label for="wo-date">{{ t('common.date') }} *</label>
          <DatePicker v-model="form.woDate" input-id="wo-date" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="wo-client">{{ t('hierarchy.client') }} *</label>
          <Select v-model="form.clientId" input-id="wo-client" :options="lookups.clients.value" option-label="name" option-value="id" filter @change="form.siteId = null" />
        </div>
        <div class="field">
          <label for="wo-site">{{ t('hierarchy.site') }} *</label>
          <Select v-model="form.siteId" input-id="wo-site" :options="lookups.sitesOf(form.clientId)" option-label="name" option-value="id" :disabled="!form.clientId" />
        </div>
      </div>
      <div class="row">
        <div class="field">
          <label for="wo-title">{{ t('doc.title') }} *</label>
          <InputText id="wo-title" v-model="form.title" />
        </div>
        <div class="field">
          <label for="wo-ref">{{ t('workOrder.clientRef') }}</label>
          <InputText id="wo-ref" v-model="form.clientRef" />
        </div>
      </div>
      <div class="grid cols-3">
        <div class="field">
          <label for="wo-start">{{ t('workOrder.startDate') }}</label>
          <DatePicker v-model="form.startDate" input-id="wo-start" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="wo-end">{{ t('workOrder.endDate') }}</label>
          <DatePicker v-model="form.endDate" input-id="wo-end" date-format="dd/mm/yy" show-icon />
        </div>
        <div class="field">
          <label for="wo-ret">{{ t('workOrder.retention') }}</label>
          <InputNumber v-model="form.retentionPercent" input-id="wo-ret" :min="0" :max="100" :max-fraction-digits="2" :locale="fmt.intlLocale.value" suffix=" %" />
        </div>
      </div>
      <div class="field">
        <label>{{ t('doc.lines') }}</label>
        <LinesEditor v-model="form.lines" :work-items="lookups.workItems.value" />
      </div>
      <div class="totals">
        <template v-if="hasLines">
          <div>
            <label for="wo-disc">{{ t('doc.discount') }}</label>
            <InputNumber v-model="form.discount" input-id="wo-disc" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ " size="small" />
          </div>
        </template>
        <template v-else>
          <div>
            <label for="wo-lump">{{ t('workOrder.lumpSum') }}</label>
            <InputNumber v-model="form.contractValue" input-id="wo-lump" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value" prefix="৳ " size="small" />
          </div>
          <small class="muted">{{ t('workOrder.lumpSumHint') }}</small>
        </template>
        <div class="grand"><span>{{ t('workOrder.contractValue') }}</span><strong>{{ fmt.money(value) }}</strong></div>
      </div>
      <div class="field">
        <label for="wo-notes">{{ t('doc.notes') }}</label>
        <Textarea id="wo-notes" v-model="form.notes" rows="2" auto-resize />
      </div>
      <div class="flex">
        <span class="spacer" />
        <Button type="button" :label="t('common.cancel')" text @click="visible = false" />
        <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="!form.clientId || !form.siteId || !form.title || !form.woDate" />
      </div>
    </form>
  </Dialog>
</template>

<style scoped>
.totals { display: grid; justify-content: end; gap: 6px; }
.totals > div { display: flex; align-items: center; justify-content: space-between; gap: 24px; min-width: 320px; }
.totals .grand { font-size: 1.15rem; border-top: 1px solid var(--app-border); padding-top: 6px; }
</style>
