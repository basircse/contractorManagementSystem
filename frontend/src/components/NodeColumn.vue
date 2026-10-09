<script setup lang="ts">
import { useI18n } from 'vue-i18n'

interface NodeLike { id: number; name: string; code: string | null; status: string }

defineProps<{
  title: string
  items: NodeLike[]
  selectedId: number | null
  canAdd: boolean
  canEdit: boolean
  loading?: boolean
  emptyText?: string
}>()
const emit = defineEmits<{
  select: [id: number]
  add: []
  edit: [item: NodeLike]
  toggleArchive: [item: NodeLike]
}>()
const { t } = useI18n()
</script>

<template>
  <div class="col">
    <div class="col-head">
      <span>{{ title }}</span>
      <Button v-if="canEdit && canAdd" icon="pi pi-plus" size="small" text rounded :aria-label="t('common.add')" v-tooltip.top="t('common.add')" @click="emit('add')" />
    </div>
    <div class="col-body">
      <div v-if="loading" class="muted small pad">{{ t('common.loading') }}</div>
      <div v-else-if="!items.length" class="muted small pad">{{ emptyText ?? t('common.none') }}</div>
      <div
        v-for="it in items"
        :key="it.id"
        class="node"
        :class="{ selected: it.id === selectedId, archived: it.status === 'ARCHIVED' }"
        role="button"
        tabindex="0"
        @click="emit('select', it.id)"
        @keydown.enter="emit('select', it.id)"
      >
        <div class="node-name">
          <span>{{ it.name }}</span>
          <Tag v-if="it.status === 'ARCHIVED'" :value="t('common.archived')" severity="secondary" class="small" />
        </div>
        <div v-if="canEdit" class="node-actions" @click.stop>
          <Button icon="pi pi-pencil" size="small" text rounded :aria-label="t('common.edit')" @click="emit('edit', it)" />
          <Button
            :icon="it.status === 'ARCHIVED' ? 'pi pi-replay' : 'pi pi-inbox'"
            size="small" text rounded severity="secondary"
            :aria-label="it.status === 'ARCHIVED' ? t('common.restore') : t('common.archive')"
            v-tooltip.top="it.status === 'ARCHIVED' ? t('common.restore') : t('common.archive')"
            @click="emit('toggleArchive', it)"
          />
        </div>
        <i v-else class="pi pi-angle-right muted" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.col { background: var(--app-surface); border: 1px solid var(--app-border); border-radius: 10px; display: flex; flex-direction: column; min-height: 360px; min-width: 0; }
.col-head { display: flex; align-items: center; justify-content: space-between; padding: 8px 8px 8px 14px; border-bottom: 1px solid var(--app-border); font-weight: 600; }
.col-body { padding: 6px; overflow-y: auto; max-height: 65vh; }
.pad { padding: 10px; }
.node { display: flex; align-items: center; justify-content: space-between; gap: 6px; padding: 6px 8px; border-radius: 8px; cursor: pointer; }
.node:hover { background: #f1f5f4; }
.node.selected { background: #ccfbf1; }
.node.archived .node-name > span { color: var(--app-muted); text-decoration: line-through; }
.node-name { display: flex; align-items: center; gap: 6px; min-width: 0; }
.node-name > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node-actions { display: flex; flex-shrink: 0; opacity: 0.55; }
.node:hover .node-actions, .node.selected .node-actions { opacity: 1; }
</style>
