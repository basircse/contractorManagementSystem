<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { http } from '@/api/http'
import { SKILL_TIERS, type RateCard, type Site, type SkillTier } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { isoDate, parseIso, useFormat } from '@/composables/useFormat'
import { useNotify } from '@/composables/useNotify'

const { t } = useI18n()
const auth = useAuthStore()
const confirm = useConfirm()
const notify = useNotify()
const fmt = useFormat()
const canEdit = computed(() => !auth.readOnly)

const rows = ref<RateCard[]>([])
const sites = ref<Site[]>([])
const loading = ref(false)
const siteName = (id: number | null) => (id ? sites.value.find((s) => s.id === id)?.name ?? '#' + id : t('rate.allSites'))
const tierOptions = computed(() => SKILL_TIERS.map((v) => ({ value: v, label: fmt.tier(v) })))

async function load() {
  loading.value = true
  try {
    const [r, s] = await Promise.all([http.get<RateCard[]>('/app/rate-cards'), http.get<Site[]>('/app/sites')])
    rows.value = r.data
    sites.value = s.data
  } catch (e) {
    notify.error(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const open = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ skillTier: 'HELPER' as SkillTier, siteId: null as number | null, dailyRate: 0, otHourlyRate: 0, skillAllowance: 0, effectiveFrom: new Date() as Date | null })
const saving = ref(false)

function openNew() {
  editingId.value = null
  form.value = { skillTier: 'HELPER', siteId: null, dailyRate: 0, otHourlyRate: 0, skillAllowance: 0, effectiveFrom: new Date() }
  open.value = true
}
function openEdit(r: RateCard) {
  editingId.value = r.id
  form.value = { skillTier: r.skillTier, siteId: r.siteId, dailyRate: r.dailyRate, otHourlyRate: r.otHourlyRate, skillAllowance: r.skillAllowance, effectiveFrom: parseIso(r.effectiveFrom) }
  open.value = true
}
async function save() {
  saving.value = true
  const body = { ...form.value, effectiveFrom: isoDate(form.value.effectiveFrom) }
  try {
    if (editingId.value) await http.put(`/app/rate-cards/${editingId.value}`, body)
    else await http.post('/app/rate-cards', body)
    open.value = false
    notify.success()
    await load()
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}
function remove(r: RateCard) {
  confirm.require({
    message: `${fmt.tier(r.skillTier)} — ${fmt.money(r.dailyRate)}`,
    header: t('common.delete'),
    acceptLabel: t('common.yes'),
    rejectLabel: t('common.no'),
    acceptProps: { severity: 'danger' },
    accept: async () => {
      try {
        await http.delete(`/app/rate-cards/${r.id}`)
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
      <h1>{{ t('rate.title') }}</h1>
      <div class="actions">
        <Button v-if="canEdit" :label="t('common.add')" icon="pi pi-plus" @click="openNew" />
      </div>
    </div>
    <Message severity="info" :closable="false" style="margin-bottom: 12px">{{ t('rate.hint') }}</Message>
    <div class="card">
      <DataTable :value="rows" :loading="loading" data-key="id" size="small" striped-rows row-group-mode="subheader" group-rows-by="skillTier">
        <template #groupheader="{ data }"><strong>{{ fmt.tier(data.skillTier) }}</strong></template>
        <template #empty><span class="muted">{{ t('common.none') }}</span></template>
        <Column field="skillTier" :header="t('labour.skillTier')">
          <template #body="{ data }">{{ fmt.tier(data.skillTier) }}</template>
        </Column>
        <Column :header="t('hierarchy.site')">
          <template #body="{ data }">{{ siteName(data.siteId) }}</template>
        </Column>
        <Column :header="t('rate.dailyRate')" body-class="num" header-class="num">
          <template #body="{ data }">{{ fmt.money(data.dailyRate) }}</template>
        </Column>
        <Column :header="t('rate.allowance')" body-class="num">
          <template #body="{ data }">{{ fmt.money(data.skillAllowance) }}</template>
        </Column>
        <Column :header="t('rate.otRate')" body-class="num">
          <template #body="{ data }">{{ fmt.money(data.otHourlyRate) }}</template>
        </Column>
        <Column :header="t('rate.effectiveFrom')">
          <template #body="{ data }">{{ fmt.date(data.effectiveFrom) }}</template>
        </Column>
        <Column v-if="canEdit" style="width: 1%">
          <template #body="{ data }">
            <div class="flex" style="flex-wrap: nowrap">
              <Button icon="pi pi-pencil" text size="small" :aria-label="t('common.edit')" @click="openEdit(data)" />
              <Button icon="pi pi-trash" text size="small" severity="danger" :aria-label="t('common.delete')" @click="remove(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="open" modal :header="editingId ? t('common.edit') : t('common.add')" :style="{ width: '520px', maxWidth: '95vw' }">
      <form class="form" @submit.prevent="save">
        <div class="row">
          <div class="field">
            <label for="r-tier">{{ t('labour.skillTier') }} *</label>
            <Select input-id="r-tier" v-model="form.skillTier" :options="tierOptions" option-label="label" option-value="value" />
          </div>
          <div class="field">
            <label for="r-site">{{ t('hierarchy.site') }}</label>
            <Select input-id="r-site" v-model="form.siteId" :options="sites" option-label="name" option-value="id" :placeholder="t('rate.allSites')" show-clear />
          </div>
        </div>
        <div class="row">
          <div class="field">
            <label for="r-daily">{{ t('rate.dailyRate') }} *</label>
            <InputNumber v-model="form.dailyRate" input-id="r-daily" :locale="fmt.intlLocale.value" :min="0" :max-fraction-digits="2" prefix="৳ " />
          </div>
          <div class="field">
            <label for="r-allow">{{ t('rate.allowance') }}</label>
            <InputNumber v-model="form.skillAllowance" input-id="r-allow" :locale="fmt.intlLocale.value" :min="0" :max-fraction-digits="2" prefix="৳ " />
          </div>
        </div>
        <div class="row">
          <div class="field">
            <label for="r-ot">{{ t('rate.otRate') }}</label>
            <InputNumber v-model="form.otHourlyRate" input-id="r-ot" :locale="fmt.intlLocale.value" :min="0" :max-fraction-digits="2" prefix="৳ " />
          </div>
          <div class="field">
            <label for="r-from">{{ t('rate.effectiveFrom') }} *</label>
            <DatePicker v-model="form.effectiveFrom" input-id="r-from" show-icon date-format="dd/mm/yy" />
          </div>
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="open = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="!form.effectiveFrom" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
