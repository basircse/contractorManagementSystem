<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import type { Building, Client, Floor, Site, Unit } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useNotify } from '@/composables/useNotify'
import { isoDate, parseIso } from '@/composables/useFormat'
import NodeColumn from '@/components/NodeColumn.vue'

type Level = 'clients' | 'sites' | 'buildings' | 'floors' | 'units'
const LEVELS: Level[] = ['clients', 'sites', 'buildings', 'floors', 'units']
const PARENT_PARAM: Record<Level, string | null> = { clients: null, sites: 'clientId', buildings: 'siteId', floors: 'buildingId', units: 'floorId' }

const { t } = useI18n()
const auth = useAuthStore()
const notify = useNotify()
const canEdit = computed(() => !auth.readOnly)

const showArchived = ref(false)
const items = reactive<Record<Level, (Client | Site | Building | Floor | Unit)[]>>({ clients: [], sites: [], buildings: [], floors: [], units: [] })
const selected = reactive<Record<Level, number | null>>({ clients: null, sites: null, buildings: null, floors: null, units: null })
const loading = reactive<Record<Level, boolean>>({ clients: false, sites: false, buildings: false, floors: false, units: false })

function parentOf(level: Level): number | null {
  const i = LEVELS.indexOf(level)
  return i === 0 ? null : selected[LEVELS[i - 1]]
}

async function load(level: Level) {
  const param = PARENT_PARAM[level]
  const parent = parentOf(level)
  if (param && parent === null) {
    items[level] = []
    return
  }
  loading[level] = true
  try {
    const params: Record<string, unknown> = { includeArchived: showArchived.value }
    if (param) params[param] = parent
    items[level] = (await http.get(`/app/${level}`, { params })).data
  } catch (e) {
    notify.error(e)
  } finally {
    loading[level] = false
  }
}

function select(level: Level, id: number) {
  selected[level] = id
  const i = LEVELS.indexOf(level)
  for (const deeper of LEVELS.slice(i + 1)) {
    selected[deeper] = null
    items[deeper] = []
  }
  if (i + 1 < LEVELS.length) load(LEVELS[i + 1])
}

async function reloadAll() {
  for (const l of LEVELS) await load(l)
}
watch(showArchived, reloadAll)
onMounted(() => load('clients'))

// ------------------------------------------------------------ edit dialog
const dlgOpen = ref(false)
const dlgLevel = ref<Level>('clients')
const dlgId = ref<number | null>(null)
const form = ref<Record<string, any>>({})
const saving = ref(false)

const singular: Record<Level, string> = { clients: 'client', sites: 'site', buildings: 'building', floors: 'floor', units: 'unit' }

function openAdd(level: Level) {
  dlgLevel.value = level
  dlgId.value = null
  form.value = { name: '', code: '', description: '' }
  dlgOpen.value = true
}
function openEdit(level: Level, item: Record<string, unknown>) {
  dlgLevel.value = level
  dlgId.value = item.id as number
  form.value = { ...item, startDate: parseIso(item.startDate as string | null) }
  dlgOpen.value = true
}

async function save() {
  const level = dlgLevel.value
  const f = form.value
  const body: Record<string, unknown> = { name: f.name, code: f.code || null, description: f.description || null }
  if (level === 'clients') Object.assign(body, { phone: f.phone || null, email: f.email || null, address: f.address || null })
  if (level === 'sites') Object.assign(body, { address: f.address || null, startDate: isoDate(f.startDate as Date | null) })
  if (level === 'floors') body.levelNo = f.levelNo ?? null
  const param = PARENT_PARAM[level]
  if (param) body[param] = dlgId.value ? f[param] : parentOf(level)

  saving.value = true
  try {
    if (dlgId.value) await http.put(`/app/${level}/${dlgId.value}`, body)
    else await http.post(`/app/${level}`, body)
    dlgOpen.value = false
    notify.success()
    await load(level)
  } catch (e) {
    notify.error(e)
  } finally {
    saving.value = false
  }
}

