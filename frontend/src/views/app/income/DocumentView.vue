<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { http } from '@/api/http'
import type { Bill, PricedLine, Profile, Quotation } from '@/api/types'
import { useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'
import { statusSeverity } from '@/composables/useLookups'
import { amountInWords } from '@/utils/amountInWords'

/** Printable quotation / bill on the contractor's letterhead (A4). */
const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const notify = useNotify()
const fmt = useFormat()

const kind = computed(() => String(route.params.kind) as 'quotation' | 'bill')
const profile = ref<Profile | null>(null)
const quote = ref<Quotation | null>(null)
const bill = ref<Bill | null>(null)

onMounted(async () => {
  try {
    const id = route.params.id
    const [p, d] = await Promise.all([
      http.get<Profile>('/app/profile'),
      kind.value === 'bill' ? http.get<Bill>(`/app/bills/${id}`) : http.get<Quotation>(`/app/quotations/${id}`),
    ])
    profile.value = p.data
    if (kind.value === 'bill') bill.value = d.data as Bill
    else quote.value = d.data as Quotation
  } catch (e) {
    notify.error(e)
  }
})

const lines = computed<PricedLine[]>(() => (bill.value?.lines ?? quote.value?.lines ?? []) as PricedLine[])
const words = (v: number) => amountInWords(v, locale.value === 'bn' ? 'bn' : 'en')
function print() {
  window.print()
}
</script>

<template>
  <div class="page">
    <div class="page-header no-print">
      <Button :label="t('common.back')" icon="pi pi-arrow-left" text @click="router.back()" />
      <div class="actions">
        <Tag v-if="bill" :severity="statusSeverity(bill.status === 'SUBMITTED' ? bill.payStatus : bill.status)"
             :value="bill.status === 'SUBMITTED' ? t(`bill.payStatuses.${bill.payStatus}`) : t(`bill.statuses.${bill.status}`)" />
        <Tag v-if="quote" :severity="statusSeverity(quote.status)" :value="t(`quotation.statuses.${quote.status}`)" />
        <Button :label="t('common.print')" icon="pi pi-print" @click="print" />
      </div>
    </div>

    <div v-if="profile && (bill || quote)" class="sheet">
      <header class="letterhead">
        <div>
          <div class="company">{{ profile.name }}</div>
          <div v-if="profile.ownerName" class="muted">{{ profile.ownerName }}</div>
          <div class="muted small">{{ profile.address }}</div>
          <div class="muted small">
            <span v-if="profile.phone">{{ t('common.phone') }}: {{ fmt.digits(profile.phone) }}</span>
            <span v-if="profile.email"> · {{ profile.email }}</span>
          </div>
        </div>
        <div class="doc-title">{{ bill ? t('bill.invoice') : t('quotation.title') }}</div>
      </header>

      <section class="meta">
        <div>
          <div class="muted small">{{ t('doc.to') }}</div>
          <template v-if="bill">
            <strong>{{ bill.client.name }}</strong>
            <div v-if="bill.client.address" class="small">{{ bill.client.address }}</div>
            <div class="small">{{ t('hierarchy.site') }}: {{ bill.site.name }}<span v-if="bill.site.address">, {{ bill.site.address }}</span></div>
          </template>
          <template v-else-if="quote">
            <strong>{{ quote.clientName }}</strong>
            <div v-if="quote.siteName" class="small">{{ t('hierarchy.site') }}: {{ quote.siteName }}</div>
          </template>
        </div>
        <table class="kv">
          <template v-if="bill">
            <tr><td>{{ t('bill.no') }}</td><td><strong>{{ bill.billNo }}</strong></td></tr>
            <tr><td>{{ t('bill.date') }}</td><td>{{ fmt.date(bill.billDate) }}</td></tr>
            <tr v-if="bill.dueDate"><td>{{ t('bill.dueDate') }}</td><td>{{ fmt.date(bill.dueDate) }}</td></tr>
            <tr><td>{{ t('bill.workOrderRef') }}</td><td>{{ bill.woNo }}<span v-if="bill.clientRef"> ({{ bill.clientRef }})</span></td></tr>
            <tr v-if="bill.periodFrom || bill.periodTo"><td>{{ t('bill.period') }}</td><td>{{ fmt.date(bill.periodFrom) }} – {{ fmt.date(bill.periodTo) }}</td></tr>
          </template>
          <template v-else-if="quote">
            <tr><td>{{ t('quotation.no') }}</td><td><strong>{{ quote.quoteNo }}</strong></td></tr>
            <tr><td>{{ t('common.date') }}</td><td>{{ fmt.date(quote.quoteDate) }}</td></tr>
            <tr v-if="quote.validUntil"><td>{{ t('quotation.validUntil') }}</td><td>{{ fmt.date(quote.validUntil) }}</td></tr>
          </template>
        </table>
      </section>

      <p class="subject"><span class="muted">{{ t('doc.subject') }}:</span> <strong>{{ bill ? `${bill.title} — ${bill.woTitle}` : quote?.title }}</strong></p>

      <table class="items">
        <thead>
          <tr>
            <th style="width: 40px">{{ t('doc.sl') }}</th>
            <th>{{ t('doc.description') }}</th>
            <th class="num">{{ t('doc.quantity') }}</th>
            <th>{{ t('doc.uom') }}</th>
            <th class="num">{{ t('doc.rate') }}</th>
            <th class="num">{{ t('doc.amount') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(l, i) in lines" :key="i">
            <td>{{ fmt.digits(i + 1) }}</td>
            <td>{{ l.description }}</td>
            <td class="num">{{ fmt.num(l.quantity, 3) }}</td>
            <td>{{ l.uom }}</td>
            <td class="num">{{ fmt.money(l.rate) }}</td>
            <td class="num">{{ fmt.money(l.amount) }}</td>
          </tr>
        </tbody>
      </table>

      <section class="sum">
        <table v-if="bill">
          <tr><td>{{ t('bill.gross') }}</td><td class="num">{{ fmt.money(bill.grossAmount) }}</td></tr>
          <tr v-if="Number(bill.retentionAmount)"><td>{{ t('bill.retention') }}</td><td class="num">− {{ fmt.money(bill.retentionAmount) }}</td></tr>
          <tr v-if="Number(bill.deductionAmount)"><td>{{ t('bill.deduction') }}<span v-if="bill.deductionNote" class="muted small"> ({{ bill.deductionNote }})</span></td><td class="num">− {{ fmt.money(bill.deductionAmount) }}</td></tr>
          <tr class="grand"><td>{{ t('bill.net') }}</td><td class="num">{{ fmt.money(bill.netAmount) }}</td></tr>
        </table>
        <table v-else-if="quote">
          <tr><td>{{ t('doc.subtotal') }}</td><td class="num">{{ fmt.money(quote.subtotal) }}</td></tr>
          <tr v-if="Number(quote.discount)"><td>{{ t('doc.discount') }}</td><td class="num">− {{ fmt.money(quote.discount) }}</td></tr>
          <tr class="grand"><td>{{ t('doc.grandTotal') }}</td><td class="num">{{ fmt.money(quote.total) }}</td></tr>
        </table>
      </section>
      <p class="words"><span class="muted">{{ t('bill.inWords') }}:</span> {{ words(bill ? bill.netAmount : quote?.total ?? 0) }}</p>

      <section v-if="bill" class="running">
        <table>
          <tr>
            <td>{{ t('workOrder.contractValue') }}</td><td class="num">{{ fmt.money(bill.contractValue) }}</td>
            <td>{{ t('bill.previous') }}</td><td class="num">{{ fmt.money(bill.previousGross) }}</td>
          </tr>
          <tr>
            <td>{{ t('bill.thisBill') }}</td><td class="num">{{ fmt.money(bill.grossAmount) }}</td>
            <td>{{ t('bill.cumulative') }}</td><td class="num">{{ fmt.money(Number(bill.previousGross) + Number(bill.grossAmount)) }}</td>
          </tr>
          <tr v-if="bill.receipts.length">
            <td>{{ t('bill.received') }}</td><td class="num">{{ fmt.money(bill.received) }}</td>
            <td>{{ t('bill.balance') }}</td><td class="num"><strong>{{ fmt.money(bill.balance) }}</strong></td>
          </tr>
        </table>
      </section>

      <section v-if="quote?.terms" class="terms">
        <h3>{{ t('quotation.terms') }}</h3>
        <p>{{ quote.terms }}</p>
      </section>
      <p v-if="(bill?.notes || quote?.notes)" class="small">{{ bill?.notes || quote?.notes }}</p>

      <footer class="signs">
        <div v-if="bill">{{ t('doc.receivedBy') }}</div>
        <div v-else />
        <div>{{ t('doc.signature') }}<br /><span class="small muted">{{ profile.name }}</span></div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.sheet { background: #fff; max-width: 820px; margin: 0 auto; padding: 36px 40px; border: 1px solid var(--app-border); border-radius: 8px; color: #111; }
.letterhead { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 3px solid var(--p-primary-600); padding-bottom: 12px; gap: 16px; }
.company { font-size: 1.5rem; font-weight: 700; color: var(--p-primary-800); }
.doc-title { font-size: 1.6rem; font-weight: 700; letter-spacing: 0.04em; color: #333; text-transform: uppercase; }
.meta { display: flex; justify-content: space-between; gap: 24px; margin: 18px 0 8px; flex-wrap: wrap; }
.kv td { padding: 2px 8px; font-size: 0.92rem; }
.kv td:first-child { color: #555; }
.subject { margin: 10px 0 14px; }
.items { width: 100%; border-collapse: collapse; font-size: 0.92rem; }
.items th { background: #f1f5f5; text-align: left; padding: 8px; border: 1px solid #d5dddd; }
.items th.num { text-align: right; }
.items td { padding: 7px 8px; border: 1px solid #e1e7e7; }
.sum { display: flex; justify-content: flex-end; margin-top: 10px; }
.sum table { min-width: 340px; border-collapse: collapse; }
.sum td { padding: 5px 8px; }
.sum .grand td { font-weight: 700; font-size: 1.1rem; border-top: 2px solid #333; }
.words { margin: 10px 0; font-style: italic; }
.running table { width: 100%; border-collapse: collapse; font-size: 0.88rem; margin-top: 8px; background: #fafcfc; }
.running td { padding: 6px 8px; border: 1px solid #e6ecec; }
.terms h3 { font-size: 0.95rem; margin: 18px 0 4px; }
.terms p { white-space: pre-line; margin: 0; font-size: 0.9rem; }
.signs { display: flex; justify-content: space-between; margin-top: 70px; }
.signs > div { min-width: 200px; border-top: 1px solid #555; padding-top: 6px; text-align: center; }
.signs > div:empty { border: none; }
@media print {
  .sheet { border: none; padding: 0; max-width: none; }
  .page { padding: 0; }
}
@media (max-width: 640px) { .sheet { padding: 18px 14px; } .items { font-size: 0.8rem; } .sum table { min-width: 0; width: 100%; } }
</style>
