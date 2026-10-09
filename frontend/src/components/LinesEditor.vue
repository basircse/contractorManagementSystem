<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PricedLine, WorkItem } from '@/api/types'
import { useFormat } from '@/composables/useFormat'

/** Editable priced lines (quotation / work order / bill): work item, description, unit, qty × rate. */
const props = withDefaults(defineProps<{ workItems: WorkItem[]; readonly?: boolean }>(), { readonly: false })
const lines = defineModel<PricedLine[]>({ required: true })
const { t } = useI18n()
const fmt = useFormat()

const amountOf = (l: PricedLine) => Math.round((Number(l.quantity) || 0) * (Number(l.rate) || 0) * 100) / 100
const subtotal = computed(() => lines.value.reduce((s, l) => s + amountOf(l), 0))
defineExpose({ subtotal })

function add() {
  lines.value = [...lines.value, { id: null, workItemId: null, description: '', uom: null, quantity: 1, rate: 0 }]
}
function remove(i: number) {
  lines.value = lines.value.filter((_, j) => j !== i)
}
function pickItem(l: PricedLine, id: number | null) {
  l.workItemId = id
  const w = props.workItems.find((x) => x.id === id)
  if (w) {
    if (!l.description) l.description = fmt.itemName(w)
    if (!l.uom) l.uom = w.uom
  }
}
</script>

<template>
  <div class="lines">
    <table>
      <thead>
        <tr>
          <th style="width: 36px">{{ t('doc.sl') }}</th>
          <th style="width: 190px">{{ t('doc.workItem') }}</th>
          <th>{{ t('doc.description') }} *</th>
          <th style="width: 80px">{{ t('doc.uom') }}</th>
          <th style="width: 120px" class="num">{{ t('doc.quantity') }}</th>
          <th style="width: 130px" class="num">{{ t('doc.rate') }}</th>
          <th style="width: 130px" class="num">{{ t('doc.amount') }}</th>
          <th v-if="!readonly" style="width: 40px" />
        </tr>
      </thead>
      <tbody>
        <tr v-for="(l, i) in lines" :key="i">
          <td class="muted">{{ fmt.digits(i + 1) }}</td>
          <td>
            <Select :model-value="l.workItemId" :options="workItems" :option-label="(w: WorkItem) => fmt.itemName(w)" option-value="id"
                    :placeholder="t('doc.noWorkItem')" show-clear filter :disabled="readonly" size="small" class="w-full"
                    :aria-label="t('doc.workItem')" @update:model-value="pickItem(l, $event)" />
          </td>
          <td><InputText v-model="l.description" size="small" class="w-full" :disabled="readonly" :aria-label="t('doc.description')" /></td>
          <td><InputText v-model="l.uom" size="small" class="w-full" :disabled="readonly" :aria-label="t('doc.uom')" /></td>
          <td>
            <InputNumber v-model="l.quantity" size="small" :min="0" :max-fraction-digits="3" :locale="fmt.intlLocale.value"
                         :disabled="readonly" input-class="num w-full" class="w-full" :aria-label="t('doc.quantity')" />
          </td>
          <td>
            <InputNumber v-model="l.rate" size="small" :min="0" :max-fraction-digits="2" :locale="fmt.intlLocale.value"
                         :disabled="readonly" input-class="num w-full" class="w-full" :aria-label="t('doc.rate')" />
          </td>
          <td class="num">{{ fmt.money(amountOf(l)) }}</td>
          <td v-if="!readonly">
            <Button icon="pi pi-times" text rounded size="small" severity="danger" :aria-label="t('doc.removeLine')" @click="remove(i)" />
          </td>
        </tr>
        <tr v-if="!lines.length">
          <td :colspan="readonly ? 7 : 8" class="muted small">{{ t('common.none') }}</td>
        </tr>
      </tbody>
      <tfoot>
        <tr>
          <td :colspan="6">
            <Button v-if="!readonly" :label="t('doc.addLine')" icon="pi pi-plus" text size="small" @click="add" />
          </td>
          <td class="num"><strong>{{ fmt.money(subtotal) }}</strong></td>
          <td v-if="!readonly" />
        </tr>
      </tfoot>
    </table>
  </div>
</template>

<style scoped>
.lines { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; min-width: 820px; }
th { text-align: left; font-size: 0.8rem; color: var(--app-muted); font-weight: 500; padding: 6px 4px; border-bottom: 1px solid var(--app-border); }
th.num { text-align: right; }
td { padding: 4px; vertical-align: middle; border-bottom: 1px solid var(--app-border); }
tfoot td { border-bottom: none; padding-top: 8px; }
.w-full { width: 100%; }
:deep(.p-inputnumber-input) { width: 100%; text-align: right; }
</style>