async function toggleArchive(level: Level, item: { id: number; status: string }) {
  try {
    await http.post(`/app/${level}/${item.id}/${item.status === 'ARCHIVED' ? 'restore' : 'archive'}`)
    notify.success()
    await load(level)
  } catch (e) {
    notify.error(e)
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ t('nav.projects') }}</h1>
      <div class="actions">
        <label class="flex small">
          <ToggleSwitch v-model="showArchived" />
          {{ t('common.showArchived') }}
        </label>
      </div>
    </div>
    <div class="columns">
      <NodeColumn
        v-for="(level, i) in LEVELS"
        :key="level"
        :title="t(`hierarchy.${level}`)"
        :items="items[level]"
        :selected-id="selected[level]"
        :loading="loading[level]"
        :can-edit="canEdit"
        :can-add="i === 0 || selected[LEVELS[i - 1]] !== null"
        :empty-text="i > 0 && selected[LEVELS[i - 1]] === null ? '←' : undefined"
        @select="select(level, $event)"
        @add="openAdd(level)"
        @edit="openEdit(level, $event as unknown as Record<string, unknown>)"
        @toggle-archive="toggleArchive(level, $event)"
      />
    </div>

    <Dialog v-model:visible="dlgOpen" modal :header="(dlgId ? t('common.edit') : t('common.add')) + ' — ' + t(`hierarchy.${singular[dlgLevel]}`)" :style="{ width: '520px', maxWidth: '95vw' }">
      <form class="form" @submit.prevent="save">
        <div class="row">
          <div class="field">
            <label for="n-name">{{ t('common.name') }} *</label>
            <InputText id="n-name" v-model="form.name" required autofocus />
          </div>
          <div class="field">
            <label for="n-code">{{ t('common.code') }}</label>
            <InputText id="n-code" v-model="form.code" />
          </div>
        </div>
        <template v-if="dlgLevel === 'clients'">
          <div class="row">
            <div class="field">
              <label for="n-phone">{{ t('common.phone') }}</label>
              <InputText id="n-phone" v-model="form.phone" />
            </div>
            <div class="field">
              <label for="n-email">{{ t('common.email') }}</label>
              <InputText id="n-email" v-model="form.email" type="email" />
            </div>
          </div>
        </template>
        <div v-if="dlgLevel === 'clients' || dlgLevel === 'sites'" class="field">
          <label for="n-address">{{ t('common.address') }}</label>
          <InputText id="n-address" v-model="form.address" />
        </div>
        <div v-if="dlgLevel === 'sites'" class="field">
          <label for="n-start">{{ t('hierarchy.startDate') }}</label>
          <DatePicker v-model="form.startDate" input-id="n-start" show-icon date-format="dd/mm/yy" />
        </div>
        <div v-if="dlgLevel === 'floors'" class="field">
          <label for="n-level">{{ t('hierarchy.levelNo') }}</label>
          <InputNumber v-model="form.levelNo" input-id="n-level" :locale="$i18n.locale === 'bn' ? 'bn-BD' : 'en-US'" :use-grouping="false" />
        </div>
        <div class="field">
          <label for="n-desc">{{ t('common.description') }}</label>
          <Textarea id="n-desc" v-model="form.description" rows="2" auto-resize />
        </div>
        <div class="flex">
          <span class="spacer" />
          <Button type="button" :label="t('common.cancel')" text @click="dlgOpen = false" />
          <Button type="submit" :label="t('common.save')" :loading="saving" />
        </div>
      </form>
    </Dialog>
  </div>
</template>

<style scoped>
.columns { display: grid; grid-template-columns: repeat(5, minmax(180px, 1fr)); gap: 12px; overflow-x: auto; padding-bottom: 6px; }
@media (max-width: 1100px) { .columns { grid-template-columns: repeat(5, 220px); } }
</style>
