<script setup lang="ts">
import { onMounted, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api/http'
import type { Building, Floor, Site, Unit } from '@/api/types'

export interface LocationValue {
  siteId: number | null
  buildingId: number | null
  floorId: number | null
  unitId: number | null
}

const props = withDefaults(defineProps<{ showUnit?: boolean; siteRequired?: boolean; compact?: boolean }>(), {
  showUnit: true,
  siteRequired: true,
  compact: false,
})
const model = defineModel<LocationValue>({ required: true })
const { t } = useI18n()
const uid = useId()

const sites = ref<Site[]>([])
const buildings = ref<Building[]>([])
const floors = ref<Floor[]>([])
const units = ref<Unit[]>([])

onMounted(async () => {
  sites.value = (await http.get<Site[]>('/app/sites')).data
  await loadChildren()
})

async function loadChildren() {
  const v = model.value
  buildings.value = v.siteId ? (await http.get<Building[]>('/app/buildings', { params: { siteId: v.siteId } })).data : []
  floors.value = v.buildingId ? (await http.get<Floor[]>('/app/floors', { params: { buildingId: v.buildingId } })).data : []
  units.value = v.floorId && props.showUnit ? (await http.get<Unit[]>('/app/units', { params: { floorId: v.floorId } })).data : []
}

function set(level: keyof LocationValue, id: number | null) {
  const next = { ...model.value, [level]: id }
  const order: (keyof LocationValue)[] = ['siteId', 'buildingId', 'floorId', 'unitId']
  for (const deeper of order.slice(order.indexOf(level) + 1)) next[deeper] = null
  model.value = next
}

watch(() => [model.value.siteId, model.value.buildingId, model.value.floorId], loadChildren)
</script>

<template>
  <div class="loc" :class="{ compact }">
    <div class="field">
      <label :for="`${uid}-site`">{{ t('hierarchy.site') }}{{ siteRequired ? ' *' : '' }}</label>
      <Select :input-id="`${uid}-site`" :model-value="model.siteId" :options="sites" option-label="name" option-value="id"
              :placeholder="siteRequired ? t('hierarchy.selectSite') : t('common.all')" :show-clear="!siteRequired" filter
              @update:model-value="set('siteId', $event)" />
    </div>
    <div class="field">
      <label :for="`${uid}-building`">{{ t('hierarchy.building') }}</label>
      <Select :input-id="`${uid}-building`" :model-value="model.buildingId" :options="buildings" option-label="name" option-value="id"
              :placeholder="t('common.all')" show-clear :disabled="!model.siteId"
              @update:model-value="set('buildingId', $event)" />
    </div>
    <div class="field">
      <label :for="`${uid}-floor`">{{ t('hierarchy.floor') }}</label>
      <Select :input-id="`${uid}-floor`" :model-value="model.floorId" :options="floors" option-label="name" option-value="id"
              :placeholder="t('common.all')" show-clear :disabled="!model.buildingId"
              @update:model-value="set('floorId', $event)" />
    </div>
    <div v-if="showUnit" class="field">
      <label :for="`${uid}-unit`">{{ t('hierarchy.unit') }}</label>
      <Select :input-id="`${uid}-unit`" :model-value="model.unitId" :options="units" option-label="name" option-value="id"
              :placeholder="t('common.all')" show-clear :disabled="!model.floorId"
              @update:model-value="set('unitId', $event)" />
    </div>
  </div>
</template>

<style scoped>
.loc { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; }
.field { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.field label { font-size: 0.8rem; color: var(--app-muted); }
.field :deep(.p-select) { width: 100%; }
</style>
